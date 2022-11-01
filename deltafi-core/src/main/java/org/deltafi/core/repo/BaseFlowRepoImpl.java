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

import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.core.generated.types.FlowState;
import org.deltafi.core.types.Flow;
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
    private static final String TEST_MODE = "flowStatus.testMode";

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
                Query idMatches = Query.query(Criteria.where(ID).is(flowName).and(FLOW_STATUS_STATE).ne(flowState));
        Update flowStateUpdate = Update.update(FLOW_STATUS_STATE, flowState);
        return 1 == mongoTemplate.updateFirst(idMatches, flowStateUpdate, entityType).getModifiedCount();
    }

    @Override
    public boolean updateFlowTestMode(String flowName, boolean testMode) {
        Query idMatches = Query.query(Criteria.where(ID).is(flowName).and(TEST_MODE).is(!testMode));
        Update testModeUpdate = Update.update(TEST_MODE, testMode);
        return 1 == mongoTemplate.updateFirst(idMatches, testModeUpdate, entityType).getModifiedCount();
    }

}
