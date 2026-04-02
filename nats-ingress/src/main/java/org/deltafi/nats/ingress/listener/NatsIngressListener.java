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
package org.deltafi.nats.ingress.listener;

import io.nats.client.*;
import io.nats.client.impl.Headers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.ingress.config.DeltaFiProperties;
import org.deltafi.ingress.service.IngressRequestor;
import org.deltafi.nats.ingress.config.NatsProperties;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class NatsIngressListener {

    private final Connection natsConnection;
    private final IngressRequestor ingressRequestor;
    private final NatsProperties natsProperties;
    private final DeltaFiProperties deltaFiProperties;
    private final Clock clock;

    private Dispatcher dispatcher;

    @PostConstruct
    public void startListening() {
        String messageSubject = natsProperties.getMessage().getSubject();

        log.info("Starting NATS message consumer for subject: {}", messageSubject);

        try {
            dispatcher = natsConnection.createDispatcher(this::handleMessage);
            dispatcher.subscribe(messageSubject);
            log.info("Successfully subscribed to subject: {}", messageSubject);
        } catch (Exception e) {
            log.error("Error starting NATS message consumer", e);
            throw new NatsException("Failed to start NATS consumer", e);
        }
    }

    @PreDestroy
    public void stopListening() {
        log.info("Stopping NATS message consumer");
        if (dispatcher != null) {
            dispatcher.unsubscribe(natsProperties.getMessage().getSubject());
        }
    }

    private void handleMessage(Message message) {
        Instant now = clock.instant();

        log.debug("Received message of {} bytes for subject {} at {}",
                message.getData().length, message.getSubject(), now);

        try {
            ingressRequestor.byValue()
                    .dataSource(deltaFiProperties.getDataSource())
                    .mediaType(deltaFiProperties.getMediaType())
                    .bytes(message.getData())
                    .addMetadata(deltaFiProperties.getMetadata())
                    .addMetadata(toMetadata(message.getHeaders()))
                    .addMetadata("nats.subject", message.getSubject())
                    .execute();

            message.ackSync(Duration.ofSeconds(10));

            if (message.getReplyTo() != null) {
                publishReply(message.getReplyTo(), "Message received and ingressed at %s".formatted(now));
            }
        } catch (Exception e) {
            log.error("Failed to ingress NATS message to DeltaFi", e);

            if (message.getReplyTo() != null) {
                publishReply(message.getReplyTo(),
                        "Message received but failed ingress at %s: %s".formatted(now, e.getMessage()));
            }
        }
    }

    private Map<String, String> toMetadata(Headers headers) {
        return headers == null ? Collections.emptyMap() : headers.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, es -> es.getValue().toString()));
    }

    private void publishReply(String replyTo, String message) {
        natsConnection.publish(replyTo, message.getBytes(StandardCharsets.UTF_8));
        log.debug("Published reply to {}: {}", replyTo, message);
    }

    static class NatsException extends RuntimeException {
        public NatsException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}