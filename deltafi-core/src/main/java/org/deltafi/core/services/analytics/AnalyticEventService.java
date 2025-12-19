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
package org.deltafi.core.services.analytics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.types.DeltaFileFlowState;
import org.deltafi.common.types.FlowType;
import org.deltafi.core.repo.AnalyticsRepo;
import org.deltafi.core.repo.EventAnnotationsRepo;
import org.deltafi.core.services.*;
import org.deltafi.core.services.analytics.AnalyticsClient.AnalyticsEventRequest;
import org.deltafi.core.types.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.IntStream;

/**
 * Generate analytic events.
 */
@RequiredArgsConstructor
@Slf4j
@Service
@Transactional
public class AnalyticEventService {

    private final AnalyticsRepo analyticsRepo;
    private final EventAnnotationsRepo eventAnnotationsRepo;
    private final FlowDefinitionService flowDefinitionService;
    private final EventGroupService eventGroupService;
    private final AnnotationKeyService annotationKeyService;
    private final AnnotationValueService annotationValueService;
    private final ActionNameService actionNameService;
    private final ErrorCauseService errorCauseService;
    private final DeltaFiPropertiesService deltaFiPropertiesService;
    private final AnalyticsClient analyticsClient;
    private final ProvenanceClient provenanceClient;
    private final static String DEFAULT_EVENT_GROUP = "No Group";
    private final static int BATCH_SIZE = 1000;
    private final static int MAX_QUEUE_SIZE = 200000;

    private interface QueuedEvent {}
    public record QueuedAnalyticsEntity(AnalyticsEntity entity) implements QueuedEvent {}
    public record QueuedAnnotationEvent(UUID did, Map<String, String> annotations) implements QueuedEvent {}

    private final ConcurrentLinkedDeque<QueuedEvent> eventQueue = new ConcurrentLinkedDeque<>();

    private boolean isDisabled() {
        return !deltaFiPropertiesService.getDeltaFiProperties().isTimescaleAnalyticsEnabled();
    }

    private Map<String, String> filterAllowedAnnotations(Map<String, String> annotations) {
        if (annotations == null || annotations.isEmpty()) {
            return Map.of();
        }
        List<String> allowed = deltaFiPropertiesService.getDeltaFiProperties().allowedAnalyticsAnnotationsList();
        Map<String, String> filtered = new HashMap<>();
        for (Map.Entry<String, String> entry : annotations.entrySet()) {
            if (allowed.contains(entry.getKey())) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }
        return filtered;
    }

    private Map<String, String> filterProvenanceAnnotations(Map<String, String> annotations) {
        if (annotations == null || annotations.isEmpty()) {
            return Map.of();
        }
        List<String> allowed = deltaFiPropertiesService.getDeltaFiProperties().provenanceAnnotationsAllowedList();
        if (allowed.isEmpty()) {
            return Map.of();
        }
        Map<String, String> filtered = new HashMap<>();
        for (Map.Entry<String, String> entry : annotations.entrySet()) {
            if (allowed.contains(entry.getKey())) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }
        return filtered;
    }

