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

    private final ConcurrentLinkedQueue<TSEgress> egressQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<TSIngress> ingressQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<TSError> errorQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<TSFilter> filterQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<TSCancel> cancelQueue = new ConcurrentLinkedQueue<>();

    private boolean isDisabled() {
        return !deltaFiPropertiesService.getDeltaFiProperties().isMetricsEnabled();
    }

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
                        .annotations(deltaFile.annotationMap())
                        .build();

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
                .annotations(annotations)
                .count(1)
                .survey(false)
                .build();

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
    public void recordAnnotations(UUID did, OffsetDateTime created, String dataSource, long ingressBytes, Map<String, String> annotations) {
        recordIngress(did, created, dataSource, ingressBytes, annotations);
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
                .annotations(annotations)
                .count(count)
                .survey(true)
                .build();

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
                .key(new TSId(timestamp, deltaFile.getDataSource()))
                .cause(cause)
                .flow(flow)
                .action(action)
                .annotations(deltaFile.annotationMap())
                .build();

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
                .annotations(deltaFile.annotationMap())
                .build();

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
                .annotations(deltaFile.annotationMap())
                .build();

        cancelQueue.offer(tsCancel);
    }

    /**
     * Generate analytic events for Surveys
     *
     * @param surveyEvents the list of surveys to record
     * @return list of errors if there was invalid survey data
     */
    public List<SurveyError> recordSurveys(List<SurveyEvent> surveyEvents) {
        if (!isDisabled()) {
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

        processQueue(egressQueue, tsEgressRepo::batchInsert);
        processQueue(ingressQueue, tsIngressRepo::batchUpsert);
        processQueue(errorQueue, tsErrorRepo::batchInsert);
        processQueue(filterQueue, tsFilterRepo::batchInsert);
        processQueue(cancelQueue, tsCancelRepo::batchInsert);
    }

    private <T> void processQueue(Queue<T> queue, Consumer<List<T>> saveFunction) {
        List<T> batch = new ArrayList<>();
        int batchSize = 1000;

        T item;
        while ((item = queue.poll()) != null && batch.size() < batchSize) {
            batch.add(item);
        }

        if (batch.isEmpty()) {
            return;
        }

        try {
            saveFunction.accept(batch);
        } catch (Exception e) {
            log.error("Error processing batch. Re-queueing items.", e);
            queue.addAll(batch);
        }
    }
}
