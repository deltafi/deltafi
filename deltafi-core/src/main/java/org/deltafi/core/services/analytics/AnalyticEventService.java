/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.services.analytics;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.core.repo.timescale.*;
import org.deltafi.core.types.DeltaFile;
import org.deltafi.core.services.DeltaFiPropertiesService;
import org.deltafi.core.types.DeltaFileFlow;
import org.deltafi.core.types.timescale.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.stream.IntStream;

/**
 * Generate analytic events.
 */
@AllArgsConstructor
@Slf4j
@Service
public class AnalyticEventService {
    private final DeltaFiPropertiesService deltaFiPropertiesService;
    private final TSCancelRepo tsCancelRepo;
    private final TSEgressRepo tsEgressRepo;
    private final TSErrorRepo tsErrorRepo;
    private final TSIngressRepo tsIngressRepo;
    private final TSFilterRepo tsFilterRepo;
    private final TSAnnotationRepo tsAnnotationRepo;
    private final Clock clock;

    private final ConcurrentLinkedQueue<TSEgress> egressQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<TSIngress> ingressQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<TSError> errorQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<TSFilter> filterQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<TSCancel> cancelQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<TSAnnotation> annotationQueue = new ConcurrentLinkedQueue<>();

    private boolean isDisabled() {
        return !deltaFiPropertiesService.getDeltaFiProperties().isMetricsEnabled();
    }

    private final static int BATCH_SIZE = 1000;
    // this limit is only applied when database inserts fail
    // determines whether metrics are requeued or dropped
    private final static int MAX_QUEUE_SIZE = 10000;

    /**
     * Generate an analytic event for DeltaFile egress
     *
     * @param  deltaFile  the DeltaFile to be recorded
     */
    public void recordEgress(DeltaFile deltaFile, DeltaFileFlow flow) {
        if (isDisabled()) return;

        TSEgress tsEgress = TSEgress.builder()
                        .key(new TSId(flow.getModified(), deltaFile.getDataSource()))
                        .egressor(flow.getName())
                        .egressBytes(flow.lastContentSize())
                        .build();

        deltaFile.annotationMap().forEach((k, v) -> {
            TSAnnotation tsAnnotation = TSAnnotation.builder()
                    .id(new TSAnnotationId(tsEgress.getKey().getId(), k))
                    .dataSource(deltaFile.getDataSource())
                    .entityTimestamp(flow.getModified())
                    .value(v)
                    .build();
            annotationQueue.offer(tsAnnotation);
        });

        egressQueue.offer(tsEgress);
    }

    /**
     * Generate an analytic event for DeltaFile ingress
     *
     * @param did the DeltaFile id
     * @param created creation time
     * @param dataSource the data source
     * @param ingressBytes bytes ingressed
     * @param annotations map of annotations
     */
    public void recordIngress(UUID did, OffsetDateTime created, String dataSource, long ingressBytes, Map<String, String> annotations) {
        if (isDisabled()) return;

        TSIngress tsIngress = TSIngress.builder()
                .key(new TSId(did, created, dataSource))
                .ingressBytes(ingressBytes)
                .count(1)
                .survey(false)
                .build();

        annotations.forEach((k, v) -> {
            TSAnnotation tsAnnotation = TSAnnotation.builder()
                    .id(new TSAnnotationId(tsIngress.getKey().getId(), k))
                    .dataSource(dataSource)
                    .entityTimestamp(created)
                    .value(v)
                    .build();
            annotationQueue.offer(tsAnnotation);
        });

        ingressQueue.offer(tsIngress);
    }

    /*
     * Record annotations for a DeltaFile. Delegates to recordIngress to upsert the entire record if it's not present,
     * which can happen due to timing, else only the annotations will be merged with the existing record.
     *
     * @param did the DeltaFile id
     * @param created creation time
     * @param dataSource the data source
     * @param ingressBytes bytes ingressed
     * @param annotations map of annotations
     */
    public void recordAnnotations(UUID did, OffsetDateTime created, String dataSource, Map<String, String> annotations) {
        annotations.forEach((k, v) -> {
            TSAnnotation tsAnnotation = TSAnnotation.builder()
                    .id(new TSAnnotationId(did, k))
                    .dataSource(dataSource)
                    .entityTimestamp(created)
                    .value(v)
                    .build();
            annotationQueue.offer(tsAnnotation);
        });
    }

    /**
     * Generate an analytic event for survey
     *
     * @param id the survey id
     * @param created creation time
     * @param dataSource the data source
     * @param count the number of files ingressed
     * @param ingressBytes bytes ingressed
     * @param annotations map of annotations
     */
    public void recordSurvey(UUID id, OffsetDateTime created, String dataSource, int count, long ingressBytes, Map<String, String> annotations) {
        if (isDisabled()) return;

        TSIngress tsIngress = TSIngress.builder()
                .key(new TSId(id, created, dataSource))
                .ingressBytes(ingressBytes)
                .count(count)
                .survey(true)
                .build();

        annotations.forEach((k, v) -> {
            TSAnnotation tsAnnotation = TSAnnotation.builder()
                    .id(new TSAnnotationId(tsIngress.getKey().getId(), k))
                    .dataSource(dataSource)
                    .entityTimestamp(created)
                    .value(v)
                    .build();
            annotationQueue.offer(tsAnnotation);
        });

        ingressQueue.offer(tsIngress);
    }

