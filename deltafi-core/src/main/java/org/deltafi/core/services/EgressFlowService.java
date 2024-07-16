/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
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
import org.deltafi.common.types.Subscriber;
import org.deltafi.core.converters.EgressFlowPlanConverter;
import org.deltafi.core.repo.EgressFlowRepo;
import org.deltafi.core.services.pubsub.SubscriberService;
import org.deltafi.core.snapshot.SystemSnapshot;
import org.deltafi.core.snapshot.types.EgressFlowSnapshot;
import org.deltafi.core.types.EgressFlow;

import org.deltafi.core.types.EgressFlowPlanEntity;
import org.deltafi.core.validation.EgressFlowValidator;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
public
class EgressFlowService extends FlowService<EgressFlowPlanEntity, EgressFlow, EgressFlowSnapshot> implements SubscriberService {

    private static final EgressFlowPlanConverter EGRESS_FLOW_PLAN_CONVERTER = new EgressFlowPlanConverter();

    private Map<String, Set<Subscriber>> topicSubscribers;

    public EgressFlowService(EgressFlowRepo flowRepo, PluginVariableService pluginVariableService, EgressFlowValidator egressFlowValidator, BuildProperties buildProperties) {
        super("egress", flowRepo, pluginVariableService, EGRESS_FLOW_PLAN_CONVERTER, egressFlowValidator, buildProperties);
        refreshCache();
    }

    @Override
    public synchronized void refreshCache() {
        super.refreshCache();
        topicSubscribers = buildSubsriberMap();
    }

    @Override
    void copyFlowSpecificFields(EgressFlow sourceFlow, EgressFlow targetFlow) {
        targetFlow.setExpectedAnnotations(sourceFlow.getExpectedAnnotations());
    }

    @Override
    protected Class<EgressFlowPlanEntity> getFlowPlanClass() {
        return EgressFlowPlanEntity.class;
    }

    /**
     * Sets the expected set of annotations for a given flow, identified by its name. If the update is successful,
     * the method refreshes the cache and returns true.
     *
     * @param flowName The name of the flow to update, represented as a {@code String}.
     * @param expectedAnnotations The new set of expected annotations to be set for the specified flow, as an {@code set}
     * @return A {@code boolean} value indicating whether the update was successful (true) or not (false)
     */
    public boolean setExpectedAnnotations(String flowName, Set<String> expectedAnnotations) {
        EgressFlow flow = getFlowOrThrow(flowName);

        if (Objects.equals(expectedAnnotations, flow.getExpectedAnnotations())) {
            log.warn("Tried to update the expected annotations on egress flow {} to the same set of annotations: {}", flowName, expectedAnnotations);
            return false;
        }

        if (((EgressFlowRepo) flowRepo).updateExpectedAnnotations(flowName, expectedAnnotations)) {
            refreshCache();
            return true;
        }

        return false;
    }

    @Override
    public void updateSnapshot(SystemSnapshot systemSnapshot) {
        refreshCache();
        systemSnapshot.setEgressFlows(getAll().stream().map(EgressFlowSnapshot::new).toList());
    }

    @Override
    public List<EgressFlowSnapshot> getFlowSnapshots(SystemSnapshot systemSnapshot) {
        return systemSnapshot.getEgressFlows();
    }

    @Override
    public Set<Subscriber> subscriberForTopic(String topic) {
        return topicSubscribers.getOrDefault(topic, Set.of());
    }
}
