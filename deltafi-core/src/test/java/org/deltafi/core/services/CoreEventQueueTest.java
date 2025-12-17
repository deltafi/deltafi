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
import org.deltafi.common.queue.valkey.ValkeyKeyedBlockingQueue;
import org.deltafi.common.test.time.TestClock;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.ActionEvent;
import org.deltafi.common.types.ActionExecution;
import org.deltafi.common.types.ActionInput;
import io.valkey.resps.Tuple;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CoreEventQueueTest {

    @Mock
    private ValkeyKeyedBlockingQueue valkeyKeyedBlockingQueue;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private static final TestClock TEST_CLOCK = new TestClock();

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
    void testConvertBasic() throws JsonProcessingException {
        when(valkeyKeyedBlockingQueue.take(DGS_QUEUE_NAME)).thenReturn(GOOD_BASIC);

        CoreEventQueue coreEventQueue = new CoreEventQueue(valkeyKeyedBlockingQueue, TEST_CLOCK);
        ActionEvent actionEvent = coreEventQueue.takeResult(QUEUE_NAME);
        assertEquals(DID, actionEvent.getDid());
    }

    @Test
    void testConvertUnicode() throws JsonProcessingException {
        when(valkeyKeyedBlockingQueue.take(DGS_QUEUE_NAME)).thenReturn(GOOD_UNICODE);

        CoreEventQueue coreEventQueue = new CoreEventQueue(valkeyKeyedBlockingQueue, TEST_CLOCK);
        ActionEvent actionEvent = coreEventQueue.takeResult(QUEUE_NAME);
        assertEquals("āȂ.̃Є", actionEvent.getActionName());
    }

    @Test
    void testExtraFieldsIgnored() throws JsonProcessingException {
        when(valkeyKeyedBlockingQueue.take(DGS_QUEUE_NAME)).thenReturn(EXTRA_FIELDS_IGNORED);

        CoreEventQueue coreEventQueue = new CoreEventQueue(valkeyKeyedBlockingQueue, TEST_CLOCK);
        ActionEvent actionEvent = coreEventQueue.takeResult(QUEUE_NAME);
        assertEquals(DID, actionEvent.getDid());
    }

    @Test
    void testWrongJsonType() {
        when(valkeyKeyedBlockingQueue.take(DGS_QUEUE_NAME)).thenReturn(getActionEventsArray());

        CoreEventQueue coreEventQueue = new CoreEventQueue(valkeyKeyedBlockingQueue, TEST_CLOCK);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> coreEventQueue.takeResult(QUEUE_NAME))
                .isInstanceOf(JsonProcessingException.class)
                .hasMessageContaining("from Array value");
    }

    @Test
    void testInvalidConversion() {
        when(valkeyKeyedBlockingQueue.take(DGS_QUEUE_NAME)).thenReturn(INVALID_DATE);

        CoreEventQueue coreEventQueue = new CoreEventQueue(valkeyKeyedBlockingQueue, TEST_CLOCK);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> coreEventQueue.takeResult(QUEUE_NAME))
                .isInstanceOf(JsonProcessingException.class)
                .hasMessageContaining("Cannot deserialize value of type `java.time.OffsetDateTime");
    }

    @Test
    void testIllegalControlChars() {
        when(valkeyKeyedBlockingQueue.take(DGS_QUEUE_NAME)).thenReturn(ILLEGAL_CONTROL_CHARS);

        CoreEventQueue coreEventQueue = new CoreEventQueue(valkeyKeyedBlockingQueue, TEST_CLOCK);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> coreEventQueue.takeResult(QUEUE_NAME))
                .isInstanceOf(JsonProcessingException.class)
                .hasMessageContaining("Illegal unquoted character");
    }

    @Test
    void testMetricsOverflow() {
        when(valkeyKeyedBlockingQueue.take(DGS_QUEUE_NAME)).thenReturn(METRICS_OVERFLOW);

        CoreEventQueue coreEventQueue = new CoreEventQueue(valkeyKeyedBlockingQueue, TEST_CLOCK);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> coreEventQueue.takeResult(QUEUE_NAME))
                .isInstanceOf(JsonProcessingException.class)
                .hasMessageContaining("Numeric value (12345678901234567890) out of range of long");
    }

    @Test
    @SneakyThrows
    void testGetLongRunningTasks() {
        when(valkeyKeyedBlockingQueue.getLongRunningTasks())
                .thenReturn(Map.of(
                        "TestClass:testAction:a3aeb57e-180f-4ea5-a997-2fd291e1d8e1",
                        OBJECT_MAPPER.writeValueAsString(List.of(OffsetDateTime.now().minusMinutes(5), OffsetDateTime.now().minusSeconds(5))),
                        "TestClass:testAction:a3aeb57e-180f-4ea5-a997-2fd291e1d8e2",
                        OBJECT_MAPPER.writeValueAsString(List.of(OffsetDateTime.now().minusMinutes(5), OffsetDateTime.now().minusSeconds(1))),
                        "TestClass:testAction:a3aeb57e-180f-4ea5-a997-2fd291e1d8e3",
                        OBJECT_MAPPER.writeValueAsString(List.of(OffsetDateTime.now().minusMinutes(5), OffsetDateTime.now().minusHours(1)))
                ));

        CoreEventQueue coreEventQueue = new CoreEventQueue(valkeyKeyedBlockingQueue, TEST_CLOCK);
        List<ActionExecution> result = coreEventQueue.getLongRunningTasks();
        assertEquals(2, result.size());
    }

    @Test
    @SneakyThrows
    void testLongRunningTaskExists() {
        when(valkeyKeyedBlockingQueue.getLongRunningTasks())
                .thenReturn(Map.of("TestClass:testAction:" + DID,
                        OBJECT_MAPPER.writeValueAsString(List.of(OffsetDateTime.now().minusMinutes(5), OffsetDateTime.now().minusSeconds(1)))));

        CoreEventQueue coreEventQueue = new CoreEventQueue(valkeyKeyedBlockingQueue, TEST_CLOCK);
        boolean exists = coreEventQueue.longRunningTaskExists("TestClass", "testAction", DID);
        assertTrue(exists);
    }

    @Test
    @SneakyThrows
    void testLongRunningTaskExistsExpired() {
        when(valkeyKeyedBlockingQueue.getLongRunningTasks())
                .thenReturn(Map.of("TestClass:testAction:" + DID,
                        OBJECT_MAPPER.writeValueAsString(List.of(OffsetDateTime.now().minusMinutes(5), OffsetDateTime.now().minusHours(100)))));

        CoreEventQueue coreEventQueue = new CoreEventQueue(valkeyKeyedBlockingQueue, TEST_CLOCK);
        boolean exists = coreEventQueue.longRunningTaskExists("TestClass", "testAction", DID);
        assertFalse(exists);
    }

    @Test
    @SneakyThrows
    void testRemoveExpiredLongRunningTasks() {
        when(valkeyKeyedBlockingQueue.getLongRunningTasks())
                .thenReturn(Map.of(
                        "TestClass:testAction:a3aeb57e-180f-4ea5-a997-2fd291e1d8e1",
                        OBJECT_MAPPER.writeValueAsString(List.of(OffsetDateTime.now().minusMinutes(30), OffsetDateTime.now().minusMinutes(25))),
                        "TestClass:testAction:a3aeb57e-180f-4ea5-a997-2fd291e1d8e2",
                        OBJECT_MAPPER.writeValueAsString(List.of(OffsetDateTime.now().minusMinutes(5), OffsetDateTime.now().minusSeconds(1)))
                ));

        CoreEventQueue coreEventQueue = new CoreEventQueue(valkeyKeyedBlockingQueue, TEST_CLOCK);
        coreEventQueue.removeExpiredLongRunningTasks();
        verify(valkeyKeyedBlockingQueue, times(1)).removeLongRunningTask("TestClass:testAction:a3aeb57e-180f-4ea5-a997-2fd291e1d8e1");
    }

    @Test
    @SneakyThrows
    void testStreamQueue() {
        UUID did1 = UUID.randomUUID();
        UUID did2 = UUID.randomUUID();
        long score1 = System.currentTimeMillis() - 60000; // 1 minute ago
        long score2 = System.currentTimeMillis();

        String json1 = """
                {
                    "actionContext": {
                        "did": "%s",
                        "flowName": "flow1",
                        "actionName": "action1"
                    },
                    "queueName": "%s"
                }
                """.formatted(did1.toString(), QUEUE_NAME);

        String json2 = """
                {
                    "actionContext": {
                        "did": "%s",
                        "flowName": "flow2",
                        "actionName": "action2"
                    },
                    "queueName": "%s"
                }
                """.formatted(did2.toString(), QUEUE_NAME);

        Tuple tuple1 = mock(Tuple.class);
        when(tuple1.getElement()).thenReturn(json1);
        when(tuple1.getScore()).thenReturn((double) score1);

        Tuple tuple2 = mock(Tuple.class);
        when(tuple2.getElement()).thenReturn(json2);
        when(tuple2.getScore()).thenReturn((double) score2);

        doAnswer(invocation -> {
            var consumer = invocation.<java.util.function.Consumer<Tuple>>getArgument(2);
            consumer.accept(tuple1);
            consumer.accept(tuple2);
            return null;
        }).when(valkeyKeyedBlockingQueue).scanSortedSet(eq(QUEUE_NAME), anyInt(), any());

        CoreEventQueue coreEventQueue = new CoreEventQueue(valkeyKeyedBlockingQueue, TEST_CLOCK);
        List<CoreEventQueue.QueuedActionInfo> result = new ArrayList<>();
        coreEventQueue.streamQueue(QUEUE_NAME, result::add);

        assertEquals(2, result.size());

        assertEquals("flow1", result.get(0).flowName());
        assertEquals("action1", result.get(0).actionName());
        assertEquals(did1, result.get(0).did());

        assertEquals("flow2", result.get(1).flowName());
        assertEquals("action2", result.get(1).actionName());
        assertEquals(did2, result.get(1).did());
    }

    @Test
    @SneakyThrows
    void testStreamQueueEmpty() {
        doAnswer(invocation -> null).when(valkeyKeyedBlockingQueue).scanSortedSet(eq(QUEUE_NAME), anyInt(), any());

        CoreEventQueue coreEventQueue = new CoreEventQueue(valkeyKeyedBlockingQueue, TEST_CLOCK);
        List<CoreEventQueue.QueuedActionInfo> result = new ArrayList<>();
        coreEventQueue.streamQueue(QUEUE_NAME, result::add);

        assertTrue(result.isEmpty());
    }

    @Test
    @SneakyThrows
    void testStreamQueueSkipsMalformedJson() {
        UUID did1 = UUID.randomUUID();
        long score1 = System.currentTimeMillis();

        String validJson = """
                {
                    "actionContext": {
                        "did": "%s",
                        "flowName": "flow1",
                        "actionName": "action1"
                    },
                    "queueName": "%s"
                }
                """.formatted(did1.toString(), QUEUE_NAME);

        Tuple validTuple = mock(Tuple.class);
        when(validTuple.getElement()).thenReturn(validJson);
        when(validTuple.getScore()).thenReturn((double) score1);

        Tuple malformedTuple = mock(Tuple.class);
        when(malformedTuple.getElement()).thenReturn("not valid json {{{");

        doAnswer(invocation -> {
            var consumer = invocation.<java.util.function.Consumer<Tuple>>getArgument(2);
            consumer.accept(malformedTuple);
            consumer.accept(validTuple);
            return null;
        }).when(valkeyKeyedBlockingQueue).scanSortedSet(eq(QUEUE_NAME), anyInt(), any());

        CoreEventQueue coreEventQueue = new CoreEventQueue(valkeyKeyedBlockingQueue, TEST_CLOCK);
        List<CoreEventQueue.QueuedActionInfo> result = new ArrayList<>();
        coreEventQueue.streamQueue(QUEUE_NAME, result::add);

        // Should skip the malformed one and return just the valid one
        assertEquals(1, result.size());
        assertEquals("flow1", result.get(0).flowName());
    }

    private String getActionEventsArray() {
        return "[\"not an object\"]";
    }
}
