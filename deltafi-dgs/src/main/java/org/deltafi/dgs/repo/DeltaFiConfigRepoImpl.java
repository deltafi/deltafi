package org.deltafi.dgs.repo;

import com.mongodb.client.result.DeleteResult;
import org.deltafi.dgs.api.types.ConfigType;
import org.deltafi.dgs.configuration.*;
import org.springframework.data.mongodb.core.FindAndReplaceOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

import static org.deltafi.dgs.api.types.ConfigType.*;

@SuppressWarnings("unused")
public class DeltaFiConfigRepoImpl implements DeltaFiConfigRepoCustom {

    public static final String NAME = "name";
    public static final String EGRESS_ACTION_NAME = "egressAction";
    public static final String CONFIG_TYPE = "configType";
    public static final String COLLECTION_NAME = "deltaFiConfig";

    public static final List<ConfigType> FLOW_CONFIGS = Arrays.asList(INGRESS_FLOW, EGRESS_FLOW);

    private static final FindAndReplaceOptions UPSERT = FindAndReplaceOptions.options().upsert().returnNew();

    private final MongoTemplate mongoTemplate;

    public DeltaFiConfigRepoImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public <C extends DeltaFiConfiguration> C upsertConfiguration(C config, Class<C> clazz) {
        config.setModified(OffsetDateTime.now());
        return mongoTemplate.findAndReplace(byNameAndConfigType(config.getName(), config.getConfigType()),
                config, UPSERT, clazz, COLLECTION_NAME);
    }

    @Override
    public LoadActionConfiguration findLoadAction(String name) {
        return findByNameAndType(name, LOAD_ACTION, LoadActionConfiguration.class);
    }

    @Override
    public FormatActionConfiguration findFormatAction(String name) {
        return findByNameAndType(name, FORMAT_ACTION, FormatActionConfiguration.class);
    }

    @Override
    public EnrichActionConfiguration findEnrichAction(String name) {
        return findByNameAndType(name, ENRICH_ACTION, EnrichActionConfiguration.class);
    }

    @Override
    public LoadActionGroupConfiguration findLoadActionGroup(String name) {
        return findByNameAndType(name, LOAD_ACTION_GROUP, LoadActionGroupConfiguration.class);
    }

    @Override
    public IngressFlowConfiguration findIngressFlowConfig(String name) {
        return findByNameAndType(name, INGRESS_FLOW, IngressFlowConfiguration.class);
    }

    @Override
    public EgressFlowConfiguration findEgressFlowConfig(String name) {
        return findByNameAndType(name, EGRESS_FLOW, EgressFlowConfiguration.class);
    }

    @Override
    public EgressFlowConfiguration findEgressFlowForAction(String actionName) {
        Query query = Query.query(Criteria.where(CONFIG_TYPE).is(EGRESS_FLOW).and(EGRESS_ACTION_NAME).is(actionName));
        return mongoTemplate.findOne(query, EgressFlowConfiguration.class, COLLECTION_NAME);
    }

    @Override
    public List<EgressFlowConfiguration> findAllEgressFlows() {
        Query query = Query.query(Criteria.where(CONFIG_TYPE).is(EGRESS_FLOW));
        return mongoTemplate.find(query, EgressFlowConfiguration.class, COLLECTION_NAME);
    }

    @Override
    public boolean exists(DeltaFiConfiguration config) {
        return mongoTemplate.count(byNameAndConfigType(config.getName(), config.getConfigType()), COLLECTION_NAME) > 0;
    }

    @Override
    public void deleteActionConfigs() {
        Query query = Query.query(Criteria.where(CONFIG_TYPE).nin(FLOW_CONFIGS));
        mongoTemplate.remove(query, COLLECTION_NAME);
    }

    @Override
    public void deleteFlowConfigs() {
        Query query = Query.query(Criteria.where(CONFIG_TYPE).in(FLOW_CONFIGS));
        mongoTemplate.remove(query, COLLECTION_NAME);
    }

    @Override
    public long deleteAllWithCount() {
        DeleteResult result = mongoTemplate.remove(new Query(), COLLECTION_NAME);
        return result.getDeletedCount();
    }

    <C extends DeltaFiConfiguration> C findByNameAndType(String name, ConfigType configType, Class<C> clazz) {
        return mongoTemplate.findOne(byNameAndConfigType(name, configType), clazz, COLLECTION_NAME);
    }

    Query byNameAndConfigType(String name, ConfigType configType) {
        return Query.query(Criteria.where(NAME).is(name).and(CONFIG_TYPE).is(configType));
    }

}
