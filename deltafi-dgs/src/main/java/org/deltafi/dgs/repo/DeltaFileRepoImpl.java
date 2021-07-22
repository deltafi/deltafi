package org.deltafi.dgs.repo;

import org.deltafi.dgs.api.types.DeltaFile;
import org.deltafi.dgs.configuration.DeltaFiProperties;
import org.deltafi.dgs.generated.types.ActionState;
import org.deltafi.dgs.generated.types.DeltaFileStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.OffsetDateTime;
import java.util.*;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.deltafi.dgs.delete.DeleteConstants.DELETE_ACTION;

@SuppressWarnings("unused")
public class DeltaFileRepoImpl implements DeltaFileRepoCustom {

    private static final Logger logger = LoggerFactory.getLogger(DeltaFileRepoImpl.class);

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
    private final DeltaFiProperties properties;

    public DeltaFileRepoImpl(MongoTemplate mongoTemplate, DeltaFiProperties properties) {
        this.mongoTemplate = mongoTemplate;
        this.properties = properties;
    }

    @Override
    public List<DeltaFile> updateForRequeue(OffsetDateTime requeueTime) {
        requeue(requeueTime);
        return findQueuedAt(requeueTime);
    }

    @Override
    public void markForDelete(OffsetDateTime createdBeforeDate, OffsetDateTime completedBeforeDate,
                                                String flowName, String policy) {
        // one of these must be set for any matches to occur
        if (isNull(createdBeforeDate) && isNull(completedBeforeDate)) {
            return;
        }

        Query query = new Query(buildReadyForDeleteCriteria(createdBeforeDate, completedBeforeDate, flowName));

        List<DeltaFile> deltaFilesToMark =  mongoTemplate.find(query, DeltaFile.class);
        deltaFilesToMark.forEach(deltaFile -> this.doMarkForDeleteAndSave(deltaFile, policy));
    }

    void doMarkForDeleteAndSave(DeltaFile deltaFile, String policy) {
        deltaFile.markForDelete(DELETE_ACTION, policy);
        mongoTemplate.save(deltaFile);
    }

    void requeue(OffsetDateTime requeueTime) {
        mongoTemplate.updateMulti(buildReadyForRequeueQuery(requeueTime), buildRequeueUpdate(requeueTime), DeltaFile.class);
    }

    Update buildRequeueUpdate(OffsetDateTime modified) {
        Update update = new Update();

        // clear out any old error messages
        update.set(ACTIONS_UPDATE_ERROR, null);
        update.set(ACTIONS_UPDATE_ERROR_CONTEXT, null);
        update.set(ACTIONS_UPDATE_MODIFIED, modified);

        update.set(MODIFIED, nonNull(modified) ? modified : OffsetDateTime.now());

        Criteria queued = Criteria.where(ACTION_STATE).is(ActionState.QUEUED.name());

        long epochMs = requeueThreshold(modified).toInstant().toEpochMilli();
        Criteria expired = Criteria.where(ACTION_MODIFIED).lt(new Date(epochMs));

        update.filterArray(new Criteria().andOperator(queued, expired));

        return update;
    }

    List<DeltaFile> findQueuedAt(OffsetDateTime modified) {
        return mongoTemplate.find(buildRequeuedQuery(modified), DeltaFile.class);
    }

    private Query buildReadyForRequeueQuery(OffsetDateTime requeueTime) {
        Criteria queued = Criteria.where(ACTIONS_STATE).is(ActionState.QUEUED.name());

        long epochMs = requeueThreshold(requeueTime).toInstant().toEpochMilli();
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
        if (nonNull(flowName)) {
            Criteria flow = Criteria.where(SOURCE_INFO_FLOW).is(flowName);
            return new Criteria().andOperator(buildDeleteTimeCriteria(createdBeforeDate, completedBeforeDate), flow);
        } else {
            return buildDeleteTimeCriteria(createdBeforeDate, completedBeforeDate);
        }
    }

    private Criteria buildDeleteTimeCriteria(OffsetDateTime createdBeforeDate, OffsetDateTime completedBeforeDate) {
        if (nonNull(createdBeforeDate ) && isNull(completedBeforeDate)) {
            return createdBeforeCriteria(createdBeforeDate);
        }

        if (nonNull(completedBeforeDate) && isNull(createdBeforeDate)) {
            return completedBeforeCriteria(completedBeforeDate);
        }

        return new Criteria().orOperator(createdBeforeCriteria(createdBeforeDate), completedBeforeCriteria(completedBeforeDate));
    }

    private Criteria createdBeforeCriteria(OffsetDateTime createdBeforeDate) {
        return Criteria.where(CREATED).lt(createdBeforeDate);
    }

    private Criteria completedBeforeCriteria(OffsetDateTime completedBeforeDate) {
        Criteria completed = Criteria.where(STAGE).is(DeltaFileStage.COMPLETE.name());
        Criteria lastModified = Criteria.where(MODIFIED).lt(completedBeforeDate);
        return new Criteria().andOperator(completed, lastModified);
    }

    private OffsetDateTime requeueThreshold(OffsetDateTime requeueTime) {
        return requeueTime.minusSeconds(properties.getRequeueSeconds());
    }
}