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
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.converters.KeyValueConverter;
import org.deltafi.common.types.*;
import org.deltafi.core.collect.*;
import org.deltafi.core.exceptions.MissingEgressFlowException;
import org.deltafi.core.exceptions.MissingFlowException;
import org.deltafi.common.types.Subscriber;
import org.deltafi.core.types.*;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.*;

import static org.deltafi.common.constant.DeltaFiConstants.SYNTHETIC_EGRESS_ACTION_FOR_TEST_EGRESS;
import static org.deltafi.common.constant.DeltaFiConstants.SYNTHETIC_EGRESS_ACTION_FOR_TEST_NORMALIZE;

@Service
@AllArgsConstructor
@Slf4j
public class StateMachine {
    private final Clock clock;
    private final TransformFlowService transformFlowService;
    private final NormalizeFlowService normalizeFlowService;
    private final EnrichFlowService enrichFlowService;
    private final EgressFlowService egressFlowService;
    private final DeltaFiPropertiesService deltaFiPropertiesService;
    private final IdentityService identityService;
    private final QueueManagementService queueManagementService;
    private final CollectEntryService collectEntryService;
    private final ScheduledCollectService scheduledCollectService;

    List<ActionInvocation> advance(DeltaFile deltaFile) {
        return advance(deltaFile, false, new HashMap<>());
    }

    /**
     * Advance the state of the given DeltaFile
     *
     * @param deltaFile The deltaFile to advance
     * @param newDeltaFile Whether this is a new DeltaFile. Used to determine whether routing affinity is needed
     * @param pendingQueued A map of queue names to number of times to be queued so far. This object is mutated to include new entries
     * @return a list of actions that should receive this DeltaFile next
     * @throws MissingEgressFlowException when a DeltaFile advances into the EGRESS stage, but does not have an egress
     * flow configured.
     */
    List<ActionInvocation> advance(DeltaFile deltaFile, boolean newDeltaFile, Map<String, Long> pendingQueued) throws MissingEgressFlowException {
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

        List<ActionInvocation> actionInvocations = switch (deltaFile.getSourceInfo().getProcessingType()) {
            case NORMALIZATION -> advanceNormalization(deltaFile, newDeltaFile, pendingQueued);
            case TRANSFORMATION -> advanceTransformation(deltaFile, newDeltaFile, pendingQueued);
        };

        if (!deltaFile.hasPendingActions()) {
            deltaFile.setStage(deltaFile.hasErroredAction() ? DeltaFileStage.ERROR : DeltaFileStage.COMPLETE);
        }

        deltaFile.recalculateBytes();

        return actionInvocations;
    }

    private List<ActionInvocation> advanceNormalization(DeltaFile deltaFile, boolean newDeltaFile, Map<String, Long> pendingQueued) throws MissingEgressFlowException {
        return switch (deltaFile.getStage()) {
            case INGRESS -> advanceIngressStage(deltaFile, newDeltaFile, pendingQueued);
            case ENRICH -> advanceEnrichStage(deltaFile, newDeltaFile, pendingQueued);
            case EGRESS -> advanceEgressStage(deltaFile, newDeltaFile, pendingQueued);
            default -> new ArrayList<>();
        };
    }

    public List<ActionInvocation> advanceSubscriber(Subscriber subscriber, DeltaFile deltaFile, boolean newDeltaFile) {
        if (subscriber instanceof TransformFlow transformFlow) {
            if (deltaFile.getSourceInfo().getProcessingType() == null) {
                deltaFile.getSourceInfo().setProcessingType(ProcessingType.TRANSFORMATION);
            }

            List<ActionInvocation> enqueueActions = advanceTransformation(transformFlow, deltaFile, newDeltaFile, new HashMap<>());
            if (!deltaFile.hasPendingActions()) {
                deltaFile.setStage(deltaFile.hasErroredAction() ? DeltaFileStage.ERROR : DeltaFileStage.COMPLETE);
            }

            deltaFile.recalculateBytes();

            return enqueueActions;
        }

        throw new IllegalArgumentException("Unexpected subscriber type " + subscriber.getClass().getSimpleName());
    }

