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
package org.deltafi.core.domain.services;

import lombok.AllArgsConstructor;
import org.deltafi.core.domain.api.converters.KeyValueConverter;
import org.deltafi.core.domain.api.types.ActionInput;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.configuration.*;
import org.deltafi.core.domain.generated.types.DeltaFileStage;
import org.deltafi.core.domain.types.EgressFlow;
import org.deltafi.core.domain.types.EnrichFlow;
import org.deltafi.core.domain.types.IngressFlow;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class StateMachine {

    private final IngressFlowService ingressFlowService;
    private final EnrichFlowService enrichFlowService;
    private final EgressFlowService egressFlowService;

    /**
     * Advance the state of the given DeltaFile
     *
     * @param deltaFile The deltaFile to advance
     * @return a list of actions that should receive this DeltaFile next
     */
    public List<ActionInput> advance(DeltaFile deltaFile) {
        List<ActionInput> enqueueActions = new ArrayList<>();
        switch (deltaFile.getStage()) {
            case INGRESS:
                if (deltaFile.hasErroredAction()) {
                    break;
                }

                IngressFlow ingressFlow = ingressFlowService.getRunningFlowByName(deltaFile.getSourceInfo().getFlow());

                // transform
                TransformActionConfiguration nextTransformAction = getTransformAction(ingressFlow, deltaFile);
                if (nextTransformAction != null) {
                    deltaFile.queueAction(nextTransformAction.getName());
                    enqueueActions.add(nextTransformAction.buildActionInput(deltaFile));
                    break;
                }

                // load
                LoadActionConfiguration loadAction = ingressFlow.getLoadAction();
                if (loadAction != null && !deltaFile.hasTerminalAction(loadAction.getName())) {
                    deltaFile.queueAction(loadAction.getName());
                    enqueueActions.add(loadAction.buildActionInput(deltaFile));
                    break;
                }

                // if transform and load are complete, move to enrich stage
                deltaFile.setStage(DeltaFileStage.ENRICH);
            case ENRICH:
                List<ActionInput> enrichActions = enrichFlowService.getRunningFlows().stream()
                        .map(enrichFlow -> advanceEnrich(enrichFlow, deltaFile))
                        .flatMap(Collection::stream)
                        .collect(Collectors.toList());

                if (!enrichActions.isEmpty()) {
                    enrichActions.forEach(actionInput -> deltaFile.queueNewAction(actionInput.getActionContext().getName()));
                    enqueueActions.addAll(enrichActions);
                    break;
                }

                // if all enrich actions are complete, move to egress stage
                deltaFile.setStage(DeltaFileStage.EGRESS);
            case EGRESS:
                List<ActionInput> egressActions = egressFlowService.getMatchingFlows(deltaFile.getSourceInfo().getFlow()).stream()
                        .map(egressFlow -> advanceEgress(egressFlow, deltaFile))
                        .flatMap(Collection::stream)
                        .collect(Collectors.toList());

                egressActions.forEach(actionInput -> deltaFile.queueNewAction(actionInput.getActionContext().getName()));

                enqueueActions.addAll(egressActions);

                break;
        }

        if (!deltaFile.hasPendingActions()) {
            deltaFile.setStage(deltaFile.hasErroredAction() ? DeltaFileStage.ERROR : DeltaFileStage.COMPLETE);
        }

        DeltaFilesService.calculateTotalBytes(deltaFile);
        return enqueueActions;
    }

    private TransformActionConfiguration getTransformAction(IngressFlow ingressFlow, DeltaFile deltaFile) {
        return ingressFlow.getTransformActions().stream()
                .filter(transformAction -> !deltaFile.hasTerminalAction(transformAction.getName()))
                .findFirst().orElse(null);
    }

    List<ActionInput> advanceEnrich(EnrichFlow enrichFlow, DeltaFile deltaFile) {
        return enrichFlow.getEnrichActions().stream()
                .filter(enrichActionConfiguration -> enrichActionReady(enrichActionConfiguration, deltaFile))
                .filter(enrichActionConfiguration -> isNewAction(enrichActionConfiguration, deltaFile))
                .map(actionConfiguration -> actionConfiguration.buildActionInput(deltaFile))
                .collect(Collectors.toList());
    }

    List<ActionInput> advanceEgress(EgressFlow egressFlow, DeltaFile deltaFile) {
        return nextEgressActions(egressFlow, deltaFile).stream()
                .filter(actionConfiguration -> isNewAction(actionConfiguration, deltaFile))
                .map(actionConfiguration -> actionConfiguration.buildActionInput(deltaFile))
                .collect(Collectors.toList());
    }

    List<ActionConfiguration> nextEgressActions(EgressFlow egressFlow, DeltaFile deltaFile) {
        List<ActionConfiguration> nextActions = new ArrayList<>();

        if (formatActionReady(egressFlow.getFormatAction(), deltaFile)) {
            nextActions.add(egressFlow.getFormatAction());
            return nextActions;
        }

        nextActions.addAll(getValidateActions(egressFlow, deltaFile));

        if (nextActions.isEmpty() && egressActionReady(egressFlow, deltaFile)) {
            nextActions.add(egressFlow.getEgressAction());
        }

        return nextActions;
    }

    boolean isNewAction(ActionConfiguration actionConfiguration, DeltaFile deltaFile) {
        return deltaFile.isNewAction(actionConfiguration.getName());
    }

    private boolean enrichActionReady(EnrichActionConfiguration enrichAction, DeltaFile deltaFile) {
        return !deltaFile.hasTerminalAction(enrichAction.getName()) &&
                deltaFile.hasDomains(enrichAction.getRequiresDomains()) &&
                deltaFile.hasEnrichments(enrichAction.getRequiresEnrichment()) &&
                hasMetadataMatches(deltaFile, enrichAction);
    }

    private boolean hasMetadataMatches(DeltaFile deltaFile, EnrichActionConfiguration config) {
        Map<String, String> requiresMetadata = KeyValueConverter.convertKeyValues(config.getRequiresMetadataKeyValues());
        if (requiresMetadata.isEmpty()) {
            return true;
        } else {
            if ((deltaFile.getProtocolStack() != null) && !deltaFile.getProtocolStack().isEmpty()) {
                Map<String, String> metadataMap = KeyValueConverter.convertKeyValues(deltaFile.getLastProtocolLayer().getMetadata());
                if (matchesAllMetadata(requiresMetadata, metadataMap)) {
                    return true;
                }
            }

            if (deltaFile.getSourceInfo() != null) {
                Map<String, String> sourceInfoMetadataMap = KeyValueConverter.convertKeyValues(deltaFile.getSourceInfo().getMetadata());
                return matchesAllMetadata(requiresMetadata, sourceInfoMetadataMap);
            }
        }
        return false;
    }

    private boolean matchesAllMetadata(Map<String, String> requiresMetadata, Map<String, String> metadataMap) {
        return requiresMetadata.keySet().stream().allMatch(k -> requiresMetadata.get(k).equals(metadataMap.get(k)));
    }

    private boolean formatActionReady(FormatActionConfiguration config, DeltaFile deltaFile) {
        return !deltaFile.hasTerminalAction(config.getName()) &&
                deltaFile.hasDomains(config.getRequiresDomains()) &&
                deltaFile.hasEnrichments(config.getRequiresEnrichment());
    }

    List<ActionConfiguration> getValidateActions(EgressFlow egressFlow, DeltaFile deltaFile) {
        if (Objects.isNull(egressFlow.getValidateActions()) || !validateActionsReady(egressFlow, deltaFile)) {
            return Collections.emptyList();
    }

        return egressFlow.getValidateActions().stream()
                .filter(v -> !deltaFile.hasTerminalAction(v.getName())).collect(Collectors.toList());
    }

    private boolean validateActionsReady(EgressFlow egressFlow, DeltaFile deltaFile) {
        return deltaFile.hasCompletedAction(egressFlow.getFormatAction().getName());
    }

    private boolean egressActionReady(EgressFlow egressFlow, DeltaFile deltaFile) {
        return deltaFile.hasCompletedAction(egressFlow.getFormatAction().getName()) &&
                deltaFile.hasCompletedActions(egressFlow.validateActionNames()) &&
                !deltaFile.hasTerminalAction(egressFlow.getEgressAction().getName());
    }

}
