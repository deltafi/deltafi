package org.deltafi.core.domain.api.repo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.generated.types.ActionState;
import org.deltafi.core.domain.generated.types.DeltaFileStage;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

@SuppressWarnings("unused")
@RequiredArgsConstructor
@Slf4j
public class DeltaFileRepoImpl implements DeltaFileRepoCustom {
    public static final String ID = "_id";
    public static final String VERSION = "version";
    public static final String MODIFIED = "modified";
    public static final String CREATED = "created";
    public static final String STAGE = "stage";

    public static final String SOURCE_INFO_FLOW = "sourceInfo.flow";

    public static final String ACTIONS = "actions";
    public static final String ACTIONS_STATE = "state";
    public static final String ACTIONS_NAME = "name";
    public static final String ACTIONS_MODIFIED = "modified";

    public static final String ACTION_MODIFIED = "action.modified";
    public static final String ACTION_STATE = "action.state";
    public static final String ACTIONS_UPDATE_STATE = "actions.$[action].state";
    public static final String ACTIONS_UPDATE_MODIFIED = "actions.$[action].modified";
    public static final String ACTIONS_UPDATE_ERROR = "actions.$[action].errorCause";
    public static final String ACTIONS_UPDATE_ERROR_CONTEXT = "actions.$[action].errorContext";
    public static final String ACTIONS_UPDATE_HISTORY = "actions.$[action].history";

    private final MongoTemplate mongoTemplate;

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
        Criteria queued = Criteria.where(ACTIONS_STATE).is(ActionState.QUEUED.name());

        long epochMs = requeueThreshold(requeueTime, requeueSeconds).toInstant().toEpochMilli();
        Criteria expired = Criteria.where(ACTIONS_MODIFIED).lt(new Date(epochMs));

        Criteria actionElemMatch = new Criteria().andOperator(queued, expired);

        return new Query(Criteria.where(ACTIONS).elemMatch(actionElemMatch));
    }

    private Query buildRequeuedQuery(OffsetDateTime requeueTime) {
        Criteria queued = Criteria.where(ACTIONS_STATE).is(ActionState.QUEUED.name());
        Criteria requeuedTime = Criteria.where(ACTIONS_MODIFIED).is(requeueTime);

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
        Criteria notDeleted = Criteria.where(STAGE).ne(DeltaFileStage.DELETE.name());
        Criteria created = Criteria.where(CREATED).lt(createdBeforeDate);
        return new Criteria().andOperator(notDeleted, created);
    }

    private Criteria completedBeforeCriteria(OffsetDateTime completedBeforeDate) {
        Criteria completed = Criteria.where(STAGE).is(DeltaFileStage.COMPLETE.name());
        Criteria lastModified = Criteria.where(MODIFIED).lt(completedBeforeDate);
        return new Criteria().andOperator(completed, lastModified);
    }

    private OffsetDateTime requeueThreshold(OffsetDateTime requeueTime, int requeueSeconds) {
        return requeueTime.minusSeconds(requeueSeconds);
    }
}