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
// ABOUTME: Unit tests for FlowValidationService.
// ABOUTME: Tests flow revalidation when plugin state changes.
package org.deltafi.core.services;

import org.deltafi.common.types.ActionConfiguration;
import org.deltafi.common.types.ActionType;
import org.deltafi.common.types.FlowType;
import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.core.generated.types.FlowState;
import org.deltafi.core.generated.types.FlowStatus;
import org.deltafi.core.types.DataSink;
import org.deltafi.core.types.TransformFlow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlowValidationServiceTest {

    private static final PluginCoordinates PLUGIN_A = new PluginCoordinates("org.test", "plugin-a", "1.0.0");
    private static final PluginCoordinates PLUGIN_B = new PluginCoordinates("org.test", "plugin-b", "1.0.0");

    @Mock
    private FlowCacheService flowCacheService;

    @Mock
    private TimedDataSourceService timedDataSourceService;

    @Mock
    private RestDataSourceService restDataSourceService;

    @Mock
    private OnErrorDataSourceService onErrorDataSourceService;

    @Mock
    private TransformFlowService transformFlowService;

    @Mock
    private DataSinkService dataSinkService;

    @Mock
    private PluginService pluginService;

    private FlowValidationService flowValidationService;

    @BeforeEach
    void setUp() {
        flowValidationService = new FlowValidationService(
                flowCacheService,
                timedDataSourceService,
                restDataSourceService,
                onErrorDataSourceService,
                transformFlowService,
                dataSinkService,
                pluginService
        );
    }

    @Test
    void revalidateFlowsForPlugin_revalidatesFlowsOwnedByPlugin() {
        TransformFlow flow = createFlow("test-transform", FlowType.TRANSFORM, PLUGIN_A, true);
        when(flowCacheService.getAllFlows()).thenReturn(List.of(flow));

        flowValidationService.revalidateFlowsForPlugin(PLUGIN_A);

        verify(flowCacheService).refreshCache();
        verify(transformFlowService).validateAndSaveFlow("test-transform");
    }

    @Test
    void revalidateFlowsForPlugin_revalidatesFlowsUsingActionsFromPlugin() {
        // Flow owned by plugin-b but uses an action from plugin-a
        TransformFlow flow = createFlowWithAction("test-transform", PLUGIN_B, "org.test.MyAction");
        when(flowCacheService.getAllFlows()).thenReturn(List.of(flow));
        when(pluginService.getPluginWithAction("org.test.MyAction")).thenReturn("plugin-a");

        flowValidationService.revalidateFlowsForPlugin(PLUGIN_A);

        verify(flowCacheService).refreshCache();
        verify(transformFlowService).validateAndSaveFlow("test-transform");
    }

    @Test
    void revalidateFlowsForPlugin_noActionWhenNoAffectedFlows() {
        // Flow owned by plugin-b with action from plugin-b - not affected by plugin-a change
        TransformFlow flow = createFlowWithAction("test-transform", PLUGIN_B, "org.test.OtherAction");
        when(flowCacheService.getAllFlows()).thenReturn(List.of(flow));
        when(pluginService.getPluginWithAction("org.test.OtherAction")).thenReturn("plugin-b");

        flowValidationService.revalidateFlowsForPlugin(PLUGIN_A);

        verify(flowCacheService).refreshCache();
        verifyNoInteractions(transformFlowService, restDataSourceService, timedDataSourceService,
                onErrorDataSourceService, dataSinkService);
    }

    @Test
    void revalidateFlowsForPlugin_revalidatesMultipleFlowTypes() {
        TransformFlow transformFlow = createFlow("test-transform", FlowType.TRANSFORM, PLUGIN_A, true);
        DataSink dataSinkFlow = createDataSink("test-sink", PLUGIN_A, true);
        when(flowCacheService.getAllFlows()).thenReturn(List.of(transformFlow, dataSinkFlow));

        flowValidationService.revalidateFlowsForPlugin(PLUGIN_A);

        verify(transformFlowService).validateAndSaveFlow("test-transform");
        verify(dataSinkService).validateAndSaveFlow("test-sink");
    }

    @Test
    void revalidateInvalidFlows_revalidatesAllInvalidFlows() {
        TransformFlow invalidFlow = createFlow("test-transform", FlowType.TRANSFORM, PLUGIN_A, false);
        when(flowCacheService.getInvalidFlows()).thenReturn(List.of(invalidFlow));

        flowValidationService.revalidateInvalidFlows();

        verify(flowCacheService).refreshCache();
        verify(transformFlowService).validateAndSaveFlow("test-transform");
    }

    @Test
    void revalidateInvalidFlows_noActionWhenNoInvalidFlows() {
        when(flowCacheService.getInvalidFlows()).thenReturn(Collections.emptyList());

        flowValidationService.revalidateInvalidFlows();

        verify(flowCacheService).refreshCache();
        verifyNoInteractions(transformFlowService);
    }

    @Test
    void revalidateInvalidFlows_revalidatesFlowsFromMultiplePlugins() {
        TransformFlow flowA = createFlow("flow-a", FlowType.TRANSFORM, PLUGIN_A, false);
        TransformFlow flowB = createFlow("flow-b", FlowType.TRANSFORM, PLUGIN_B, false);
        when(flowCacheService.getInvalidFlows()).thenReturn(List.of(flowA, flowB));

        flowValidationService.revalidateInvalidFlows();

        verify(transformFlowService).validateAndSaveFlow("flow-a");
        verify(transformFlowService).validateAndSaveFlow("flow-b");
    }

    @Test
    void revalidateInvalidFlows_continuesOnException() {
        TransformFlow flow1 = createFlow("flow1", FlowType.TRANSFORM, PLUGIN_A, false);
        TransformFlow flow2 = createFlow("flow2", FlowType.TRANSFORM, PLUGIN_A, false);
        when(flowCacheService.getInvalidFlows()).thenReturn(List.of(flow1, flow2));
        doThrow(new RuntimeException("Validation failed")).when(transformFlowService).validateAndSaveFlow("flow1");

        flowValidationService.revalidateInvalidFlows();

        // Should still attempt to validate flow2 even though flow1 failed
        verify(transformFlowService).validateAndSaveFlow("flow1");
        verify(transformFlowService).validateAndSaveFlow("flow2");
    }

    private TransformFlow createFlow(String name, FlowType type, PluginCoordinates plugin, boolean valid) {
        TransformFlow flow = new TransformFlow();
        flow.setName(name);
        FlowStatus status = new FlowStatus();
        status.setState(FlowState.RUNNING);
        status.setValid(valid);
        flow.setFlowStatus(status);
        flow.setSourcePlugin(plugin);
        return flow;
    }

    private TransformFlow createFlowWithAction(String name, PluginCoordinates sourcePlugin, String actionType) {
        TransformFlow flow = new TransformFlow();
        flow.setName(name);
        FlowStatus status = new FlowStatus();
        status.setState(FlowState.RUNNING);
        status.setValid(true);
        flow.setFlowStatus(status);
        flow.setSourcePlugin(sourcePlugin);
        ActionConfiguration action = new ActionConfiguration("myAction", ActionType.TRANSFORM, actionType);
        flow.setTransformActions(List.of(action));
        return flow;
    }

    private DataSink createDataSink(String name, PluginCoordinates plugin, boolean valid) {
        DataSink flow = new DataSink();
        flow.setName(name);
        FlowStatus status = new FlowStatus();
        status.setState(FlowState.RUNNING);
        status.setValid(valid);
        flow.setFlowStatus(status);
        flow.setSourcePlugin(plugin);
        return flow;
    }
}
