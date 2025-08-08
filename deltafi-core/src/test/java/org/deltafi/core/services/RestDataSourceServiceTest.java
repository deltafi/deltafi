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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.deltafi.common.types.FlowType;

import org.deltafi.core.exceptions.MissingFlowException;
import org.deltafi.core.generated.types.RateLimit;
import org.deltafi.core.generated.types.RateLimitInput;
import org.deltafi.core.generated.types.RateLimitUnit;
import org.deltafi.core.repo.RestDataSourceRepo;
import org.deltafi.core.types.RestDataSource;
import org.deltafi.core.types.Result;
import org.deltafi.core.types.snapshot.RestDataSourceSnapshot;
import org.deltafi.core.util.FlowBuilders;
import org.deltafi.core.validation.FlowValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.info.BuildProperties;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.spy;

@ExtendWith(MockitoExtension.class)
class RestDataSourceServiceTest {

    @Mock
    private RestDataSourceRepo restDataSourceRepo;

    @Mock
    private PluginVariableService pluginVariableService;

    @Mock
    private FlowValidator flowValidator;

    @Mock
    private BuildProperties buildProperties;

    @Mock
    private ErrorCountService errorCountService;

    @Mock
    private FlowCacheService flowCacheService;

    @Mock
    private EventService eventService;

    @Mock
    private RateLimitService rateLimitService;

    private RestDataSourceService restDataSourceService;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @BeforeEach
    void setUp() {
        restDataSourceService = spy(new RestDataSourceService(
                restDataSourceRepo,
                pluginVariableService,
                flowValidator,
                buildProperties,
                errorCountService,
                flowCacheService,
                eventService,
                rateLimitService
        ));
    }

    @Test
    void testSetRateLimitSuccess() throws JsonProcessingException {
        String flowName = "test-flow";
        RateLimitInput rateLimitInput = RateLimitInput.newBuilder()
                .unit(RateLimitUnit.FILES)
                .maxAmount(100L)
                .durationSeconds(60)
                .build();

        RestDataSource restDataSource = FlowBuilders.buildDataSource(flowName);
        String expectedJson = OBJECT_MAPPER.writeValueAsString(rateLimitInput);

        doReturn(restDataSource).when(restDataSourceService).getFlowOrThrow(flowName);
        when(restDataSourceRepo.updateRateLimit(flowName, expectedJson)).thenReturn(restDataSource);

        boolean result = restDataSourceService.setRateLimit(flowName, rateLimitInput);

        assertTrue(result);
        verify(restDataSourceRepo).updateRateLimit(flowName, expectedJson);
        verify(rateLimitService).updateLimit(flowName, 100L, Duration.ofSeconds(60));
        verify(flowCacheService).updateCache(restDataSource);
    }

    @Test
    void testSetRateLimitAlreadySet() {
        String flowName = "test-flow";
        RateLimitInput rateLimitInput = RateLimitInput.newBuilder()
                .unit(RateLimitUnit.FILES)
                .maxAmount(100L)
                .durationSeconds(60)
                .build();

        RestDataSource restDataSource = FlowBuilders.buildDataSource(flowName);
        restDataSource.setRateLimit(org.deltafi.core.generated.types.RateLimit.newBuilder()
                .unit(org.deltafi.core.generated.types.RateLimitUnit.FILES)
                .maxAmount(100L)
                .durationSeconds(60)
                .build());

        doReturn(restDataSource).when(restDataSourceService).getFlowOrThrow(flowName);

        boolean result = restDataSourceService.setRateLimit(flowName, rateLimitInput);

        assertFalse(result);
        verify(restDataSourceRepo, never()).updateRateLimit(any(), any());
        verify(rateLimitService, never()).updateLimit(any(), anyLong(), any());
        verify(flowCacheService, never()).updateCache(any());
    }

    @Test
    void testSetRateLimitDatabaseUpdateFails() throws JsonProcessingException {
        String flowName = "test-flow";
        RateLimitInput rateLimitInput = RateLimitInput.newBuilder()
                .unit(RateLimitUnit.BYTES)
                .maxAmount(1000L)
                .durationSeconds(30)
                .build();

        RestDataSource restDataSource = FlowBuilders.buildDataSource(flowName);
        String expectedJson = OBJECT_MAPPER.writeValueAsString(rateLimitInput);

        doReturn(restDataSource).when(restDataSourceService).getFlowOrThrow(flowName);
        when(restDataSourceRepo.updateRateLimit(flowName, expectedJson)).thenReturn(null);

        boolean result = restDataSourceService.setRateLimit(flowName, rateLimitInput);

        assertFalse(result);
        verify(restDataSourceRepo).updateRateLimit(flowName, expectedJson);
        verify(rateLimitService, never()).updateLimit(any(), anyLong(), any());
        verify(flowCacheService, never()).updateCache(any());
    }