    /**
     * Generate an analytic event for DeltaFile egress
     *
     * @param  deltaFile  the DeltaFile to be recorded
     */
    public void recordEgress(DeltaFile deltaFile, DeltaFileFlow flow) {
        if (isDisabled()) return;
        if (invalidEvent("egress", deltaFile.getDid(),
                Map.of(
                        "timestamp", flow.getModified(),
                        "dataSource", deltaFile.getDataSource(),
                        "egressor", flow.getName()
                ))) return;

        FlowDefinition dataSourceFlow = flowDefinitionService.getOrCreateFlow(deltaFile.getDataSource(), deltaFile.firstFlow().getType());
        FlowDefinition egressFlow = flowDefinitionService.getOrCreateFlow(flow.getName(), flow.getType());
        Map<String, String> annotations = Annotation.toMap(deltaFile.getAnnotations());
        int eventGroupId = resolveEventGroupOrDefault(annotations);

        AnalyticsEntity entity = new AnalyticsEntity();
        entity.setId(new AnalyticsEntityId(deltaFile.getDid(), flow.getModified()));
        entity.setDataSourceId(dataSourceFlow.getId());
        entity.setFlowId(egressFlow.getId());
        entity.setEventGroupId(eventGroupId);
        entity.setEventType(EventTypeEnum.EGRESS);
        entity.setBytesCount(flow.lastContentSize());
        entity.setFileCount(1);
        entity.setAnalyticIngressType(deltaFile.getParentDids().isEmpty() ? AnalyticIngressTypeEnum.DATA_SOURCE : AnalyticIngressTypeEnum.CHILD);

        eventQueue.add(new QueuedAnalyticsEntity(entity));

        // Send to analytics collector
        String ingressType = deltaFile.getParentDids().isEmpty() ? "DATA_SOURCE" : "CHILD";
        analyticsClient.writeEvent(AnalyticsEventRequest.from(
                flow.getModified(), deltaFile.getCreated(), deltaFile.getDid(), deltaFile.getDataSource(),
                "EGRESS", flow.lastContentSize(), 1, flow.getName(),
                null, null, ingressType, filterAllowedAnnotations(annotations)));
    }

    /**
     * Generate an analytic event for DeltaFile ingress
     *
     * @param did the DeltaFile id
     * @param created creation time
     * @param dataSource the data source
     * @param dataSourceType the data source type
     * @param ingressBytes bytes ingressed
     * @param annotations set of annotations
     */
    public void recordIngress(UUID did,
                              OffsetDateTime created,
                              String dataSource,
                              FlowType dataSourceType,
                              long ingressBytes,
                              Map<String, String> annotations,
                              AnalyticIngressTypeEnum ingressType) {
        if (isDisabled()) return;
        if (invalidEvent("ingress", did,
                Map.of(
                        "timestamp", created,
                        "dataSource", dataSource,
                        "did", did
                ))) return;
        if (ingressBytes < 0) {
            log.warn("Discarding ingress metric for did {}: ingress bytes {} are negative", did, ingressBytes);
            return;
        }

        FlowDefinition dataSourceFlow = flowDefinitionService.getOrCreateFlow(dataSource, dataSourceType);
        int eventGroupId = resolveEventGroupOrDefault(annotations);

        AnalyticsEntity entity = new AnalyticsEntity();
        entity.setId(new AnalyticsEntityId(did, created));
        entity.setDataSourceId(dataSourceFlow.getId());
        entity.setEventGroupId(eventGroupId);
        entity.setEventType(EventTypeEnum.INGRESS);
        entity.setBytesCount(ingressBytes);
        entity.setFileCount(1);
        entity.setAnalyticIngressType(ingressType);

        eventQueue.add(new QueuedAnalyticsEntity(entity));

        if (!annotations.isEmpty()) {
            eventQueue.add(new QueuedAnnotationEvent(did, new HashMap<>(annotations)));
        }

        // Send to analytics collector (for ingress, created is both eventTime and creationTime)
        analyticsClient.writeEvent(AnalyticsEventRequest.from(
                created, created, did, dataSource, "INGRESS", ingressBytes, 1,
                null, null, null, ingressType.name(), filterAllowedAnnotations(annotations)));
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
        if (invalidEvent("survey", id,
                Map.of(
                        "timestamp", created,
                        "dataSource", dataSource,
                        "id", id
                ))) return;
        if (count < 0) {
            log.warn("Discarding survey metric for dataSource {}: count {} is negative", dataSource, count);
            return;
        }
        if (ingressBytes < 0) {
            log.warn("Discarding survey metric for dataSource {}: ingress bytes {} are negative", dataSource, ingressBytes);
            return;
        }

        FlowDefinition dataSourceFlow = flowDefinitionService.getOrCreateFlow(dataSource, FlowType.REST_DATA_SOURCE);
        int eventGroupId = resolveEventGroupOrDefault(annotations);

        AnalyticsEntity entity = new AnalyticsEntity();
        entity.setId(new AnalyticsEntityId(id, created));
        entity.setDataSourceId(dataSourceFlow.getId());
        entity.setEventGroupId(eventGroupId);
        entity.setEventType(EventTypeEnum.INGRESS);
        entity.setBytesCount(ingressBytes);
        entity.setFileCount(count);
        entity.setAnalyticIngressType(AnalyticIngressTypeEnum.SURVEY);

        eventQueue.add(new QueuedAnalyticsEntity(entity));

        if (!annotations.isEmpty()) {
            eventQueue.add(new QueuedAnnotationEvent(id, new HashMap<>(annotations)));
        }

        // Send to analytics collector (for survey, created is both eventTime and creationTime)
        analyticsClient.writeEvent(AnalyticsEventRequest.from(
                created, created, id, dataSource, "INGRESS", ingressBytes, count,
                null, null, null, "SURVEY", filterAllowedAnnotations(annotations)));
    }

