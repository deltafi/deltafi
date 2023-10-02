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

import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException;
import lombok.AllArgsConstructor;
import org.deltafi.common.converters.KeyValueConverter;
import org.deltafi.common.types.*;
import org.deltafi.core.exceptions.MissingEgressFlowException;
import org.deltafi.core.exceptions.MissingFlowException;
import org.deltafi.core.types.EgressFlow;
import org.deltafi.core.types.EnrichFlow;
import org.deltafi.core.types.NormalizeFlow;
import org.deltafi.core.types.TransformFlow;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;

import static org.deltafi.common.constant.DeltaFiConstants.SYNTHETIC_EGRESS_ACTION_FOR_TEST_EGRESS;
import static org.deltafi.common.constant.DeltaFiConstants.SYNTHETIC_EGRESS_ACTION_FOR_TEST_NORMALIZE;

@Service
@AllArgsConstructor
public class StateMachine {

    private final NormalizeFlowService normalizeFlowService;
    private final EnrichFlowService enrichFlowService;
    private final EgressFlowService egressFlowService;
    private final TransformFlowService transformFlowService;
    private final DeltaFiPropertiesService deltaFiPropertiesService;
    private final IdentityService identityService;
    private final QueueManagementService queueManagementService;

    public List<ActionInput> advance(DeltaFile deltaFile) {
        return advance(deltaFile, false);
    }

    /**
     * Advance the state of the given DeltaFile
     *
     * @param deltaFile The deltaFile to advance
     * @param newDeltaFile Whether this is a new DeltaFile. Used to determine whether routing affinity is needed
     * @return a list of actions that should receive this DeltaFile next
     * @throws MissingEgressFlowException when a DeltaFile advances into the EGRESS stage, but does not have an egress
     * flow configured.
     */
    public List<ActionInput> advance(DeltaFile deltaFile, boolean newDeltaFile) throws MissingEgressFlowException {
        if (deltaFile.getStage() == DeltaFileStage.INGRESS && deltaFile.getSourceInfo().getProcessingType() == null) {
            if (transformFlowService.hasRunningFlow(deltaFile.getSourceInfo().getFlow())) {
                deltaFile.getSourceInfo().setProcessingType(ProcessingType.TRANSFORMATION);
            } else if (normalizeFlowService.hasRunningFlow(deltaFile.getSourceInfo().getFlow())) {
                deltaFile.getSourceInfo().setProcessingType(ProcessingType.NORMALIZATION);
            } else if (transformFlowService.hasFlow(deltaFile.getSourceInfo().getFlow()) ||
                        normalizeFlowService.hasFlow(deltaFile.getSourceInfo().getFlow())) {
                throw new MissingFlowException("Flow is not running: " + deltaFile.getSourceInfo().getFlow());
            } else {
                throw new MissingFlowException("Flow is not installed: " + deltaFile.getSourceInfo().getFlow());
            }
        }

        Action lastAction = deltaFile.lastAction();
        if (lastAction.getState() == ActionState.RETRIED && lastAction.getType() == ActionType.INGRESS) {
            Action toComplete = deltaFile.queueAction(lastAction.getFlow(), lastAction.getName(), ActionType.INGRESS, false);
            toComplete.setContent(lastAction.getContent());
            toComplete.setMetadata(lastAction.getMetadata());
            toComplete.setState(ActionState.COMPLETE);
        }

        List<ActionInput> enqueueActions = switch (deltaFile.getSourceInfo().getProcessingType()) {
            case NORMALIZATION -> advanceNormalization(deltaFile, newDeltaFile);
            case TRANSFORMATION -> advanceTransformation(deltaFile, newDeltaFile);
        };

        if (!deltaFile.hasPendingActions()) {
            deltaFile.setStage(deltaFile.hasErroredAction() ? DeltaFileStage.ERROR : DeltaFileStage.COMPLETE);
        }

        deltaFile.recalculateBytes();

        return enqueueActions;
    }

