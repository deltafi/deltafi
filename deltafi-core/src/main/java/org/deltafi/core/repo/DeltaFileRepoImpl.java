/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
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
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.collect.Lists;
import com.mongodb.client.result.UpdateResult;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.types.*;
import org.deltafi.core.generated.types.*;
import org.deltafi.core.types.*;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.UncategorizedMongoDbException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.deltafi.common.types.ActionState.*;

@Repository
@RequiredArgsConstructor
@Slf4j
@Transactional
public class DeltaFileRepoImpl implements DeltaFileRepoCustom {
    public static final String ID = "_id";
    public static final String VERSION = "version";
    public static final String MODIFIED = "modified";
    public static final String CREATED = "created";
    public static final String STAGE = "stage";
    public static final String NAME = "name";
    public static final String CONTENT_DELETED = "contentDeleted";
    public static final String DATA_SOURCE = "dataSource";
    public static final String ERROR_ACKNOWLEDGED = "errorAcknowledged";
    public static final String EGRESSED = "egressed";
    public static final String EGRESS_FLOWS = "egressFlows";
    public static final String FILTERED = "filtered";
    public static final String PENDING_ANNOTATIONS = "flows.pendingAnnotations";
    public static final String FIRST_PENDING_ANNOTATIONS = "flows.pendingAnnotations.0";
    public static final String TEST_MODE = "flows.testMode";
    public static final String REFERENCED_BYTES = "referencedBytes";
    public static final String TOTAL_BYTES = "totalBytes";
    public static final String INGRESS_BYTES = "ingressBytes";
    public static final String REPLAYED = "replayed";
    public static final String REQUEUE_COUNT = "requeueCount";
    public static final String NEXT_AUTO_RESUME = "nextAutoResume";
    public static final String NORMALIZED_NAME = "normalizedName";
    public static final String FLOWS_INPUT_METADATA = "flows.input.metadata";
    public static final String ACTIONS_NAME = "flows.actions.name";
    public static final String ACTIONS_STATE = "flows.actions.state";
    private static final String COLLECTION = "deltaFiles";
    private static final String TTL_INDEX_NAME = "ttl_index";
    private static final String DID = "did";
    private static final String FLOWS = "flows";
    public static final String ANNOTATIONS = "annotations";
    public static final String ANNOTATION_KEYS = "annotationKeys";
    private static final String IN_FLIGHT = "inFlight";
    private static final String TERMINAL = "terminal";
    private static final String CONTENT_DELETABLE = "contentDeletable";
    private static final String CREATED_BEFORE_INDEX = "created_before_index";
    private static final String TERMINAL_INDEX = "terminal_index";
    public static final String TYPE = "type";

