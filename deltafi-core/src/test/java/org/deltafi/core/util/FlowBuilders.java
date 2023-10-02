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

import org.deltafi.common.types.*;
import org.deltafi.core.generated.types.FlowState;
import org.deltafi.core.types.*;

import java.time.Duration;
import java.util.List;

import static org.deltafi.core.util.Constants.*;

public class FlowBuilders {
    public static TimedIngressFlow buildTimedIngressFlow(FlowState flowState) {
        TimedIngressActionConfiguration tic = new TimedIngressActionConfiguration("SampleTimedIngressAction", "type");

        return buildFlow(TIMED_INGRESS_FLOW_NAME, tic, flowState, false, Duration.ofSeconds(5), NORMALIZE_FLOW_NAME);
    }

    public static TimedIngressFlow buildTimedIngressErrorFlow(FlowState flowState) {
        TimedIngressActionConfiguration tic = new TimedIngressActionConfiguration("SampleTimedIngressErrorAction", "type");

        return buildFlow(TIMED_INGRESS_ERROR_FLOW_NAME, tic, flowState, false, Duration.ofSeconds(5), MISSING_FLOW_NAME);
    }

    public static NormalizeFlow buildNormalizeFlow(FlowState flowState) {
        LoadActionConfiguration lc = new LoadActionConfiguration("SampleLoadAction", "type");
        TransformActionConfiguration tc = new TransformActionConfiguration("Utf8TransformAction", "type");
        TransformActionConfiguration tc2 = new TransformActionConfiguration("SampleTransformAction", "type");

        return buildFlow(NORMALIZE_FLOW_NAME, lc, List.of(tc, tc2), flowState, false);
    }

    public static NormalizeFlow buildRunningFlow(String name, LoadActionConfiguration loadActionConfiguration, List<TransformActionConfiguration> transforms, boolean testMode) {
        return buildFlow(name, loadActionConfiguration, transforms, FlowState.RUNNING, testMode);
    }

    public static TimedIngressFlow buildFlow(String name, TimedIngressActionConfiguration timedIngressActionConfiguration, FlowState flowState, boolean testMode, Duration interval, String targetFlow) {
        TimedIngressFlow timedIngressFlow = new TimedIngressFlow();
        timedIngressFlow.setName(name);
        timedIngressFlow.getFlowStatus().setState(flowState);
        timedIngressFlow.setTimedIngressAction(timedIngressActionConfiguration);
        timedIngressFlow.setTestMode(testMode);
        timedIngressFlow.setInterval(interval);
        timedIngressFlow.setTargetFlow(targetFlow);
        return timedIngressFlow;
    }

    public static NormalizeFlow buildFlow(String name, LoadActionConfiguration loadActionConfiguration, List<TransformActionConfiguration> transforms, FlowState flowState, boolean testMode) {
        NormalizeFlow normalizeFlow = new NormalizeFlow();
        normalizeFlow.setName(name);
        normalizeFlow.getFlowStatus().setState(flowState);
        normalizeFlow.setLoadAction(loadActionConfiguration);
        normalizeFlow.setTransformActions(transforms);
        normalizeFlow.setTestMode(testMode);
        return normalizeFlow;
    }

    public static EgressFlow buildEgressFlow(FlowState flowState) {
        FormatActionConfiguration sampleFormat = new FormatActionConfiguration("SampleFormatAction", "type", List.of("sampleDomain"));
        sampleFormat.setRequiresEnrichments(List.of("sampleEnrichment"));

        EgressActionConfiguration sampleEgress = new EgressActionConfiguration("SampleEgressAction", "type");

        return buildFlow(EGRESS_FLOW_NAME, sampleFormat, sampleEgress, flowState, false);
    }

