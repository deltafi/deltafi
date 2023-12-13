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
package org.deltafi.core.util;

import org.deltafi.common.content.Segment;
import org.deltafi.common.types.*;

import java.time.OffsetDateTime;
import java.util.*;

import static org.deltafi.common.types.ActionState.QUEUED;
import static org.deltafi.common.types.DeltaFile.CURRENT_SCHEMA_VERSION;
import static org.deltafi.core.util.Constants.*;

public class FullFlowExemplars {
    public static DeltaFile postIngressDeltaFile(String did) {
        Content content = new Content("name", "application/octet-stream", List.of(new Segment("objectName", 0, 500, did)));
        DeltaFile deltaFile = Util.emptyDeltaFile(did, "flow", List.of(content));
        deltaFile.setIngressBytes(500L);
        deltaFile.queueAction("sampleNormalize", "Utf8TransformAction", ActionType.TRANSFORM, false);
        deltaFile.setSourceInfo(new SourceInfo("input.txt", NORMALIZE_FLOW_NAME, SOURCE_METADATA));
        deltaFile.getActions().get(0).setMetadata(SOURCE_METADATA);
        deltaFile.setSchemaVersion(DeltaFile.CURRENT_SCHEMA_VERSION);
        return deltaFile;
    }

    public static DeltaFile postTransformUtf8DeltaFile(String did) {
        DeltaFile deltaFile = postIngressDeltaFile(did);
        Content content = new Content("file.json", "application/octet-stream", new Segment("utf8ObjectName", 0, 500, did));
        deltaFile.completeAction(NORMALIZE_FLOW_NAME, "Utf8TransformAction", START_TIME, STOP_TIME, List.of(content), Map.of("deleteMe", "soon"), List.of());
        deltaFile.queueAction(NORMALIZE_FLOW_NAME, "SampleTransformAction", ActionType.TRANSFORM, false);
        return deltaFile;
    }

    public static DeltaFile postTransformDeltaFile(String did) {
        DeltaFile deltaFile = postTransformUtf8DeltaFile(did);
        Content content = new Content("transformed", "application/octet-stream", new Segment("objectName", 0, 500, did));
        deltaFile.completeAction(NORMALIZE_FLOW_NAME, "SampleTransformAction", START_TIME, STOP_TIME, List.of(content), TRANSFORM_METADATA, List.of("deleteMe"));
        deltaFile.queueAction(NORMALIZE_FLOW_NAME, "SampleEgressAction", ActionType.EGRESS, false);
        deltaFile.addAnnotations(Map.of("transformKey", "transformValue"));
        return deltaFile;
    }

    public static DeltaFile postTransformHadErrorDeltaFile(String did) {
        DeltaFile deltaFile = postTransformUtf8DeltaFile(did);
        deltaFile.setStage(DeltaFileStage.ERROR);
        deltaFile.errorAction(NORMALIZE_FLOW_NAME, "SampleTransformAction", START_TIME, STOP_TIME, "transform failed", "message");
        return deltaFile;
    }

    @SuppressWarnings("SameParameterValue")
    public static DeltaFile postResumeTransformDeltaFile(String did) {
        DeltaFile deltaFile = postTransformHadErrorDeltaFile(did);
        deltaFile.retryErrors(List.of(new ResumeMetadata(NORMALIZE_FLOW_NAME, "SampleTransformAction", Map.of("AuthorizedBy", "ABC", "anotherKey", "anotherValue"), List.of("removeMe"))));
        deltaFile.setStage(DeltaFileStage.IN_FLIGHT);
        deltaFile.getActions().add(Action.builder().flow(NORMALIZE_FLOW_NAME).name("SampleTransformAction").type(ActionType.TRANSFORM).state(QUEUED).attempt(2).build());
        return deltaFile;
    }
/*
    public static DeltaFile postEnrichDeltaFileWithUnicodeAnnotation(String did) {
        DeltaFile deltaFile = postEnrichDeltaFile(did);
        deltaFile.addAnnotationIfAbsent("āȂ", "̃Є");
        return deltaFile;
    }

    public static DeltaFile postEnrichInvalidDeltaFile(String did) {
        DeltaFile deltaFile = postDomainDeltaFile(did);
        deltaFile.setStage(DeltaFileStage.ERROR);
        deltaFile.errorAction("sampleEnrich", "SampleEnrichAction", START_TIME, STOP_TIME,
                INVALID_ACTION_EVENT_RECEIVED, "STARTS:Action event type does not match the populated object");
        return deltaFile;
    }*/

    /*public static DeltaFile postFormatHadErrorDeltaFile(String did) {
        DeltaFile deltaFile = postEnrichDeltaFile(did);
        deltaFile.setStage(DeltaFileStage.ERROR);
        deltaFile.errorAction("sampleEgress", "SampleFormatAction", START_TIME, STOP_TIME, "format failed", "message");
        return deltaFile;
    }

    public static DeltaFile postResumeFormatDeltaFile(String did) {
        DeltaFile deltaFile = postFormatHadErrorDeltaFile(did);
        deltaFile.retryErrors(List.of(new ResumeMetadata("sampleEgress", "SampleFormatAction", Map.of("a", "b"), List.of("loadSampleVersion"))));
        deltaFile.setStage(DeltaFileStage.EGRESS);
        deltaFile.getActions().add(Action.builder().flow("sampleEgress").name("SampleFormatAction").state(QUEUED).attempt(2).build());
        return deltaFile;
    }*/

    public static DeltaFile postErrorDeltaFile(String did) {
        return postErrorDeltaFile(did, null, null);
    }

