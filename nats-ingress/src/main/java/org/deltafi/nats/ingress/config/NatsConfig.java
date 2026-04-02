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
package org.deltafi.nats.ingress.config;

import io.nats.client.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(NatsProperties.class)
@Slf4j
public class NatsConfig {
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    public Connection natsConnection(NatsProperties natsProperties) throws IOException, InterruptedException {
        NatsProperties.Server server = natsProperties.getServer();
        log.info("Connecting to NATS server at: {}", server.getUrl());

        Options options = new Options.Builder()
                .server(server.getUrl())
                .userInfo(server.getUser(), server.getPassword())
                .connectionTimeout(Duration.ofMillis(server.getConnectionTimeout()))
                .maxReconnects(server.getMaxReconnect())
                .reconnectWait(Duration.ofSeconds(2))
                .connectionListener((conn, type) -> log.info("NATS connection event: {}", type))
                .errorListener(new LogErrorListener())
                .build();

        Connection connection = Nats.connect(options);
        log.info("Successfully connected to NATS server");

        return connection;
    }

    @Slf4j
    static class LogErrorListener implements ErrorListener {
        @Override
        public void errorOccurred(Connection conn, String error) {
            log.error("NATS connection error: {}", error);
        }

        @Override
        public void exceptionOccurred(Connection conn, Exception exp) {
            log.error("NATS connection exception", exp);
        }

        @Override
        public void slowConsumerDetected(Connection conn, Consumer consumer) {
            log.warn("NATS slow consumer detected");
        }
    }
}