    @Test
    void testSetRateLimitFlowNotFound() {
        String flowName = "non-existent-flow";
        RateLimitInput rateLimitInput = RateLimitInput.newBuilder()
                .unit(RateLimitUnit.FILES)
                .maxAmount(50L)
                .durationSeconds(120)
                .build();

        doThrow(MissingFlowException.notFound("flow not found", FlowType.REST_DATA_SOURCE)).when(restDataSourceService).getFlowOrThrow(flowName);

        assertThrows(MissingFlowException.class, () -> restDataSourceService.setRateLimit(flowName, rateLimitInput));

        verify(restDataSourceRepo, never()).updateRateLimit(any(), any());
        verify(rateLimitService, never()).updateLimit(any(), anyLong(), any());
        verify(flowCacheService, never()).updateCache(any());
    }

    @Test
    void testRemoveRateLimitSuccess() {
        String flowName = "test-flow";

        RestDataSource restDataSource = FlowBuilders.buildDataSource(flowName);
        restDataSource.setRateLimit(org.deltafi.core.generated.types.RateLimit.newBuilder()
                .unit(org.deltafi.core.generated.types.RateLimitUnit.FILES)
                .maxAmount(100L)
                .durationSeconds(60)
                .build());

        doReturn(restDataSource).when(restDataSourceService).getFlowOrThrow(flowName);
        when(restDataSourceRepo.removeRateLimit(flowName)).thenReturn(restDataSource);

        boolean result = restDataSourceService.removeRateLimit(flowName);

        assertTrue(result);
        verify(restDataSourceRepo).removeRateLimit(flowName);
        verify(rateLimitService).removeBucket(flowName);
        verify(flowCacheService).updateCache(restDataSource);
    }

    @Test
    void testRemoveRateLimitNotSet() {
        String flowName = "test-flow";

        RestDataSource restDataSource = FlowBuilders.buildDataSource(flowName);

        doReturn(restDataSource).when(restDataSourceService).getFlowOrThrow(flowName);

        boolean result = restDataSourceService.removeRateLimit(flowName);

        assertFalse(result);
        verify(restDataSourceRepo, never()).removeRateLimit(any());
        verify(rateLimitService, never()).removeBucket(any());
        verify(flowCacheService, never()).updateCache(any());
    }

    @Test
    void testRemoveRateLimitDatabaseUpdateFails() {
        String flowName = "test-flow";

        RestDataSource restDataSource = FlowBuilders.buildDataSource(flowName);
        restDataSource.setRateLimit(org.deltafi.core.generated.types.RateLimit.newBuilder()
                .unit(org.deltafi.core.generated.types.RateLimitUnit.BYTES)
                .maxAmount(500L)
                .durationSeconds(30)
                .build());

        doReturn(restDataSource).when(restDataSourceService).getFlowOrThrow(flowName);
        when(restDataSourceRepo.removeRateLimit(flowName)).thenReturn(null);

        boolean result = restDataSourceService.removeRateLimit(flowName);

        assertFalse(result);
        verify(restDataSourceRepo).removeRateLimit(flowName);
        verify(rateLimitService, never()).removeBucket(any());
        verify(flowCacheService, never()).updateCache(any());
    }

    @Test
    void testRemoveRateLimitFlowNotFound() {
        String flowName = "non-existent-flow";

        doThrow(MissingFlowException.notFound("flow not found", FlowType.REST_DATA_SOURCE)).when(restDataSourceService).getFlowOrThrow(flowName);

        assertThrows(MissingFlowException.class, () -> restDataSourceService.removeRateLimit(flowName));

        verify(restDataSourceRepo, never()).removeRateLimit(any());
        verify(rateLimitService, never()).removeBucket(any());
        verify(flowCacheService, never()).updateCache(any());
    }

    @Test
    void testSetRateLimitDifferentUnits() throws JsonProcessingException {
        String flowName = "test-flow";
        RateLimitInput fileLimitInput = RateLimitInput.newBuilder()
                .unit(RateLimitUnit.FILES)
                .maxAmount(10L)
                .durationSeconds(60)
                .build();

        RestDataSource restDataSource = FlowBuilders.buildDataSource(flowName);
        restDataSource.setRateLimit(org.deltafi.core.generated.types.RateLimit.newBuilder()
                .unit(org.deltafi.core.generated.types.RateLimitUnit.BYTES)
                .maxAmount(1000L)
                .durationSeconds(60)
                .build());

        String expectedJson = OBJECT_MAPPER.writeValueAsString(fileLimitInput);

        doReturn(restDataSource).when(restDataSourceService).getFlowOrThrow(flowName);
        when(restDataSourceRepo.updateRateLimit(flowName, expectedJson)).thenReturn(restDataSource);

        boolean result = restDataSourceService.setRateLimit(flowName, fileLimitInput);

        assertTrue(result);
        verify(restDataSourceRepo).updateRateLimit(flowName, expectedJson);
        verify(rateLimitService).updateLimit(flowName, 10L, Duration.ofSeconds(60));
        verify(flowCacheService).updateCache(restDataSource);
    }

