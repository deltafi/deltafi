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
package org.deltafi.core.util;

import org.deltafi.common.content.Segment;
import org.deltafi.common.types.*;
import org.deltafi.core.types.*;

import java.time.OffsetDateTime;
import java.util.*;

import static org.deltafi.common.constant.DeltaFiConstants.INVALID_ACTION_EVENT_RECEIVED;
import static org.deltafi.common.constant.DeltaFiConstants.SYNTHETIC_EGRESS_ACTION_FOR_TEST;
import static org.deltafi.common.types.ActionState.QUEUED;
import static org.deltafi.core.services.pubsub.PublisherService.NO_SUBSCRIBERS;
import static org.deltafi.core.services.pubsub.PublisherService.NO_SUBSCRIBER_CAUSE;
import static org.deltafi.core.util.Constants.*;
import static org.deltafi.core.util.FlowBuilders.EGRESS_TOPIC;
import static org.deltafi.core.util.FlowBuilders.TRANSFORM_TOPIC;

public class FullFlowExemplars {

    public static final String SAMPLE_EGRESS_ACTION = "SampleEgressAction";
    public static final ActionConfiguration TRANSFORM1 = new ActionConfiguration("Utf8TransformAction", ActionType.TRANSFORM, "type");
    public static final ActionConfiguration TRANSFORM2 = new ActionConfiguration("SampleTransformAction", ActionType.TRANSFORM, "type");
    public static final ActionConfiguration EGRESS = new ActionConfiguration(SAMPLE_EGRESS_ACTION, ActionType.EGRESS, "type");
    public static final List<ActionConfiguration> TRANSFORM_ACTIONS = List.of(TRANSFORM1, TRANSFORM2);

    public static final UUID UUID_1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID UUID_2 = UUID.fromString("22222222-2222-2222-2222-222222222222");

    public static DeltaFile ingressedFromAction(UUID did, String dataSource) {
        Content content = new Content("filename", "application/text",
                new Segment(UUID.fromString("11111111-1111-1111-1111-111111111111"), 0, 36, did));
        Map<String, String> metadata = Map.of("smoke", "test");

        DeltaFile deltaFile = DeltaFile.builder()
                .did(did)
                .parentDids(new ArrayList<>())
                .childDids(new ArrayList<>())
                .ingressBytes(36L)
                .name("filename")
                .dataSource(dataSource)
                .stage(DeltaFileStage.IN_FLIGHT)
                .created(OffsetDateTime.now())
                .modified(OffsetDateTime.now())
                .flows(new LinkedHashSet<>())
                .egressed(false)
                .filtered(false)
                .build();

        DeltaFileFlow ingressFlow = DeltaFileFlow.builder()
                .name(TIMED_DATA_SOURCE_NAME)
                .type(FlowType.TIMED_DATA_SOURCE)
                .state(DeltaFileFlowState.COMPLETE)
                .actions(new ArrayList<>())
                .publishTopics(List.of(TRANSFORM_TOPIC))
                .build();
        deltaFile.getFlows().add(ingressFlow);

        Action ingress = ingressFlow.addAction("SampleTimedIngressAction", ActionType.INGRESS,
                ActionState.COMPLETE, OffsetDateTime.now());
        ingress.setContent(List.of(content));
        ingress.setMetadata(metadata);

        DeltaFileFlow transformFlow = deltaFile.addFlow(TRANSFORM_FLOW_NAME, FlowType.TRANSFORM, ingressFlow,
                Set.of(TRANSFORM_TOPIC), OffsetDateTime.now());

        transformFlow.addAction("Utf8TransformAction", ActionType.TRANSFORM, ActionState.QUEUED, OffsetDateTime.now());

        return deltaFile;
    }

    public static DeltaFile ingressedFromActionWithError(UUID did) {
        DeltaFile deltaFile = ingressedFromAction(did, TIMED_DATA_SOURCE_ERROR_NAME);
        deltaFile.getFlow(TIMED_DATA_SOURCE_NAME).setPublishTopics(List.of(MISSING_PUBLISH_TOPIC));
        deltaFile.setFlows(new HashSet<>(Set.of(deltaFile.firstFlow())));
        DeltaFileFlow flow = deltaFile.firstFlow();
        flow.setName(TIMED_DATA_SOURCE_ERROR_NAME);
        flow.setState(DeltaFileFlowState.ERROR);
        flow.firstAction().setName("SampleTimedIngressErrorAction");

        Action action = flow.addAction("NO_SUBSCRIBERS", ActionType.PUBLISH, ActionState.ERROR, OffsetDateTime.now());
        action.setErrorCause(NO_SUBSCRIBER_CAUSE);
        action.setErrorContext("No subscribers found for data source 'sampleTimedDataSourceError' on topic 'missingPublishTopic'");
        action.setContent(flow.firstAction().getContent());

        deltaFile.setStage(DeltaFileStage.ERROR);

        return deltaFile;
    }

