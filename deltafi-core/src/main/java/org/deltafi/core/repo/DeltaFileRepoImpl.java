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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.collect.Lists;
import jakarta.persistence.*;
import jakarta.persistence.criteria.*;
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
    public static final String CREATED = "created";
    public static final String STAGE = "stage";
    public static final String NAME = "name";
    public static final String CONTENT_DELETED = "contentDeleted";
    public static final String DATA_SOURCE = "dataSource";
    public static final String EGRESSED = "egressed";
    public static final String FILTERED = "filtered";
    public static final String REFERENCED_BYTES = "referencedBytes";
    public static final String TOTAL_BYTES = "totalBytes";
    public static final String INGRESS_BYTES = "ingressBytes";
    public static final String REPLAYED = "replayed";
    public static final String REQUEUE_COUNT = "requeueCount";
    private static final String DID = "did";
    private static final String FLOWS = "flows";
    private static final String TERMINAL = "terminal";
    public static final String TYPE = "type";

    // a magic number known by the GUI that says there are "many" total results
    private static final int MANY_RESULTS = 10_000;

    @PersistenceContext
    private final EntityManager entityManager;
    private final JdbcTemplate jdbcTemplate;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    @Override
    @Transactional
    public List<DeltaFile> updateForRequeue(OffsetDateTime requeueTime, Duration requeueDuration, Set<String> skipActions, Set<UUID> skipDids, int limit) {
        StringBuilder filesToRequeueQuery = new StringBuilder("""
            SELECT df
            FROM DeltaFile df
            WHERE df.stage = 'IN_FLIGHT'
            AND df.modified < :requeueThreshold
            """);

        if (skipDids != null && !skipDids.isEmpty()) {
            filesToRequeueQuery.append("AND df.did NOT IN :skipDids\n");
        }

        filesToRequeueQuery.append("""
            AND EXISTS (
                SELECT action
                FROM df.flows flow
                JOIN flow.actions action
                WHERE flow.state = 'IN_FLIGHT'
                AND action.modified < :requeueThreshold
                AND action.state IN ('QUEUED', 'COLD_QUEUED')
            """);

        if (skipActions != null && !skipActions.isEmpty()) {
            filesToRequeueQuery.append("AND action.name NOT IN :skipActions\n");
        }

        filesToRequeueQuery.append(") ORDER BY df.modified ASC LIMIT :limit");

        OffsetDateTime requeueThreshold = requeueTime.minus(requeueDuration);
        TypedQuery<DeltaFile> typedQuery = entityManager.createQuery(filesToRequeueQuery.toString(), DeltaFile.class)
                .setParameter("requeueThreshold", requeueThreshold)
                .setParameter("limit", limit);

        if (skipDids != null && !skipDids.isEmpty()) {
            typedQuery.setParameter("skipDids", skipDids);
        }
        if (skipActions != null && !skipActions.isEmpty()) {
            typedQuery.setParameter("skipActions", skipActions);
        }
        List<DeltaFile> filesToRequeue = typedQuery.getResultList();

        if (filesToRequeue.isEmpty()) {
            return filesToRequeue;
        }

        filesToRequeue.forEach(deltaFile -> {
            deltaFile.setRequeueCount(deltaFile.getRequeueCount() + 1);
            deltaFile.setModified(requeueTime);
        });

        filesToRequeue.stream()
                .flatMap(d -> d.getFlows().stream())
                .flatMap(f -> f.getActions().stream())
                .filter(a -> (a.getState() == QUEUED || a.getState() == COLD_QUEUED) &&
                        a.getModified().isBefore(requeueThreshold) && (skipActions == null || !skipActions.contains(a.getName())))
                .forEach(action -> {
                    action.setState(QUEUED);
                    action.setModified(requeueTime);
                    action.setQueued(requeueTime);
                    action.getDeltaFileFlow().updateState(requeueTime);
                });

        return filesToRequeue;
    }

    @Override
    @Transactional
    public List<DeltaFile> updateColdQueuedForRequeue(List<String> actionNames, int maxFiles, OffsetDateTime modified) {
        List<DeltaFile> filesToRequeue = entityManager.createQuery("""
                SELECT df
                FROM DeltaFile df
                JOIN df.flows flow
                JOIN flow.actions action
                WHERE df.stage = 'IN_FLIGHT'
                AND EXISTS (
                  SELECT action
                  FROM df.flows flow
                  JOIN flow.actions action
                  WHERE  action.state = 'COLD_QUEUED'
                  AND action.name IN :actionNames
                )
                ORDER BY df.modified ASC
                LIMIT :limit
                """, DeltaFile.class)
                .setParameter("actionNames", actionNames)
                .setParameter("limit", maxFiles)
                .getResultList();

        if (filesToRequeue.isEmpty()) {
            return filesToRequeue;
        }

        filesToRequeue.forEach(deltaFile -> {
            deltaFile.setRequeueCount(deltaFile.getRequeueCount() + 1);
            deltaFile.setModified(modified);
        });

        filesToRequeue.stream()
                .flatMap(d -> d.getFlows().stream())
                .flatMap(f -> f.getActions().stream())
                .filter(a -> a.getState() == COLD_QUEUED && actionNames.contains(a.getName()))
                .forEach(action -> {
                    action.setState(QUEUED);
                    action.setModified(modified);
                    action.setQueued(modified);
                    action.getDeltaFileFlow().updateState(modified);
                });

        return filesToRequeue;
    }

    @Override
    public List<DeltaFile> findReadyForAutoResume(OffsetDateTime maxReadyTime) {
        String queryStr = """
            SELECT DISTINCT df
            FROM Action action
            JOIN action.deltaFileFlow flow
            JOIN flow.deltaFile df
            WHERE action.nextAutoResume < :maxReadyTime
            AND df.stage = 'ERROR'
            AND df.contentDeleted IS NULL
        """;

        TypedQuery<DeltaFile> query = entityManager.createQuery(queryStr, DeltaFile.class)
                .setParameter("maxReadyTime", maxReadyTime);

        return query.getResultList();
    }


    @Override
    public List<DeltaFile> findResumePolicyCandidates(String dataSource) {
        StringBuilder queryBuilder = new StringBuilder("""
                SELECT df
                FROM DeltaFile df
                WHERE df.stage = 'ERROR'
                AND df.contentDeleted IS NULL
                AND EXISTS (
                    SELECT 1
                    FROM DeltaFileFlow flow
                    JOIN flow.actions action
                    WHERE flow.deltaFile = df
                    AND action.nextAutoResume IS NULL
                    AND action.errorAcknowledged IS NULL
                )
            """);

        if (dataSource != null) {
            queryBuilder.append("AND df.dataSource = :dataSource ");
        }

        TypedQuery<DeltaFile> query = entityManager.createQuery(queryBuilder.toString(), DeltaFile.class);

        if (dataSource != null) {
            query.setParameter("dataSource", dataSource);
        }

        return query.getResultList();
    }

    @Override
    @Transactional
    public void updateForAutoResume(List<UUID> dids, String policyName, OffsetDateTime nextAutoResume) {
        if (dids == null || dids.isEmpty()) {
            return;
        }

        for (List<UUID> batch : Lists.partition(dids, 500)) {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaUpdate<Action> update = cb.createCriteriaUpdate(Action.class);
            Root<Action> root = update.from(Action.class);

            update.set("nextAutoResume", nextAutoResume)
                    .set("nextAutoResumeReason", policyName)
                    .where(cb.and(
                            root.get("deltaFileFlow").get("deltaFile").get("did").in(batch),
                            cb.equal(root.get("state"), ERROR)
                    ));

            entityManager.createQuery(update).executeUpdate();
        }
    }

    @Override
    public List<DeltaFileDeleteDTO> findForTimedDelete(OffsetDateTime createdBeforeDate, OffsetDateTime completedBeforeDate,
                                                       long minBytes, String flow, boolean deleteMetadata, int batchSize) {
        if (createdBeforeDate == null && completedBeforeDate == null) {
            return Collections.emptyList();
        }

        StringBuilder queryBuilder = new StringBuilder(
                "WITH eligible_files AS ( " +
                        "    SELECT df.did, df.content_deleted, df.total_bytes, df.modified " +
                        "    FROM delta_files df " +
                        "    WHERE ");

        List<String> conditions = new ArrayList<>();
        Map<String, Object> parameters = new HashMap<>();

        if (createdBeforeDate != null) {
            conditions.add("df.created < :createdBeforeDate");
            parameters.put("createdBeforeDate", createdBeforeDate);
        }

        if (completedBeforeDate != null) {
            conditions.add("df.modified < :completedBeforeDate");
            conditions.add("df.terminal = true");
            parameters.put("completedBeforeDate", completedBeforeDate);
        }

        if (flow != null) {
            conditions.add("df.data_source = :flow");
            parameters.put("flow", flow);
        }

        if (minBytes > 0L) {
            conditions.add("df.total_bytes >= :minBytes");
            parameters.put("minBytes", minBytes);
        }

        if (!deleteMetadata) {
            conditions.add("df.content_deletable = true");
        }

        queryBuilder.append(String.join(" AND ", conditions));
        queryBuilder.append(" ORDER BY ");
        queryBuilder.append(createdBeforeDate != null ? "df.created" : "df.modified");
        queryBuilder.append(" ASC LIMIT :batchSize ) ");

        queryBuilder.append(
                "SELECT ef.did, ef.content_deleted, ef.total_bytes, " +
                        "       array_agg(a.content) as content_list " +
                        "FROM eligible_files ef " +
                        "LEFT JOIN delta_file_flows f ON ef.did = f.delta_file_id " +
                        "LEFT JOIN actions a ON f.id = a.delta_file_flow_id " +
                        "GROUP BY ef.did, ef.content_deleted, ef.total_bytes, ef.modified " +
                        "ORDER BY ef.modified " +
                        "LIMIT :batchSize");

        Query query = entityManager.createNativeQuery(queryBuilder.toString());

        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            query.setParameter(entry.getKey(), entry.getValue());
        }
        query.setParameter("batchSize", batchSize);

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
        List<Content> contentList = new ArrayList<>();
        if (row[3] instanceof String[]) {
            for (String content : (String[]) row[3]) {
                if (content != null) {
                    try {
                        contentList.addAll(OBJECT_MAPPER.readValue(content, new TypeReference<>() {}));
                    } catch (JsonProcessingException ignored) {
                    }
                }
            }
        }
        return new DeltaFileDeleteDTO(did, contentDeleted, totalBytes, contentList);
    }

    private static String diskSpaceDeleteQuery(String dataSource) {
        StringBuilder queryBuilder = new StringBuilder("""
        WITH eligible_files AS (
            SELECT df.did, df.content_deleted, df.total_bytes, df.modified
            FROM delta_files df
            WHERE df.content_deletable = true
        """);

        if (dataSource != null) {
            queryBuilder.append("AND df.data_source = :dataSource ");
        }

        queryBuilder.append("""
            ORDER BY df.modified ASC
            LIMIT :batchSize
        )
        SELECT ef.did, ef.content_deleted, ef.total_bytes, 
               array_agg(a.content) as content_list
        FROM eligible_files ef
        LEFT JOIN delta_file_flows f ON ef.did = f.delta_file_id
        LEFT JOIN actions a ON f.id = a.delta_file_flow_id
        GROUP BY ef.did, ef.content_deleted, ef.total_bytes, ef.modified
        ORDER BY ef.modified
    """);

        return queryBuilder.toString();
    }

    @Override
    public DeltaFiles deltaFiles(Integer offset, int limit, DeltaFilesFilter filter, DeltaFileOrder orderBy, List<String> includeFields) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<DeltaFile> query = cb.createQuery(DeltaFile.class);
        Root<DeltaFile> root = query.from(DeltaFile.class);

        List<Predicate> predicates = buildDeltaFilesCriteria(cb, root, filter);
        query.where(cb.and(predicates.toArray(new Predicate[0])));

        if (orderBy == null) {
            orderBy = new DeltaFileOrder();
            orderBy.setField("modified");
            orderBy.setDirection(DeltaFileDirection.DESC);
        }

        query.orderBy(orderBy.getDirection() == DeltaFileDirection.ASC ?
                cb.asc(root.get(orderBy.getField())) :
                cb.desc(root.get(orderBy.getField())));

        TypedQuery<DeltaFile> typedQuery = entityManager.createQuery(query);

        if (offset != null && offset > 0) {
            typedQuery.setFirstResult(offset);
        } else {
            offset = 0;
        }

        typedQuery.setMaxResults(limit);

        List<DeltaFile> deltaFileList = typedQuery.getResultList();

        DeltaFiles deltaFiles = new DeltaFiles();
        deltaFiles.setOffset(offset);
        deltaFiles.setDeltaFiles(deltaFileList);
        deltaFiles.setCount(deltaFileList.size());

        if (deltaFileList.size() < limit) {
            deltaFiles.setTotalCount(offset + deltaFileList.size());
        } else {
            // this makes me sad. JPA does not support limiting a subquery, so we can't do something like
            // SELECT COUNT(*) FROM (SELECT 1 FROM delta_files WHERE /* criteria */ LIMIT /* limit */)
            // building the criteria string manually would be hairy
            // so return an array of 1s -- a bunch of unfortunately wasted bytes over the wire, and count them
            CriteriaBuilder countCb = entityManager.getCriteriaBuilder();
            CriteriaQuery<Integer> criteriaQuery = countCb.createQuery(Integer.class);
            Root<DeltaFile> countRoot = criteriaQuery.from(DeltaFile.class);

            List<Predicate> countPredicates = buildDeltaFilesCriteria(countCb, countRoot, filter);
            criteriaQuery.select(countCb.literal(1))
                    .where(countCb.and(countPredicates.toArray(new Predicate[0])));

            TypedQuery<Integer> countQuery = entityManager.createQuery(criteriaQuery)
                    .setMaxResults(MANY_RESULTS);

            deltaFiles.setTotalCount(countQuery.getResultList().size());
        }

        return deltaFiles;
    }

    private List<Predicate> buildDeltaFilesCriteria(CriteriaBuilder cb, Root<DeltaFile> root, DeltaFilesFilter filter) {
        List<Predicate> predicates = new ArrayList<>();

        if (filter == null) {
            return predicates;
        }

        if (filter.getDataSources() != null && !filter.getDataSources().isEmpty()) {
            predicates.add(root.get(DATA_SOURCE).in(filter.getDataSources()));
        }

        if (filter.getTransformFlows() != null && !filter.getTransformFlows().isEmpty()) {
            Join<DeltaFile, DeltaFileFlow> flowJoin = root.join(FLOWS);
            predicates.add(cb.and(flowJoin.get(NAME).in(filter.getTransformFlows()), cb.equal(flowJoin.get(TYPE), FlowType.TRANSFORM)));
        }

        if (filter.getEgressFlows() != null && !filter.getEgressFlows().isEmpty()) {
            Subquery<Long> subquery = cb.createQuery().subquery(Long.class);
            Root<DeltaFile> subRoot = subquery.from(DeltaFile.class);
            Join<DeltaFile, DeltaFileFlow> subFlowJoin = subRoot.join("flows");

            subquery.select(cb.literal(1L)).where(
                    cb.equal(subRoot.get("did"), root.get("did")),
                    subFlowJoin.get("name").in(filter.getEgressFlows())
            );

            predicates.add(cb.exists(subquery));
        }

        if (filter.getDids() != null && !filter.getDids().isEmpty()) {
            predicates.add(root.get(DID).in(filter.getDids()));
        }

        if (filter.getParentDid() != null) {
            predicates.add(cb.isMember(filter.getParentDid(), root.get("parentDids")));
        }

        if (filter.getCreatedAfter() != null) {
            predicates.add(cb.greaterThan(root.get(CREATED), filter.getCreatedAfter()));
        }

        if (filter.getCreatedBefore() != null) {
            predicates.add(cb.lessThan(root.get(CREATED), filter.getCreatedBefore()));
        }

        if (filter.getAnnotations() != null && !filter.getAnnotations().isEmpty()) {
            for (KeyValue keyValue : filter.getAnnotations()) {
                Subquery<Long> subquery = cb.createQuery().subquery(Long.class);
                Root<DeltaFile> subRoot = subquery.from(DeltaFile.class);
                Join<DeltaFile, Annotation> subAnnotationJoin = subRoot.join("annotations");

                subquery.select(cb.literal(1L)).where(
                        cb.equal(subRoot.get("did"), root.get("did")),
                        cb.equal(subAnnotationJoin.get("key"), keyValue.getKey()),
                        cb.equal(subAnnotationJoin.get("value"), keyValue.getValue())
                );

                predicates.add(cb.exists(subquery));
            }
        }

        if (filter.getModifiedAfter() != null) {
            predicates.add(cb.greaterThan(root.get("modified"), filter.getModifiedAfter()));
        }

        if (filter.getModifiedBefore() != null) {
            predicates.add(cb.lessThan(root.get("modified"), filter.getModifiedBefore()));
        }

        if (filter.getTerminalStage() != null) {
            predicates.add(cb.equal(root.get(TERMINAL), filter.getTerminalStage()));
        }

        if (filter.getStage() != null) {
            predicates.add(cb.equal(root.get(STAGE), filter.getStage().name()));
        }

        if (filter.getNameFilter() != null) {
            addNameCriteria(cb, root, filter.getNameFilter(), predicates);
        }

        if (filter.getActions() != null && !filter.getActions().isEmpty()) {
            for (String actionName : filter.getActions()) {
                Subquery<Long> subquery = cb.createQuery().subquery(Long.class);
                Root<DeltaFile> subRoot = subquery.from(DeltaFile.class);
                Join<DeltaFile, DeltaFileFlow> subFlowJoin = subRoot.join("flows");
                Join<DeltaFileFlow, Action> subActionJoin = subFlowJoin.join("actions");

                subquery.select(cb.literal(1L)).where(
                        cb.equal(subRoot.get("did"), root.get("did")),
                        cb.equal(subActionJoin.get("name"), actionName)
                );

                predicates.add(cb.exists(subquery));
            }
        }

        if (filter.getPendingAnnotations() != null) {
            Subquery<Long> subquery = cb.createQuery().subquery(Long.class);
            Root<DeltaFile> subRoot = subquery.from(DeltaFile.class);
            Join<DeltaFile, DeltaFileFlow> subFlowJoin = subRoot.join("flows");

            if (filter.getPendingAnnotations()) {
                subquery.select(cb.literal(1L)).where(
                        cb.equal(subRoot.get("did"), root.get("did")),
                        cb.greaterThan(cb.function("jsonb_array_length", Integer.class, subFlowJoin.get("pendingAnnotations")), 0));
            } else {
                subquery.select(cb.literal(1L)).where(
                        cb.equal(subRoot.get("did"), root.get("did")),
                        cb.equal(cb.function("jsonb_array_length", Integer.class, subFlowJoin.get("pendingAnnotations")), 0));
            }

            predicates.add(cb.exists(subquery));
        }

        if (filter.getErrorCause() != null) {
            Subquery<Long> subquery = cb.createQuery().subquery(Long.class);
            Root<DeltaFile> subRoot = subquery.from(DeltaFile.class);
            Join<DeltaFile, DeltaFileFlow> subFlowJoin = subRoot.join("flows");
            Join<DeltaFileFlow, Action> subActionJoin = subFlowJoin.join("actions");

            subquery.select(cb.literal(1L)).where(
                    cb.equal(subRoot.get("did"), root.get("did")),
                    cb.equal(subActionJoin.get("state"), "ERROR"),
                    cb.like(cb.lower(subActionJoin.get("errorCause")), "%" + filter.getErrorCause().toLowerCase() + "%")
            );
            predicates.add(cb.exists(subquery));
        }

        if (filter.getFilteredCause() != null) {
            Subquery<Long> subquery = cb.createQuery().subquery(Long.class);
            Root<DeltaFile> subRoot = subquery.from(DeltaFile.class);
            Join<DeltaFile, DeltaFileFlow> subFlowJoin = subRoot.join("flows");
            Join<DeltaFileFlow, Action> subActionJoin = subFlowJoin.join("actions");

            subquery.select(cb.literal(1L)).where(
                    cb.equal(subRoot.get("did"), root.get("did")),
                    cb.equal(subActionJoin.get("state"), "FILTERED"),
                    cb.like(cb.lower(subActionJoin.get("filteredCause")), "%" + filter.getFilteredCause().toLowerCase() + "%")
            );
            predicates.add(cb.exists(subquery));
        }

        if (filter.getErrorAcknowledged() != null) {
            Subquery<Long> subquery = cb.createQuery().subquery(Long.class);
            Root<DeltaFile> subRoot = subquery.from(DeltaFile.class);
            Join<DeltaFile, DeltaFileFlow> subFlowJoin = subRoot.join("flows");
            Join<DeltaFileFlow, Action> subActionJoin = subFlowJoin.join("actions");

            if (filter.getErrorAcknowledged()) {
                subquery.select(cb.literal(1L)).where(
                        cb.equal(subRoot.get("did"), root.get("did")),
                        cb.equal(subActionJoin.get("state"), "ERROR"),
                        cb.isNotNull((subActionJoin.get("errorAcknowledged"))));

                predicates.add(cb.exists(subquery));
            } else {
                subquery.select(cb.literal(1L)).where(
                        cb.equal(subRoot.get("did"), root.get("did")),
                        cb.equal(subActionJoin.get("state"), "ERROR"),
                        cb.isNull((subActionJoin.get("errorAcknowledged"))));
            }

            predicates.add(cb.exists(subquery));
        }

        if (filter.getFiltered() != null) {
            predicates.add(cb.equal(root.get("filtered"), filter.getFiltered()));
        }

        if (filter.getRequeueCountMin() != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get(REQUEUE_COUNT), filter.getRequeueCountMin()));
        }

        if (filter.getIngressBytesMin() != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get(INGRESS_BYTES), filter.getIngressBytesMin()));
        }

        if (filter.getIngressBytesMax() != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get(INGRESS_BYTES), filter.getIngressBytesMax()));
        }

        if (filter.getReferencedBytesMin() != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get(REFERENCED_BYTES), filter.getReferencedBytesMin()));
        }

        if (filter.getReferencedBytesMax() != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get(REFERENCED_BYTES), filter.getReferencedBytesMax()));
        }

        if (filter.getTotalBytesMin() != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get(TOTAL_BYTES), filter.getTotalBytesMin()));
        }

        if (filter.getTotalBytesMax() != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get(TOTAL_BYTES), filter.getTotalBytesMax()));
        }

        if (filter.getEgressed() != null) {
            predicates.add(cb.equal(root.get(EGRESSED), filter.getEgressed()));
        }

        if (filter.getTestMode() != null) {
            Subquery<Long> subquery = cb.createQuery().subquery(Long.class);
            Root<DeltaFile> subRoot = subquery.from(DeltaFile.class);
            Join<DeltaFile, DeltaFileFlow> subFlowJoin = subRoot.join("flows");

            subquery.select(cb.literal(1L)).where(
                    cb.equal(subRoot.get("did"), root.get("did")),
                    cb.equal(subFlowJoin.get("testMode"), true));

            if (filter.getTestMode()) {
                predicates.add(cb.exists(subquery));
            } else {
                predicates.add(cb.not(cb.exists(subquery)));
            }
        }

        if (filter.getReplayable() != null) {
            if (filter.getReplayable()) {
                predicates.add(cb.isNull(root.get(REPLAYED)));
                predicates.add(cb.isNull(root.get(CONTENT_DELETED)));
            } else {
                predicates.add(cb.or(cb.isNotNull(root.get(REPLAYED)), cb.isNotNull(root.get(CONTENT_DELETED))));
            }
        }

        if (filter.getContentDeleted() != null) {
            if (filter.getContentDeleted()) {
                predicates.add(cb.isNotNull(root.get(CONTENT_DELETED)));
            } else {
                predicates.add(cb.isNull(root.get(CONTENT_DELETED)));
            }
        }

        if (filter.getReplayed() != null) {
            if (filter.getReplayed()) {
                predicates.add(cb.isNotNull(root.get(REPLAYED)));
            } else {
                predicates.add(cb.isNull(root.get(REPLAYED)));
            }
        }

        return predicates;
    }

    private void addNameCriteria(CriteriaBuilder cb, Root<DeltaFile> root, NameFilter nameFilter, List<Predicate> predicates) {
        String name = nameFilter.getName();
        if (nameFilter.getCaseSensitive() != null && !nameFilter.getCaseSensitive()) {
            name = name.toLowerCase();
            predicates.add(cb.like(cb.lower(root.get("name")), "%" + name + "%"));
        } else {
            predicates.add(cb.like(root.get("name"), "%" + name + "%"));
        }
    }

    @Override
    @Transactional
    public void setContentDeletedByDidIn(List<UUID> dids, OffsetDateTime now, String reason) {
        if (dids == null || dids.isEmpty()) {
            return;
        }

        for (List<UUID> batch : Lists.partition(dids, 500)) {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaUpdate<DeltaFile> update = cb.createCriteriaUpdate(DeltaFile.class);
            Root<DeltaFile> root = update.from(DeltaFile.class);

            update.set("contentDeleted", now)
                    .set("contentDeletedReason", reason)
                    .set("contentDeletable", false)
                    .where(root.get("did").in(batch));

            entityManager.createQuery(update).executeUpdate();
        }
    }

    private static final String INSERT_DELTA_FILES = """
            INSERT INTO delta_files (did, name, normalized_name, data_source, parent_dids, join_id, child_dids,
                                     requeue_count, ingress_bytes, referenced_bytes, total_bytes, stage,
                                     created, modified, content_deleted, content_deleted_reason,
                                     egressed, filtered, replayed, replay_did, terminal,
                                     content_deletable, version)
            VALUES (?, ?, ?, ?, ?::jsonb, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""";

    private static final String INSERT_DELTA_FILE_FLOWS = """
            INSERT INTO delta_file_flows (id, name, number, type, state, created, modified, flow_plan, input,
                                          publish_topics, depth, pending_annotations, test_mode, test_mode_reason,
                                          join_id, pending_actions, delta_file_id, version)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?::jsonb, ?, ?, ?, ?::jsonb, ?, ?)""";

    private static final String INSERT_ACTIONS = """
            INSERT INTO actions (id, name, number, type, state, created, queued, start, stop, modified, error_cause,
                                 error_context, error_acknowledged, error_acknowledged_reason, next_auto_resume,
                                 next_auto_resume_reason, filtered_cause, filtered_context, attempt, content,
                                 metadata, delete_metadata_keys, replay_start, delta_file_flow_id, version)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?, ?)""";

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
                 PreparedStatement psAction = ps.getConnection().prepareStatement(INSERT_ACTIONS);
                 PreparedStatement psAnnotation = ps.getConnection().prepareStatement(INSERT_ANNOTATIONS)) {

                for (DeltaFile deltaFile : deltaFiles) {
                    setDeltaFileParameters(psDeltaFile, deltaFile);
                    psDeltaFile.addBatch();

                    for (DeltaFileFlow flow : deltaFile.getFlows()) {
                        setDeltaFileFlowParameters(psDeltaFileFlow, flow, deltaFile);
                        psDeltaFileFlow.addBatch();

                        for (Action action : flow.getActions()) {
                            setActionParameters(psAction, action, flow);
                            psAction.addBatch();
                        }
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
                psAction.executeBatch();
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
        ps.setString(3, deltaFile.getNormalizedName());
        ps.setString(4, deltaFile.getDataSource());
        ps.setString(5, toJson(deltaFile.getParentDids()));
        ps.setObject(6, deltaFile.getJoinId());
        ps.setString(7, toJson(deltaFile.getChildDids()));
        ps.setInt(8, deltaFile.getRequeueCount());
        ps.setLong(9, deltaFile.getIngressBytes());
        ps.setLong(10, deltaFile.getReferencedBytes());
        ps.setLong(11, deltaFile.getTotalBytes());
        ps.setString(12, deltaFile.getStage().name());
        ps.setTimestamp(13, toTimestamp(deltaFile.getCreated()));
        ps.setTimestamp(14, toTimestamp(deltaFile.getModified()));
        ps.setTimestamp(15, toTimestamp(deltaFile.getContentDeleted()));
        ps.setString(16, deltaFile.getContentDeletedReason());
        ps.setObject(17, deltaFile.getEgressed());
        ps.setObject(18, deltaFile.getFiltered());
        ps.setTimestamp(19, toTimestamp(deltaFile.getReplayed()));
        ps.setObject(20, deltaFile.getReplayDid());
        ps.setBoolean(21, deltaFile.isTerminal());
        ps.setBoolean(22, deltaFile.isContentDeletable());
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
        ps.setLong(18, deltaFile.getVersion());
    }

    private void setActionParameters(PreparedStatement ps, Action action, DeltaFileFlow flow) throws SQLException {
        ps.setObject(1, action.getId());
        ps.setString(2, action.getName());
        ps.setInt(3, action.getNumber());
        ps.setString(4, action.getType().name());
        ps.setString(5, action.getState().name());
        ps.setTimestamp(6, toTimestamp(action.getCreated()));
        ps.setTimestamp(7, toTimestamp(action.getQueued()));
        ps.setTimestamp(8, toTimestamp(action.getStart()));
        ps.setTimestamp(9, toTimestamp(action.getStop()));
        ps.setTimestamp(10, toTimestamp(action.getModified()));
        ps.setString(11, action.getErrorCause());
        ps.setString(12, action.getErrorContext());
        ps.setTimestamp(13, toTimestamp(action.getErrorAcknowledged()));
        ps.setString(14, action.getErrorAcknowledgedReason());
        ps.setTimestamp(15, toTimestamp(action.getNextAutoResume()));
        ps.setString(16, action.getNextAutoResumeReason());
        ps.setString(17, action.getFilteredCause());
        ps.setString(18, action.getFilteredContext());
        ps.setInt(19, action.getAttempt());
        ps.setString(20, toJson(action.getContent()));
        ps.setString(21, toJson(action.getMetadata()));
        ps.setString(22, toJson(action.getDeleteMetadataKeys()));
        ps.setBoolean(23, action.isReplayStart());
        ps.setObject(24, flow.getId());
        ps.setLong(25, flow.getVersion());
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

    @Transactional
    public void batchedBulkDeleteByDidIn(List<UUID> dids) {
        if (dids == null || dids.isEmpty()) {
            return;
        }

        String sql = """
        WITH deleted_delta_file_flows AS (
            DELETE FROM delta_file_flows
            WHERE delta_file_id IN (:dids)
            RETURNING id
        )
        DELETE FROM actions
        WHERE delta_file_flow_id IN (
            SELECT id FROM deleted_delta_file_flows
        );

        DELETE FROM annotations
        WHERE delta_file_id IN (:dids);

        DELETE FROM delta_files
        WHERE did IN (:dids);

    """;

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("dids", dids);
        query.executeUpdate();

        entityManager.flush();
        entityManager.clear();
    }
}
