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
package org.deltafi.core.action.activemq;

import jakarta.jms.*;
import jakarta.jms.Queue;
import lombok.Data;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.awaitility.Awaitility;
import org.deltafi.actionkit.action.egress.EgressInput;
import org.deltafi.actionkit.action.egress.EgressResultType;
import org.deltafi.actionkit.action.parameters.EnvVar;
import org.deltafi.test.asserters.EgressResultAssert;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.*;
import java.util.concurrent.TimeUnit;

class ActiveMQPublishTest {
    @Data
    private static class TestMessageListener implements MessageListener {
        private ArrayList<Message> messagesReceived = new ArrayList<>();

        @Override
        public void onMessage(Message message) {
            messagesReceived.add(message);
        }
    }

    ActiveMQPublish action = new ActiveMQPublish();

    DeltaFiTestRunner runner = DeltaFiTestRunner.setup();

    @Test
    void publishesTextToQueue() throws JMSException {
        String brokerUrl = "vm://embedded?broker.persistent=false";

        ActiveMQConnectionFactory activeMQConnectionFactory = new ActiveMQConnectionFactory(brokerUrl);
        try (QueueConnection queueConnection = activeMQConnectionFactory.createQueueConnection()) {
            queueConnection.start();
            Session session = queueConnection.createSession();
            Queue queue = session.createQueue("test-queue");
            MessageConsumer consumer = session.createConsumer(queue);
            TestMessageListener testMessageListener = new TestMessageListener();
            consumer.setMessageListener(testMessageListener);

            ActiveMQPublishParameters params = new ActiveMQPublishParameters();
            params.setBrokerUrl(brokerUrl);
            params.setUserName("admin");
            EnvVar passwordEnvVar = Mockito.mock(EnvVar.class);
            Mockito.when(passwordEnvVar.resolveOrDefault(Mockito.any())).thenReturn("admin");
            params.setPassword(passwordEnvVar);
            params.setMessageBodyType(MessageBodyType.TEXT);
            params.setDestinationType(DestinationType.QUEUE);
            params.setDestinationName("test-queue");
            params.setPropertyMetadataPatterns(List.of(".*\\.txt", "abc.*xyz"));

            EgressResultType result = action.egress(runner.actionContext(), params, EgressInput.builder()
                    .content(runner.saveContent("This is the test message").getFirst())
                    .metadata(Map.of("key.txt", "value-1", "abcANYxyz", "value-2", "unmatched", "value-3"))
                    .build());

            EgressResultAssert.assertThat(result);

            Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> !testMessageListener.getMessagesReceived().isEmpty());
            Message message = testMessageListener.getMessagesReceived().getFirst();
            Assertions.assertEquals("This is the test message", ((TextMessage) message).getText());
            Assertions.assertEquals("value-1", message.getStringProperty("key.txt"));
            Assertions.assertEquals("value-2", message.getStringProperty("abcANYxyz"));
            Assertions.assertNull(message.getStringProperty("unmatched"));
        }
    }

    @Test
    void publishesBytesToTopic() throws JMSException {
        String brokerUrl = "vm://embedded?broker.persistent=false";

        ActiveMQConnectionFactory activeMQConnectionFactory = new ActiveMQConnectionFactory(brokerUrl);
        try (TopicConnection topicConnection = activeMQConnectionFactory.createTopicConnection()) {
            topicConnection.start();
            Session session = topicConnection.createSession();
            Topic topic = session.createTopic("test-topic");
            MessageConsumer consumer = session.createConsumer(topic);
            TestMessageListener testMessageListener = new TestMessageListener();
            consumer.setMessageListener(testMessageListener);

            ActiveMQPublishParameters params = new ActiveMQPublishParameters();
            params.setBrokerUrl(brokerUrl);
            params.setUserName("admin");
            EnvVar passwordEnvVar = Mockito.mock(EnvVar.class);
            Mockito.when(passwordEnvVar.resolveOrDefault(Mockito.any())).thenReturn("admin");
            params.setPassword(passwordEnvVar);
            params.setMessageBodyType(MessageBodyType.BYTES);
            params.setDestinationType(DestinationType.TOPIC);
            params.setDestinationName("test-topic");
            params.setPropertyMetadataPatterns(List.of(".*\\.txt", "abc.*xyz"));

            EgressResultType result = action.egress(runner.actionContext(), params, EgressInput.builder()
                    .content(runner.saveContent("This is the test message".getBytes()).getFirst())
                    .metadata(Map.of("key.txt", "value-1", "abcANYxyz", "value-2", "unmatched", "value-3"))
                    .build());

            EgressResultAssert.assertThat(result);

            Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> !testMessageListener.getMessagesReceived().isEmpty());
            Message message = testMessageListener.getMessagesReceived().getFirst();
            BytesMessage bytesMessage = (BytesMessage) message;
            byte[] bytes = new byte[(int) bytesMessage.getBodyLength()];
            bytesMessage.readBytes(bytes);
            Assertions.assertArrayEquals("This is the test message".getBytes(), bytes);
            Assertions.assertEquals("value-1", message.getStringProperty("key.txt"));
            Assertions.assertEquals("value-2", message.getStringProperty("abcANYxyz"));
            Assertions.assertNull(message.getStringProperty("unmatched"));
        }
    }
}
