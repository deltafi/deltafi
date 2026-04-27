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
package org.deltafi.activemq.ingress;

import org.deltafi.ingress.exception.IngressException;
import org.deltafi.ingress.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jms.core.JmsTemplate;

import javax.ws.rs.core.MediaType;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.artemis.mode=embedded",
        "spring.jms.cache.enabled=false",
        "deltafi.media-type=" + MediaType.APPLICATION_JSON,
        "deltafi.data-source=smoke-rest-data-source",
        "deltafi.metadata.key-1=a",
        "deltafi.metadata.key-2=b"
})
class ActivemqIngressApplicationTests {

    private static final Map<String, String> TEST_METADATA = Map.of("key-1", "a", "key-2", "b");

    @TestConfiguration
    public static class Configuration {
        @Bean
        public DeltaFiIngressService deltaFiIngressService() {
            return Mockito.mock(DeltaFiIngressService.class);
        }

        @Bean
        public IngressRequestor ingressRequestor(DeltaFiIngressService deltaFiIngressService) {
            return new IngressRequestor(deltaFiIngressService, null);
        }
    }

    @Autowired
    JmsTemplate jmsTemplate;

    @Autowired
    DeltaFiIngressService deltaFiIngressService;

    @BeforeEach
    void setUp() {
        Mockito.clearInvocations(deltaFiIngressService);
    }
/*
    @Test
    @SuppressWarnings("unchecked")
    void receivesMessage() throws IngressException {
        byte[] bytes = "This is a test".getBytes(StandardCharsets.UTF_8);

        jmsTemplate.convertAndSend("deltafi-ingress-topic", bytes, message -> {
            message.setStringProperty("JMSCorrelationID", "abc");
            message.setStringProperty("property_1", "c");
            message.setIntProperty("property_2", 123);
            return message;
        });

        ArgumentCaptor<IngressRequest<?>> ingressRequestCaptor = ArgumentCaptor.forClass(IngressRequest.class);
        Mockito.verify(deltaFiIngressService, Mockito.timeout(5000)).ingress(ingressRequestCaptor.capture());
        assertArrayEquals(bytes, ((IngressRequest.ByValue) ingressRequestCaptor.getValue()).bytes());
        assertEquals(MediaType.APPLICATION_JSON,
                ((IngressRequest.ByValue) ingressRequestCaptor.getValue()).mediaType());
        assertEquals("smoke-rest-data-source", ingressRequestCaptor.getValue().dataSource());
        Map<String, String> combined = new HashMap<>(TEST_METADATA);
        combined.put("JMSXDeliveryCount", "1");
        combined.put("X-Correlation-ID", "abc");
        combined.put("property_1", "c");
        combined.put("property_2", "123");
        combined.put("receivedOnDestination", "deltafi-ingress-topic");
        assertEquals(combined, ingressRequestCaptor.getValue().metadata());
    }

    @Test
    @SuppressWarnings("unchecked")
    void receivesMessageWithContentType() throws IngressException {
        byte[] bytes = "This is a test".getBytes(StandardCharsets.UTF_8);

        jmsTemplate.convertAndSend("deltafi-ingress-topic", bytes, message -> {
            message.setStringProperty("JMSCorrelationID", "abc");
            message.setStringProperty("ContentType", MediaType.APPLICATION_XML);
            message.setStringProperty("property_1", "c");
            message.setIntProperty("property_2", 123);
            return message;
        });

        ArgumentCaptor<IngressRequest<?>> ingressRequestCaptor = ArgumentCaptor.forClass(IngressRequest.class);
        Mockito.verify(deltaFiIngressService, Mockito.timeout(5000)).ingress(ingressRequestCaptor.capture());
        assertArrayEquals(bytes, ((IngressRequest.ByValue) ingressRequestCaptor.getValue()).bytes());
        assertEquals(MediaType.APPLICATION_XML,
                ((IngressRequest.ByValue) ingressRequestCaptor.getValue()).mediaType());
        assertEquals("smoke-rest-data-source", ingressRequestCaptor.getValue().dataSource());
        Map<String, String> combined = new HashMap<>(TEST_METADATA);
        combined.put("JMSXDeliveryCount", "1");
        combined.put("X-Correlation-ID", "abc");
        combined.put("ContentType", MediaType.APPLICATION_XML);
        combined.put("property_1", "c");
        combined.put("property_2", "123");
        combined.put("receivedOnDestination", "deltafi-ingress-topic");
        assertEquals(combined, ingressRequestCaptor.getValue().metadata());
    }

 */
}