    private static final Map<String, Index> INDICES;
    static {
        INDICES = new HashMap<>();
        INDICES.put(CREATED_BEFORE_INDEX, new Index().named(CREATED_BEFORE_INDEX).on(CREATED, Sort.Direction.ASC).on(DATA_SOURCE, Sort.Direction.ASC));
        INDICES.put("modified_before_index", new Index().named("modified_before_index").on(MODIFIED, Sort.Direction.ASC).on(DATA_SOURCE, Sort.Direction.ASC));
        INDICES.put("auto_resume_index", new Index().named("auto_resume_index").on(NEXT_AUTO_RESUME, Sort.Direction.ASC).on(STAGE, Sort.Direction.ASC));
        INDICES.put("flow_first_index", new Index().named("flow_first_index").on(DATA_SOURCE, Sort.Direction.ASC).on(NORMALIZED_NAME, Sort.Direction.ASC).on(MODIFIED, Sort.Direction.ASC));
        INDICES.put("metadata_index", new Index().named("metadata_index").on(ANNOTATIONS + ".$**", Sort.Direction.ASC));
        INDICES.put("metadata_keys_index", new Index().named("metadata_keys_index").on(ANNOTATION_KEYS, Sort.Direction.ASC).sparse());
        INDICES.put("pending_annotations_index", new Index().named("pending_annotations_index").on(PENDING_ANNOTATIONS, Sort.Direction.ASC).sparse());
        INDICES.put("egress_flow_index", new Index().named("egress_flow_index").on(EGRESS_FLOWS, Sort.Direction.ASC).on(MODIFIED, Sort.Direction.ASC));
        INDICES.put("ingress_bytes_index", new Index().named("ingress_bytes_index").on(INGRESS_BYTES, Sort.Direction.ASC).on(MODIFIED, Sort.Direction.ASC));
        INDICES.put("in_flight_index", new Index().named("in_flight_index").on(IN_FLIGHT, Sort.Direction.ASC).on(MODIFIED, Sort.Direction.ASC).partial(PartialIndexFilter.of(Criteria.where(IN_FLIGHT).is(true))));
        INDICES.put(TERMINAL_INDEX, new Index().named(TERMINAL_INDEX).on(TERMINAL, Sort.Direction.ASC).on(DATA_SOURCE, Sort.Direction.ASC).on(MODIFIED, Sort.Direction.ASC));
        INDICES.put("content_deletable_index", new Index().named("content_deletable_index").on(CONTENT_DELETABLE, Sort.Direction.ASC).on(DATA_SOURCE, Sort.Direction.ASC).on(MODIFIED, Sort.Direction.ASC).partial(PartialIndexFilter.of(Criteria.where(CONTENT_DELETABLE).is(true))));

        // partial index to support finding DeltaFiles that are pending annotations
        INDICES.put("first_pending_annotations_index", new Index().named("first_pending_annotations_index")
                .on(FIRST_PENDING_ANNOTATIONS, Sort.Direction.ASC).partial(PartialIndexFilter.of(Criteria.where(FIRST_PENDING_ANNOTATIONS).exists(true))).on(MODIFIED, Sort.Direction.ASC));

        // use partial indexes for boolean fields filtering on the more selective value
        INDICES.put("egressed_index", new Index().named("egressed_index").on(EGRESSED, Sort.Direction.ASC).partial(PartialIndexFilter.of(Criteria.where(EGRESSED).is(false))).on(MODIFIED, Sort.Direction.ASC));
        INDICES.put("test_mode_index", new Index().named("test_mode_index").on(TEST_MODE, Sort.Direction.ASC).partial(PartialIndexFilter.of(Criteria.where(TEST_MODE).is(true))).on(MODIFIED, Sort.Direction.ASC));
        INDICES.put("filtered_index", new Index().named("filtered_index").on(FILTERED, Sort.Direction.ASC).partial(PartialIndexFilter.of(Criteria.where(FILTERED).is(true))).on(MODIFIED, Sort.Direction.ASC));
        INDICES.put("error_index", new Index().named("error_index").on(STAGE, Sort.Direction.ASC).on(ERROR_ACKNOWLEDGED, Sort.Direction.ASC).partial(PartialIndexFilter.of(Criteria.where(STAGE).is("ERROR"))));

        INDICES.put("cold_queued_index", new Index().named("cold_queued_index").on(IN_FLIGHT, Sort.Direction.ASC).on(ACTIONS_STATE, Sort.Direction.ASC).on(ACTIONS_NAME, Sort.Direction.ASC).on(MODIFIED, Sort.Direction.ASC).partial(PartialIndexFilter.of(Criteria.where(IN_FLIGHT).is(true).and(ACTIONS_STATE).is(COLD_QUEUED.name()))));
        INDICES.put("queued_index", new Index().named("queued_index").on(IN_FLIGHT, Sort.Direction.ASC).on(ACTIONS_STATE, Sort.Direction.ASC).on(ACTIONS_NAME, Sort.Direction.ASC).on(MODIFIED, Sort.Direction.ASC).partial(PartialIndexFilter.of(Criteria.where(IN_FLIGHT).is(true).and(ACTIONS_STATE).is(QUEUED.name()))));
    }

    @PersistenceContext
    private final EntityManager entityManager;
    private final JdbcTemplate jdbcTemplate;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final MongoTemplate mongoTemplate;

    public void ensureAllIndices(Duration newTtl) {
        IndexOperations idxOps = mongoTemplate.indexOps(DeltaFile.class);
        List<IndexInfo> existingIndexes = idxOps.getIndexInfo();

        INDICES.forEach((indexName, indexDef) -> IndexUtils.updateIndices(idxOps, indexName, indexDef, existingIndexes));

        Set<String> expected = new HashSet<>(INDICES.keySet());
        expected.add("_id_");
        expected.add(TTL_INDEX_NAME);
        existingIndexes.forEach(existingIndex -> removeUnknownIndices(idxOps, existingIndex, expected));

        // TODO: set up shard indexes
    }

    private Duration getTtlExpirationFromMongo() {
        return getIndexes().stream().filter(index -> index.getName().equals(TTL_INDEX_NAME)).findFirst()
                .flatMap(IndexInfo::getExpireAfter).orElse(null);
    }

