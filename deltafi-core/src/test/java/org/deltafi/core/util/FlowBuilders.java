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
import org.deltafi.core.generated.types.FlowStatus;
import org.deltafi.core.types.*;

import java.util.List;

import static org.deltafi.core.datafetchers.FlowPlanDatafetcherTestHelper.PLUGIN_COORDINATES;
import static org.deltafi.core.util.Constants.*;

public class FlowBuilders {
    public static final String TRANSFORM_TOPIC = "transform-topic";
    public static final String EGRESS_TOPIC = "egress-topic";
    public static RestDataSource buildRestDataSource(FlowState flowState) {
        RestDataSource dataSource = new RestDataSource();
        dataSource.setName(REST_DATA_SOURCE_NAME);
        dataSource.getFlowStatus().setState(flowState);
        dataSource.setTestMode(false);
        dataSource.setTopic(TRANSFORM_TOPIC);
        dataSource.setSourcePlugin(PLUGIN_COORDINATES);
        return dataSource;
    }

    public static TimedDataSource buildTimedDataSource(FlowState flowState) {
        ActionConfiguration tic = new ActionConfiguration("SampleTimedIngressAction", ActionType.TIMED_INGRESS, "type");

        return buildDataSource(TIMED_DATA_SOURCE_NAME, tic, flowState, false, "*/5 * * * * *", TRANSFORM_TOPIC);
    }

    public static TimedDataSource buildTimedDataSourceError(FlowState flowState) {
        ActionConfiguration tic = new ActionConfiguration("SampleTimedIngressErrorAction", ActionType.TIMED_INGRESS, "type");

        return buildDataSource(TIMED_DATA_SOURCE_ERROR_NAME, tic, flowState, false, "*/5 * * * * *", MISSING_PUBLISH_TOPIC);
    }

    public static RestDataSource buildDataSource(String topic) {
        RestDataSource restDataSource = new RestDataSource();
        restDataSource.setName("RestDataSource");
        restDataSource.setTopic(topic);
        restDataSource.setSourcePlugin(PLUGIN_COORDINATES);
        restDataSource.setFlowStatus(FlowStatus.newBuilder().state(FlowState.RUNNING).build());
        return restDataSource;
    }

    public static TimedDataSource buildDataSource(String name, ActionConfiguration ActionConfiguration, FlowState flowState, boolean testMode, String cronSchedule, String targetFlow) {
        TimedDataSource dataSource = new TimedDataSource();
        dataSource.setName(name);
        dataSource.getFlowStatus().setState(flowState);
        dataSource.setTimedIngressAction(ActionConfiguration);
        dataSource.setTestMode(testMode);
        dataSource.setCronSchedule(cronSchedule);
        dataSource.setTopic(targetFlow);
        dataSource.setSourcePlugin(PLUGIN_COORDINATES);
        return dataSource;
    }

    public static PublishRules publishRules(String topic) {
        PublishRules publishRules = new PublishRules();
        publishRules.setRules(List.of(new Rule(topic)));
        return publishRules;
    }

    public static EgressFlow buildEgressFlow(FlowState flowState) {
        ActionConfiguration sampleEgress = new ActionConfiguration("SampleEgressAction", ActionType.EGRESS, "type");

        return buildFlow(EGRESS_FLOW_NAME, sampleEgress, flowState, false);
    }

    public static EgressFlow buildFlow(String name, ActionConfiguration egressAction, FlowState flowState, boolean testMode) {
        EgressFlow egressFlow = new EgressFlow();
        egressFlow.setName(name);
        egressFlow.setEgressAction(egressAction);
        egressFlow.getFlowStatus().setState(flowState);
        egressFlow.setTestMode(testMode);
        return egressFlow;
    }

    public static EgressFlow buildRunningEgressFlow(String name, ActionConfiguration egressAction, boolean testMode) {
        return buildFlow(name, egressAction, FlowState.RUNNING, testMode);
    }

    public static TransformFlow buildTransformFlow(FlowState flowState) {
        ActionConfiguration tc = new ActionConfiguration("sampleTransform.Utf8TransformAction", ActionType.TRANSFORM, "type");
        ActionConfiguration tc2 = new ActionConfiguration("sampleTransform.SampleTransformAction", ActionType.TRANSFORM, "type");

        return buildFlow(TRANSFORM_FLOW_NAME, List.of(tc, tc2), flowState, false);
    }

    public static TransformFlow buildFlow(String name, List<ActionConfiguration> transforms, FlowState flowState, boolean testMode) {
        TransformFlow transformFlow = new TransformFlow();
        transformFlow.setName(name);
        transformFlow.getFlowStatus().setState(flowState);
        transformFlow.setTransformActions(transforms);
        transformFlow.setTestMode(testMode);
        return transformFlow;
    }

    public static TransformFlow buildRunningTransformFlow(String name, List<ActionConfiguration> transforms, boolean testMode) {
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

    public static TransformFlowPlanEntity buildTransformFlowPlan(String name, String groupId, String artifactId, String version) {
        PluginCoordinates pluginCoordinates = PluginCoordinates.builder()
                .groupId(groupId).artifactId(artifactId).version(version).build();
        return buildTransformFlowPlan(name, pluginCoordinates);
    }

    public static TransformFlowPlanEntity buildTransformFlowPlan(String name, PluginCoordinates pluginCoordinates) {
        TransformFlowPlanEntity transformFlowPlan = new TransformFlowPlanEntity(name, "desc");
        transformFlowPlan.setSourcePlugin(pluginCoordinates);
        return transformFlowPlan;
    }
}
