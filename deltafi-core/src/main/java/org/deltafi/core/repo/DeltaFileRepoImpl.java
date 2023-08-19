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

import com.google.common.collect.Lists;
import com.mongodb.client.MongoCollection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.BsonValue;
import org.bson.Document;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.common.types.ActionState;
import org.deltafi.common.types.DeltaFile;
import org.deltafi.common.types.DeltaFileStage;
import org.deltafi.core.generated.types.*;
import org.deltafi.core.types.DeltaFiles;
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
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

@SuppressWarnings("unused")
@RequiredArgsConstructor
@Slf4j
public class DeltaFileRepoImpl implements DeltaFileRepoCustom {
    public static final String ID = "_id";
    public static final String VERSION = "version";
    public static final String PARENT_DIDS = "parentDids";
    public static final String MODIFIED = "modified";
    public static final String CREATED = "created";
    public static final String STAGE = "stage";
    public static final String STATE = "state";
    public static final String ACTIONS_METADATA = "actions.metadata";
    public static final String ACTIONS_DELETE_METADATA_KEYS = "actions.deleteMetadataKeys";
    public static final String DOMAINS_NAME = "actions.domains.name";
    public static final String ENRICHMENTS_NAME = "actions.enrichments.name";
    public static final String CONTENT_DELETED = "contentDeleted";
    public static final String CONTENT_DELETED_REASON = "contentDeletedReason";
    public static final String KEY = "key";
    public static final String VALUE = "value";
    public static final String ERROR_CAUSE = "errorCause";
    public static final String FILTERED_CAUSE = "filteredCause";
    public static final String ERROR_ACKNOWLEDGED = "errorAcknowledged";
    public static final String EGRESSED = "egressed";
    public static final String EGRESS_FLOW = "egress.flow";
    public static final String FILTERED = "filtered";
    public static final String PENDING_ANNOTATIONS_FOR_FLOWS = "pendingAnnotationsForFlows";
    public static final String FIRST_PENDING_ANNOTATIONS_FOR_FLOWS = "pendingAnnotationsForFlows.0";
    public static final String TEST_MODE = "testMode";
    public static final String REFERENCED_BYTES = "referencedBytes";
    public static final String TOTAL_BYTES = "totalBytes";
    public static final String INGRESS_BYTES = "ingressBytes";
    public static final String REPLAYED = "replayed";
    public static final String REQUEUE_COUNT = "requeueCount";
    public static final String NEXT_AUTO_RESUME = "nextAutoResume";
    public static final String NEXT_AUTO_RESUME_REASON = "nextAutoResumeReason";
    public static final String SOURCE_INFO_FILENAME = "sourceInfo.filename";
    public static final String SOURCE_INFO_NORMALIZED_FILENAME = "sourceInfo.normalizedFilename";
    public static final String SOURCE_INFO_FLOW = "sourceInfo.flow";
    public static final String SOURCE_INFO_METADATA = "sourceInfo.metadata";
    public static final String SOURCE_INFO_PROCESSING_TYPE = "sourceInfo.processingType";
    public static final String FORMATTED_DATA_FILENAME = "formattedData.content.name";
    public static final String FORMATTED_DATA_FORMAT_ACTION = "formattedData.formatAction";
    public static final String FORMATTED_DATA_METADATA = "formattedData.metadata";
    public static final String FORMATTED_DATA_EGRESS_ACTIONS = "formattedData.egressActions";
    public static final String ACTIONS = "actions";
    public static final String ACTIONS_ATTEMPT = "actions.attempt";
    public static final String ACTIONS_ERROR_CAUSE = "actions.errorCause";
    public static final String ACTIONS_NAME = "actions.name";
    public static final String ACTIONS_TYPE = "actions.type";
    public static final String ACTIONS_FLOW = "actions.flow";
    public static final String ACTIONS_STATE = "actions.state";
    public static final String ACTIONS_MODIFIED = "actions.modified";
    public static final String ACTION_MODIFIED = "action.modified";
    public static final String ACTION_STATE = "action.state";
    public static final String ACTIONS_UPDATE_STATE = "actions.$[action].state";
    public static final String ACTIONS_UPDATE_MODIFIED = "actions.$[action].modified";
    public static final String ACTIONS_UPDATE_QUEUED = "actions.$[action].queued";
    public static final String ACTIONS_UPDATE_ERROR = "actions.$[action].errorCause";
    public static final String ACTIONS_UPDATE_ERROR_CONTEXT = "actions.$[action].errorContext";
    public static final String ACTIONS_UPDATE_HISTORY = "actions.$[action].history";
    private static final String COLLECTION = "deltaFile";
    private static final String TTL_INDEX_NAME = "ttl_index";
    private static final String SCHEMA_VERSION = "schemaVersion";
    // Aggregation variables
    private static final String COUNT_FOR_PAGING = "countForPaging";
    private static final String COUNT_LOWER_CASE = "count";
    private static final String DID = "did";
    private static final String DIDS = "dids";
    private static final String ERROR_MESSAGE = "errorMessage";
    private static final String FLOW_LOWER_CASE = "flow";
    private static final String GROUP_COUNT = "groupCount";
    private static final String ID_ERROR_MESSAGE = ID + "." + ERROR_MESSAGE;
    private static final String ID_FLOW = ID + "." + FLOW_LOWER_CASE;
    private static final String UNWIND_STATE = "unwindState";
    public static final String ANNOTATIONS = "annotations";
    public static final String ANNOTATION_KEYS = "annotationKeys";

