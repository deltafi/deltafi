/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.services;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.types.FlowType;
import org.deltafi.common.types.Subscriber;
import org.deltafi.common.types.TransformFlowPlan;
import org.deltafi.core.converters.TransformFlowPlanConverter;
import org.deltafi.core.repo.TransformFlowRepo;
import org.deltafi.core.services.pubsub.SubscriberService;
import org.deltafi.core.types.snapshot.SystemSnapshot;
import org.deltafi.core.types.snapshot.TransformFlowSnapshot;
import org.deltafi.core.types.*;
import org.deltafi.core.types.TransformFlow;
import org.deltafi.core.validation.FlowValidator;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j
public class TransformFlowService extends FlowService<TransformFlowPlan, TransformFlow, TransformFlowSnapshot, TransformFlowRepo> implements SubscriberService {

    private static final TransformFlowPlanConverter TRANSFORM_FLOW_PLAN_CONVERTER = new TransformFlowPlanConverter();

    private Map<String, Set<Subscriber>> topicSubscribers;

    public TransformFlowService(TransformFlowRepo transformFlowRepo, PluginVariableService pluginVariableService, FlowValidator flowValidator, BuildProperties buildProperties, FlowCacheService flowCacheService) {
        super(FlowType.TRANSFORM, transformFlowRepo, pluginVariableService, TRANSFORM_FLOW_PLAN_CONVERTER, flowValidator, buildProperties, flowCacheService, TransformFlow.class, TransformFlowPlan.class);
    }

    @Override
    public void onRefreshCache() {
        topicSubscribers = buildSubscriberMap();
    }

    @Override
    public void updateSnapshot(SystemSnapshot systemSnapshot) {
        refreshCache();
        systemSnapshot.setTransformFlows(getAll().stream().map(TransformFlowSnapshot::new).toList());
    }

    @Override
    public List<TransformFlowSnapshot> getFlowSnapshots(SystemSnapshot systemSnapshot) {
        return systemSnapshot.getTransformFlows();
    }

    @Override
    public Set<Subscriber> subscriberForTopic(String topic) {
        return topicSubscribers.getOrDefault(topic, Set.of());
    }
}
