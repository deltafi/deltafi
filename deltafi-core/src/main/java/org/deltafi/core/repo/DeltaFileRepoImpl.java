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
import com.mongodb.client.MongoCollection;
import com.mongodb.client.result.UpdateResult;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.BsonValue;
import org.bson.Document;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.common.types.*;
import org.deltafi.core.generated.types.*;
import org.deltafi.core.types.*;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.UncategorizedMongoDbException;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;

import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.deltafi.common.types.ActionState.*;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

@SuppressWarnings("unused")
@Slf4j
@Repository
@RequiredArgsConstructor
@Transactional
public class DeltaFileRepoImpl implements DeltaFileRepoCustom {
    public static final String ID = "_id";
    public static final String VERSION = "version";
    public static final String PARENT_DIDS = "parentDids";
    public static final String MODIFIED = "modified";
    public static final String CREATED = "created";
    public static final String STAGE = "stage";
    public static final String STATE = "state";
    public static final String NAME = "name";
    public static final String ACTIONS_METADATA = "actions.metadata";
    public static final String ACTIONS_DELETE_METADATA_KEYS = "actions.deleteMetadataKeys";
    public static final String CONTENT_DELETED = "contentDeleted";
    public static final String CONTENT_DELETED_REASON = "contentDeletedReason";
    public static final String KEY = "key";
    public static final String VALUE = "value";
    public static final String DATA_SOURCE = "dataSource";
    public static final String ERROR_CAUSE = "errorCause";
    public static final String FILTERED_CAUSE = "filteredCause";
    public static final String ERROR_ACKNOWLEDGED = "errorAcknowledged";
    public static final String EGRESSED = "egressed";
    public static final String EGRESS_FLOWS = "egressFlows";
    public static final String FILTERED = "filtered";
    public static final String HAS_PENDING_ANNOTATIONS = "hasPendingAnnotations";
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
    public static final String FORMATTED_DATA_FILENAME = "formattedData.content.name";
    public static final String FORMATTED_DATA_FORMAT_ACTION = "formattedData.formatAction";
    public static final String FORMATTED_DATA_METADATA = "formattedData.metadata";
    public static final String FORMATTED_DATA_EGRESS_ACTIONS = "formattedData.egressActions";
    public static final String FLOWS_INPUT_METADATA = "flows.input.metadata";
    public static final String FLOWS_NAME = "flows.name";
    public static final String FLOWS_STATE = "flows.state";
    public static final String FLOWS_ACTIONS = "flows.actions";
    public static final String FLOWS_ACTIONS_NAME = "flows.actions.name";
    public static final String FLOWS_ACTIONS_STATE = "flows.actions.state";
    public static final String FLOWS_ACTIONS_TYPE = "flows.actions.type";
    public static final String FLOWS_ACTIONS_METADATA = "flows.actions.metadata";
    public static final String FLOWS_ACTIONS_DELETE_METADATA_KEYS = "flows.actions.deleteMetadataKeys";
    public static final String ACTIONS_ATTEMPT = "actions.attempt";
    public static final String ACTIONS_ERROR_CAUSE = "flows.actions.errorCause";
    public static final String ACTIONS_FILTERED_CAUSE = "flows.actions.filteredCause";
    public static final String ACTIONS_NAME = "flows.actions.name";
    public static final String ACTIONS_TYPE = "flows.actions.type";
    public static final String ACTIONS_STATE = "flows.actions.state";
    public static final String ACTIONS_MODIFIED = "flows.actions.modified";
    public static final String ACTION_MODIFIED = "action.modified";
    public static final String ACTION_STATE = "action.state";
    public static final String ACTIONS_UPDATE_STATE = "flows.$[flow].actions.$[action].state";
    public static final String ACTIONS_UPDATE_MODIFIED = "flows.$[flow].actions.$[action].modified";
    public static final String ACTIONS_UPDATE_QUEUED = "flows.$[flow].actions.$[action].queued";
    public static final String ACTIONS_UPDATE_ERROR = "flows.$[flow].actions.$[action].errorCause";
    public static final String ACTIONS_UPDATE_ERROR_CONTEXT = "flows.$[flow].actions.$[action].errorContext";
    public static final String ACTIONS_UPDATE_HISTORY = "flows.$[flow].actions.$[action].history";
    private static final String COLLECTION = "deltaFiles";
    private static final String TTL_INDEX_NAME = "ttl_index";
    private static final String SCHEMA_VERSION = "schemaVersion";
    // Aggregation variables
    private static final String COUNT_FOR_PAGING = "countForPaging";
    private static final String COUNT_LOWER_CASE = "count";
    private static final String DID = "did";
    private static final String DIDS = "dids";
    private static final String ERROR_MESSAGE = "errorMessage";
    private static final String ACTIONS = "actions";
    private static final String FLOW_LOWER_CASE = "flow";
    private static final String FLOWS = "flows";
    private static final String FLOW_STATE = "flow.state";
    private static final String GROUP_COUNT = "groupCount";
    private static final String ID_ERROR_MESSAGE = ID + "." + ERROR_MESSAGE;
    private static final String ID_DATA_SOURCE = ID + "." + DATA_SOURCE;
    private static final String UNWIND_STATE = "unwindState";
    public static final String ANNOTATIONS = "annotations";
    public static final String ANNOTATION_KEYS = "annotationKeys";