    // these will automatically be up-converted when included as query fields, if present
    private static final String OLD_PROTOCOL_STACK_SEGMENTS = "protocolStack.content.contentReference.segments";
    private static final String OLD_PROTOCOL_STACK_SEGMENTS_2 = "protocolStack.content.segments";
    private static final String OLD_PROTOCOL_STACK_ACTION_NAME = "protocolStack.action";
    private static final String OLD_FORMATTED_DATA_SEGMENTS = "formattedData.contentReference.segments";
    private static final String OLD_FORMATTED_DATA_SEGMENTS_2 = "formattedData.content.segments";
    private static final String OLD_FORMATTED_DATA_ACTION_NAME = "formattedData.formatAction";

    private static final String ACTION_SEGMENTS = "actions.content.segments";

    private static final String CUMULATIVE_BYTES = "cumulativeBytes";
    private static final String OVER = "over";
    private static final String CUMULATIVE_OVER = "cumulativeOver";

    private static final Set<DeltaFileStage> TERMINAL_STAGES = Set.of(DeltaFileStage.COMPLETE, DeltaFileStage.ERROR, DeltaFileStage.CANCELLED);
    private static final Set<DeltaFileStage> ACTIVE_STAGES = Set.of(DeltaFileStage.INGRESS, DeltaFileStage.ENRICH, DeltaFileStage.EGRESS);

    public static final int MAX_COUNT = 50_000;

    static class FlowCountAndDids {
        TempSourceInfo sourceInfo;
        int groupCount;
        List<String> dids;

        static class TempSourceInfo {
            String flow;
        }
    }

    static class MessageFlowGroup {
        TempGroupId id;
        int groupCount;
        List<String> dids;

        static class TempGroupId {
            String errorMessage;
            String flow;
        }
    }

    private static final Map<String, Index> INDICES;
    static {
        INDICES = new HashMap<>();
        INDICES.put("queued_loop", new Index().named("queued_loop").on(REQUEUE_COUNT, Sort.Direction.DESC).on(STAGE, Sort.Direction.ASC));
        INDICES.put("completed_before_index", new Index().named("completed_before_index").on(STAGE, Sort.Direction.ASC).on(MODIFIED, Sort.Direction.ASC).on(SOURCE_INFO_FLOW, Sort.Direction.ASC));
        INDICES.put("created_before_index", new Index().named("created_before_index").on(CREATED, Sort.Direction.ASC).on(SOURCE_INFO_FLOW, Sort.Direction.ASC));
        INDICES.put("modified_before_index", new Index().named("modified_before_index").on(MODIFIED, Sort.Direction.ASC).on(SOURCE_INFO_FLOW, Sort.Direction.ASC));
        INDICES.put("auto_resume_index", new Index().named("auto_resume_index").on(NEXT_AUTO_RESUME, Sort.Direction.ASC).on(STAGE, Sort.Direction.ASC));
        INDICES.put("flow_first_index", new Index().named("flow_first_index").on(SOURCE_INFO_FLOW, Sort.Direction.ASC).on(MODIFIED, Sort.Direction.ASC));
        INDICES.put("metadata_index", new Index().named("metadata_index").on(ANNOTATIONS + ".$**", Sort.Direction.ASC));
        INDICES.put("domain_name_index", new Index().named("domain_name_index").on(DOMAINS_NAME, Sort.Direction.ASC));
        INDICES.put("metadata_keys_index", new Index().named("metadata_keys_index").on(ANNOTATION_KEYS, Sort.Direction.ASC));
        INDICES.put("disk_space_delete_index", new Index().named("disk_space_delete_index").on(CONTENT_DELETED, Sort.Direction.ASC).on(STAGE, Sort.Direction.ASC).on(CREATED, Sort.Direction.ASC).on(TOTAL_BYTES, Sort.Direction.ASC));
        INDICES.put("pending_annotations_for_flows_index", new Index().named("pending_annotations_for_flows_index").on(PENDING_ANNOTATIONS_FOR_FLOWS, Sort.Direction.ASC));
        INDICES.put("egress_flow_index", new Index().named("egress_flow_index").on(EGRESS_FLOW, Sort.Direction.ASC).on(MODIFIED, Sort.Direction.ASC));
        INDICES.put("processing_type_index", new Index().named("processing_type_index").on(SOURCE_INFO_PROCESSING_TYPE, Sort.Direction.ASC).on(MODIFIED, Sort.Direction.ASC));
        INDICES.put("filename_index", new Index().named("filename_index").on(SOURCE_INFO_FILENAME, Sort.Direction.ASC).on(MODIFIED, Sort.Direction.ASC));
        INDICES.put("normalized_filename_index", new Index().named("normalized_filename_index").on(SOURCE_INFO_NORMALIZED_FILENAME, Sort.Direction.ASC).on(MODIFIED, Sort.Direction.ASC));

        // partial index to support finding DeltaFiles that are pending annotations
        INDICES.put("first_pending_annotations_for_flows_index", new Index().named("first_pending_annotations_for_flows_index")
                .on(FIRST_PENDING_ANNOTATIONS_FOR_FLOWS, Sort.Direction.ASC).partial(PartialIndexFilter.of(Criteria.where(FIRST_PENDING_ANNOTATIONS_FOR_FLOWS).exists(true))).on(MODIFIED, Sort.Direction.ASC));

        // use partial indexes for boolean fields filtering on the more selective value
        INDICES.put("egressed_index", new Index().named("egressed_index").on(EGRESSED, Sort.Direction.ASC).partial(PartialIndexFilter.of(Criteria.where(EGRESSED).is(false))).on(MODIFIED, Sort.Direction.ASC));
        INDICES.put("test_mode_index", new Index().named("test_mode_index").on(TEST_MODE, Sort.Direction.ASC).partial(PartialIndexFilter.of(Criteria.where(TEST_MODE).is(true))).on(MODIFIED, Sort.Direction.ASC));
        INDICES.put("filtered_index", new Index().named("filtered_index").on(FILTERED, Sort.Direction.ASC).partial(PartialIndexFilter.of(Criteria.where(FILTERED).is(true))).on(MODIFIED, Sort.Direction.ASC));
    }

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
        if (Objects.isNull(cachedTtlDuration)) {
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

    public List<String> readDidsWithContent() {
        Criteria criteria = new Criteria(CONTENT_DELETED).isNull();
        Query query = new Query();
        query.addCriteria(criteria);
        return mongoTemplate.findDistinct(query, ID, DeltaFile.class, String.class);
    }

    @Override
    public List<DeltaFile> updateForRequeue(OffsetDateTime requeueTime, int requeueSeconds) {
        List<DeltaFile> filesToRequeue = mongoTemplate.find(buildReadyForRequeueQuery(requeueTime, requeueSeconds), DeltaFile.class);
        List<DeltaFile> requeuedDeltaFiles = new ArrayList<>();
        for (List<DeltaFile> batch : Lists.partition(filesToRequeue, 1000)) {
            List<String> dids = batch.stream().map(DeltaFile::getDid).toList();
            Query query = new Query().addCriteria(Criteria.where(ID).in(dids));
            mongoTemplate.updateMulti(query, buildRequeueUpdate(requeueTime, requeueSeconds), DeltaFile.class);
            requeuedDeltaFiles.addAll(mongoTemplate.find(query, DeltaFile.class));
        }

        return requeuedDeltaFiles;
    }

    @Override
    public List<DeltaFile> findReadyForAutoResume(OffsetDateTime maxReadyTime) {
        return mongoTemplate.find(buildReadyForAutoResume(maxReadyTime), DeltaFile.class);
    }

    private Query buildReadyForAutoResume(OffsetDateTime maxReadyTime) {
        Criteria inError = Criteria.where(STAGE).is(DeltaFileStage.ERROR);
        Criteria ready = Criteria.where(NEXT_AUTO_RESUME).lt(maxReadyTime);

        Query requeueQuery = new Query(new Criteria().andOperator(inError, ready));
        requeueQuery.fields().include(ID, SOURCE_INFO_FLOW);

        return requeueQuery;
    }

    @Override
    public List<DeltaFile> findResumePolicyCandidates(String flowName) {
        return mongoTemplate.find(buildResumePolicyCanidatesQuery(flowName), DeltaFile.class);
    }

    private Query buildResumePolicyCanidatesQuery(String flowName) {
        List<Criteria> andCriteria = new ArrayList<>();

        andCriteria.add(Criteria.where(STAGE).is(DeltaFileStage.ERROR));
        andCriteria.add(Criteria.where(NEXT_AUTO_RESUME).isNull());
        andCriteria.add(Criteria.where(ERROR_ACKNOWLEDGED).isNull());
        andCriteria.add(Criteria.where(CONTENT_DELETED).isNull());

        if (nonNull(flowName)) {
            andCriteria.add(Criteria.where(SOURCE_INFO_FLOW).is(flowName));
        }

        Query requeueQuery = new Query(new Criteria().andOperator(andCriteria.toArray(new Criteria[0])));
        requeueQuery.fields().include(ID, SOURCE_INFO_FLOW, ACTIONS_NAME, ACTIONS_ERROR_CAUSE, ACTIONS_STATE, ACTIONS_TYPE, ACTIONS_ATTEMPT);

        return requeueQuery;
    }

    @Override
    public void updateForAutoResume(List<String> dids, String policyName, OffsetDateTime nextAutoResume) {
        if (dids != null && !dids.isEmpty()) {
            BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, DeltaFile.class);
            Update update = new Update().set(NEXT_AUTO_RESUME, nextAutoResume).set(NEXT_AUTO_RESUME_REASON, policyName);
            for (List<String> batch : Lists.partition(dids, 1000)) {
                Query query = new Query().addCriteria(Criteria.where(ID).in(batch));
                bulkOps.updateMulti(query, update);
            }
            bulkOps.execute();
        }
    }

