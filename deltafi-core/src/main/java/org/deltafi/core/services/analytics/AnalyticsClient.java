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
 * ABOUTME: HTTP client for sending analytics events to the analytics service.
 * ABOUTME: Buffers events in memory and sends them in batches via HTTP POST.
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
public class AnalyticsClient {

    private static final int BUFFER_SIZE = 10_000;
    private static final int FLUSH_INTERVAL_SECONDS = 10;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String analyticsUrl;
    private final DeltaFiPropertiesService deltaFiPropertiesService;

    private final ConcurrentLinkedQueue<AnalyticsEventRequest> eventBuffer = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<UUID, PendingAnnotation> pendingAnnotations = new ConcurrentHashMap<>();
    private final AtomicBoolean flushPending = new AtomicBoolean(false);
    private ScheduledExecutorService scheduler;

    private record PendingAnnotation(Map<String, String> annotations, long creationTime) {}

    public AnalyticsClient(
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
        return deltaFiPropertiesService.getDeltaFiProperties().isParquetAnalyticsEnabled();
    }

    @PostConstruct
    public void init() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "analytics-client");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(this::flushBuffer, FLUSH_INTERVAL_SECONDS, FLUSH_INTERVAL_SECONDS, TimeUnit.SECONDS);
        log.info("Analytics client initialized. URL: {}", analyticsUrl);
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

    public void writeEvent(AnalyticsEventRequest event) {
        if (!isEnabled()) return;

        // Merge any pending annotations for this DID
        PendingAnnotation pending = pendingAnnotations.remove(UUID.fromString(event.did()));
        if (pending != null && event.annotations() != null) {
            Map<String, String> merged = new HashMap<>(event.annotations());
            merged.putAll(pending.annotations());
            event = event.withAnnotations(merged);
        } else if (pending != null) {
            event = event.withAnnotations(pending.annotations());
        }

        eventBuffer.add(event);

        if (eventBuffer.size() >= BUFFER_SIZE && flushPending.compareAndSet(false, true)) {
            scheduler.submit(() -> {
                try {
                    flushBuffer();
                } finally {
                    flushPending.set(false);
                }
            });
        }
    }

    public void queueAnnotations(UUID did, Map<String, String> annotations, OffsetDateTime creationTime) {
        if (!isEnabled() || annotations == null || annotations.isEmpty()) return;

        long creationMillis = creationTime != null ? creationTime.toInstant().toEpochMilli() : System.currentTimeMillis();
        pendingAnnotations.merge(did, new PendingAnnotation(new HashMap<>(annotations), creationMillis),
                (existing, newPending) -> {
                    Map<String, String> merged = new HashMap<>(existing.annotations());
                    merged.putAll(newPending.annotations());
                    return new PendingAnnotation(merged, existing.creationTime());
                });
    }

    private synchronized void flushBuffer() {
        flushEvents();
        flushAnnotations();
    }

    private void flushEvents() {
        if (eventBuffer.isEmpty()) return;

        List<AnalyticsEventRequest> batch = new ArrayList<>();
        AnalyticsEventRequest event;
        while ((event = eventBuffer.poll()) != null && batch.size() < BUFFER_SIZE * 2) {
            batch.add(event);
        }

        if (batch.isEmpty()) return;

        try {
            String json = objectMapper.writeValueAsString(batch);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(analyticsUrl + "/events"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.debug("Sent {} analytics events", batch.size());
            } else if (response.statusCode() >= 500) {
                log.error("Server error sending analytics events: HTTP {} - {}. Will retry.", response.statusCode(), response.body());
                eventBuffer.addAll(batch);
            } else {
                // 4xx errors - data is rejected and will never be accepted, don't retry
                log.error("Analytics events rejected: HTTP {} - {}. Dropping {} events.", response.statusCode(), response.body(), batch.size());
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize analytics events: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to send analytics events: {}", e.getMessage(), e);
            // Re-queue events on failure
            eventBuffer.addAll(batch);
        }
    }

    private void flushAnnotations() {
        if (pendingAnnotations.isEmpty()) return;

        // Snapshot and clear pending annotations
        Map<UUID, PendingAnnotation> toSend = new HashMap<>();
        var iterator = pendingAnnotations.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            toSend.put(entry.getKey(), entry.getValue());
            iterator.remove();
        }

        if (toSend.isEmpty()) return;

        // Convert to list format expected by analytics service
        List<AnnotationRequest> batch = toSend.entrySet().stream()
                .map(e -> new AnnotationRequest(e.getKey().toString(), e.getValue().annotations(), e.getValue().creationTime()))
                .toList();

        try {
            String json = objectMapper.writeValueAsString(batch);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(analyticsUrl + "/annotations"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.debug("Sent {} annotation updates", batch.size());
            } else if (response.statusCode() >= 500) {
                log.error("Server error sending annotations: HTTP {} - {}. Will retry.", response.statusCode(), response.body());
                for (var entry : toSend.entrySet()) {
                    pendingAnnotations.merge(entry.getKey(), entry.getValue(), (existing, newPending) -> {
                        Map<String, String> merged = new HashMap<>(existing.annotations());
                        merged.putAll(newPending.annotations());
                        return new PendingAnnotation(merged, existing.creationTime());
                    });
                }
            } else {
                // 4xx errors - data is rejected and will never be accepted, don't retry
                log.error("Annotations rejected: HTTP {} - {}. Dropping {} annotations.", response.statusCode(), response.body(), batch.size());
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize annotations: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to send annotations: {}", e.getMessage(), e);
            // Re-queue annotations on failure
            for (var entry : toSend.entrySet()) {
                pendingAnnotations.merge(entry.getKey(), entry.getValue(), (existing, newPending) -> {
                    Map<String, String> merged = new HashMap<>(existing.annotations());
                    merged.putAll(newPending.annotations());
                    return new PendingAnnotation(merged, existing.creationTime());
                });
            }
        }
    }

    private record AnnotationRequest(String did, Map<String, String> annotations, long creationTime) {}

    /**
     * Analytics event request to be sent to the analytics service.
     * Field names match the Go service's expected JSON format.
     */
    public record AnalyticsEventRequest(
            String did,
            String dataSource,
            String eventType,
            long eventTime,  // Unix millis - when the event occurred
            long creationTime,  // Unix millis - when the DeltaFile was created (used for partitioning)
            long bytes,
            int fileCount,
            String flowName,
            String actionName,
            String cause,
            String ingressType,
            Map<String, String> annotations
    ) {
        public AnalyticsEventRequest withAnnotations(Map<String, String> newAnnotations) {
            return new AnalyticsEventRequest(did, dataSource, eventType, eventTime, creationTime, bytes, fileCount,
                    flowName, actionName, cause, ingressType, newAnnotations);
        }

        public static AnalyticsEventRequest from(OffsetDateTime eventTime, OffsetDateTime creationTime, UUID did, String dataSource,
                                                  String eventType, long bytes, int files, String flowName,
                                                  String actionName, String cause, String ingressType,
                                                  Map<String, String> annotations) {
            return new AnalyticsEventRequest(
                    did.toString(),
                    dataSource,
                    eventType,
                    eventTime.toInstant().toEpochMilli(),
                    creationTime.toInstant().toEpochMilli(),
                    bytes,
                    files,
                    flowName,
                    actionName,
                    cause,
                    ingressType,
                    annotations
            );
        }
    }
}
