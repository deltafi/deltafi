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
package org.deltafi.core.util;

import lombok.RequiredArgsConstructor;
import org.deltafi.common.content.Segment;
import org.deltafi.common.types.*;
import org.deltafi.core.services.FlowDefinitionService;
import org.deltafi.core.types.*;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

import static org.deltafi.common.constant.DeltaFiConstants.INVALID_ACTION_EVENT_RECEIVED;
import static org.deltafi.common.constant.DeltaFiConstants.SYNTHETIC_EGRESS_ACTION_FOR_TEST;
import static org.deltafi.common.types.ActionState.QUEUED;
import static org.deltafi.core.services.pubsub.PublisherService.NO_SUBSCRIBERS;
import static org.deltafi.core.services.pubsub.PublisherService.NO_SUBSCRIBER_CAUSE;
import static org.deltafi.core.util.Constants.*;
import static org.deltafi.core.util.FlowBuilders.EGRESS_TOPIC;
import static org.deltafi.core.util.FlowBuilders.TRANSFORM_TOPIC;

@Service
@RequiredArgsConstructor
public class FullFlowExemplarService {

    private final FlowDefinitionService flowDefinitionService;
    private final UtilService utilService;

    public static final String SAMPLE_EGRESS_ACTION = "SampleEgressAction";
    public static final ActionConfiguration TRANSFORM1 = new ActionConfiguration("Utf8TransformAction", ActionType.TRANSFORM, "type");
    public static final ActionConfiguration TRANSFORM2 = new ActionConfiguration("SampleTransformAction", ActionType.TRANSFORM, "type");
    public static final ActionConfiguration EGRESS = new ActionConfiguration(SAMPLE_EGRESS_ACTION, ActionType.EGRESS, "type");
    public static final List<ActionConfiguration> TRANSFORM_ACTIONS = List.of(TRANSFORM1, TRANSFORM2);

    public static final UUID UUID_1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID UUID_2 = UUID.fromString("22222222-2222-2222-2222-222222222222");

    public DeltaFile ingressedFromAction(UUID did, String dataSource) {
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
                .created(now())
                .modified(now())
                .flows(new LinkedHashSet<>())
                .egressed(false)
                .filtered(false)
                .build();
        deltaFile.addAnnotations(Map.of("a", "b", "c", "d"));

        DeltaFileFlow ingressFlow = DeltaFileFlow.builder()
                .flowDefinition(flowDefinitionService.getOrCreateFlow(TIMED_DATA_SOURCE_NAME, FlowType.TIMED_DATA_SOURCE))
                .state(DeltaFileFlowState.COMPLETE)
                .actions(new ArrayList<>())
                .publishTopics(List.of(TRANSFORM_TOPIC))
                .build();
        deltaFile.getFlows().add(ingressFlow);

        Action ingress = ingressFlow.addAction("SampleTimedIngressAction", null, ActionType.INGRESS,
                ActionState.COMPLETE, now());
        ingress.setContent(List.of(content));
        ingress.setMetadata(metadata);

        DeltaFileFlow transformFlow = deltaFile.addFlow(flowDefinitionService.getOrCreateFlow(TRANSFORM_FLOW_NAME, FlowType.TRANSFORM),
                ingressFlow, Set.of(TRANSFORM_TOPIC), now());

        transformFlow.addAction("Utf8TransformAction", null, ActionType.TRANSFORM, ActionState.QUEUED, now());
        deltaFile.wireBackPointers();

        return deltaFile;
    }

    public DeltaFile ingressedFromActionWithError(UUID did) {
        DeltaFile deltaFile = ingressedFromAction(did, TIMED_DATA_SOURCE_ERROR_NAME);
        deltaFile.getFlow(TIMED_DATA_SOURCE_NAME).setPublishTopics(List.of(MISSING_PUBLISH_TOPIC));
        deltaFile.setFlows(new HashSet<>(Set.of(deltaFile.firstFlow())));
        DeltaFileFlow flow = deltaFile.firstFlow();
        flow.setFlowDefinition(flowDefinitionService.getOrCreateFlow(TIMED_DATA_SOURCE_ERROR_NAME, FlowType.TIMED_DATA_SOURCE));
        flow.setState(DeltaFileFlowState.ERROR);
        flow.firstAction().setName("SampleTimedIngressErrorAction");

        Action action = flow.addAction("NO_SUBSCRIBERS", null, ActionType.PUBLISH, ActionState.ERROR, now());
        action.setErrorCause(NO_SUBSCRIBER_CAUSE);
        action.setErrorContext("No subscribers found for timed data source 'sampleTimedDataSourceError' on topic 'missingPublishTopic'");
        action.setContent(flow.firstAction().getContent());

        deltaFile.setStage(DeltaFileStage.ERROR);

        return deltaFile;
    }