    public static DeltaFile postTransformDeltaFileWithUnicodeAnnotation(UUID did) {
        DeltaFile deltaFile = postTransformDeltaFile(did);
        deltaFile.addAnnotationIfAbsent("first", "one");
        deltaFile.addAnnotationIfAbsent("second", "two");
        deltaFile.addAnnotationIfAbsent("āȂ", "̃Є");
        return deltaFile;
    }

    public static Action getAction(DeltaFileFlow flow, String actionName) {
        return flow.getAction(actionName);
    }

    public static DeltaFile postTransformInvalidDeltaFile(UUID did) {
        DeltaFile deltaFile = postTransformUtf8DeltaFile(did);
        deltaFile.setStage(DeltaFileStage.ERROR);
        DeltaFileFlow flow = deltaFile.getFlow(UUID_1);
        flow.setState(DeltaFileFlowState.ERROR);
        Action action = getAction(flow,"SampleTransformAction");
        action.changeState(ActionState.ERROR, START_TIME, STOP_TIME, OffsetDateTime.now());
        action.setErrorCause(INVALID_ACTION_EVENT_RECEIVED);
        action.setErrorContext("STARTS:Action event type does not match the populated object");

        return deltaFile;
    }

    public static DeltaFile postIngressDeltaFile(UUID did) {
        Content content = new Content("name", "application/octet-stream", new Segment(UUID.fromString("11111111-1111-1111-1111-111111111111"), 0, 500, did));
        DeltaFile deltaFile = Util.emptyDeltaFile(did, TIMED_DATA_SOURCE_NAME, List.of(content));
        deltaFile.setIngressBytes(500L);
        DeltaFileFlow flow = deltaFile.addFlow(TRANSFORM_FLOW_NAME, FlowType.TRANSFORM, deltaFile.firstFlow(), OffsetDateTime.now());
        flow.setId(UUID_1);
        flow.setPendingActions(TRANSFORM_ACTIONS.stream().map(ActionConfiguration::getName).toList());
        flow.getInput().setMetadata(SOURCE_METADATA);
        flow.getInput().setTopics(Set.of(TRANSFORM_TOPIC));
        flow.queueAction("Utf8TransformAction", ActionType.TRANSFORM, false, OffsetDateTime.now());
        deltaFile.setName("input.txt");
        deltaFile.setDataSource(REST_DATA_SOURCE_NAME);
        return deltaFile;
    }

    public static DeltaFile postTransformUtf8DeltaFile(UUID did) {
        DeltaFile deltaFile = postIngressDeltaFile(did);
        deltaFile.setStage(DeltaFileStage.IN_FLIGHT);
        Content content = new Content("file.json", "application/octet-stream", new Segment(UUID.fromString("11111111-1111-1111-1111-111111111114"), 0, 500, did));
        DeltaFileFlow flow = deltaFile.getFlow(UUID_1);
        flow.getInput().setTopics(Set.of(TRANSFORM_TOPIC));
        Action action = flow.getAction("Utf8TransformAction");
        action.complete(START_TIME, STOP_TIME, List.of(content), Map.of(), List.of(), OffsetDateTime.now());
        flow.queueAction("SampleTransformAction", ActionType.TRANSFORM, false, OffsetDateTime.now());
        return deltaFile;
    }

    public static DeltaFile postTransformUtf8NoSubscriberDeltaFile(UUID did) {
        DeltaFile deltaFile = postTransformUtf8DeltaFile(did);
        Content content = new Content("transformed", "application/octet-stream", new Segment(UUID.fromString("11111111-1111-1111-1111-111111111111"), 0, 500, did));
        DeltaFileFlow flow = deltaFile.getFlow(UUID_1);
        flow.setPublishTopics(List.of(EGRESS_TOPIC));
        Action action = flow.getAction("SampleTransformAction");
        action.complete(START_TIME, STOP_TIME, List.of(content), TRANSFORM_METADATA, List.of(), OffsetDateTime.now());
        Action noSubAction = flow.queueNewAction(NO_SUBSCRIBERS, ActionType.PUBLISH, false, OffsetDateTime.now());
        noSubAction.error(OffsetDateTime.now(), OffsetDateTime.now(), OffsetDateTime.now(), NO_SUBSCRIBER_CAUSE, "");
        deltaFile.setStage(DeltaFileStage.ERROR);
        return deltaFile;
    }