    @Override
    public List<DeltaFile> findForDelete(OffsetDateTime createdBeforeDate, OffsetDateTime completedBeforeDate,
                                         long minBytes, String flowName, String policy, boolean deleteMetadata,
                                         int batchSize) {
        // one of these must be set for any matches to occur
        if (isNull(createdBeforeDate) && isNull(completedBeforeDate)) {
            return Collections.emptyList();
        }

        Query query = new Query(buildReadyForDeleteCriteria(createdBeforeDate, completedBeforeDate, minBytes, flowName, deleteMetadata, false));
        query.limit(batchSize);
        addDeltaFilesOrderBy(query, DeltaFileOrder.newBuilder().field(CREATED).direction(DeltaFileDirection.ASC).build());
        query.fields().include(ID, TOTAL_BYTES, OLD_PROTOCOL_STACK_SEGMENTS, OLD_PROTOCOL_STACK_SEGMENTS_2, ACTION_SEGMENTS, OLD_FORMATTED_DATA_SEGMENTS, OLD_FORMATTED_DATA_SEGMENTS_2, OLD_PROTOCOL_STACK_ACTION_NAME, OLD_FORMATTED_DATA_ACTION_NAME, ACTIONS_NAME);

        return mongoTemplate.find(query, DeltaFile.class);
    }