    public DeltaFile postTransformDeltaFileWithUnicodeAnnotation(UUID did) {
        DeltaFile deltaFile = postTransformDeltaFile(did);
        deltaFile.addAnnotationIfAbsent("first", "one");
        deltaFile.addAnnotationIfAbsent("second", "two");
        deltaFile.addAnnotationIfAbsent("āȂ", "̃Є");
        return deltaFile;
    }

    public Action getAction(DeltaFileFlow flow, String actionName) {
        return flow.getAction(actionName);
    }

    public DeltaFile postTransformInvalidDeltaFile(UUID did) {
        DeltaFile deltaFile = postTransformUtf8DeltaFile(did);
        deltaFile.setStage(DeltaFileStage.ERROR);
        DeltaFileFlow flow = deltaFile.getFlow(UUID_1);
        flow.setState(DeltaFileFlowState.ERROR);
        Action action = getAction(flow,"SampleTransformAction");
        action.changeState(ActionState.ERROR, START_TIME, STOP_TIME, now());
        action.setErrorCause(INVALID_ACTION_EVENT_RECEIVED);
        action.setErrorContext("STARTS:Action event type does not match the populated object");

        return deltaFile;
    }

    public DeltaFile postIngressDeltaFile(UUID did) {
        Content content = new Content("name", "application/octet-stream", new Segment(UUID.fromString("11111111-1111-1111-1111-111111111111"), 0, 500, did));
        DeltaFile deltaFile = utilService.emptyDeltaFile(did, TIMED_DATA_SOURCE_NAME, List.of(content));
        deltaFile.firstFlow().firstAction().setMetadata(SOURCE_METADATA);
        deltaFile.setIngressBytes(500L);
        DeltaFileFlow flow = deltaFile.addFlow(flowDefinitionService.getOrCreateFlow(TRANSFORM_FLOW_NAME, FlowType.TRANSFORM), deltaFile.firstFlow(), now());
        flow.setId(UUID_1);
        flow.setPendingActions(TRANSFORM_ACTIONS.stream().map(ActionConfiguration::getName).toList());
        flow.getInput().setTopics(Set.of(TRANSFORM_TOPIC));
        flow.queueAction("Utf8TransformAction", null, ActionType.TRANSFORM, false, now());
        deltaFile.setName("input.txt");
        deltaFile.setDataSource(REST_DATA_SOURCE_NAME);
        return deltaFile;
    }

    public DeltaFile postTransformUtf8DeltaFile(UUID did) {
        DeltaFile deltaFile = postIngressDeltaFile(did);
        deltaFile.setStage(DeltaFileStage.IN_FLIGHT);
        Content content = new Content("file.json", "application/octet-stream", new Segment(UUID.fromString("11111111-1111-1111-1111-111111111114"), 0, 500, did));
        DeltaFileFlow flow = deltaFile.getFlow(UUID_1);
        flow.getInput().setTopics(Set.of(TRANSFORM_TOPIC));
        Action action = flow.getAction("Utf8TransformAction");
        action.complete(START_TIME, STOP_TIME, List.of(content), Map.of(), List.of(), now());
        flow.queueAction("SampleTransformAction", null, ActionType.TRANSFORM, false, now());
        return deltaFile;
    }

    public DeltaFile postTransformUtf8NoSubscriberDeltaFile(UUID did) {
        DeltaFile deltaFile = postTransformUtf8DeltaFile(did);
        Content content = new Content("transformed", "application/octet-stream", new Segment(UUID.fromString("11111111-1111-1111-1111-111111111111"), 0, 500, did));
        DeltaFileFlow flow = deltaFile.getFlow(UUID_1);
        flow.setPublishTopics(List.of(EGRESS_TOPIC));
        Action action = flow.getAction("SampleTransformAction");
        action.complete(START_TIME, STOP_TIME, List.of(content), TRANSFORM_METADATA, List.of(), now());
        Action noSubAction = flow.queueNewAction(NO_SUBSCRIBERS, null, ActionType.PUBLISH, false, now());
        noSubAction.error(now(), now(), now(), NO_SUBSCRIBER_CAUSE, "");
        deltaFile.setStage(DeltaFileStage.ERROR);
        return deltaFile;
    }

