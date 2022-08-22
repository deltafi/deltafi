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
package org.deltafi.core.domain.repo;

import com.mongodb.MongoException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.common.types.ActionState;
import org.deltafi.common.types.DeltaFile;
import org.deltafi.common.types.DeltaFileStage;
import org.deltafi.common.types.KeyValue;
import org.deltafi.core.domain.generated.types.*;
import org.deltafi.core.domain.types.DeltaFiles;
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
import org.springframework.data.mongodb.util.MongoDbErrorCodes;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoField;
import java.util.*;
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
    public static final String KEY = "key";
    public static final String VALUE = "value";
    public static final String ERROR_ACKNOWLEDGED = "errorAcknowledged";
    public static final String EGRESSED = "egressed";
    public static final String FILTERED = "filtered";
    public static final String TOTAL_BYTES = "totalBytes";
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
    public static final String INDEXED_METADATA = "indexedMetadata.";

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
            "completed_before_index", new Index().named("completed_before_index").on(STAGE, Sort.Direction.ASC).on(MODIFIED, Sort.Direction.ASC).on(SOURCE_INFO_FLOW, Sort.Direction.ASC),
            "created_before_index", new Index().named("created_before_index").on(CREATED, Sort.Direction.ASC).on(SOURCE_INFO_FLOW, Sort.Direction.ASC),
            "modified_before_index", new Index().named("modified_before_index").on(MODIFIED, Sort.Direction.ASC).on(SOURCE_INFO_FLOW, Sort.Direction.ASC),
            "requeue_index", new Index().named("requeue_index").on(ACTIONS_STATE, Sort.Direction.ASC).on(ACTIONS_MODIFIED, Sort.Direction.ASC),
            "metadata_index", new Index().named("metadata_index").on(INDEXED_METADATA+"$**", Sort.Direction.ASC));

    private final MongoTemplate mongoTemplate;
    private Duration cachedTtlDuration;

    public void ensureAllIndices(Duration newTtl) {
        setExpirationIndex(newTtl);
        IndexOperations idxOps = mongoTemplate.indexOps(DeltaFile.class);
        List<IndexInfo> existingIndexes = idxOps.getIndexInfo();

        INDICES.forEach((indexName, indexDef) -> updateIndices(idxOps, indexName, indexDef, existingIndexes));

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
        requeue(requeueTime, requeueSeconds);
        return findQueuedAt(requeueTime);
    }

    @Override
    public List<DeltaFile> findForDelete(OffsetDateTime createdBeforeDate, OffsetDateTime completedBeforeDate,
                                         long minBytes, String flowName, String policy, boolean deleteMetadata,
                                         int batchSize) {
        // one of these must be set for any matches to occur
        if (isNull(createdBeforeDate) && isNull(completedBeforeDate)) {
            return Collections.emptyList();
        }

        Query query = new Query(buildReadyForDeleteCriteria(createdBeforeDate, completedBeforeDate, minBytes, flowName, deleteMetadata));
        query.limit(batchSize);

        return mongoTemplate.find(query, DeltaFile.class);
    }

    @Override
    public List<DeltaFile> findForDelete(long bytesToDelete, String flow, String policy, int batchSize) {
        if (bytesToDelete < 1) {
            throw new IllegalArgumentException("bytesToDelete (" + bytesToDelete + ") must be positive");
        }

        // get an exhaustive list of all deltafiles with content, sorted by created date ASC, so we can figure out when to start the delete threshold
        // TODO: move to mongo 5 and use $setWindowFields to push this work to the database (see https://stackoverflow.com/questions/27995085/how-to-calculate-the-running-total-using-aggregate/70135796#70135796)
        Query query = new Query();
        query.fields().include(TOTAL_BYTES, CREATED);
        query.limit(batchSize);

        query.addCriteria(Criteria.where(CONTENT_DELETED).isNull());
        query.addCriteria(Criteria.where(TOTAL_BYTES).gt(0L));

        DeltaFileOrder orderBy = DeltaFileOrder.newBuilder().field(CREATED).direction(DeltaFileDirection.ASC).build();
        query.with(Sort.by(Collections.singletonList(new Sort.Order(Sort.Direction.fromString(orderBy.getDirection().name()), orderBy.getField()))));
        List<DeltaFile> deltaFiles = mongoTemplate.find(query, DeltaFile.class);

        if (deltaFiles.isEmpty()) {
            return deltaFiles;
        }

        OffsetDateTime createdDate = null;
        long bytesSoFar = 0;
        for (DeltaFile deltaFile : deltaFiles) {
            bytesSoFar += deltaFile.getTotalBytes();
            createdDate = deltaFile.getCreated();
            if (bytesSoFar >= bytesToDelete) {
                break;
            }
        }

        // add a millisecond to include the last DeltaFile we found
        createdDate = createdDate.plus(1, ChronoField.MILLI_OF_DAY.getBaseUnit());

        return findForDelete(createdDate, null, 0, flow, policy, false, batchSize);
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

    void requeue(OffsetDateTime requeueTime, int requeueSeconds) {
        mongoTemplate.updateMulti(buildReadyForRequeueQuery(requeueTime, requeueSeconds), buildRequeueUpdate(requeueTime, requeueSeconds), DeltaFile.class);
    }

    Update buildRequeueUpdate(OffsetDateTime modified, int requeueSeconds) {
        Update update = new Update();

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

    List<DeltaFile> findQueuedAt(OffsetDateTime modified) {
        return mongoTemplate.find(buildRequeuedQuery(modified), DeltaFile.class);
    }

    private Query buildReadyForRequeueQuery(OffsetDateTime requeueTime, int requeueSeconds) {
        Criteria queued = Criteria.where(STATE).is(ActionState.QUEUED.name());

        long epochMs = requeueThreshold(requeueTime, requeueSeconds).toInstant().toEpochMilli();
        Criteria expired = Criteria.where(MODIFIED).lt(new Date(epochMs));

        Criteria actionElemMatch = new Criteria().andOperator(queued, expired);

        return new Query(Criteria.where(ACTIONS).elemMatch(actionElemMatch));
    }

    private Query buildRequeuedQuery(OffsetDateTime requeueTime) {
        Criteria queued = Criteria.where(STATE).is(ActionState.QUEUED.name());
        Criteria requeuedTime = Criteria.where(MODIFIED).is(requeueTime);

        Criteria actionElemMatch = new Criteria().andOperator(queued, requeuedTime);

        return new Query(Criteria.where(ACTIONS).elemMatch(actionElemMatch));
    }

    private Criteria buildReadyForDeleteCriteria(OffsetDateTime createdBeforeDate, OffsetDateTime completedBeforeDate, long minBytes, String flowName, boolean deleteMetadata) {
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
                andCriteria.add(Criteria.where(SOURCE_INFO_FILENAME).is(filter.getSourceInfo().getFilename()));
            }

            if (nonNull(filter.getSourceInfo().getFlow())) {
                andCriteria.add(Criteria.where(SOURCE_INFO_FLOW).is(filter.getSourceInfo().getFlow()));
            }

            if (nonNull(filter.getSourceInfo().getMetadata())) {
                filter.getSourceInfo().getMetadata().forEach(m -> andCriteria.add(Criteria.where(SOURCE_INFO_METADATA).elemMatch(Criteria.where(KEY).is(m.getKey()).and(VALUE).is(m.getValue()))));
            }
        }

        if (nonNull(filter.getActions()) && !filter.getActions().isEmpty()) {
            andCriteria.add(Criteria.where(ACTIONS_NAME).all(filter.getActions()));
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

        return Criteria.where(INDEXED_METADATA + key).is(value);
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

    private void updateIndices(IndexOperations idxOps, String indexName, Index index, List<IndexInfo> existingIndexes) {
        try {
            log.debug("Ensure index {}", indexName);
            idxOps.ensureIndex(index);
        } catch (UncategorizedMongoDbException ex) {
            if ( ex.getCause() instanceof MongoException && MongoDbErrorCodes.isDataIntegrityViolationCode(((MongoException) ex.getCause()).getCode()) && indexExists(indexName, existingIndexes)) {
                log.info("An old version of index {} exists, attempting to recreate it", indexName);
                recreateIndex(idxOps, indexName, index);
            } else {
                log.error("Failed to ensure index: {}", index, ex);
            }
        }
    }

    private void recreateIndex(IndexOperations idxOps, String indexName, Index index) {
        try {
            idxOps.dropIndex(indexName);
            idxOps.ensureIndex(index);
        } catch (UncategorizedMongoDbException ex) {
            log.error("Failed to recreate index: {}", index, ex);
        }
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

    private boolean indexExists(String name, List<IndexInfo> existingIndexes) {
        return existingIndexes.stream()
                .anyMatch(indexInfo -> ObjectUtils.nullSafeEquals(name, indexInfo.getName()));
    }

    @Override
    public ErrorsByFlow getErrorSummaryByFlow(Integer offset, int limit, ErrorSummaryFilter filter, DeltaFileOrder orderBy) {

        long elementsToSkip = (nonNull(offset) && offset > 0) ? offset : 0;

        MatchOperation matchesErrorStage = Aggregation.match(buildErrorSummaryCriteria(filter));

        Aggregation countAggregation = Aggregation.newAggregation(
                matchesErrorStage,
                group(SOURCE_INFO_FLOW).count().as(GROUP_COUNT),
                count().as(COUNT_FOR_PAGING));

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
                    limit(limit)
            );

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
        Aggregation countAggregation = Aggregation.newAggregation(aggregationWithCount);

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
            Aggregation pagingAggregation = Aggregation.newAggregation(stagesWithPaging);

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
}