    @Test
    void testSetRateLimitDifferentAmounts() throws JsonProcessingException {
        String flowName = "test-flow";
        RateLimitInput rateLimitInput = RateLimitInput.newBuilder()
                .unit(RateLimitUnit.FILES)
                .maxAmount(200L)
                .durationSeconds(60)
                .build();

        RestDataSource restDataSource = FlowBuilders.buildDataSource(flowName);
        restDataSource.setRateLimit(org.deltafi.core.generated.types.RateLimit.newBuilder()
                .unit(org.deltafi.core.generated.types.RateLimitUnit.FILES)
                .maxAmount(100L)
                .durationSeconds(60)
                .build());

        String expectedJson = OBJECT_MAPPER.writeValueAsString(rateLimitInput);

        doReturn(restDataSource).when(restDataSourceService).getFlowOrThrow(flowName);
        when(restDataSourceRepo.updateRateLimit(flowName, expectedJson)).thenReturn(restDataSource);

        boolean result = restDataSourceService.setRateLimit(flowName, rateLimitInput);

        assertTrue(result);
        verify(restDataSourceRepo).updateRateLimit(flowName, expectedJson);
        verify(rateLimitService).updateLimit(flowName, 200L, Duration.ofSeconds(60));
        verify(flowCacheService).updateCache(restDataSource);
    }

    @Test
    void testSetRateLimitDifferentDuration() throws JsonProcessingException {
        String flowName = "test-flow";
        RateLimitInput rateLimitInput = RateLimitInput.newBuilder()
                .unit(RateLimitUnit.FILES)
                .maxAmount(100L)
                .durationSeconds(120)
                .build();

        RestDataSource restDataSource = FlowBuilders.buildDataSource(flowName);
        restDataSource.setRateLimit(org.deltafi.core.generated.types.RateLimit.newBuilder()
                .unit(org.deltafi.core.generated.types.RateLimitUnit.FILES)
                .maxAmount(100L)
                .durationSeconds(60)
                .build());

        String expectedJson = OBJECT_MAPPER.writeValueAsString(rateLimitInput);

        doReturn(restDataSource).when(restDataSourceService).getFlowOrThrow(flowName);
        when(restDataSourceRepo.updateRateLimit(flowName, expectedJson)).thenReturn(restDataSource);

        boolean result = restDataSourceService.setRateLimit(flowName, rateLimitInput);

        assertTrue(result);
        verify(restDataSourceRepo).updateRateLimit(flowName, expectedJson);
        verify(rateLimitService).updateLimit(flowName, 100L, Duration.ofSeconds(120));
        verify(flowCacheService).updateCache(restDataSource);
    }

    @Test
    void testFlowSpecificUpdateFromSnapshotWithRateLimit() {
        String flowName = "test-flow";
        RestDataSource flow = FlowBuilders.buildDataSource(flowName);
        flow.setName(flowName);
        flow.setMaxErrors(5);
        flow.setRateLimit(null);

        RestDataSourceSnapshot snapshot = new RestDataSourceSnapshot(flowName);
        snapshot.setName(flowName);
        snapshot.setMaxErrors(10);
        
        RateLimit rateLimit = RateLimit.newBuilder()
                .unit(RateLimitUnit.FILES)
                .maxAmount(100L)
                .durationSeconds(60)
                .build();
        snapshot.setRateLimit(rateLimit);

        Result result = new Result();
        boolean changed = restDataSourceService.flowSpecificUpdateFromSnapshot(flow, snapshot, result);

        assertTrue(changed);
        assertEquals(10, flow.getMaxErrors());
        assertEquals(rateLimit, flow.getRateLimit());
        verify(rateLimitService).updateLimit(flowName, 100L, Duration.ofSeconds(60));
    }

