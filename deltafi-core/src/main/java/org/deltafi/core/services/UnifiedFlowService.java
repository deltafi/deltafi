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

import lombok.AllArgsConstructor;
import org.deltafi.common.types.ActionConfiguration;
import org.deltafi.common.types.ActionType;
import org.deltafi.common.types.TransformActionConfiguration;
import org.deltafi.core.types.EgressFlow;
import org.deltafi.core.types.EnrichFlow;
import org.deltafi.core.types.NormalizeFlow;
import org.deltafi.core.types.TransformFlow;
import org.springframework.stereotype.Service;

import java.util.*;

@AllArgsConstructor
@Service
public class UnifiedFlowService {
    NormalizeFlowService normalizeFlowService;
    EnrichFlowService enrichFlowService;
    EgressFlowService egressFlowService;
    TransformFlowService transformFlowService;

    public List<TransformActionConfiguration> runningTransformActions() {
        List<TransformActionConfiguration> configs = new ArrayList<>(transformFlowService.getRunningFlows().stream()
                .map(TransformFlow::getTransformActions)
                .flatMap(Collection::stream).toList());
        configs.addAll(normalizeFlowService.getRunningFlows().stream()
                .map(NormalizeFlow::getTransformActions)
                .flatMap(Collection::stream).toList());
        return configs;
    }

    public ActionConfiguration runningAction(String actionName, ActionType actionType) {
        return switch (actionType) {
            case TRANSFORM:
                yield runningTransformActions().stream()
                        .filter(action -> Objects.equals(action.getName(), actionName))
                        .findFirst()
                        .orElse(null);
            case LOAD:
                yield normalizeFlowService.getRunningFlows().stream()
                        .map(NormalizeFlow::getLoadAction)
                        .filter(action -> action.getName().equals(actionName))
                        .findFirst()
                        .orElse(null);
            case DOMAIN:
                yield enrichFlowService.getRunningFlows().stream()
                        .map(EnrichFlow::getDomainActions)
                        .flatMap(Collection::stream)
                        .filter(action -> action.getName().equals(actionName))
                        .findFirst()
                        .orElse(null);
            case ENRICH:
                yield enrichFlowService.getRunningFlows().stream()
                        .map(EnrichFlow::getEnrichActions)
                        .flatMap(Collection::stream)
                        .filter(action -> action.getName().equals(actionName))
                        .findFirst()
                        .orElse(null);
            case FORMAT:
                yield egressFlowService.getRunningFlows().stream()
                        .map(EgressFlow::getFormatAction)
                        .filter(action -> action.getName().equals(actionName))
                        .findFirst()
                        .orElse(null);
            case VALIDATE:
                yield egressFlowService.getRunningFlows().stream()
                        .map(EgressFlow::getValidateActions)
                        .flatMap(Collection::stream)
                        .filter(action -> action.getName().equals(actionName))
                        .findFirst()
                        .orElse(null);
            case EGRESS:
                yield egressFlowService.getRunningFlows().stream()
                        .map(EgressFlow::getEgressAction)
                        .filter(action -> action.getName().equals(actionName))
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

        configs.addAll(normalizeFlowService.getAll().stream()
                .map(NormalizeFlow::allActionConfigurations)
                .flatMap(Collection::stream)
                .toList());

        configs.addAll(enrichFlowService.getAll().stream()
                .map(EnrichFlow::allActionConfigurations)
                .flatMap(Collection::stream)
                .toList());

        configs.addAll(egressFlowService.getAll().stream()
                .map(EgressFlow::allActionConfigurations)
                .flatMap(Collection::stream)
                .toList());

        return configs;
    }
}
