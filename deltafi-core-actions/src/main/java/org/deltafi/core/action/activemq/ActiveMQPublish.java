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
import lombok.extern.slf4j.Slf4j;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.deltafi.actionkit.action.egress.*;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.filter.FilterResult;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.ActionOptions;
import org.deltafi.core.action.egress.NoContentPolicy;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Component
@Slf4j
public class ActiveMQPublish extends EgressAction<ActiveMQPublishParameters> {
    public ActiveMQPublish() {
        super(ActionOptions.builder()
                .description("Publish to an ActiveMQ destination.")
                .inputSpec(ActionOptions.InputSpec.builder()
                        .contentSummary("""
                                Single content will be converted to a JMS BytesMessage or
                                TextMessage body. If no content is available but the NoContentPolicy
                                is set to SEND_EMPTY the message will be sent with an empty body.""")
                        .metadataSummary("""
                                Metadata received by this action can be sent as message
                                properties. Set regex patterns to match on in the propertyMetadataPatterns
                                parameter. Use '.*' to include all metadata in properties.""")
                        .build())
                .outputSpec(ActionOptions.OutputSpec.builder()
                        .contentSummary("""
                                Content is converted to a JMS BytesMessage or TextMessage
                                and published to the specified ActiveMQ broker.""")
                        .build())
                .errors("When there is no content and noContentPolicy is ERROR",
                        "When unable to connect to the JMS broker",
                        "When unable to publish a message to the JMS destination")
                .filters("When there is no content and noContentPolicy is FILTER")
                .build());
    }

    @Override
    public EgressResultType egress(@NotNull ActionContext context, @NotNull ActiveMQPublishParameters params,
            @NotNull EgressInput egressInput) {
        if (egressInput.getContent() == null) {
            NoContentPolicy policy = params.getNoContentPolicy();

            switch (policy) {
                case FILTER:
                    return new FilterResult(context, "Content is null - filtered by noContentPolicy");
                case ERROR:
                    return new ErrorResult(context, "Cannot perform egress: no content available",
                            "The egress action requires content but none was provided").logErrorTo(log);
                case SEND_EMPTY:
                    break;
                default:
                    return new ErrorResult(context, "Unknown noContentPolicy: " + policy,
                            "The noContentPolicy value is not recognized").logErrorTo(log);
            }
        }

        ConnectionFactory factory = getConnectionFactory(params);
        try (Connection connection = factory.createConnection()) {
            connection.start();
            return publish(context, params, egressInput, connection);
        } catch (JMSException e) {
            return new ErrorResult(context, "Failed to connect to broker; " + params.connectDetails(), e);
        }
    }

    private ConnectionFactory getConnectionFactory(ActiveMQPublishParameters params) {
        return new ActiveMQConnectionFactory(
                params.getUserName(),
                params.getPassword() != null ? params.getPassword().resolveOrDefault(null) : null,
                params.getBrokerUrl());
    }

    private EgressResultType publish(ActionContext context, ActiveMQPublishParameters params, EgressInput input,
            Connection connection) {
        try {
            try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {

                Destination destination;
                if (DestinationType.TOPIC.equals(params.getDestinationType())) {
                    destination = session.createTopic(params.getDestinationName());
                } else {
                    destination = session.createQueue(params.getDestinationName());
                }

                MessageProducer producer = session.createProducer(destination);
                if (params.isPersistent()) {
                    producer.setDeliveryMode(DeliveryMode.PERSISTENT);
                } else {
                    producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
                }

                Message message;
                if (MessageBodyType.BYTES.equals(params.getMessageBodyType())) {
                    BytesMessage bytesMessage = session.createBytesMessage();
                    if (input.getContent() != null) {
                        bytesMessage.writeBytes(input.getContent().loadBytes());
                    }
                    message = bytesMessage;
                } else {
                    if (input.getContent() == null) {
                        message = session.createTextMessage("");
                    } else {
                        message = session.createTextMessage(input.getContent().loadString());
                    }
                }

                if ((params.getPropertyMetadataPatterns() != null) && !params.getPropertyMetadataPatterns().isEmpty()) {
                    List<Pattern> patterns = params.getPropertyMetadataPatterns().stream().map(Pattern::compile).toList();
                    input.getMetadata().entrySet().stream()
                            .filter(entry -> patterns.stream().anyMatch(pattern -> pattern.matcher(entry.getKey()).matches()))
                            .forEach(entry -> {
                                try {
                                    message.setStringProperty(entry.getKey(), entry.getValue());
                                } catch (JMSException e) {
                                    throw new RuntimeException(e);
                                }
                            });
                }

                producer.send(message);

                return new EgressResult(context);
            }
        } catch (JMSException e) {
            return new ErrorResult(context, "Failed to publish; " + params.publishDetails(), e);
        }
    }
}
