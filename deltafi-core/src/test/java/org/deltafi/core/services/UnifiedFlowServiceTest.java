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
    NormalizeFlowService normalizeFlowService;
    @Mock
    EnrichFlowService enrichFlowService;
    @Mock
    EgressFlowService egressFlowService;
    @Mock
    TransformFlowService transformFlowService;

    @InjectMocks
    UnifiedFlowService unifiedFlowService;

    @Test
    void testRunningTransformActions() {
        TransformActionConfiguration transformFlowAction = mock(TransformActionConfiguration.class);
        TransformFlow transformFlow = mock(TransformFlow.class);
        when(transformFlow.getTransformActions()).thenReturn(List.of(transformFlowAction));
        TransformActionConfiguration normalizeFlowAction = mock(TransformActionConfiguration.class);
        NormalizeFlow normalizeFlow = mock(NormalizeFlow.class);
        when(normalizeFlow.getTransformActions()).thenReturn(List.of(normalizeFlowAction));

        when(transformFlowService.getRunningFlows()).thenReturn(List.of(transformFlow));
        when(normalizeFlowService.getRunningFlows()).thenReturn(List.of(normalizeFlow));

        assertEquals(List.of(transformFlowAction, normalizeFlowAction), unifiedFlowService.runningTransformActions());
    }

    @Test
    void testRunningActionForTransformType() {
        TransformActionConfiguration transformFlowAction = mock(TransformActionConfiguration.class);
        when(transformFlowAction.getName()).thenReturn("anotherAction");
        TransformFlow transformFlow = mock(TransformFlow.class);
        when(transformFlow.getTransformActions()).thenReturn(List.of(transformFlowAction));
        TransformActionConfiguration normalizeFlowAction = mock(TransformActionConfiguration.class);
        when(normalizeFlowAction.getName()).thenReturn("testAction");
        NormalizeFlow normalizeFlow = mock(NormalizeFlow.class);
        when(normalizeFlow.getTransformActions()).thenReturn(List.of(normalizeFlowAction));

        when(transformFlowService.getRunningFlows()).thenReturn(List.of(transformFlow));
        when(normalizeFlowService.getRunningFlows()).thenReturn(List.of(normalizeFlow));

        assertEquals(normalizeFlowAction, unifiedFlowService.runningAction("testAction", ActionType.TRANSFORM));
    }

    @Test
    void testRunningActionForLoadType() {
        LoadActionConfiguration loadAction = mock(LoadActionConfiguration.class);
        when(loadAction.getName()).thenReturn("testAction");
        NormalizeFlow normalizeFlow = mock(NormalizeFlow.class);
        when(normalizeFlow.getLoadAction()).thenReturn(loadAction);

        when(normalizeFlowService.getRunningFlows()).thenReturn(List.of(normalizeFlow));

        assertEquals(loadAction, unifiedFlowService.runningAction("testAction", ActionType.LOAD));
    }

    @Test
    void testRunningActionForDomainType() {
        DomainActionConfiguration domainAction = mock(DomainActionConfiguration.class);
        when(domainAction.getName()).thenReturn("testAction");
        EnrichFlow enrichFlow = mock(EnrichFlow.class);
        when(enrichFlow.getDomainActions()).thenReturn(List.of(domainAction));

        when(enrichFlowService.getRunningFlows()).thenReturn(List.of(enrichFlow));

        assertEquals(domainAction, unifiedFlowService.runningAction("testAction", ActionType.DOMAIN));
    }

    @Test
    void testRunningActionForEnrichType() {
        EnrichActionConfiguration enrichAction = mock(EnrichActionConfiguration.class);
        when(enrichAction.getName()).thenReturn("testAction");
        EnrichFlow enrichFlow = mock(EnrichFlow.class);
        when(enrichFlow.getEnrichActions()).thenReturn(List.of(enrichAction));

        when(enrichFlowService.getRunningFlows()).thenReturn(List.of(enrichFlow));

        assertEquals(enrichAction, unifiedFlowService.runningAction("testAction", ActionType.ENRICH));
    }

    @Test
    void testRunningActionForFormatType() {
        FormatActionConfiguration formatAction = mock(FormatActionConfiguration.class);
        when(formatAction.getName()).thenReturn("testAction");
        EgressFlow egressFlow = mock(EgressFlow.class);
        when(egressFlow.getFormatAction()).thenReturn(formatAction);

        when(egressFlowService.getRunningFlows()).thenReturn(List.of(egressFlow));

        assertEquals(formatAction, unifiedFlowService.runningAction("testAction", ActionType.FORMAT));
    }

    @Test
    void testRunningActionForValidateType() {
        ValidateActionConfiguration validateAction = mock(ValidateActionConfiguration.class);
        when(validateAction.getName()).thenReturn("testAction");
        EgressFlow egressFlow = mock(EgressFlow.class);
        when(egressFlow.getValidateActions()).thenReturn(List.of(validateAction));

        when(egressFlowService.getRunningFlows()).thenReturn(List.of(egressFlow));

        assertEquals(validateAction, unifiedFlowService.runningAction("testAction", ActionType.VALIDATE));
    }

    @Test
    void testRunningActionForEgressType() {
        EgressActionConfiguration transformFlowEgressAction = mock(EgressActionConfiguration.class);
        when(transformFlowEgressAction.getName()).thenReturn("anotherAction");
        TransformFlow transformFlow = mock(TransformFlow.class);
        when(transformFlow.getEgressAction()).thenReturn(transformFlowEgressAction);
        EgressActionConfiguration egressFlowEgressAction = mock(EgressActionConfiguration.class);
        when(egressFlowEgressAction.getName()).thenReturn("testAction");
        EgressFlow egressFlow = mock(EgressFlow.class);
        when(egressFlow.getEgressAction()).thenReturn(egressFlowEgressAction);

        when(transformFlowService.getRunningFlows()).thenReturn(List.of(transformFlow));
        when(egressFlowService.getRunningFlows()).thenReturn(List.of(egressFlow));

        assertEquals(egressFlowEgressAction, unifiedFlowService.runningAction("testAction", ActionType.EGRESS));
    }

    @Test
    void testAllActionConfigurations() {
        ActionConfiguration transformAction = mock(ActionConfiguration.class);
        ActionConfiguration normalizeAction = mock(ActionConfiguration.class);
        ActionConfiguration enrichAction = mock(ActionConfiguration.class);
        ActionConfiguration egressAction = mock(ActionConfiguration.class);

        TransformFlow transformFlow = mock(TransformFlow.class);
        when(transformFlow.allActionConfigurations()).thenReturn(List.of(transformAction));

        NormalizeFlow normalizeFlow = mock(NormalizeFlow.class);
        when(normalizeFlow.allActionConfigurations()).thenReturn(List.of(normalizeAction));

        EnrichFlow enrichFlow = mock(EnrichFlow.class);
        when(enrichFlow.allActionConfigurations()).thenReturn(List.of(enrichAction));

        EgressFlow egressFlow = mock(EgressFlow.class);
        when(egressFlow.allActionConfigurations()).thenReturn(List.of(egressAction));

        when(transformFlowService.getAll()).thenReturn(List.of(transformFlow));
        when(normalizeFlowService.getAll()).thenReturn(List.of(normalizeFlow));
        when(enrichFlowService.getAll()).thenReturn(List.of(enrichFlow));
        when(egressFlowService.getAll()).thenReturn(List.of(egressFlow));

        assertEquals(List.of(transformAction, normalizeAction, enrichAction, egressAction),
                unifiedFlowService.allActionConfigurations());
    }
}