    private static final String ACTION_SEGMENTS = "flows.actions.content.segments";

    private static final String CUMULATIVE_BYTES = "cumulativeBytes";
    private static final String OVER = "over";
    private static final String CUMULATIVE_OVER = "cumulativeOver";

    private static final String IN_FLIGHT = "inFlight";
    private static final String IN_FLIGHT_COUNT = "inFlightCount";
    private static final String IN_FLIGHT_BYTES = "inFlightBytes";

    private static final String TERMINAL = "terminal";
    private static final String CONTENT_DELETABLE = "contentDeletable";

    private static final String CREATED_BEFORE_INDEX = "created_before_index";
    private static final String TERMINAL_INDEX = "terminal_index";

    public static final int MAX_COUNT = 50_000;
    public static final String TYPE = "type";

    static class FlowCountAndDids {
        String dataSource;
        int groupCount;
        List<UUID> dids;
    }

    static class MessageFlowGroup {
        TempGroupId id;
        int groupCount;
        List<UUID> dids;

        static class TempGroupId {
            String errorMessage;
            String dataSource;
        }
    }

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
    private Duration cachedTtlDuration;

    public void ensureAllIndices(Duration newTtl) {
        setExpirationIndex(newTtl);
        IndexOperations idxOps = mongoTemplate.indexOps(DeltaFile.class);
        List<IndexInfo> existingIndexes = idxOps.getIndexInfo();

        INDICES.forEach((indexName, indexDef) -> IndexUtils.updateIndices(idxOps, indexName, indexDef, existingIndexes));

        Set<String> expected = new HashSet<>(INDICES.keySet());
        expected.add("_id_");
        expected.add(TTL_INDEX_NAME);
        existingIndexes.forEach(existingIndex -> removeUnknownIndices(idxOps, existingIndex, expected));

        // TODO: set up shard indexes
    }

    @Override
    public void setExpirationIndex(Duration newTtl) {
        Duration currentTtl = getTtlExpiration();
        if (Objects.nonNull(newTtl) && !newTtl.equals(currentTtl)) {
            log.info("DeltaFile TTL was {}, changing it to {}", currentTtl, newTtl);
            if (Objects.nonNull(currentTtl)) {
                mongoTemplate.indexOps(COLLECTION).dropIndex(TTL_INDEX_NAME);
            }
            mongoTemplate.indexOps(COLLECTION).ensureIndex(new Index().on(CREATED, Sort.Direction.ASC).named(TTL_INDEX_NAME).expire(newTtl.getSeconds()));
            cachedTtlDuration = newTtl;
        }
    }

