package org.deltafi.dgs.repo;

import org.deltafi.dgs.api.types.DeltaFile;
import org.deltafi.dgs.configuration.DeltaFiProperties;
import org.deltafi.dgs.generated.types.ActionEvent;
import org.deltafi.dgs.generated.types.ActionState;
import org.deltafi.dgs.generated.types.DeltaFileStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

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

    public static final String ACTION_NAME = "action.name";
    public static final String ACTIONS_UPDATE_STATE = "actions.$[action].state";
    public static final String ACTIONS_UPDATE_MODIFIED = "actions.$[action].modified";
    public static final String ACTIONS_UPDATE_ERROR = "actions.$[action].errorMessage";
    public static final String ACTIONS_UPDATE_HISTORY = "actions.$[action].history";

    private final MongoTemplate mongoTemplate;
    private final DeltaFiProperties properties;

    public DeltaFileRepoImpl(MongoTemplate mongoTemplate, DeltaFiProperties properties) {
        this.mongoTemplate = mongoTemplate;
        this.properties = properties;
    }

    @Override
    public List<DeltaFile> findAndDispatchForAction(String actionName, Integer limit, Boolean dryRun) {
        return findReadyForDispatch(actionName, limit).stream()
                .map(deltaFile -> this.dispatchAction(deltaFile, actionName, dryRun))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
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

    DeltaFile dispatchAction(DeltaFile deltaFile, String actionName, Boolean dryRun) {
        if (dryRun) {
            return deltaFile;
        }

        OffsetDateTime now = OffsetDateTime.now();

        Update dispatchUpdate = buildDispatchUpdate(now);
        dispatchUpdate.filterArray(Criteria.where(ACTION_NAME).is(actionName));

        return versionedUpdate(deltaFile, new Query(), dispatchUpdate, now).orElse(null);
    }

    Update buildDispatchUpdate(OffsetDateTime modified) {
        Update update = new Update();

        ActionEvent actionEvent = ActionEvent.newBuilder().state(ActionState.DISPATCHED).time(modified).build();

        update.set(ACTIONS_UPDATE_STATE, ActionState.DISPATCHED.name());
        // clear out any old error messages
        update.set(ACTIONS_UPDATE_ERROR, null);
        update.set(ACTIONS_UPDATE_MODIFIED, modified);
        update.addToSet(ACTIONS_UPDATE_HISTORY, actionEvent);

        return update;
    }

    /**
     * FindAndModify the given DeltaFile if we are working with the latest version
     *
     * @param deltaFile - deltaFile that needs to be updated
     * @param query - query with criteria to search for, id and version will always be added to the criteria
     * @param update - updates to perform for the record, version and modified will always be added to the update
     * @param modified - optional modified time to use, if null the current time is used
     * @return - The updated version of the DeltaFile if the modify was successful, otherwise empty
     */
    public Optional<DeltaFile> versionedUpdate(DeltaFile deltaFile, Query query, Update update, OffsetDateTime modified) {
        Criteria versionCriteria = Criteria.where(ID).is(deltaFile.getDid()).and(VERSION).is(deltaFile.getVersion());
        query.addCriteria(versionCriteria);
        update.set(VERSION, deltaFile.getVersion() + 1);
        update.set(MODIFIED, nonNull(modified) ? modified : OffsetDateTime.now());

        FindAndModifyOptions options = FindAndModifyOptions.options().returnNew(true).upsert(false);

        DeltaFile modifiedDeltafile = mongoTemplate.findAndModify(query, update, options, DeltaFile.class);
        if (isNull(modifiedDeltafile)) {
            logger.debug("Cannot update deltaFile {} with version {}. The version did not match or the record was removed", deltaFile.getDid(), deltaFile.getVersion());
            return Optional.empty();
        }
        return Optional.of(modifiedDeltafile);
    }

    List<DeltaFile> findReadyForDispatch(String actionName, Integer limit) {
        return mongoTemplate.find(buildReadyForDispatchQuery(actionName, limit), DeltaFile.class);
    }

    private Query buildReadyForDispatchQuery(String actionName, Integer limit) {
        Criteria nameMatches = Criteria.where(ACTIONS_NAME).is(actionName);
        Criteria queued = Criteria.where(ACTIONS_STATE).is(ActionState.QUEUED.name());
        Criteria expired = Criteria.where(ACTIONS_MODIFIED).lt(feedThreshold());
        Criteria dispatched = Criteria.where(ACTIONS_STATE).is(ActionState.DISPATCHED.name());

        Criteria timedOut = new Criteria().andOperator(dispatched, expired);
        Criteria stateMatches = new Criteria().orOperator(queued, timedOut);

        Criteria actionElemMatch = new Criteria().andOperator(nameMatches, stateMatches);

        Query query = new Query(Criteria.where(ACTIONS).elemMatch(actionElemMatch));
        query.fields().include(ID, VERSION);
        query.limit(limit);

        return query;
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

    private Date feedThreshold() {
        return Date.from(Instant.now().minusSeconds(properties.getFeedTimeoutSeconds()));
    }
}