    public DeltaFile postResumeNoSubscribersDeltaFile(UUID did) {
        DeltaFile deltaFile = postTransformUtf8NoSubscriberDeltaFile(did);
        deltaFile.resumeErrors(List.of(new ResumeMetadata(TRANSFORM_FLOW_NAME, NO_SUBSCRIBERS, Map.of(), List.of())), now());

        DeltaFileFlow flow = deltaFile.getFlow(UUID_1);
        flow.setState(DeltaFileFlowState.COMPLETE);

        DeltaFileFlow dataSink = deltaFile.addFlow(flowDefinitionService.getOrCreateFlow(DATA_SINK_FLOW_NAME, FlowType.DATA_SINK), flow, now());
        dataSink.getInput().setTopics(Set.of(EGRESS_TOPIC));
        dataSink.queueAction(SAMPLE_EGRESS_ACTION, null, ActionType.EGRESS, false, now());
        deltaFile.setStage(DeltaFileStage.IN_FLIGHT);
        return deltaFile;
    }

    public DeltaFile postCancelDeltaFile(UUID did) {
        DeltaFile deltaFile = postTransformUtf8DeltaFile(did);
        deltaFile.cancel(now());
        return deltaFile;
    }

    public DeltaFile postTransformHadErrorDeltaFile(UUID did) {
        DeltaFile deltaFile = postTransformUtf8DeltaFile(did);
        deltaFile.lastFlow().setPendingActions(List.of(TRANSFORM2.getName()));
        deltaFile.setStage(DeltaFileStage.ERROR);
        DeltaFileFlow flow = deltaFile.getFlow(UUID_1);
        Action action = flow.getAction("SampleTransformAction");
        action.error(START_TIME, STOP_TIME, now(), "transform failed", "message");
        flow.updateState();
        return deltaFile;
    }

    @SuppressWarnings("SameParameterValue")
    public DeltaFile postResumeTransformDeltaFile(UUID did) {
        DeltaFile deltaFile = postTransformHadErrorDeltaFile(did);
        deltaFile.resumeErrors(List.of(new ResumeMetadata(TRANSFORM_FLOW_NAME, "SampleTransformAction", Map.of("AuthorizedBy", "ABC", "anotherKey", "anotherValue"), List.of("removeMe"))), now());
        deltaFile.setStage(DeltaFileStage.IN_FLIGHT);
        DeltaFileFlow flow = deltaFile.getFlow(UUID_1);
        Action action = flow.queueAction("SampleTransformAction", null, ActionType.TRANSFORM, false, now());
        action.setAttempt(2);
        return deltaFile;
    }

    public DeltaFile postTransformDeltaFile(UUID did) {
        DeltaFile deltaFile = withCompleteTransformFlow(did);
        DeltaFileFlow flow = deltaFile.getFlow(UUID_1);

        DeltaFileFlow dataSink = deltaFile.addFlow(flowDefinitionService.getOrCreateFlow(DATA_SINK_FLOW_NAME, FlowType.DATA_SINK), flow, now());
        dataSink.setId(UUID_2);
        dataSink.getInput().setTopics(Set.of(EGRESS_TOPIC));
        dataSink.queueAction(SAMPLE_EGRESS_ACTION, null, ActionType.EGRESS, false, now());
        return deltaFile;
    }

    private DeltaFile withCompleteTransformFlow(UUID did) {
        DeltaFile deltaFile = postTransformUtf8DeltaFile(did);
        deltaFile.setStage(DeltaFileStage.IN_FLIGHT);
        Content content = new Content("transformed", "application/octet-stream", new Segment(UUID.fromString("11111111-1111-1111-1111-111111111111"), 0, 500, did));
        DeltaFileFlow flow = deltaFile.getFlow(UUID_1);
        flow.setState(DeltaFileFlowState.COMPLETE);
        flow.setPublishTopics(List.of(EGRESS_TOPIC));
        flow.getInput().setTopics(Set.of(TRANSFORM_TOPIC));
        Action action = flow.getAction("SampleTransformAction");
        action.complete(START_TIME, STOP_TIME, List.of(content), TRANSFORM_METADATA, List.of(), now());
        return deltaFile;
    }

    public DeltaFile postErrorDeltaFile(UUID did) {
        return postErrorDeltaFile(did, null, null);
    }

