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

import org.deltafi.common.types.ActionConfiguration;
import org.deltafi.common.types.ActionType;
import org.deltafi.common.types.FlowType;
import org.deltafi.core.configuration.DeltaFiProperties;
import org.deltafi.core.repo.DeltaFileFlowRepo;
import org.deltafi.core.repo.DeltaFileFlowRepoCustom.OldestColdQueueEntry;
import org.deltafi.core.repo.FlowDefinitionRepo;
import org.deltafi.core.types.FlowDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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

    @InjectMocks
    QueueManagementService queueManagementService;

    @Test
    void testIdentifyColdQueuesAdd() {
        when(coreEventQueue.keys()).thenReturn(Set.of(QUEUE_NAME));
        when(unifiedFlowService.allActionConfigurations()).thenReturn(List.of(ACTION_CONFIGURATION));
        when(deltaFiPropertiesService.getDeltaFiProperties()).thenReturn(deltaFiProperties);
        when(deltaFiProperties.getInMemoryQueueSize()).thenReturn(10);
        when(coreEventQueue.size(QUEUE_NAME)).thenReturn(12L);

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
        when(coreEventQueue.size(QUEUE_NAME)).thenReturn(400L);

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
        when(coreEventQueue.size(QUEUE_NAME)).thenReturn(8L);

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
}