    private List<ActionInput> advanceNormalization(DeltaFile deltaFile, boolean newDeltaFile) throws MissingEgressFlowException {
        return switch (deltaFile.getStage()) {
            case INGRESS -> advanceIngressStage(deltaFile, newDeltaFile);
            case ENRICH -> advanceEnrichStage(deltaFile, newDeltaFile);
            case EGRESS -> advanceEgressStage(deltaFile, newDeltaFile);
            default -> new ArrayList<>();
        };
    }

    private List<ActionInput> advanceTransformation(DeltaFile deltaFile, boolean newDeltaFile) {
        if (deltaFile.hasErroredAction() || deltaFile.hasFilteredAction() || deltaFile.hasReinjectedAction()) {
            return Collections.emptyList();
        }

        TransformFlow transformFlow = transformFlowService.getRunningFlowByName(deltaFile.getSourceInfo().getFlow());
        TransformActionConfiguration nextTransformAction = transformFlow.getTransformActions().stream()
                .filter(transformAction -> !deltaFile.hasTerminalAction(transformFlow.getName(), transformAction.getName()))
                .findFirst().orElse(null);
        if (nextTransformAction != null) {
            Action action = deltaFile.queueAction(transformFlow.getName(), nextTransformAction.getName(), ActionType.TRANSFORM, queueManagementService.coldQueue(nextTransformAction.getType()));
            return List.of(buildActionInput(transformFlow.getName(), nextTransformAction, deltaFile, null, newDeltaFile, action));
        }

        deltaFile.setStage(DeltaFileStage.EGRESS);

        if (transformFlow.isTestMode()) {
            deltaFile.queueAction(transformFlow.getName(), SYNTHETIC_EGRESS_ACTION_FOR_TEST_EGRESS, ActionType.EGRESS, false);
            deltaFile.completeAction(transformFlow.getName(), SYNTHETIC_EGRESS_ACTION_FOR_TEST_EGRESS, OffsetDateTime.now(), OffsetDateTime.now());
            deltaFile.addEgressFlow(transformFlow.getName());
            deltaFile.setTestModeReason("Transform flow '" + transformFlow.getName() + "' in test mode");
            return Collections.emptyList();
        }

        EgressActionConfiguration egressAction = transformFlow.getEgressAction();
        if (deltaFile.hasTerminalAction(transformFlow.getName(), egressAction.getName())) {
            return Collections.emptyList();
        }

        Action action = deltaFile.queueAction(transformFlow.getName(), egressAction.getName(), ActionType.EGRESS, queueManagementService.coldQueue(egressAction.getType()));
        deltaFile.addEgressFlow(transformFlow.getName());
        return List.of(buildActionInput(transformFlow.getName(), egressAction, deltaFile, transformFlow.getName(), newDeltaFile, action));
    }

    private List<ActionInput> advanceIngressStage(DeltaFile deltaFile, boolean newDeltaFile) {
        if (deltaFile.hasErroredAction() || deltaFile.hasFilteredAction() || deltaFile.hasReinjectedAction()) {
            return Collections.emptyList();
        }

        NormalizeFlow normalizeFlow = normalizeFlowService.getRunningFlowByName(deltaFile.getSourceInfo().getFlow());

        TransformActionConfiguration nextTransformAction = normalizeFlow.getTransformActions().stream()
                .filter(transformAction -> !deltaFile.hasTerminalAction(normalizeFlow.getName(), transformAction.getName()))
                .findFirst().orElse(null);
        if (nextTransformAction != null) {
            Action action = deltaFile.queueAction(normalizeFlow.getName(), nextTransformAction.getName(), ActionType.TRANSFORM, queueManagementService.coldQueue(nextTransformAction.getType()));
            return List.of(buildActionInput(normalizeFlow.getName(), nextTransformAction, deltaFile, null, newDeltaFile, action));
        }

        LoadActionConfiguration loadAction = normalizeFlow.getLoadAction();
        if ((loadAction != null) && !deltaFile.hasTerminalAction(normalizeFlow.getName(), loadAction.getName())) {
            Action action = deltaFile.queueAction(normalizeFlow.getName(), loadAction.getName(), ActionType.LOAD, queueManagementService.coldQueue(loadAction.getType()));
            return List.of(buildActionInput(normalizeFlow.getName(), loadAction, deltaFile, null, newDeltaFile, action));
        }

        deltaFile.setStage(DeltaFileStage.ENRICH);

        return advanceEnrichStage(deltaFile, newDeltaFile);
    }

