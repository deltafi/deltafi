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

import org.deltafi.common.action.ActionEventQueue;
import org.deltafi.common.types.ActionConfiguration;
import org.deltafi.common.types.ActionDescriptor;
import org.deltafi.common.types.ActionType;
import org.deltafi.common.types.TransformActionConfiguration;
import org.deltafi.core.configuration.DeltaFiProperties;
import org.deltafi.core.repo.DeltaFileRepo;
import org.deltafi.core.types.ColdQueuedActionSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QueueManagementServiceTest {

    private static final String QUEUE_NAME = "queueName";
    private static final ActionDescriptor ACTION_DESCRIPTOR = new ActionDescriptor(QUEUE_NAME, "", ActionType.TRANSFORM, null, null, null);
    private static final String ACTION_NAME = "actionName";
    private static final ColdQueuedActionSummary COLD_QUEUED_ACTION_SUMMARY = new ColdQueuedActionSummary(ACTION_NAME, ActionType.TRANSFORM, 1);
    private static final ActionConfiguration ACTION_CONFIGURATION = new TransformActionConfiguration(ACTION_NAME, QUEUE_NAME);

    @Mock
    ActionDescriptorService actionDescriptorService;

    @Mock
    ActionEventQueue actionEventQueue;

    @Mock
    DeltaFileRepo deltaFileRepo;

    @Mock
    UnifiedFlowService unifiedFlowService;

    @Mock
    DeltaFilesService deltaFilesService;

    @Mock
    DeltaFiPropertiesService deltaFiPropertiesService;

    @Mock
    DeltaFiProperties deltaFiProperties;

    @InjectMocks
    QueueManagementService queueManagementService;

    @Test
    void testIdentifyColdQueuesAdd() {
        when(actionEventQueue.keys()).thenReturn(Set.of(QUEUE_NAME));
        when(actionDescriptorService.getAll()).thenReturn(List.of(ACTION_DESCRIPTOR));
        when(deltaFiPropertiesService.getDeltaFiProperties()).thenReturn(deltaFiProperties);
        when(deltaFiProperties.getInMemoryQueueSize()).thenReturn(10);
        when(actionEventQueue.size(QUEUE_NAME)).thenReturn(12L);

        queueManagementService.identifyColdQueues();
        assertTrue(queueManagementService.coldQueue(QUEUE_NAME));
    }

    @Test
    void testIdentifyColdQueuesUpdate() {
        queueManagementService.getColdQueues().put(QUEUE_NAME, 500L);
        when(actionEventQueue.keys()).thenReturn(Set.of(QUEUE_NAME));
        when(actionDescriptorService.getAll()).thenReturn(List.of(ACTION_DESCRIPTOR));
        when(deltaFiPropertiesService.getDeltaFiProperties()).thenReturn(deltaFiProperties);
        when(deltaFiProperties.getInMemoryQueueSize()).thenReturn(10);
        when(actionEventQueue.size(QUEUE_NAME)).thenReturn(400L);

        queueManagementService.identifyColdQueues();
        assertTrue(queueManagementService.coldQueue(QUEUE_NAME));
        assertEquals(400L, queueManagementService.getColdQueues().get(QUEUE_NAME));
    }

    @Test
    void testIdentifyColdQueuesRemove() {
        queueManagementService.getColdQueues().put(QUEUE_NAME, 500L);
        when(actionEventQueue.keys()).thenReturn(Set.of(QUEUE_NAME));
        when(actionDescriptorService.getAll()).thenReturn(List.of(ACTION_DESCRIPTOR));
        when(deltaFiPropertiesService.getDeltaFiProperties()).thenReturn(deltaFiProperties);
        when(deltaFiProperties.getInMemoryQueueSize()).thenReturn(10);
        when(actionEventQueue.size(QUEUE_NAME)).thenReturn(8L);

        queueManagementService.identifyColdQueues();
        assertFalse(queueManagementService.coldQueue(QUEUE_NAME));
    }

    @Test
    void testColdQueueExists() {
        queueManagementService.getColdQueues().put(QUEUE_NAME, 10L);
        assertTrue(queueManagementService.coldQueue(QUEUE_NAME));
    }

    @Test
    void testColdQueueDoesNotExist() {
        assertFalse(queueManagementService.coldQueue(QUEUE_NAME));
    }

    @Test
    void testColdToWarmWithoutCheckedQueues() {
        queueManagementService.getCheckedQueues().set(false);
        queueManagementService.coldToWarm();
        verify(deltaFileRepo, times(0)).coldQueuedActionsSummary();
    }

    @Test
    void testColdToWarmWithCheckedQueues() {
        queueManagementService.getCheckedQueues().set(true);
        queueManagementService.getColdQueues().put(QUEUE_NAME, 8L);
        when(deltaFiPropertiesService.getDeltaFiProperties()).thenReturn(deltaFiProperties);
        when(deltaFiProperties.getInMemoryQueueSize()).thenReturn(10);
        when(deltaFileRepo.coldQueuedActionsSummary()).thenReturn(List.of(COLD_QUEUED_ACTION_SUMMARY));
        when(unifiedFlowService.runningAction(ACTION_NAME, ActionType.TRANSFORM)).thenReturn(ACTION_CONFIGURATION);
        queueManagementService.coldToWarm();
        verify(deltaFilesService).requeueColdQueueActions(List.of(ACTION_NAME), 2);
    }

    @Test
    void testColdQueueActions() {
        queueManagementService.getColdQueues().put(QUEUE_NAME, 8L);
        when(unifiedFlowService.allActionConfigurations()).thenReturn(List.of(ACTION_CONFIGURATION));
        assertEquals(Set.of(ACTION_NAME), queueManagementService.coldQueueActions());
    }
}