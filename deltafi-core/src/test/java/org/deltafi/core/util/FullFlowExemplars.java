/**
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

import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.common.content.Segment;
import org.deltafi.common.types.*;
import org.deltafi.core.services.DeltaFilesService;

import java.time.OffsetDateTime;
import java.util.*;

import static org.deltafi.common.constant.DeltaFiConstants.INVALID_ACTION_EVENT_RECEIVED;
import static org.deltafi.common.types.ActionState.QUEUED;
import static org.deltafi.core.util.Constants.*;

public class FullFlowExemplars {
    public static DeltaFile postIngressDeltaFile(String did) {
        Content content = new Content("name", "application/octet-stream", List.of(new Segment("objectName", 0, 500, did)));
        DeltaFile deltaFile = Util.emptyDeltaFile(did, "flow", List.of(content));
        deltaFile.setIngressBytes(500L);
        deltaFile.queueAction("sampleIngress.Utf8TransformAction", ActionType.TRANSFORM);
        deltaFile.setSourceInfo(new SourceInfo("input.txt", INGRESS_FLOW_NAME, SOURCE_METADATA));
        return deltaFile;
    }

    public static DeltaFile postTransformUtf8DeltaFile(String did) {
        DeltaFile deltaFile = postIngressDeltaFile(did);
        deltaFile.setStage(DeltaFileStage.INGRESS);
        Content content = new Content("file.json", "application/octet-stream", new Segment("utf8ObjectName", 0, 500, did));
        deltaFile.completeAction("sampleIngress.Utf8TransformAction", START_TIME, STOP_TIME, List.of(content), Map.of("deleteMe", "soon"));
        deltaFile.queueAction("sampleIngress.SampleTransformAction", ActionType.TRANSFORM);
        return deltaFile;
    }

    public static DeltaFile postTransformDeltaFile(String did) {
        DeltaFile deltaFile = postTransformUtf8DeltaFile(did);
        deltaFile.setStage(DeltaFileStage.INGRESS);
        Content content = new Content("transformed", "application/octet-stream", new Segment("objectName", 0, 500, did));
        deltaFile.completeAction("sampleIngress.SampleTransformAction", START_TIME, STOP_TIME, List.of(content), TRANSFORM_METADATA, List.of("deleteMe"));
        deltaFile.queueAction("sampleIngress.SampleLoadAction", ActionType.LOAD);
        deltaFile.addAnnotations(Map.of("transformKey", "transformValue"));
        return deltaFile;
    }

    public static DeltaFile postTransformHadErrorDeltaFile(String did) {
        DeltaFile deltaFile = postTransformUtf8DeltaFile(did);
        deltaFile.setStage(DeltaFileStage.ERROR);
        deltaFile.errorAction("sampleIngress.SampleTransformAction", START_TIME, STOP_TIME, "transform failed", "message");
        return deltaFile;
    }

    @SuppressWarnings("SameParameterValue")
    public static DeltaFile postResumeTransformDeltaFile(String did, String retryAction) {
        DeltaFile deltaFile = postTransformHadErrorDeltaFile(did);
        deltaFile.retryErrors();
        deltaFile.setStage(DeltaFileStage.INGRESS);
        deltaFile.getActions().add(Action.newBuilder().name(retryAction).state(QUEUED).attempt(2).build());
        deltaFile.getSourceInfo().setMetadata(Map.of("AuthorizedBy", "ABC", "removeMe.original", "whatever", "AuthorizedBy.original", "XYZ", "anotherKey", "anotherValue"));
        return deltaFile;
    }

    public static DeltaFile postLoadDeltaFile(String did) {
        DeltaFile deltaFile = postTransformDeltaFile(did);
        deltaFile.setStage(DeltaFileStage.ENRICH);
        deltaFile.queueAction("sampleEnrich.SampleDomainAction", ActionType.DOMAIN);
        Content content = new Content("load-content", "application/octet-stream", new Segment("objectName", 0, 500, did));
        deltaFile.completeAction("sampleIngress.SampleLoadAction", START_TIME, STOP_TIME, List.of(content), LOAD_METADATA);
        deltaFile.addDomain("sampleDomain", "sampleDomainValue", "application/octet-stream");
        deltaFile.addAnnotations(Map.of("loadKey", "loadValue"));
        return deltaFile;
    }

    public static DeltaFile postMissingEnrichDeltaFile(String did) {
        DeltaFile deltaFile = postLoadDeltaFile(did);
        deltaFile.completeAction("sampleEnrich.SampleDomainAction", START_TIME, STOP_TIME);
        deltaFile.addAnnotations(Map.of("domainKey", "domain metadata"));
        deltaFile.setStage(DeltaFileStage.ERROR);
        deltaFile.queueNewAction(DeltaFiConstants.NO_EGRESS_FLOW_CONFIGURED_ACTION, ActionType.UNKNOWN);
        deltaFile.errorAction(DeltaFilesService.buildNoEgressConfiguredErrorEvent(deltaFile, OffsetDateTime.now()));
        deltaFile.addDomain("sampleDomain", "sampleDomainValue", "application/octet-stream");
        deltaFile.getLastDataAmendedAction().setMetadata(LOAD_WRONG_METADATA);
        return deltaFile;
    }

    public static DeltaFile postDomainDeltaFile(String did) {
        DeltaFile deltaFile = postLoadDeltaFile(did);
        deltaFile.setStage(DeltaFileStage.ENRICH);
        deltaFile.queueAction("sampleEnrich.SampleEnrichAction", ActionType.ENRICH);
        deltaFile.completeAction("sampleEnrich.SampleDomainAction", START_TIME, STOP_TIME);
        deltaFile.addAnnotations(Map.of("domainKey", "domain metadata"));
        return deltaFile;
    }

    public static DeltaFile postEnrichDeltaFile(String did) {
        DeltaFile deltaFile = postDomainDeltaFile(did);
        deltaFile.setStage(DeltaFileStage.EGRESS);
        deltaFile.queueAction("sampleEgress.SampleFormatAction", ActionType.FORMAT);
        deltaFile.completeAction("sampleEnrich.SampleEnrichAction", START_TIME, STOP_TIME);
        deltaFile.addEnrichment("sampleEnrichment", "enrichmentData");
        deltaFile.addAnnotations(Map.of("first", "one", "second", "two"));
        deltaFile.addEgressFlow(EGRESS_FLOW_NAME);
        return deltaFile;
    }

    public static DeltaFile postEnrichInvalidDeltaFile(String did) {
        DeltaFile deltaFile = postDomainDeltaFile(did);
        deltaFile.setStage(DeltaFileStage.ERROR);
        deltaFile.errorAction("sampleEnrich.SampleEnrichAction", START_TIME, STOP_TIME,
                INVALID_ACTION_EVENT_RECEIVED, "STARTS:Action event type does not match the populated object");
        return deltaFile;
    }

    public static DeltaFile postFormatDeltaFile(String did) {
        DeltaFile deltaFile = postEnrichDeltaFile(did);
        deltaFile.setStage(DeltaFileStage.EGRESS);
        deltaFile.queueAction("sampleEgress.AuthorityValidateAction", ActionType.VALIDATE);
        deltaFile.queueAction("sampleEgress.SampleValidateAction", ActionType.VALIDATE);
        deltaFile.completeAction("sampleEgress.SampleFormatAction", START_TIME, STOP_TIME);
        deltaFile.getFormattedData().add(FormattedData.newBuilder()
                .formatAction("sampleEgress.SampleFormatAction")
                .metadata(Map.of("key1", "value1", "key2", "value2"))
                .content(new Content("output.txt", "application/octet-stream", new Segment("formattedObjectName", 0, 1000, did)))
                .egressActions(Collections.singletonList("sampleEgress.SampleEgressAction"))
                .validateActions(List.of("sampleEgress.AuthorityValidateAction", "sampleEgress.SampleValidateAction"))
                .build());
        return deltaFile;
    }

    public static DeltaFile postValidateDeltaFile(String did) {
        DeltaFile deltaFile = postFormatDeltaFile(did);
        deltaFile.setStage(DeltaFileStage.EGRESS);
        deltaFile.completeAction("sampleEgress.SampleValidateAction", START_TIME, STOP_TIME);
        return deltaFile;
    }

    public static DeltaFile postErrorDeltaFile(String did) {
        return postErrorDeltaFile(did, null, null);
    }

    public static DeltaFile postErrorDeltaFile(String did, String policyName, Integer autoRetryDelay) {
        OffsetDateTime nextAutoResume = autoRetryDelay == null ? null : STOP_TIME.plusSeconds(autoRetryDelay);
        DeltaFile deltaFile = postValidateDeltaFile(did);
        deltaFile.setStage(DeltaFileStage.ERROR);
        deltaFile.errorAction(
                "sampleEgress.AuthorityValidateAction",
                START_TIME,
                STOP_TIME,
                "Authority XYZ not recognized",
                "Dead beef feed face cafe",
                nextAutoResume);
        if (policyName != null) {
            deltaFile.setNextAutoResumeReason(policyName);
        }
        return deltaFile;
    }

    public static DeltaFile postResumeDeltaFile(String did, String retryAction) {
        DeltaFile deltaFile = postErrorDeltaFile(did);
        deltaFile.retryErrors();
        deltaFile.setStage(DeltaFileStage.EGRESS);
        deltaFile.getActions().add(Action.newBuilder().name(retryAction).state(QUEUED).attempt(2).build());
        deltaFile.getSourceInfo().addMetadata("a", "b");
        return deltaFile;
    }

    public static DeltaFile postCancelDeltaFile(String did) {
        DeltaFile deltaFile = postTransformUtf8DeltaFile(did);
        deltaFile.setStage(DeltaFileStage.CANCELLED);
        deltaFile.cancelQueuedActions();
        return deltaFile;
    }

    public static DeltaFile postValidateAuthorityDeltaFile(String did) {
        DeltaFile deltaFile = postValidateDeltaFile(did);
        deltaFile.setStage(DeltaFileStage.EGRESS);
        deltaFile.queueAction("sampleEgress.SampleEgressAction", ActionType.EGRESS);
        deltaFile.completeAction("sampleEgress.AuthorityValidateAction", START_TIME, STOP_TIME);
        return deltaFile;
    }

    public static DeltaFile postValidateAuthorityDeltaFileInTestMode(String did, String expectedEgressActionName) {
        DeltaFile deltaFile = postValidateDeltaFile(did);
        deltaFile.setStage(DeltaFileStage.COMPLETE);
        deltaFile.queueAction(expectedEgressActionName, ActionType.EGRESS);
        deltaFile.completeAction(expectedEgressActionName, START_TIME, STOP_TIME);
        deltaFile.completeAction("sampleEgress.AuthorityValidateAction", START_TIME, STOP_TIME);
        deltaFile.setTestModeReason(expectedEgressActionName);
        deltaFile.setEgressed(false);
        return deltaFile;
    }

    public static DeltaFile postEgressDeltaFile(String did) {
        DeltaFile deltaFile = postValidateAuthorityDeltaFile(did);
        deltaFile.setStage(DeltaFileStage.COMPLETE);
        deltaFile.setEgressed(true);
        deltaFile.completeAction("sampleEgress.SampleEgressAction", START_TIME, STOP_TIME);
        deltaFile.addEgressFlow(EGRESS_FLOW_NAME);
        return deltaFile;
    }

    public static DeltaFile transformFlowPostIngressDeltaFile(String did) {
        Content content = new Content("name", "application/octet-stream", new Segment("objectName", 0, 500, did));
        DeltaFile deltaFile = Util.emptyDeltaFile(did, "flow", List.of(content));
        deltaFile.setIngressBytes(500L);
        deltaFile.queueAction("sampleTransform.Utf8TransformAction", ActionType.TRANSFORM);
        deltaFile.setSourceInfo(new SourceInfo("input.txt", TRANSFORM_FLOW_NAME, SOURCE_METADATA, ProcessingType.TRANSFORMATION));
        return deltaFile;
    }

    public static DeltaFile transformFlowPostTransformUtf8DeltaFile(String did) {
        DeltaFile deltaFile = transformFlowPostIngressDeltaFile(did);
        deltaFile.setStage(DeltaFileStage.INGRESS);
        Content content = new Content("file.json", "application/octet-stream", new Segment("utf8ObjectName", 0, 500, did));
        deltaFile.completeAction("sampleTransform.Utf8TransformAction", START_TIME, STOP_TIME, List.of(content));
        deltaFile.queueAction("sampleTransform.SampleTransformAction", ActionType.TRANSFORM);
        return deltaFile;
    }

    public static DeltaFile transformFlowPostTransformDeltaFile(String did) {
        DeltaFile deltaFile = transformFlowPostTransformUtf8DeltaFile(did);
        deltaFile.setStage(DeltaFileStage.EGRESS);
        Content content = new Content("transformed", "application/octet-stream", new Segment("objectName", 0, 500, did));
        deltaFile.completeAction("sampleTransform.SampleTransformAction", START_TIME, STOP_TIME, List.of(content), TRANSFORM_METADATA);
        deltaFile.queueAction("sampleTransform.SampleEgressAction", ActionType.EGRESS);
        deltaFile.addEgressFlow(TRANSFORM_FLOW_NAME);
        return deltaFile;
    }

    public static DeltaFile transformFlowPostEgressDeltaFile(String did) {
        DeltaFile deltaFile = transformFlowPostTransformDeltaFile(did);
        deltaFile.setStage(DeltaFileStage.COMPLETE);
        deltaFile.setEgressed(true);
        deltaFile.completeAction("sampleTransform.SampleEgressAction", START_TIME, STOP_TIME);
        return deltaFile;
    }
}
