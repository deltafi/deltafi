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
/*
 * ABOUTME: HTTP client for sending provenance records to the analytics service.
 * ABOUTME: Buffers records in memory and sends them in batches via HTTP POST.
 */
package org.deltafi.core.services.analytics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.core.services.DeltaFiPropertiesService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
public class ProvenanceClient {

    private static final int BUFFER_SIZE = 10_000;
    private static final int DEFAULT_FLUSH_INTERVAL_SECONDS = 30;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String analyticsUrl;
    private final DeltaFiPropertiesService deltaFiPropertiesService;

    private final ConcurrentLinkedQueue<ProvenanceRecord> recordBuffer = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean flushPending = new AtomicBoolean(false);
    private ScheduledExecutorService scheduler;

    public ProvenanceClient(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            DeltaFiPropertiesService deltaFiPropertiesService,
            @Value("${analytics.url:http://deltafi-analytics:8080}") String analyticsUrl) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.deltaFiPropertiesService = deltaFiPropertiesService;
        this.analyticsUrl = analyticsUrl;
    }

    private boolean isEnabled() {
        return deltaFiPropertiesService.getDeltaFiProperties().isProvenanceEnabled();
    }

    @PostConstruct
    public void init() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "provenance-client");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(this::flushBuffer, DEFAULT_FLUSH_INTERVAL_SECONDS, DEFAULT_FLUSH_INTERVAL_SECONDS, TimeUnit.SECONDS);
        log.info("Provenance client initialized. URL: {}", analyticsUrl);
    }

    @PreDestroy
    public void shutdown() {
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                flushBuffer();
                if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    public void writeRecord(ProvenanceRecord record) {
        if (!isEnabled()) return;

        recordBuffer.add(record);

        if (recordBuffer.size() >= BUFFER_SIZE && flushPending.compareAndSet(false, true)) {
            scheduler.submit(() -> {
                try {
                    flushBuffer();
                } finally {
                    flushPending.set(false);
                }
            });
        }
    }

    private synchronized void flushBuffer() {
        if (recordBuffer.isEmpty()) return;

        List<ProvenanceRecord> batch = new ArrayList<>();
        ProvenanceRecord record;
        while ((record = recordBuffer.poll()) != null && batch.size() < BUFFER_SIZE * 2) {
            batch.add(record);
        }

        if (batch.isEmpty()) return;

        try {
            String json = objectMapper.writeValueAsString(batch);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(analyticsUrl + "/provenance"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.debug("Sent {} provenance records", batch.size());
            } else if (response.statusCode() >= 500) {
                log.error("Server error sending provenance records: HTTP {} - {}. Will retry.", response.statusCode(), response.body());
                recordBuffer.addAll(batch);
            } else {
                log.error("Provenance records rejected: HTTP {} - {}. Dropping {} records.", response.statusCode(), response.body(), batch.size());
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize provenance records: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to send provenance records: {}", e.getMessage(), e);
            recordBuffer.addAll(batch);
        }
    }

    /**
     * Provenance record to be sent to the analytics service.
     */
    public record ProvenanceRecord(
            String did,
            String parentDid,  // null for non-split files
            String systemName,
            String dataSource,
            String filename,
            List<String> transforms,
            String dataSink,
            String finalState,
            long created,      // Unix millis
            long completed,    // Unix millis
            Map<String, String> annotations
    ) {
        public static ProvenanceRecord from(
                UUID did,
                UUID parentDid,
                String systemName,
                String dataSource,
                String filename,
                List<String> transforms,
                String dataSink,
                String finalState,
                OffsetDateTime created,
                OffsetDateTime completed,
                Map<String, String> annotations) {
            return new ProvenanceRecord(
                    did.toString(),
                    parentDid != null ? parentDid.toString() : null,
                    systemName,
                    dataSource,
                    filename,
                    transforms,
                    dataSink,
                    finalState,
                    created.toInstant().toEpochMilli(),
                    completed.toInstant().toEpochMilli(),
                    annotations
            );
        }
    }
}
