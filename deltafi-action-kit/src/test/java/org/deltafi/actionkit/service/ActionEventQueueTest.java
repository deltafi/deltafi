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
package org.deltafi.actionkit.service;

import lombok.SneakyThrows;
import org.deltafi.common.queue.jackey.ValkeyKeyedBlockingQueue;
import org.deltafi.common.types.ActionExecution;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActionEventQueueTest {
    private static final ActionExecution ACTION_EXECUTION = new ActionExecution("TestClass", "testAction",0,  UUID.randomUUID(), OffsetDateTime.now(), "appName");

    @Mock
    private ValkeyKeyedBlockingQueue valkeyKeyedBlockingQueue;

    @Test
    @SneakyThrows
    void testRecordLongRunningTask() {
        ActionEventQueue actionEventQueue = new ActionEventQueue(valkeyKeyedBlockingQueue);
        actionEventQueue.recordLongRunningTask(ACTION_EXECUTION);
        verify(valkeyKeyedBlockingQueue, times(1)).recordLongRunningTask(anyString(), anyString());
    }

    @Test
    @SneakyThrows
    void testRemoveLongRunningTask() {
        ActionEventQueue actionEventQueue = new ActionEventQueue(valkeyKeyedBlockingQueue);
        actionEventQueue.removeLongRunningTask(ACTION_EXECUTION);
        verify(valkeyKeyedBlockingQueue, times(1)).removeLongRunningTask(ACTION_EXECUTION.key());
    }
}
