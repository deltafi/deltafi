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
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jms.config.JmsListenerEndpointRegistry;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.listener.MessageListenerContainer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.ws.rs.core.MediaType;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@SpringBootTest
@Testcontainers
class ActivemqIngressApplicationTests {
	@Container
	public static final GenericContainer<?> ARTEMIS_CONTAINER =
			new GenericContainer<>(DockerImageName.parse("apache/activemq-artemis:2.40.0"))
					.withEnv("ARTEMIS_USER", "user")
					.withEnv("ARTEMIS_PASSWORD", "pass")
					.withExposedPorts(61616);

	private static final Map<String, String> TEST_METADATA = Map.of("key-1", "a", "key-2", "b");

	@DynamicPropertySource
	static void addDynamicProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.artemis.broker-url",
				() -> "tcp://%s:%d".formatted(ARTEMIS_CONTAINER.getHost(), ARTEMIS_CONTAINER.getMappedPort(61616)));
		registry.add("spring.jms.cache.enabled", () -> false);
		registry.add("deltafi.media-type", () -> MediaType.APPLICATION_JSON);
		registry.add("deltafi.metadata", () -> TEST_METADATA);
	}

	static MessageListenerContainer messageListenerContainer;

	@AfterAll
	static void stopMessageListener() {
		messageListenerContainer.stop();
	}

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

	@Autowired
	public ActivemqIngressApplicationTests(JmsListenerEndpointRegistry jmsListenerEndpointRegistry) {
		messageListenerContainer = jmsListenerEndpointRegistry.getListenerContainers().iterator().next();
	}

	@BeforeEach
	void setUp() {
		Mockito.clearInvocations(deltaFiIngressService);
	}

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
		Assertions.assertArrayEquals(bytes, ((IngressRequest.ByValue) ingressRequestCaptor.getValue()).bytes());
		Assertions.assertEquals(MediaType.APPLICATION_JSON,
				((IngressRequest.ByValue) ingressRequestCaptor.getValue()).mediaType());
		Assertions.assertEquals("smoke-rest-data-source", ingressRequestCaptor.getValue().dataSource());
		Map<String, String> combined = new HashMap<>(TEST_METADATA);
		combined.put("JMSXDeliveryCount", "1");
		combined.put("X-Correlation-ID", "abc");
		combined.put("property_1", "c");
		combined.put("property_2", "123");
		combined.put("jms.queue.name", "deltafi-ingress-topic");
		Assertions.assertEquals(combined, ingressRequestCaptor.getValue().metadata());
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
		Assertions.assertArrayEquals(bytes, ((IngressRequest.ByValue) ingressRequestCaptor.getValue()).bytes());
		Assertions.assertEquals(MediaType.APPLICATION_XML,
				((IngressRequest.ByValue) ingressRequestCaptor.getValue()).mediaType());
		Assertions.assertEquals("smoke-rest-data-source", ingressRequestCaptor.getValue().dataSource());
		Map<String, String> combined = new HashMap<>(TEST_METADATA);
		combined.put("JMSXDeliveryCount", "1");
		combined.put("X-Correlation-ID", "abc");
		combined.put("ContentType", MediaType.APPLICATION_XML);
		combined.put("property_1", "c");
		combined.put("property_2", "123");
		combined.put("jms.queue.name", "deltafi-ingress-topic");
		Assertions.assertEquals(combined, ingressRequestCaptor.getValue().metadata());
	}
}
