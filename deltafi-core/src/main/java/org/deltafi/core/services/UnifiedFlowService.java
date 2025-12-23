/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.types.ActionConfiguration;
import org.deltafi.common.types.ActionType;
import org.deltafi.common.types.FlowType;
import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.core.generated.types.FlowState;
import org.deltafi.core.repo.FlowStateUpdater;
import org.deltafi.core.types.DataSink;
import org.deltafi.core.types.Flow;
import org.deltafi.core.types.FlowTagFilter;
import org.deltafi.core.types.TransformFlow;
import org.springframework.stereotype.Service;

import java.util.*;

@AllArgsConstructor
@Slf4j
@Service
public class UnifiedFlowService {
    private final TimedDataSourceService timedDataSourceService;
    private final DataSinkService dataSinkService;
    private final TransformFlowService transformFlowService;
    private final RestDataSourceService restDataSourceService;
    private final OnErrorDataSourceService onErrorDataSourceService;
    private final FlowStateUpdater flowStateUpdater;

    public List<ActionConfiguration> runningTransformActions() {
        return new ArrayList<>(transformFlowService.getRunningFlows().stream()
                .map(TransformFlow::getTransformActions)
                .flatMap(Collection::stream).toList());
    }

    public List<ActionConfiguration> runningEgressActions() {
        return new ArrayList<>(dataSinkService.getRunningFlows().stream()
                .map(DataSink::getEgressAction)
                .filter(Objects::nonNull)
                .toList());
    }

    public ActionConfiguration runningAction(String actionName, ActionType actionType) {
        if (actionName == null || actionType == null) {
            return null;
        }

        return switch (actionType) {
            case TRANSFORM:
                yield runningTransformActions().stream()
                        .filter(action -> Objects.equals(action.getName(), actionName))
                        .findFirst()
                        .orElse(null);
            case EGRESS:
                yield runningEgressActions().stream()
                        .filter(action -> Objects.equals(action.getName(), actionName))
                        .findFirst()
                        .orElse(null);
            default:
                yield null;
        };
    }

    public List<ActionConfiguration> allActionConfigurations() {
        List<ActionConfiguration> configs = new ArrayList<>(transformFlowService.getAll().stream()
                .map(TransformFlow::allActionConfigurations)
                .flatMap(Collection::stream)
                .toList());

        configs.addAll(dataSinkService.getAll().stream()
                .map(DataSink::allActionConfigurations)
                .flatMap(Collection::stream)
                .toList());

        return configs;
    }

    public List<Flow> setFlowStateByTags(FlowTagFilter filter, FlowState flowState) {
        List<Flow> flows = flowStateUpdater.setFlowStateByTags(filter, flowState);
        dataSinkService.refreshCache();
        transformFlowService.refreshCache();
        restDataSourceService.refreshCache();
        timedDataSourceService.refreshCache();
        onErrorDataSourceService.refreshCache();
        return flows;
    }

    public List<Flow> findByTagsAndNewState(FlowTagFilter filter, FlowState flowState) {
        return flowStateUpdater.findByTagsAndNewState(filter, flowState);
    }

    public List<Flow> findByTags(FlowTagFilter filter) {
        return flowStateUpdater.findByTags(filter);
    }

    /**
     * Revalidate a flow by name and type.
     * @param flowName the name of the flow
     * @param flowType the type of the flow
     */
    public void revalidateFlow(String flowName, FlowType flowType) {
        try {
            switch (flowType) {
                case REST_DATA_SOURCE -> restDataSourceService.validateAndSaveFlow(flowName);
                case TIMED_DATA_SOURCE -> timedDataSourceService.validateAndSaveFlow(flowName);
                case ON_ERROR_DATA_SOURCE -> onErrorDataSourceService.validateAndSaveFlow(flowName);
                case TRANSFORM -> transformFlowService.validateAndSaveFlow(flowName);
                case DATA_SINK -> dataSinkService.validateAndSaveFlow(flowName);
            }
        } catch (Exception e) {
            log.warn("Failed to revalidate flow {} of type {}: {}", flowName, flowType, e.getMessage());
        }
    }

    /**
     * Revalidate all invalid flows for a given plugin.
     * @param pluginCoordinates the plugin whose flows should be revalidated
     * @param invalidFlows the list of invalid flows to revalidate
     */
    public void revalidateFlowsForPlugin(PluginCoordinates pluginCoordinates, List<Flow> invalidFlows) {
        if (invalidFlows.isEmpty()) {
            return;
        }
        log.info("Revalidating {} invalid flow(s) for plugin {}", invalidFlows.size(), pluginCoordinates);
        for (Flow flow : invalidFlows) {
            revalidateFlow(flow.getName(), flow.getType());
        }
    }
}