    /**
     * Generate an analytic event for DeltaFile error.
     * @param deltaFile - errored DeltaFile
     * @param flow - name of the dataSource
     * @param action - name of the action that errored
     * @param cause - cause of the error
     * @param timestamp - timestamp of the error
     */
    public void recordError(DeltaFile deltaFile, String flow, String action, String cause, OffsetDateTime timestamp) {
        if (isDisabled()) return;

        TSError tsError = TSError.builder()
                .key(new TSId(timestamp == null ? OffsetDateTime.now(clock) : timestamp, deltaFile.getDataSource()))
                .cause(cause)
                .flow(flow)
                .action(action)
                .build();

        deltaFile.annotationMap().forEach((k, v) -> {
            TSAnnotation tsAnnotation = TSAnnotation.builder()
                    .id(new TSAnnotationId(tsError.getKey().getId(), k))
                    .dataSource(deltaFile.getDataSource())
                    .entityTimestamp(timestamp)
                    .value(v)
                    .build();
            annotationQueue.offer(tsAnnotation);
        });

        errorQueue.offer(tsError);
    }

    /**
     * Generate an analytic event for DeltaFile filter.
     * @param deltaFile - filtered DeltaFile
     * @param flow - name of the dataSource
     * @param action - name of the action that fitlered
     * @param message - cause of the filter
     * @param timestamp - timestamp of the filter
     */
    public void recordFilter(DeltaFile deltaFile, String flow, String action, String message, OffsetDateTime timestamp) {
        if (isDisabled()) return;

        TSFilter tsFilter = TSFilter.builder()
                .key(new TSId(timestamp, deltaFile.getDataSource()))
                .flow(flow)
                .action(action)
                .message(message)
                .build();

        deltaFile.annotationMap().forEach((k, v) -> {
            TSAnnotation tsAnnotation = TSAnnotation.builder()
                    .id(new TSAnnotationId(tsFilter.getKey().getId(), k))
                    .dataSource(deltaFile.getDataSource())
                    .entityTimestamp(timestamp)
                    .value(v)
                    .build();
            annotationQueue.offer(tsAnnotation);
        });

        filterQueue.offer(tsFilter);
    }

    /**
     * Generate an analytic event for DeltaFile cancel.
     * @param deltaFile - filtered DeltaFile
     * @param timestamp - timestamp of the filter
     */
    public void recordCancel(DeltaFile deltaFile, OffsetDateTime timestamp) {
        if (isDisabled()) return;

        TSCancel tsCancel = TSCancel.builder()
                .key(new TSId(timestamp, deltaFile.getDataSource()))
                .build();

        deltaFile.annotationMap().forEach((k, v) -> {
            TSAnnotation tsAnnotation = TSAnnotation.builder()
                    .id(new TSAnnotationId(tsCancel.getKey().getId(), k))
                    .dataSource(deltaFile.getDataSource())
                    .entityTimestamp(timestamp)
                    .value(v)
                    .build();
            annotationQueue.offer(tsAnnotation);
        });

        cancelQueue.offer(tsCancel);
    }

    /**
     * Generate analytic events for Surveys
     *
     * @param surveyEvents the list of surveys to record
     * @return list of errors if there was invalid survey data
     */
    public List<SurveyError> recordSurveys(List<SurveyEvent> surveyEvents) {
        if (isDisabled()) {
            log.error("Attempted to add survey metrics with analytics disabled");
            throw new DisabledAnalyticsException();
        }

        if (surveyEvents == null || surveyEvents.isEmpty()) {
            return List.of();
        }

        List<SurveyError> surveyErrors = IntStream.range(0, surveyEvents.size())
                .filter(i -> !surveyEvents.get(i).isValid())
                .mapToObj(i -> new SurveyError(i, surveyEvents.get(i)))
                .toList();


        // if there were errors do not process any of the survey entries
        if (!surveyErrors.isEmpty()) {
            return surveyErrors;
        }

        surveyEvents.forEach(s -> recordSurvey(UUID.randomUUID(), s.timestamp(), s.dataSource(), s.files(), s.ingressBytes(), s.annotations()));
        return List.of();
    }

    public record SurveyError(String error, SurveyEvent surveyEvent) {
        public SurveyError(int index, SurveyEvent surveyEvent) {
            this("Invalid survey data at " + index, surveyEvent);
        }
    }

    public static class DisabledAnalyticsException extends RuntimeException { }

    @Scheduled(fixedDelay = 5000)
    public void processBatch() {
        if (isDisabled()) return;

        boolean fullBatchProcessed;
        do {
            fullBatchProcessed = processQueue(egressQueue, tsEgressRepo::batchInsert) ||
                    processQueue(ingressQueue, tsIngressRepo::batchInsert) ||
                    processQueue(errorQueue, tsErrorRepo::batchInsert) ||
                    processQueue(filterQueue, tsFilterRepo::batchInsert) ||
                    processQueue(cancelQueue, tsCancelRepo::batchInsert) ||
                    processQueue(annotationQueue, tsAnnotationRepo::batchUpsert);
        } while (fullBatchProcessed);
    }

    private <T> boolean processQueue(Queue<T> queue, Consumer<List<T>> saveFunction) {
        List<T> batch = new ArrayList<>();

        T item;
        while (batch.size() < BATCH_SIZE && (item = queue.poll()) != null) {
            batch.add(item);
        }

        if (batch.isEmpty()) {
            return false;
        }

        try {
            saveFunction.accept(batch);
            return batch.size() == BATCH_SIZE;
        } catch (Exception e) {
            if (queue.size() < MAX_QUEUE_SIZE) {
                log.error("Error processing batch. Re-queueing items.", e);
                queue.addAll(batch);
            } else {
                log.error("Error processing batch. Max queue size exceeded, dropping items.", e);
            }
            return false;
        }
    }
}