    @Override
    public List<DeltaFile> findForDelete(long bytesToDelete, String flow, String policy, int batchSize) {
        if (bytesToDelete < 1) {
            throw new IllegalArgumentException("bytesToDelete (" + bytesToDelete + ") must be positive");
        }

        Query query = new Query(buildReadyForDeleteCriteria(null, null, 1, flow, false, true));
        query.limit(batchSize);
        addDeltaFilesOrderBy(query, DeltaFileOrder.newBuilder().field(CREATED).direction(DeltaFileDirection.ASC).build());
        query.fields().include(ID, TOTAL_BYTES, OLD_PROTOCOL_STACK_SEGMENTS, OLD_PROTOCOL_STACK_SEGMENTS_2, ACTION_SEGMENTS, OLD_FORMATTED_DATA_SEGMENTS, OLD_FORMATTED_DATA_SEGMENTS_2, OLD_PROTOCOL_STACK_ACTION_NAME, OLD_FORMATTED_DATA_ACTION_NAME, ACTIONS_NAME);

        List<DeltaFile> deltaFiles = mongoTemplate.find(query, DeltaFile.class);
        AtomicLong sum = new AtomicLong();
        AtomicBoolean met = new AtomicBoolean(false);
        return deltaFiles.stream()
                .takeWhile(d -> {
                    // hacky inclusive takeWhile because we need n + 1 to go over bytesToDelete
                    boolean done = sum.addAndGet(d.getTotalBytes()) >= bytesToDelete && met.get();
                    met.set(sum.get() >= bytesToDelete);
                    return !done;
                })
                .toList();
    }

    @Override
    public DeltaFiles deltaFiles(Integer offset, int limit, DeltaFilesFilter filter, DeltaFileOrder orderBy) {
        return deltaFiles(offset, limit, filter, orderBy, null);
    }

    @Override
    public DeltaFiles deltaFiles(Integer offset, int limit, DeltaFilesFilter filter, DeltaFileOrder orderBy, List<String> includeFields) {
        Criteria filterCriteria = buildDeltaFilesCriteria(filter);
        Query query = new Query(filterCriteria);

        if (nonNull(offset) && offset > 0) {
            query.skip(offset);
        } else {
            offset = 0;
        }

        query.limit(limit);

        if (Objects.nonNull(includeFields)) {
            query.fields().include(SCHEMA_VERSION);
            for (String includeField : includeFields) {
                query.fields().include(includeField);
            }
        }

        addDeltaFilesOrderBy(query, orderBy);

        DeltaFiles deltaFiles = new DeltaFiles();
        deltaFiles.setOffset(offset);
        if (Objects.nonNull(includeFields) && includeFields.isEmpty()) {
            deltaFiles.setDeltaFiles(Collections.emptyList());
        } else {
            deltaFiles.setDeltaFiles(mongoTemplate.find(query, DeltaFile.class));
        }
        deltaFiles.setCount(deltaFiles.getDeltaFiles().size());
        if ((Objects.isNull(includeFields) || !includeFields.isEmpty()) && deltaFiles.getCount() < limit) {
            deltaFiles.setTotalCount(deltaFiles.getOffset() + deltaFiles.getCount());
        } else {
            int total = (int) mongoTemplate.count(new Query(filterCriteria).limit(MAX_COUNT), DeltaFile.class);
            deltaFiles.setTotalCount(total);
        }

        return deltaFiles;
    }

    Update buildRequeueUpdate(OffsetDateTime modified, int requeueSeconds) {
        Update update = new Update();
        update.inc(REQUEUE_COUNT, 1);

        // clear out any old error messages
        update.set(ACTIONS_UPDATE_ERROR, null);
        update.set(ACTIONS_UPDATE_ERROR_CONTEXT, null);
        update.set(ACTIONS_UPDATE_MODIFIED, modified);
        update.set(ACTIONS_UPDATE_QUEUED, modified);

        update.set(MODIFIED, nonNull(modified) ? modified : OffsetDateTime.now());

        Criteria queued = Criteria.where(ACTION_STATE).is(ActionState.QUEUED.name());

        long epochMs = requeueThreshold(modified, requeueSeconds).toInstant().toEpochMilli();
        Criteria expired = Criteria.where(ACTION_MODIFIED).lt(new Date(epochMs));

        update.filterArray(new Criteria().andOperator(queued, expired));

        return update;
    }

    private Query buildReadyForRequeueQuery(OffsetDateTime requeueTime, int requeueSeconds) {
        Criteria notComplete = Criteria.where(STAGE).not().in(DeltaFileStage.COMPLETE, DeltaFileStage.ERROR, DeltaFileStage.CANCELLED);

        long epochMs = requeueThreshold(requeueTime, requeueSeconds).toInstant().toEpochMilli();
        Criteria expired = Criteria.where(MODIFIED).lt(new Date(epochMs));

        Query requeueQuery = new Query(new Criteria().andOperator(notComplete, expired));
        requeueQuery.fields().include(ID);

        return requeueQuery;
    }

    private Criteria buildReadyForDeleteCriteria(OffsetDateTime createdBeforeDate, OffsetDateTime completedBeforeDate,
            long minBytes, String flowName, boolean deleteMetadata, boolean completeOnly) {
        Criteria criteria = new Criteria();
        List<Criteria> andCriteria = new ArrayList<>();

        if (createdBeforeDate != null || completedBeforeDate != null) {
            andCriteria.add(buildDeleteTimeCriteria(createdBeforeDate, completedBeforeDate));
        }

        if (nonNull(flowName)) {
            andCriteria.add(Criteria.where(SOURCE_INFO_FLOW).is(flowName));
        }

        if (minBytes > 0L) {
            andCriteria.add(Criteria.where(TOTAL_BYTES).gte(minBytes));
        }

        if (!deleteMetadata) {
            andCriteria.add(Criteria.where(CONTENT_DELETED).isNull());
        }

        if (completeOnly) {
            andCriteria.add(Criteria.where(STAGE).in(DeltaFileStage.COMPLETE, DeltaFileStage.CANCELLED));
        }

        andCriteria.add(Criteria.where(PENDING_ANNOTATIONS_FOR_FLOWS).is(null));

        if (andCriteria.size() == 1) {
            criteria = andCriteria.get(0);
        } else {
            criteria.andOperator(andCriteria.toArray(new Criteria[0]));
        }

        return criteria;
    }