    public static DeltaFile postResumeNoSubscribersDeltaFile(UUID did) {
        DeltaFile deltaFile = postTransformUtf8NoSubscriberDeltaFile(did);
        deltaFile.resumeErrors(List.of(new ResumeMetadata(TRANSFORM_FLOW_NAME, NO_SUBSCRIBERS, Map.of(), List.of())), OffsetDateTime.now());

        DeltaFileFlow flow = deltaFile.getFlow(UUID_1);
        flow.setState(DeltaFileFlowState.COMPLETE);

        DeltaFileFlow egressFlow = deltaFile.addFlow(EGRESS_FLOW_NAME, FlowType.EGRESS, flow, OffsetDateTime.now());
        egressFlow.getInput().setTopics(Set.of(EGRESS_TOPIC));
        egressFlow.getInput().setMetadata(flow.getMetadata());
        egressFlow.getInput().setContent(flow.lastContent());
        egressFlow.queueAction(SAMPLE_EGRESS_ACTION, ActionType.EGRESS, false, OffsetDateTime.now());
        deltaFile.setStage(DeltaFileStage.IN_FLIGHT);
        return deltaFile;
    }

    public static DeltaFile postCancelDeltaFile(UUID did) {
        DeltaFile deltaFile = postTransformUtf8DeltaFile(did);
        deltaFile.cancel(OffsetDateTime.now());
        return deltaFile;
    }

    public static DeltaFile postTransformHadErrorDeltaFile(UUID did) {
        DeltaFile deltaFile = postTransformUtf8DeltaFile(did);
        deltaFile.lastFlow().setPendingActions(List.of(TRANSFORM2.getName()));
        deltaFile.setStage(DeltaFileStage.ERROR);
        DeltaFileFlow flow = deltaFile.getFlow(UUID_1);
        Action action = flow.getAction("SampleTransformAction");
        action.error(START_TIME, STOP_TIME, OffsetDateTime.now(), "transform failed", "message");
        return deltaFile;
    }

    @SuppressWarnings("SameParameterValue")
    public static DeltaFile postResumeTransformDeltaFile(UUID did) {
        DeltaFile deltaFile = postTransformHadErrorDeltaFile(did);
        deltaFile.resumeErrors(List.of(new ResumeMetadata(TRANSFORM_FLOW_NAME, "SampleTransformAction", Map.of("AuthorizedBy", "ABC", "anotherKey", "anotherValue"), List.of("removeMe"))), OffsetDateTime.now());
        deltaFile.setStage(DeltaFileStage.IN_FLIGHT);
        DeltaFileFlow flow = deltaFile.getFlow(UUID_1);
        Action action = flow.queueAction("SampleTransformAction", ActionType.TRANSFORM, false, OffsetDateTime.now());
        action.setAttempt(2);
        return deltaFile;
    }

    public static DeltaFile postTransformDeltaFile(UUID did) {
        DeltaFile deltaFile = withCompleteTransformFlow(did);
        DeltaFileFlow flow = deltaFile.getFlow(UUID_1);

        DeltaFileFlow egressFlow = deltaFile.addFlow(EGRESS_FLOW_NAME, FlowType.EGRESS, flow, OffsetDateTime.now());
        egressFlow.setId(UUID_2);
        egressFlow.getInput().setTopics(Set.of(EGRESS_TOPIC));
        egressFlow.getInput().setMetadata(flow.getMetadata());
        egressFlow.getInput().setContent(flow.lastContent());
        egressFlow.queueAction(SAMPLE_EGRESS_ACTION, ActionType.EGRESS, false, OffsetDateTime.now());
        return deltaFile;
    }