    @Override
    public Duration getTtlExpiration() {
        if (cachedTtlDuration == null) {
            cachedTtlDuration = getTtlExpirationFromMongo();
        }
        return cachedTtlDuration;
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
    public List<DeltaFile> updateForRequeue(OffsetDateTime requeueTime, Duration requeueDuration, Set<String> skipActions, Set<UUID> skipDids) {
        List<DeltaFile> filesToRequeue = mongoTemplate.find(buildReadyForRequeueQuery(requeueTime, requeueDuration, skipActions, skipDids), DeltaFile.class);
        List<DeltaFile> requeuedDeltaFiles = new ArrayList<>();
        for (List<DeltaFile> batch : Lists.partition(filesToRequeue, 1000)) {
            List<UUID> dids = batch.stream().map(DeltaFile::getDid).toList();
            Query query = new Query().addCriteria(Criteria.where(ID).in(dids));
            mongoTemplate.updateMulti(query, buildRequeueUpdate(requeueTime, requeueDuration), DeltaFile.class);
            requeuedDeltaFiles.addAll(mongoTemplate.find(query, DeltaFile.class));
        }

        return requeuedDeltaFiles;
    }

    @Override
    public List<DeltaFile> updateColdQueuedForRequeue(List<String> actionNames, int maxFiles, OffsetDateTime modified) {
        List<DeltaFile> filesToRequeue = mongoTemplate.find(buildReadyForColdRequeueQuery(actionNames, maxFiles), DeltaFile.class);
        List<DeltaFile> requeuedDeltaFiles = new ArrayList<>();
        for (List<DeltaFile> batch : Lists.partition(filesToRequeue, 1000)) {
            List<UUID> dids = batch.stream().map(DeltaFile::getDid).toList();
            Query query = new Query().addCriteria(Criteria.where(ID).in(dids));
            mongoTemplate.updateMulti(query, buildColdRequeueUpdate(modified), DeltaFile.class);
            requeuedDeltaFiles.addAll(mongoTemplate.find(query, DeltaFile.class));
        }

        return requeuedDeltaFiles;
    }

    @Override
    public List<DeltaFile> findReadyForAutoResume(OffsetDateTime maxReadyTime) {
        return mongoTemplate.find(buildReadyForAutoResume(maxReadyTime), DeltaFile.class);
    }

    private Query buildReadyForAutoResume(OffsetDateTime maxReadyTime) {
        Query requeueQuery = new Query(Criteria.where(STAGE).is(DeltaFileStage.ERROR)
                .and(FLOWS_ACTIONS).elemMatch(Criteria.where(NEXT_AUTO_RESUME).lt(maxReadyTime)));
        requeueQuery.fields().include(ID, DATA_SOURCE, SCHEMA_VERSION);

        return requeueQuery;
    }

    @Override
    public List<DeltaFile> findResumePolicyCandidates(String dataSource) {
        return mongoTemplate.find(buildResumePolicyCanidatesQuery(dataSource), DeltaFile.class);
    }

    private Query buildResumePolicyCanidatesQuery(String dataSource) {
        Criteria criteria = Criteria.where(STAGE).is(DeltaFileStage.ERROR)
                .and(FLOWS_ACTIONS).not().elemMatch(
                        Criteria.where(NEXT_AUTO_RESUME).ne(null)
                                .orOperator(Criteria.where(ERROR_ACKNOWLEDGED).ne(null)))
                .and(CONTENT_DELETED).isNull();

        if (dataSource != null) {
            criteria.and(DATA_SOURCE).is(dataSource);
        }

        Query requeueQuery = new Query(criteria);
        requeueQuery.fields().include(ID, DATA_SOURCE, FLOWS_STATE, ACTIONS_NAME, ACTIONS_ERROR_CAUSE, ACTIONS_STATE, ACTIONS_TYPE, ACTIONS_ATTEMPT, SCHEMA_VERSION);

        return requeueQuery;
    }

    @Override
    public void updateForAutoResume(List<UUID> dids, String policyName, OffsetDateTime nextAutoResume) {
        Update update = new Update()
                .set("flows.$[].actions.$[action].nextAutoResume", nextAutoResume)
                .set("flows.$[].actions.$[action].nextAutoResumeReason", policyName)
                .filterArray(Criteria.where(ACTION_STATE).is(ERROR));
        batchedBulkUpdateByIds(dids, update);
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

    @Override
    public List<DeltaFile> findForDiskSpaceDelete(long bytesToDelete, String dataSource, int batchSize) {
        if (bytesToDelete < 1) {
            throw new IllegalArgumentException("bytesToDelete (" + bytesToDelete + ") must be positive");
        }

        Criteria criteria = Criteria.where(CONTENT_DELETABLE).is(true);

        if (dataSource != null) {
            criteria.and(DATA_SOURCE).is(dataSource);
        }

        Query query = new Query(criteria);
        query.limit(batchSize);
        query.with(Sort.by(Sort.Direction.ASC, MODIFIED));
        query.fields().include(ID, TOTAL_BYTES, ACTION_SEGMENTS, ACTIONS_NAME, SCHEMA_VERSION);

        List<DeltaFile> deltaFiles = mongoTemplate.find(query, DeltaFile.class);
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
            CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
            Root<DeltaFile> countRoot = countQuery.from(DeltaFile.class);
            countQuery.select(cb.count(countRoot)).where(cb.and(predicates.toArray(new Predicate[0])));
            Long total = entityManager.createQuery(countQuery).getSingleResult();
            deltaFiles.setTotalCount(total.intValue());
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

        addErrorAndFilterCriteria(filter, predicates, cb, root);

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
        if (Boolean.TRUE.equals(nameFilter.getRegex())) {
            // TODO: fix this
            throw new UnsupportedOperationException("Regex filtering is not supported");
        }

        String name = nameFilter.getName();
        if (nameFilter.getCaseSensitive() != null && !nameFilter.getCaseSensitive()) {
            name = name.toLowerCase();
            predicates.add(cb.like(cb.lower(root.get("name")), "%" + name + "%"));
        } else {
            predicates.add(cb.like(root.get("name"), "%" + name + "%"));
        }
    }

    private void addErrorAndFilterCriteria(DeltaFilesFilter filter, List<Predicate> predicates, CriteriaBuilder cb, Root<DeltaFile> root) {
        if (filter.getErrorCause() != null) {
            predicates.add(cb.equal(root.get("errorCause"), filter.getErrorCause()));
        }

        if (filter.getFilteredCause() != null) {
            predicates.add(cb.equal(root.get("filteredCause"), filter.getFilteredCause()));
        }

        if (filter.getErrorAcknowledged() != null) {
            predicates.add(cb.equal(root.get("errorAcknowledged"), filter.getErrorAcknowledged()));
        }

        if (filter.getFiltered() != null) {
            predicates.add(cb.equal(root.get("filtered"), filter.getFiltered()));
        }
    }

    Update buildRequeueUpdate(OffsetDateTime modified, Duration requeueDuration) {
        if (modified == null) {
            modified = OffsetDateTime.now();
        }

        Update update = new Update();

        long epochMs = requeueThreshold(modified, requeueDuration);
        update.filterArray(Criteria.where(FLOW_STATE).is(DeltaFileFlowState.IN_FLIGHT.name()));
        update.filterArray(Criteria.where(ACTION_STATE).is(ActionState.QUEUED.name())
                .and(ACTION_MODIFIED).lt(new Date(epochMs)));

        update.inc(REQUEUE_COUNT, 1);

        // clear out any old error messages
        update.set(ACTIONS_UPDATE_ERROR, null);
        update.set(ACTIONS_UPDATE_ERROR_CONTEXT, null);

        update.set(ACTIONS_UPDATE_MODIFIED, modified);
        update.set(ACTIONS_UPDATE_QUEUED, modified);

        update.set(MODIFIED, modified);

        update.inc(VERSION, 1);

        return update;
    }

    Update buildColdRequeueUpdate(OffsetDateTime modified) {
        Update update = new Update();
        update.inc(REQUEUE_COUNT, 1);

        // clear out any old error messages
        update.set(ACTIONS_UPDATE_ERROR, null);
        update.set(ACTIONS_UPDATE_ERROR_CONTEXT, null);
        update.set(ACTIONS_UPDATE_MODIFIED, modified);
        update.set(ACTIONS_UPDATE_QUEUED, modified);

        update.set(ACTIONS_UPDATE_STATE, ActionState.QUEUED.name());
        update.filterArray(Criteria.where(FLOW_STATE).is(DeltaFileFlowState.IN_FLIGHT.name()));
        update.filterArray(Criteria.where(ACTION_STATE).is(COLD_QUEUED.name()));
        update.set(MODIFIED, modified);

        update.inc(VERSION, 1);

        return update;
    }

    private Query buildReadyForRequeueQuery(OffsetDateTime requeueTime, Duration requeueDuration, Set<String> skipActions, Set<UUID> skipDids) {
        Criteria criteria = Criteria.where(IN_FLIGHT).is(true);
        long epochMs = requeueThreshold(requeueTime, requeueDuration);
        criteria.and(MODIFIED).lt(new Date(epochMs));

        if (skipDids != null && !skipDids.isEmpty()) {
            criteria.and(DID).not().in(skipDids);
        }

        if (skipActions != null && !skipActions.isEmpty()) {
            Criteria actionsCriteria = new Criteria().orOperator(
                    Criteria.where(STATE).is(QUEUED.toString()).and(NAME).in(skipActions),
                    Criteria.where(STATE).is(COLD_QUEUED.name())
            );
            criteria.and(FLOWS_ACTIONS).not().elemMatch(actionsCriteria);
        } else {
            criteria.and(FLOWS_ACTIONS).not().elemMatch(Criteria.where(STATE).is(COLD_QUEUED.name()));
        }

        Query requeueQuery = new Query(criteria);
        requeueQuery.fields().include(ID);

        return requeueQuery;
    }

    private Query buildReadyForColdRequeueQuery(List<String> actionNames, int maxFiles) {
        Criteria notComplete = Criteria.where(IN_FLIGHT).is(true)
                .and(FLOWS_ACTIONS_STATE).is(COLD_QUEUED)
                .and(FLOWS_ACTIONS_NAME).in(actionNames);

        Criteria coldQueuedCriteria = new Criteria().andOperator(
                Criteria.where(NAME).in(actionNames),
                Criteria.where(STATE).is(COLD_QUEUED.name())
        );

        Criteria actionMatch = Criteria.where(FLOWS_ACTIONS).elemMatch(coldQueuedCriteria);

        Query requeueQuery = new Query(new Criteria().andOperator(notComplete, actionMatch));
        requeueQuery.fields().include(ID);
        requeueQuery.limit(maxFiles);

        return requeueQuery;
    }

    private void addErrorAndFilterCriteria(DeltaFilesFilter filter, Criteria criteria) {
        Criteria erroredActionCriteria = getErroredActionsCriteria(filter.getErrorCause(), filter.getErrorAcknowledged());
        Criteria filteredActionCriteria = getFilteredActionsCriteria(filter.getFilteredCause());

        if (erroredActionCriteria != null && filteredActionCriteria != null) {
            Criteria errorOrFilteredCriteria = new Criteria().orOperator(erroredActionCriteria, filteredActionCriteria);
            criteria.and(FLOWS_ACTIONS).elemMatch(errorOrFilteredCriteria);
        } else if (erroredActionCriteria != null) {
            criteria.and(FLOWS_ACTIONS).elemMatch(erroredActionCriteria);
        } else if (filteredActionCriteria != null) {
            boolean filtered = filter.getFiltered() == null || filter.getFiltered();
            criteria.and(FILTERED).is(filtered);
            criteria.and(FLOWS_ACTIONS).elemMatch(filteredActionCriteria);
        } else if (filter.getFiltered() != null) {
            criteria.and(FILTERED).is(filter.getFiltered());
        }
    }

    private Criteria getErroredActionsCriteria(String errorCause, Boolean acknowledged) {
        if (errorCause == null && acknowledged == null) {
            return null;
        }
        List<Criteria> elemCriteria = new ArrayList<>();
        elemCriteria.add(Criteria.where(STATE).is(ERROR.name()));
        if (errorCause != null) {
            elemCriteria.add(Criteria.where(ERROR_CAUSE).regex(errorCause));
        }

        if (acknowledged != null) {
            if (acknowledged) {
                elemCriteria.add(Criteria.where(ERROR_ACKNOWLEDGED).ne(null));
            } else {
                elemCriteria.add(Criteria.where(ERROR_ACKNOWLEDGED).isNull());
            }
        }
        return new Criteria().andOperator(elemCriteria);
    }

    private Criteria getFilteredActionsCriteria(String filteredCause) {
        return filteredCause != null ? new Criteria().andOperator(
                Criteria.where(STATE).is(ActionState.FILTERED.name()), Criteria.where(FILTERED_CAUSE).regex(filteredCause)) : null;
    }

    private void addAnnotationCriteria(String key, String value, Criteria criteria) {
        if (null == key || null == value) {
            return;
        }

        if (key.contains(".")) {
            key = StringUtils.replace(key, ".", DeltaFiConstants.MONGO_MAP_KEY_DOT_REPLACEMENT);
        }

        criteria.and(ANNOTATIONS + "." + key).is(value);
    }

    private long requeueThreshold(OffsetDateTime requeueTime, Duration requeueDuration) {
        return requeueTime.minus(requeueDuration).toInstant().toEpochMilli();
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

    @Override
    public SummaryByFlow getErrorSummaryByFlow(Integer offset, int limit, ErrorSummaryFilter filter, DeltaFileOrder orderBy) {
        return getSummaryByFlow(offset, limit, buildErrorSummaryCriteria(filter), orderBy);
    }

    @Override
    public SummaryByFlow getFilteredSummaryByFlow(Integer offset, int limit, FilteredSummaryFilter filter, DeltaFileOrder orderBy) {
        return getSummaryByFlow(offset, limit, buildFilterSummaryCriteria(filter), orderBy);
    }

    private SummaryByFlow getSummaryByFlow(Integer offset, int limit, Criteria filter, DeltaFileOrder orderBy) {
        long elementsToSkip = (offset != null && offset > 0) ? offset : 0;

        MatchOperation matchesErrorStage = Aggregation.match(filter);

        Aggregation countAggregation = Aggregation.newAggregation(
                matchesErrorStage,
                group(DATA_SOURCE).count().as(GROUP_COUNT),
                count().as(COUNT_FOR_PAGING))
                .withOptions(AggregationOptions.builder().allowDiskUse(true).build());

        final Long countForPaging = Optional
                .ofNullable(mongoTemplate.aggregate(countAggregation, COLLECTION,
                        Document.class).getUniqueMappedResult())
                .map(doc -> ((Integer) doc.get(COUNT_FOR_PAGING)).longValue())
                .orElse(0L);

        List<CountPerFlow> countPerFlow = new ArrayList<>();
        if (countForPaging > 0) {
            Aggregation pagingAggregation = Aggregation.newAggregation(
                    matchesErrorStage,
                    group(DATA_SOURCE).count().as(GROUP_COUNT).addToSet(ID).as(DIDS),
                    project(DIDS, GROUP_COUNT).and(DATA_SOURCE).previousOperation(),
                    errorSummaryByFlowSort(orderBy),
                    skip(elementsToSkip),
                    limit(limit))
                    .withOptions(AggregationOptions.builder().allowDiskUse(true).build());

            AggregationResults<FlowCountAndDids> aggResults = mongoTemplate.aggregate(
                    pagingAggregation, COLLECTION, FlowCountAndDids.class);

            for (FlowCountAndDids r : aggResults.getMappedResults()) {
                countPerFlow.add(CountPerFlow.newBuilder()
                        .flow(r.dataSource)
                        .count(r.dids.size())
                        .dids(r.dids)
                        .build()
                );
            }
        }

        return new SummaryByFlow((int)elementsToSkip, countPerFlow.size(), countForPaging.intValue(), countPerFlow);
    }

    public SummaryByFlowAndMessage getErrorSummaryByMessage(Integer offset, int limit, ErrorSummaryFilter filter, DeltaFileOrder orderBy) {
        return getSummaryByMessage(ACTIONS_ERROR_CAUSE, ERROR, offset, limit, buildErrorSummaryCriteria(filter), orderBy);
    }

    public SummaryByFlowAndMessage getFilteredSummaryByMessage(Integer offset, int limit, FilteredSummaryFilter filter, DeltaFileOrder orderBy) {
        return getSummaryByMessage(ACTIONS_FILTERED_CAUSE, ActionState.FILTERED, offset, limit, buildFilterSummaryCriteria(filter), orderBy);
    }

    private SummaryByFlowAndMessage getSummaryByMessage(String messageField, ActionState actionState, Integer offset, int limit, Criteria filter, DeltaFileOrder orderBy) {
        long elementsToSkip = (offset != null && offset > 0) ? offset : 0;

        MatchOperation matchesErrorStage = Aggregation.match(filter);

        GroupOperation groupByCauseAndFlow = Aggregation.group(ERROR_MESSAGE, DATA_SOURCE)
                .count().as(GROUP_COUNT)
                .addToSet(DID).as(DIDS);

        List<AggregationOperation> mainStages = Arrays.asList(
                matchesErrorStage,
                unwind(FLOWS),
                unwind(FLOWS_ACTIONS),
                project()
                        .and(DATA_SOURCE).as(DATA_SOURCE)
                        .and(ID).as(DID)
                        .and(messageField).as(ERROR_MESSAGE)
                        .and(FLOWS_ACTIONS_STATE).as(UNWIND_STATE),
                match(Criteria.where(UNWIND_STATE).is(actionState)),
                groupByCauseAndFlow
        );

        List<AggregationOperation> aggregationWithCount = new ArrayList<>(mainStages);
        aggregationWithCount.add(count().as(COUNT_FOR_PAGING));
        Aggregation countAggregation = Aggregation.newAggregation(aggregationWithCount)
                .withOptions(AggregationOptions.builder().allowDiskUse(true).build());

        final Long countForPaging = Optional
                .ofNullable(mongoTemplate.aggregate(countAggregation, COLLECTION,
                        Document.class).getUniqueMappedResult())
                .map(doc -> ((Integer) doc.get(COUNT_FOR_PAGING)).longValue())
                .orElse(0L);

        List<CountPerMessage> messageList = new ArrayList<>();
        if (countForPaging > 0) {
            List<AggregationOperation> stagesWithPaging = new ArrayList<>(mainStages);
            stagesWithPaging.add(errorSummaryByMessageSort(orderBy));
            stagesWithPaging.add(skip(elementsToSkip));
            stagesWithPaging.add(limit(limit));
            Aggregation pagingAggregation = Aggregation.newAggregation(stagesWithPaging)
                    .withOptions(AggregationOptions.builder().allowDiskUse(true).build());

            AggregationResults<MessageFlowGroup> aggResults = mongoTemplate.aggregate(
                    pagingAggregation, COLLECTION, MessageFlowGroup.class);

            for (MessageFlowGroup groupResult : aggResults.getMappedResults()) {
                messageList.add(CountPerMessage.newBuilder()
                        .message(groupResult.id.errorMessage)
                        .flow(groupResult.id.dataSource)
                        .count(groupResult.dids.size())
                        .dids(groupResult.dids)
                        .build());
            }
        }

        return new SummaryByFlowAndMessage((int) elementsToSkip, messageList.size(), countForPaging.intValue(), messageList);
    }

    public Map<String, Integer> errorCountsByFlow(Set<String> flows) {
        // Match flows in the given set, ERROR_ACKNOWLEDGED is null, and STAGE is DeltaFileStage.ERROR
        Criteria flowsCriteria = Criteria.where(DATA_SOURCE).in(flows)
                .and(FLOWS_ACTIONS).not().elemMatch(Criteria.where(ERROR_ACKNOWLEDGED).ne(null))
                .and(STAGE).is(DeltaFileStage.ERROR);

        MatchOperation matchFlowsStage = Aggregation.match(flowsCriteria);

        // Group by flow and count errors
        GroupOperation groupByFlowAndCountErrorsStage = Aggregation.group(DATA_SOURCE).count().as("errorCount");

        // clean up field names to match the FlowErrorCount class
        ProjectionOperation projectStage = Aggregation.project("errorCount").and("_id").as("flow");

        // Build the aggregation pipeline
        Aggregation aggregation = Aggregation.newAggregation(
                        matchFlowsStage,
                        groupByFlowAndCountErrorsStage,
                        projectStage)
                .withOptions(AggregationOptions.builder().allowDiskUse(true).build());

        // Execute the aggregation and map results to FlowErrorCount objects
        AggregationResults<FlowErrorCount> aggResults = mongoTemplate.aggregate(
                aggregation, COLLECTION, FlowErrorCount.class);

        // Convert the list of FlowErrorCount objects to a Map<String, Integer>
        Map<String, Integer> errorCountsByFlow = new HashMap<>();
        for (FlowErrorCount result : aggResults.getMappedResults()) {
            errorCountsByFlow.put(result.getFlow(), result.getErrorCount());
        }

        return errorCountsByFlow;
    }

    private SortOperation errorSummaryByFlowSort(DeltaFileOrder orderBy) {
        String sortField = DATA_SOURCE;
        Sort.Direction direction = Sort.Direction.ASC;

        if (orderBy != null) {
            direction = Sort.Direction.fromString(orderBy.getDirection().name());
            if (orderBy.getField().toLowerCase(Locale.ROOT).contains(COUNT_LOWER_CASE)) {
                sortField = GROUP_COUNT;
            }
        }

        return Aggregation.sort(direction, sortField);
    }

    private SortOperation errorSummaryByMessageSort(DeltaFileOrder orderBy) {
        String sortField = ID_ERROR_MESSAGE;
        String secondaryField = ID_DATA_SOURCE;
        Sort.Direction direction = Sort.Direction.ASC;

        if (orderBy != null) {
            direction = Sort.Direction.fromString(orderBy.getDirection().name());

            String requestedField = orderBy.getField().toLowerCase(Locale.ROOT);
            if (requestedField.contains(FLOW_LOWER_CASE)) {
                sortField = ID_DATA_SOURCE;
                secondaryField = ID_ERROR_MESSAGE;
            } else if (requestedField.contains(COUNT_LOWER_CASE)) {
                sortField = GROUP_COUNT;
                secondaryField = ID_ERROR_MESSAGE;
            }
        }

        return Aggregation.sort(direction, sortField).and(direction, secondaryField);
    }

    private Criteria buildErrorSummaryCriteria(ErrorSummaryFilter filter) {
        Criteria criteria = Criteria.where(STAGE).is(DeltaFileStage.ERROR);

        if (filter == null) {
            return criteria;
        }

        applySummaryCriteria(filter, criteria);

        if (filter.getErrorAcknowledged() != null) {
            if (isTrue(filter.getErrorAcknowledged())) {
                criteria.and(FLOWS).elemMatch(Criteria.where("actions").elemMatch(Criteria.where(ERROR_ACKNOWLEDGED).ne(null)));
            } else {
                criteria.and(FLOWS).not().elemMatch(Criteria.where("actions").elemMatch(Criteria.where(ERROR_ACKNOWLEDGED).ne(null)));
            }
        }

        return criteria;
    }

    private Criteria buildFilterSummaryCriteria(FilteredSummaryFilter filter) {
        Criteria criteria = Criteria.where(FILTERED).is(true);

        if (filter == null) {
            return criteria;
        }

        applySummaryCriteria(filter, criteria);

        return criteria;
    }

    private void applySummaryCriteria(SummaryFilter filter, Criteria criteria) {
        // TODO: fixme
        //addModifiedDateCriteria(criteria, filter.getModifiedAfter(), filter.getModifiedBefore());

        if (filter.getFlow() != null) {
            criteria.and(DATA_SOURCE).is(filter.getFlow());
        }
    }

    private boolean filterStrings(BsonValue bsonValue) {
        return bsonValue != null && bsonValue.isString();
    }

    private String bsonValueAsString(BsonValue bsonValue) {
        return bsonValue.asString().getValue();
    }

    @Override
    public void setContentDeletedByDidIn(List<UUID> dids, OffsetDateTime now, String reason) {
        batchedBulkUpdateByIds(dids, new Update().set(CONTENT_DELETED, now)
                .set(CONTENT_DELETED_REASON, reason)
                .set(CONTENT_DELETABLE, false));
    }

    @Override
    public Long estimatedCount() {
        return mongoTemplate.execute(COLLECTION, MongoCollection::estimatedDocumentCount);
    }

    @Override
    public DeltaFileStats deltaFileStats() {
        List<AggregationOperation> aggregationOps = new ArrayList<>();
        Criteria inFlightCriteria = Criteria.where(IN_FLIGHT).is(true);
        aggregationOps.add(Aggregation.match(inFlightCriteria));

        ProjectionOperation project = Aggregation.project()
                .andExclude("_id")
                .andInclude(REFERENCED_BYTES);
        aggregationOps.add(project);

        aggregationOps.add(group("null").count().as(IN_FLIGHT_COUNT)
                .sum(REFERENCED_BYTES).as(IN_FLIGHT_BYTES));

        Aggregation aggregation = Aggregation.newAggregation(aggregationOps)
                .withOptions(AggregationOptions.builder().allowDiskUse(true).build());

        AggregationResults<DeltaFileStats> aggResults = mongoTemplate.aggregate(
                aggregation, COLLECTION, DeltaFileStats.class);

        if (aggResults.getMappedResults().isEmpty()) {
            return new DeltaFileStats(estimatedCount(), 0L, 0L);
        }

        DeltaFileStats deltaFileStats = aggResults.getMappedResults().getFirst();
        deltaFileStats.setTotalCount(estimatedCount());
        return deltaFileStats;
    }

    @Override
    public List<ColdQueuedActionSummary> coldQueuedActionsSummary() {
        Criteria stageCriteria = Criteria.where(IN_FLIGHT).is(true);
        MatchOperation matchStage = Aggregation.match(stageCriteria);

        UnwindOperation unwindFlows = Aggregation.unwind(FLOWS);
        UnwindOperation unwindActions = Aggregation.unwind(FLOWS_ACTIONS);

        Criteria actionStateCriteria = Criteria.where(ACTIONS_STATE).is("COLD_QUEUED");
        MatchOperation matchActionState = Aggregation.match(actionStateCriteria);

        ProjectionOperation projectFields = Aggregation.project()
                .and(ACTIONS_NAME).as(NAME)
                .and(ACTIONS_TYPE).as(TYPE);

        GroupOperation groupByActionNameAndType = Aggregation.group(NAME, TYPE).count().as(COUNT_LOWER_CASE);

        Aggregation aggregation = Aggregation.newAggregation(
                matchStage,
                unwindFlows,
                unwindActions,
                matchActionState,
                projectFields,
                groupByActionNameAndType
        ).withOptions(AggregationOptions.builder().allowDiskUse(true).build());

        AggregationResults<Document> aggResults = mongoTemplate.aggregate(aggregation, COLLECTION, Document.class);

        return aggResults.getMappedResults().stream()
                .map(doc -> {
                    String actionName = ((Document) doc.get("_id")).getString(NAME);
                    String actionType = ((Document) doc.get("_id")).getString(TYPE);
                    Integer count = doc.getInteger(COUNT_LOWER_CASE);
                    return new ColdQueuedActionSummary(actionName, ActionType.valueOf(actionType), count);
                })
                .toList();
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
                                              collect_id, delta_file_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?::jsonb, ?, ?, ?, ?)""";

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
            ps.setObject(16, flow.getDeltaFile().getDid());
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
        return offsetDateTime == null ? null : Timestamp.valueOf(offsetDateTime.toLocalDateTime());
    }

    private void batchedBulkUpdateByIds(List<UUID> dids, Update update) {
        if (dids == null || dids.isEmpty()) {
            return;
        }

        BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, DeltaFile.class);
        for (List<UUID> batch : Lists.partition(dids, 500)) {
            Query query = new Query().addCriteria(Criteria.where(ID).in(batch));
            bulkOps.updateMulti(query, update);
        }
        bulkOps.execute();
    }

    @Transactional
    public void batchedBulkDeleteByDidIn(List<UUID> dids) {
        if (dids == null || dids.isEmpty()) {
            return;
        }

        for (List<UUID> batch : Lists.partition(dids, 500)) {
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