    private Criteria buildDeltaFilesCriteria(DeltaFilesFilter filter) {
        Criteria criteria = new Criteria();

        if (isNull(filter)) {
            return criteria;
        }

        List<Criteria> andCriteria = new ArrayList<>();

        if (filter.getEgressFlows() != null && !filter.getEgressFlows().isEmpty()) {
            andCriteria.add(Criteria.where(EGRESS_FLOW).in(filter.getEgressFlows()));
        }

        if (nonNull(filter.getDids()) && !filter.getDids().isEmpty()) {
            andCriteria.add(Criteria.where(ID).in(filter.getDids()));
        }

        if (nonNull(filter.getParentDid())) {
            andCriteria.add(Criteria.where(PARENT_DIDS).in(filter.getParentDid()));
        }

        if (nonNull(filter.getCreatedAfter())) {
            andCriteria.add(Criteria.where(CREATED).gt(filter.getCreatedAfter()));
        }

        if (nonNull(filter.getCreatedBefore())) {
            andCriteria.add(Criteria.where(CREATED).lt(filter.getCreatedBefore()));
        }

        if (nonNull(filter.getDomains()) && !filter.getDomains().isEmpty()) {
            andCriteria.add(Criteria.where(DOMAINS_NAME).all(filter.getDomains()));
        }

        if (nonNull(filter.getEnrichments()) && !filter.getEnrichments().isEmpty()) {
            andCriteria.add(Criteria.where(ENRICHMENTS_NAME).all(filter.getEnrichments()));
        }

        if (nonNull(filter.getAnnotations())) {
            List<Criteria> metadataCriteria = filter.getAnnotations().stream()
                    .map(e -> fromAnnotations(e.getKey(), e.getValue())).filter(Objects::nonNull).toList();
            andCriteria.addAll(metadataCriteria);
        }

        if (nonNull(filter.getContentDeleted())) {
            if (filter.getContentDeleted()) {
                andCriteria.add(Criteria.where(CONTENT_DELETED).ne(null));
            } else {
                andCriteria.add(Criteria.where(CONTENT_DELETED).is(null));
            }
        }

        if (nonNull(filter.getModifiedAfter())) {
            andCriteria.add(Criteria.where(MODIFIED).gt(filter.getModifiedAfter()));
        }

        if (nonNull(filter.getModifiedBefore())) {
            andCriteria.add(Criteria.where(MODIFIED).lt(filter.getModifiedBefore()));
        }

        if (nonNull(filter.getStage()) && nonNull(filter.getTerminalStage())) {
            Set<DeltaFileStage> stages = new HashSet<>(filter.getTerminalStage() ? TERMINAL_STAGES : ACTIVE_STAGES);
            stages.add(filter.getStage());
            andCriteria.add(Criteria.where(STAGE).in(stages));
        } else if (nonNull(filter.getTerminalStage())) {
            andCriteria.add(Criteria.where(STAGE).in(filter.getTerminalStage() ? TERMINAL_STAGES : ACTIVE_STAGES));
        } else if (nonNull(filter.getStage())) {
            andCriteria.add(Criteria.where(STAGE).is(filter.getStage().name()));
        }

        if (nonNull(filter.getSourceInfo())) {
            if (nonNull(filter.getSourceInfo().getFilename()) && filter.getSourceInfo().getFilenameFilter() == null) {
                // fall back to using the filename if it is set and filenameFilter is null
                FilenameFilter filenameFilter = new FilenameFilter(".*" + filter.getSourceInfo().getFilename() + ".*", true, false);
                filter.getSourceInfo().setFilenameFilter(filenameFilter);
            }

            if (nonNull(filter.getSourceInfo().getFilenameFilter())) {
                andCriteria.add(filenameCriteria(filter.getSourceInfo().getFilenameFilter()));
            }

            if (nonNull(filter.getSourceInfo().getFlow())) {
                andCriteria.add(Criteria.where(SOURCE_INFO_FLOW).is(filter.getSourceInfo().getFlow()));
            }

            if (nonNull(filter.getSourceInfo().getIngressFlows())) {
                andCriteria.add(Criteria.where(SOURCE_INFO_FLOW).in(filter.getSourceInfo().getIngressFlows()));
            }

            if (nonNull(filter.getSourceInfo().getMetadata())) {
                filter.getSourceInfo().getMetadata().forEach(keyValue -> {
                    String searchKey = keyValue.getKey();
                    if (searchKey.contains(".")) {
                        searchKey = StringUtils.replace(keyValue.getKey(), ".", DeltaFiConstants.MONGO_MAP_KEY_DOT_REPLACEMENT);
                    }
                    andCriteria.add(Criteria.where(SOURCE_INFO_METADATA + "." + searchKey).is(keyValue.getValue()));
                });
            }

            if (nonNull(filter.getSourceInfo().getProcessingType())) {
                andCriteria.add(Criteria.where(SOURCE_INFO_PROCESSING_TYPE).is(filter.getSourceInfo().getProcessingType()));
            }
        }

        if (nonNull(filter.getActions()) && !filter.getActions().isEmpty()) {
            andCriteria.add(Criteria.where(ACTIONS_NAME).all(filter.getActions()));
        }

        if (nonNull(filter.getErrorCause())) {
            Criteria actionElemMatch = new Criteria().andOperator(Criteria.where(STATE).is(ActionState.ERROR),
                    Criteria.where(ERROR_CAUSE).regex(filter.getErrorCause()));
            andCriteria.add(Criteria.where(ACTIONS).elemMatch(actionElemMatch));
        }

        if (filter.getFilteredCause() != null) {
            boolean filtered = filter.getFiltered() != null ? filter.getFiltered() : true;
            andCriteria.add(Criteria.where(FILTERED).is(filtered));
            Criteria actionElemMatch = new Criteria().andOperator(Criteria.where(STATE).is(ActionState.FILTERED),
                    Criteria.where(FILTERED_CAUSE).regex(filter.getFilteredCause()));
            andCriteria.add(Criteria.where(ACTIONS).elemMatch(actionElemMatch));
        }

        if (nonNull(filter.getRequeueCountMin())) {
            andCriteria.add(Criteria.where(REQUEUE_COUNT).gte(filter.getRequeueCountMin()));
        }

        if (nonNull(filter.getIngressBytesMin())) {
            andCriteria.add(Criteria.where(INGRESS_BYTES).gte(filter.getIngressBytesMin()));
        }

        if (nonNull(filter.getIngressBytesMax())) {
            andCriteria.add(Criteria.where(INGRESS_BYTES).lte(filter.getIngressBytesMax()));
        }

        if (nonNull(filter.getReferencedBytesMin())) {
            andCriteria.add(Criteria.where(REFERENCED_BYTES).gte(filter.getReferencedBytesMin()));
        }

        if (nonNull(filter.getReferencedBytesMax())) {
            andCriteria.add(Criteria.where(REFERENCED_BYTES).lte(filter.getReferencedBytesMax()));
        }

        if (nonNull(filter.getTotalBytesMin())) {
            andCriteria.add(Criteria.where(TOTAL_BYTES).gte(filter.getTotalBytesMin()));
        }

        if (nonNull(filter.getTotalBytesMax())) {
            andCriteria.add(Criteria.where(TOTAL_BYTES).lte(filter.getTotalBytesMax()));
        }

        if (nonNull(filter.getErrorAcknowledged())) {
            if (filter.getErrorAcknowledged()) {
                andCriteria.add(Criteria.where(ERROR_ACKNOWLEDGED).ne(null));
            } else {
                andCriteria.add(Criteria.where(ERROR_ACKNOWLEDGED).is(null));
            }
        }

        if (nonNull(filter.getEgressed())) {
            andCriteria.add(Criteria.where(EGRESSED).is(filter.getEgressed()));
        }

        if (nonNull(filter.getFiltered())) {
            andCriteria.add(Criteria.where(FILTERED).is(filter.getFiltered()));
        }

        if (nonNull(filter.getTestMode())) {
            if (filter.getTestMode()) {
                andCriteria.add(Criteria.where(TEST_MODE).is(true));
            } else {
                andCriteria.add(Criteria.where(TEST_MODE).ne(true));
            }
        }

        if (nonNull(filter.getReplayable())) {
            if (filter.getReplayable()) {
                andCriteria.add(Criteria.where(REPLAYED).isNull());
                andCriteria.add(Criteria.where(CONTENT_DELETED).isNull());
            } else {
                andCriteria.add(new Criteria().orOperator(Criteria.where(REPLAYED).ne(null), Criteria.where(CONTENT_DELETED).ne(null)));
            }
        }

        if (nonNull(filter.getReplayed())) {
            if (filter.getReplayed()) {
                andCriteria.add(Criteria.where(REPLAYED).ne(null));
            } else {
                andCriteria.add(Criteria.where(REPLAYED).isNull());
            }
        }

        if (nonNull(filter.getPendingAnnotations())) {
            if (filter.getPendingAnnotations()) {
                andCriteria.add(Criteria.where(FIRST_PENDING_ANNOTATIONS_FOR_FLOWS).exists(true));
            } else {
                andCriteria.add(Criteria.where(PENDING_ANNOTATIONS_FOR_FLOWS).is(null));
            }
        }

        if (!andCriteria.isEmpty()) {
            if (andCriteria.size() == 1) {
                criteria = andCriteria.get(0);
            } else {
                criteria.andOperator(andCriteria.toArray(new Criteria[0]));
            }
        }

        return criteria;
    }

