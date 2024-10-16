/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.deltafi.core.repo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.collect.Lists;
import jakarta.persistence.*;
import org.jetbrains.annotations.NotNull;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.types.*;
import org.deltafi.core.generated.types.*;
import org.deltafi.core.types.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;

import static org.deltafi.common.types.ActionState.*;

@Repository
@RequiredArgsConstructor
@Slf4j
public class DeltaFileRepoImpl implements DeltaFileRepoCustom {
    public static final String FILTERED = "filtered";

    // a magic number known by the GUI that says there are "many" total results
    private static final int MANY_RESULTS = 10_000;

    @PersistenceContext
    private final EntityManager entityManager;
    private final JdbcTemplate jdbcTemplate;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .configure(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS, true)
            .configure(SerializationFeature.WRITE_DATES_WITH_ZONE_ID, true);

    private List<DeltaFile> fetchByDidIn(List<UUID> dids) {
        if (dids.isEmpty()) {
            return Collections.emptyList();
        }

        String deltaFileQuery = """
            SELECT DISTINCT df FROM DeltaFile df
            LEFT JOIN FETCH df.flows f
            LEFT JOIN FETCH df.annotations a
            WHERE df.did IN :dids
        """;

        return entityManager.createQuery(deltaFileQuery, DeltaFile.class)
                .setParameter("dids", dids)
                .getResultList();
    }

    @Override
    @Transactional
    public List<DeltaFile> updateForRequeue(OffsetDateTime requeueTime, Duration requeueDuration, Set<String> skipActions, Set<UUID> skipDids, int limit) {
        StringBuilder sqlQuery = new StringBuilder("""
            SELECT df.did FROM delta_files df
            WHERE df.stage = 'IN_FLIGHT'
            AND df.modified < :requeueThreshold
            AND EXISTS ( SELECT 1
                         FROM delta_file_flows dff
                         WHERE dff.delta_file_id = df.did
                         AND dff.state = 'IN_FLIGHT'
                         AND dff.modified < :requeueThreshold
                         AND dff.cold_queued = false
        """);

        if (skipActions != null && !skipActions.isEmpty()) {
            sqlQuery.append("AND (dff.actions->(jsonb_array_length(dff.actions) - 1))->>'name' NOT IN (:skipActions)");
        }

        sqlQuery.append(") ");

        if (skipDids != null && !skipDids.isEmpty()) {
            sqlQuery.append("\nAND df.did NOT IN (:skipDids)");
        }

        sqlQuery.append("\nORDER BY df.modified LIMIT :limit");

        OffsetDateTime requeueThreshold = requeueTime.minus(requeueDuration);
        Query query = entityManager.createNativeQuery(sqlQuery.toString(), UUID.class)
                .setParameter("requeueThreshold", requeueThreshold)
                .setParameter("limit", limit);

        if (skipDids != null && !skipDids.isEmpty()) {
            query.setParameter("skipDids", skipDids);
        }
        if (skipActions != null && !skipActions.isEmpty()) {
            query.setParameter("skipActions", skipActions);
        }

        @SuppressWarnings("unchecked")
        List<UUID> didsToRequeue = query.getResultList();
        List<DeltaFile> filesToRequeue = fetchByDidIn(didsToRequeue);

        filesToRequeue.forEach(deltaFile -> {
            deltaFile.setRequeueCount(deltaFile.getRequeueCount() + 1);
            deltaFile.setModified(requeueTime);
            deltaFile.getFlows().forEach(flow -> {
                Action action = flow.lastAction();
                if ((action.getState() == QUEUED) &&
                        action.getModified().isBefore(requeueThreshold) &&
                        (skipActions == null || !skipActions.contains(action.getName()))) {
                    action.setModified(requeueTime);
                    action.setQueued(requeueTime);
                    flow.updateState();
                }
            });
        });

        return filesToRequeue;
    }

