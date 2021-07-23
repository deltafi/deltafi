package org.deltafi.dgs.repo;

import com.mongodb.client.result.DeleteResult;
import org.deltafi.dgs.api.types.ConfigType;
import org.deltafi.dgs.configuration.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.FindAndReplaceOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static org.deltafi.dgs.api.types.ConfigType.*;

@SuppressWarnings("unused")
public class DeltaFiConfigRepoImpl implements DeltaFiConfigRepoCustom {

    private final static Logger log = LoggerFactory.getLogger(DeltaFiConfigRepoImpl.class);

    public static final String ID = "_id";
    public static final String NAME = "name";
    public static final String EGRESS_ACTION_NAME = "egressAction";
    public static final String CONFIG_TYPE = "configType";
    public static final String COLLECTION_NAME = "deltaFiConfig";
    public static final String FORMAT_ACTION_FIELD = "formatAction";

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
    public EgressFlowConfiguration findEgressFlowByEgressActionName(String actionName) {
        Query query = Query.query(Criteria.where(CONFIG_TYPE).is(EGRESS_FLOW).and(EGRESS_ACTION_NAME).is(actionName));
        return mongoTemplate.findOne(query, EgressFlowConfiguration.class, COLLECTION_NAME);
    }

    @Override
    public List<String> findEgressActionsWithFormatAction(String formatAction) {
        Query query = Query.query(Criteria.where(CONFIG_TYPE).is(EGRESS_FLOW).and(FORMAT_ACTION_FIELD).is(formatAction));
        query.fields().include(EGRESS_ACTION_NAME).exclude(ID);
        return mongoTemplate.find(query, EgressFlowConfiguration.class, COLLECTION_NAME).stream()
                .map(EgressFlowConfiguration::getEgressAction).collect(Collectors.toList());
    }

    @Override
    public List<EgressFlowConfiguration> findAllEgressFlows() {
        return findAllByConfigType(EGRESS_FLOW, EgressFlowConfiguration.class);
    }

    public List<DomainEndpointConfiguration> findAllDomainEndpoints() {
        return findAllByConfigType(DOMAIN_ENDPOINT, DomainEndpointConfiguration.class);
    }

    @Override
    public boolean exists(DeltaFiConfiguration config) {
        return mongoTemplate.count(byNameAndConfigType(config.getName(), config.getConfigType()), COLLECTION_NAME) > 0;
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

    <C extends DeltaFiConfiguration> List<C> findAllByConfigType(ConfigType type, Class<C> clazz) {
        Query query = Query.query(Criteria.where(CONFIG_TYPE).is(type));
        return mongoTemplate.find(query, clazz, COLLECTION_NAME);
    }

}