    private Criteria fromAnnotations(String key, String value) {
        if (null == key || null == value) {
            return null;
        }

        if (key.contains(".")) {
            key = StringUtils.replace(key, ".", DeltaFiConstants.MONGO_MAP_KEY_DOT_REPLACEMENT);
        }

        return Criteria.where(ANNOTATIONS + "." + key).is(value);
    }

    private void addDeltaFilesOrderBy(Query query, DeltaFileOrder orderBy) {
        if (isNull(orderBy)) {
            orderBy = DeltaFileOrder.newBuilder().field(CREATED).direction(DeltaFileDirection.DESC).build();
        }

        query.with(Sort.by(Collections.singletonList(new Sort.Order(Sort.Direction.fromString(orderBy.getDirection().name()), orderBy.getField()))));
    }

    private Criteria buildDeleteTimeCriteria(OffsetDateTime createdBeforeDate, OffsetDateTime completedBeforeDate) {
        Criteria createdBeforeCriteria = nonNull(createdBeforeDate) ? createdBeforeCriteria(createdBeforeDate) : null;
        Criteria completedBeforeCriteria = nonNull(completedBeforeDate) ? completedBeforeCriteria(completedBeforeDate) : null;

        if (nonNull(createdBeforeCriteria) && isNull(completedBeforeCriteria)) {
            return createdBeforeCriteria;
        }

        if (nonNull(completedBeforeCriteria) && isNull(createdBeforeCriteria)) {
            return completedBeforeCriteria;
        }

        return new Criteria().orOperator(createdBeforeCriteria, completedBeforeCriteria);
    }