    /**
     * Records an analytic event for a DeltaFile error.
     *
     * @param deltaFile  the DeltaFile that encountered an error
     * @param flowName   the name of the flow in which the error occurred
     * @param flowType   the type of the flow in which the error occurred
     * @param actionName the name of the action associated with the error
     * @param cause      a string representing the error cause
     * @param eventTime  the time when the error event occurred
     */
    public void recordError(DeltaFile deltaFile, String flowName, FlowType flowType, String actionName, String cause, OffsetDateTime eventTime) {
        if (isDisabled()) return;
        if (invalidEvent("error", deltaFile.getDid(),
                Map.of(
                        "timestamp", eventTime,
                        "dataSource", deltaFile.getDataSource()
                ))) return;

        FlowDefinition dataSourceFlow = flowDefinitionService.getOrCreateFlow(deltaFile.getDataSource(), deltaFile.firstFlow().getType());
        FlowDefinition errorFlow = flowDefinitionService.getOrCreateFlow(flowName, flowType);
        int eventGroupId = resolveEventGroupOrDefault(Annotation.toMap(deltaFile.getAnnotations()));
        int causeId = errorCauseService.getOrCreateCause(cause);
        int actionId = actionNameService.getOrCreateActionName(actionName);

        AnalyticsEntity entity = new AnalyticsEntity();
        entity.setId(new AnalyticsEntityId(deltaFile.getDid(), eventTime));
        entity.setDataSourceId(dataSourceFlow.getId());
        entity.setFlowId(errorFlow.getId());
        entity.setEventGroupId(eventGroupId);
        entity.setEventType(EventTypeEnum.ERROR);
        entity.setCauseId(causeId);
        entity.setActionId(actionId);
        entity.setFileCount(1);
        entity.setAnalyticIngressType(deltaFile.getParentDids().isEmpty() ? AnalyticIngressTypeEnum.DATA_SOURCE : AnalyticIngressTypeEnum.CHILD);

        eventQueue.add(new QueuedAnalyticsEntity(entity));

        // Send to analytics collector
        Map<String, String> annotations = Annotation.toMap(deltaFile.getAnnotations());
        String ingressType = deltaFile.getParentDids().isEmpty() ? "DATA_SOURCE" : "CHILD";
        analyticsClient.writeEvent(AnalyticsEventRequest.from(
                eventTime, deltaFile.getCreated(), deltaFile.getDid(), deltaFile.getDataSource(),
                "ERROR", 0, 1, flowName, actionName, cause, ingressType, filterAllowedAnnotations(annotations)));
    }

