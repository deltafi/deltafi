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
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.types.*;
import org.deltafi.core.exceptions.JoinException;
import org.deltafi.core.services.analytics.AnalyticEventService;
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
    private final RestDataSourceService restDataSourceService;
    private final TimedDataSourceService timedDataSourceService;
    private final TransformFlowService transformFlowService;
    private final EgressFlowService egressFlowService;
    private final DeltaFiPropertiesService deltaFiPropertiesService;
    private final IdentityService identityService;
    private final QueueManagementService queueManagementService;
    private final JoinEntryService joinEntryService;
    private final ScheduledJoinService scheduledJoinService;
    private final PublisherService publisherService;
    private final AnalyticEventService analyticEventService;

    /**
     * Advance a set of DeltaFiles to the next step using the state machine. Call if advancing multiple deltaFiles
     * to ensure that
     *
     * @param inputs List of StateMachine input objects
     * @return the list of action invocations to be performed
     * flow configured.
     */
    public List<WrappedActionInput> advance(List<StateMachineInput> inputs) {
        Map<String, Long> pendingQueued = new HashMap<>();
        List<WrappedActionInput> actionInputs = new ArrayList<>();
        for (StateMachineInput input : inputs) {
            actionInputs.addAll(advance(input, pendingQueued));
        }
        return actionInputs;
    }

    private List<WrappedActionInput> advance(StateMachineInput input, Map<String, Long> pendingQueued) {
        List<WrappedActionInput> actionInputs = updateCurrentFlow(input, pendingQueued);
        if (actionInputs.isEmpty() && (input.flow().getState() == DeltaFileFlowState.COMPLETE ||
                input.flow().lastActionType() == ActionType.PUBLISH)) {
            actionInputs.addAll(publishToNewFlows(input, pendingQueued));
        }
        final DeltaFileStage startingStage = input.deltaFile().getStage();
        input.deltaFile().updateState(OffsetDateTime.now(clock));
        final DeltaFileStage endingStage = input.deltaFile().getStage();
        if (!startingStage.equals(endingStage) && endingStage.equals(DeltaFileStage.COMPLETE)) {
            analyticEventService.recordCompleted(input.deltaFile());
        }
        return actionInputs;
    }

    private List<WrappedActionInput> updateCurrentFlow(StateMachineInput input, Map<String, Long> pendingQueued) {
        List<WrappedActionInput> actionInputs = new ArrayList<>();

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

    private List<WrappedActionInput> advanceTransform(StateMachineInput input, Map<String, Long> pendingQueued) {
        String pendingAction = input.flow().getNextPendingAction();
        ActionConfiguration nextTransformAction = transformFlowService.findActionConfig(input.flow().getName(), pendingAction);

        if (nextTransformAction != null && !input.flow().hasFinalAction(nextTransformAction.getName())) {
            return addNextAction(input, nextTransformAction, pendingQueued);
        }

        return new ArrayList<>();
    }

    private List<WrappedActionInput> advanceEgress(StateMachineInput input, Map<String, Long> pendingQueued) {
        EgressFlow egressFlow = egressFlowService.getRunningFlowByName(input.flow().getName());
        ActionConfiguration nextEgressAction = egressFlow.getEgressAction();

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

    private List<WrappedActionInput> syntheticEgress(StateMachineInput input) {
        Action action = input.flow().addAction(SYNTHETIC_EGRESS_ACTION_FOR_TEST, ActionType.EGRESS, ActionState.FILTERED,
                OffsetDateTime.now(clock));
        action.setFilteredCause("Filtered by test mode");
        action.setFilteredContext("Filtered by test mode with a reason of - " + input.flow().getTestModeReason());
        input.deltaFile().setFiltered(true);
        return Collections.emptyList();
    }

    private List<WrappedActionInput> addNextAction(StateMachineInput input, ActionConfiguration nextAction, Map<String, Long> pendingQueued) {
        Action lastAction = input.flow().lastAction();
        ActionState actionState = queueState(nextAction.getName(), pendingQueued);
        Action newAction = input.flow().addAction(nextAction.getName(), nextAction.getActionType(),
                actionState, OffsetDateTime.now(clock));
        if (lastAction != null && lastAction.getState() == ActionState.RETRIED &&
                lastAction.getName().equals(newAction.getName())) {
            newAction.setAttempt(lastAction.getAttempt() + 1);
        }

        WrappedActionInput actionInput = buildActionInput(nextAction, input.deltaFile(), input.flow(), newAction);
        return actionInput != null ? List.of(actionInput) : Collections.emptyList();
    }

    private List<WrappedActionInput> publishToNewFlows(StateMachineInput input, Map<String, Long> pendingQueued) {
        List<WrappedActionInput> actionInputs = new ArrayList<>();

        Action lastAction = input.flow().lastAction();
        if (lastAction != null && lastAction.getType() != ActionType.PUBLISH &&
                (input.flow().getType() == FlowType.EGRESS || lastAction.getState() != ActionState.COMPLETE)) {
            return Collections.emptyList();
        }

        if (input.flow().getDepth() >= deltaFiPropertiesService.getDeltaFiProperties().getMaxFlowDepth()) {
            markFlowAsCircular(input.flow());
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

    private void markFlowAsCircular(DeltaFileFlow flow) {
        // grab the last content list to copy into the synthetic error action to make it available for retry
        List<Content> lastActionContent = flow.lastActionContent();
        Action action = flow.queueNewAction("CIRCULAR_FLOWS", ActionType.PUBLISH, false, OffsetDateTime.now(clock));
        action.setState(ActionState.ERROR);
        action.setErrorCause("Circular flows detected");
        action.setErrorContext("Circular flows detected. Processing stopped at maximum depth of " +
                deltaFiPropertiesService.getDeltaFiProperties().getMaxFlowDepth());
        action.setContent(lastActionContent);
        flow.setState(DeltaFileFlowState.ERROR);
    }

    private Flow getFlow(DeltaFileFlow flow) {
        return switch (flow.getType()) {
            case REST_DATA_SOURCE -> restDataSourceService.getRunningFlowByName(flow.getName());
            case TIMED_DATA_SOURCE -> timedDataSourceService.getRunningFlowByName(flow.getName());
            case TRANSFORM -> transformFlowService.getRunningFlowByName(flow.getName());
            default -> throw new IllegalArgumentException("Unexpected value: " + flow.getType());
        };
    }

    private WrappedActionInput buildActionInput(ActionConfiguration actionConfiguration, DeltaFile deltaFile,
                                                DeltaFileFlow flow, Action action) {
        String systemName = deltaFiPropertiesService.getDeltaFiProperties().getSystemName();
        String returnAddress = deltaFiPropertiesService.getDeltaFiProperties().isCacheEnabled() ?
                identityService.getUniqueId() : null;

        if (actionConfiguration.getJoin() != null) {
            try {
                return joinActionInput(actionConfiguration, deltaFile, flow, action, systemName, returnAddress);
            } catch (JoinException joinException) {
                deltaFile.setStage(DeltaFileStage.ERROR);
                return null;
            }
        }

        return deltaFile.buildActionInput(actionConfiguration, flow, action, systemName, returnAddress, null);
    }

    private ActionState queueState(String queueName, Map<String, Long> pendingQueued) {
        Long newVal = pendingQueued.put(queueName, pendingQueued.getOrDefault(queueName, 0L) + 1);
        boolean coldQueued = queueManagementService.coldQueue(queueName, newVal == null ? 0L : newVal);
        return coldQueued ? ActionState.COLD_QUEUED : ActionState.QUEUED;
    }

    private WrappedActionInput joinActionInput(ActionConfiguration actionConfiguration, DeltaFile deltaFile,
                                               DeltaFileFlow currentFlow, Action action, String systemName, String returnAddress) throws JoinException {
        if (deltaFile.getJoinId() != null) {
            return deltaFile.buildActionInput(actionConfiguration, currentFlow, deltaFile.getParentDids(), action, systemName, returnAddress, null);
        }

        JoinEntry joinEntry = getJoinEntry(actionConfiguration, currentFlow, deltaFile.getDid());
        ActionState coldOrWarm = action.getState();
        action.setState(ActionState.JOINING);
        currentFlow.setJoinId(joinEntry.getId());

        Integer maxNum = actionConfiguration.getJoin().maxNum();
        if (maxNum == null || joinEntry.getCount() < maxNum) {
            if (joinEntry.getCount() == 1) { // Only update join check for new join entries
                scheduledJoinService.updateJoinCheck(joinEntry.getJoinDate());
            }
            joinEntryService.unlock(joinEntry.getId());
            return null;
        }

        List<UUID> joinedDids = joinEntryService.findJoinedDids(joinEntry.getId());

        WrappedActionInput actionInput = DeltaFileUtil.createAggregateInput(actionConfiguration, currentFlow, joinEntry, joinedDids, coldOrWarm, systemName, returnAddress);

        // TODO - is it safe to do this here before child has been sunk to disk?
        joinEntryService.delete(joinEntry.getId());
        scheduledJoinService.scheduleNextJoinCheck();

        return actionInput;
    }

    private JoinEntry getJoinEntry(ActionConfiguration actionConfiguration, DeltaFileFlow currentFlow, UUID parentDid) throws JoinException {
        String joinGroup = Optional.ofNullable(actionConfiguration.getJoin().metadataKey())
                .map(metadataKey -> currentFlow.getMetadata().get(metadataKey))
                .orElse("DEFAULT");

        JoinDefinition joinDefinition = new JoinDefinition(currentFlow.getName(),
                actionConfiguration.getActionType(), actionConfiguration.getName(), joinGroup);

        JoinEntry joinEntry = joinEntryService.upsertAndLock(joinDefinition,
                OffsetDateTime.now(clock).plus(actionConfiguration.getJoin().maxAge()),
                actionConfiguration.getJoin().minNum(), actionConfiguration.getJoin().maxNum(), currentFlow.getDepth(), parentDid);

        if (joinEntry == null) {
            log.error("Timed out trying to lock join entry for parent did '{}'", parentDid);
            throw new JoinException("Timed out trying to lock join entry");
        }

        return joinEntry;
    }

}