    private Criteria createdBeforeCriteria(OffsetDateTime createdBeforeDate) {
        return Criteria.where(CREATED).lt(createdBeforeDate);
    }

    private Criteria completedBeforeCriteria(OffsetDateTime completedBeforeDate) {
        Criteria completed = Criteria.where(STAGE).in(DeltaFileStage.COMPLETE);
        Criteria acknowledged = new Criteria().andOperator(
                Criteria.where(STAGE).is(DeltaFileStage.ERROR),
                Criteria.where(ERROR_ACKNOWLEDGED).ne(null));
        Criteria completedOrAcknowledged = new Criteria().orOperator(completed, acknowledged);
        Criteria lastModified = Criteria.where(MODIFIED).lt(completedBeforeDate);
        return new Criteria().andOperator(completedOrAcknowledged, lastModified);
    }

    private OffsetDateTime requeueThreshold(OffsetDateTime requeueTime, int requeueSeconds) {
        return requeueTime.minusSeconds(requeueSeconds);
    }


    private void removeUnknownIndices(IndexOperations idxOps, IndexInfo existing, Set<String> knownIndicies) {
        if (!knownIndicies.contains(existing.getName())) {
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
    public ErrorsByFlow getErrorSummaryByFlow(Integer offset, int limit, ErrorSummaryFilter filter, DeltaFileOrder orderBy) {

        long elementsToSkip = (nonNull(offset) && offset > 0) ? offset : 0;

        MatchOperation matchesErrorStage = Aggregation.match(buildErrorSummaryCriteria(filter));

        Aggregation countAggregation = Aggregation.newAggregation(
                matchesErrorStage,
                group(SOURCE_INFO_FLOW).count().as(GROUP_COUNT),
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
                    group(SOURCE_INFO_FLOW).count().as(GROUP_COUNT).push(ID).as(DIDS),
                    project(DIDS, GROUP_COUNT).and(SOURCE_INFO_FLOW).previousOperation(),
                    errorSummaryByFlowSort(orderBy),
                    skip(elementsToSkip),
                    limit(limit))
                    .withOptions(AggregationOptions.builder().allowDiskUse(true).build());

            AggregationResults<FlowCountAndDids> aggResults = mongoTemplate.aggregate(
                    pagingAggregation, COLLECTION, FlowCountAndDids.class);

            for (FlowCountAndDids r : aggResults.getMappedResults()) {
                countPerFlow.add(CountPerFlow.newBuilder()
                        .flow(r.sourceInfo.flow)
                        .count(r.groupCount)
                        .dids(r.dids)
                        .build()
                );
            }
        }

        return ErrorsByFlow.newBuilder()
                .count(countPerFlow.size())
                .countPerFlow(countPerFlow)
                .offset((int) elementsToSkip)
                .totalCount(countForPaging.intValue())
                .build();
    }

    public ErrorsByMessage getErrorSummaryByMessage(Integer offset, int limit, ErrorSummaryFilter filter, DeltaFileOrder orderBy) {

        long elementsToSkip = (nonNull(offset) && offset > 0) ? offset : 0;

        MatchOperation matchesErrorStage = Aggregation.match(buildErrorSummaryCriteria(filter));

        GroupOperation groupByCauseAndFlow = Aggregation.group(ERROR_MESSAGE, FLOW_LOWER_CASE)
                .count().as(GROUP_COUNT)
                .push(DID).as(DIDS);

        List<AggregationOperation> mainStages = Arrays.asList(
                matchesErrorStage,
                unwind(ACTIONS),
                project()
                        .and(SOURCE_INFO_FLOW).as(FLOW_LOWER_CASE)
                        .and(ID).as(DID)
                        .and(ACTIONS_ERROR_CAUSE).as(ERROR_MESSAGE)
                        .and(ACTIONS_STATE).as(UNWIND_STATE),
                match(Criteria.where(UNWIND_STATE).is(ActionState.ERROR)),
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
                        .flow(groupResult.id.flow)
                        .count(groupResult.dids.size())
                        .dids(groupResult.dids)
                        .build());
            }
        }

