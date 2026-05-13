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
package org.deltafi.actionkit.action.service;

import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.service.ActionEventQueue;
import org.deltafi.common.types.ActionExecution;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HeartbeatServiceTest {

    @Mock
    private ActionEventQueue actionEventQueue;

    @Mock
    private ActionRunner actionRunner;

    @Mock
    private Action<?, ?, ?> firstAction;

    @Mock
    private Action<?, ?, ?> secondAction;

    private HeartbeatService heartbeatService;

    @BeforeEach
    void buildService() {
        heartbeatService = new HeartbeatService(actionEventQueue, actionRunner);
    }

    @AfterEach
    void closeService() {
        heartbeatService.close();
    }

    @Test
    void setHeartbeatFiresOncePerAction() {
        when(actionRunner.getAllActions()).thenReturn(List.of(firstAction, secondAction));
        when(firstAction.getClassCanonicalName()).thenReturn("com.example.First");
        when(secondAction.getClassCanonicalName()).thenReturn("com.example.Second");

        heartbeatService.setHeartbeat();

        verify(actionEventQueue).setHeartbeat("com.example.First");
        verify(actionEventQueue).setHeartbeat("com.example.Second");
    }

    @Test
    void recordRunningTasksTracksAndRemovesCompletedExecutions() {
        ActionExecution firstExecution = new ActionExecution("com.example.First", "First", 0,
                UUID.randomUUID(), OffsetDateTime.now(), "test-app");
        ActionExecution secondExecution = new ActionExecution("com.example.Second", "Second", 0,
                UUID.randomUUID(), OffsetDateTime.now(), "test-app");

        when(actionRunner.getAllActions()).thenReturn(List.of(firstAction, secondAction));
        when(firstAction.getActionExecution()).thenReturn(firstExecution);
        when(secondAction.getActionExecution()).thenReturn(secondExecution);

        heartbeatService.recordRunningTasks();

        verify(actionEventQueue).recordLongRunningTask(firstExecution);
        verify(actionEventQueue).recordLongRunningTask(secondExecution);
        verify(actionEventQueue, never()).removeLongRunningTask(firstExecution);

        // second pass: firstAction finished, secondAction still running
        when(firstAction.getActionExecution()).thenReturn(null);

        heartbeatService.recordRunningTasks();

        verify(actionEventQueue).removeLongRunningTask(firstExecution);
        verify(actionEventQueue, times(2)).recordLongRunningTask(secondExecution);
    }
}
