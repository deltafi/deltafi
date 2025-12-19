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

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.content.Segment;
import org.deltafi.common.types.*;
import org.deltafi.core.exceptions.JoinException;
import org.deltafi.core.exceptions.MissingFlowException;
import org.deltafi.core.metrics.MetricService;
import org.deltafi.core.services.analytics.AnalyticEventService;
import org.deltafi.core.services.pubsub.PublisherService;
import org.deltafi.core.types.*;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.deltafi.common.constant.DeltaFiConstants.*;

@Service
@AllArgsConstructor
@Slf4j
public class StateMachine {
    private final Clock clock;
    private final RestDataSourceService restDataSourceService;
    private final TimedDataSourceService timedDataSourceService;
    private final OnErrorDataSourceService onErrorDataSourceService;
    private final TransformFlowService transformFlowService;
    private final DataSinkService dataSinkService;
    private final DeltaFiPropertiesService deltaFiPropertiesService;
    private final IdentityService identityService;
    private final QueueManagementService queueManagementService;
    private final JoinEntryService joinEntryService;
    private final PublisherService publisherService;
    private final MetricService metricService;
    private final AnalyticEventService analyticEventService;

    private static final String FILTERED_TEST_MODE_CAUSE = "Filtered by test mode";

    /**
     * Advance a set of DeltaFiles to the next step using the state machine. Call if advancing multiple deltaFiles
     * to ensure that
     *
     * @param inputs List of StateMachine input objects
     * @return the list of action invocations to be performed
     * dataSource configured.
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
        input.deltaFile().updateState(OffsetDateTime.now(clock));
        return actionInputs;
    }

    private List<WrappedActionInput> updateCurrentFlow(StateMachineInput input, Map<String, Long> pendingQueued) {
        List<WrappedActionInput> actionInputs = new ArrayList<>();

        Action lastAction =  input.flow().lastAction();
        // lastAction can be null if we are entering a new dataSource via a subscription
        if (lastAction == null || lastAction.getState() == ActionState.COMPLETE || lastAction.getState() == ActionState.RETRIED) {
            if (input.flow().getType() == FlowType.TRANSFORM) {
                actionInputs.addAll(advanceTransform(input, pendingQueued));
            } else if (input.flow().getType() == FlowType.DATA_SINK) {
                actionInputs.addAll(advanceDataSink(input, pendingQueued));
            }
        }

        input.flow().updateState();

        return actionInputs;
    }

    private List<WrappedActionInput> advanceTransform(StateMachineInput input, Map<String, Long> pendingQueued) {
        String pendingAction = input.flow().getNextPendingAction();
        ActionConfiguration nextTransformAction;

        if (pendingAction == null) {
            return new ArrayList<>();
        }

        try {
            nextTransformAction = transformFlowService.findRunningActionConfigOrError(input.flow().getName(), pendingAction);
        } catch (Exception e) {
            markMissingAction(input.flow(), e.getMessage());
            return new ArrayList<>();
        }

        if (nextTransformAction != null && !input.flow().hasFinalAction(nextTransformAction.getName())) {
            return addNextAction(input, nextTransformAction, pendingQueued);
        }

        return new ArrayList<>();
    }

    private List<WrappedActionInput> advanceDataSink(StateMachineInput input, Map<String, Long> pendingQueued) {
        ActionConfiguration nextEgressAction;
        DataSink dataSink = null;

        try {
            dataSink = dataSinkService.getActiveFlowByName(input.flow().getName());
            nextEgressAction = dataSink.getEgressAction();
        } catch (MissingFlowException missingFlowException) {
            if (input.flow().lastAction().getState() == ActionState.COMPLETE) {
                // the flow has been turned off but we've already completed egress
                nextEgressAction = null;
            } else {
                nextEgressAction = new ActionConfiguration(MISSING_FLOW_ACTION, ActionType.UNKNOWN, "");
            }
        }

        if (nextEgressAction == null || input.flow().hasFinalAction(nextEgressAction.getName())) {
            Set<String> expectedAnnotations = (dataSink == null) ? null : dataSink.getExpectedAnnotations();
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
        Action action = input.flow().addAction(SYNTHETIC_EGRESS_ACTION_FOR_TEST, null, ActionType.EGRESS, ActionState.FILTERED,
                OffsetDateTime.now(clock));
        action.setFilteredCause(FILTERED_TEST_MODE_CAUSE);
        action.setFilteredContext("Filtered by test mode with a reason of - " + input.flow().getTestModeReason());
        analyticEventService.recordFilter(input.deltaFile(), input.flow().getName(), input.flow().getType(), action.getName(), FILTERED_TEST_MODE_CAUSE, action.getModified());
        analyticEventService.recordProvenance(input.deltaFile(), input.flow());
        return Collections.emptyList();
    }

    private List<WrappedActionInput> addNextAction(StateMachineInput input, ActionConfiguration nextAction, Map<String, Long> pendingQueued) {
        Action lastAction = input.flow().lastAction();
        ActionState actionState = queueState(nextAction.getType(), pendingQueued);
        Action newAction = input.flow().addAction(nextAction.getName(), nextAction.getType(), nextAction.getActionType(),
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
                (input.flow().getType() == FlowType.DATA_SINK || lastAction.getState() != ActionState.COMPLETE)) {
            return Collections.emptyList();
        }

        if (input.flow().getDepth() >= deltaFiPropertiesService.getDeltaFiProperties().getMaxFlowDepth()) {
            markFlowAsCircular(input.flow());
            return Collections.emptyList();
        }

        Set<DeltaFileFlow> subscriberFlows;
        try {
            subscriberFlows = publisherService.subscribers(getFlow(input.flow()), input.deltaFile(), input.flow());
            // make sure the flow is properly terminated, this can happen when the last action was retried publish
            if (!input.flow().terminal()) {
                input.flow().updateState(DeltaFileFlowState.COMPLETE);
            }
        } catch (MissingFlowException e) {
            markMissingFlow(input.flow(), e);
            return Collections.emptyList();
        }

        generatePubSubMetrics(input.deltaFile(), input.flow(), subscriberFlows);

        for (DeltaFileFlow newFlow : subscriberFlows) {
            if (newFlow.getState() != DeltaFileFlowState.PAUSED) {
                StateMachineInput newInput = new StateMachineInput(input.deltaFile(), newFlow);
                actionInputs.addAll(advance(newInput, pendingQueued));
            }
        }

        if (!actionInputs.isEmpty() && input.flow().getState() != DeltaFileFlowState.COMPLETE) {
            input.flow().setState(DeltaFileFlowState.COMPLETE);
        }

        return actionInputs;
    }

    private void generatePubSubMetrics(DeltaFile deltafile, DeltaFileFlow publisher, Set<DeltaFileFlow> subscribers) {

        long bytes;
        if (publisher.getType() == FlowType.TRANSFORM) {
            List<Content> content = (publisher.lastAction() != null) ? publisher.lastActionContent() : publisher.lastContent();
            bytes = Segment.calculateTotalSize(content.stream().flatMap(s -> s.getSegments().stream()).collect(Collectors.toSet()));
            Map<String, String> publishTags = Map.of(
                    "dataSource", deltafile.getDataSource(),
                    "flowName", publisher.getName());

            metricService.increment(new Metric(BYTES_OUT, bytes, publishTags));
            metricService.increment(new Metric(FILES_OUT, 1, publishTags));
        } else {
            bytes = deltafile.getIngressBytes();
            Map<String, String> publishTags = Map.of("dataSource", deltafile.getDataSource());

            metricService.increment(new Metric(BYTES_FROM_SOURCE, bytes, publishTags));
            metricService.increment(new Metric(FILES_FROM_SOURCE, 1, publishTags));
        }

        for (DeltaFileFlow subscriber : subscribers) {
            // Egress flow metrics are skipped here and produced when the egress flow execution is completed
            if (subscriber.getType() != FlowType.TRANSFORM) continue;
            Map<String, String> subscribeTags = Map.of(
                    "dataSource", deltafile.getDataSource(),
                    "flowName", subscriber.getName());
            metricService.increment(new Metric(BYTES_IN, bytes, subscribeTags));
            metricService.increment(new Metric(FILES_IN, 1, subscribeTags));
        }
    }

    private void markFlowAsCircular(DeltaFileFlow flow) {
        addErroredPublishAction(flow, "CIRCULAR_FLOWS", "Circular flows detected",
                "Circular flows detected. Processing stopped at maximum depth of " + deltaFiPropertiesService.getDeltaFiProperties().getMaxFlowDepth());
    }

    private void markMissingFlow(DeltaFileFlow flow, MissingFlowException missingFlowException) {
        addErroredPublishAction(flow, MISSING_FLOW_ACTION, missingFlowException.getMissingCause(), missingFlowException.getMessage());
    }

    private void markMissingAction(DeltaFileFlow flow, String context) {
        Action action = flow.lastAction();
        OffsetDateTime now = OffsetDateTime.now(clock);
        action.error(null, null, now, "Action configuration not found", context);
    }

    private void addErroredPublishAction(DeltaFileFlow flow, String actionName, String cause, String context) {
        List<Content> lastActionContent = flow.lastActionContent();
        Action action = flow.queueNewAction(actionName, null, ActionType.PUBLISH, false, OffsetDateTime.now(clock));
        action.setState(ActionState.ERROR);
        action.setErrorCause(cause);
        action.setErrorContext(context);
        action.setContent(lastActionContent);
        flow.updateState();
    }

    private Flow getFlow(DeltaFileFlow flow) {
        return switch (flow.getType()) {
            case REST_DATA_SOURCE -> restDataSourceService.getActiveFlowByName(flow.getName());
            case TIMED_DATA_SOURCE -> timedDataSourceService.getActiveFlowByName(flow.getName());
            case ON_ERROR_DATA_SOURCE -> onErrorDataSourceService.getActiveFlowByName(flow.getName());
            case TRANSFORM -> transformFlowService.getActiveFlowByName(flow.getName());
            default -> throw new IllegalArgumentException("Unexpected value: " + flow.getType());
        };
    }

    private WrappedActionInput buildActionInput(ActionConfiguration actionConfiguration, DeltaFile deltaFile,
                                                DeltaFileFlow flow, Action action) {
        String systemName = deltaFiPropertiesService.getDeltaFiProperties().getSystemName();
        String returnAddress = identityService.getUniqueId();

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
            joinEntryService.unlock(joinEntry.getId());
            return null;
        }

        List<UUID> joinedDids = joinEntryService.findJoinedDids(joinEntry.getId());

        WrappedActionInput actionInput = DeltaFileUtil.createAggregateInput(actionConfiguration, currentFlow, joinEntry, joinedDids, coldOrWarm, systemName, returnAddress);

        // TODO - is it safe to do this here before child has been sunk to disk?
        joinEntryService.delete(joinEntry.getId());
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
