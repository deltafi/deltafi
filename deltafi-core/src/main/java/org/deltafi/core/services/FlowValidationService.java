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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.types.FlowType;
import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.core.types.Flow;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Handles flow revalidation when plugin state changes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FlowValidationService {

    private final FlowCacheService flowCacheService;
    private final TimedDataSourceService timedDataSourceService;
    private final RestDataSourceService restDataSourceService;
    private final OnErrorDataSourceService onErrorDataSourceService;
    private final TransformFlowService transformFlowService;
    private final DataSinkService dataSinkService;
    private final PluginService pluginService;

    /**
     * Revalidate all flows affected by a plugin state change.
     * This includes flows owned by the plugin AND flows using actions from the plugin.
     * Called when a plugin transitions state (INSTALLED, PENDING, FAILED, etc).
     */
    public void revalidateFlowsForPlugin(PluginCoordinates pluginCoordinates) {
        flowCacheService.refreshCache();
        pluginService.updateActionDescriptors();
        String artifactId = pluginCoordinates.getArtifactId();

        // Find all flows that are affected by this plugin:
        // 1. Flows owned by this plugin (sourcePlugin matches)
        // 2. Flows using any action from this plugin
        List<Flow> affectedFlows = flowCacheService.getAllFlows().stream()
                .filter(flow -> isFlowAffectedByPlugin(flow, pluginCoordinates, artifactId))
                .toList();

        if (affectedFlows.isEmpty()) {
            return;
        }

        log.info("Revalidating {} flow(s) affected by plugin {} state change", affectedFlows.size(), artifactId);
        for (Flow flow : affectedFlows) {
            revalidateFlow(flow.getName(), flow.getType());
        }
    }

    private boolean isFlowAffectedByPlugin(Flow flow, PluginCoordinates pluginCoordinates, String artifactId) {
        // Check if flow is owned by this plugin
        if (flow.getSourcePlugin() != null &&
                pluginCoordinates.equalsIgnoreVersion(flow.getSourcePlugin())) {
            return true;
        }

        // Check if flow uses any action from this plugin
        return flow.allActionConfigurations().stream()
                .anyMatch(action -> {
                    String actionPlugin = pluginService.getPluginWithAction(action.getType());
                    return artifactId.equals(actionPlugin);
                });
    }

    /**
     * Periodic sweep to revalidate invalid flows.
     * Catches edge cases like core restarts or race conditions.
     */
    @Scheduled(fixedRateString = "${deltafi.flow.revalidation.interval:30000}")
    public void revalidateInvalidFlows() {
        flowCacheService.refreshCache();
        List<Flow> invalidFlows = flowCacheService.getInvalidFlows();
        if (invalidFlows.isEmpty()) {
            return;
        }

        int becameValid = 0;
        for (Flow flow : invalidFlows) {
            if (revalidateFlow(flow.getName(), flow.getType())) {
                becameValid++;
            }
        }
        if (becameValid > 0) {
            log.info("Periodic revalidation: {} flow(s) became valid", becameValid);
        }
    }

    private boolean revalidateFlow(String flowName, FlowType flowType) {
        try {
            Flow result = switch (flowType) {
                case REST_DATA_SOURCE -> restDataSourceService.validateAndSaveFlow(flowName);
                case TIMED_DATA_SOURCE -> timedDataSourceService.validateAndSaveFlow(flowName);
                case ON_ERROR_DATA_SOURCE -> onErrorDataSourceService.validateAndSaveFlow(flowName);
                case TRANSFORM -> transformFlowService.validateAndSaveFlow(flowName);
                case DATA_SINK -> dataSinkService.validateAndSaveFlow(flowName);
            };
            return result != null && !result.isInvalid();
        } catch (Exception e) {
            log.warn("Failed to revalidate flow {} of type {}: {}", flowName, flowType, e.getMessage());
            return false;
        }
    }
}