    @Test
    void testFlowSpecificUpdateFromSnapshotRemoveRateLimit() {
        String flowName = "test-flow";
        RestDataSource flow = FlowBuilders.buildDataSource(flowName);
        flow.setName(flowName);
        flow.setMaxErrors(5);
        
        RateLimit existingRateLimit = RateLimit.newBuilder()
                .unit(RateLimitUnit.BYTES)
                .maxAmount(1000L)
                .durationSeconds(30)
                .build();
        flow.setRateLimit(existingRateLimit);

        RestDataSourceSnapshot snapshot = new RestDataSourceSnapshot(flowName);
        snapshot.setName(flowName);
        snapshot.setMaxErrors(5);
        snapshot.setRateLimit(null);

        Result result = new Result();
        boolean changed = restDataSourceService.flowSpecificUpdateFromSnapshot(flow, snapshot, result);

        assertTrue(changed);
        assertEquals(5, flow.getMaxErrors());
        assertNull(flow.getRateLimit());
        verify(rateLimitService).removeBucket(flowName);
    }

    @Test
    void testFlowSpecificUpdateFromSnapshotNoChanges() {
        String flowName = "test-flow";
        RestDataSource flow = FlowBuilders.buildDataSource(flowName);
        flow.setTopic("same-topic");
        flow.setMaxErrors(10);
        
        RateLimit rateLimit = RateLimit.newBuilder()
                .unit(RateLimitUnit.FILES)
                .maxAmount(50L)
                .durationSeconds(120)
                .build();
        flow.setRateLimit(rateLimit);

        RestDataSourceSnapshot snapshot = new RestDataSourceSnapshot(flowName);
        snapshot.setTopic("same-topic");
        snapshot.setMaxErrors(10);
        snapshot.setRateLimit(rateLimit);

        Result result = new Result();
        boolean changed = restDataSourceService.flowSpecificUpdateFromSnapshot(flow, snapshot, result);

        assertFalse(changed);
        assertEquals("same-topic", flow.getTopic());
        assertEquals(10, flow.getMaxErrors());
        assertEquals(rateLimit, flow.getRateLimit());
    }

    @Test
    void testFlowSpecificUpdateFromSnapshotUpdateRateLimit() {
        String flowName = "test-flow";
        RestDataSource flow = FlowBuilders.buildDataSource(flowName);
        flow.setName(flowName);
        flow.setMaxErrors(10);
        
        RateLimit oldRateLimit = RateLimit.newBuilder()
                .unit(RateLimitUnit.FILES)
                .maxAmount(50L)
                .durationSeconds(120)
                .build();
        flow.setRateLimit(oldRateLimit);

        RestDataSourceSnapshot snapshot = new RestDataSourceSnapshot(flowName);
        snapshot.setName(flowName);
        snapshot.setMaxErrors(10);
        
        RateLimit newRateLimit = RateLimit.newBuilder()
                .unit(RateLimitUnit.BYTES)
                .maxAmount(2000L)
                .durationSeconds(60)
                .build();
        snapshot.setRateLimit(newRateLimit);

        Result result = new Result();
        boolean changed = restDataSourceService.flowSpecificUpdateFromSnapshot(flow, snapshot, result);

        assertTrue(changed);
        assertEquals(10, flow.getMaxErrors());
        assertEquals(newRateLimit, flow.getRateLimit());
        verify(rateLimitService).updateLimit(flowName, 2000L, Duration.ofSeconds(60));
    }

    @Test
    void testPostConstructInitializesBuckets() {
        // Create flows with and without rate limits
        RestDataSource flowWithRateLimit1 = FlowBuilders.buildDataSource("topic");
        flowWithRateLimit1.setRateLimit(RateLimit.newBuilder()
                .unit(RateLimitUnit.FILES)
                .maxAmount(100L)
                .durationSeconds(60)
                .build());
        flowWithRateLimit1.setName("flow1");

        RestDataSource flowWithRateLimit2 = FlowBuilders.buildDataSource("topic");
        flowWithRateLimit2.setRateLimit(RateLimit.newBuilder()
                .unit(RateLimitUnit.BYTES)
                .maxAmount(500L)
                .durationSeconds(30)
                .build());
        flowWithRateLimit2.setName("flow2");

        RestDataSource flowWithoutRateLimit = FlowBuilders.buildDataSource("topic");
        flowWithoutRateLimit.setName("flow3");

        List<RestDataSource> allFlows = List.of(flowWithRateLimit1, flowWithRateLimit2, flowWithoutRateLimit);
        doReturn(allFlows).when(restDataSourceService).getAll();

        // Call postConstruct
        restDataSourceService.postConstruct();

        // Verify buckets were initialized for flows with rate limits
        verify(rateLimitService).updateLimit("flow1", 100L, Duration.ofSeconds(60));
        verify(rateLimitService).updateLimit("flow2", 500L, Duration.ofSeconds(30));
        verify(rateLimitService, never()).updateLimit(eq("flow3"), anyLong(), any());
    }
}