    private static DeltaFile withCompleteTransformFlow(UUID did) {
        DeltaFile deltaFile = postTransformUtf8DeltaFile(did);
        deltaFile.setStage(DeltaFileStage.IN_FLIGHT);
        Content content = new Content("transformed", "application/octet-stream", new Segment(UUID.fromString("11111111-1111-1111-1111-111111111111"), 0, 500, did));
        DeltaFileFlow flow = deltaFile.getFlow(UUID_1);
        flow.setState(DeltaFileFlowState.COMPLETE);
        flow.setPublishTopics(List.of(EGRESS_TOPIC));
        flow.getInput().setTopics(Set.of(TRANSFORM_TOPIC));
        Action action = flow.getAction("SampleTransformAction");
        action.complete(START_TIME, STOP_TIME, List.of(content), TRANSFORM_METADATA, List.of(), OffsetDateTime.now());
        return deltaFile;
    }

    public static DeltaFile postErrorDeltaFile(UUID did) {
        return postErrorDeltaFile(did, null, null);
    }

    public static DeltaFile postErrorDeltaFile(UUID did, String policyName, Integer autoRetryDelay) {
        OffsetDateTime nextAutoResume = autoRetryDelay == null ? null : STOP_TIME.plusSeconds(autoRetryDelay);
        DeltaFile deltaFile = postTransformDeltaFile(did);
        deltaFile.setStage(DeltaFileStage.ERROR);
        DeltaFileFlow flow = deltaFile.getFlow(UUID_2);
        flow.setPendingActions(List.of(EGRESS.getName()));
        flow.setState(DeltaFileFlowState.ERROR);
        Action action = flow.getAction(SAMPLE_EGRESS_ACTION);
        action.error(START_TIME, STOP_TIME, OffsetDateTime.now(),
                "Authority XYZ not recognized", "Dead beef feed face cafe");
        action.setNextAutoResume(nextAutoResume);
        if (policyName != null) {
            action.setNextAutoResumeReason(policyName);
        }
        return deltaFile;
    }

    public static DeltaFile postResumeDeltaFile(UUID did) {
        DeltaFile deltaFile = postErrorDeltaFile(did);
        DeltaFileFlow flow = deltaFile.getFlow(UUID_2);
        flow.getActions().getFirst().setState(ActionState.RETRIED);
        flow.firstAction().setMetadata(Map.of("a", "b"));
        Action action = flow.addAction(SAMPLE_EGRESS_ACTION, ActionType.EGRESS, QUEUED, OffsetDateTime.now());
        action.setAttempt(2);
        flow.setState(DeltaFileFlowState.IN_FLIGHT);
        deltaFile.setStage(DeltaFileStage.IN_FLIGHT);
        return deltaFile;
    }

    public static DeltaFile postTransformDeltaFileInTestMode(UUID did, OffsetDateTime now) {
        DeltaFile deltaFile = withCompleteTransformFlow(did);
        deltaFile.setFiltered(true);
        DeltaFileFlow flow = deltaFile.getFlow(UUID_1);
        flow.setTestMode(true);
        flow.setTestModeReason(TRANSFORM_FLOW_NAME);

        DeltaFileFlow egressFlow = deltaFile.addFlow(EGRESS_FLOW_NAME, FlowType.EGRESS, flow, now);
        egressFlow.getInput().setTopics(Set.of(EGRESS_TOPIC));
        Action action = egressFlow.addAction(SYNTHETIC_EGRESS_ACTION_FOR_TEST, ActionType.EGRESS, ActionState.FILTERED, now);
        action.setFilteredCause("Filtered by test mode");
        action.setFilteredContext("Filtered by test mode with a reason of - " + TRANSFORM_FLOW_NAME);
        egressFlow.setState(DeltaFileFlowState.FILTERED);
        egressFlow.setTestMode(true);
        egressFlow.setTestModeReason(TRANSFORM_FLOW_NAME);

        deltaFile.setEgressed(false);
        deltaFile.setStage(DeltaFileStage.COMPLETE);
        return deltaFile;
    }

    public static DeltaFile postEgressDeltaFile(UUID did) {
        DeltaFile deltaFile = postTransformDeltaFile(did);
        deltaFile.setStage(DeltaFileStage.COMPLETE);
        deltaFile.setEgressed(true);
        Content content = new Content("transformed", "application/octet-stream", new Segment(UUID.fromString("11111111-1111-1111-1111-111111111111"), 0, 500, did));
        DeltaFileFlow flow = deltaFile.getFlow(UUID_2);
        Action action = flow.getAction(SAMPLE_EGRESS_ACTION);
        action.complete(START_TIME, STOP_TIME, List.of(content), flow.getMetadata(), List.of(), OffsetDateTime.now());
        flow.setState(DeltaFileFlowState.COMPLETE);
        return deltaFile;
    }
}
