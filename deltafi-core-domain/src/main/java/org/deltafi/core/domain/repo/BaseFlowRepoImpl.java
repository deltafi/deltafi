package org.deltafi.core.domain.repo;

import org.deltafi.core.domain.api.types.PluginCoordinates;
import org.deltafi.core.domain.generated.types.FlowState;
import org.deltafi.core.domain.types.Flow;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;
import java.util.stream.Collectors;

public abstract class BaseFlowRepoImpl<T extends Flow> implements FlowRepoCustom<T> {

    private static final String ID = "_id";
    private static final String SOURCE_PLUGIN = "sourcePlugin";
    private static final String FLOW_STATUS_STATE = "flowStatus.state";

    private final MongoTemplate mongoTemplate;
    private final Class<T> entityType;

    protected BaseFlowRepoImpl(MongoTemplate mongoTemplate, Class<T> entityType) {
        this.mongoTemplate = mongoTemplate;
        this.entityType = entityType;
    }

    @Override
    public List<T> findRunning() {
        Query query = Query.query(Criteria.where(FLOW_STATUS_STATE).is(FlowState.RUNNING));
        return mongoTemplate.find(query, entityType);
    }

    @Override
    public List<String> findRunningBySourcePlugin(PluginCoordinates sourcePlugin) {
        Query runningPluginQuery = Query.query(Criteria.where(SOURCE_PLUGIN).is(sourcePlugin).and(FLOW_STATUS_STATE).is(FlowState.RUNNING));
        return mongoTemplate.find(runningPluginQuery, entityType).stream().map(Flow::getName).collect(Collectors.toList());
    }

    @Override
    public boolean updateFlowState(String flowName, FlowState flowState) {
        Query idMatches = Query.query(Criteria.where(ID).is(flowName));
        Update flowStateUpdate = Update.update(FLOW_STATUS_STATE, flowState);
        return 1 == mongoTemplate.updateFirst(idMatches, flowStateUpdate, entityType).getModifiedCount();
    }

}
