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
package org.deltafi.nats.ingress;

import io.nats.client.*;
import io.nats.client.impl.Headers;
import org.deltafi.common.test.time.TestClock;
import org.deltafi.ingress.exception.IngressException;
import org.deltafi.ingress.service.*;
import org.deltafi.nats.ingress.config.NatsProperties;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@SpringBootTest
@Testcontainers
class NatsIngressApplicationTests {
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

        @Bean
        public Clock clock() {
            return new TestClock();
        }
    }

    @Container
    public static final GenericContainer<?> NATS_CONTAINER =
            new GenericContainer<>(DockerImageName.parse("nats:2.12.6-alpine")).withExposedPorts(4222);

    private static final Map<String, String> TEST_METADATA = Map.of("key-1", "a", "key-2", "b");

    @DynamicPropertySource
    static void addDynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("nats.server.url",
                () -> "nats://%s:%d".formatted(NATS_CONTAINER.getHost(), NATS_CONTAINER.getMappedPort(4222)));
        registry.add("nats.server.max-reconnect", () -> 0);
        registry.add("deltafi.media-type", () -> MediaType.APPLICATION_JSON);
        registry.add("deltafi.metadata", () -> TEST_METADATA);
    }

    static Connection natsConnection;

    @AfterAll
    static void closeConnection() throws InterruptedException {
        natsConnection.close();
    }

    @Autowired
    DeltaFiIngressService deltaFiIngressService;

    @Autowired
    NatsProperties natsProperties;

    @Autowired
    Clock clock;

    @Autowired
    public NatsIngressApplicationTests(Connection connection) {
        natsConnection = connection;
    }

    @BeforeEach
    void setUp() {
        Mockito.clearInvocations(deltaFiIngressService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void ingresses() throws IOException, InterruptedException, IngressException {
        ((TestClock) clock).setInstant(Instant.now());

        Subscription subscription = natsConnection.subscribe("reply-to-subject");

        Headers headers = new Headers();
        headers.add("header-1", "c", "d"); // NATS headers are mapped to a list of values
        headers.add("header-2", "e", "f");
        byte[] bytes = "This is a test".getBytes(StandardCharsets.UTF_8);
        try (Connection connection = Nats.connect(natsProperties.getServer().getUrl())) {
            connection.publish("nats-subject", subscription.getSubject(), headers, bytes);
        }

        Message message = subscription.nextMessage(5000);
        Assertions.assertEquals("Message received and ingressed at %s".formatted(clock.instant()), new String(message.getData()));

        ArgumentCaptor<IngressRequest<?>> ingressRequestCaptor = ArgumentCaptor.forClass(IngressRequest.class);
        Mockito.verify(deltaFiIngressService, Mockito.timeout(5000)).ingress(ingressRequestCaptor.capture());
        Assertions.assertArrayEquals(bytes, ((IngressRequest.ByValue) ingressRequestCaptor.getValue()).bytes());
        Assertions.assertEquals(MediaType.APPLICATION_JSON, ((IngressRequest.ByValue) ingressRequestCaptor.getValue()).mediaType());
        Assertions.assertEquals("smoke-rest-data-source", ingressRequestCaptor.getValue().dataSource());
        Map<String, String> combined = new HashMap<>(TEST_METADATA);
        headers.forEach((key, value) -> combined.put(key, value.toString()));
        combined.put("nats.subject", "nats-subject");
        Assertions.assertEquals(combined, ingressRequestCaptor.getValue().metadata());
    }

    @Test
    void handlesIngressException() throws IOException, InterruptedException, IngressException {
        IngressException ingressException = new IngressException("Test message");
        Mockito.doThrow(ingressException).when(deltaFiIngressService).ingress(Mockito.any());

        Subscription subscription = natsConnection.subscribe("reply-to-subject-fail");

        byte[] bytes = "This is a test".getBytes(StandardCharsets.UTF_8);
        try (Connection connection = Nats.connect(natsProperties.getServer().getUrl())) {
            connection.publish("nats-subject", subscription.getSubject(), null, bytes);
        }

        Message message = subscription.nextMessage(5000);
        Assertions.assertEquals("Message received but failed ingress at %s: %s"
                .formatted(clock.instant(), ingressException.getMessage()), new String(message.getData()));
    }
}
