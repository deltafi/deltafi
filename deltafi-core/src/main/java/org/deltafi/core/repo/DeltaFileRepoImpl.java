/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import java.util.regex.Pattern;

@Repository
@RequiredArgsConstructor
@Slf4j
public class DeltaFileRepoImpl implements DeltaFileRepoCustom {
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
    public List<DeltaFile> findForRequeue(OffsetDateTime requeueTime, Duration requeueDuration, Set<String> skipActions, Set<UUID> skipDids, int limit) {
        StringBuilder sqlQuery = new StringBuilder("""
            SELECT DISTINCT did
            FROM delta_file_flows dff
            JOIN delta_files df
            ON dff.delta_file_id = df.did
            WHERE dff.modified < :requeueThreshold
            AND df.modified < :requeueThreshold
            AND dff.state = 'IN_FLIGHT'
            AND df.stage = 'IN_FLIGHT'
            AND dff.cold_queued = false""");

        if (skipActions != null && !skipActions.isEmpty()) {
            sqlQuery.append("\nAND (dff.actions->(jsonb_array_length(dff.actions) - 1))->>'n' NOT IN (:skipActions)");
        }

        if (skipDids != null && !skipDids.isEmpty()) {
            sqlQuery.append("\nAND dff.delta_file_id NOT IN (:skipDids)");
        }

        sqlQuery.append("\nLIMIT :limit");

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
        return fetchByDidIn(didsToRequeue);
    }

    @Override
    @Transactional
    public List<DeltaFile> findColdQueuedForRequeue(List<String> actionNames, int maxFiles) {
        if (actionNames == null || actionNames.isEmpty()) {
            return Collections.emptyList();
        }

        String nativeQueryStr = """
            SELECT DISTINCT delta_file_id
            FROM delta_file_flows dff
            WHERE dff.state = 'IN_FLIGHT'
            AND dff.cold_queued = TRUE
            AND EXISTS (
                SELECT 1
                FROM jsonb_array_elements(dff.actions) AS action
                WHERE action->>'s' = 'COLD_QUEUED'
                AND action->>'n' IN (:actionNames)
            )
            LIMIT :limit
        """;

        Query nativeQuery = entityManager.createNativeQuery(nativeQueryStr, UUID.class)
                .setParameter("actionNames", actionNames)
                .setParameter("limit", maxFiles);

        @SuppressWarnings("unchecked")
        List<UUID> dids = nativeQuery.getResultList();
        return fetchByDidIn(dids);
    }

