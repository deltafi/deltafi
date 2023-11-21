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
import com.mongodb.client.result.UpdateResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.BsonValue;
import org.bson.Document;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.common.types.*;
import org.deltafi.core.generated.types.*;
import org.deltafi.core.types.ColdQueuedActionSummary;
import org.deltafi.core.types.DeltaFiles;
import org.deltafi.core.types.ErrorSummaryFilter;
import org.deltafi.core.types.SummaryByFlow;
import org.deltafi.core.types.FilteredSummaryFilter;
import org.deltafi.core.types.SummaryByFlowAndMessage;
import org.deltafi.core.types.SummaryFilter;
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

import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.deltafi.common.types.ActionState.*;
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
    public static final String NAME = "name";
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
    public static final String ACTIONS_FILTERED_CAUSE = "actions.filteredCause";
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

    private static final String IN_FLIGHT = "inFlight";
    private static final String IN_FLIGHT_COUNT = "inFlightCount";
    private static final String IN_FLIGHT_BYTES = "inFlightBytes";

    private static final String TERMINAL = "terminal";
    private static final String CONTENT_DELETABLE = "contentDeletable";

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
        INDICES.put("created_before_index", new Index().named("created_before_index").on(CREATED, Sort.Direction.ASC).on(SOURCE_INFO_FLOW, Sort.Direction.ASC));
        INDICES.put("modified_before_index", new Index().named("modified_before_index").on(MODIFIED, Sort.Direction.ASC).on(SOURCE_INFO_FLOW, Sort.Direction.ASC));
        INDICES.put("auto_resume_index", new Index().named("auto_resume_index").on(NEXT_AUTO_RESUME, Sort.Direction.ASC).on(STAGE, Sort.Direction.ASC));
        INDICES.put("flow_first_index", new Index().named("flow_first_index").on(SOURCE_INFO_FLOW, Sort.Direction.ASC).on(SOURCE_INFO_NORMALIZED_FILENAME, Sort.Direction.ASC).on(MODIFIED, Sort.Direction.ASC));
        INDICES.put("metadata_index", new Index().named("metadata_index").on(ANNOTATIONS + ".$**", Sort.Direction.ASC));
        INDICES.put("domain_name_index", new Index().named("domain_name_index").on(DOMAINS_NAME, Sort.Direction.ASC).sparse());
        INDICES.put("metadata_keys_index", new Index().named("metadata_keys_index").on(ANNOTATION_KEYS, Sort.Direction.ASC).sparse());
        INDICES.put("pending_annotations_for_flows_index", new Index().named("pending_annotations_for_flows_index").on(PENDING_ANNOTATIONS_FOR_FLOWS, Sort.Direction.ASC).sparse());
        INDICES.put("egress_flow_index", new Index().named("egress_flow_index").on(EGRESS_FLOW, Sort.Direction.ASC).on(MODIFIED, Sort.Direction.ASC));
        INDICES.put("ingress_bytes_index", new Index().named("ingress_bytes_index").on(INGRESS_BYTES, Sort.Direction.ASC).on(MODIFIED, Sort.Direction.ASC));
        INDICES.put("in_flight_index", new Index().named("in_flight_index").on(IN_FLIGHT, Sort.Direction.ASC).on(MODIFIED, Sort.Direction.ASC).partial(PartialIndexFilter.of(Criteria.where(IN_FLIGHT).is(true))));
        INDICES.put("terminal_index", new Index().named("terminal_index").on(TERMINAL, Sort.Direction.ASC).on(SOURCE_INFO_FLOW, Sort.Direction.ASC).on(MODIFIED, Sort.Direction.ASC));
        INDICES.put("content_deletable_index", new Index().named("content_deletable_index").on(CONTENT_DELETABLE, Sort.Direction.ASC).on(SOURCE_INFO_FLOW, Sort.Direction.ASC).on(MODIFIED, Sort.Direction.ASC).partial(PartialIndexFilter.of(Criteria.where(CONTENT_DELETABLE).is(true))));

        // partial index to support finding DeltaFiles that are pending annotations
        INDICES.put("first_pending_annotations_for_flows_index", new Index().named("first_pending_annotations_for_flows_index")
                .on(FIRST_PENDING_ANNOTATIONS_FOR_FLOWS, Sort.Direction.ASC).partial(PartialIndexFilter.of(Criteria.where(FIRST_PENDING_ANNOTATIONS_FOR_FLOWS).exists(true))).on(MODIFIED, Sort.Direction.ASC));

        // use partial indexes for boolean fields filtering on the more selective value
        INDICES.put("egressed_index", new Index().named("egressed_index").on(EGRESSED, Sort.Direction.ASC).partial(PartialIndexFilter.of(Criteria.where(EGRESSED).is(false))).on(MODIFIED, Sort.Direction.ASC));
        INDICES.put("test_mode_index", new Index().named("test_mode_index").on(TEST_MODE, Sort.Direction.ASC).partial(PartialIndexFilter.of(Criteria.where(TEST_MODE).is(true))).on(MODIFIED, Sort.Direction.ASC));
        INDICES.put("filtered_index", new Index().named("filtered_index").on(FILTERED, Sort.Direction.ASC).partial(PartialIndexFilter.of(Criteria.where(FILTERED).is(true))).on(MODIFIED, Sort.Direction.ASC));
        INDICES.put("error_index", new Index().named("error_index").on(STAGE, Sort.Direction.ASC).on(ERROR_ACKNOWLEDGED, Sort.Direction.ASC).partial(PartialIndexFilter.of(Criteria.where(STAGE).is("ERROR"))));

        INDICES.put("cold_queued_index", new Index().named("cold_queued_index").on(IN_FLIGHT, Sort.Direction.ASC).on(ACTIONS_STATE, Sort.Direction.ASC).on(ACTIONS_NAME, Sort.Direction.ASC).on(MODIFIED, Sort.Direction.ASC).partial(PartialIndexFilter.of(Criteria.where(IN_FLIGHT).is(true).and(ACTIONS_STATE).is(COLD_QUEUED.name()))));
        INDICES.put("queued_index", new Index().named("queued_index").on(IN_FLIGHT, Sort.Direction.ASC).on(ACTIONS_STATE, Sort.Direction.ASC).on(ACTIONS_NAME, Sort.Direction.ASC).on(MODIFIED, Sort.Direction.ASC).partial(PartialIndexFilter.of(Criteria.where(IN_FLIGHT).is(true).and(ACTIONS_STATE).is(QUEUED.name()))));
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
    public List<DeltaFile> updateForRequeue(OffsetDateTime requeueTime, int requeueSeconds, Set<String> skipActions, Set<String> skipDids) {
        List<DeltaFile> filesToRequeue = mongoTemplate.find(buildReadyForRequeueQuery(requeueTime, requeueSeconds, skipActions, skipDids), DeltaFile.class);
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
    public List<DeltaFile> updateColdQueuedForRequeue(List<String> actionNames, int maxFiles, OffsetDateTime modified) {
        List<DeltaFile> filesToRequeue = mongoTemplate.find(buildReadyForColdRequeueQuery(actionNames, maxFiles), DeltaFile.class);
        List<DeltaFile> requeuedDeltaFiles = new ArrayList<>();
        for (List<DeltaFile> batch : Lists.partition(filesToRequeue, 1000)) {
            List<String> dids = batch.stream().map(DeltaFile::getDid).toList();
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
                .and(NEXT_AUTO_RESUME).lt(maxReadyTime));
        requeueQuery.fields().include(ID, SOURCE_INFO_FLOW, SCHEMA_VERSION);

        return requeueQuery;
    }

    @Override
    public List<DeltaFile> findResumePolicyCandidates(String flowName) {
        return mongoTemplate.find(buildResumePolicyCanidatesQuery(flowName), DeltaFile.class);
    }

    private Query buildResumePolicyCanidatesQuery(String flowName) {
        Criteria criteria = Criteria.where(STAGE).is(DeltaFileStage.ERROR)
                .and(NEXT_AUTO_RESUME).isNull()
                .and(ERROR_ACKNOWLEDGED).isNull()
                .and(CONTENT_DELETED).isNull();

        if (flowName != null) {
            criteria.and(SOURCE_INFO_FLOW).is(flowName);
        }

        Query requeueQuery = new Query(criteria);
        requeueQuery.fields().include(ID, SOURCE_INFO_FLOW, ACTIONS_NAME, ACTIONS_ERROR_CAUSE, ACTIONS_STATE, ACTIONS_TYPE, ACTIONS_ATTEMPT, SCHEMA_VERSION);

        return requeueQuery;
    }

    @Override
    public void updateForAutoResume(List<String> dids, String policyName, OffsetDateTime nextAutoResume) {
        batchedBulkUpdateByIds(dids, new Update().set(NEXT_AUTO_RESUME, nextAutoResume).set(NEXT_AUTO_RESUME_REASON, policyName));
    }

    @Override
    public List<DeltaFile> findForTimedDelete(OffsetDateTime createdBeforeDate, OffsetDateTime completedBeforeDate,
                                              long minBytes, String flow, boolean deleteMetadata, int batchSize) {
        // one of these must be set for any matches to occur
        if (createdBeforeDate == null && completedBeforeDate == null) {
            return Collections.emptyList();
        }

        Criteria criteria;

        if (createdBeforeDate != null) {
            criteria = Criteria.where(CREATED).lt(createdBeforeDate);
        } else {
            criteria = Criteria.where(MODIFIED).lt(completedBeforeDate);
            criteria.and(TERMINAL).is(true);
        }

        if (flow != null) {
            criteria.and(SOURCE_INFO_FLOW).is(flow);
        }

        if (minBytes > 0L) {
            criteria.and(TOTAL_BYTES).gte(minBytes);
        }

        if (!deleteMetadata) {
            criteria.and(CONTENT_DELETABLE).is(true);
        }

        Query query = new Query(criteria);
        query.limit(batchSize);

        if (createdBeforeDate != null) {
            query.with(Sort.by(Sort.Direction.ASC, CREATED));
        } else {
            query.with(Sort.by(Sort.Direction.ASC, MODIFIED));
        }
        query.fields().include(ID, TOTAL_BYTES, OLD_PROTOCOL_STACK_SEGMENTS, OLD_PROTOCOL_STACK_SEGMENTS_2, ACTION_SEGMENTS, OLD_FORMATTED_DATA_SEGMENTS, OLD_FORMATTED_DATA_SEGMENTS_2, OLD_PROTOCOL_STACK_ACTION_NAME, OLD_FORMATTED_DATA_ACTION_NAME, ACTIONS_NAME, CONTENT_DELETED, SCHEMA_VERSION);
        query.withHint("terminal_index");

        return mongoTemplate.find(query, DeltaFile.class);
    }

    @Override
    public List<DeltaFile> findForDiskSpaceDelete(long bytesToDelete, String flow, int batchSize) {
        if (bytesToDelete < 1) {
            throw new IllegalArgumentException("bytesToDelete (" + bytesToDelete + ") must be positive");
        }

        Criteria criteria = Criteria.where(CONTENT_DELETABLE).is(true);

        if (flow != null) {
            criteria.and(SOURCE_INFO_FLOW).is(flow);
        }

        Query query = new Query(criteria);
        query.limit(batchSize);
        query.with(Sort.by(Sort.Direction.ASC, MODIFIED));
        query.fields().include(ID, TOTAL_BYTES, OLD_PROTOCOL_STACK_SEGMENTS, OLD_PROTOCOL_STACK_SEGMENTS_2, ACTION_SEGMENTS, OLD_FORMATTED_DATA_SEGMENTS, OLD_FORMATTED_DATA_SEGMENTS_2, OLD_PROTOCOL_STACK_ACTION_NAME, OLD_FORMATTED_DATA_ACTION_NAME, ACTIONS_NAME, SCHEMA_VERSION);

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
        Criteria filterCriteria = buildDeltaFilesCriteria(filter);
        Query query = new Query(filterCriteria);

        if (offset != null && offset > 0) {
            query.skip(offset);
        } else {
            offset = 0;
        }

        query.limit(limit);

        if (includeFields != null) {
            query.fields().include(SCHEMA_VERSION);
            for (String includeField : includeFields) {
                query.fields().include(includeField);
            }
        }

        if (orderBy == null) {
            orderBy = DeltaFileOrder.newBuilder().field(MODIFIED).direction(DeltaFileDirection.DESC).build();
        }

        query.with(Sort.by(Sort.Direction.fromString(orderBy.getDirection().name()), orderBy.getField()));

        DeltaFiles deltaFiles = new DeltaFiles();
        deltaFiles.setOffset(offset);
        if (includeFields != null && includeFields.isEmpty()) {
            deltaFiles.setDeltaFiles(Collections.emptyList());
        } else {
            deltaFiles.setDeltaFiles(mongoTemplate.find(query, DeltaFile.class));
        }
        deltaFiles.setCount(deltaFiles.getDeltaFiles().size());
        if ((includeFields == null || !includeFields.isEmpty()) && deltaFiles.getCount() < limit) {
            deltaFiles.setTotalCount(deltaFiles.getOffset() + deltaFiles.getCount());
        } else {
            int total = (int) mongoTemplate.count(new Query(filterCriteria).limit(MAX_COUNT), DeltaFile.class);
            deltaFiles.setTotalCount(total);
        }

        return deltaFiles;
    }

    Update buildRequeueUpdate(OffsetDateTime modified, int requeueSeconds) {
        if (modified == null) {
            modified = OffsetDateTime.now();
        }

        Update update = new Update();
        update.inc(REQUEUE_COUNT, 1);

        // clear out any old error messages
        update.set(ACTIONS_UPDATE_ERROR, null);
        update.set(ACTIONS_UPDATE_ERROR_CONTEXT, null);
        update.set(ACTIONS_UPDATE_MODIFIED, modified);
        update.set(ACTIONS_UPDATE_QUEUED, modified);

        update.set(MODIFIED, modified);

        long epochMs = requeueThreshold(modified, requeueSeconds);
        update.filterArray(Criteria.where(ACTION_STATE).is(ActionState.QUEUED.name())
                .and(ACTION_MODIFIED).lt(new Date(epochMs)));

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
        update.filterArray(Criteria.where(ACTION_STATE).is(COLD_QUEUED.name()));
        update.set(MODIFIED, modified);

        update.inc(VERSION, 1);

        return update;
    }

    private Query buildReadyForRequeueQuery(OffsetDateTime requeueTime, int requeueSeconds, Set<String> skipActions, Set<String> skipDids) {
        Criteria criteria = Criteria.where(IN_FLIGHT).is(true);
        long epochMs = requeueThreshold(requeueTime, requeueSeconds);
        criteria.and(MODIFIED).lt(new Date(epochMs));

        if (skipDids != null && !skipDids.isEmpty()) {
            criteria.and(DID).not().in(skipDids);
        }

        if (skipActions != null && !skipActions.isEmpty()) {
            Criteria actionsCriteria = new Criteria().orOperator(
                    Criteria.where(STATE).is(QUEUED.toString()).and(NAME).in(skipActions),
                    Criteria.where(STATE).is(COLD_QUEUED.name())
            );
            criteria.and(ACTIONS).not().elemMatch(actionsCriteria);
        } else {
            criteria.and(ACTIONS).not().elemMatch(Criteria.where(STATE).is(COLD_QUEUED.name()));
        }

        Query requeueQuery = new Query(criteria);
        requeueQuery.fields().include(ID);

        return requeueQuery;
    }

    private Query buildReadyForColdRequeueQuery(List<String> actionNames, int maxFiles) {
        Criteria notComplete = Criteria.where(IN_FLIGHT).is(true)
                .and(ACTIONS_STATE).is(COLD_QUEUED)
                .and(ACTIONS_NAME).in(actionNames);

        Criteria coldQueuedCriteria = new Criteria().andOperator(
                Criteria.where(NAME).in(actionNames),
                Criteria.where(STATE).is(COLD_QUEUED.name())
        );

        Criteria actionMatch = Criteria.where(ACTIONS).elemMatch(coldQueuedCriteria);

        Query requeueQuery = new Query(new Criteria().andOperator(notComplete, actionMatch));
        requeueQuery.fields().include(ID);
        requeueQuery.limit(maxFiles);

        return requeueQuery;
    }

    private Criteria buildDeltaFilesCriteria(DeltaFilesFilter filter) {
        final Criteria criteria = new Criteria();

        if (filter == null) {
            return criteria;
        }

        if (filter.getEgressFlows() != null && !filter.getEgressFlows().isEmpty()) {
            criteria.and(EGRESS_FLOW).in(filter.getEgressFlows());
        }

        if (filter.getDids() != null && !filter.getDids().isEmpty()) {
            criteria.and(ID).in(filter.getDids());
        }

        if (filter.getParentDid() != null) {
            criteria.and(PARENT_DIDS).in(filter.getParentDid());
        }

        if (filter.getCreatedAfter() != null && filter.getCreatedBefore() != null) {
            criteria.and(CREATED).gt(filter.getCreatedAfter()).lt(filter.getCreatedBefore());
        } else if (filter.getCreatedAfter() != null) {
            criteria.and(CREATED).gt(filter.getCreatedAfter());
        } else if (filter.getCreatedBefore() != null) {
            criteria.and(CREATED).lt(filter.getCreatedBefore());
        }

        if (filter.getDomains() != null && !filter.getDomains().isEmpty()) {
            criteria.and(DOMAINS_NAME).all(filter.getDomains());
        }

        if (filter.getEnrichments() != null && !filter.getEnrichments().isEmpty()) {
            criteria.and(ENRICHMENTS_NAME).all(filter.getEnrichments());
        }

        if (filter.getAnnotations() != null) {
            filter.getAnnotations()
                    .forEach(e -> addAnnotationCriteria(e.getKey(), e.getValue(), criteria));
        }

        if (filter.getContentDeleted() != null) {
            if (isTrue(filter.getContentDeleted())) {
                criteria.and(CONTENT_DELETED).ne(null);
            } else {
                criteria.and(CONTENT_DELETED).is(null);
            }
        }

        if (filter.getModifiedAfter() != null && filter.getModifiedBefore() != null) {
            criteria.and(MODIFIED).gt(filter.getModifiedAfter()).lt(filter.getModifiedBefore());
        } else if (filter.getModifiedAfter() != null) {
            criteria.and(MODIFIED).gt(filter.getModifiedAfter());
        } else if (filter.getModifiedBefore() != null) {
            criteria.and(MODIFIED).lt(filter.getModifiedBefore());
        }

        if (filter.getTerminalStage() != null) {
            criteria.and(TERMINAL).is(isTrue(filter.getTerminalStage()));
        }

        if (filter.getStage() != null) {
            criteria.and(STAGE).is(filter.getStage().name());
        }

        if (filter.getSourceInfo() != null) {
            if (filter.getSourceInfo().getFilename() != null && filter.getSourceInfo().getFilenameFilter() == null) {
                // fall back to using the filename if it is set and filenameFilter is null
                FilenameFilter filenameFilter = new FilenameFilter(".*" + filter.getSourceInfo().getFilename() + ".*", true, false);
                filter.getSourceInfo().setFilenameFilter(filenameFilter);
            }

            if (filter.getSourceInfo().getFilenameFilter() != null) {
                addFilenameCriteria(filter.getSourceInfo().getFilenameFilter(), criteria);
            }

            if (filter.getSourceInfo().getFlow() != null) {
                criteria.and(SOURCE_INFO_FLOW).is(filter.getSourceInfo().getFlow());
            }

            if (filter.getSourceInfo().getIngressFlows() != null) {
                criteria.and(SOURCE_INFO_FLOW).in(filter.getSourceInfo().getIngressFlows());
            }

            if (filter.getSourceInfo().getMetadata() != null) {
                filter.getSourceInfo().getMetadata().forEach(keyValue -> {
                    String searchKey = keyValue.getKey();
                    if (searchKey.contains(".")) {
                        searchKey = StringUtils.replace(keyValue.getKey(), ".", DeltaFiConstants.MONGO_MAP_KEY_DOT_REPLACEMENT);
                    }
                    criteria.and(SOURCE_INFO_METADATA + "." + searchKey).is(keyValue.getValue());
                });
            }

            if (filter.getSourceInfo().getProcessingType() != null) {
                criteria.and(SOURCE_INFO_PROCESSING_TYPE).is(filter.getSourceInfo().getProcessingType());
            }
        }

        if (filter.getActions() != null && !filter.getActions().isEmpty()) {
            criteria.and(ACTIONS_NAME).all(filter.getActions());
        }

        if (filter.getErrorCause() != null) {
            Criteria actionElemMatch = new Criteria().andOperator(Criteria.where(STATE).is(ERROR.name()),
                    Criteria.where(ERROR_CAUSE).regex(filter.getErrorCause()));
            criteria.and(ACTIONS).elemMatch(actionElemMatch);
        }

        if (filter.getFilteredCause() != null) {
            boolean filtered = filter.getFiltered() == null || filter.getFiltered();
            criteria.and(FILTERED).is(filtered);
            Criteria actionElemMatch = new Criteria().andOperator(Criteria.where(STATE).is(ActionState.FILTERED.name()),
                    Criteria.where(FILTERED_CAUSE).regex(filter.getFilteredCause()));
            criteria.and(ACTIONS).elemMatch(actionElemMatch);
        } else if (filter.getFiltered() != null) {
            criteria.and(FILTERED).is(filter.getFiltered());
        }

        if (filter.getRequeueCountMin() != null) {
            criteria.and(REQUEUE_COUNT).gte(filter.getRequeueCountMin());
        }

        if (filter.getIngressBytesMin() != null && filter.getIngressBytesMax() != null) {
            criteria.and(INGRESS_BYTES).gte(filter.getIngressBytesMin()).lte(filter.getIngressBytesMax());
        } else if (filter.getIngressBytesMin() != null) {
            criteria.and(INGRESS_BYTES).gte(filter.getIngressBytesMin());
        } else if (filter.getIngressBytesMax() != null) {
            criteria.and(INGRESS_BYTES).lte(filter.getIngressBytesMax());
        }

        if (filter.getReferencedBytesMin() != null && filter.getReferencedBytesMax() != null) {
            criteria.and(REFERENCED_BYTES).gte(filter.getReferencedBytesMin()).lte(filter.getReferencedBytesMax());
        } else if (filter.getReferencedBytesMin() != null) {
            criteria.and(REFERENCED_BYTES).gte(filter.getReferencedBytesMin());
        } else if (filter.getReferencedBytesMax() != null) {
            criteria.and(REFERENCED_BYTES).lte(filter.getReferencedBytesMax());
        }

        if (filter.getTotalBytesMin() != null && filter.getTotalBytesMax() != null) {
            criteria.and(TOTAL_BYTES).gte(filter.getTotalBytesMin()).lte(filter.getTotalBytesMax());
        } else if (filter.getTotalBytesMin() != null) {
            criteria.and(TOTAL_BYTES).gte(filter.getTotalBytesMin());
        } else if (filter.getTotalBytesMax() != null) {
            criteria.and(TOTAL_BYTES).lte(filter.getTotalBytesMax());
        }

        if (filter.getErrorAcknowledged() != null) {
            if (isTrue(filter.getErrorAcknowledged())) {
                criteria.and(ERROR_ACKNOWLEDGED).ne(null);
            } else {
                criteria.and(ERROR_ACKNOWLEDGED).is(null);
            }
        }

        if (filter.getEgressed() != null) {
            criteria.and(EGRESSED).is(filter.getEgressed());
        }

        if (filter.getTestMode() != null) {
            if (isTrue(filter.getTestMode())) {
                criteria.and(TEST_MODE).is(true);
            } else {
                criteria.and(TEST_MODE).ne(true);
            }
        }

        if (filter.getReplayable() != null) {
            if (isTrue(filter.getReplayable())) {
                criteria.and(REPLAYED).isNull();
                criteria.and(CONTENT_DELETED).isNull();
            } else {
                criteria.orOperator(Criteria.where(REPLAYED).ne(null), Criteria.where(CONTENT_DELETED).ne(null));
            }
        }

        if (filter.getReplayed() != null) {
            if (isTrue(filter.getReplayed())) {
                criteria.and(REPLAYED).ne(null);
            } else {
                criteria.and(REPLAYED).isNull();
            }
        }

        if (filter.getPendingAnnotations() != null) {
            if (isTrue(filter.getPendingAnnotations())) {
                criteria.and(FIRST_PENDING_ANNOTATIONS_FOR_FLOWS).exists(true);
            } else {
                criteria.and(PENDING_ANNOTATIONS_FOR_FLOWS).is(null);
            }
        }

        return criteria;
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

    private long requeueThreshold(OffsetDateTime requeueTime, int requeueSeconds) {
        return requeueTime.minusSeconds(requeueSeconds).toInstant().toEpochMilli();
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
                    group(SOURCE_INFO_FLOW).count().as(GROUP_COUNT).addToSet(ID).as(DIDS),
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

        GroupOperation groupByCauseAndFlow = Aggregation.group(ERROR_MESSAGE, FLOW_LOWER_CASE)
                .count().as(GROUP_COUNT)
                .addToSet(DID).as(DIDS);

        List<AggregationOperation> mainStages = Arrays.asList(
                matchesErrorStage,
                unwind(ACTIONS),
                project()
                        .and(SOURCE_INFO_FLOW).as(FLOW_LOWER_CASE)
                        .and(ID).as(DID)
                        .and(messageField).as(ERROR_MESSAGE)
                        .and(ACTIONS_STATE).as(UNWIND_STATE),
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
                        .flow(groupResult.id.flow)
                        .count(groupResult.dids.size())
                        .dids(groupResult.dids)
                        .build());
            }
        }

        return new SummaryByFlowAndMessage((int) elementsToSkip, messageList.size(), countForPaging.intValue(), messageList);
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

        if (filter == null) {
            return criteria;
        }

        applySummaryCriteria(filter, criteria);

        if (filter.getErrorAcknowledged() != null) {
            if (isTrue(filter.getErrorAcknowledged())) {
                criteria.and(ERROR_ACKNOWLEDGED).ne(null);
            } else {
                criteria.and(ERROR_ACKNOWLEDGED).is(null);
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
        if (filter.getModifiedAfter() != null && filter.getModifiedBefore() != null) {
            criteria.and(MODIFIED).gt(filter.getModifiedAfter()).lt(filter.getModifiedBefore());
        } else if (filter.getModifiedAfter() != null) {
            criteria.and(MODIFIED).gt(filter.getModifiedAfter());
        } else if (filter.getModifiedBefore() != null) {
            criteria.and(MODIFIED).lt(filter.getModifiedBefore());
        }

        if (filter.getFlow() != null) {
            criteria.and(SOURCE_INFO_FLOW).is(filter.getFlow());
        }
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

        DeltaFileStats deltaFileStats = aggResults.getMappedResults().get(0);
        deltaFileStats.setTotalCount(estimatedCount());
        return deltaFileStats;
    }

    @Override
    public void removePendingAnnotationsForFlow(String flow) {
        pullFlowFromPendingAnnotationsForFlow(flow);
        unsetEmptyPendingAnnotationsForFlow();
    }

    @Override
    public List<ColdQueuedActionSummary> coldQueuedActionsSummary() {
        Criteria stageCriteria = Criteria.where(IN_FLIGHT).is(true);
        MatchOperation matchStage = Aggregation.match(stageCriteria);

        UnwindOperation unwindActions = Aggregation.unwind("actions");

        Criteria actionStateCriteria = Criteria.where("actions.state").is("COLD_QUEUED");
        MatchOperation matchActionState = Aggregation.match(actionStateCriteria);

        GroupOperation groupByActionNameAndType = Aggregation.group(Fields.fields().and("actions.name").and("actions.type"))
                .count().as("count");

        Aggregation aggregation = Aggregation.newAggregation(
                matchStage,
                unwindActions,
                matchActionState,
                groupByActionNameAndType
        ).withOptions(AggregationOptions.builder().allowDiskUse(true).build());

        AggregationResults<Document> aggResults = mongoTemplate.aggregate(aggregation, "deltaFile", Document.class);

        return aggResults.getMappedResults().stream()
                .map(doc -> {
                    String actionName = ((Document) doc.get("_id")).getString("name");
                    String actionType = ((Document) doc.get("_id")).getString("type");
                    Integer count = doc.getInteger("count");
                    return new ColdQueuedActionSummary(actionName, ActionType.valueOf(actionType), count);
                })
                .toList();
    }

    private void pullFlowFromPendingAnnotationsForFlow(String flow) {
        Query query = Query.query(Criteria.where(PENDING_ANNOTATIONS_FOR_FLOWS).is(flow));
        Update pullOutFlow = new Update().pull(PENDING_ANNOTATIONS_FOR_FLOWS, flow).inc(VERSION, 1);
        mongoTemplate.updateMulti(query, pullOutFlow, DeltaFile.class);
    }

    private void unsetEmptyPendingAnnotationsForFlow() {
        Query query = Query.query(Criteria.where(PENDING_ANNOTATIONS_FOR_FLOWS).is(List.of()));
        Update unsetEmptyList = new Update().unset(PENDING_ANNOTATIONS_FOR_FLOWS).inc(VERSION, 1);
        mongoTemplate.updateMulti(query, unsetEmptyList, DeltaFile.class);
    }

    private void batchedBulkUpdateByIds(List<String> dids, Update update) {
        if (dids == null || dids.isEmpty()) {
            return;
        }

        BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, DeltaFile.class);
        for (List<String> batch : Lists.partition(dids, 500)) {
            Query query = new Query().addCriteria(Criteria.where(ID).in(batch));
            bulkOps.updateMulti(query, update);
        }
        bulkOps.execute();
    }

    public void batchedBulkDeleteByDidIn(List<String> dids) {
        if (dids == null || dids.isEmpty()) {
            return;
        }

        BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, DeltaFile.class);

        for (List<String> batch : Lists.partition(dids, 500)) {
            Query query = new Query().addCriteria(Criteria.where(ID).in(batch));
            bulkOps.remove(query);
        }

        bulkOps.execute();
    }


    private void addFilenameCriteria(FilenameFilter filenameFilter, Criteria criteria) {
        String filename = filenameFilter.getFilename();
        Objects.requireNonNull(filename, "The filename must be provided in the FilenameFilter");

        String searchField = SOURCE_INFO_NORMALIZED_FILENAME;

        if (isFalse(filenameFilter.getCaseSensitive())) {
            filename =  filename.toLowerCase();
        } else {
            searchField = SOURCE_INFO_FILENAME;
        }

        // use the exact match criteria by default
        if (isTrue(filenameFilter.getRegex())) {
            criteria.and(searchField).regex(filename);
        } else {
            criteria.and(searchField).is(filename);
        }
    }

    public boolean update(String did, long version, Update update) {
        Query query = new Query(Criteria.where(ID).is(did).and(VERSION).is(version));
        UpdateResult result = mongoTemplate.updateFirst(query, update, DeltaFile.class);

        return result.wasAcknowledged() && result.getMatchedCount() > 0;
    }
}