    private ActionInput buildActionInput(String flow, ActionConfiguration actionConfiguration, DeltaFile deltaFile,
            String egressFlow, boolean newDeltaFile, Action action) {
        String returnAddress = !newDeltaFile &&
                deltaFiPropertiesService.getDeltaFiProperties().getDeltaFileCache().isEnabled() ?
                identityService.getUniqueId() : null;
        return actionConfiguration.buildActionInput(flow, deltaFile,
                deltaFiPropertiesService.getDeltaFiProperties().getSystemName(), egressFlow, returnAddress, OffsetDateTime.now(), action);
    }

    private List<ActionInput> advanceEnrichStage(DeltaFile deltaFile, boolean newDeltaFile) {
        if (deltaFile.hasErroredAction()) {
            return Collections.emptyList();
        }

        List<EnrichFlow> allEnrichFlows = enrichFlowService.getRunningFlows();

        List<Pair<String, DomainActionConfiguration>> domainActionConfigurations = allEnrichFlows.stream()
                .map(enrichFlow -> nextDomainActions(enrichFlow, deltaFile).stream()
                        .map(domainActionConfiguration -> Pair.of(enrichFlow.getName(), domainActionConfiguration))
                        .toList())
                .flatMap(Collection::stream)
                .toList();

        if (!domainActionConfigurations.isEmpty()) {
            return domainActionConfigurations.stream()
                    .map(flowConfigPair -> {
                        Action action = deltaFile.queueNewAction(flowConfigPair.getFirst(), flowConfigPair.getSecond().getName(), ActionType.DOMAIN, queueManagementService.coldQueue(flowConfigPair.getSecond().getType()));
                        return buildActionInput(flowConfigPair.getFirst(), flowConfigPair.getSecond(), deltaFile, null, newDeltaFile, action);
                    }).toList();
        }

        List<Pair<String, EnrichActionConfiguration>> enrichActionConfigurations = allEnrichFlows.stream()
                .map(enrichFlow -> nextEnrichActions(enrichFlow, deltaFile).stream()
                        .map(enrichActionConfiguration -> Pair.of(enrichFlow.getName(), enrichActionConfiguration))
                        .toList())
                .flatMap(Collection::stream)
                .toList();

        if (!enrichActionConfigurations.isEmpty()) {
            return enrichActionConfigurations.stream()
                    .map(flowConfigPair -> {
                        Action action = deltaFile.queueNewAction(flowConfigPair.getFirst(), flowConfigPair.getSecond().getName(), ActionType.ENRICH, queueManagementService.coldQueue(flowConfigPair.getSecond().getType()));
                        return buildActionInput(flowConfigPair.getFirst(), flowConfigPair.getSecond(), deltaFile, null, newDeltaFile, action);
                    }).toList();
        }

        if (!deltaFile.hasPendingActions() && !deltaFile.hasErroredAction()) {
            deltaFile.setStage(DeltaFileStage.EGRESS);
            return advanceEgressStage(deltaFile, newDeltaFile);
        }

        return List.of();
    }

    public List<DomainActionConfiguration> nextDomainActions(EnrichFlow enrichFlow, DeltaFile deltaFile) {
        return enrichFlow.getDomainActions().stream()
                .filter(domainActionConfiguration -> domainActionReady(enrichFlow, domainActionConfiguration, deltaFile))
                .filter(domainActionConfiguration -> deltaFile.isNewAction(enrichFlow.getName(), domainActionConfiguration.getName()))
                .toList();
    }