    @Override
    @Transactional
    public List<DeltaFile> findPausedForRequeue(Set<String> skipRestDataSources, Set<String> skipTimedDataSource,
                                                Set<String> skipTransforms, Set<String> skipDataSinks, int maxFiles) {
        // join to delta_files to check the overall paused flag
        // only resume if all other flows are terminal, causing the top level paused flag to be set
        // else there can be split brain between caches in core workers
        String nativeQueryStr = """
                SELECT DISTINCT dff.delta_file_id
                FROM delta_file_flows dff
                JOIN delta_files df ON dff.delta_file_id = df.did AND df.paused = TRUE
                JOIN flow_definitions fd ON fd.id = dff.flow_definition_id
                WHERE dff.state = 'PAUSED'
                AND (
                   (fd.type = 'REST_DATA_SOURCE' AND (:skipRestDataSources IS NULL OR fd.name NOT IN (:skipRestDataSources)))
                   OR
                   (fd.type = 'TIMED_DATA_SOURCE' AND (:skipTimedDataSources IS NULL OR fd.name NOT IN (:skipTimedDataSources)))
                   OR
                   (fd.type = 'TRANSFORM' AND (:skipTransforms IS NULL OR fd.name NOT IN (:skipTransforms)))
                   OR
                   (fd.type = 'DATA_SINK' AND (:skipDataSinks IS NULL OR fd.name NOT IN (:skipDataSinks)))
                 )
                 LIMIT :limit
            """;

        Query nativeQuery = entityManager.createNativeQuery(nativeQueryStr, UUID.class)
                .setParameter("skipRestDataSources", skipRestDataSources)
                .setParameter("skipTimedDataSources", skipTimedDataSource)
                .setParameter("skipTransforms", skipTransforms)
                .setParameter("skipDataSinks", skipDataSinks)
                .setParameter("limit", maxFiles);

        @SuppressWarnings("unchecked")
        List<UUID> dids = nativeQuery.getResultList();
        return fetchByDidIn(dids);
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
                        WHERE (action->>'nr')::text IS NULL
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
    public List<UUID> findForResumeByFlowTypeAndName(FlowType flowType, String flowName, boolean includeAcknowledged, int limit) {
        String queryBuilder = """
                    SELECT df.did
                    FROM delta_files df
                    WHERE df.stage = 'ERROR'
                    AND df.content_deleted IS NULL
                    AND EXISTS (
                        SELECT 1
                        FROM delta_file_flows flow
                        JOIN flow_definitions fd ON fd.id = flow.flow_definition_id
                        WHERE flow.delta_file_id = df.did
                        AND flow.state = 'ERROR'
                        AND fd.type = CAST(:flowType AS dff_type_enum)
                        AND fd.name = :flowName
                        AND (:includeAcknowledged OR flow.error_acknowledged IS NULL)
                    )
                    LIMIT :limit
                """;
        Query query = entityManager.createNativeQuery(queryBuilder, UUID.class)
                .setParameter("flowType", flowType.name())
                .setParameter("flowName", flowName)
                .setParameter("includeAcknowledged", includeAcknowledged)
                .setParameter("limit", limit);

        @SuppressWarnings("unchecked")
        List<UUID> dids = query.getResultList();
        return dids;
    }

    @Override
    public List<UUID> findForResumeByErrorCause(String errorCause, boolean includeAcknowledged, int limit) {
        String queryBuilder = """
                    SELECT df.did
                    FROM delta_files df
                    WHERE df.stage = 'ERROR'
                    AND df.content_deleted IS NULL
                    AND EXISTS (
                        SELECT 1
                        FROM delta_file_flows flow
                        WHERE flow.delta_file_id = df.did
                        AND flow.state = 'ERROR'
                        AND flow.error_or_filter_cause = :errorCause
                        AND (:includeAcknowledged OR flow.error_acknowledged IS NULL)
                    )
                    LIMIT :limit
                """;
        Query query = entityManager.createNativeQuery(queryBuilder, UUID.class)
                .setParameter("errorCause", errorCause)
                .setParameter("includeAcknowledged", includeAcknowledged)
                .setParameter("limit", limit);

        @SuppressWarnings("unchecked")
        List<UUID> dids = query.getResultList();
        return dids;
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
            long minBytes, String flow, boolean deleteMetadata, boolean includePinned, int batchSize) {
        if (createdBefore == null && completedBefore == null) {
            return Collections.emptyList();
        }

        StringBuilder queryBuilder = new StringBuilder("""
                SELECT df.did, df.content_deleted, df.total_bytes, df.content_object_ids
                FROM delta_files df
                WHERE
                """);

        if (!includePinned) {
            queryBuilder.append(" df.pinned = false AND");
        }

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

        List<UUID> contentObjectIds = row[3] != null
                ? Arrays.asList((UUID[]) row[3])
                : new ArrayList<>();

        return new DeltaFileDeleteDTO(did, contentDeleted, totalBytes, contentObjectIds);
    }

    private static String diskSpaceDeleteQuery(String dataSource) {
        StringBuilder queryBuilder = new StringBuilder("""
            SELECT df.did, df.content_deleted, df.total_bytes, df.content_object_ids
            FROM delta_files df
            WHERE df.content_deletable = true AND df.pinned = false
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
        // TODO: make includeFields work. The dataFetcher parses out these requested graphql fields and includes flow.* and flow.action.*,
        //  which no longer make sense here since we're not fetching the flows and actions are in jsonb
        //  we should either handle this or adjust the object that is returned by the deltaFiles query to not be a full deltaFile
        /* String fields = (includeFields == null || includeFields.isEmpty()) ? "*" : includeFields.stream()
                .map(DeltaFileRepoImpl::toSnakeCase)
                .collect(Collectors.joining(", ")); */
        StringBuilder sqlQuery = new StringBuilder("SELECT * FROM delta_files df WHERE TRUE\n");
        Map<String, Object> parameters = new HashMap<>();
        String criteria = buildDeltaFilesCriteria(parameters, filter);

        sqlQuery.append(criteria);

        if (orderBy != null) {
            sqlQuery.append("ORDER BY df.")
                    .append(toSnakeCase(orderBy.getField()))
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

    private static final Pattern CAMEL_CASE_PATTERN = Pattern.compile("([a-z])([A-Z])");

    private static String toSnakeCase(String str) {
        if (str == null || str.isEmpty()) {
            return "";
        }

        return CAMEL_CASE_PATTERN
                .matcher(str)
                .replaceAll("$1_$2")
                .toLowerCase();
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
            criteria.append("AND df.stage = CAST(:stage as df_stage_enum) ");
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
                criteria.append("AND array_length(dff.pending_annotations, 1) > 0) ");
            } else {
                criteria.append("AND EXISTS (SELECT 1 FROM delta_file_flows dff ");
                criteria.append("WHERE dff.delta_file_id = df.did ");
                criteria.append("AND (dff.pending_annotations IS NULL OR array_length(dff.pending_annotations, 1) IS NULL)) ");
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
                criteria.append("WHERE action->>'n' = :actionName_");
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

        if (filter.getTransforms() != null && !filter.getTransforms().isEmpty()) {
            criteria.append("AND df.transforms && CAST(:transforms AS text[]) ");
            parameters.put("transforms", filter.getTransforms().toArray(new String[0]));
        }

        if (filter.getDataSinks() != null && !filter.getDataSinks().isEmpty()) {
            criteria.append("AND df.data_sinks && CAST(:dataSinks AS text[]) ");
            parameters.put("dataSinks", filter.getDataSinks().toArray(new String[0]));
        }

        if (filter.getTopics() != null && !filter.getTopics().isEmpty()) {
            criteria.append("AND df.topics && CAST(:topics AS text[]) ");
            parameters.put("topics", filter.getTopics().toArray(new String[0]));
        }

        if (filter.getPaused() != null) {
            criteria.append("AND df.paused = :paused ");
            parameters.put("paused", filter.getPaused());
        }

        return criteria.toString();
    }

    @Override
    @Transactional
    public void setContentDeletedByDidIn(List<UUID> dids, OffsetDateTime now, String reason) {
        if (dids == null || dids.isEmpty()) {
            return;
        }

        String sql = """
            UPDATE delta_files
            SET content_deleted = :now,
                content_deleted_reason = :reason,
                content_deletable = false
            WHERE did IN (:dids)
        """;

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("now", now);
        query.setParameter("reason", reason);
        query.setParameter("dids", dids);
        query.executeUpdate();
    }

    private static final String INSERT_DELTA_FILES = """
            INSERT INTO delta_files (did, name, data_source, parent_dids, join_id, child_dids,
                                     requeue_count, ingress_bytes, referenced_bytes, total_bytes, stage,
                                     created, modified, content_deleted, content_deleted_reason,
                                     egressed, filtered, replayed, replay_did, terminal,
                                     content_deletable, content_object_ids, topics, transforms, data_sinks, paused,
                                     waiting_for_children, version)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS df_stage_enum), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""";

    private static final String INSERT_DELTA_FILE_FLOWS = """
            INSERT INTO delta_file_flows (id, flow_definition_id, number, state, created, modified, input,
                                          publish_topics, depth, pending_annotations, test_mode, test_mode_reason,
                                          join_id, pending_actions, delta_file_id, version, actions,
                                          error_acknowledged, error_acknowledged_reason, cold_queued, error_or_filter_cause,
                                          next_auto_resume)
            VALUES (?, ?, ?, CAST(? AS dff_state_enum), ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?)""";

    private static final String INSERT_ANNOTATIONS = """
            INSERT INTO annotations (id, key, value, delta_file_id)
            VALUES (?, ?, ?, ?)""";

    @Override
    @Transactional
    public void insertOne(DeltaFile deltaFile) {
        jdbcTemplate.execute((Connection connection) -> {
            try (PreparedStatement psDeltaFile = connection.prepareStatement(INSERT_DELTA_FILES);
                 PreparedStatement psDeltaFileFlow = connection.prepareStatement(INSERT_DELTA_FILE_FLOWS);
                 PreparedStatement psAnnotation = connection.prepareStatement(INSERT_ANNOTATIONS)) {

                setDeltaFileParameters(psDeltaFile, deltaFile);
                psDeltaFile.executeUpdate();

                for (DeltaFileFlow flow : deltaFile.getFlows()) {
                    setDeltaFileFlowParameters(psDeltaFileFlow, flow, deltaFile);
                    psDeltaFileFlow.addBatch();
                }
                psDeltaFileFlow.executeBatch();

                if (!deltaFile.getAnnotations().isEmpty()) {
                    for (Annotation annotation : deltaFile.getAnnotations()) {
                        setAnnotationParameters(psAnnotation, annotation, deltaFile);
                        psAnnotation.addBatch();
                    }
                    psAnnotation.executeBatch();
                }

                return null;
            } catch (SQLException e) {
                throw new RuntimeException("Error inserting DeltaFile: %s".formatted(e.getMessage()), e);
            }
        });
    }

    @Override
    @Transactional
    public void insertBatch(List<DeltaFile> deltaFiles) {
        if (deltaFiles.size() == 1) {
            insertOne(deltaFiles.getFirst());
            return;
        } else if (deltaFiles.isEmpty()) {
            return;
        }

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

                boolean foundAnnotation = false;

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
                            foundAnnotation = true;
                        }
                    }
                }

                psDeltaFile.executeBatch();
                psDeltaFileFlow.executeBatch();
                if (foundAnnotation) {
                    psAnnotation.executeBatch();
                }

                return null;
            }
        });
    }

    private void setDeltaFileParameters(PreparedStatement ps, DeltaFile deltaFile) throws SQLException {
        Connection conn = ps.getConnection();

        ps.setObject(1, deltaFile.getDid());
        ps.setString(2, deltaFile.getName());
        ps.setString(3, deltaFile.getDataSource());
        ps.setObject(4, conn.createArrayOf("uuid", deltaFile.getParentDids().toArray(new UUID[0])));
        ps.setObject(5, deltaFile.getJoinId());
        ps.setObject(6, conn.createArrayOf("uuid", deltaFile.getChildDids().toArray(new UUID[0])));
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
        ps.setObject(22, conn.createArrayOf("uuid", deltaFile.getContentObjectIds().toArray(new UUID[0])));
        ps.setArray(23, conn.createArrayOf("text", deltaFile.getTopics().toArray(new String[0])));
        ps.setArray(24, conn.createArrayOf("text", deltaFile.getTransforms().toArray(new String[0])));
        ps.setArray(25, conn.createArrayOf("text", deltaFile.getDataSinks().toArray(new String[0])));
        ps.setBoolean(26, deltaFile.getPaused());
        ps.setBoolean(27, deltaFile.getWaitingForChildren());
        ps.setLong(28, deltaFile.getVersion());
    }

    private void setDeltaFileFlowParameters(PreparedStatement ps, DeltaFileFlow flow, DeltaFile deltaFile) throws SQLException {
        Connection conn = ps.getConnection();

        ps.setObject(1, flow.getId());
        ps.setInt(2, flow.getFlowDefinition().getId());
        ps.setInt(3, flow.getNumber());
        ps.setString(4, flow.getState().name());
        ps.setTimestamp(5, toTimestamp(flow.getCreated()));
        ps.setTimestamp(6, toTimestamp(flow.getModified()));
        ps.setString(7, toJson(flow.getInput()));
        ps.setArray(8, conn.createArrayOf("text", flow.getPublishTopics().toArray(new String[0])));
        ps.setInt(9, flow.getDepth());
        ps.setArray(10, conn.createArrayOf("text", flow.getPendingAnnotations().toArray(new String[0])));
        ps.setBoolean(11, flow.isTestMode());
        ps.setString(12, flow.getTestModeReason());
        ps.setObject(13, flow.getJoinId());
        ps.setArray(14, conn.createArrayOf("text", flow.getPendingActions().toArray(new String[0])));
        ps.setObject(15, deltaFile.getDid());
        ps.setLong(16, flow.getVersion());
        ps.setString(17, toJson(flow.getActions()));
        ps.setTimestamp(18, toTimestamp(flow.getErrorAcknowledged()));
        ps.setString(19, flow.getErrorAcknowledgedReason());
        ps.setBoolean(20, flow.isColdQueued());
        ps.setString(21, flow.getErrorOrFilterCause());
        ps.setTimestamp(22, toTimestamp(flow.getNextAutoResume()));
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

    @Override
    @Transactional
    public int completeParents() {
        int batchSize = 1000;
        int totalUpdated = 0;
        boolean hasMore = true;

        while (hasMore) {
            String sql = """
            WITH candidates AS (
                SELECT df.did,
                       NOT EXISTS (
                           SELECT 1
                           FROM delta_file_flows dff
                           WHERE dff.delta_file_id = df.did
                           AND array_length(dff.pending_annotations, 1) > 0
                       ) as can_be_terminal
                FROM delta_files df
                WHERE df.waiting_for_children = true
                AND df.stage = 'COMPLETE'
                AND NOT EXISTS (
                    SELECT 1
                    FROM delta_files child
                    WHERE child.did = ANY(df.child_dids)
                    AND child.terminal = false
                )
                LIMIT :batchSize
            )
            UPDATE delta_files df
            SET waiting_for_children = false,
                terminal = c.can_be_terminal,
                modified = now()
            FROM candidates c
            WHERE df.did = c.did
            """;

            Query query = entityManager.createNativeQuery(sql);
            query.setParameter("batchSize", batchSize);

            int updated = query.executeUpdate();
            totalUpdated += updated;
            hasMore = updated == batchSize;
        }

        return totalUpdated;
    }
}
