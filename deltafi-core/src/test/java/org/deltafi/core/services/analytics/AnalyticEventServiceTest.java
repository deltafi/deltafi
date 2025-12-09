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
/*
 * ABOUTME: Tests for AnalyticEventService annotation filtering functionality.
 * ABOUTME: Verifies that only allowed annotations are sent to the Parquet analytics collector.
 */
package org.deltafi.core.services.analytics;

import org.deltafi.common.types.FlowType;
import org.deltafi.core.configuration.DeltaFiProperties;
import org.deltafi.core.repo.AnalyticsRepo;
import org.deltafi.core.repo.EventAnnotationsRepo;
import org.deltafi.core.services.*;
import org.deltafi.core.services.analytics.AnalyticsClient.AnalyticsEventRequest;
import org.deltafi.core.types.AnalyticIngressTypeEnum;
import org.deltafi.core.types.FlowDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalyticEventServiceTest {

    @InjectMocks
    AnalyticEventService analyticEventService;

    @Mock
    AnalyticsRepo analyticsRepo;

    @Mock
    EventAnnotationsRepo eventAnnotationsRepo;

    @Mock
    FlowDefinitionService flowDefinitionService;

    @Mock
    EventGroupService eventGroupService;

    @Mock
    AnnotationKeyService annotationKeyService;

    @Mock
    AnnotationValueService annotationValueService;

    @Mock
    ActionNameService actionNameService;

    @Mock
    ErrorCauseService errorCauseService;

    @Mock
    DeltaFiPropertiesService deltaFiPropertiesService;

    @Mock
    AnalyticsClient analyticsClient;

    private DeltaFiProperties mockProperties;

    @BeforeEach
    void setup() {
        mockProperties = mock(DeltaFiProperties.class);
        when(deltaFiPropertiesService.getDeltaFiProperties()).thenReturn(mockProperties);
        when(mockProperties.isTimescaleAnalyticsEnabled()).thenReturn(true);
    }

    @Test
    void recordIngress_filtersAnnotationsForParquetCollector() {
        // Setup - allow only "allowed1" and "allowed2" annotations
        when(mockProperties.allowedAnalyticsAnnotationsList()).thenReturn(List.of("allowed1", "allowed2"));
        when(eventGroupService.getOrCreateEventGroupId(anyString())).thenReturn(1);

        FlowDefinition mockFlow = mock(FlowDefinition.class);
        when(mockFlow.getId()).thenReturn(1);
        when(flowDefinitionService.getOrCreateFlow(anyString(), any(FlowType.class))).thenReturn(mockFlow);

        // Input annotations with both allowed and disallowed keys
        Map<String, String> inputAnnotations = new HashMap<>();
        inputAnnotations.put("allowed1", "value1");
        inputAnnotations.put("allowed2", "value2");
        inputAnnotations.put("notAllowed", "shouldBeFiltered");

        // Execute
        analyticEventService.recordIngress(
                UUID.randomUUID(),
                OffsetDateTime.now(),
                "testDataSource",
                FlowType.REST_DATA_SOURCE,
                100L,
                inputAnnotations,
                AnalyticIngressTypeEnum.DATA_SOURCE
        );

        // Verify the collector receives filtered annotations
        ArgumentCaptor<AnalyticsEventRequest> captor = ArgumentCaptor.forClass(AnalyticsEventRequest.class);
        verify(analyticsClient).writeEvent(captor.capture());

        Map<String, String> sentAnnotations = captor.getValue().annotations();
        assertThat(sentAnnotations).containsOnlyKeys("allowed1", "allowed2");
        assertThat(sentAnnotations).containsEntry("allowed1", "value1");
        assertThat(sentAnnotations).containsEntry("allowed2", "value2");
        assertThat(sentAnnotations).doesNotContainKey("notAllowed");
    }

    @Test
    void recordIngress_emptyAnnotationsWhenNoneAllowed() {
        // Setup - allow only keys that aren't in the input
        when(mockProperties.allowedAnalyticsAnnotationsList()).thenReturn(List.of("other1", "other2"));
        when(eventGroupService.getOrCreateEventGroupId(anyString())).thenReturn(1);

        FlowDefinition mockFlow = mock(FlowDefinition.class);
        when(mockFlow.getId()).thenReturn(1);
        when(flowDefinitionService.getOrCreateFlow(anyString(), any(FlowType.class))).thenReturn(mockFlow);

        Map<String, String> inputAnnotations = new HashMap<>();
        inputAnnotations.put("notAllowed1", "value1");
        inputAnnotations.put("notAllowed2", "value2");

        // Execute
        analyticEventService.recordIngress(
                UUID.randomUUID(),
                OffsetDateTime.now(),
                "testDataSource",
                FlowType.REST_DATA_SOURCE,
                100L,
                inputAnnotations,
                AnalyticIngressTypeEnum.DATA_SOURCE
        );

        // Verify the collector receives empty annotations
        ArgumentCaptor<AnalyticsEventRequest> captor = ArgumentCaptor.forClass(AnalyticsEventRequest.class);
        verify(analyticsClient).writeEvent(captor.capture());

        Map<String, String> sentAnnotations = captor.getValue().annotations();
        assertThat(sentAnnotations).isEmpty();
    }

    @Test
    void queueAnnotations_filtersAnnotationsForParquetCollector() {
        // Setup
        when(mockProperties.allowedAnalyticsAnnotationsList()).thenReturn(List.of("allowed1"));

        Map<String, String> inputAnnotations = new HashMap<>();
        inputAnnotations.put("allowed1", "value1");
        inputAnnotations.put("notAllowed", "shouldBeFiltered");

        UUID did = UUID.randomUUID();
        OffsetDateTime creationTime = OffsetDateTime.now();

        // Execute
        analyticEventService.queueAnnotations(did, inputAnnotations, creationTime);

        // Verify the collector receives filtered annotations
        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(analyticsClient).queueAnnotations(eq(did), captor.capture(), eq(creationTime));

        Map<String, String> sentAnnotations = captor.getValue();
        assertThat(sentAnnotations).containsOnlyKeys("allowed1");
        assertThat(sentAnnotations).doesNotContainKey("notAllowed");
    }
}