    private boolean domainActionReady(EnrichFlow enrichFlow, DomainActionConfiguration domainAction, DeltaFile deltaFile) {
        return !deltaFile.hasTerminalAction(enrichFlow.getName(), domainAction.getName()) &&
                (domainAction.getRequiresDomains() == null || deltaFile.hasDomains(domainAction.getRequiresDomains()));
    }

    public List<EnrichActionConfiguration> nextEnrichActions(EnrichFlow enrichFlow, DeltaFile deltaFile) {
        return enrichFlow.getEnrichActions().stream()
                .filter(enrichActionConfiguration -> enrichActionReady(enrichFlow, enrichActionConfiguration, deltaFile))
                .filter(enrichActionConfiguration -> deltaFile.isNewAction(enrichFlow.getName(), enrichActionConfiguration.getName()))
                .toList();
    }

    private boolean enrichActionReady(EnrichFlow enrichFlow, EnrichActionConfiguration enrichAction, DeltaFile deltaFile) {
        return !deltaFile.hasTerminalAction(enrichFlow.getName(), enrichAction.getName()) &&
                (enrichAction.getRequiresDomains() == null || deltaFile.hasDomains(enrichAction.getRequiresDomains())) &&
                (enrichAction.getRequiresEnrichments() == null || deltaFile.hasEnrichments(enrichAction.getRequiresEnrichments())) &&
                hasMetadataMatches(deltaFile, enrichAction);
    }

    private boolean hasMetadataMatches(DeltaFile deltaFile, EnrichActionConfiguration config) {
        Map<String, String> requiresMetadata = KeyValueConverter.convertKeyValues(config.getRequiresMetadataKeyValues());
        if (requiresMetadata.isEmpty()) {
            return true;
        }

        if (matchesAllMetadata(requiresMetadata, deltaFile.lastCompleteDataAmendedAction().getMetadata())) {
            return true;
        }

        if (deltaFile.getSourceInfo() != null) {
            return matchesAllMetadata(requiresMetadata, deltaFile.getSourceInfo().getMetadata());
        }

        return false;
    }

    private boolean matchesAllMetadata(Map<String, String> requiresMetadata, Map<String, String> metadataMap) {
        return requiresMetadata.keySet().stream().allMatch(k -> requiresMetadata.get(k).equals(metadataMap.get(k)));
    }

    private List<ActionInput> advanceEgressStage(DeltaFile deltaFile, boolean newDeltaFile) {
        List<? extends Pair<String, ? extends ActionConfiguration>> egressActionConfigurations = egressFlowService.getMatchingFlows(deltaFile.getSourceInfo().getFlow()).stream()
                .map(egressFlow -> nextEgressFlowActions(egressFlow, deltaFile))
                .flatMap(Collection::stream)
                .toList();

        if (deltaFile.getEgress().isEmpty() && egressActionConfigurations.isEmpty()) {
            throw new MissingEgressFlowException(deltaFile.getDid());
        }

        return egressActionConfigurations.stream()
                .map(flowConfigPair -> {
                    Action action = deltaFile.queueNewAction(flowConfigPair.getFirst(), flowConfigPair.getSecond().getName(), flowConfigPair.getSecond().getActionType(), queueManagementService.coldQueue(flowConfigPair.getSecond().getType()));
                    deltaFile.addEgressFlow(flowConfigPair.getFirst());
                    return buildActionInput(flowConfigPair.getFirst(), flowConfigPair.getSecond(), deltaFile, flowConfigPair.getFirst(), newDeltaFile, action);
                })
                .toList();
    }

    List<? extends Pair<String, ? extends ActionConfiguration>> nextEgressFlowActions(EgressFlow egressFlow, DeltaFile deltaFile) {
        return nextEgressFlowActionConfigurations(egressFlow, deltaFile).stream()
                .filter(actionConfiguration -> deltaFile.isNewAction(egressFlow.getName(), actionConfiguration.getName()))
                .map(actionConfiguration -> Pair.of(egressFlow.getName(), actionConfiguration))
                .toList();
    }

