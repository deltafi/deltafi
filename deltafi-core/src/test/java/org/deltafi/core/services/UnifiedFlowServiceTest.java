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

import org.deltafi.common.types.*;
import org.deltafi.core.types.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UnifiedFlowServiceTest {
    @Mock
    DataSinkService dataSinkService;
    @Mock
    TransformFlowService transformFlowService;

    @InjectMocks
    UnifiedFlowService unifiedFlowService;

    @Test
    void testRunningTransformActions() {
        TransformFlow transformFlow = mock(TransformFlow.class);
        ActionConfiguration transformFlowAction = mock(ActionConfiguration.class);
        ActionConfiguration transformFlowAction2 = mock(ActionConfiguration.class);
        when(transformFlow.getTransformActions()).thenReturn(List.of(transformFlowAction, transformFlowAction2));

        when(transformFlowService.getRunningFlows()).thenReturn(List.of(transformFlow));

        assertEquals(List.of(transformFlowAction, transformFlowAction2), unifiedFlowService.runningTransformActions());
    }

    @Test
    void testRunningActionForTransformType() {
        ActionConfiguration transformFlowAction = mock(ActionConfiguration.class);
        when(transformFlowAction.getName()).thenReturn("anotherAction");
        TransformFlow transformFlow = mock(TransformFlow.class);
        ActionConfiguration transformFlowAction2 = mock(ActionConfiguration.class);
        when(transformFlowAction2.getName()).thenReturn("testAction");
        when(transformFlow.getTransformActions()).thenReturn(List.of(transformFlowAction, transformFlowAction2));

        when(transformFlowService.getRunningFlows()).thenReturn(List.of(transformFlow));

        assertEquals(transformFlowAction2, unifiedFlowService.runningAction("testAction", ActionType.TRANSFORM));
    }

    @Test
    void testRunningActionForEgressType() {
        ActionConfiguration dataSinkEgressAction = mock(ActionConfiguration.class);
        when(dataSinkEgressAction.getName()).thenReturn("testAction");
        DataSink dataSink = mock(DataSink.class);
        when(dataSink.getEgressAction()).thenReturn(dataSinkEgressAction);
        when(dataSinkService.getRunningFlows()).thenReturn(List.of(dataSink));

        assertEquals(dataSinkEgressAction, unifiedFlowService.runningAction("testAction", ActionType.EGRESS));
    }

    @Test
    void testAllActionConfigurations() {
        ActionConfiguration transformAction = mock(ActionConfiguration.class);
        ActionConfiguration egressAction = mock(ActionConfiguration.class);

        TransformFlow transformFlow = mock(TransformFlow.class);
        when(transformFlow.allActionConfigurations()).thenReturn(List.of(transformAction));

        DataSink dataSink = mock(DataSink.class);
        when(dataSink.allActionConfigurations()).thenReturn(List.of(egressAction));

        when(transformFlowService.getAll()).thenReturn(List.of(transformFlow));
        when(dataSinkService.getAll()).thenReturn(List.of(dataSink));

        assertEquals(List.of(transformAction, egressAction),
                unifiedFlowService.allActionConfigurations());
    }
}