    /**
     * Generate an analytic event for DeltaFile filter.
     * @param deltaFile - filtered DeltaFile
     * @param flowName   the name of the flow in which the filter occurred
     * @param flowType   the type of the flow in which the filter occurred
     * @param actionName the name of the action associated with the filter
     * @param cause      a string representing the filter cause
     * @param eventTime  the time when the filter event occurred
     */
    public void recordFilter(DeltaFile deltaFile, String flowName, FlowType flowType, String actionName, String cause, OffsetDateTime eventTime) {
        if (isDisabled()) return;
        if (invalidEvent("filter", deltaFile.getDid(),
                Map.of(
                        "timestamp", eventTime,
                        "dataSource", deltaFile.getDataSource()
                ))) return;

        FlowDefinition dataSourceFlow = flowDefinitionService.getOrCreateFlow(deltaFile.getDataSource(), deltaFile.firstFlow().getType());
        FlowDefinition errorFlow = flowDefinitionService.getOrCreateFlow(flowName, flowType);
        int eventGroupId = resolveEventGroupOrDefault(Annotation.toMap(deltaFile.getAnnotations()));
        int causeId = errorCauseService.getOrCreateCause(cause);
        int actionId = actionNameService.getOrCreateActionName(actionName);

        AnalyticsEntity entity = new AnalyticsEntity();
        entity.setId(new AnalyticsEntityId(deltaFile.getDid(), eventTime));
        entity.setDataSourceId(dataSourceFlow.getId());
        entity.setFlowId(errorFlow.getId());
        entity.setEventGroupId(eventGroupId);
        entity.setEventType(EventTypeEnum.FILTER);
        entity.setCauseId(causeId);
        entity.setActionId(actionId);
        entity.setFileCount(1);
        entity.setAnalyticIngressType(deltaFile.getParentDids().isEmpty() ? AnalyticIngressTypeEnum.DATA_SOURCE : AnalyticIngressTypeEnum.CHILD);

        eventQueue.add(new QueuedAnalyticsEntity(entity));

        // Send to analytics collector
        Map<String, String> filterAnnotations = Annotation.toMap(deltaFile.getAnnotations());
        String filterIngressType = deltaFile.getParentDids().isEmpty() ? "DATA_SOURCE" : "CHILD";
        analyticsClient.writeEvent(AnalyticsEventRequest.from(
                eventTime, deltaFile.getCreated(), deltaFile.getDid(), deltaFile.getDataSource(),
                "FILTER", 0, 1, flowName, actionName, cause, filterIngressType, filterAllowedAnnotations(filterAnnotations)));
    }

    /**
     * Generate an analytic event for DeltaFile cancel.
     * @param deltaFile - cancelled DeltaFile
     */
    public void recordCancel(DeltaFile deltaFile) {
        if (isDisabled()) return;
        if (invalidEvent("cancel", deltaFile.getDid(),
                Map.of(
                        "timestamp", deltaFile.getModified(),
                        "dataSource", deltaFile.getDataSource()
                ))) return;

        FlowDefinition dataSourceFlow = flowDefinitionService.getOrCreateFlow(deltaFile.getDataSource(), deltaFile.firstFlow().getType());
        int eventGroupId = resolveEventGroupOrDefault(Annotation.toMap(deltaFile.getAnnotations()));

        AnalyticsEntity entity = new AnalyticsEntity();
        entity.setId(new AnalyticsEntityId(deltaFile.getDid(), deltaFile.getModified()));
        entity.setDataSourceId(dataSourceFlow.getId());
        entity.setEventGroupId(eventGroupId);
        entity.setEventType(EventTypeEnum.CANCEL);
        entity.setFileCount(1);
        entity.setAnalyticIngressType(deltaFile.getParentDids().isEmpty() ? AnalyticIngressTypeEnum.DATA_SOURCE : AnalyticIngressTypeEnum.CHILD);

        eventQueue.add(new QueuedAnalyticsEntity(entity));

        // Send to analytics collector
        Map<String, String> cancelAnnotations = Annotation.toMap(deltaFile.getAnnotations());
        String cancelIngressType = deltaFile.getParentDids().isEmpty() ? "DATA_SOURCE" : "CHILD";
        analyticsClient.writeEvent(AnalyticsEventRequest.from(
                deltaFile.getModified(), deltaFile.getCreated(), deltaFile.getDid(), deltaFile.getDataSource(),
                "CANCEL", 0, 1, null, null, null, cancelIngressType, filterAllowedAnnotations(cancelAnnotations)));
    }

