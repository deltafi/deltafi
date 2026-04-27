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
package org.deltafi.activemq.ingress.listener;

import jakarta.jms.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.command.ActiveMQTopic;
import org.deltafi.common.uuid.UUIDGenerator;
import org.deltafi.ingress.config.DeltaFiProperties;
import org.deltafi.ingress.exception.IngressException;
import org.deltafi.ingress.service.IngressRequestor;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class JmsIngressListener {

    private final IngressRequestor ingressRequestor;
    private final DeltaFiProperties deltaFiProperties;
    private final UUIDGenerator uuidGenerator;

    @JmsListener(destination = "${listener.destination.name}")
    public void receive(Message message) throws JMSException, IngressException {
        String msgId = message.getJMSMessageID() != null ?
                message.getJMSMessageID() : uuidGenerator.generate().toString();

        String mediaType = message.getStringProperty("ContentType") != null ?
                message.getStringProperty("ContentType") : deltaFiProperties.getMediaType();

        ingressRequestor.byValue()
                .deltaFileName(msgId)
                .dataSource(deltaFiProperties.getDataSource())
                .mediaType(mediaType)
                .bytes(extractMessageBytes(message))
                .addMetadata(deltaFiProperties.getMetadata())
                .addMetadata(propertiesToMetadata(message))
                .addMetadata("receivedOnDestination", getDestination(message))
                .execute();
    }

    private String getDestination(Message message) throws JMSException {
        Destination destination = message.getJMSDestination();
        if (destination instanceof ActiveMQQueue queue) {
            return queue.getQueueName();
        } else if (destination instanceof ActiveMQTopic topic) {
            return topic.getTopicName();
        }
        return destination.toString();
    }

    private byte[] extractMessageBytes(Message message) throws JMSException {
        return switch (message) {
            case TextMessage textMessage -> textMessage.getText().getBytes(StandardCharsets.UTF_8);
            case BytesMessage bytesMessage -> from(bytesMessage);
            default -> throw new IllegalArgumentException("Unknown message type: " + message);
        };
    }

    private byte[] from(BytesMessage bytesMessage) throws JMSException {
        byte[] bytes = new byte[(int) bytesMessage.getBodyLength()];
        bytesMessage.readBytes(bytes);
        return bytes;
    }

    private Map<String, String> propertiesToMetadata(Message message) throws JMSException {
        Map<String, String> metadata = new HashMap<>();
        if (message.getJMSCorrelationID() != null) {
            metadata.put("X-Correlation-ID", message.getJMSCorrelationID());
        }

        Enumeration<?> propertyNames = message.getPropertyNames();
        while (propertyNames.hasMoreElements()) {
            String propertyName = (String) propertyNames.nextElement();
            Object propertyValue = message.getObjectProperty(propertyName);
            if (propertyValue != null) {
                metadata.put(propertyName, propertyValue.toString());
            }
        }
        return metadata;
    }
}