    @Override
    public List<IndexInfo> getIndexes() {
        return mongoTemplate.indexOps(COLLECTION).getIndexInfo();
    }

    @Override
    @Transactional
    public List<DeltaFile> updateForRequeue(OffsetDateTime requeueTime, Duration requeueDuration, Set<String> skipActions, Set<UUID> skipDids, int limit) {
        StringBuilder filesToRequeueQuery = new StringBuilder("""
            SELECT df
            FROM DeltaFile df
            WHERE df.inFlight = true
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
                .setParameter("limit", limit);;
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

        List<UUID> dids = filesToRequeue.stream().map(DeltaFile::getDid).toList();

        entityManager.createQuery("""
            UPDATE DeltaFile df
            SET df.requeueCount = df.requeueCount + 1,
                df.modified = :modified,
                df.version = df.version + 1
            WHERE df.did IN :dids
            """)
                .setParameter("modified", requeueTime)
                .setParameter("dids", dids)
                .executeUpdate();

        filesToRequeue.forEach(deltaFile -> {
            deltaFile.setRequeueCount(deltaFile.getRequeueCount() + 1);
            deltaFile.setModified(requeueTime);
            deltaFile.setVersion(deltaFile.getVersion() + 1);
        });

        List<Action> actions = filesToRequeue.stream()
                .flatMap(d -> d.getFlows().stream())
                .flatMap(f -> f.getActions().stream())
                .filter(a -> (a.getState() == ActionState.QUEUED || a.getState() == COLD_QUEUED) &&
                        a.getModified().isBefore(requeueThreshold) && (skipActions == null || !skipActions.contains(a.getName())))
                .toList();

        List<UUID> actionIds = actions.stream().map(Action::getId).toList();

        entityManager.createQuery("""
            UPDATE Action a
            SET a.state = 'QUEUED',
                a.modified = :modified,
                a.queued = :modified
            WHERE a.id IN :actionIds
            """)
                .setParameter("modified", requeueTime)
                .setParameter("actionIds", actionIds)
                .executeUpdate();

        actions.forEach(action -> {
            action.setState(ActionState.QUEUED);
            action.setModified(requeueTime);
            action.setQueued(requeueTime);
            action.getDeltaFileFlow().updateState(requeueTime);
        });

        Set<UUID> deltaFileFlowIds = actions.stream().map(a -> a.getDeltaFileFlow().getId()).collect(Collectors.toSet());

        entityManager.createQuery("""
            UPDATE DeltaFileFlow d
            SET d.modified = :modified
            WHERE d.id IN :deltaFileFlowIds
            """)
                .setParameter("modified", requeueTime)
                .setParameter("deltaFileFlowIds", deltaFileFlowIds)
                .executeUpdate();

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
                WHERE df.inFlight = true
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

        List<UUID> dids = filesToRequeue.stream().map(DeltaFile::getDid).toList();
        entityManager.createQuery("""
                UPDATE DeltaFile df
                SET df.requeueCount = df.requeueCount + 1,
                    df.modified = :modified,
                    df.version = df.version + 1
                WHERE df.did IN :dids
                """)
                .setParameter("modified", modified)
                .setParameter("dids", dids)
                .executeUpdate();
        filesToRequeue.forEach(deltaFile -> {
            deltaFile.setRequeueCount(deltaFile.getRequeueCount() + 1);
            deltaFile.setModified(modified);
            deltaFile.setVersion(deltaFile.getVersion() + 1);
        });

        List<Action> actions = filesToRequeue.stream()
                .flatMap(d -> d.getFlows().stream())
                .flatMap(f -> f.getActions().stream())
                .filter(a -> a.getState() == COLD_QUEUED && actionNames.contains(a.getName()))
                .toList();

        List<UUID> actionIds = actions.stream()
                .map(Action::getId)
                .toList();

        entityManager.createQuery("""
                UPDATE Action a
                SET a.state = 'QUEUED',
                    a.modified = :modified,
                    a.queued = :modified
                WHERE a.id IN :actionIds
                """)
                .setParameter("modified", modified)
                .setParameter("actionIds", actionIds)
                .executeUpdate();

        actions.forEach(action -> {
            action.setState(QUEUED);
            action.setModified(modified);
            action.setQueued(modified);
        });

        Set<UUID> deltaFileFlowIds = actions.stream().map(a -> a.getDeltaFileFlow().getId()).collect(Collectors.toSet());

        entityManager.createQuery("""
            UPDATE DeltaFileFlow d
            SET d.modified = :modified
            WHERE d.id IN :deltaFileFlowIds
            """)
                .setParameter("modified", modified)
                .setParameter("deltaFileFlowIds", deltaFileFlowIds)
                .executeUpdate();

        return filesToRequeue;
    }

    @Override
    public List<DeltaFile> findReadyForAutoResume(OffsetDateTime maxReadyTime) {
                String queryStr = """
                SELECT df
                FROM DeltaFile df
                WHERE df.stage = 'ERROR'
                AND EXISTS (
                    SELECT 1
                    FROM DeltaFileFlow flow
                    JOIN flow.actions action
                    WHERE flow.deltaFile = df
                    AND action.nextAutoResume < :maxReadyTime
                )
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
                            cb.equal(root.get("state"), ActionState.ERROR)
                    ));