    /**
     * Record a provenance event for DeltaFile lineage tracking.
     * Called when a DeltaFile reaches a terminal state (complete, error, filter, cancel).
     *
     * @param deltaFile the DeltaFile
     * @param terminalFlow the flow that reached a terminal state
     */
    public void recordProvenance(DeltaFile deltaFile, DeltaFileFlow terminalFlow) {
        if (!deltaFiPropertiesService.getDeltaFiProperties().isProvenanceEnabled()) {
            return;
        }

        String systemName = deltaFiPropertiesService.getDeltaFiProperties().getSystemName();
        List<String> transforms = buildTransformPath(deltaFile, terminalFlow);
        Map<String, String> annotations = filterProvenanceAnnotations(Annotation.toMap(deltaFile.getAnnotations()));
        String dataSink = terminalFlow.isDataSink() ? terminalFlow.getName() : null;
        String finalState = terminalFlow.getState().name();
        UUID parentDid = deltaFile.getParentDids().isEmpty() ? null : deltaFile.getParentDids().getFirst();

        provenanceClient.writeRecord(ProvenanceClient.ProvenanceRecord.from(
                deltaFile.getDid(),
                parentDid,
                systemName,
                deltaFile.getDataSource(),
                deltaFile.getName(),
                transforms,
                dataSink,
                finalState,
                deltaFile.getCreated(),
                terminalFlow.getModified(),
                annotations
        ));
    }

    /**
     * Record provenance for all cancelled flows in a DeltaFile.
     * Called when a DeltaFile is cancelled.
     *
     * @param deltaFile the cancelled DeltaFile
     */
    public void recordProvenanceForCancel(DeltaFile deltaFile) {
        if (!deltaFiPropertiesService.getDeltaFiProperties().isProvenanceEnabled()) {
            return;
        }

        for (DeltaFileFlow flow : deltaFile.getFlows()) {
            if (flow.getState() == DeltaFileFlowState.CANCELLED) {
                recordProvenance(deltaFile, flow);
            }
        }
    }

    /**
     * Record provenance for a split event.
     * Called when a DeltaFile is split into children.
     *
     * @param deltaFile the parent DeltaFile that was split
     * @param flow the flow where the split occurred
     */
    public void recordProvenanceForSplit(DeltaFile deltaFile, DeltaFileFlow flow) {
        if (!deltaFiPropertiesService.getDeltaFiProperties().isProvenanceEnabled()) {
            return;
        }

        String systemName = deltaFiPropertiesService.getDeltaFiProperties().getSystemName();
        List<String> transforms = buildTransformPath(deltaFile, flow);
        Map<String, String> annotations = filterProvenanceAnnotations(Annotation.toMap(deltaFile.getAnnotations()));
        UUID parentDid = deltaFile.getParentDids().isEmpty() ? null : deltaFile.getParentDids().getFirst();

        provenanceClient.writeRecord(ProvenanceClient.ProvenanceRecord.from(
                deltaFile.getDid(),
                parentDid,
                systemName,
                deltaFile.getDataSource(),
                deltaFile.getName(),
                transforms,
                null,  // No data sink for splits
                "SPLIT",
                deltaFile.getCreated(),
                flow.getModified(),
                annotations
        ));
    }