    public static EnrichFlow buildEnrichFlow(FlowState flowState) {
        EnrichFlow enrichFlow = new EnrichFlow();
        enrichFlow.setName("sampleEnrich");

        DomainActionConfiguration sampleDomain = new DomainActionConfiguration("SampleDomainAction", "SampleDomainType", List.of("sampleDomain"));

        EnrichActionConfiguration sampleEnrich = new EnrichActionConfiguration("SampleEnrichAction", "type", List.of("sampleDomain"));
        sampleEnrich.setRequiresMetadataKeyValues(List.of(new KeyValue("loadSampleType", "load-sample-type")));

        enrichFlow.setDomainActions(List.of(sampleDomain));
        enrichFlow.setEnrichActions(List.of(sampleEnrich));
        enrichFlow.getFlowStatus().setState(flowState);
        return enrichFlow;
    }

    public static EgressFlow buildRunningFlow(String name, FormatActionConfiguration formatAction, EgressActionConfiguration egressAction, boolean testMode) {
        return buildFlow(name, formatAction, egressAction, FlowState.RUNNING, testMode);
    }

    public static EgressFlow buildFlow(String name, FormatActionConfiguration formatAction, EgressActionConfiguration egressAction, FlowState flowState, boolean testMode) {
        EgressFlow egressFlow = new EgressFlow();
        egressFlow.setName(name);
        egressFlow.setFormatAction(formatAction);
        egressFlow.setEgressAction(egressAction);
        egressFlow.setIncludeNormalizeFlows(null);
        egressFlow.getFlowStatus().setState(flowState);
        egressFlow.setTestMode(testMode);
        return egressFlow;
    }

    public static TransformFlow buildTransformFlow(FlowState flowState) {
        EgressActionConfiguration lc = new EgressActionConfiguration("sampleTransform.SampleEgressAction", "type");
        TransformActionConfiguration tc = new TransformActionConfiguration("sampleTransform.Utf8TransformAction", "type");
        TransformActionConfiguration tc2 = new TransformActionConfiguration("sampleTransform.SampleTransformAction", "type");

        return buildFlow(TRANSFORM_FLOW_NAME, lc, List.of(tc, tc2), flowState, false);
    }

    public static TransformFlow buildFlow(String name, EgressActionConfiguration egressActionConfiguration, List<TransformActionConfiguration> transforms, FlowState flowState, boolean testMode) {
        TransformFlow transformFlow = new TransformFlow();
        transformFlow.setName(name);
        transformFlow.getFlowStatus().setState(flowState);
        transformFlow.setEgressAction(egressActionConfiguration);
        transformFlow.setTransformActions(transforms);
        transformFlow.setTestMode(testMode);
        return transformFlow;
    }

    public static TransformFlow buildRunningFlow(String name, EgressActionConfiguration egressActionConfiguration, List<TransformActionConfiguration> transforms, boolean testMode) {
        return buildFlow(name, egressActionConfiguration, transforms, FlowState.RUNNING, testMode);
    }

    public static NormalizeFlow buildNormalizeFlow(String name, String groupId, String artifactId, String version) {
        PluginCoordinates pluginCoordinates = PluginCoordinates.builder()
                .groupId(groupId).artifactId(artifactId).version(version).build();
        return buildNormalizeFlow(name, pluginCoordinates);
    }

    public static NormalizeFlow buildNormalizeFlow(String name, PluginCoordinates pluginCoordinates) {
        NormalizeFlow normalizeFlow = new NormalizeFlow();
        normalizeFlow.setName(name);
        normalizeFlow.setSourcePlugin(pluginCoordinates);
        normalizeFlow.migrate();
        return normalizeFlow;
    }

    public static NormalizeFlowPlan buildNormalizeFlowPlan(String name, String groupId, String artifactId, String version) {
        PluginCoordinates pluginCoordinates = PluginCoordinates.builder()
                .groupId(groupId).artifactId(artifactId).version(version).build();
        return buildNormalizeFlowPlan(name, pluginCoordinates);
    }

    public static NormalizeFlowPlan buildNormalizeFlowPlan(String name, PluginCoordinates pluginCoordinates) {
        NormalizeFlowPlan normalizeFlowPlan = new NormalizeFlowPlan(name, FlowType.NORMALIZE, "desc");
        normalizeFlowPlan.setSourcePlugin(pluginCoordinates);
        return normalizeFlowPlan;
    }
}
