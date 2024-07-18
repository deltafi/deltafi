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
import org.deltafi.common.types.FlowType;
import org.deltafi.core.converters.TransformFlowPlanConverter;
import org.deltafi.core.generated.types.IngressFlowErrorState;
import org.deltafi.core.repo.TransformFlowRepo;
import org.deltafi.common.types.Subscriber;
import org.deltafi.core.services.pubsub.SubscriberService;
import org.deltafi.core.snapshot.SystemSnapshot;
import org.deltafi.core.snapshot.types.TransformFlowSnapshot;
import org.deltafi.core.types.*;
import org.deltafi.core.validation.TransformFlowValidator;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class TransformFlowService extends FlowService<TransformFlowPlanEntity, TransformFlow, TransformFlowSnapshot, TransformFlowRepo> implements SubscriberService {

    private static final TransformFlowPlanConverter TRANSFORM_FLOW_PLAN_CONVERTER = new TransformFlowPlanConverter();

    private final ErrorCountService errorCountService;

    private Map<String, Set<Subscriber>> topicSubscribers;

    public TransformFlowService(TransformFlowRepo transformFlowRepo, PluginVariableService pluginVariableService, TransformFlowValidator transformFlowValidator, ErrorCountService errorCountService, BuildProperties buildProperties) {
        super("transform", transformFlowRepo, pluginVariableService, TRANSFORM_FLOW_PLAN_CONVERTER, transformFlowValidator, buildProperties);
        this.errorCountService = errorCountService;
        refreshCache();
    }

    @Override
    public synchronized void refreshCache() {
        super.refreshCache();
        topicSubscribers = buildSubsriberMap();
    }

    @Override
    void copyFlowSpecificFields(TransformFlow sourceFlow, TransformFlow targetFlow) {
        targetFlow.setMaxErrors(sourceFlow.getMaxErrors());
    }

    @Override
    protected Class<TransformFlow> getFlowClass() {
        return TransformFlow.class;
    }

    @Override
    protected Class<TransformFlowPlanEntity> getFlowPlanClass() {
        return TransformFlowPlanEntity.class;
    }

    @Override
    protected FlowType getFlowType() {
        return FlowType.TRANSFORM;
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
    public boolean flowSpecificUpdateFromSnapshot(TransformFlow flow, TransformFlowSnapshot transformFlowSnapshot, Result result) {
        if (flow.getMaxErrors() != transformFlowSnapshot.getMaxErrors()) {
            flow.setMaxErrors(transformFlowSnapshot.getMaxErrors());
            return true;
        }

        return false;
    }

    @Override
    public Set<Subscriber> subscriberForTopic(String topic) {
        return topicSubscribers.getOrDefault(topic, Set.of());
    }

    /**
     * Sets the maximum number of errors allowed for a given flow, identified by its name.
     * If the maximum errors for the flow are already set to the specified value, the method
     * logs a warning and returns false. If the update is successful, the method refreshes the
     * cache and returns true.
     *
     * @param flowName  The name of the flow to update, represented as a {@code String}.
     * @param maxErrors The new maximum number of errors to be set for the specified flow, as an {@code int}.
     * @return A {@code boolean} value indicating whether the update was successful (true) or not (false).
     */
    public boolean setMaxErrors(String flowName, int maxErrors) {
        TransformFlow flow = getFlowOrThrow(flowName);

        if (flow.getMaxErrors() == maxErrors) {
            log.warn("Tried to set max errors on transform flow {} to {} when already set", flowName, maxErrors);
            return false;
        }

        if (flowRepo.updateMaxErrors(flowName, maxErrors) > 0) {
            refreshCache();
            return true;
        }

        return false;
    }

    /**
     * Retrieves a map containing the maximum number of errors allowed per flow.
     * This method filters out flows with a maximum error count of 0, only including
     * those with a positive maximum error count.
     *
     * @return A {@code Map<String, Integer>} where each key represents a flow name,
     * and the corresponding value is the maximum number of errors allowed for that flow.
     */
    public Map<String, Integer> maxErrorsPerFlow() {
        return getRunningFlows().stream()
                .filter(e -> e.getMaxErrors() >= 0)
                .collect(Collectors.toMap(Flow::getName, TransformFlow::getMaxErrors));
    }

    public List<IngressFlowErrorState> ingressFlowErrorsExceeded() {
        return getRunningFlows().stream()
                .map(f -> new IngressFlowErrorState(f.getName(), errorCountService.errorsForFlow(f.getName()), f.getMaxErrors()))
                .filter(s -> s.getMaxErrors() >= 0 && s.getCurrErrors() > s.getMaxErrors())
                .toList();
    }

    public Set<String> flowErrorsExceeded() {
        return ingressFlowErrorsExceeded()
                .stream()
                .map(IngressFlowErrorState::getName)
                .collect(Collectors.toSet());
    }

}
