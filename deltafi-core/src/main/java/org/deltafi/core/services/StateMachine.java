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
import org.deltafi.core.services.pubsub.PublisherService;
import org.deltafi.core.types.*;
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
    private final DataSourceService dataSourceService;
    private final TransformFlowService transformFlowService;
    private final EgressFlowService egressFlowService;
    private final DeltaFiPropertiesService deltaFiPropertiesService;
    private final IdentityService identityService;
    private final QueueManagementService queueManagementService;
    private final CollectEntryService collectEntryService;
    private final ScheduledCollectService scheduledCollectService;
    private final PublisherService publisherService;

    /**
     * Advance a set of DeltaFiles to the next step using the state machine. Call if advancing multiple deltaFiles
     * to ensure that
     *
     * @param inputs List of StateMachine input objects
     * @return the list of action invocations to be performed
     * flow configured.
     */
    public List<ActionInput> advance(List<StateMachineInput> inputs) {
        Map<String, Long> pendingQueued = new HashMap<>();
        List<ActionInput> actionInputs = new ArrayList<>();
        for (StateMachineInput input : inputs) {
            actionInputs.addAll(advance(input, pendingQueued));
        }
        return actionInputs;
    }

    private List<ActionInput> advance(StateMachineInput input, Map<String, Long> pendingQueued) {
        List<ActionInput> actionInputs = updateCurrentFlow(input, pendingQueued);
        if (actionInputs.isEmpty() &&
                (input.flow().getState() == DeltaFileFlowState.COMPLETE || input.flow().lastActionType() == ActionType.PUBLISH)) {
            actionInputs.addAll(publishToNewFlows(input, pendingQueued));
        }
        input.deltaFile().updateState(OffsetDateTime.now(clock));

        return actionInputs;
    }

    private List<ActionInput> updateCurrentFlow(StateMachineInput input, Map<String, Long> pendingQueued) {
        List<ActionInput> actionInputs = new ArrayList<>();

        Action lastAction =  input.flow().lastAction();
        // lastAction can be null if we are entering a new flow via a subscription
        if (lastAction == null || lastAction.getState() == ActionState.COMPLETE || lastAction.getState() == ActionState.RETRIED) {
            if (input.flow().getType() == FlowType.TRANSFORM) {
                actionInputs.addAll(advanceTransform(input, pendingQueued));
            } else if (input.flow().getType() == FlowType.EGRESS) {
                actionInputs.addAll(advanceEgress(input, pendingQueued));
            }
        }

        input.flow().updateState(OffsetDateTime.now(clock));

        return actionInputs;
    }

    private List<ActionInput> advanceTransform(StateMachineInput input, Map<String, Long> pendingQueued) {
        ActionConfiguration nextTransformAction = input.flow().getNextActionConfiguration();

        if (nextTransformAction != null && !input.flow().hasFinalAction(nextTransformAction.getName())) {
            return addNextAction(input, nextTransformAction, pendingQueued);
        }

        return new ArrayList<>();
    }

    private List<ActionInput> advanceEgress(StateMachineInput input, Map<String, Long> pendingQueued) {
        EgressFlow egressFlow = egressFlowService.getRunningFlowByName(input.flow().getName());

        ActionConfiguration nextEgressAction = input.flow().getNextActionConfiguration();
        if (nextEgressAction == null || input.flow().hasFinalAction(nextEgressAction.getName())) {
            Set<String> expectedAnnotations = egressFlow.getExpectedAnnotations();
            if (expectedAnnotations != null && !expectedAnnotations.isEmpty()) {
                Set<String> pendingAnnotations = input.deltaFile().getPendingAnnotations(expectedAnnotations);
                input.flow().setPendingAnnotations(pendingAnnotations);
            }
            return new ArrayList<>();
        }

        return input.flow().isTestMode() ?
                syntheticEgress(input) : addNextAction(input, nextEgressAction, pendingQueued);
    }

    private List<ActionInput> syntheticEgress(StateMachineInput input) {
        Action action = input.flow().addAction(SYNTHETIC_EGRESS_ACTION_FOR_TEST, ActionType.EGRESS, ActionState.FILTERED,
                OffsetDateTime.now(clock));
        action.setFilteredCause("Filtered by test mode");
        action.setFilteredCause("Filtered by test mode with a reason of - " + input.flow().getTestModeReason());
        input.deltaFile().setFiltered(true);
        return Collections.emptyList();
    }

    private List<ActionInput> addNextAction(StateMachineInput input, ActionConfiguration nextAction, Map<String, Long> pendingQueued) {
        Action lastAction = input.flow().lastAction();
        ActionState actionState = queueState(nextAction.getName(), pendingQueued);
        Action newAction = input.flow().addAction(nextAction.getName(), nextAction.getActionType(),
                actionState, OffsetDateTime.now(clock));
        if (lastAction != null && lastAction.getState() == ActionState.RETRIED &&
                lastAction.getName().equals(newAction.getName())) {
            newAction.setAttempt(lastAction.getAttempt() + 1);
        }
        return List.of(buildActionInput(nextAction, input.deltaFile(), input.flow(), newAction));
    }

    private List<ActionInput> publishToNewFlows(StateMachineInput input, Map<String, Long> pendingQueued) {
        List<ActionInput> actionInputs = new ArrayList<>();

        Action lastAction = input.flow().lastAction();
        if (lastAction != null && lastAction.getType() != ActionType.PUBLISH &&
                (input.flow().getType() == FlowType.EGRESS || lastAction.getState() != ActionState.COMPLETE)) {
            return Collections.emptyList();
        }

        Set<DeltaFileFlow> subscriberFlows = publisherService.subscribers(getFlow(input.flow()), input.deltaFile(), input.flow());

        for (DeltaFileFlow newFlow : subscriberFlows) {
            StateMachineInput newInput = new StateMachineInput(input.deltaFile(), newFlow);
            actionInputs.addAll(advance(newInput, pendingQueued));
        }

        if (!actionInputs.isEmpty() && input.flow().getState() != DeltaFileFlowState.COMPLETE) {
            input.flow().setState(DeltaFileFlowState.COMPLETE);
        }

        return actionInputs;
    }

    private Flow getFlow(DeltaFileFlow flow) {
        return switch (flow.getType()) {
            case REST_DATA_SOURCE, TIMED_DATA_SOURCE -> dataSourceService.getRunningFlowByName(flow.getName());
            case TRANSFORM -> transformFlowService.getRunningFlowByName(flow.getName());
            default -> throw new IllegalArgumentException("Unexpected value: " + flow.getType());
        };
    }

    private ActionInput buildActionInput(ActionConfiguration actionConfiguration, DeltaFile deltaFile,
                                         DeltaFileFlow flow, Action action) {
        String systemName = deltaFiPropertiesService.getDeltaFiProperties().getSystemName();
        String returnAddress = deltaFile.getVersion() > 0 &&
                deltaFiPropertiesService.getDeltaFiProperties().getDeltaFileCache().isEnabled() ?
                identityService.getUniqueId() : null;
        return actionConfiguration.buildActionInput(deltaFile, flow, action, systemName, returnAddress, null);
    }

    private ActionState queueState(String queueName, Map<String, Long> pendingQueued) {
        Long newVal = pendingQueued.put(queueName, pendingQueued.getOrDefault(queueName, 0L) + 1);
        boolean coldQueued = queueManagementService.coldQueue(queueName, newVal == null ? 0L : newVal);
        return coldQueued ? ActionState.COLD_QUEUED : ActionState.QUEUED;
    }

    //TODO: put collect stuff back
/*
    private Optional<ActionInput> collect(String flow, ActionConfiguration actionConfiguration, DeltaFile deltaFile,
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

        ActionInput ActionInput = buildCollectingActionInput(collectEntry,
                collectEntryService.findCollectedDids(collectEntry.getId()), actionConfiguration, actionState);

        collectEntryService.delete(collectEntry.getId());
        scheduledCollectService.scheduleNextCollectCheck();

        return Optional.of(ActionInput);
    }

    private CollectingActionInput buildCollectingActionInput(CollectEntry collectEntry,
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

        return CollectingActionInput.builder()
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
    }*/
}