    public static DeltaFile postErrorDeltaFile(String did, String policyName, Integer autoRetryDelay) {
        OffsetDateTime nextAutoResume = autoRetryDelay == null ? null : STOP_TIME.plusSeconds(autoRetryDelay);
        DeltaFile deltaFile = postTransformDeltaFile(did);
        deltaFile.setStage(DeltaFileStage.ERROR);
        deltaFile.errorAction("sampleEgress", "AuthorityValidateAction", START_TIME, STOP_TIME,
                "Authority XYZ not recognized", "Dead beef feed face cafe", nextAutoResume);
        if (policyName != null) {
            deltaFile.setNextAutoResumeReason(policyName);
        }
        return deltaFile;
    }

    public static DeltaFile postResumeDeltaFile(String did, String flow, String retryAction, ActionType actionType) {
        DeltaFile deltaFile = postErrorDeltaFile(did);
        deltaFile.retryErrors(List.of(new ResumeMetadata("sampleEgress", "AuthorityValidateAction", Map.of("a", "b"), Collections.emptyList())));
        deltaFile.setStage(DeltaFileStage.IN_FLIGHT);
        deltaFile.getActions().add(Action.builder().flow(flow).name(retryAction).type(actionType).state(QUEUED).attempt(2).build());
        return deltaFile;
    }

    public static DeltaFile postCancelDeltaFile(String did) {
        DeltaFile deltaFile = postTransformUtf8DeltaFile(did);
        deltaFile.setStage(DeltaFileStage.CANCELLED);
        deltaFile.cancelQueuedActions();
        return deltaFile;
    }

    public static DeltaFile postTransformDeltaFileInTestMode(String did, String flow, String expectedEgressActionName) {
        DeltaFile deltaFile = postTransformDeltaFile(did);
        deltaFile.setStage(DeltaFileStage.COMPLETE);
        deltaFile.queueAction(flow, expectedEgressActionName, ActionType.EGRESS, false);
        deltaFile.completeAction(flow, expectedEgressActionName, START_TIME, STOP_TIME);
        deltaFile.completeAction(flow, "SampleTransformAction", START_TIME, STOP_TIME);
        deltaFile.setTestModeReason(expectedEgressActionName);
        deltaFile.setEgressed(false);
        return deltaFile;
    }


    public static DeltaFile postEgressDeltaFile(String did) {
        DeltaFile deltaFile = postTransformDeltaFile(did);
        deltaFile.setStage(DeltaFileStage.COMPLETE);
        deltaFile.setEgressed(true);
        deltaFile.completeAction("sampleEgress", "SampleEgressAction", START_TIME, STOP_TIME, List.of(new Content("output.txt", "application/octet-stream", new Segment("formattedObjectName", 0, 1000, did))), Map.of("key1", "value1", "key2", "value2"), List.of());
        deltaFile.addEgressFlow(EGRESS_FLOW_NAME);
        return deltaFile;
    }

    public static DeltaFile transformFlowPostIngressDeltaFile(String did) {
        Content content = new Content("name", "application/octet-stream", new Segment("objectName", 0, 500, did));
        DeltaFile deltaFile = Util.emptyDeltaFile(did, "flow", List.of(content));
        deltaFile.setIngressBytes(500L);
        deltaFile.queueAction("sampleTransform", "Utf8TransformAction", ActionType.TRANSFORM, false);
        deltaFile.setSourceInfo(new SourceInfo("input.txt", TRANSFORM_FLOW_NAME, SOURCE_METADATA));
        deltaFile.setSchemaVersion(CURRENT_SCHEMA_VERSION);
        return deltaFile;
    }

    public static DeltaFile transformFlowPostTransformUtf8DeltaFile(String did) {
        DeltaFile deltaFile = transformFlowPostIngressDeltaFile(did);
        deltaFile.setStage(DeltaFileStage.IN_FLIGHT);
        Content content = new Content("file.json", "application/octet-stream", new Segment("utf8ObjectName", 0, 500, did));
        deltaFile.completeAction("sampleTransform", "Utf8TransformAction", START_TIME, STOP_TIME, List.of(content), Map.of(), List.of());
        deltaFile.queueAction("sampleTransform", "SampleTransformAction", ActionType.TRANSFORM, false);
        return deltaFile;
    }

    public static DeltaFile transformFlowPostTransformDeltaFile(String did) {
        DeltaFile deltaFile = transformFlowPostTransformUtf8DeltaFile(did);
        deltaFile.setStage(DeltaFileStage.IN_FLIGHT);
        Content content = new Content("transformed", "application/octet-stream", new Segment("objectName", 0, 500, did));
        deltaFile.completeAction("sampleTransform", "SampleTransformAction", START_TIME, STOP_TIME, List.of(content), TRANSFORM_METADATA, List.of());
        deltaFile.queueAction("sampleTransform", "SampleEgressAction", ActionType.EGRESS, false);
        deltaFile.addEgressFlow(TRANSFORM_FLOW_NAME);
        return deltaFile;
    }

    public static DeltaFile transformFlowPostEgressDeltaFile(String did) {
        DeltaFile deltaFile = transformFlowPostTransformDeltaFile(did);
        deltaFile.setStage(DeltaFileStage.COMPLETE);
        deltaFile.setEgressed(true);
        Content content = new Content("transformed", "application/octet-stream", new Segment("objectName", 0, 500, did));
        deltaFile.completeAction("sampleTransform", "SampleEgressAction", START_TIME, STOP_TIME, List.of(content), deltaFile.getMetadata(), List.of());
        return deltaFile;
    }
}
