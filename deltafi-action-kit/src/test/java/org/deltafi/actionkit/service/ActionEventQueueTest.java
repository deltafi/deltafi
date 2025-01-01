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

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.SneakyThrows;
import org.deltafi.common.action.EventQueueProperties;
import org.deltafi.common.queue.jackey.ValkeyKeyedBlockingQueue;
import org.deltafi.common.types.ActionEvent;
import org.deltafi.common.types.ActionExecution;
import org.deltafi.common.types.ActionInput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ActionEventQueueTest {

    private static final UUID DID = UUID.randomUUID();

    @Test
    @SneakyThrows
    public void testRecordLongRunningTask() {
        ActionExecution actionExecution = new ActionExecution("TestClass", "testAction", DID, OffsetDateTime.now());
        try (MockedConstruction<ValkeyKeyedBlockingQueue> mock = Mockito.mockConstruction(ValkeyKeyedBlockingQueue.class)) {
            ActionEventQueue actionEventQueue = new ActionEventQueue(new EventQueueProperties(), 2);
            actionEventQueue.recordLongRunningTask(actionExecution);
            verify(mock.constructed().getFirst(), times(1)).recordLongRunningTask(anyString(), anyString());
        }
    }

    @Test
    @SneakyThrows
    public void testRemoveLongRunningTask() {
        ActionExecution actionExecution = new ActionExecution("TestClass", "testAction", DID, OffsetDateTime.now());
        try (MockedConstruction<ValkeyKeyedBlockingQueue> mock = Mockito.mockConstruction(ValkeyKeyedBlockingQueue.class)) {
            ActionEventQueue actionEventQueue = new ActionEventQueue(new EventQueueProperties(), 2);
            actionEventQueue.removeLongRunningTask(actionExecution);
            verify(mock.constructed().getFirst(), times(1)).removeLongRunningTask(actionExecution.key());
        }
    }
}
