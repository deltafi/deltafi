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

import java.util.List;

import static org.deltafi.core.util.Constants.*;

public class FlowBuilders {
    public static TimedIngressFlow buildTimedIngressFlow(FlowState flowState) {
        TimedIngressActionConfiguration tic = new TimedIngressActionConfiguration("SampleTimedIngressAction", "type");

        return buildFlow(TIMED_INGRESS_FLOW_NAME, tic, flowState, false, "*/5 * * * * *", NORMALIZE_FLOW_NAME);
    }

    public static TimedIngressFlow buildTimedIngressErrorFlow(FlowState flowState) {
        TimedIngressActionConfiguration tic = new TimedIngressActionConfiguration("SampleTimedIngressErrorAction", "type");

        return buildFlow(TIMED_INGRESS_ERROR_FLOW_NAME, tic, flowState, false, "*/5 * * * * *", MISSING_FLOW_NAME);
    }

    public static TimedIngressFlow buildFlow(String name, TimedIngressActionConfiguration timedIngressActionConfiguration, FlowState flowState, boolean testMode, String cronSchedule, String targetFlow) {
        TimedIngressFlow timedIngressFlow = new TimedIngressFlow();
        timedIngressFlow.setName(name);
        timedIngressFlow.getFlowStatus().setState(flowState);
        timedIngressFlow.setTimedIngressAction(timedIngressActionConfiguration);
        timedIngressFlow.setTestMode(testMode);
        timedIngressFlow.setCronSchedule(cronSchedule);
        timedIngressFlow.setTargetFlow(targetFlow);
        return timedIngressFlow;
    }

    public static EgressFlow buildEgressFlow(FlowState flowState) {
        EgressActionConfiguration sampleEgress = new EgressActionConfiguration("SampleEgressAction", "type");

        return buildFlow(EGRESS_FLOW_NAME, sampleEgress, flowState, false);
    }

    public static EgressFlow buildFlow(String name, EgressActionConfiguration egressAction, FlowState flowState, boolean testMode) {
        EgressFlow egressFlow = new EgressFlow();
        egressFlow.setName(name);
        egressFlow.setEgressAction(egressAction);
        egressFlow.getFlowStatus().setState(flowState);
        egressFlow.setTestMode(testMode);
        return egressFlow;
    }

    public static EgressFlow buildRunningFlow(String name, EgressActionConfiguration egressAction, boolean testMode) {
        return buildFlow(name, egressAction, FlowState.RUNNING, testMode);
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

    public static TransformFlow buildTransformFlow(String name, String groupId, String artifactId, String version) {
        PluginCoordinates pluginCoordinates = PluginCoordinates.builder()
                .groupId(groupId).artifactId(artifactId).version(version).build();
        return buildTransformFlow(name, pluginCoordinates);
    }

    public static TransformFlow buildTransformFlow(String name, PluginCoordinates pluginCoordinates) {
        TransformFlow transformFlow = new TransformFlow();
        transformFlow.setName(name);
        transformFlow.setSourcePlugin(pluginCoordinates);
        transformFlow.migrate();
        return transformFlow;
    }

    public static TransformFlowPlan buildTransformFlowPlan(String name, String groupId, String artifactId, String version) {
        PluginCoordinates pluginCoordinates = PluginCoordinates.builder()
                .groupId(groupId).artifactId(artifactId).version(version).build();
        return buildTransformFlowPlan(name, pluginCoordinates);
    }

    public static TransformFlowPlan buildTransformFlowPlan(String name, PluginCoordinates pluginCoordinates) {
        TransformFlowPlan transformFlowPlan = new TransformFlowPlan(name, FlowType.TRANSFORM, "desc");
        transformFlowPlan.setSourcePlugin(pluginCoordinates);
        return transformFlowPlan;
    }
}