            entityManager.createQuery(update).executeUpdate();
        }
    }

    @Override
    public List<DeltaFile> findForTimedDelete(OffsetDateTime createdBeforeDate, OffsetDateTime completedBeforeDate,
                                              long minBytes, String flow, boolean deleteMetadata, int batchSize) {
        if (createdBeforeDate == null && completedBeforeDate == null) {
            return Collections.emptyList();
        }

        StringBuilder queryBuilder = new StringBuilder("SELECT d FROM DeltaFile d WHERE ");
        boolean hasPreviousCondition = false;

        if (createdBeforeDate != null) {
            queryBuilder.append("d.created < :createdBeforeDate ");
            hasPreviousCondition = true;
        }

        if (completedBeforeDate != null) {
            if (hasPreviousCondition) queryBuilder.append("AND ");
            queryBuilder.append("d.modified < :completedBeforeDate AND d.terminal = true ");
        }

        if (flow != null) {
            queryBuilder.append("AND d.dataSource = :flow ");
        }

        if (minBytes > 0L) {
            queryBuilder.append("AND d.totalBytes >= :minBytes ");
        }

        if (!deleteMetadata) {
            queryBuilder.append("AND d.contentDeletable = true ");
        }

        queryBuilder.append("ORDER BY ");
        if (createdBeforeDate != null) {
            queryBuilder.append("d.created ASC");
        } else {
            queryBuilder.append("d.modified ASC");
        }

        TypedQuery<DeltaFile> query = entityManager.createQuery(queryBuilder.toString(), DeltaFile.class);

        if (createdBeforeDate != null) {
            query.setParameter("createdBeforeDate", createdBeforeDate);
        }

        if (completedBeforeDate != null) {
            query.setParameter("completedBeforeDate", completedBeforeDate);
        }

        if (flow != null) {
            query.setParameter("flow", flow);
        }

        if (minBytes > 0L) {
            query.setParameter("minBytes", minBytes);
        }

        query.setMaxResults(batchSize);

        return query.getResultList();
    }

    // TODO: do this in one shot
    @Override
    public List<DeltaFile> findForDiskSpaceDelete(long bytesToDelete, String dataSource, int batchSize) {
        if (bytesToDelete < 1) {
            throw new IllegalArgumentException("bytesToDelete (%s) must be positive".formatted(bytesToDelete));
        }

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<DeltaFile> query = cb.createQuery(DeltaFile.class);
        Root<DeltaFile> root = query.from(DeltaFile.class);

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(root.get("contentDeletable"), true));

        if (dataSource != null) {
            predicates.add(cb.equal(root.get("dataSource"), dataSource));
        }

        query.select(root)
                .where(predicates.toArray(new Predicate[0]))
                .orderBy(cb.asc(root.get("modified")));

        TypedQuery<DeltaFile> typedQuery = entityManager.createQuery(query);
        typedQuery.setMaxResults(batchSize);
        List<DeltaFile> deltaFiles = typedQuery.getResultList();

        long[] sum = {0};
        return deltaFiles.stream()
                .filter(d -> {
                    boolean over = sum[0] <= bytesToDelete;
                    sum[0] += d.getTotalBytes();
                    return over;
                })
                .toList();
    }

    @Override
    public DeltaFiles deltaFiles(Integer offset, int limit, DeltaFilesFilter filter, DeltaFileOrder orderBy, List<String> includeFields) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<DeltaFile> query = cb.createQuery(DeltaFile.class);
        Root<DeltaFile> root = query.from(DeltaFile.class);

        List<Predicate> predicates = buildDeltaFilesCriteria(cb, root, query, filter);
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
            CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
            Root<DeltaFile> countRoot = countQuery.from(DeltaFile.class);
            countQuery.select(cb.count(countRoot)).where(cb.and(predicates.toArray(new Predicate[0])));
            Long total = entityManager.createQuery(countQuery).getSingleResult();
            deltaFiles.setTotalCount(total.intValue());
        }

        return deltaFiles;
    }

    private List<Predicate> buildDeltaFilesCriteria(CriteriaBuilder cb, Root<DeltaFile> root, CriteriaQuery<DeltaFile> query, DeltaFilesFilter filter) {
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
            predicates.add(root.get(EGRESS_FLOWS).in(filter.getEgressFlows()));
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
                Subquery<Long> subquery = query.subquery(Long.class);
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
                Subquery<Long> subquery = query.subquery(Long.class);
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
            Subquery<Long> subquery = query.subquery(Long.class);
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
            Subquery<Long> subquery = query.subquery(Long.class);
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
            Subquery<Long> subquery = query.subquery(Long.class);
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
            Subquery<Long> subquery = query.subquery(Long.class);
            Root<DeltaFile> subRoot = subquery.from(DeltaFile.class);
            Join<DeltaFile, DeltaFileFlow> subFlowJoin = subRoot.join("flows");
            Join<DeltaFileFlow, Action> subActionJoin = subFlowJoin.join("actions");

            subquery.select(cb.literal(1L)).where(
                    cb.equal(subRoot.get("did"), root.get("did")),
                    cb.isNotNull((subActionJoin.get("errorAcknowledged"))));

            if (filter.getErrorAcknowledged()) {
                predicates.add(cb.exists(subquery));
            } else {
                predicates.add(cb.not(cb.exists(subquery)));
            }
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
            predicates.add(cb.equal(root.get(TEST_MODE), filter.getTestMode()));
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

    private void removeUnknownIndices(IndexOperations idxOps, IndexInfo existing, Set<String> knownIndices) {
        if (!knownIndices.contains(existing.getName())) {
            log.info("Dropping unknown index {}", existing.getName());
            dropIndex(idxOps, existing.getName());
        }
    }

    private void dropIndex(IndexOperations idxOps, String indexName) {
        try {
            idxOps.dropIndex(indexName);
        } catch (UncategorizedMongoDbException ex) {
            log.error("Failed to remove unknown index {}", indexName, ex);
        }
    }

    // TODO: can this happen in one shot?
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

    @Override
    @Transactional
    public void batchInsert(List<DeltaFile> deltaFiles) {
        String sql = """
                INSERT INTO delta_files (did, name, normalized_name, data_source, parent_dids, collect_id, child_dids,
                                         requeue_count, ingress_bytes, referenced_bytes, total_bytes, stage, 
                                         egress_flows, created, modified, content_deleted, content_deleted_reason,
                                         egressed, filtered, replayed, replay_did, in_flight, terminal,
                                         content_deletable, version, schema_version)
                VALUES (?, ?, ?, ?, ?::jsonb, ?, ?::jsonb, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""";

        jdbcTemplate.batchUpdate(sql, deltaFiles, 1000, (ps, deltaFile) -> {
            ps.setObject(1, deltaFile.getDid());
            ps.setString(2, deltaFile.getName());
            ps.setString(3, deltaFile.getNormalizedName());
            ps.setString(4, deltaFile.getDataSource());
            ps.setString(5, toJson(deltaFile.getParentDids()));
            ps.setObject(6, deltaFile.getCollectId());
            ps.setString(7, toJson(deltaFile.getChildDids()));
            ps.setInt(8, deltaFile.getRequeueCount());
            ps.setLong(9, deltaFile.getIngressBytes());
            ps.setLong(10, deltaFile.getReferencedBytes());
            ps.setLong(11, deltaFile.getTotalBytes());
            ps.setString(12, deltaFile.getStage().name());
            ps.setString(13, toJson(deltaFile.getEgressFlows()));
            ps.setTimestamp(14, toTimestamp(deltaFile.getCreated()));
            ps.setTimestamp(15, toTimestamp(deltaFile.getModified()));
            ps.setTimestamp(16, toTimestamp(deltaFile.getContentDeleted()));
            ps.setString(17, deltaFile.getContentDeletedReason());
            ps.setObject(18, deltaFile.getEgressed());
            ps.setObject(19, deltaFile.getFiltered());
            ps.setTimestamp(20, toTimestamp(deltaFile.getReplayed()));
            ps.setObject(21, deltaFile.getReplayDid());
            ps.setBoolean(22, deltaFile.isInFlight());
            ps.setBoolean(23, deltaFile.isTerminal());
            ps.setBoolean(24, deltaFile.isContentDeletable());
            ps.setLong(25, deltaFile.getVersion());
            ps.setInt(26, deltaFile.getSchemaVersion());
        });

        // Batch insert DeltaFileFlows
        List<DeltaFileFlow> deltaFileFlows = new ArrayList<>();
        for (DeltaFile deltaFile : deltaFiles) {
            for (DeltaFileFlow flow : deltaFile.getFlows()) {
                flow.setDeltaFile(deltaFile);  // Ensure relationship is set
                deltaFileFlows.add(flow);
            }
        }

        String deltaFileFlowSql = """
                INSERT INTO delta_file_flows (id, name, number, type, state, created, modified, flow_plan, input,
                                              publish_topics, depth, pending_annotations, test_mode, test_mode_reason,
                                              collect_id, pending_actions, delta_file_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?::jsonb, ?, ?, ?, ?::jsonb, ?)""";

        jdbcTemplate.batchUpdate(deltaFileFlowSql, deltaFileFlows, 1000, (ps, flow) -> {
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
            ps.setObject(15, flow.getCollectId());
            ps.setString(16, toJson(flow.getPendingActions()));
            ps.setObject(17, flow.getDeltaFile().getDid());
        });

        // Batch insert Actions
        List<Action> actions = new ArrayList<>();
        for (DeltaFileFlow flow : deltaFileFlows) {
            for (Action action : flow.getActions()) {
                action.setDeltaFileFlow(flow);  // Ensure relationship is set
                actions.add(action);
            }
        }

        String actionSql = """
                INSERT INTO actions (id, name, number, type, state, created, queued, start, stop, modified, error_cause,
                                     error_context, error_acknowledged, error_acknowledged_reason, next_auto_resume,
                                     next_auto_resume_reason, filtered_cause, filtered_context, attempt, content,
                                     metadata, delete_metadata_keys, replay_start, delta_file_flow_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?)""";

        jdbcTemplate.batchUpdate(actionSql, actions, 1000, (ps, action) -> {
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
            ps.setObject(24, action.getDeltaFileFlow().getId());
        });

        // Batch insert Annotations
        List<Annotation> annotations = new ArrayList<>();
        for (DeltaFile deltaFile : deltaFiles) {
            for (Annotation annotation : deltaFile.getAnnotations()) {
                annotation.setDeltaFile(deltaFile);  // Ensure relationship is set
                annotations.add(annotation);
            }
        }

        String annotationSql = """
                INSERT INTO annotations (id, key, value, delta_file_id)
                VALUES (?, ?, ?, ?)""";

        jdbcTemplate.batchUpdate(annotationSql, annotations, 1000, (ps, annotation) -> {
            ps.setObject(1, annotation.getId());
            ps.setString(2, annotation.getKey());
            ps.setString(3, annotation.getValue());
            ps.setObject(4, annotation.getDeltaFile().getDid());
        });
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

        for (List<UUID> batch : Lists.partition(dids, 500)) {
            entityManager.createQuery("DELETE FROM Action a WHERE a.deltaFileFlow.id IN " +
                            "(SELECT f.id FROM DeltaFileFlow f WHERE f.deltaFile.id in :dids)")
                    .setParameter("dids", batch)
                    .executeUpdate();
            entityManager.createQuery("DELETE FROM DeltaFileFlow f WHERE f.deltaFile.id in :dids")
                    .setParameter("dids", batch)
                    .executeUpdate();
            entityManager.createQuery("DELETE FROM Annotation a WHERE a.deltaFile.id in :dids")
                    .setParameter("dids", batch)
                    .executeUpdate();
            entityManager.createQuery("DELETE FROM DeltaFile d WHERE d.did IN :dids")
                    .setParameter("dids", batch)
                    .executeUpdate();
            entityManager.flush();
            entityManager.clear();
        }
    }

    public boolean update(UUID did, long version, Update update) {
        Query query = new Query(Criteria.where(ID).is(did).and(VERSION).is(version));
        UpdateResult result = mongoTemplate.updateFirst(query, update, DeltaFile.class);

        return result.wasAcknowledged() && result.getMatchedCount() > 0;
    }
}
