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

import lombok.AllArgsConstructor;
import org.deltafi.common.types.ActionConfiguration;
import org.deltafi.common.types.ActionType;
import org.deltafi.core.types.EgressFlow;
import org.deltafi.core.types.TransformFlow;
import org.springframework.stereotype.Service;

import java.util.*;

@AllArgsConstructor
@Service
public class UnifiedFlowService {
    EgressFlowService egressFlowService;
    TransformFlowService transformFlowService;

    public List<ActionConfiguration> runningTransformActions() {
        return new ArrayList<>(transformFlowService.getRunningFlows().stream()
                .map(TransformFlow::getTransformActions)
                .flatMap(Collection::stream).toList());
    }

    public List<ActionConfiguration> runningEgressActions() {
        return new ArrayList<>(egressFlowService.getRunningFlows().stream()
                .map(EgressFlow::getEgressAction)
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

        configs.addAll(egressFlowService.getAll().stream()
                .map(EgressFlow::allActionConfigurations)
                .flatMap(Collection::stream)
                .toList());

        return configs;
    }
}
