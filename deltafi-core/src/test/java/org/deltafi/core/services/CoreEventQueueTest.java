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
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.SneakyThrows;
import org.deltafi.common.action.EventQueueProperties;
import org.deltafi.common.queue.jackey.ValkeyKeyedBlockingQueue;
import org.deltafi.common.types.ActionEvent;
import org.deltafi.common.types.ActionExecution;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URISyntaxException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CoreEventQueueTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private static final String QUEUE_NAME = "queueName";
    private static final String DGS_QUEUE_NAME = "dgs-" + QUEUE_NAME;
    private static final UUID DID = UUID.randomUUID();
    private static final String GOOD_BASIC = """
            {
              "did": "%s",
              "actionName": "flowName.ActionName",
              "start": "2021-07-11T13:44:22.183Z",
              "stop": "2021-07-11T13:44:22.184Z",
              "type": "TRANSFORM",
              "transform": [
                {
                  "annotations": {
                    "first": "one",
                    "second": "two"
                  }
                }
              ]
            }
            """.formatted(DID.toString());
    private static final String GOOD_UNICODE = """
            {
                "did": "%s",
                "actionName": "āȂ.̃Є",
                "start": "2021-07-11T13:44:22.183Z",
                "stop": "2021-07-11T13:44:22.184Z",
                "type": "TRANSFORM",
                "transform": [{
                    "annotations": {
                        "first": "one",
                        "second": "two"
                    }
                }]
            }
            """.formatted(DID.toString());
    private static final String EXTRA_FIELDS_IGNORED = """
            {
                "did": "%s",
                "extra": "field",
                "actionName": "sampleTransform.SampleTransformAction",
                "start": "2021-07-11T13:44:22.183Z",
                "stop": "2021-07-11T13:44:22.184Z",
                "type": "TRANSFORM",
                "somethingElse": {
                    "annotations": {
                        "first": "one",
                        "second": "two"
                    }
                }
            }
            """.formatted(DID.toString());
    private static final String ILLEGAL_CONTROL_CHARS = """
            {
                "did": "%s",
                "actionName": "\u0000\u0001醑Ȃ",
                "start": "2021-07-11T13:44:22.183Z",
                "stop": "2021-07-11T13:44:22.184Z",
                "type": "TRANSFORM",
                "transform": {
                    "annotations": {
                        "first": "one",
                        "second": "two"
                    }
                }
            }
            """.formatted(DID.toString());
    private static final String INVALID_DATE = """
            {
                "did": "%s",
                "actionName": "sampleTransform.SampleTransformAction",
                "start": "NOTADATETIME",
                "stop": "2021-07-11T13:44:22.184Z",
                "type": "TRANSFORM",
                "transform": {
                    "annotations": {
                        "first": "one",
                        "second": "two"
                    }
                }
            }
            """.formatted(DID.toString());
    private static final String METRICS_OVERFLOW = """
            {
                "did": "%s",
                "actionName": "sampleTransform.SampleTransformAction",
                "start": "2021-07-11T13:44:22.183Z",
                "stop": "2021-07-11T13:44:22.184Z",
                "type": "TRANSFORM",
                "metrics": [
                    {
                        "name": "my-metric", "value": 12345678901234567890, "tags": { "this": "that" }
                     }
                ],
                "transform": {
                    "annotations": {
                        "first": "one",
                        "second": "two"
                    }
                }
            }
            """.formatted(DID.toString());

    @Test
    void testConvertBasic() throws JsonProcessingException, URISyntaxException {
        try (MockedConstruction<ValkeyKeyedBlockingQueue> mock =
                     Mockito.mockConstruction(ValkeyKeyedBlockingQueue.class, (mockJackey, context)
                             -> when(mockJackey.take(DGS_QUEUE_NAME))
                                     .thenReturn(GOOD_BASIC))) {

            CoreEventQueue coreEventQueue = new CoreEventQueue(new EventQueueProperties(), 2);
            assertEquals(1, mock.constructed().size());
            ActionEvent actionEvent = coreEventQueue.takeResult(QUEUE_NAME);
            assertEquals(DID, actionEvent.getDid());
        }
    }

    @Test
    void testConvertUnicode() throws JsonProcessingException, URISyntaxException {
        try (MockedConstruction<ValkeyKeyedBlockingQueue> mock =
                     Mockito.mockConstruction(ValkeyKeyedBlockingQueue.class, (mockJackey, context)
                             -> when(mockJackey.take(DGS_QUEUE_NAME))
                                     .thenReturn(GOOD_UNICODE))) {

            CoreEventQueue coreEventQueue = new CoreEventQueue(new EventQueueProperties(), 2);
            assertEquals(1, mock.constructed().size());
            ActionEvent actionEvent = coreEventQueue.takeResult(QUEUE_NAME);
            assertEquals("āȂ.̃Є", actionEvent.getActionName());
        }
    }

    @Test
    void testExtraFieldsIgnored() throws JsonProcessingException, URISyntaxException {
        try (MockedConstruction<ValkeyKeyedBlockingQueue> mock =
                     Mockito.mockConstruction(ValkeyKeyedBlockingQueue.class, (mockJackey, context)
                             -> when(mockJackey.take(DGS_QUEUE_NAME))
                                     .thenReturn(EXTRA_FIELDS_IGNORED))) {

            CoreEventQueue coreEventQueue = new CoreEventQueue(new EventQueueProperties(), 2);
            assertEquals(1, mock.constructed().size());
            ActionEvent actionEvent = coreEventQueue.takeResult(QUEUE_NAME);
            assertEquals(DID, actionEvent.getDid());
        }
    }

    @Test
    void testWrongJsonType() throws URISyntaxException {
        try (MockedConstruction<ValkeyKeyedBlockingQueue> mock =
                     Mockito.mockConstruction(ValkeyKeyedBlockingQueue.class)) {

            CoreEventQueue coreEventQueue = new CoreEventQueue(new EventQueueProperties(), 2);
            assertEquals(1, mock.constructed().size());
            ValkeyKeyedBlockingQueue mockJackey = mock.constructed().getFirst();
            when(mockJackey.take(DGS_QUEUE_NAME))
                    .thenReturn(getActionEventsArray());
            org.assertj.core.api.Assertions.assertThatThrownBy(
                            () -> coreEventQueue.takeResult(QUEUE_NAME))
                    .isInstanceOf(JsonProcessingException.class)
                    .hasMessageContaining("from Array value");
        }
    }

    @Test
    void testInvalidConversion() throws URISyntaxException {
        try (MockedConstruction<ValkeyKeyedBlockingQueue> mock =
                     Mockito.mockConstruction(ValkeyKeyedBlockingQueue.class)) {

            CoreEventQueue coreEventQueue = new CoreEventQueue(new EventQueueProperties(), 2);
            assertEquals(1, mock.constructed().size());
            ValkeyKeyedBlockingQueue mockJackey = mock.constructed().getFirst();
            when(mockJackey.take(DGS_QUEUE_NAME))
                    .thenReturn(INVALID_DATE);
            org.assertj.core.api.Assertions.assertThatThrownBy(
                            () -> coreEventQueue.takeResult(QUEUE_NAME))
                    .isInstanceOf(JsonProcessingException.class)
                    .hasMessageContaining("Cannot deserialize value of type `java.time.OffsetDateTime");
        }
    }

    @Test
    void testIllegalControlChars() throws URISyntaxException {
        try (MockedConstruction<ValkeyKeyedBlockingQueue> mock =
                     Mockito.mockConstruction(ValkeyKeyedBlockingQueue.class)) {

            CoreEventQueue coreEventQueue = new CoreEventQueue(new EventQueueProperties(), 2);
            assertEquals(1, mock.constructed().size());
            ValkeyKeyedBlockingQueue mockJackey = mock.constructed().getFirst();
            when(mockJackey.take(DGS_QUEUE_NAME))
                    .thenReturn(ILLEGAL_CONTROL_CHARS);
            org.assertj.core.api.Assertions.assertThatThrownBy(
                            () -> coreEventQueue.takeResult(QUEUE_NAME))
                    .isInstanceOf(JsonProcessingException.class)
                    .hasMessageContaining("Illegal unquoted character");
        }
    }

    @Test
    void testMetricsOverflow() throws URISyntaxException {
        try (MockedConstruction<ValkeyKeyedBlockingQueue> mock =
                     Mockito.mockConstruction(ValkeyKeyedBlockingQueue.class)) {

            CoreEventQueue coreEventQueue = new CoreEventQueue(new EventQueueProperties(), 2);
            assertEquals(1, mock.constructed().size());
            ValkeyKeyedBlockingQueue mockJackey = mock.constructed().getFirst();
            when(mockJackey.take(DGS_QUEUE_NAME))
                    .thenReturn(METRICS_OVERFLOW);

            org.assertj.core.api.Assertions.assertThatThrownBy(
                            () -> coreEventQueue.takeResult(QUEUE_NAME))
                    .isInstanceOf(JsonProcessingException.class)
                    .hasMessageContaining("Numeric value (12345678901234567890) out of range of long");
        }
    }

    private String getActionEventsArray() {
        return "[" + GOOD_BASIC + GOOD_BASIC + "]";
    }

    @Test
    @SneakyThrows
    void testGetLongRunningTasks() {
        try (MockedConstruction<ValkeyKeyedBlockingQueue> ignored = Mockito.mockConstruction(ValkeyKeyedBlockingQueue.class, (mockJackey, context)
                -> when(mockJackey.getLongRunningTasks())
                .thenReturn(Map.of("TestClass:testAction:a3aeb57e-180f-4ea5-a997-2fd291e1d8e1", OBJECT_MAPPER.writeValueAsString(List.of(OffsetDateTime.now().minusMinutes(5), OffsetDateTime.now().minusSeconds(5))),
                        "TestClass:testAction:a3aeb57e-180f-4ea5-a997-2fd291e1d8e2", OBJECT_MAPPER.writeValueAsString(List.of(OffsetDateTime.now().minusMinutes(5), OffsetDateTime.now().minusSeconds(1))),
                        "TestClass:testAction:a3aeb57e-180f-4ea5-a997-2fd291e1d8e3", OBJECT_MAPPER.writeValueAsString(List.of(OffsetDateTime.now().minusMinutes(5), OffsetDateTime.now().minusHours(1))))))) {

            CoreEventQueue coreEventQueue = new CoreEventQueue(new EventQueueProperties(), 2);
            List<ActionExecution> result = coreEventQueue.getLongRunningTasks();
            assertEquals(2, result.size());
        }
    }



    @Test
    @SneakyThrows
    void testLongRunningTaskExists() {
        try (MockedConstruction<ValkeyKeyedBlockingQueue> ignored = Mockito.mockConstruction(ValkeyKeyedBlockingQueue.class, (mockJackey, context)
                -> when(mockJackey.getLongRunningTask("TestClass:testAction:" + DID))
                .thenReturn(OBJECT_MAPPER.writeValueAsString(List.of(OffsetDateTime.now().minusMinutes(5), OffsetDateTime.now().minusSeconds(1)))))) {

            CoreEventQueue coreEventQueue = new CoreEventQueue(new EventQueueProperties(), 2);
            boolean exists = coreEventQueue.longRunningTaskExists("TestClass", "testAction", DID);
            assertTrue(exists);
        }
    }

    @Test
    @SneakyThrows
    void testLongRunningTaskExistsExpired() {
        try (MockedConstruction<ValkeyKeyedBlockingQueue> ignored = Mockito.mockConstruction(ValkeyKeyedBlockingQueue.class, (mockJackey, context)
                -> when(mockJackey.getLongRunningTask("TestClass:testAction:" + DID))
                .thenReturn(OBJECT_MAPPER.writeValueAsString(List.of(OffsetDateTime.now().minusMinutes(5), OffsetDateTime.now().minusHours(100)))))) {

            CoreEventQueue coreEventQueue = new CoreEventQueue(new EventQueueProperties(), 2);
            boolean exists = coreEventQueue.longRunningTaskExists("TestClass", "testAction", DID);
            assertFalse(exists);
        }
    }

    @Test
    @SneakyThrows
    void testRemoveExpiredLongRunningTasks() {
        try (MockedConstruction<ValkeyKeyedBlockingQueue> mock = Mockito.mockConstruction(ValkeyKeyedBlockingQueue.class, (mockJackey, context)
                -> when(mockJackey.getLongRunningTasks())
                .thenReturn(Map.of("TestClass:testAction:a3aeb57e-180f-4ea5-a997-2fd291e1d8e1", OBJECT_MAPPER.writeValueAsString(List.of(OffsetDateTime.now().minusMinutes(30), OffsetDateTime.now().minusMinutes(25))),
                        "TestClass:testAction:a3aeb57e-180f-4ea5-a997-2fd291e1d8e2", OBJECT_MAPPER.writeValueAsString(List.of(OffsetDateTime.now().minusMinutes(5), OffsetDateTime.now().minusSeconds(1))))))) {

            CoreEventQueue coreEventQueue = new CoreEventQueue(new EventQueueProperties(), 2);
            coreEventQueue.removeExpiredLongRunningTasks();
            verify(mock.constructed().getFirst(), times(1)).removeLongRunningTask("TestClass:testAction:a3aeb57e-180f-4ea5-a997-2fd291e1d8e1");
        }
    }
}