    @Override
    @Transactional
    public List<DeltaFile> updateColdQueuedForRequeue(List<String> actionNames, int maxFiles, OffsetDateTime modified) {
        String nativeQueryStr = """
            SELECT df.did FROM delta_files df
            WHERE df.stage = 'IN_FLIGHT'
            AND EXISTS (
                SELECT 1
                FROM delta_file_flows dff
                WHERE dff.delta_file_id = df.did
                AND dff.state = 'IN_FLIGHT'
                AND dff.cold_queued = TRUE
                AND EXISTS (
                    SELECT 1
                    FROM jsonb_array_elements(dff.actions) AS action
                    WHERE action->>'state' = 'COLD_QUEUED'
                    AND action->>'name' IN (:actionNames)
                )
            )
            ORDER BY df.modified
            LIMIT :limit
        """;

        Query nativeQuery = entityManager.createNativeQuery(nativeQueryStr, UUID.class)
                .setParameter("actionNames", actionNames)
                .setParameter("limit", maxFiles);

        @SuppressWarnings("unchecked")
        List<UUID> dids = nativeQuery.getResultList();
        List<DeltaFile> filesToRequeue = fetchByDidIn(dids);

        filesToRequeue.forEach(deltaFile -> {
            deltaFile.setRequeueCount(deltaFile.getRequeueCount() + 1);
            deltaFile.setModified(modified);
            deltaFile.getFlows().forEach(flow -> {
                Action action = flow.lastAction();
                if (action.getState() == COLD_QUEUED && actionNames.contains(action.getName())) {
                    action.setState(QUEUED);
                    action.setModified(modified);
                    action.setQueued(modified);
                    flow.updateState();
                }
            });
        });

        return filesToRequeue;
    }

    @Override
    public List<DeltaFile> findReadyForAutoResume(OffsetDateTime maxReadyTime) {
        String queryStr = """
            SELECT df.did
            FROM delta_files df
            WHERE df.stage = 'ERROR'
            AND df.content_deleted IS NULL
            AND EXISTS (
                SELECT 1
                FROM delta_file_flows dff
                WHERE dff.delta_file_id = df.did
                AND dff.state = 'ERROR'
                AND dff.next_auto_resume < :maxReadyTime
            )
            """;

        Query query = entityManager.createNativeQuery(queryStr, UUID.class)
                .setParameter("maxReadyTime", maxReadyTime);

        @SuppressWarnings("unchecked")
        List<UUID> dids = query.getResultList();

        return fetchByDidIn(dids);
    }

    @Override
    public List<DeltaFile> findResumePolicyCandidates(String dataSource) {
        StringBuilder queryBuilder = new StringBuilder("""
                SELECT df.did
                FROM delta_files df
                WHERE df.stage = 'ERROR'
                AND df.content_deleted IS NULL
                AND EXISTS (
                    SELECT 1
                    FROM delta_file_flows flow
                    WHERE flow.delta_file_id = df.did
                    AND flow.error_acknowledged IS NULL
                    AND EXISTS (
                        SELECT 1
                        FROM jsonb_array_elements(flow.actions) action
                        WHERE action->>'nextAutoResume' IS NULL
                    )
                )
            """);

        if (dataSource != null) {
            queryBuilder.append("AND df.data_source = :dataSource ");
        }

        Query query = entityManager.createNativeQuery(queryBuilder.toString(), UUID.class);

        if (dataSource != null) {
            query.setParameter("dataSource", dataSource);
        }

        @SuppressWarnings("unchecked")
        List<UUID> dids = query.getResultList();
        return fetchByDidIn(dids);
    }

    @Override
    @Transactional
    public void updateForAutoResume(List<UUID> dids, String policyName, OffsetDateTime nextAutoResume) {
        if (dids == null || dids.isEmpty()) {
            return;
        }

        String nextAutoResumeJson;
        String policyNameJson;
        try {
            nextAutoResumeJson = OBJECT_MAPPER.writeValueAsString(nextAutoResume);
            policyNameJson = OBJECT_MAPPER.writeValueAsString(policyName);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }

        for (List<UUID> batch : Lists.partition(dids, 500)) {
            String queryStr = """
                UPDATE delta_file_flows
                SET actions = jsonb_set(
                    jsonb_set(
                        actions,
                        CAST(ARRAY[jsonb_array_length(actions) - 1, 'nextAutoResume'] AS text[]),
                        CAST(? AS jsonb),
                        false
                    ),
                    CAST(ARRAY[jsonb_array_length(actions) - 1, 'nextAutoResumeReason'] AS text[]),
                    CAST(? AS jsonb),
                    false
                )
                FROM delta_files df
                WHERE delta_file_flows.delta_file_id = df.did
                AND df.did IN (?)
                AND EXISTS (
                    SELECT 1
                    FROM jsonb_array_elements(delta_file_flows.actions) AS action
                    WHERE action->>'state' = 'ERROR'
                )
            """;

            Query query = entityManager.createNativeQuery(queryStr)
                    .setParameter(1, nextAutoResumeJson)
                    .setParameter(2, policyNameJson)
                    .setParameter(3, batch);

            query.executeUpdate();
        }
    }