    /**
     * Build the list of transform flow names that led to the given terminal flow.
     * Walks backwards through ancestorIds to build the path.
     */
    private List<String> buildTransformPath(DeltaFile deltaFile, DeltaFileFlow terminalFlow) {
        List<String> transforms = new ArrayList<>();
        if (terminalFlow.getInput() == null || terminalFlow.getInput().getAncestorIds() == null) {
            return transforms;
        }
        for (int ancestorId : terminalFlow.getInput().getAncestorIds()) {
            DeltaFileFlow ancestor = deltaFile.getFlow(ancestorId);
            if (ancestor != null && ancestor.getType() == FlowType.TRANSFORM) {
                transforms.add(ancestor.getName());
            }
        }
        return transforms;
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

    /**
     * Queue annotations for batch processing
     * @param did DeltaFile ID
     * @param annotations Map of annotations
     * @param creationTime DeltaFile creation time for hour-based annotation partitioning
     */
    public void queueAnnotations(UUID did, Map<String, String> annotations, OffsetDateTime creationTime) {
        if (isDisabled() || annotations.isEmpty()) return;
        eventQueue.add(new QueuedAnnotationEvent(did, new HashMap<>(annotations)));

        analyticsClient.queueAnnotations(did, filterAllowedAnnotations(annotations), creationTime);
    }

    /**
     * Scheduled job to process analytics and annotation batches
     */
    public void processEventBatch() {
        if (isDisabled() || eventQueue.isEmpty()) return;

        do {
            List<QueuedAnalyticsEntity> analyticsBatch = new ArrayList<>(BATCH_SIZE);
            List<QueuedAnnotationEvent> annotationBatch = new ArrayList<>(BATCH_SIZE);
            // Poll items until the size of one batch reaches BATCH_SIZE,
            // or until there are no more items in the queue.
            label:
            while (analyticsBatch.size() < BATCH_SIZE || annotationBatch.size() < BATCH_SIZE) {
                QueuedEvent item = eventQueue.poll();
                switch (item) {
                    case null:
                        break label;
                    case QueuedAnalyticsEntity ae:
                        analyticsBatch.add(ae);
                        break;
                    case QueuedAnnotationEvent an:
                        annotationBatch.add(an);
                        break;
                    default:
                        break;
                }
            }
            // Process analytics events.
            if (!analyticsBatch.isEmpty()) {
                try {
                    List<AnalyticsEntity> entities = analyticsBatch.stream()
                            .map(QueuedAnalyticsEntity::entity)
                            .toList();
                    analyticsRepo.batchInsert(entities);
                } catch (Exception e) {
                    if (eventQueue.size() < MAX_QUEUE_SIZE) {
                        log.error("Error processing analytics batch. Re-queueing items.", e);
                        // Re-queue the items at the front of the deque to maintain order.
                        for (int i = annotationBatch.size() - 1; i >= 0; i--) {
                            eventQueue.offerFirst(annotationBatch.get(i));
                        }
                        for (int i = analyticsBatch.size() - 1; i >= 0; i--) {
                            eventQueue.offerFirst(analyticsBatch.get(i));
                        }
                    } else {
                        log.error("Error processing analytics batch. Max queue size exceeded, dropping items.", e);
                    }
                    break;
                }
            }

            // Process annotation events.
            if (!annotationBatch.isEmpty()) {
                try {
                    insertAnnotationsBulk(annotationBatch);
                } catch (Exception e) {
                    if (eventQueue.size() < MAX_QUEUE_SIZE) {
                        log.error("Error processing annotation batch: {}. Re-queueing items.", e.getMessage(), e);
                        for (int i = annotationBatch.size() - 1; i >= 0; i--) {
                            eventQueue.offerFirst(annotationBatch.get(i));
                        }
                    } else {
                        log.error("Error processing annotation batch: {}. Max queue size exceeded, dropping items.", e.getMessage(), e);
                    }
                }
            }
        } while (eventQueue.size() >= BATCH_SIZE && !isDisabled());
    }

    /**
     * Bulk insert annotations
     */
    private void insertAnnotationsBulk(List<QueuedAnnotationEvent> batch) {
        // Prepare bulk annotation inserts
        List<Object[]> batchParams = new ArrayList<>();
        for (QueuedAnnotationEvent event : batch) {
            Map<String, String> annotations = event.annotations();
            UUID did = event.did();

            for (Map.Entry<String, String> anno : annotations.entrySet()) {
                String keyName = anno.getKey();
                if (!deltaFiPropertiesService.getDeltaFiProperties().allowedAnalyticsAnnotationsList().contains(keyName)) {
                    continue;
                }
                String valName = anno.getValue();
                int keyId = annotationKeyService.getOrCreateKeyId(keyName);
                int valId = annotationValueService.getOrCreateValueId(valName);

                batchParams.add(new Object[]{did, keyId, valId});
            }
        }

        if (!batchParams.isEmpty()) {
            eventAnnotationsRepo.bulkUpsertAnnotations(batchParams);
        }

        // Group annotations by did for group id and updated field updates
        Map<UUID, Map<String, String>> didToAnnotations = new HashMap<>();
        for (QueuedAnnotationEvent event : batch) {
            UUID did = event.did();
            Map<String, String> annotations = event.annotations();

            didToAnnotations.merge(did, annotations, (existingMap, newMap) -> {
                Map<String, String> mergedMap = new HashMap<>(existingMap);
                mergedMap.putAll(newMap);
                return mergedMap;
            });
        }

        // "notify" analytics that they have been updated
        OffsetDateTime now = OffsetDateTime.now();
        List<Object[]> updatesWithGroup = new ArrayList<>();
        List<Object[]> updatesWithoutGroup = new ArrayList<>();

        for (Map.Entry<UUID, Map<String, String>> entry : didToAnnotations.entrySet()) {
            UUID did = entry.getKey();
            Integer eventGroup = resolveEventGroupOrNull(entry.getValue());
            if (eventGroup != null) {
                updatesWithGroup.add(new Object[]{ eventGroup, now, did });
            } else {
                updatesWithoutGroup.add(new Object[]{ now, did });
            }
        }

        if (!updatesWithGroup.isEmpty()) {
            analyticsRepo.batchUpdateEventGroupIdAndUpdated(updatesWithGroup);
        }

        if (!updatesWithoutGroup.isEmpty()) {
            analyticsRepo.batchUpdateUpdated(updatesWithoutGroup);
        }
    }

    private int resolveEventGroupOrDefault(Map<String, String> annotationMap) {
        if (!annotationMap.containsKey(deltaFiPropertiesService.getDeltaFiProperties().getAnalyticsGroupName())) {
            return eventGroupService.getOrCreateEventGroupId(DEFAULT_EVENT_GROUP);
        }
        String groupValue = annotationMap.get(deltaFiPropertiesService.getDeltaFiProperties().getAnalyticsGroupName());
        return eventGroupService.getOrCreateEventGroupId(groupValue);
    }

    private Integer resolveEventGroupOrNull(Map<String, String> annotationMap) {
        if (!annotationMap.containsKey(deltaFiPropertiesService.getDeltaFiProperties().getAnalyticsGroupName())) {
            return null;
        }
        String groupValue = annotationMap.get(deltaFiPropertiesService.getDeltaFiProperties().getAnalyticsGroupName());
        return eventGroupService.getOrCreateEventGroupId(groupValue);
    }

    public record SurveyError(String error, SurveyEvent surveyEvent) {
        public SurveyError(int index, SurveyEvent surveyEvent) {
            this("Invalid survey data at " + index, surveyEvent);
        }
    }

    public static class DisabledAnalyticsException extends RuntimeException { }

    private boolean invalidEvent(String eventType, UUID did, Map<String, Object> fields) {
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            if (entry.getValue() == null) {
                log.warn("Discarding {} metric for did {}: {} is null", eventType, did, entry.getKey());
                return true;
            }
        }
        return false;
    }
}