    public DeltaFile postErrorDeltaFile(UUID did, String policyName, Integer autoRetryDelay) {
        OffsetDateTime nextAutoResume = autoRetryDelay == null ? null : STOP_TIME.plusSeconds(autoRetryDelay);
        DeltaFile deltaFile = postTransformDeltaFile(did);
        deltaFile.setStage(DeltaFileStage.ERROR);
        DeltaFileFlow flow = deltaFile.getFlow(UUID_2);
        flow.setPendingActions(List.of(EGRESS.getName()));
        flow.setState(DeltaFileFlowState.ERROR);
        Action action = flow.getAction(SAMPLE_EGRESS_ACTION);
        action.error(START_TIME, STOP_TIME, now(),
                "Authority XYZ not recognized", "Dead beef feed face cafe");
        action.setNextAutoResume(nextAutoResume);
        if (policyName != null) {
            action.setNextAutoResumeReason(policyName);
        }
        return deltaFile;
    }

    public DeltaFile postResumeDeltaFile(UUID did) {
        DeltaFile deltaFile = postErrorDeltaFile(did);
        DeltaFileFlow flow = deltaFile.getFlow(UUID_2);
        flow.getActions().getFirst().setState(ActionState.RETRIED);
        flow.firstAction().setMetadata(Map.of("a", "b"));
        Action action = flow.addAction(SAMPLE_EGRESS_ACTION, null, ActionType.EGRESS, QUEUED, now());
        action.setAttempt(2);
        flow.setState(DeltaFileFlowState.IN_FLIGHT);
        deltaFile.setStage(DeltaFileStage.IN_FLIGHT);
        return deltaFile;
    }

    public DeltaFile postTransformDeltaFileInTestMode(UUID did, OffsetDateTime now) {
        DeltaFile deltaFile = withCompleteTransformFlow(did);
        deltaFile.setFiltered(true);
        DeltaFileFlow flow = deltaFile.getFlow(UUID_1);
        flow.setTestMode(true);
        flow.setTestModeReason(TRANSFORM_FLOW_NAME);

        DeltaFileFlow dataSink = deltaFile.addFlow(flowDefinitionService.getOrCreateFlow(DATA_SINK_FLOW_NAME, FlowType.DATA_SINK), flow, now);
        dataSink.getInput().setTopics(Set.of(EGRESS_TOPIC));
        Action action = dataSink.addAction(SYNTHETIC_EGRESS_ACTION_FOR_TEST, null, ActionType.EGRESS, ActionState.FILTERED, now);
        action.setFilteredCause("Filtered by test mode");
        action.setFilteredContext("Filtered by test mode with a reason of - " + TRANSFORM_FLOW_NAME);
        dataSink.setState(DeltaFileFlowState.FILTERED);
        dataSink.setTestMode(true);
        dataSink.setTestModeReason(TRANSFORM_FLOW_NAME);

        deltaFile.setEgressed(false);
        deltaFile.setStage(DeltaFileStage.COMPLETE);
        return deltaFile;
    }

    public DeltaFile postEgressDeltaFile(UUID did) {
        return postEgressDeltaFile(did, null, null);
    }

    public DeltaFile postEgressDeltaFile(UUID did, Set<String> pendingAnnotations, Map<String, String> annotations) {
        DeltaFile deltaFile = postTransformDeltaFile(did);
        deltaFile.setStage(DeltaFileStage.COMPLETE);
        deltaFile.setEgressed(true);
        Content content = new Content("transformed", "application/octet-stream", new Segment(UUID.fromString("11111111-1111-1111-1111-111111111111"), 0, 500, did));
        DeltaFileFlow flow = deltaFile.getFlow(UUID_2);
        Action action = flow.getAction(SAMPLE_EGRESS_ACTION);
        action.complete(START_TIME, STOP_TIME, List.of(content), Map.of(), List.of(), now());
        if (pendingAnnotations != null) {
            deltaFile.setPendingAnnotations("sampleEgress", pendingAnnotations);
            deltaFile.setTerminal(false);
            flow.setState(DeltaFileFlowState.PENDING_ANNOTATIONS);
            flow.setPendingActions(pendingAnnotations.stream().toList());
        } else {
            deltaFile.setTerminal(true);
            flow.setState(DeltaFileFlowState.COMPLETE);
        }
        if (annotations != null) {
            deltaFile.addAnnotations(annotations);
        }
        return deltaFile;
    }
    
    private OffsetDateTime now() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return now.withNano((now.getNano() / 1000) * 1000); // truncate to microseconds
    }
}
