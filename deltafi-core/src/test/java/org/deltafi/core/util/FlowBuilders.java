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
import org.deltafi.core.types.DataSource;

import java.util.List;
import java.util.Set;

import static org.deltafi.core.util.Constants.*;

public class FlowBuilders {
    public static final String TRANSFORM_TOPIC = "transform-topic";
    public static final String EGRESS_TOPIC = "egress-topic";
    public static DataSource buildTimedIngressFlow(FlowState flowState) {
        TimedIngressActionConfiguration tic = new TimedIngressActionConfiguration("SampleTimedIngressAction", "type");

        return buildFlow(TIMED_INGRESS_FLOW_NAME, tic, flowState, false, "*/5 * * * * *", TRANSFORM_TOPIC);
    }

    public static DataSource buildTimedIngressErrorFlow(FlowState flowState) {
        TimedIngressActionConfiguration tic = new TimedIngressActionConfiguration("SampleTimedIngressErrorAction", "type");

        return buildFlow(TIMED_INGRESS_ERROR_FLOW_NAME, tic, flowState, false, "*/5 * * * * *", MISSING_FLOW_NAME);
    }

    public static RestDataSource buildFlow(String topic) {
        RestDataSource restDataSource = new RestDataSource();
        restDataSource.setName("RestDataSource");
        restDataSource.setTopic(topic);
        return restDataSource;
    }

    public static TimedDataSource buildFlow(String name, TimedIngressActionConfiguration timedIngressActionConfiguration, FlowState flowState, boolean testMode, String cronSchedule, String targetFlow) {
        TimedDataSource dataSource = new TimedDataSource();
        dataSource.setName(name);
        dataSource.getFlowStatus().setState(flowState);
        dataSource.setTimedIngressAction(timedIngressActionConfiguration);
        dataSource.setTestMode(testMode);
        dataSource.setCronSchedule(cronSchedule);
        dataSource.setTopic(targetFlow);
        return dataSource;
    }

    public static PublishRules publishRules(String topic) {
        PublishRules publishRules = new PublishRules();
        publishRules.setRules(List.of(new Rule(Set.of(topic))));
        return publishRules;
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

    public static EgressFlow buildRunningEgressFlow(String name, EgressActionConfiguration egressAction, boolean testMode) {
        return buildFlow(name, egressAction, FlowState.RUNNING, testMode);
    }

    public static TransformFlow buildTransformFlow(FlowState flowState) {
        TransformActionConfiguration tc = new TransformActionConfiguration("sampleTransform.Utf8TransformAction", "type");
        TransformActionConfiguration tc2 = new TransformActionConfiguration("sampleTransform.SampleTransformAction", "type");

        return buildFlow(TRANSFORM_FLOW_NAME, List.of(tc, tc2), flowState, false);
    }

    public static TransformFlow buildFlow(String name, List<TransformActionConfiguration> transforms, FlowState flowState, boolean testMode) {
        TransformFlow transformFlow = new TransformFlow();
        transformFlow.setName(name);
        transformFlow.getFlowStatus().setState(flowState);
        transformFlow.setTransformActions(transforms);
        transformFlow.setTestMode(testMode);
        return transformFlow;
    }

    public static TransformFlow buildRunningTransformFlow(String name, List<TransformActionConfiguration> transforms, boolean testMode) {
        return buildFlow(name, transforms, FlowState.RUNNING, testMode);
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
