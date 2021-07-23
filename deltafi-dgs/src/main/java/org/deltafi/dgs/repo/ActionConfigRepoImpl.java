package org.deltafi.dgs.repo;

import com.mongodb.client.result.DeleteResult;
import org.deltafi.dgs.configuration.*;
import org.deltafi.dgs.generated.types.ActionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.FindAndReplaceOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.time.OffsetDateTime;
import java.util.List;

@SuppressWarnings("unused")
public class ActionConfigRepoImpl implements ActionConfigRepoCustom {

    private static final Logger log = LoggerFactory.getLogger(ActionConfigRepoImpl.class);
    private static final String COLLECTION_NAME = "actions";

    private static final String ID = "_id";
    private static final String NAME = "name";
    private static final String ACTION_TYPE = "actionType";

    private static final FindAndReplaceOptions UPSERT = FindAndReplaceOptions.options().upsert().returnNew();

    private final MongoTemplate mongoTemplate;

    public ActionConfigRepoImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public <C extends ActionConfiguration> C upsertConfiguration(C config, Class<C> clazz) {
        config.setModified(OffsetDateTime.now());
        return mongoTemplate.findAndReplace(byNameAndActionType(config.getName(), config.getActionType()),
                config, UPSERT, clazz, COLLECTION_NAME);
    }

    @Override
    public LoadActionConfiguration findLoadAction(String name) {
        return findByName(name, LoadActionConfiguration.class);
    }

    @Override
    public FormatActionConfiguration findFormatAction(String name) {
        return findByName(name, FormatActionConfiguration.class);
    }

    @Override
    public EnrichActionConfiguration findEnrichAction(String name) {
        return findByName(name, EnrichActionConfiguration.class);
    }

    @Override
    public boolean exists(ActionConfiguration config) {
        return mongoTemplate.count(byNameAndActionType(config.getName(), config.getActionType()), COLLECTION_NAME) > 0;
    }

    @Override
    public long deleteAllWithCount() {
        DeleteResult result = mongoTemplate.remove(new Query(), COLLECTION_NAME);
        return result.getDeletedCount();
    }

    private <C extends ActionConfiguration> C findByName(String name, Class<C> clazz) {
        return mongoTemplate.findOne(byName(name), clazz, COLLECTION_NAME);
    }

    private Query byName(String name) {
        return Query.query(Criteria.where(NAME).is(name));
    }

    private Query byNameAndActionType(String name, ActionType actionType) {
        return Query.query(Criteria.where(NAME).is(name).and(ACTION_TYPE).is(actionType));
    }

    private <C extends ActionConfiguration> List<C> findAllByConfigType(ActionType type, Class<C> clazz) {
        Query query = Query.query(Criteria.where(ACTION_TYPE).is(type));
        return mongoTemplate.find(query, clazz, COLLECTION_NAME);
    }

}
