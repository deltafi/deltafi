/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.common.types.ActionState;
import org.deltafi.common.types.DeltaFile;
import org.deltafi.common.types.DeltaFileStage;
import org.deltafi.common.types.KeyValue;
import org.deltafi.core.generated.types.*;
import org.deltafi.core.types.DeltaFiles;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.UncategorizedMongoDbException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

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
    public static final String DOMAINS_NAME = "domains.name";
    public static final String ENRICHMENT_NAME = "enrichment.name";
    public static final String CONTENT_DELETED = "contentDeleted";
    public static final String CONTENT_DELETED_REASON = "contentDeletedReason";
    public static final String KEY = "key";
    public static final String VALUE = "value";
    public static final String ERROR_CAUSE = "errorCause";
    public static final String ERROR_ACKNOWLEDGED = "errorAcknowledged";
    public static final String EGRESSED = "egressed";
    public static final String EGRESS_FLOW = "egress.flow";
    public static final String FILTERED = "filtered";
    public static final String TEST_MODE = "testMode";
    public static final String TOTAL_BYTES = "totalBytes";
    public static final String INGRESS_BYTES = "ingressBytes";
    public static final String REQUEUE_COUNT = "requeueCount";
    public static final String SOURCE_INFO_FILENAME = "sourceInfo.filename";
    public static final String SOURCE_INFO_FLOW = "sourceInfo.flow";
    public static final String SOURCE_INFO_METADATA = "sourceInfo.metadata";
    public static final String FORMATTED_DATA_FILENAME = "formattedData.filename";
    public static final String FORMATTED_DATA_FORMAT_ACTION = "formattedData.formatAction";
    public static final String FORMATTED_DATA_METADATA = "formattedData.metadata";
    public static final String FORMATTED_DATA_EGRESS_ACTIONS = "formattedData.egressActions";
    public static final String ACTIONS = "actions";
    public static final String ACTIONS_ERROR_CAUSE = "actions.errorCause";
    public static final String ACTIONS_NAME = "actions.name";
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
    public static final String INDEXED_METADATA = "indexedMetadata";

    private static final String PROTOCOL_STACK_SEGMENTS = "protocolStack.content.contentReference.segments";
    private static final String FORMATTED_DATA_SEGMENTS = "formattedData.contentReference.segments";

    private static final String CUMULATIVE_BYTES = "cumulativeBytes";
    private static final String OVER = "over";
    private static final String CUMULATIVE_OVER = "cumulativeOver";

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

    private static final Map<String, Index> INDICES = Map.of(
            "action_search", new Index().named("action_search").on(ACTIONS_NAME, Sort.Direction.ASC),
            "queued_loop", new Index().named("queued_loop").on(REQUEUE_COUNT, Sort.Direction.DESC).on(STAGE, Sort.Direction.ASC),
            "completed_before_index", new Index().named("completed_before_index").on(STAGE, Sort.Direction.ASC).on(MODIFIED, Sort.Direction.ASC).on(SOURCE_INFO_FLOW, Sort.Direction.ASC),
            "created_before_index", new Index().named("created_before_index").on(CREATED, Sort.Direction.ASC).on(SOURCE_INFO_FLOW, Sort.Direction.ASC),
            "modified_before_index", new Index().named("modified_before_index").on(MODIFIED, Sort.Direction.ASC).on(SOURCE_INFO_FLOW, Sort.Direction.ASC),
            "requeue_index", new Index().named("requeue_index").on(ACTIONS_STATE, Sort.Direction.ASC).on(ACTIONS_MODIFIED, Sort.Direction.ASC),
            "metadata_index", new Index().named("metadata_index").on(INDEXED_METADATA + ".$**", Sort.Direction.ASC),
            "domain_name_index", new Index().named("domain_name_index").on(DOMAINS_NAME, Sort.Direction.ASC));

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
        for (List<DeltaFile> batch : Lists.partition(filesToRequeue, 1000)) {
            Query query = new Query().addCriteria(Criteria.where(ID).in(batch.stream().map(DeltaFile::getDid).collect(Collectors.toList())));
            mongoTemplate.updateMulti(query, buildRequeueUpdate(requeueTime, requeueSeconds), DeltaFile.class);
        }

        List<String> dids = filesToRequeue.stream().map(DeltaFile::getDid).collect(Collectors.toList());
        Query query = Query.query(Criteria.where(ID).in(dids));
        return mongoTemplate.find(query, DeltaFile.class);
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
        query.fields().include(ID, PROTOCOL_STACK_SEGMENTS, FORMATTED_DATA_SEGMENTS);

        return mongoTemplate.find(query, DeltaFile.class);
    }

    @Override
    public List<DeltaFile> findForDelete(long bytesToDelete, String flow, String policy, int batchSize) {
        if (bytesToDelete < 1) {
            throw new IllegalArgumentException("bytesToDelete (" + bytesToDelete + ") must be positive");
        }

        Query query = new Query(buildReadyForDeleteCriteria(OffsetDateTime.now(), null, 1, flow, false, true));
        query.limit(batchSize);
        addDeltaFilesOrderBy(query, DeltaFileOrder.newBuilder().field(CREATED).direction(DeltaFileDirection.ASC).build());
        query.fields().include(ID, TOTAL_BYTES, PROTOCOL_STACK_SEGMENTS, FORMATTED_DATA_SEGMENTS);

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
                .collect(Collectors.toList());
    }

    @Override
    public DeltaFiles deltaFiles(Integer offset, int limit, DeltaFilesFilter filter, DeltaFileOrder orderBy) {
        return deltaFiles(offset, limit, filter, orderBy, null);
    }

    @Override
    public DeltaFiles deltaFiles(Integer offset, int limit, DeltaFilesFilter filter, DeltaFileOrder orderBy, List<String> includeFields) {
        Query query = new Query(buildDeltaFilesCriteria(filter));

        if (nonNull(offset) && offset > 0) {
            query.skip(offset);
        } else {
            offset = 0;
        }

        query.limit(limit);

        if (Objects.nonNull(includeFields)) {
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
            int total = (int) mongoTemplate.count(new Query(buildDeltaFilesCriteria(filter)), DeltaFile.class);
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
        Criteria queued = Criteria.where(STATE).is(ActionState.QUEUED.name());

        long epochMs = requeueThreshold(requeueTime, requeueSeconds).toInstant().toEpochMilli();
        Criteria expired = Criteria.where(MODIFIED).lt(new Date(epochMs));

        Criteria actionElemMatch = new Criteria().andOperator(queued, expired);

        return new Query(Criteria.where(ACTIONS).elemMatch(actionElemMatch));
    }

    private Criteria buildReadyForDeleteCriteria(OffsetDateTime createdBeforeDate, OffsetDateTime completedBeforeDate, long minBytes, String flowName, boolean deleteMetadata, boolean completeOnly) {
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
            andCriteria.add(Criteria.where(STAGE).in(DeltaFileStage.COMPLETE.name(), DeltaFileStage.CANCELLED.name()));
        }

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
            andCriteria.add(Criteria.where(EGRESS_FLOW).all(filter.getEgressFlows()));
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

        if (nonNull(filter.getEnrichment()) && !filter.getEnrichment().isEmpty()) {
            andCriteria.add(Criteria.where(ENRICHMENT_NAME).all(filter.getEnrichment()));
        }

        if (nonNull(filter.getIndexedMetadata())) {
            List<Criteria> metadataCriteria = filter.getIndexedMetadata().stream()
                    .map(this::fromIndexedMetadata).filter(Objects::nonNull).collect(Collectors.toList());
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

        if (nonNull(filter.getStage())) {
            andCriteria.add(Criteria.where(STAGE).is(filter.getStage().name()));
        }

        if (nonNull(filter.getSourceInfo())) {
            if (nonNull(filter.getSourceInfo().getFilename())) {
                andCriteria.add(Criteria.where(SOURCE_INFO_FILENAME).regex(".*" + filter.getSourceInfo().getFilename() + ".*", "i"));
            }

            if (nonNull(filter.getSourceInfo().getFlow())) {
                andCriteria.add(Criteria.where(SOURCE_INFO_FLOW).is(filter.getSourceInfo().getFlow()));
            }

            if(nonNull(filter.getSourceInfo().getIngressFlows())) {
                andCriteria.add(Criteria.where(SOURCE_INFO_FLOW).in(filter.getSourceInfo().getIngressFlows()));
            }

            if (nonNull(filter.getSourceInfo().getMetadata())) {
                filter.getSourceInfo().getMetadata().forEach(m -> andCriteria.add(Criteria.where(SOURCE_INFO_METADATA).elemMatch(Criteria.where(KEY).is(m.getKey()).and(VALUE).is(m.getValue()))));
            }
        }

        if (nonNull(filter.getActions()) && !filter.getActions().isEmpty()) {
            andCriteria.add(Criteria.where(ACTIONS_NAME).all(filter.getActions()));
        }

        if (nonNull(filter.getErrorCause())) {
            ActionState actionState = (nonNull(filter.getFiltered()) && filter.getFiltered()) ?
                    ActionState.FILTERED : ActionState.ERROR;
            Criteria actionElemMatch = new Criteria().andOperator(Criteria.where(STATE).is(actionState.name()),
                    Criteria.where(ERROR_CAUSE).regex(filter.getErrorCause()));
            andCriteria.add(Criteria.where(ACTIONS).elemMatch(actionElemMatch));
        }

        if (nonNull(filter.getFormattedData())) {
            if (nonNull(filter.getFormattedData().getFilename())) {
                andCriteria.add(Criteria.where(FORMATTED_DATA_FILENAME).is(filter.getFormattedData().getFilename()));
            }

            if (nonNull(filter.getFormattedData().getFormatAction())) {
                andCriteria.add(Criteria.where(FORMATTED_DATA_FORMAT_ACTION).is(filter.getFormattedData().getFormatAction()));
            }

            if (nonNull(filter.getFormattedData().getMetadata())) {
                filter.getFormattedData().getMetadata().forEach(m -> andCriteria.add(Criteria.where(FORMATTED_DATA_METADATA).elemMatch(Criteria.where(KEY).is(m.getKey()).and(VALUE).is(m.getValue()))));
            }

            if (nonNull(filter.getFormattedData().getEgressActions()) && !filter.getFormattedData().getEgressActions().isEmpty()) {
                andCriteria.add(Criteria.where(FORMATTED_DATA_EGRESS_ACTIONS).all(filter.getFormattedData().getEgressActions()));
            }
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

        if (!andCriteria.isEmpty()) {
            if (andCriteria.size() == 1) {
                criteria = andCriteria.get(0);
            } else {
                criteria.andOperator(andCriteria.toArray(new Criteria[0]));
            }
        }

        return criteria;
    }

    private Criteria fromIndexedMetadata(KeyValue keyValue) {
        String key = keyValue.getKey();
        String value = keyValue.getValue();

        if (null == key || null == value) {
            return null;
        }

        if (key.contains(".")) {
            key = StringUtils.replace(key, ".", DeltaFiConstants.MONGO_MAP_KEY_DOT_REPLACEMENT);
        }

        return Criteria.where(INDEXED_METADATA + "." + key).is(value);
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
        Criteria completed = Criteria.where(STAGE).is(DeltaFileStage.COMPLETE);
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
        return mongoTemplate.findDistinct(new Query(Criteria.where(DOMAINS_NAME).ne(null)), DOMAINS_NAME, DeltaFile.class, String.class);
    }

    @Override
    public List<String> indexedMetadataKeys(String domain) {
        List<AggregationOperation> operations = new ArrayList<>();

        if (domain != null && !domain.isEmpty()) {
            operations.add(match(Criteria.where(DOMAINS_NAME).is(domain)));
        }
        operations.add(project().and(ObjectOperators.valueOf(INDEXED_METADATA).toArray()).as(INDEXED_METADATA));
        operations.add(unwind(INDEXED_METADATA));
        operations.add(group().addToSet("indexedMetadata.k").as("keys"));

        Aggregation aggregation = newAggregation(operations).withOptions(AggregationOptions.builder().allowDiskUse(true).build());

        List<String> keys = Optional
                .ofNullable(mongoTemplate.aggregate(aggregation, COLLECTION, Document.class).getUniqueMappedResult())
                .map(doc -> doc.getList("keys", String.class))
                .orElse(Collections.emptyList());
        Collections.sort(keys);

        return keys;
    }

    @Override
    public void setContentDeletedByDidIn(List<String> dids, OffsetDateTime now, String reason) {
        Update update = new Update().set(CONTENT_DELETED, now).set(CONTENT_DELETED_REASON, reason);
        for (List<String> batch : Lists.partition(dids, 1000)) {
            Query query = new Query().addCriteria(Criteria.where(ID).in(batch));
            mongoTemplate.updateMulti(query, update, DeltaFile.class);
        }
    }
}