    private List<? extends ActionConfiguration> nextEgressFlowActionConfigurations(EgressFlow egressFlow,
            DeltaFile deltaFile) {
        if (formatActionReady(egressFlow, egressFlow.getFormatAction(), deltaFile)) {
            return List.of(egressFlow.getFormatAction());
        }

        List<? extends ActionConfiguration> validateActions = nextValidateActions(egressFlow, deltaFile);
        if (!validateActions.isEmpty()) {
            return validateActions;
        }

        if (!egressActionReady(egressFlow, deltaFile)) {
            return Collections.emptyList();
        }

        NormalizeFlow normalizeFlow = null;
        try {
            normalizeFlow = normalizeFlowService.getFlowOrThrow(deltaFile.getSourceInfo().getFlow());
        } catch (DgsEntityNotFoundException ignored) {
            // if the ingress flow cannot be found, keep it set to null and assume it was not in test mode
        }
        if (egressFlow.isTestMode() || (normalizeFlow != null && normalizeFlow.isTestMode())) {
            String action = egressFlow.isTestMode() ? SYNTHETIC_EGRESS_ACTION_FOR_TEST_EGRESS : SYNTHETIC_EGRESS_ACTION_FOR_TEST_NORMALIZE;
            deltaFile.queueAction(egressFlow.getName(), action, ActionType.EGRESS, false);
            deltaFile.completeAction(egressFlow.getName(), action, OffsetDateTime.now(), OffsetDateTime.now());
            deltaFile.addEgressFlow(egressFlow.getName());
            if (normalizeFlow != null && normalizeFlow.isTestMode()) {
                deltaFile.setTestModeReason("Normalize flow '" + normalizeFlow.getName() + "' in test mode");
            } else {
                deltaFile.setTestModeReason("Egress flow '" + egressFlow.getName() + "' in test mode");
            }
        } else {
            return List.of(egressFlow.getEgressAction());
        }

        return Collections.emptyList();
    }

    private boolean formatActionReady(EgressFlow egressFlow, FormatActionConfiguration config, DeltaFile deltaFile) {
        return !deltaFile.hasTerminalAction(egressFlow.getName(), config.getName()) &&
                (config.getRequiresDomains() == null || deltaFile.hasDomains(config.getRequiresDomains())) &&
                (config.getRequiresEnrichments() == null || deltaFile.hasEnrichments(config.getRequiresEnrichments()));
    }

    private List<? extends ActionConfiguration> nextValidateActions(EgressFlow egressFlow, DeltaFile deltaFile) {
        if (Objects.isNull(egressFlow.getValidateActions()) || !validateActionsReady(egressFlow, deltaFile)) {
            return Collections.emptyList();
        }

        return egressFlow.getValidateActions().stream()
                .filter(validate -> !deltaFile.hasTerminalAction(egressFlow.getName(), validate.getName()))
                .toList();
    }

    private boolean validateActionsReady(EgressFlow egressFlow, DeltaFile deltaFile) {
        return deltaFile.hasCompletedAction(egressFlow.getName(), egressFlow.getFormatAction().getName());
    }

    private boolean egressActionReady(EgressFlow egressFlow, DeltaFile deltaFile) {
        return deltaFile.hasCompletedAction(egressFlow.getName(), egressFlow.getFormatAction().getName()) &&
                deltaFile.hasCompletedActions(egressFlow.getName(), egressFlow.validateActionNames()) &&
                !deltaFile.hasTerminalAction(egressFlow.getName(), egressFlow.getEgressAction().getName()) &&
                !deltaFile.hasTerminalAction(egressFlow.getName(), SYNTHETIC_EGRESS_ACTION_FOR_TEST_EGRESS) &&
                !deltaFile.hasTerminalAction(egressFlow.getName(), SYNTHETIC_EGRESS_ACTION_FOR_TEST_NORMALIZE);
    }
}
