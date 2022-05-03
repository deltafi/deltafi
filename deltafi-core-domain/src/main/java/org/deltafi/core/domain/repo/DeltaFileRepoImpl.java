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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.api.types.DeltaFiles;
import org.deltafi.core.domain.generated.types.*;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

@SuppressWarnings("unused")
@RequiredArgsConstructor
@Slf4j
public class DeltaFileRepoImpl implements DeltaFileRepoCustom {
    private static final String COLLECTION = "deltaFile";
    private static final String TTL_INDEX_NAME = "ttl_index";

    public static final String ID = "_id";
    public static final String VERSION = "version";
    public static final String MODIFIED = "modified";
    public static final String CREATED = "created";
    public static final String STAGE = "stage";
    public static final String STATE = "state";
    public static final String DOMAINS_NAME = "domains.name";
    public static final String ENRICHMENT_NAME = "enrichment.name";
    public static final String MARKED_FOR_DELETE = "markedForDelete";
    public static final String KEY = "key";
    public static final String VALUE = "value";
    public static final String ERROR_ACKNOWLEDGED = "errorAcknowledged";

    public static final String SOURCE_INFO_FILENAME = "sourceInfo.filename";
    public static final String SOURCE_INFO_FLOW = "sourceInfo.flow";
    public static final String SOURCE_INFO_METADATA = "sourceInfo.metadata";

    public static final String FORMATTED_DATA_FILENAME = "formattedData.filename";
    public static final String FORMATTED_DATA_FORMAT_ACTION = "formattedData.formatAction";
    public static final String FORMATTED_DATA_METADATA = "formattedData.metadata";
    public static final String FORMATTED_DATA_EGRESS_ACTIONS = "formattedData.egressActions";

    public static final String ACTIONS = "actions";
    public static final String ACTIONS_NAME = "actions.name";

    public static final String ACTION_MODIFIED = "action.modified";
    public static final String ACTION_STATE = "action.state";
    public static final String ACTIONS_UPDATE_STATE = "actions.$[action].state";
    public static final String ACTIONS_UPDATE_MODIFIED = "actions.$[action].modified";
    public static final String ACTIONS_UPDATE_QUEUED = "actions.$[action].queued";
    public static final String ACTIONS_UPDATE_ERROR = "actions.$[action].errorCause";
    public static final String ACTIONS_UPDATE_ERROR_CONTEXT = "actions.$[action].errorContext";
    public static final String ACTIONS_UPDATE_HISTORY = "actions.$[action].history";

    private final MongoTemplate mongoTemplate;
    private Duration cachedTtlDuration;

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

    public Set<String> readDids() {
        return StreamSupport
                .stream(mongoTemplate.getCollection(COLLECTION).distinct(ID, String.class).spliterator(), true)
                .collect(Collectors.toSet());
    }

    @Override
    public List<DeltaFile> updateForRequeue(OffsetDateTime requeueTime, int requeueSeconds) {
        requeue(requeueTime, requeueSeconds);
        return findQueuedAt(requeueTime);
    }

    @Override
    public List<DeltaFile> markForDelete(OffsetDateTime createdBeforeDate, OffsetDateTime completedBeforeDate,
                                         String flowName, String policy) {
        // one of these must be set for any matches to occur
        if (isNull(createdBeforeDate) && isNull(completedBeforeDate)) {
            return Collections.emptyList();
        }

        Query query = new Query(buildReadyForDeleteCriteria(createdBeforeDate, completedBeforeDate, flowName));

        List<DeltaFile> deltaFilesToMark = mongoTemplate.find(query, DeltaFile.class);
        deltaFilesToMark.forEach(deltaFile -> doMarkForDeleteAndSave(deltaFile, policy));

        return deltaFilesToMark;
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

    void doMarkForDeleteAndSave(DeltaFile deltaFile, String policy) {
        deltaFile.markForDelete(policy);
        mongoTemplate.save(deltaFile);
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

    private Criteria buildReadyForDeleteCriteria(OffsetDateTime createdBeforeDate, OffsetDateTime completedBeforeDate, String flowName) {
        Criteria deleteTimeCriteria = buildDeleteTimeCriteria(createdBeforeDate, completedBeforeDate);
        if (nonNull(flowName)) {
            return new Criteria().andOperator(deleteTimeCriteria, Criteria.where(SOURCE_INFO_FLOW).is(flowName));
        }
        return deleteTimeCriteria;
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

        if (nonNull(filter.getIsMarkedForDelete())) {
            if (filter.getIsMarkedForDelete()) {
                andCriteria.add(Criteria.where(MARKED_FOR_DELETE).ne(null));
            } else {
                andCriteria.add(Criteria.where(MARKED_FOR_DELETE).is(null));
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

        if (!andCriteria.isEmpty()) {
            if (andCriteria.size() == 1) {
                criteria = andCriteria.get(0);
            } else {
                criteria.andOperator(andCriteria.toArray(new Criteria[0]));
            }
        }

        return criteria;
    }

    private void addDeltaFilesOrderBy(Query query, DeltaFileOrder orderBy) {
        if (isNull(orderBy)) {
            orderBy = DeltaFileOrder.newBuilder().field("created").direction(DeltaFileDirection.DESC).build();
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
        Criteria notDeleted = Criteria.where(STAGE).ne(DeltaFileStage.DELETE);
        Criteria created = Criteria.where(CREATED).lt(createdBeforeDate);
        return new Criteria().andOperator(notDeleted, created);
    }

    private Criteria completedBeforeCriteria(OffsetDateTime completedBeforeDate) {
        Criteria completed = Criteria.where(STAGE).is(DeltaFileStage.COMPLETE);
        Criteria lastModified = Criteria.where(MODIFIED).lt(completedBeforeDate);
        return new Criteria().andOperator(completed, lastModified);
    }

    private OffsetDateTime requeueThreshold(OffsetDateTime requeueTime, int requeueSeconds) {
        return requeueTime.minusSeconds(requeueSeconds);
    }
}
