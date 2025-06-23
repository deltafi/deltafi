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
package org.deltafi.core.datafetchers;

import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException;
import org.deltafi.core.audit.CoreAuditLogger;
import org.deltafi.core.generated.types.RateLimitInput;
import org.deltafi.core.generated.types.RateLimitUnit;
import org.deltafi.core.services.*;
import org.deltafi.core.validation.DataSinkPlanValidator;
import org.deltafi.core.validation.RestDataSourcePlanValidator;
import org.deltafi.core.validation.TimedDataSourcePlanValidator;
import org.deltafi.core.validation.TransformFlowPlanValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlowPlanDatafetcherTest {

    @Mock
    private DataSinkService dataSinkService;

    @Mock
    private RestDataSourceService restDataSourceService;

    @Mock
    private TransformFlowService transformFlowService;

    @Mock
    private AnnotationService annotationService;

    @Mock
    private PluginVariableService pluginVariableService;

    @Mock
    private TimedDataSourceService timedDataSourceService;

    @Mock
    private CoreAuditLogger auditLogger;

    @Mock
    private FlowCacheService flowCacheService;

    @Mock
    private PluginService pluginService;

    @Mock
    private DataSinkPlanValidator dataSinkPlanValidator;

    @Mock
    private RestDataSourcePlanValidator restDataSourcePlanValidator;

    @Mock
    private TimedDataSourcePlanValidator timedDataSourcePlanValidator;

    @Mock
    private TransformFlowPlanValidator transformFlowPlanValidator;

    private FlowPlanDatafetcher flowPlanDatafetcher;

    @BeforeEach
    void setUp() {
        flowPlanDatafetcher = new FlowPlanDatafetcher(
                dataSinkService,
                restDataSourceService,
                transformFlowService,
                annotationService,
                pluginVariableService,
                timedDataSourceService,
                auditLogger,
                flowCacheService,
                pluginService,
                dataSinkPlanValidator,
                restDataSourcePlanValidator,
                timedDataSourcePlanValidator,
                transformFlowPlanValidator
        );
        
        lenient().when(restDataSourceService.hasFlow("test-rest-datasource")).thenReturn(true);
        lenient().when(restDataSourceService.hasFlow("files-test-datasource")).thenReturn(true);
        lenient().when(restDataSourceService.hasFlow("bytes-test-datasource")).thenReturn(true);
        lenient().when(restDataSourceService.hasFlow("exception-test-datasource")).thenReturn(true);
    }

    @Test
    void testSetRestDataSourceRateLimitSuccess() {
        String flowName = "test-rest-datasource";
        RateLimitInput rateLimitInput = RateLimitInput.newBuilder()
                .unit(RateLimitUnit.FILES)
                .maxAmount(100L)
                .durationSeconds(60)
                .build();

        when(restDataSourceService.setRateLimit(flowName, rateLimitInput)).thenReturn(true);

        boolean result = flowPlanDatafetcher.setRestDataSourceRateLimit(flowName, rateLimitInput);

        assertTrue(result);
        verify(restDataSourceService).setRateLimit(flowName, rateLimitInput);
        verify(auditLogger).audit(eq("set rate limit to {} for data source {}"), eq(rateLimitInput), eq(flowName));
    }

    @Test
    void testSetRestDataSourceRateLimitFails() {
        String flowName = "test-rest-datasource";
        RateLimitInput rateLimitInput = RateLimitInput.newBuilder()
                .unit(RateLimitUnit.BYTES)
                .maxAmount(1000L)
                .durationSeconds(30)
                .build();

        when(restDataSourceService.setRateLimit(flowName, rateLimitInput)).thenReturn(false);

        boolean result = flowPlanDatafetcher.setRestDataSourceRateLimit(flowName, rateLimitInput);

        assertFalse(result);
        verify(restDataSourceService).setRateLimit(flowName, rateLimitInput);
        verify(auditLogger).audit(eq("set rate limit to {} for data source {}"), eq(rateLimitInput), eq(flowName));
    }

    @Test
    void testSetRestDataSourceRateLimitWithNullFlowName() {
        String flowName = null;
        RateLimitInput rateLimitInput = RateLimitInput.newBuilder()
                .unit(RateLimitUnit.FILES)
                .maxAmount(50L)
                .durationSeconds(120)
                .build();

        assertThrows(DgsEntityNotFoundException.class, () -> flowPlanDatafetcher.setRestDataSourceRateLimit(flowName, rateLimitInput));

        verify(restDataSourceService, never()).setRateLimit(any(), any());
        verify(auditLogger, never()).audit(any(), any(), any());
    }

    @Test
    void testRemoveRestDataSourceRateLimitSuccess() {
        String flowName = "test-rest-datasource";

        when(restDataSourceService.removeRateLimit(flowName)).thenReturn(true);

        boolean result = flowPlanDatafetcher.removeRestDataSourceRateLimit(flowName);

        assertTrue(result);
        verify(restDataSourceService).removeRateLimit(flowName);
        verify(auditLogger).audit(eq("remove rate limit for data source {}"), eq(flowName));
    }

    @Test
    void testRemoveRestDataSourceRateLimitFails() {
        String flowName = "test-rest-datasource";

        when(restDataSourceService.removeRateLimit(flowName)).thenReturn(false);

        boolean result = flowPlanDatafetcher.removeRestDataSourceRateLimit(flowName);

        assertFalse(result);
        verify(restDataSourceService).removeRateLimit(flowName);
        verify(auditLogger).audit(eq("remove rate limit for data source {}"), eq(flowName));
    }

    @Test
    void testSetRestDataSourceRateLimitWithFilesUnit() {
        String flowName = "files-test-datasource";
        RateLimitInput rateLimitInput = RateLimitInput.newBuilder()
                .unit(RateLimitUnit.FILES)
                .maxAmount(500L)
                .durationSeconds(300)
                .build();

        when(restDataSourceService.setRateLimit(flowName, rateLimitInput)).thenReturn(true);

        boolean result = flowPlanDatafetcher.setRestDataSourceRateLimit(flowName, rateLimitInput);

        assertTrue(result);
        verify(restDataSourceService).setRateLimit(flowName, rateLimitInput);
        verify(auditLogger).audit(eq("set rate limit to {} for data source {}"), eq(rateLimitInput), eq(flowName));
    }

    @Test
    void testSetRestDataSourceRateLimitWithBytesUnit() {
        String flowName = "bytes-test-datasource";
        RateLimitInput rateLimitInput = RateLimitInput.newBuilder()
                .unit(RateLimitUnit.BYTES)
                .maxAmount(1048576L) // 1MB
                .durationSeconds(60)
                .build();

        when(restDataSourceService.setRateLimit(flowName, rateLimitInput)).thenReturn(true);

        boolean result = flowPlanDatafetcher.setRestDataSourceRateLimit(flowName, rateLimitInput);

        assertTrue(result);
        verify(restDataSourceService).setRateLimit(flowName, rateLimitInput);
        verify(auditLogger).audit(eq("set rate limit to {} for data source {}"), eq(rateLimitInput), eq(flowName));
    }

    @Test
    void testSetRestDataSourceRateLimitServiceException() {
        String flowName = "exception-test-datasource";
        RateLimitInput rateLimitInput = RateLimitInput.newBuilder()
                .unit(RateLimitUnit.FILES)
                .maxAmount(10L)
                .durationSeconds(60)
                .build();

        when(restDataSourceService.setRateLimit(flowName, rateLimitInput))
                .thenThrow(new RuntimeException("Database error"));

        assertThrows(RuntimeException.class, () -> flowPlanDatafetcher.setRestDataSourceRateLimit(flowName, rateLimitInput));

        verify(restDataSourceService).setRateLimit(flowName, rateLimitInput);
        verify(auditLogger).audit(eq("set rate limit to {} for data source {}"), eq(rateLimitInput), eq(flowName));
    }

    @Test
    void testRemoveRestDataSourceRateLimitServiceException() {
        String flowName = "exception-test-datasource";

        when(restDataSourceService.removeRateLimit(flowName))
                .thenThrow(new RuntimeException("Database error"));

        assertThrows(RuntimeException.class, () -> flowPlanDatafetcher.removeRestDataSourceRateLimit(flowName));

        verify(restDataSourceService).removeRateLimit(flowName);
        verify(auditLogger).audit(eq("remove rate limit for data source {}"), eq(flowName));
    }
}