    @Override
    @Transactional
    public int deleteIfNoContent(OffsetDateTime createdBefore, OffsetDateTime completedBefore, long minBytes, String flow, int batchSize) {
        if (createdBefore == null && completedBefore == null) {
            return 0;
        }

        StringBuilder queryBuilder = new StringBuilder("""
                WITH deleted_files AS (
                    SELECT did FROM delta_files WHERE content_deleted IS NOT NULL
                """);

        Map<String, Object> parameters = new HashMap<>();

        if (createdBefore != null) {
            queryBuilder.append(" AND created < :createdBefore");
            parameters.put("createdBefore", createdBefore);
        }

        if (completedBefore != null) {
            queryBuilder.append(" AND modified < :completedBefore AND terminal = true");
            parameters.put("completedBefore", completedBefore);
        }

        if (flow != null) {
            queryBuilder.append(" AND data_source = :flow");
            parameters.put("flow", flow);
        }

        if (minBytes > 0L) {
            queryBuilder.append(" AND total_bytes >= :minBytes");
            parameters.put("minBytes", minBytes);
        }

        queryBuilder.append("""
                    LIMIT :batchSize
                    ),
                    deleted_flows AS (
                        DELETE FROM delta_file_flows
                        WHERE delta_file_id IN (SELECT did FROM deleted_files)
                    ),
                    deleted_annotations AS (
                        DELETE FROM annotations
                        WHERE delta_file_id IN (SELECT did FROM deleted_files)
                    ),
                    deleted_delta_files AS (
                        DELETE FROM delta_files
                        WHERE did IN (SELECT did FROM deleted_files)
                    )
                    SELECT COUNT(*) FROM deleted_files;
                """);

        Query query = entityManager.createNativeQuery(queryBuilder.toString());
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            query.setParameter(entry.getKey(), entry.getValue());
        }
        query.setParameter("batchSize", batchSize);

        Number result = (Number) query.getSingleResult();