    List<ActionInvocation> advanceTransformation(TransformFlow transformFlow, DeltaFile deltaFile, boolean newDeltaFile, Map<String, Long> pendingQueued) {
        if (skipTransform(deltaFile)) {
            return Collections.emptyList();
        }

        return internalAdvanceTransformation(transformFlow, deltaFile, newDeltaFile, pendingQueued);
    }

    private List<ActionInvocation> advanceTransformation(DeltaFile deltaFile, boolean newDeltaFile, Map<String, Long> pendingQueued) {
        if (skipTransform(deltaFile)) {
            return Collections.emptyList();
        }

        TransformFlow transformFlow = transformFlowService.getRunningFlowByName(deltaFile.getSourceInfo().getFlow());
        return internalAdvanceTransformation(transformFlow, deltaFile, newDeltaFile, pendingQueued);
    }

    private boolean skipTransform(DeltaFile deltaFile) {
        return deltaFile.hasErroredAction() || deltaFile.hasFilteredAction() || deltaFile.hasReinjectedAction();
    }

    private List<ActionInvocation> internalAdvanceTransformation(TransformFlow transformFlow, DeltaFile deltaFile, boolean newDeltaFile, Map<String, Long> pendingQueued) {
        if (transformFlow.getTransformActions().stream().anyMatch(transformActionConfiguration ->
                deltaFile.hasCollectedAction(transformFlow.getName(), transformActionConfiguration.getName()))) {
            deltaFile.setStage(DeltaFileStage.COMPLETE);
            return Collections.emptyList();
        }
        TransformActionConfiguration nextTransformAction = transformFlow.getTransformActions().stream()
                .filter(transformAction -> !deltaFile.hasTerminalAction(transformFlow.getName(), transformAction.getName()))
                .findFirst().orElse(null);
        if (nextTransformAction != null) {
            return buildActionInvocation(transformFlow, nextTransformAction, deltaFile, newDeltaFile, pendingQueued)
                    .map(List::of).orElse(Collections.emptyList());
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

        Action action = deltaFile.queueAction(transformFlow.getName(), egressAction.getName(), ActionType.EGRESS,
                coldQueue(egressAction.getType(), pendingQueued));
        deltaFile.addEgressFlow(transformFlow.getName());
        return buildActionInvocation(transformFlow.getName(), egressAction, deltaFile, transformFlow.getName(), newDeltaFile, action)
                .map(List::of).orElse(Collections.emptyList());
    }

    private List<ActionInvocation> advanceIngressStage(DeltaFile deltaFile, boolean newDeltaFile, Map<String, Long> pendingQueued) {
        if (deltaFile.hasErroredAction() || deltaFile.hasFilteredAction() || deltaFile.hasReinjectedAction()) {
            return Collections.emptyList();
        }

        NormalizeFlow normalizeFlow = normalizeFlowService.getRunningFlowByName(deltaFile.getSourceInfo().getFlow());

        if (normalizeFlow.getTransformActions().stream().anyMatch(transformActionConfiguration ->
                deltaFile.hasCollectedAction(normalizeFlow.getName(), transformActionConfiguration.getName()))) {
            deltaFile.setStage(DeltaFileStage.COMPLETE);
            return Collections.emptyList();
        }
        TransformActionConfiguration nextTransformAction = normalizeFlow.getTransformActions().stream()
                .filter(transformAction -> !deltaFile.hasTerminalAction(normalizeFlow.getName(), transformAction.getName()))
                .findFirst().orElse(null);
        if (nextTransformAction != null) {
            return buildActionInvocation(normalizeFlow, nextTransformAction, deltaFile, newDeltaFile, pendingQueued)
                    .map(List::of).orElse(Collections.emptyList());
        }

        LoadActionConfiguration loadAction = normalizeFlow.getLoadAction();
        if ((loadAction != null) && deltaFile.hasCollectedAction(normalizeFlow.getName(), loadAction.getName())) {
            deltaFile.setStage(DeltaFileStage.COMPLETE);
            return Collections.emptyList();
        }
        if ((loadAction != null) && !deltaFile.hasTerminalAction(normalizeFlow.getName(), loadAction.getName())) {
            return buildActionInvocation(normalizeFlow, loadAction, deltaFile, newDeltaFile, pendingQueued)
                    .map(List::of).orElse(Collections.emptyList());
        }

        deltaFile.setStage(DeltaFileStage.ENRICH);

        return advanceEnrichStage(deltaFile, newDeltaFile, pendingQueued);
    }

    private List<ActionInvocation> advanceEnrichStage(DeltaFile deltaFile, boolean newDeltaFile, Map<String, Long> pendingQueued) {
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
                        Action action = deltaFile.queueNewAction(flowConfigPair.getFirst(),
                                flowConfigPair.getSecond().getName(), ActionType.DOMAIN,
                                coldQueue(flowConfigPair.getSecond().getType(), pendingQueued));
                        return buildActionInvocation(flowConfigPair.getFirst(), flowConfigPair.getSecond(), deltaFile,
                                null, newDeltaFile, action).orElse(null);
                    })
                    .filter(Objects::nonNull)
                    .toList();
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
                        Action action = deltaFile.queueNewAction(flowConfigPair.getFirst(),
                                flowConfigPair.getSecond().getName(), ActionType.ENRICH,
                                coldQueue(flowConfigPair.getSecond().getType(), pendingQueued));
                        return buildActionInvocation(flowConfigPair.getFirst(), flowConfigPair.getSecond(), deltaFile,
                                null, newDeltaFile, action).orElse(null);
                    })
                    .filter(Objects::nonNull)
                    .toList();
        }

        if (!deltaFile.hasPendingActions() && !deltaFile.hasErroredAction()) {
            deltaFile.setStage(DeltaFileStage.EGRESS);
            return advanceEgressStage(deltaFile, newDeltaFile, pendingQueued);
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

    private List<ActionInvocation> advanceEgressStage(DeltaFile deltaFile, boolean newDeltaFile, Map<String, Long> pendingQueued) {
        // Collecting format actions cannot occur in parallel with other egress flow actions because DeltaFiles could
        // become stale in the cache before the collection is complete. Add the first collecting format action that is
        // ready (if one exists) to the DeltaFile.
        if (!deltaFile.isAggregate()) {
            for (EgressFlow egressFlow : egressFlowService.getMatchingFlows(deltaFile.getSourceInfo().getFlow())) {
                if ((egressFlow.getFormatAction().getCollect() != null) &&
                        formatActionReady(egressFlow, egressFlow.getFormatAction(), deltaFile) &&
                        deltaFile.isNewAction(egressFlow.getName(), egressFlow.getFormatAction().getName())) {
                    try {
                        Optional<ActionInvocation> actionInvocation = collect(egressFlow.getName(),
                                egressFlow.getFormatAction(), deltaFile, coldQueue(egressFlow.getFormatAction().getType(), pendingQueued) ?
                                        ActionState.COLD_QUEUED : ActionState.QUEUED);
                        deltaFile.addAction(egressFlow.getName(), egressFlow.getFormatAction().getName(),
                                egressFlow.getFormatAction().getActionType(), ActionState.COLLECTING);
                        return actionInvocation.map(List::of).orElse(Collections.emptyList());
                    } catch (CollectException e) {
                        deltaFile.setStage(DeltaFileStage.ERROR);
                        return Collections.emptyList();
                    }
                }
            }
        }

        List<Pair<String, ? extends ActionConfiguration>> egressActionConfigurations =
                egressFlowService.getMatchingFlows(deltaFile.getSourceInfo().getFlow()).stream()
                        .map(egressFlow -> nextEgressFlowActions(egressFlow, deltaFile))
                        .flatMap(Collection::stream)
                        .toList();

        if (deltaFile.getEgress().isEmpty() && egressActionConfigurations.isEmpty() &&
                !deltaFile.hasActionInState(ActionState.COLLECTING) &&
                !deltaFile.hasActionInState(ActionState.COLLECTED)) {
            throw new MissingEgressFlowException(deltaFile.getDid());
        }

        return egressActionConfigurations.stream()
                .map(flowConfigPair -> {
                    Action action = deltaFile.queueNewAction(flowConfigPair.getFirst(),
                            flowConfigPair.getSecond().getName(), flowConfigPair.getSecond().getActionType(),
                            coldQueue(flowConfigPair.getSecond().getType(), pendingQueued));
                    deltaFile.addEgressFlow(flowConfigPair.getFirst());
                    return buildActionInvocation(flowConfigPair.getFirst(), flowConfigPair.getSecond(), deltaFile,
                            flowConfigPair.getFirst(), newDeltaFile, action).orElse(null);
                })
                .filter(Objects::nonNull)
                .toList();
    }

    List<Pair<String, ? extends ActionConfiguration>> nextEgressFlowActions(EgressFlow egressFlow, DeltaFile deltaFile) {
        List<Pair<String, ? extends ActionConfiguration>> egressActions = new ArrayList<>();
        for (ActionConfiguration actionConfiguration : nextEgressFlowActionConfigurations(egressFlow, deltaFile)) {
            if (!deltaFile.isNewAction(egressFlow.getName(), actionConfiguration.getName())) {
                continue;
            }
            egressActions.add(Pair.of(egressFlow.getName(), actionConfiguration));
        }
        return egressActions;
    }

    private List<? extends ActionConfiguration> nextEgressFlowActionConfigurations(EgressFlow egressFlow,
            DeltaFile deltaFile) {
        if (formatActionReady(egressFlow, egressFlow.getFormatAction(), deltaFile)) {
            return List.of(egressFlow.getFormatAction());
        }

        if (deltaFile.hasCollectedAction(egressFlow.getName(), egressFlow.getFormatAction().getName())) {
            return Collections.emptyList();
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

    private Optional<ActionInvocation> buildActionInvocation(Flow flow, ActionConfiguration actionConfiguration,
            DeltaFile deltaFile, boolean newDeltaFile, Map<String, Long> pendingQueued) {
        if (actionConfiguration.getCollect() == null) {
            Action action = deltaFile.queueAction(flow.getName(), actionConfiguration.getName(),
                    actionConfiguration.getActionType(), coldQueue(actionConfiguration.getType(), pendingQueued));
            return buildActionInvocation(flow.getName(), actionConfiguration, deltaFile, null, newDeltaFile, action);
        }

        if (deltaFile.isAggregate()) {
            // Resuming a failed collect action in an aggregate
            Action action = deltaFile.queueAction(flow.getName(), actionConfiguration.getName(),
                    actionConfiguration.getActionType(), coldQueue(actionConfiguration.getType(), pendingQueued));
            return Optional.of(CollectingActionInvocation.builder()
                    .actionConfiguration(actionConfiguration)
                    .flow(flow.getName())
                    .deltaFile(deltaFile)
                    .egressFlow(deltaFile.getStage() == DeltaFileStage.EGRESS ? flow.getName() : null)
                    .actionCreated(action.getCreated())
                    .action(action)
                    .collectedDids(deltaFile.getParentDids())
                    .processingType(deltaFile.getSourceInfo().getProcessingType())
                    .stage(deltaFile.getStage())
                    .build());
        }

        try {
            Optional<ActionInvocation> actionInvocation = collect(flow.getName(), actionConfiguration, deltaFile,
                    coldQueue(actionConfiguration.getType(), pendingQueued) ? ActionState.COLD_QUEUED : ActionState.QUEUED);
            deltaFile.addAction(flow.getName(), actionConfiguration.getName(), actionConfiguration.getActionType(),
                    ActionState.COLLECTING);
            return actionInvocation;
        } catch (CollectException e) {
            deltaFile.setStage(DeltaFileStage.ERROR);
            return Optional.empty();
        }
    }

    private Optional<ActionInvocation> buildActionInvocation(String flow, ActionConfiguration actionConfiguration,
            DeltaFile deltaFile, String egressFlow, boolean newDeltaFile, Action action) {
        String returnAddress = !newDeltaFile &&
                deltaFiPropertiesService.getDeltaFiProperties().getDeltaFileCache().isEnabled() ?
                identityService.getUniqueId() : null;
        return Optional.of(ActionInvocation.builder()
                .actionConfiguration(actionConfiguration)
                .flow(flow)
                .deltaFile(deltaFile)
                .egressFlow(egressFlow)
                .returnAddress(returnAddress)
                .actionCreated(OffsetDateTime.now())
                .action(action)
                .build());
    }

    private Optional<ActionInvocation> collect(String flow, ActionConfiguration actionConfiguration, DeltaFile deltaFile,
            ActionState actionState) throws CollectException {
        String collectGroup = actionConfiguration.getCollect().metadataKey() == null ? "DEFAULT" :
                deltaFile.getMetadata().getOrDefault(actionConfiguration.getCollect().metadataKey(), "DEFAULT");

        CollectDefinition collectDefinition = new CollectDefinition(deltaFile.getSourceInfo().getProcessingType(),
                deltaFile.getStage(), flow, actionConfiguration.getActionType(), actionConfiguration.getName(), collectGroup);

        CollectEntry collectEntry = collectEntryService.upsertAndLock(collectDefinition,
                OffsetDateTime.now(clock).plus(actionConfiguration.getCollect().maxAge()),
                actionConfiguration.getCollect().minNum(), actionConfiguration.getCollect().maxNum(), deltaFile.getDid());

        if (collectEntry == null) {
            throw new CollectException("Timed out trying to lock collect entry");
        }

        if (collectEntry.getCount() < actionConfiguration.getCollect().maxNum()) {
            if (collectEntry.getCount() == 1) { // Only update collect check for new collect entries
                scheduledCollectService.updateCollectCheck(collectEntry.getCollectDate());
            }
            collectEntryService.unlock(collectEntry.getId());
            return Optional.empty();
        }

        ActionInvocation actionInvocation = buildCollectingActionInvocation(collectEntry,
                collectEntryService.findCollectedDids(collectEntry.getId()), actionConfiguration, actionState);

        collectEntryService.delete(collectEntry.getId());
        scheduledCollectService.scheduleNextCollectCheck();

        return Optional.of(actionInvocation);
    }

    private CollectingActionInvocation buildCollectingActionInvocation(CollectEntry collectEntry,
            List<String> collectedDids, ActionConfiguration actionConfiguration, ActionState actionState) {
        OffsetDateTime now = OffsetDateTime.now(clock);

        Action action = Action.builder()
                .name(collectEntry.getCollectDefinition().getAction())
                .type(collectEntry.getCollectDefinition().getActionType())
                .flow(collectEntry.getCollectDefinition().getFlow())
                .state(actionState)
                .created(now)
                .queued(now)
                .modified(now)
                .build();

        return CollectingActionInvocation.builder()
                .actionConfiguration(actionConfiguration)
                .flow(collectEntry.getCollectDefinition().getFlow())
                .egressFlow(collectEntry.getCollectDefinition().getStage() == DeltaFileStage.EGRESS ?
                        collectEntry.getCollectDefinition().getFlow() : null)
                .actionCreated(now)
                .action(action)
                .collectedDids(collectedDids)
                .processingType(collectEntry.getCollectDefinition().getProcessingType())
                .stage(collectEntry.getCollectDefinition().getStage())
                .build();
    }

    private boolean coldQueue(String queueName, Map<String, Long> pendingQueued) {
        Long newVal = pendingQueued.put(queueName, pendingQueued.getOrDefault(queueName, 0L) + 1);
        return queueManagementService.coldQueue(queueName, newVal == null ? 0L : newVal);
    }
}
