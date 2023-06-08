/**
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
package org.deltafi.common.action;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.deltafi.common.queue.jedis.JedisKeyedBlockingQueue;
import org.deltafi.common.types.ActionEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
public class ActionEventQueueTest {

    private final static String QUEUE_NAME = "queueName";
    private final static String DGS_QUEUE_NAME = "dgs-" + QUEUE_NAME;
    private final static String GOOD_BASIC = """
            {
                "did": "did",
                "action": "flowName.ActionName",
                "start": "2021-07-11T13:44:22.183Z",
                "stop": "2021-07-11T13:44:22.184Z",
                "type": "ENRICH",
                "enrich": {
                    "enrichments": [ { "name": "sampleEnrichment", "value": "enrichmentData", "mediaType": "application/octet-stream" } ],
                    "annotations": {
                        "first": "one",
                        "second": "two"
                    }
                }
            }
            """;
    private final static String GOOD_UNICODE = """
            {
                "did": "did",
                "action": "\u0101\u0202.\u0303\u0404",
                "start": "2021-07-11T13:44:22.183Z",
                "stop": "2021-07-11T13:44:22.184Z",
                "type": "ENRICH",
                "enrich": {
                    "enrichments": [ { "name": "sampleEnrichment", "value": "enrichmentData", "mediaType": "application/octet-stream" } ],
                    "annotations": {
                        "first": "one",
                        "second": "two"
                    }
                }
            }
            """;
    private final static String EXTRA_FIELDS_IGNORED = """
            {
                "did": "did",
                "extra": "field",
                "action": "sampleEnrich.SampleEnrichAction",
                "start": "2021-07-11T13:44:22.183Z",
                "stop": "2021-07-11T13:44:22.184Z",
                "type": "ENRICH",
                "somethingElse": {
                    "enrichments": [ { "name": "sampleEnrichment", "value": "enrichmentData", "mediaType": "application/octet-stream" } ],
                    "annotations": {
                        "first": "one",
                        "second": "two"
                    }
                }
            }
            """;
    private final static String ILLEGAL_CONTROL_CHARS = """
            {
                "did": "did",
                "action": "\u0000\u0001\u9191\u0202",
                "start": "2021-07-11T13:44:22.183Z",
                "stop": "2021-07-11T13:44:22.184Z",
                "type": "ENRICH",
                "enrich": {
                    "enrichments": [ { "name": "sampleEnrichment", "value": "enrichmentData", "mediaType": "application/octet-stream" } ],
                    "annotations": {
                        "first": "one",
                        "second": "two"
                    }
                }
            }
            """;
    private final static String INVALID_DATE = """
            {
                "did": "did",
                "action": "sampleEnrich.SampleEnrichAction",
                "start": "NOTADATETIME",
                "stop": "2021-07-11T13:44:22.184Z",
                "type": "ENRICH",
                "enrich": {
                    "enrichments": [ { "name": "sampleEnrichment", "value": "enrichmentData", "mediaType": "application/octet-stream" } ],
                    "annotations": {
                        "first": "one",
                        "second": "two"
                    }
                }
            }
            """;
    private final static String METRICS_OVERFLOW = """
            {
                "did": "did",
                "action": "sampleEnrich.SampleEnrichAction",
                "start": "2021-07-11T13:44:22.183Z",
                "stop": "2021-07-11T13:44:22.184Z",
                "type": "ENRICH",
                "metrics": [
                    {
                        "name": "my-metric", "value": 12345678901234567890, "tags": { "this": "that" }
                     }
                ],
                "enrich": {
                    "enrichments": [ { "name": "sampleEnrichment", "value": "enrichmentData", "mediaType": "application/octet-stream" } ],
                    "annotations": {
                        "first": "one",
                        "second": "two"
                    }
                }
            }
            """;

    private final static String RANDOM_STRING =
            "jaklfjads;lfjlj13kl;4j3kl24j3l;2jdslj ;l 44298471298jdflkas";

    @Test
    public void testConvertBasic() throws JsonProcessingException, URISyntaxException {
        try (MockedConstruction<JedisKeyedBlockingQueue> mock =
                     Mockito.mockConstruction(JedisKeyedBlockingQueue.class, (mockJedis, context)
                             -> {
                         Mockito.when(mockJedis.take(DGS_QUEUE_NAME))
                                 .thenReturn(GOOD_BASIC);
                     })) {

            ActionEventQueue actionEventQueue = new ActionEventQueue(new ActionEventQueueProperties(), 2);
            assertEquals(1, mock.constructed().size());
            ActionEvent actionEvent = actionEventQueue.takeResult(QUEUE_NAME);
            assertEquals("did", actionEvent.getDid());
        }
    }

    @Test
    public void testConvertLarge() throws JsonProcessingException, URISyntaxException {
        try (MockedConstruction<JedisKeyedBlockingQueue> mock =
                     Mockito.mockConstruction(JedisKeyedBlockingQueue.class, (mockJedis, context)
                             -> {
                         Mockito.when(mockJedis.take(DGS_QUEUE_NAME))
                                 .thenReturn(loadFile("largeFile"));
                     })) {

            ActionEventQueue actionEventQueue = new ActionEventQueue(new ActionEventQueueProperties(), 2);
            assertEquals(1, mock.constructed().size());
            ActionEvent actionEvent = actionEventQueue.takeResult(QUEUE_NAME);
            assertEquals("did", actionEvent.getDid());
            assertEquals(20_000, actionEvent.getEnrich().getAnnotations().size());
        }
    }

    @Test
    public void testConvertUnicode() throws JsonProcessingException, URISyntaxException {
        try (MockedConstruction<JedisKeyedBlockingQueue> mock =
                     Mockito.mockConstruction(JedisKeyedBlockingQueue.class, (mockJedis, context)
                             -> {
                         Mockito.when(mockJedis.take(DGS_QUEUE_NAME))
                                 .thenReturn(GOOD_UNICODE);
                     })) {

            ActionEventQueue actionEventQueue = new ActionEventQueue(new ActionEventQueueProperties(), 2);
            assertEquals(1, mock.constructed().size());
            ActionEvent actionEvent = actionEventQueue.takeResult(QUEUE_NAME);
            assertEquals("\u0101\u0202.\u0303\u0404", actionEvent.getAction());
        }
    }

    @Test
    public void testExtraFieldsIgnored() throws JsonProcessingException, URISyntaxException {
        try (MockedConstruction<JedisKeyedBlockingQueue> mock =
                     Mockito.mockConstruction(JedisKeyedBlockingQueue.class, (mockJedis, context)
                             -> {
                         Mockito.when(mockJedis.take(DGS_QUEUE_NAME))
                                 .thenReturn(EXTRA_FIELDS_IGNORED);
                     })) {

            ActionEventQueue actionEventQueue = new ActionEventQueue(new ActionEventQueueProperties(), 2);
            assertEquals(1, mock.constructed().size());
            ActionEvent actionEvent = actionEventQueue.takeResult(QUEUE_NAME);
            assertEquals("did", actionEvent.getDid());
        }
    }

    @Test
    public void testWrongJsonType() throws URISyntaxException {
        try (MockedConstruction<JedisKeyedBlockingQueue> mock =
                     Mockito.mockConstruction(JedisKeyedBlockingQueue.class)) {

            ActionEventQueue actionEventQueue = new ActionEventQueue(new ActionEventQueueProperties(), 2);
            assertEquals(1, mock.constructed().size());
            JedisKeyedBlockingQueue mockJedis = mock.constructed().get(0);
            Mockito.when(mockJedis.take(DGS_QUEUE_NAME))
                    .thenReturn(getActionEventsArray());
            org.assertj.core.api.Assertions.assertThatThrownBy(
                            () -> actionEventQueue.takeResult(QUEUE_NAME))
                    .isInstanceOf(JsonProcessingException.class)
                    .hasMessageContaining("from Array value");
        }
    }

    public void testInvalidConversion() throws URISyntaxException {
        try (MockedConstruction<JedisKeyedBlockingQueue> mock =
                     Mockito.mockConstruction(JedisKeyedBlockingQueue.class)) {

            ActionEventQueue actionEventQueue = new ActionEventQueue(new ActionEventQueueProperties(), 2);
            assertEquals(1, mock.constructed().size());
            JedisKeyedBlockingQueue mockJedis = mock.constructed().get(0);
            Mockito.when(mockJedis.take(DGS_QUEUE_NAME))
                    .thenReturn(INVALID_DATE);
            org.assertj.core.api.Assertions.assertThatThrownBy(
                            () -> actionEventQueue.takeResult(QUEUE_NAME))
                    .isInstanceOf(JsonProcessingException.class)
                    .hasMessageContaining("Cannot deserialize value of type `java.time.OffsetDateTime");
        }
    }

    @Test
    public void testIllegalControlChars() throws URISyntaxException {
        try (MockedConstruction<JedisKeyedBlockingQueue> mock =
                     Mockito.mockConstruction(JedisKeyedBlockingQueue.class)) {

            ActionEventQueue actionEventQueue = new ActionEventQueue(new ActionEventQueueProperties(), 2);
            assertEquals(1, mock.constructed().size());
            JedisKeyedBlockingQueue mockJedis = mock.constructed().get(0);
            Mockito.when(mockJedis.take(DGS_QUEUE_NAME))
                    .thenReturn(ILLEGAL_CONTROL_CHARS);
            org.assertj.core.api.Assertions.assertThatThrownBy(
                            () -> actionEventQueue.takeResult(QUEUE_NAME))
                    .isInstanceOf(JsonProcessingException.class)
                    .hasMessageContaining("Illegal unquoted character");
        }
    }

    @Test
    public void testMetricsOverflow() throws URISyntaxException {
        try (MockedConstruction<JedisKeyedBlockingQueue> mock =
                     Mockito.mockConstruction(JedisKeyedBlockingQueue.class)) {

            ActionEventQueue actionEventQueue = new ActionEventQueue(new ActionEventQueueProperties(), 2);
            assertEquals(1, mock.constructed().size());
            JedisKeyedBlockingQueue mockJedis = mock.constructed().get(0);
            Mockito.when(mockJedis.take(DGS_QUEUE_NAME))
                    .thenReturn(METRICS_OVERFLOW);

            org.assertj.core.api.Assertions.assertThatThrownBy(
                            () -> actionEventQueue.takeResult(QUEUE_NAME))
                    .isInstanceOf(JsonProcessingException.class)
                    .hasMessageContaining("Numeric value (12345678901234567890) out of range of long");
        }
    }

    private String getActionEventsArray() {
        String longString = "[" + GOOD_BASIC + GOOD_BASIC + "]";
        return longString;
    }

    private String loadFile(String filename) throws IOException {
        String json = new String(Objects.requireNonNull(
                ActionEventQueueTest.class.getClassLoader().getResourceAsStream(
                        "action-events/" + filename + ".json")).readAllBytes());
        return json;
    }

}