        return result.intValue();
    }

    @Override
    public List<DeltaFileDeleteDTO> findForTimedDelete(OffsetDateTime createdBefore, OffsetDateTime completedBefore,
                                                       long minBytes, String flow, boolean deleteMetadata, int batchSize) {
        if (createdBefore == null && completedBefore == null) {
            return Collections.emptyList();
        }

        StringBuilder queryBuilder = new StringBuilder("""
                SELECT df.did, df.content_deleted, df.total_bytes, df.content_object_ids
                FROM delta_files df
                WHERE
                """);

        Map<String, Object> parameters = new HashMap<>();

        if (createdBefore != null) {
            queryBuilder.append(" df.created < :createdBefore");
            parameters.put("createdBefore", createdBefore);
        }

        if (completedBefore != null) {
            if (createdBefore != null) {
                queryBuilder.append(" AND");
            }
            queryBuilder.append(" df.modified < :completedBefore");
            queryBuilder.append(" AND df.terminal = true");
            parameters.put("completedBefore", completedBefore);
        }

        if (flow != null) {
            queryBuilder.append(" AND df.data_source = :flow");
            parameters.put("flow", flow);
        }

        if (minBytes > 0L) {
            queryBuilder.append(" AND df.total_bytes >= :minBytes");
            parameters.put("minBytes", minBytes);
        }

        if (!deleteMetadata) {
            queryBuilder.append(" AND df.content_deletable = true");
        }

        queryBuilder.append(" LIMIT :batchSize");

        Query query = entityManager.createNativeQuery(queryBuilder.toString());

        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            query.setParameter(entry.getKey(), entry.getValue());
        }
        query.setParameter("batchSize", batchSize);

        @SuppressWarnings("unchecked")
        List<Object[]> results = query.getResultList();

        return results.stream()
                .map(this::deserializeDeltaFileDeleteDTO)
                .toList();
    }

    @Override
    public List<DeltaFileDeleteDTO> findForDiskSpaceDelete(long bytesToDelete, String dataSource, int batchSize) {
        if (bytesToDelete < 1) {
            throw new IllegalArgumentException("bytesToDelete (%s) must be positive".formatted(bytesToDelete));
        }

        Query nativeQuery = entityManager.createNativeQuery(diskSpaceDeleteQuery(dataSource));

        if (dataSource != null) {
            nativeQuery.setParameter("dataSource", dataSource);
        }
        nativeQuery.setParameter("batchSize", batchSize);

        @SuppressWarnings("unchecked")
        List<Object[]> results = nativeQuery.getResultList();

        long[] sum = {0};
        return results.stream()
                .filter(row -> {
                    long totalBytes = ((Number) row[2]).longValue();
                    boolean over = sum[0] <= bytesToDelete;
                    sum[0] += totalBytes;
                    return over;
                })
                .map(this::deserializeDeltaFileDeleteDTO)
                .toList();
    }

    private DeltaFileDeleteDTO deserializeDeltaFileDeleteDTO(Object[] row) {
        UUID did = (UUID) row[0];
        OffsetDateTime contentDeleted = row[1] != null
                ? ((Instant) row[1]).atZone(ZoneId.systemDefault()).toOffsetDateTime()
                : null;
        long totalBytes = ((Number) row[2]).longValue();
        List<UUID> contentObjectIds = new ArrayList<>();
        String contentJson = (String) row[3];

        if (contentJson != null && !contentJson.equals("[]")) {
            try {
                JsonNode uuidArray = OBJECT_MAPPER.readTree(contentJson);
                for (JsonNode uuidNode : uuidArray) {
                    if (uuidNode.isTextual()) {
                        contentObjectIds.add(UUID.fromString(uuidNode.asText()));
                    }
                }
            } catch (JsonProcessingException ignored) {}
        }

        return new DeltaFileDeleteDTO(did, contentDeleted, totalBytes, contentObjectIds);
    }

    private static String diskSpaceDeleteQuery(String dataSource) {
        StringBuilder queryBuilder = new StringBuilder("""
            SELECT df.did, df.content_deleted, df.total_bytes, df.content_object_ids
            FROM delta_files df
            WHERE df.content_deletable = true
    """);

        if (dataSource != null) {
            queryBuilder.append("AND df.data_source = :dataSource ");
        }

        queryBuilder.append("""
            ORDER BY df.modified
            LIMIT :batchSize
    """);

        return queryBuilder.toString();
    }

    @Override
    public DeltaFiles deltaFiles(Integer offset, int limit, DeltaFilesFilter filter, DeltaFileOrder orderBy, List<String> includeFields) {
        StringBuilder sqlQuery = new StringBuilder("SELECT * FROM delta_files df WHERE TRUE\n");
        Map<String, Object> parameters = new HashMap<>();
        String criteria = buildDeltaFilesCriteria(parameters, filter);

        sqlQuery.append(criteria);

        if (orderBy != null) {
            sqlQuery.append("ORDER BY df.")
                    .append(orderBy.getField())
                    .append(" ")
                    .append(orderBy.getDirection() == DeltaFileDirection.ASC ? "ASC" : "DESC")
                    .append(" ");
        } else {
            sqlQuery.append("ORDER BY df.modified DESC ");
        }

        sqlQuery.append("LIMIT :limit OFFSET :offset");

        int intOffset = offset == null ? 0 : offset;

        Query query = entityManager.createNativeQuery(sqlQuery.toString(), DeltaFile.class);
        query.setParameter("limit", limit);
        query.setParameter("offset", intOffset);
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            query.setParameter(entry.getKey(), entry.getValue());
        }

        @SuppressWarnings("unchecked")
        List<DeltaFile> deltaFileList = query.getResultList();

        DeltaFiles deltaFiles = new DeltaFiles();
        deltaFiles.setOffset(intOffset);
        deltaFiles.setDeltaFiles(deltaFileList);
        deltaFiles.setCount(deltaFileList.size());

        if (deltaFileList.size() < limit) {
            deltaFiles.setTotalCount(intOffset + deltaFileList.size());
        } else {
            String countQuerySql = "SELECT COUNT (*) FROM (SELECT 1 FROM delta_files df WHERE TRUE\n" + criteria +
                    "LIMIT " + MANY_RESULTS + ") as sub";
            Query countQuery = entityManager.createNativeQuery(countQuerySql, Integer.class);
            for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                countQuery.setParameter(entry.getKey(), entry.getValue());
            }

            deltaFiles.setTotalCount((Integer) countQuery.getSingleResult());
        }

        return deltaFiles;
    }

    private String buildDeltaFilesCriteria(Map<String, Object> parameters, DeltaFilesFilter filter) {
        StringBuilder criteria = new StringBuilder();
        if (filter == null) {
            return criteria.toString();
        }

        if (filter.getDataSources() != null && !filter.getDataSources().isEmpty()) {
            criteria.append("AND df.data_source IN (:dataSources) ");
            parameters.put("dataSources", filter.getDataSources());
        }
        if (filter.getDids() != null && !filter.getDids().isEmpty()) {
            criteria.append("AND df.did IN (:dids) ");
            parameters.put("dids", filter.getDids());
        }
        if (filter.getParentDid() != null) {
            criteria.append("AND :parentDid IN df.dids ");
            parameters.put("parentDid", filter.getParentDid());
        }
        if (filter.getCreatedAfter() != null) {
            criteria.append("AND df.created > :createdAfter ");
            parameters.put("createdAfter", filter.getCreatedAfter());
        }
        if (filter.getCreatedBefore() != null) {
            criteria.append("AND df.created < :createdBefore ");
            parameters.put("createdBefore", filter.getCreatedBefore());
        }
        if (filter.getModifiedAfter() != null) {
            criteria.append("AND df.modified > :modifiedAfter ");
            parameters.put("modifiedAfter", filter.getModifiedAfter());
        }
        if (filter.getModifiedBefore() != null) {
            criteria.append("AND df.modified < :modifiedBefore ");
            parameters.put("modifiedBefore", filter.getModifiedBefore());
        }
        if (filter.getTerminalStage() != null) {
            criteria.append("AND df.terminal = :terminal ");
            parameters.put("terminal", filter.getTerminalStage());
        }
        if (filter.getStage() != null) {
            criteria.append("AND df.stage = :stage ");
            parameters.put("stage", filter.getStage().name());
        }
        if (filter.getFiltered() != null) {
            criteria.append("AND df.filtered = :filtered ");
            parameters.put("filtered", filter.getFiltered());
        }
        if (filter.getRequeueCountMin() != null) {
            criteria.append("AND df.requeue_count >= :requeueCountMin ");
            parameters.put("requeueCountMin", filter.getRequeueCountMin());
        }
        if (filter.getIngressBytesMin() != null) {
            criteria.append("AND df.ingress_bytes >= :ingressBytesMin ");
            parameters.put("ingressBytesMin", filter.getIngressBytesMin());
        }
        if (filter.getIngressBytesMax() != null) {
            criteria.append("AND df.ingress_bytes <= :ingressBytesMax ");
            parameters.put("ingressBytesMax", filter.getIngressBytesMax());
        }
        if (filter.getReferencedBytesMin() != null) {
            criteria.append("AND df.referenced_bytes >= :referencedBytesMin ");
            parameters.put("referencedBytesMin", filter.getReferencedBytesMin());
        }
        if (filter.getReferencedBytesMax() != null) {
            criteria.append("AND df.referenced_bytes <= :referencedBytesMax ");
            parameters.put("referencedBytesMax", filter.getReferencedBytesMax());
        }
        if (filter.getTotalBytesMin() != null) {
            criteria.append("AND df.total_bytes >= :totalBytesMin ");
            parameters.put("totalBytesMin", filter.getTotalBytesMin());
        }
        if (filter.getTotalBytesMax() != null) {
            criteria.append("AND df.total_bytes <= :totalBytesMax ");
            parameters.put("totalBytesMax", filter.getTotalBytesMax());
        }
        if (filter.getEgressed() != null) {
            criteria.append("AND df.egressed = :egressed ");
            parameters.put("egressed", filter.getEgressed());
        }

        if (filter.getAnnotations() != null && !filter.getAnnotations().isEmpty()) {
            for (int i = 0; i < filter.getAnnotations().size(); i++) {
                KeyValue keyValue = filter.getAnnotations().get(i);
                criteria.append("AND EXISTS (SELECT 1 FROM annotations a WHERE a.delta_file_id = df.did ");
                criteria.append("AND a.key = :annotationKey").append(i).append(" ");
                criteria.append("AND a.value = :annotationValue").append(i).append(") ");
                parameters.put("annotationKey" + i, keyValue.getKey());
                parameters.put("annotationValue" + i, keyValue.getValue());
            }
        }

        if (filter.getReplayable() != null) {
            if (filter.getReplayable()) {
                criteria.append("AND df.replayed IS NULL AND df.content_deleted IS NULL ");
            } else {
                criteria.append("AND (df.replayed IS NOT NULL OR df.content_deleted IS NOT NULL) ");
            }
        }

        if (filter.getContentDeleted() != null) {
            if (filter.getContentDeleted()) {
                criteria.append("AND df.content_deleted IS NOT NULL ");
            } else {
                criteria.append("AND df.content_deleted IS NULL ");
            }
        }

        if (filter.getReplayed() != null) {
            if (filter.getReplayed()) {
                criteria.append("AND df.replayed IS NOT NULL ");
            } else {
                criteria.append("AND df.replayed IS NULL ");
            }
        }

        if (filter.getNameFilter() != null) {
            String name = filter.getNameFilter().getName();

            if (filter.getNameFilter().getCaseSensitive() != null && !filter.getNameFilter().getCaseSensitive()) {
                criteria.append("AND LOWER(df.name) LIKE :name ");
                parameters.put("name", "%" + name.toLowerCase() + "%");
            } else {
                criteria.append("AND df.name LIKE :name ");
                parameters.put("name", "%" + name + "%");
            }
        }

        if (filter.getErrorCause() != null) {
            criteria.append("AND EXISTS (SELECT 1 FROM delta_file_flows dff ");
            criteria.append("WHERE dff.delta_file_id = df.did ");
            criteria.append("AND dff.state = 'ERROR' ");
            criteria.append("AND LOWER(dff.error_or_filter_cause) LIKE :errorCause) ");

            parameters.put("errorCause", "%" + filter.getErrorCause().toLowerCase() + "%");
        }

        if (filter.getFilteredCause() != null) {
            criteria.append("AND EXISTS (SELECT 1 FROM delta_file_flows dff ");
            criteria.append("WHERE dff.delta_file_id = df.did ");
            criteria.append("AND dff.state = 'FILTERED' ");
            criteria.append("AND LOWER(dff.error_or_filter_cause) LIKE :filteredCause) ");

            parameters.put("filteredCause", "%" + filter.getFilteredCause().toLowerCase() + "%");
        }

        if (filter.getPendingAnnotations() != null) {
            if (filter.getPendingAnnotations()) {
                criteria.append("AND EXISTS (SELECT 1 FROM delta_file_flows dff ");
                criteria.append("WHERE dff.delta_file_id = df.did ");
                criteria.append("AND jsonb_array_length(dff.pending_annotations) > 0) ");
            } else {
                criteria.append("AND EXISTS (SELECT 1 FROM delta_file_flows dff ");
                criteria.append("WHERE dff.delta_file_id = df.did ");
                criteria.append("AND jsonb_array_length(dff.pending_annotations) = 0) ");
            }
        }

        if (filter.getTestMode() != null) {
            if (filter.getTestMode()) {
                criteria.append("AND EXISTS (SELECT 1 FROM delta_file_flows dff ");
            } else {
                criteria.append("AND NOT EXISTS (SELECT 1 FROM delta_file_flows dff ");
            }
            criteria.append("WHERE dff.delta_file_id = df.did ");
            criteria.append("AND dff.test_mode = true) ");
        }

        if (filter.getActions() != null && !filter.getActions().isEmpty()) {
            for (String actionName : filter.getActions()) {
                criteria.append("AND EXISTS (SELECT 1 FROM delta_file_flows dff ");
                criteria.append("WHERE dff.delta_file_id = df.did ");
                criteria.append("AND EXISTS ( ");
                criteria.append("SELECT 1 FROM jsonb_array_elements(dff.actions) AS action ");
                criteria.append("WHERE action->>'name' = :actionName_");
                criteria.append(actionName.replace(" ", "_"));
                criteria.append(")) ");

                parameters.put("actionName_" + actionName.replace(" ", "_"), actionName);
            }
        }

        if (filter.getErrorAcknowledged() != null) {
            criteria.append("AND EXISTS (SELECT 1 FROM delta_file_flows dff ");
            criteria.append("WHERE dff.delta_file_id = df.did ");
            criteria.append("AND dff.state = 'ERROR' ");
            if (filter.getErrorAcknowledged()) {
                criteria.append("AND dff.error_acknowledged IS NOT NULL) ");
            } else {
                criteria.append("AND dff.error_acknowledged IS NULL) ");
            }
        }

        if (filter.getTransformFlows() != null && !filter.getTransformFlows().isEmpty()) {
            criteria.append("AND EXISTS (SELECT 1 FROM delta_file_flows dff ");
            criteria.append("WHERE dff.delta_file_id = df.did ");
            criteria.append("AND dff.name IN (:transformFlows) ");
            criteria.append("AND dff.type = 'TRANSFORM') ");

            parameters.put("transformFlows", filter.getTransformFlows());
        }

        if (filter.getEgressFlows() != null && !filter.getEgressFlows().isEmpty()) {
            criteria.append("AND EXISTS (SELECT 1 FROM delta_file_flows dff ");
            criteria.append("WHERE dff.delta_file_id = df.did ");
            criteria.append("AND dff.name IN (:egressFlows) ");
            criteria.append("AND dff.type = 'EGRESS') ");

            parameters.put("egressFlows", filter.getEgressFlows());
        }



        return criteria.toString();
    }

    @Override
    @Transactional
    public void setContentDeletedByDidIn(List<UUID> dids, OffsetDateTime now, String reason) {
        if (dids == null || dids.isEmpty()) {
            return;
        }

        jdbcTemplate.execute("DROP TABLE IF EXISTS temp_dids");
        jdbcTemplate.execute("CREATE TEMPORARY TABLE temp_dids (did UUID PRIMARY KEY) ON COMMIT DROP");

        jdbcTemplate.batchUpdate(
                "INSERT INTO temp_dids (did) VALUES (?)",
                dids,
                1000,
                (ps, did) -> ps.setObject(1, did)
        );

        String sql = """
            UPDATE delta_files df
            SET content_deleted = :now,
                content_deleted_reason = :reason,
                content_deletable = false
            FROM temp_dids td
            WHERE df.did = td.did
        """;

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("now", now);
        query.setParameter("reason", reason);
        query.executeUpdate();
    }

    private static final String INSERT_DELTA_FILES = """
            INSERT INTO delta_files (did, name, data_source, parent_dids, join_id, child_dids,
                                     requeue_count, ingress_bytes, referenced_bytes, total_bytes, stage,
                                     created, modified, content_deleted, content_deleted_reason,
                                     egressed, filtered, replayed, replay_did, terminal,
                                     content_deletable, content_object_ids, version)
            VALUES (?, ?, ?, ?::jsonb, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)""";

    private static final String INSERT_DELTA_FILE_FLOWS = """
            INSERT INTO delta_file_flows (id, name, number, type, state, created, modified, flow_plan, input,
                                          publish_topics, depth, pending_annotations, test_mode, test_mode_reason,
                                          join_id, pending_actions, delta_file_id, version, actions,
                                          error_acknowledged, error_acknowledged_reason, cold_queued, error_or_filter_cause,
                                          next_auto_resume)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?::jsonb, ?, ?, ?, ?::jsonb, ?, ?, ?::jsonb, ?, ?, ?, ?, ?)""";

    private static final String INSERT_ANNOTATIONS = """
            INSERT INTO annotations (id, key, value, delta_file_id)
            VALUES (?, ?, ?, ?)""";

    @Override
    @Transactional
    public void batchInsert(List<DeltaFile> deltaFiles) {
        jdbcTemplate.execute(new PreparedStatementCreator() {
            @NotNull
            @Override
            public PreparedStatement createPreparedStatement(@NotNull Connection connection) throws SQLException {
                connection.setAutoCommit(false);
                return connection.prepareStatement("SELECT 1"); // Dummy statement
            }
        }, (PreparedStatementCallback<Void>) ps -> {
            try (PreparedStatement psDeltaFile = ps.getConnection().prepareStatement(INSERT_DELTA_FILES);
                 PreparedStatement psDeltaFileFlow = ps.getConnection().prepareStatement(INSERT_DELTA_FILE_FLOWS);
                 PreparedStatement psAnnotation = ps.getConnection().prepareStatement(INSERT_ANNOTATIONS)) {

                for (DeltaFile deltaFile : deltaFiles) {
                    setDeltaFileParameters(psDeltaFile, deltaFile);
                    psDeltaFile.addBatch();

                    for (DeltaFileFlow flow : deltaFile.getFlows()) {
                        setDeltaFileFlowParameters(psDeltaFileFlow, flow, deltaFile);
                        psDeltaFileFlow.addBatch();
                    }

                    if (!deltaFile.getAnnotations().isEmpty()) {
                        for (Annotation annotation : deltaFile.getAnnotations()) {
                            setAnnotationParameters(psAnnotation, annotation, deltaFile);
                            psAnnotation.addBatch();
                        }
                    }
                }

                psDeltaFile.executeBatch();
                psDeltaFileFlow.executeBatch();
                if (deltaFiles.stream().anyMatch(df -> !df.getAnnotations().isEmpty())) {
                    psAnnotation.executeBatch();
                }

                return null;
            }
        });
    }

    private void setDeltaFileParameters(PreparedStatement ps, DeltaFile deltaFile) throws SQLException {
        ps.setObject(1, deltaFile.getDid());
        ps.setString(2, deltaFile.getName());
        ps.setString(3, deltaFile.getDataSource());
        ps.setString(4, toJson(deltaFile.getParentDids()));
        ps.setObject(5, deltaFile.getJoinId());
        ps.setString(6, toJson(deltaFile.getChildDids()));
        ps.setInt(7, deltaFile.getRequeueCount());
        ps.setLong(8, deltaFile.getIngressBytes());
        ps.setLong(9, deltaFile.getReferencedBytes());
        ps.setLong(10, deltaFile.getTotalBytes());
        ps.setString(11, deltaFile.getStage().name());
        ps.setTimestamp(12, toTimestamp(deltaFile.getCreated()));
        ps.setTimestamp(13, toTimestamp(deltaFile.getModified()));
        ps.setTimestamp(14, toTimestamp(deltaFile.getContentDeleted()));
        ps.setString(15, deltaFile.getContentDeletedReason());
        ps.setObject(16, deltaFile.getEgressed());
        ps.setObject(17, deltaFile.getFiltered());
        ps.setTimestamp(18, toTimestamp(deltaFile.getReplayed()));
        ps.setObject(19, deltaFile.getReplayDid());
        ps.setBoolean(20, deltaFile.isTerminal());
        ps.setBoolean(21, deltaFile.isContentDeletable());
        ps.setString(22, toJson(deltaFile.getContentObjectIds()));
        ps.setLong(23, deltaFile.getVersion());
    }

    private void setDeltaFileFlowParameters(PreparedStatement ps, DeltaFileFlow flow, DeltaFile deltaFile) throws SQLException {
        ps.setObject(1, flow.getId());
        ps.setString(2, flow.getName());
        ps.setInt(3, flow.getNumber());
        ps.setString(4, flow.getType().name());
        ps.setString(5, flow.getState().name());
        ps.setTimestamp(6, toTimestamp(flow.getCreated()));
        ps.setTimestamp(7, toTimestamp(flow.getModified()));
        ps.setString(8, toJson(flow.getFlowPlan()));
        ps.setString(9, toJson(flow.getInput()));
        ps.setString(10, toJson(flow.getPublishTopics()));
        ps.setInt(11, flow.getDepth());
        ps.setString(12, toJson(flow.getPendingAnnotations()));
        ps.setBoolean(13, flow.isTestMode());
        ps.setString(14, flow.getTestModeReason());
        ps.setObject(15, flow.getJoinId());
        ps.setString(16, toJson(flow.getPendingActions()));
        ps.setObject(17, deltaFile.getDid());
        ps.setLong(18, flow.getVersion());
        ps.setString(19, toJson(flow.getActions()));
        ps.setTimestamp(20, toTimestamp(flow.getErrorAcknowledged()));
        ps.setString(21, flow.getErrorAcknowledgedReason());
        ps.setBoolean(22, flow.isColdQueued());
        ps.setString(23, flow.getErrorOrFilterCause());
        ps.setTimestamp(24, toTimestamp(flow.getNextAutoResume()));
    }

    private void setAnnotationParameters(PreparedStatement ps, Annotation annotation, DeltaFile deltaFile) throws SQLException {
        ps.setObject(1, annotation.getId());
        ps.setString(2, annotation.getKey());
        ps.setString(3, annotation.getValue());
        ps.setObject(4, deltaFile.getDid());
    }

    private String toJson(Object object) {
        try {
            return object == null ? null : OBJECT_MAPPER.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private Timestamp toTimestamp(OffsetDateTime offsetDateTime) {
        return offsetDateTime == null ? null : Timestamp.from(offsetDateTime.toInstant());
    }

    @Override
    @Transactional
    public void batchedBulkDeleteByDidIn(List<UUID> dids) {
        if (dids == null || dids.isEmpty()) {
            return;
        }

        String sql = """
        WITH deleted_delta_file_flows AS (
            DELETE FROM delta_file_flows
            WHERE delta_file_id IN (:dids)
        ),
        deleted_annotations AS (
            DELETE FROM annotations
            WHERE delta_file_id IN (:dids)
        ),
        deleted_delta_files AS (
            DELETE FROM delta_files
            WHERE did IN (:dids)
        )
        SELECT 1;
        """;

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("dids", dids);
        query.getSingleResult();
    }
}
