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
package org.deltafi.core.services;

import lombok.AllArgsConstructor;
import org.deltafi.common.converters.KeyValueConverter;
import org.deltafi.common.types.*;
import org.deltafi.core.types.EgressFlow;
import org.deltafi.core.types.EnrichFlow;
import org.deltafi.core.types.IngressFlow;
import org.deltafi.core.configuration.*;
import org.deltafi.core.exceptions.MissingEgressFlowException;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.deltafi.common.constant.DeltaFiConstants.SYNTHETIC_EGRESS_ACTION_FOR_TEST_EGRESS;
import static org.deltafi.common.constant.DeltaFiConstants.SYNTHETIC_EGRESS_ACTION_FOR_TEST_INGRESS;

@Service
@AllArgsConstructor
public class StateMachine {

    private final IngressFlowService ingressFlowService;
    private final EnrichFlowService enrichFlowService;
    private final EgressFlowService egressFlowService;
    private final DeltaFiProperties deltaFiProperties;

    /**
     * Advance the state of the given DeltaFile
     *
     * @param deltaFile The deltaFile to advance
     * @return a list of actions that should receive this DeltaFile next
     * @throws MissingEgressFlowException when a DeltaFile advances into the EGRESS stage, but does not have an egress flow configured.
     */
    public List<ActionInput> advance(DeltaFile deltaFile) throws MissingEgressFlowException {
        List<ActionInput> enqueueActions = new ArrayList<>();
        switch (deltaFile.getStage()) {
            case INGRESS:
                if (deltaFile.hasErroredAction() || deltaFile.hasFilteredAction() || deltaFile.hasSplitAction()) {
                    break;
                }
                IngressFlow ingressFlow = ingressFlowService.getRunningFlowByName(deltaFile.getSourceInfo().getFlow());
                // transform
                TransformActionConfiguration nextTransformAction = getTransformAction(ingressFlow, deltaFile);
                if (nextTransformAction != null) {
                    deltaFile.queueAction(nextTransformAction.getName());
                    enqueueActions.add(buildActionInput(nextTransformAction, deltaFile, null));
                    break;
                }

                // load
                LoadActionConfiguration loadAction = ingressFlow.getLoadAction();
                if (loadAction != null && !deltaFile.hasTerminalAction(loadAction.getName())) {
                    deltaFile.queueAction(loadAction.getName());
                    enqueueActions.add(buildActionInput(loadAction, deltaFile, null));
                    break;
                }

                // if transform and load are complete, move to enrich stage
                deltaFile.setStage(DeltaFileStage.ENRICH);
            case ENRICH:
                if (deltaFile.hasErroredAction()) {
                    break;
                }

                List<ActionInput> enrichActions = enrichFlowService.getRunningFlows().stream()
                        .map(enrichFlow -> advanceEnrichStage(enrichFlow, deltaFile))
                        .flatMap(Collection::stream)
                        .collect(Collectors.toList());

                if (!enrichActions.isEmpty()) {
                    enrichActions.forEach(actionInput -> deltaFile.queueNewAction(actionInput.getActionContext().getName()));
                    enqueueActions.addAll(enrichActions);
                    break;
                }

                // if all enrich actions are complete without errors, move to egress stage
                if (!deltaFile.hasPendingActions() && !deltaFile.hasErroredAction()) {
                    deltaFile.setStage(DeltaFileStage.EGRESS);
                } else {
                    break;
                }
            case EGRESS:
                List<ActionInput> egressActions = egressFlowService.getMatchingFlows(deltaFile.getSourceInfo().getFlow()).stream()
                        .map(egressFlow -> advanceEgress(egressFlow, deltaFile))
                        .flatMap(Collection::stream)
                        .collect(Collectors.toList());

                if (deltaFile.getEgress().isEmpty() && egressActions.isEmpty()) {
                    throw new MissingEgressFlowException(deltaFile.getDid());
                }

                egressActions.forEach(actionInput -> {
                    deltaFile.addEgressFlow(actionInput.getActionContext().getEgressFlow());
                    deltaFile.queueNewAction(actionInput.getActionContext().getName());
                });

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

    List<ActionInput> advanceEnrichStage(EnrichFlow enrichFlow, DeltaFile deltaFile) {
        List<ActionInput> domainActions = nextDomainActions(enrichFlow, deltaFile);

        return domainActions.isEmpty() ? nextEnrichActions(enrichFlow, deltaFile) : domainActions;
    }

    List<ActionInput> nextDomainActions(EnrichFlow enrichFlow, DeltaFile deltaFile) {
        return enrichFlow.getDomainActions().stream()
                .filter(domainActionConfiguration -> domainActionReady(domainActionConfiguration, deltaFile))
                .filter(domainActionConfiguration -> isNewAction(domainActionConfiguration, deltaFile))
                .map(actionConfiguration -> buildActionInput(actionConfiguration, deltaFile, null))
                .collect(Collectors.toList());
    }

    List<ActionInput> nextEnrichActions(EnrichFlow enrichFlow, DeltaFile deltaFile) {
        return enrichFlow.getEnrichActions().stream()
                .filter(enrichActionConfiguration -> enrichActionReady(enrichActionConfiguration, deltaFile))
                .filter(enrichActionConfiguration -> isNewAction(enrichActionConfiguration, deltaFile))
                .map(actionConfiguration -> buildActionInput(actionConfiguration, deltaFile, null))
                .collect(Collectors.toList());
    }

    List<ActionInput> advanceEgress(EgressFlow egressFlow, DeltaFile deltaFile) {
        return nextEgressActions(egressFlow, deltaFile).stream()
                .filter(actionConfiguration -> isNewAction(actionConfiguration, deltaFile))
                .map(actionConfiguration -> buildActionInput(actionConfiguration, deltaFile, egressFlow.getName()))
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
            IngressFlow ingressFlow = ingressFlowService.getRunningFlowByName(deltaFile.getSourceInfo().getFlow());
            if(egressFlow.isTestMode() || (ingressFlow != null && ingressFlow.isTestMode())) {
                String action = egressFlow.getName() + "." + (egressFlow.isTestMode() ? SYNTHETIC_EGRESS_ACTION_FOR_TEST_EGRESS : SYNTHETIC_EGRESS_ACTION_FOR_TEST_INGRESS);
                deltaFile.queueAction(action);
                deltaFile.completeAction(action, OffsetDateTime.now(), OffsetDateTime.now());
                deltaFile.addEgressFlow(egressFlow.getName());
                if(egressFlow.isTestMode()) {
                    deltaFile.setTestMode("Egress flow '" + egressFlow.getName() + "' in test mode");
                } else {
                    deltaFile.setTestMode("Ingress flow '" + ingressFlow.getName() + "' in test mode");
                }
            } else {
                nextActions.add(egressFlow.getEgressAction());
            }
        }

        return nextActions;
    }

    boolean isNewAction(ActionConfiguration actionConfiguration, DeltaFile deltaFile) {
        return deltaFile.isNewAction(actionConfiguration.getName());
    }

    private boolean domainActionReady(DomainActionConfiguration domainAction, DeltaFile deltaFile) {
        return !deltaFile.hasTerminalAction(domainAction.getName()) &&
                (domainAction.getRequiresDomains() == null || deltaFile.hasDomains(domainAction.getRequiresDomains()));
    }

    private boolean enrichActionReady(EnrichActionConfiguration enrichAction, DeltaFile deltaFile) {
        return !deltaFile.hasTerminalAction(enrichAction.getName()) &&
                (enrichAction.getRequiresDomains() == null || deltaFile.hasDomains(enrichAction.getRequiresDomains())) &&
                (enrichAction.getRequiresEnrichments() == null || deltaFile.hasEnrichments(enrichAction.getRequiresEnrichments())) &&
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
                (config.getRequiresDomains() == null || deltaFile.hasDomains(config.getRequiresDomains())) &&
                (config.getRequiresEnrichments() == null || deltaFile.hasEnrichments(config.getRequiresEnrichments()));
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
                !deltaFile.hasTerminalAction(egressFlow.getEgressAction().getName()) &&
                !deltaFile.hasTerminalAction(egressFlow.getName() + "." + SYNTHETIC_EGRESS_ACTION_FOR_TEST_EGRESS) &&
                !deltaFile.hasTerminalAction(egressFlow.getName() + "." + SYNTHETIC_EGRESS_ACTION_FOR_TEST_INGRESS);
    }

    private ActionInput buildActionInput(ActionConfiguration actionConfiguration, DeltaFile deltaFile, String egressFlow) {
        return actionConfiguration.buildActionInput(deltaFile, deltaFiProperties.getSystemName(), egressFlow);
    }

}
