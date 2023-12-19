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
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.types.*;
import org.deltafi.core.collect.*;
import org.deltafi.core.exceptions.MissingEgressFlowException;
import org.deltafi.common.types.Subscriber;
import org.deltafi.core.types.*;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.*;

import static org.deltafi.common.constant.DeltaFiConstants.SYNTHETIC_EGRESS_ACTION_FOR_TEST;

@Service
@AllArgsConstructor
@Slf4j
public class StateMachine {
    private final Clock clock;
    private final TransformFlowService transformFlowService;
    private final EgressFlowService egressFlowService;
    private final DeltaFiPropertiesService deltaFiPropertiesService;
    private final IdentityService identityService;
    private final QueueManagementService queueManagementService;
    private final CollectEntryService collectEntryService;
    private final ScheduledCollectService scheduledCollectService;

    // TODO: detect flows that don't have a followon flow

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
        Action lastAction = deltaFile.lastAction();
        if (lastAction.getState() == ActionState.RETRIED && lastAction.getType() == ActionType.INGRESS) {
            Action toComplete = deltaFile.queueAction(lastAction.getFlow(), lastAction.getName(), ActionType.INGRESS, false);
            toComplete.setContent(lastAction.getContent());
            toComplete.setMetadata(lastAction.getMetadata());
            toComplete.setState(ActionState.COMPLETE);
        }

        List<ActionInvocation> actionInvocations = advanceTransformation(deltaFile, newDeltaFile, pendingQueued);

        if (!deltaFile.hasPendingActions()) {
            deltaFile.setStage(deltaFile.hasErroredAction() ? DeltaFileStage.ERROR : DeltaFileStage.COMPLETE);
        }

        deltaFile.recalculateBytes();

        return actionInvocations;
    }

    public List<ActionInvocation> advanceSubscriber(Subscriber subscriber, DeltaFile deltaFile, boolean newDeltaFile) {
        if (subscriber instanceof TransformFlow transformFlow) {
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
        return deltaFile.hasErroredAction() || deltaFile.hasFilteredAction();
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

        if (transformFlow.isTestMode()) {
            deltaFile.queueAction(transformFlow.getName(), SYNTHETIC_EGRESS_ACTION_FOR_TEST, ActionType.EGRESS, false);
            deltaFile.completeAction(transformFlow.getName(), SYNTHETIC_EGRESS_ACTION_FOR_TEST, OffsetDateTime.now(), OffsetDateTime.now(), deltaFile.lastCompleteAction().getContent(), deltaFile.getMetadata(), List.of());
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
        if (egressFlow.isTestMode()) {
            // TODO: test mode should be passed from flow to flow in the DeltaFile
            deltaFile.queueAction(egressFlow.getName(), SYNTHETIC_EGRESS_ACTION_FOR_TEST, ActionType.EGRESS, false);
            deltaFile.completeAction(egressFlow.getName(), SYNTHETIC_EGRESS_ACTION_FOR_TEST, OffsetDateTime.now(), OffsetDateTime.now());
            deltaFile.addEgressFlow(egressFlow.getName());
            deltaFile.setTestModeReason("Egress flow '" + egressFlow.getName() + "' in test mode");
        } else {
            return List.of(egressFlow.getEgressAction());
        }

        return Collections.emptyList();
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
                    // TODO: figure out what to do with the egress flow list after we have proper egress again
                    //.egressFlow(deltaFile.getStage() == DeltaFileStage.EGRESS ? flow.getName() : null)
                    .actionCreated(action.getCreated())
                    .action(action)
                    .collectedDids(deltaFile.getParentDids())
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

        CollectDefinition collectDefinition = new CollectDefinition(deltaFile.getStage(), flow,
                actionConfiguration.getActionType(), actionConfiguration.getName(), collectGroup);

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
                // TODO: figure out what to do with the egress flow list after we have proper egress again
                //.egressFlow(collectEntry.getCollectDefinition().getStage() == DeltaFileStage.EGRESS ?
                //        collectEntry.getCollectDefinition().getFlow() : null)
                .actionCreated(now)
                .action(action)
                .collectedDids(collectedDids)
                .stage(collectEntry.getCollectDefinition().getStage())
                .build();
    }

    private boolean coldQueue(String queueName, Map<String, Long> pendingQueued) {
        Long newVal = pendingQueued.put(queueName, pendingQueued.getOrDefault(queueName, 0L) + 1);
        return queueManagementService.coldQueue(queueName, newVal == null ? 0L : newVal);
    }
}
