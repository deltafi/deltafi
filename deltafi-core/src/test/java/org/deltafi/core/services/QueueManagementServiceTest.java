/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
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

import org.deltafi.common.types.ActionConfiguration;
import org.deltafi.common.types.ActionType;
import org.deltafi.common.types.DeltaFileFlowState;
import org.deltafi.common.types.FlowType;
import org.deltafi.core.configuration.DeltaFiProperties;
import org.deltafi.core.generated.types.RateLimit;
import org.deltafi.core.generated.types.RateLimitUnit;
import org.deltafi.core.repo.DeltaFileFlowRepo;
import org.deltafi.core.repo.DeltaFileFlowRepoCustom.OldestColdQueueEntry;
import org.deltafi.core.repo.FlowDefinitionRepo;
import org.deltafi.core.types.DataSink;
import org.deltafi.core.types.DeltaFile;
import org.deltafi.core.types.DeltaFileFlow;
import org.deltafi.core.types.FlowDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.core.env.Environment;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QueueManagementServiceTest {

    private static final String QUEUE_NAME = "queueName";
    private static final String ACTION_NAME = "actionName";
    private static final ActionConfiguration ACTION_CONFIGURATION = new ActionConfiguration(ACTION_NAME, ActionType.TRANSFORM, QUEUE_NAME);

    @Mock
    CoreEventQueue coreEventQueue;

    @Mock
    DeltaFileFlowRepo deltaFileFlowRepo;

    @Mock
    FlowDefinitionRepo flowDefinitionRepo;

    @Mock
    UnifiedFlowService unifiedFlowService;

    @Mock
    DeltaFilesService deltaFilesService;

    @Mock
    DeltaFiPropertiesService deltaFiPropertiesService;

    @Mock
    DeltaFiProperties deltaFiProperties;

    @Mock
    Environment env;

    @Mock
    RateLimitService rateLimitService;

    @Mock
    DataSinkService dataSinkService;

    QueueManagementService queueManagementService;

    @BeforeEach
    void setUp() {
        queueManagementService = new QueueManagementService(
                coreEventQueue,
                deltaFileFlowRepo,
                flowDefinitionRepo,
                unifiedFlowService,
                deltaFilesService,
                deltaFiPropertiesService,
                env,
                rateLimitService,
                dataSinkService
        );
    }

    @Test
    void testIdentifyColdQueuesAdd() {
        when(coreEventQueue.keys()).thenReturn(Set.of(QUEUE_NAME));
        when(unifiedFlowService.allActionConfigurations()).thenReturn(List.of(ACTION_CONFIGURATION));
        when(deltaFiPropertiesService.getDeltaFiProperties()).thenReturn(deltaFiProperties);
        when(deltaFiProperties.getInMemoryQueueSize()).thenReturn(10);
        when(coreEventQueue.sizes(Set.of(QUEUE_NAME))).thenReturn(Map.of(QUEUE_NAME, 12L));

        queueManagementService.refreshQueues();
        assertTrue(queueManagementService.coldQueue(QUEUE_NAME, 0L));
    }

    @Test
    void testIdentifyColdQueuesUpdate() {
        queueManagementService.getColdQueues().add(QUEUE_NAME);
        when(coreEventQueue.keys()).thenReturn(Set.of(QUEUE_NAME));
        when(unifiedFlowService.allActionConfigurations()).thenReturn(List.of(ACTION_CONFIGURATION));
        when(deltaFiPropertiesService.getDeltaFiProperties()).thenReturn(deltaFiProperties);
        when(deltaFiProperties.getInMemoryQueueSize()).thenReturn(10);
        when(coreEventQueue.sizes(Set.of(QUEUE_NAME))).thenReturn(Map.of(QUEUE_NAME, 400L));

        queueManagementService.refreshQueues();
        assertTrue(queueManagementService.coldQueue(QUEUE_NAME, 0L));
        assertEquals(400L, queueManagementService.getAllQueues().get(QUEUE_NAME));
    }

    @Test
    void testIdentifyColdQueuesRemove() {
        queueManagementService.getColdQueues().add(QUEUE_NAME);
        when(coreEventQueue.keys()).thenReturn(Set.of(QUEUE_NAME));
        when(unifiedFlowService.allActionConfigurations()).thenReturn(List.of(ACTION_CONFIGURATION));
        when(deltaFiPropertiesService.getDeltaFiProperties()).thenReturn(deltaFiProperties);
        when(deltaFiProperties.getInMemoryQueueSize()).thenReturn(10);
        when(coreEventQueue.sizes(Set.of(QUEUE_NAME))).thenReturn(Map.of(QUEUE_NAME, 8L));

        queueManagementService.refreshQueues();
        assertFalse(queueManagementService.coldQueue(QUEUE_NAME, 0L));
    }

    @Test
    void testColdQueueExists() {
        queueManagementService.getColdQueues().add(QUEUE_NAME);
        assertTrue(queueManagementService.coldQueue(QUEUE_NAME, 0L));
    }

    @Test
    void testColdQueueDoesNotExist() {
        when(deltaFiPropertiesService.getDeltaFiProperties()).thenReturn(deltaFiProperties);
        when(deltaFiProperties.getInMemoryQueueSize()).thenReturn(10);
        assertFalse(queueManagementService.coldQueue(QUEUE_NAME, 0L));
    }

    @Test
    void testPendingExceedsMax() {
        queueManagementService.getAllQueues().put(QUEUE_NAME, 8L);
        when(deltaFiPropertiesService.getDeltaFiProperties()).thenReturn(deltaFiProperties);
        when(deltaFiProperties.getInMemoryQueueSize()).thenReturn(10);
        assertFalse(queueManagementService.coldQueue(QUEUE_NAME, 0L));
        assertTrue(queueManagementService.coldQueue(QUEUE_NAME, 2L));
    }

    @Test
    void testColdToWarmWithoutCheckedQueues() {
        queueManagementService.getCheckedQueues().set(false);
        queueManagementService.coldToWarm();
        verify(deltaFileFlowRepo, times(0)).distinctColdQueuedActions();
    }

    @Test
    void testColdToWarmWithCheckedQueues() {
        queueManagementService.getCheckedQueues().set(true);
        queueManagementService.getAllQueues().put(QUEUE_NAME, 8L);
        when(deltaFiPropertiesService.getDeltaFiProperties()).thenReturn(deltaFiProperties);
        when(deltaFiProperties.getInMemoryQueueSize()).thenReturn(10);
        when(deltaFileFlowRepo.distinctColdQueuedActions()).thenReturn(List.of(QUEUE_NAME));
        when(env.getProperty("schedule.maintenance")).thenReturn("true");
        queueManagementService.scheduleColdToWarm();
        verify(deltaFilesService).requeueColdQueueActions(QUEUE_NAME, 2);
    }

    @Test
    void testColdToWarmDefersWhenResultQueueBackedUp() {
        queueManagementService.getCheckedQueues().set(true);
        when(deltaFileFlowRepo.distinctColdQueuedActions()).thenReturn(List.of(QUEUE_NAME));
        when(deltaFilesService.resultQueueBackedUp()).thenReturn(true);
        when(env.getProperty("schedule.maintenance")).thenReturn("true");

        queueManagementService.scheduleColdToWarm();

        verify(deltaFilesService, never()).requeueColdQueueActions(anyString(), anyInt());
    }

    @Test
    void testRateLimitedColdToWarmDefersWhenResultQueueBackedUp() {
        String flowName = "test-data-sink";
        queueManagementService.getCheckedQueues().set(true);
        when(deltaFileFlowRepo.distinctRateLimitedColdQueuedDataSinks()).thenReturn(List.of(flowName));
        when(deltaFilesService.resultQueueBackedUp()).thenReturn(true);
        when(env.getProperty("schedule.maintenance")).thenReturn("true");

        queueManagementService.scheduleRateLimitedColdToWarm();

        // saturated shared queue: no tokens consumed and nothing warmed
        verify(rateLimitService, never()).consume(anyString(), anyLong(), anyLong(), any());
        verify(deltaFilesService, never()).requeueRateLimitedColdQueueActions(anyString(), anyInt());
        verify(deltaFilesService, never()).requeueSpecificRateLimitedFiles(anyString(), any());
        verifyNoInteractions(dataSinkService);
    }

    @Test
    void testRateLimitedColdToWarmWithoutCheckedQueues() {
        queueManagementService.getCheckedQueues().set(false);
        queueManagementService.scheduleRateLimitedColdToWarm();
        verify(deltaFileFlowRepo, never()).distinctRateLimitedColdQueuedDataSinks();
    }

    @Test
    void testColdQueueActions() {
        queueManagementService.getColdQueues().add(QUEUE_NAME);
        when(unifiedFlowService.allActionConfigurations()).thenReturn(List.of(ACTION_CONFIGURATION));
        assertEquals(Set.of(ACTION_NAME), queueManagementService.coldQueueActions());
    }

    @Test
    void testGetDetailedWarmQueueMetrics() {
        // Setup queue with items from different flows
        queueManagementService.getAllQueues().put(QUEUE_NAME, 3L);

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime older = now.minusMinutes(5);
        OffsetDateTime oldest = now.minusMinutes(10);

        when(flowDefinitionRepo.findAll()).thenReturn(List.of(
                FlowDefinition.builder().name("flow1").type(FlowType.TRANSFORM).build(),
                FlowDefinition.builder().name("flow2").type(FlowType.DATA_SINK).build()
        ));

        // Mock streamQueue to invoke the consumer with test data
        List<CoreEventQueue.QueuedActionInfo> testItems = List.of(
                new CoreEventQueue.QueuedActionInfo("flow1", "action1", UUID.randomUUID(), oldest),
                new CoreEventQueue.QueuedActionInfo("flow1", "action1", UUID.randomUUID(), older),
                new CoreEventQueue.QueuedActionInfo("flow2", "action2", UUID.randomUUID(), now)
        );
        doAnswer(invocation -> {
            var consumer = invocation.<java.util.function.Consumer<CoreEventQueue.QueuedActionInfo>>getArgument(1);
            testItems.forEach(consumer);
            return null;
        }).when(coreEventQueue).streamQueue(eq(QUEUE_NAME), any());

        List<QueueManagementService.WarmQueueMetrics> metrics = queueManagementService.getDetailedWarmQueueMetrics();

        assertEquals(2, metrics.size());

        // First entry (sorted by actionClass, flowName, actionName)
        var flow1Metrics = metrics.stream()
                .filter(m -> m.flowName().equals("flow1"))
                .findFirst()
                .orElseThrow();
        assertEquals(QUEUE_NAME, flow1Metrics.actionClass());
        assertEquals("action1", flow1Metrics.actionName());
        assertEquals("TRANSFORM", flow1Metrics.flowType());
        assertEquals(2, flow1Metrics.count());
        assertEquals(oldest, flow1Metrics.oldestQueuedAt());

        // Second entry
        var flow2Metrics = metrics.stream()
                .filter(m -> m.flowName().equals("flow2"))
                .findFirst()
                .orElseThrow();
        assertEquals(QUEUE_NAME, flow2Metrics.actionClass());
        assertEquals("action2", flow2Metrics.actionName());
        assertEquals("DATA_SINK", flow2Metrics.flowType());
        assertEquals(1, flow2Metrics.count());
        assertEquals(now, flow2Metrics.oldestQueuedAt());
    }

    @Test
    void testGetDetailedWarmQueueMetricsEmpty() {
        when(flowDefinitionRepo.findAll()).thenReturn(List.of());
        List<QueueManagementService.WarmQueueMetrics> metrics = queueManagementService.getDetailedWarmQueueMetrics();
        assertTrue(metrics.isEmpty());
    }

    @Test
    void testGetDetailedWarmQueueMetricsCaching() {
        queueManagementService.getAllQueues().put(QUEUE_NAME, 1L);
        when(flowDefinitionRepo.findAll()).thenReturn(List.of(
                FlowDefinition.builder().name("flow1").type(FlowType.TRANSFORM).build()
        ));
        doAnswer(invocation -> {
            var consumer = invocation.<java.util.function.Consumer<CoreEventQueue.QueuedActionInfo>>getArgument(1);
            consumer.accept(new CoreEventQueue.QueuedActionInfo("flow1", "action1", UUID.randomUUID(), OffsetDateTime.now()));
            return null;
        }).when(coreEventQueue).streamQueue(eq(QUEUE_NAME), any());

        // First call should fetch
        queueManagementService.getDetailedWarmQueueMetrics();
        verify(coreEventQueue, times(1)).streamQueue(eq(QUEUE_NAME), any());

        // Second call within cache window should not fetch again
        queueManagementService.getDetailedWarmQueueMetrics();
        verify(coreEventQueue, times(1)).streamQueue(eq(QUEUE_NAME), any());
    }

    @Test
    void testGetOldestQueuedInfo_bothEmpty_returnsNull() {
        when(flowDefinitionRepo.findAll()).thenReturn(List.of());
        when(deltaFileFlowRepo.getOldestColdQueueEntry()).thenReturn(Optional.empty());

        assertNull(queueManagementService.getOldestQueuedInfo());
    }

    @Test
    void testGetOldestQueuedInfo_onlyWarmHasData_returnsWarmInfo() {
        OffsetDateTime warmOldest = OffsetDateTime.now().minusHours(1);
        UUID warmDid = UUID.randomUUID();

        queueManagementService.getAllQueues().put(QUEUE_NAME, 1L);
        when(flowDefinitionRepo.findAll()).thenReturn(List.of(
                FlowDefinition.builder().name("flow1").type(FlowType.TRANSFORM).build()
        ));
        doAnswer(invocation -> {
            var consumer = invocation.<java.util.function.Consumer<CoreEventQueue.QueuedActionInfo>>getArgument(1);
            consumer.accept(new CoreEventQueue.QueuedActionInfo("flow1", "action1", warmDid, warmOldest));
            return null;
        }).when(coreEventQueue).streamQueue(eq(QUEUE_NAME), any());
        when(deltaFileFlowRepo.getOldestColdQueueEntry()).thenReturn(Optional.empty());

        var result = queueManagementService.getOldestQueuedInfo();

        assertNotNull(result);
        assertEquals(warmOldest, result.timestamp());
        assertEquals(warmDid, result.did());
    }

    @Test
    void testGetOldestQueuedInfo_onlyColdHasData_returnsColdInfo() {
        OffsetDateTime coldOldest = OffsetDateTime.now().minusHours(2);
        UUID coldDid = UUID.randomUUID();

        when(flowDefinitionRepo.findAll()).thenReturn(List.of());
        when(deltaFileFlowRepo.getOldestColdQueueEntry()).thenReturn(
                Optional.of(new OldestColdQueueEntry(coldDid, coldOldest)));

        var result = queueManagementService.getOldestQueuedInfo();

        assertNotNull(result);
        assertEquals(coldOldest, result.timestamp());
        assertEquals(coldDid, result.did());
    }

    @Test
    void testGetOldestQueuedInfo_warmIsOlder_returnsWarmInfo() {
        OffsetDateTime warmOldest = OffsetDateTime.now().minusHours(3);
        OffsetDateTime coldOldest = OffsetDateTime.now().minusHours(1);
        UUID warmDid = UUID.randomUUID();
        UUID coldDid = UUID.randomUUID();

        queueManagementService.getAllQueues().put(QUEUE_NAME, 1L);
        when(flowDefinitionRepo.findAll()).thenReturn(List.of(
                FlowDefinition.builder().name("flow1").type(FlowType.TRANSFORM).build()
        ));
        doAnswer(invocation -> {
            var consumer = invocation.<java.util.function.Consumer<CoreEventQueue.QueuedActionInfo>>getArgument(1);
            consumer.accept(new CoreEventQueue.QueuedActionInfo("flow1", "action1", warmDid, warmOldest));
            return null;
        }).when(coreEventQueue).streamQueue(eq(QUEUE_NAME), any());
        when(deltaFileFlowRepo.getOldestColdQueueEntry()).thenReturn(
                Optional.of(new OldestColdQueueEntry(coldDid, coldOldest)));

        var result = queueManagementService.getOldestQueuedInfo();

        assertNotNull(result);
        assertEquals(warmOldest, result.timestamp());
        assertEquals(warmDid, result.did());
    }

    @Test
    void testGetOldestQueuedInfo_coldIsOlder_returnsColdInfo() {
        OffsetDateTime warmOldest = OffsetDateTime.now().minusHours(1);
        OffsetDateTime coldOldest = OffsetDateTime.now().minusHours(3);
        UUID warmDid = UUID.randomUUID();
        UUID coldDid = UUID.randomUUID();

        queueManagementService.getAllQueues().put(QUEUE_NAME, 1L);
        when(flowDefinitionRepo.findAll()).thenReturn(List.of(
                FlowDefinition.builder().name("flow1").type(FlowType.TRANSFORM).build()
        ));
        doAnswer(invocation -> {
            var consumer = invocation.<java.util.function.Consumer<CoreEventQueue.QueuedActionInfo>>getArgument(1);
            consumer.accept(new CoreEventQueue.QueuedActionInfo("flow1", "action1", warmDid, warmOldest));
            return null;
        }).when(coreEventQueue).streamQueue(eq(QUEUE_NAME), any());
        when(deltaFileFlowRepo.getOldestColdQueueEntry()).thenReturn(
                Optional.of(new OldestColdQueueEntry(coldDid, coldOldest)));

        var result = queueManagementService.getOldestQueuedInfo();

        assertNotNull(result);
        assertEquals(coldOldest, result.timestamp());
        assertEquals(coldDid, result.did());
    }

    // --- Rate-limited BYTES warming tests ---

    @Test
    void testWarmRateLimitedBytesFiles_firstFileExceedsLimit_stillWarmsIt() {
        String flowName = "test-data-sink";
        String bucketName = "datasink:" + flowName;

        // Setup data sink with BYTES rate limit of 100 bytes
        DataSink dataSink = new DataSink();
        dataSink.setName(flowName);
        dataSink.setRateLimit(RateLimit.newBuilder()
                .unit(RateLimitUnit.BYTES)
                .maxAmount(100L)
                .durationSeconds(60)
                .build());

        // Create a file with 500 bytes content (exceeds limit)
        DeltaFile deltaFile = createDeltaFileWithFlow(flowName, 500L);
        List<DeltaFile> candidates = List.of(deltaFile);

        when(deltaFileFlowRepo.distinctRateLimitedColdQueuedDataSinks()).thenReturn(List.of(flowName));
        when(dataSinkService.getFlowOrThrow(flowName)).thenReturn(dataSink);
        when(rateLimitService.getAvailableTokens(bucketName)).thenReturn(100L);
        when(deltaFilesService.findRateLimitedColdQueuedFiles(flowName, 5000)).thenReturn(candidates);
        when(deltaFiPropertiesService.getDeltaFiProperties()).thenReturn(deltaFiProperties);
        when(deltaFiProperties.getInMemoryQueueSize()).thenReturn(5000);
        when(env.getProperty("schedule.maintenance")).thenReturn("true");

        queueManagementService.getCheckedQueues().set(true);
        queueManagementService.scheduleRateLimitedColdToWarm();

        // Should consume 500 bytes (exceeding the 100 limit) and warm the file
        verify(rateLimitService).consume(bucketName, 500L, 100L, Duration.ofSeconds(60));
        verify(deltaFilesService).requeueSpecificRateLimitedFiles(eq(flowName), eq(candidates));
    }

    @Test
    void testWarmRateLimitedBytesFiles_multipleFilesWithinLimit() {
        String flowName = "test-data-sink";
        String bucketName = "datasink:" + flowName;

        DataSink dataSink = new DataSink();
        dataSink.setName(flowName);
        dataSink.setRateLimit(RateLimit.newBuilder()
                .unit(RateLimitUnit.BYTES)
                .maxAmount(1000L)
                .durationSeconds(60)
                .build());

        // Create files: 100, 200, 300 bytes = 600 total, within 1000 limit
        DeltaFile file1 = createDeltaFileWithFlow(flowName, 100L);
        DeltaFile file2 = createDeltaFileWithFlow(flowName, 200L);
        DeltaFile file3 = createDeltaFileWithFlow(flowName, 300L);
        List<DeltaFile> candidates = List.of(file1, file2, file3);

        when(deltaFileFlowRepo.distinctRateLimitedColdQueuedDataSinks()).thenReturn(List.of(flowName));
        when(dataSinkService.getFlowOrThrow(flowName)).thenReturn(dataSink);
        when(rateLimitService.getAvailableTokens(bucketName)).thenReturn(1000L);
        when(deltaFilesService.findRateLimitedColdQueuedFiles(flowName, 5000)).thenReturn(candidates);
        when(deltaFiPropertiesService.getDeltaFiProperties()).thenReturn(deltaFiProperties);
        when(deltaFiProperties.getInMemoryQueueSize()).thenReturn(5000);
        when(env.getProperty("schedule.maintenance")).thenReturn("true");

        queueManagementService.getCheckedQueues().set(true);
        queueManagementService.scheduleRateLimitedColdToWarm();

        // Should consume all 600 bytes and warm all 3 files
        verify(rateLimitService).consume(bucketName, 600L, 1000L, Duration.ofSeconds(60));
        verify(deltaFilesService).requeueSpecificRateLimitedFiles(eq(flowName), eq(candidates));
    }

    @Test
    void testWarmRateLimitedBytesFiles_stopsAfterExceedingLimit() {
        String flowName = "test-data-sink";
        String bucketName = "datasink:" + flowName;

        DataSink dataSink = new DataSink();
        dataSink.setName(flowName);
        dataSink.setRateLimit(RateLimit.newBuilder()
                .unit(RateLimitUnit.BYTES)
                .maxAmount(500L)
                .durationSeconds(60)
                .build());

        // Create files: 300, 400, 100 bytes
        // First file (300) is under limit, second (400) would exceed, so stop after first
        DeltaFile file1 = createDeltaFileWithFlow(flowName, 300L);
        DeltaFile file2 = createDeltaFileWithFlow(flowName, 400L);
        DeltaFile file3 = createDeltaFileWithFlow(flowName, 100L);
        List<DeltaFile> candidates = List.of(file1, file2, file3);

        when(deltaFileFlowRepo.distinctRateLimitedColdQueuedDataSinks()).thenReturn(List.of(flowName));
        when(dataSinkService.getFlowOrThrow(flowName)).thenReturn(dataSink);
        when(rateLimitService.getAvailableTokens(bucketName)).thenReturn(500L);
        when(deltaFilesService.findRateLimitedColdQueuedFiles(flowName, 5000)).thenReturn(candidates);
        when(deltaFiPropertiesService.getDeltaFiProperties()).thenReturn(deltaFiProperties);
        when(deltaFiProperties.getInMemoryQueueSize()).thenReturn(5000);
        when(env.getProperty("schedule.maintenance")).thenReturn("true");

        queueManagementService.getCheckedQueues().set(true);
        queueManagementService.scheduleRateLimitedColdToWarm();

        // Should only warm file1 (300 bytes)
        verify(rateLimitService).consume(bucketName, 300L, 500L, Duration.ofSeconds(60));
        verify(deltaFilesService).requeueSpecificRateLimitedFiles(eq(flowName), argThat(list ->
            list.size() == 1 && list.contains(file1)));
    }

    @Test
    void testWarmRateLimitedFilesUnit_consumesTokensPerFile() {
        String flowName = "test-data-sink";
        String bucketName = "datasink:" + flowName;

        DataSink dataSink = new DataSink();
        dataSink.setName(flowName);
        dataSink.setRateLimit(RateLimit.newBuilder()
                .unit(RateLimitUnit.FILES)
                .maxAmount(10L)
                .durationSeconds(60)
                .build());

        when(deltaFileFlowRepo.distinctRateLimitedColdQueuedDataSinks()).thenReturn(List.of(flowName));
        when(dataSinkService.getFlowOrThrow(flowName)).thenReturn(dataSink);
        when(rateLimitService.getAvailableTokens(bucketName)).thenReturn(5L);
        when(deltaFiPropertiesService.getDeltaFiProperties()).thenReturn(deltaFiProperties);
        when(deltaFiProperties.getInMemoryQueueSize()).thenReturn(5000);
        when(env.getProperty("schedule.maintenance")).thenReturn("true");

        queueManagementService.getCheckedQueues().set(true);
        queueManagementService.scheduleRateLimitedColdToWarm();

        // Should consume 5 tokens (files) and requeue up to 5 files
        verify(rateLimitService).consume(bucketName, 5L, 10L, Duration.ofSeconds(60));
        verify(deltaFilesService).requeueRateLimitedColdQueueActions(flowName, 5);
    }

    @Test
    void testWarmRateLimitedBytesFiles_noTokensAvailable_skips() {
        String flowName = "test-data-sink";
        String bucketName = "datasink:" + flowName;

        DataSink dataSink = new DataSink();
        dataSink.setName(flowName);
        dataSink.setRateLimit(RateLimit.newBuilder()
                .unit(RateLimitUnit.BYTES)
                .maxAmount(100L)
                .durationSeconds(60)
                .build());

        when(deltaFileFlowRepo.distinctRateLimitedColdQueuedDataSinks()).thenReturn(List.of(flowName));
        when(dataSinkService.getFlowOrThrow(flowName)).thenReturn(dataSink);
        when(rateLimitService.getAvailableTokens(bucketName)).thenReturn(0L);
        // These won't be called since we bail early when tokens <= 0, but are needed for cold queue processing
        lenient().when(deltaFiPropertiesService.getDeltaFiProperties()).thenReturn(deltaFiProperties);
        lenient().when(deltaFiProperties.getInMemoryQueueSize()).thenReturn(5000);
        when(env.getProperty("schedule.maintenance")).thenReturn("true");

        queueManagementService.getCheckedQueues().set(true);
        queueManagementService.scheduleRateLimitedColdToWarm();

        // Should not consume or requeue anything for rate-limited items
        verify(rateLimitService, never()).consume(anyString(), anyLong(), anyLong(), any());
        verify(deltaFilesService, never()).requeueSpecificRateLimitedFiles(anyString(), any());
        verify(deltaFilesService, never()).requeueRateLimitedColdQueueActions(anyString(), anyInt());
    }

    @Test
    void testWarmRateLimitedFiles_dataSinkNoLongerExists_warmsAllToRemove() {
        String flowName = "deleted-data-sink";

        when(deltaFileFlowRepo.distinctRateLimitedColdQueuedDataSinks()).thenReturn(List.of(flowName));
        when(dataSinkService.getFlowOrThrow(flowName)).thenThrow(new RuntimeException("Not found"));
        when(deltaFiPropertiesService.getDeltaFiProperties()).thenReturn(deltaFiProperties);
        when(deltaFiProperties.getInMemoryQueueSize()).thenReturn(5000);
        when(env.getProperty("schedule.maintenance")).thenReturn("true");

        queueManagementService.getCheckedQueues().set(true);
        queueManagementService.scheduleRateLimitedColdToWarm();

        // Should warm all entries for the deleted sink
        verify(deltaFilesService).requeueRateLimitedColdQueueActions(flowName, 5000);
    }

    @Test
    void testWarmRateLimitedFiles_rateLimitRemoved_warmsAllToRemove() {
        String flowName = "test-data-sink";

        DataSink dataSink = new DataSink();
        dataSink.setName(flowName);
        dataSink.setRateLimit(null); // Rate limit was removed

        when(deltaFileFlowRepo.distinctRateLimitedColdQueuedDataSinks()).thenReturn(List.of(flowName));
        when(dataSinkService.getFlowOrThrow(flowName)).thenReturn(dataSink);
        when(deltaFiPropertiesService.getDeltaFiProperties()).thenReturn(deltaFiProperties);
        when(deltaFiProperties.getInMemoryQueueSize()).thenReturn(5000);
        when(env.getProperty("schedule.maintenance")).thenReturn("true");

        queueManagementService.getCheckedQueues().set(true);
        queueManagementService.scheduleRateLimitedColdToWarm();

        // Should warm all entries since rate limit was removed
        verify(deltaFilesService).requeueRateLimitedColdQueueActions(flowName, 5000);
    }

    @Test
    void testRateLimitedColdToWarmDefersChecksUntilNextEligibleInterval() {
        String flowName = "test-data-sink";
        String bucketName = "datasink:" + flowName;

        DataSink dataSink = new DataSink();
        dataSink.setName(flowName);
        dataSink.setRateLimit(RateLimit.newBuilder()
                .unit(RateLimitUnit.FILES)
                .maxAmount(1L)
                .durationSeconds(60)
                .build());

        when(deltaFileFlowRepo.distinctRateLimitedColdQueuedDataSinks()).thenReturn(List.of(flowName));
        when(dataSinkService.getFlowOrThrow(flowName)).thenReturn(dataSink);
        when(rateLimitService.getAvailableTokens(bucketName)).thenReturn(0L);
        when(env.getProperty("schedule.maintenance")).thenReturn("true");

        queueManagementService.getCheckedQueues().set(true);
        queueManagementService.scheduleRateLimitedColdToWarm();
        queueManagementService.scheduleRateLimitedColdToWarm();

        verify(rateLimitService, times(1)).getAvailableTokens(bucketName);
    }

    @Test
    void testRateLimitedBacklogSetSelfHealsFromDbScan() {
        String flowName = "test-data-sink";
        String bucketName = "datasink:" + flowName;

        DataSink dataSink = new DataSink();
        dataSink.setName(flowName);
        dataSink.setRateLimit(RateLimit.newBuilder()
                .unit(RateLimitUnit.FILES)
                .maxAmount(1L)
                .durationSeconds(1)
                .build());

        when(deltaFileFlowRepo.distinctRateLimitedColdQueuedDataSinks()).thenReturn(List.of(), List.of(flowName));
        when(env.getProperty("schedule.maintenance")).thenReturn("true");
        when(dataSinkService.getFlowOrThrow(flowName)).thenReturn(dataSink);
        when(rateLimitService.getAvailableTokens(bucketName)).thenReturn(1L);
        when(deltaFiPropertiesService.getDeltaFiProperties()).thenReturn(deltaFiProperties);
        when(deltaFiProperties.getInMemoryQueueSize()).thenReturn(5000);

        queueManagementService.getCheckedQueues().set(true);
        queueManagementService.scheduleRateLimitedColdToWarm();
        queueManagementService.scheduleRateLimitedColdToWarm();

        verify(deltaFileFlowRepo, times(2)).distinctRateLimitedColdQueuedDataSinks();
        verify(dataSinkService).getFlowOrThrow(flowName);
    }

    @Test
    void testHasRateLimitedBacklogFallsBackToDbExists() {
        String flowName = "test-data-sink";

        when(deltaFileFlowRepo.existsRateLimitedColdQueuedDataSink(flowName)).thenReturn(true, true);

        assertTrue(queueManagementService.hasRateLimitedBacklog(flowName));
        assertTrue(queueManagementService.hasRateLimitedBacklog(flowName));
        verify(deltaFileFlowRepo, times(2)).existsRateLimitedColdQueuedDataSink(flowName);
    }

    private DeltaFile createDeltaFileWithFlow(String flowName, long contentSize) {
        UUID did = UUID.randomUUID();

        DeltaFileFlow flow = mock(DeltaFileFlow.class, withSettings().strictness(Strictness.LENIENT));
        when(flow.getName()).thenReturn(flowName);
        when(flow.getState()).thenReturn(DeltaFileFlowState.IN_FLIGHT);
        when(flow.lastContentSize()).thenReturn(contentSize);

        DeltaFile deltaFile = mock(DeltaFile.class, withSettings().strictness(Strictness.LENIENT));
        when(deltaFile.getDid()).thenReturn(did);
        when(deltaFile.getFlows()).thenReturn(new LinkedHashSet<>(List.of(flow)));

        return deltaFile;
    }
}