        return ErrorsByMessage.newBuilder()
                .count(messageList.size())
                .offset((int) elementsToSkip)
                .totalCount(countForPaging.intValue())
                .countPerMessage(messageList)
                .build();
    }

    public Map<String, Integer> errorCountsByFlow(Set<String> flows) {
        // Match flows in the given set, ERROR_ACKNOWLEDGED is null, and STAGE is DeltaFileStage.ERROR
        Criteria flowsCriteria = Criteria.where(SOURCE_INFO_FLOW).in(flows)
                .and(ERROR_ACKNOWLEDGED).is(null)
                .and(STAGE).is(DeltaFileStage.ERROR);
        MatchOperation matchFlowsStage = Aggregation.match(flowsCriteria);

        // Group by flow and count errors
        GroupOperation groupByFlowAndCountErrorsStage = Aggregation.group(SOURCE_INFO_FLOW).count().as("errorCount");

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
        String sortField = SOURCE_INFO_FLOW;
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
        String secondaryField = ID_FLOW;
        Sort.Direction direction = Sort.Direction.ASC;

        if (orderBy != null) {
            direction = Sort.Direction.fromString(orderBy.getDirection().name());

            String requestedField = orderBy.getField().toLowerCase(Locale.ROOT);
            if (requestedField.contains(FLOW_LOWER_CASE)) {
                sortField = ID_FLOW;
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

        if (isNull(filter)) {
            return criteria;
        }

        List<Criteria> andCriteria = new ArrayList<>();
        andCriteria.add(criteria);

        if (nonNull(filter.getModifiedAfter())) {
            andCriteria.add(Criteria.where(MODIFIED).gt(filter.getModifiedAfter()));
        }

        if (nonNull(filter.getModifiedBefore())) {
            andCriteria.add(Criteria.where(MODIFIED).lt(filter.getModifiedBefore()));
        }

        if (nonNull(filter.getErrorAcknowledged())) {
            if (filter.getErrorAcknowledged()) {
                andCriteria.add(Criteria.where(ERROR_ACKNOWLEDGED).ne(null));
            } else {
                andCriteria.add(Criteria.where(ERROR_ACKNOWLEDGED).is(null));
            }
        }

        if (nonNull(filter.getFlow())) {
            andCriteria.add(Criteria.where(SOURCE_INFO_FLOW).is(filter.getFlow()));
        }

        if (andCriteria.size() > 1) {
            criteria.andOperator(andCriteria.toArray(new Criteria[0]));
        }

        return criteria;
    }

    @Override
    public List<String> domains() {
        return mongoTemplate.findDistinct(new Query(), DOMAINS_NAME, DeltaFile.class, String.class).stream()
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public List<String> annotationKeys(String domain) {
        Query query = new Query();
        if (domain != null && !domain.isEmpty()) {
            query.addCriteria(Criteria.where(DOMAINS_NAME).is(domain));
        }

        return mongoTemplate.findDistinct(query, ANNOTATION_KEYS, DeltaFile.class, BsonValue.class).stream()
                .filter(this::filterStrings)
                .map(this::bsonValueAsString)
                .toList();
    }

    private boolean filterStrings(BsonValue bsonValue) {
        return bsonValue != null && bsonValue.isString();
    }

    private String bsonValueAsString(BsonValue bsonValue) {
        return bsonValue.asString().getValue();
    }

    @Override
    public void setContentDeletedByDidIn(List<String> dids, OffsetDateTime now, String reason) {
        BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, DeltaFile.class);
        Update update = new Update().set(CONTENT_DELETED, now).set(CONTENT_DELETED_REASON, reason);
        for (List<String> batch : Lists.partition(dids, 1000)) {
            Query query = new Query().addCriteria(Criteria.where(ID).in(batch));
            bulkOps.updateMulti(query, update);
        }
        bulkOps.execute();
    }

    @Override
    public Long estimatedCount() {
        return mongoTemplate.execute(COLLECTION, MongoCollection::estimatedDocumentCount);
    }

    @Override
    public DeltaFileStats deltaFileStats(boolean inFlightOnly, boolean includeDeletedContent) {
        List<AggregationOperation> aggregationOps = new ArrayList<>();
        List<Criteria> criteriaList = new ArrayList<>();
        if (inFlightOnly) {
            criteriaList.add(Criteria.where(STAGE).in(DeltaFileStage.INGRESS,
                    DeltaFileStage.ENRICH, DeltaFileStage.EGRESS));
        }
        if (!includeDeletedContent) {
            criteriaList.add(Criteria.where(CONTENT_DELETED).isNull());
        }
        if (!criteriaList.isEmpty()) {
            Criteria andCriteria = new Criteria();
            andCriteria.andOperator(criteriaList);
            MatchOperation match = Aggregation.match(andCriteria);
            aggregationOps.add(match);
        }
        aggregationOps.add(group("null").count().as("count")
                .sum("totalBytes").as("totalBytes")
                .sum("referencedBytes").as("referencedBytes"));

        Aggregation aggregation = Aggregation.newAggregation(aggregationOps)
                .withOptions(AggregationOptions.builder().allowDiskUse(true).build());

        AggregationResults<DeltaFileStats> aggResults = mongoTemplate.aggregate(
                aggregation, COLLECTION, DeltaFileStats.class);

        if (aggResults.getMappedResults().isEmpty()) {
            return new DeltaFileStats(0, 0L, 0L);
        }

        return aggResults.getMappedResults().get(0);
    }

    @Override
    public void removePendingAnnotationsForFlow(String flow) {
        pullFlowFromPendingAnnotationsForFlow(flow);
        unsetEmptyPendingAnnotationsForFlow();
    }

    private void pullFlowFromPendingAnnotationsForFlow(String flow) {
        Query query = Query.query(Criteria.where(PENDING_ANNOTATIONS_FOR_FLOWS).is(flow));
        Update pullOutFlow = new Update().pull(PENDING_ANNOTATIONS_FOR_FLOWS, flow);
        mongoTemplate.updateMulti(query, pullOutFlow, DeltaFile.class);
    }

    private void unsetEmptyPendingAnnotationsForFlow() {
        Query query = Query.query(Criteria.where(PENDING_ANNOTATIONS_FOR_FLOWS).is(List.of()));
        Update unsetEmptyList = new Update().unset(PENDING_ANNOTATIONS_FOR_FLOWS);
        mongoTemplate.updateMulti(query, unsetEmptyList, DeltaFile.class);
    }

    private Criteria filenameCriteria(FilenameFilter filenameFilter) {
        String filename = filenameFilter.getFilename();
        String searchField = SOURCE_INFO_NORMALIZED_FILENAME;

        if (!Boolean.TRUE.equals(filenameFilter.getCaseSensitive())) {
            filename = filename != null ? filename.toLowerCase() : null;
        } else {
            searchField = SOURCE_INFO_FILENAME;
        }

        // use the exact match criteria by default
        return Boolean.TRUE.equals(filenameFilter.getRegex()) ?
            Criteria.where(searchField).regex(filename) :
            Criteria.where(searchField).is(filename);
    }
}
