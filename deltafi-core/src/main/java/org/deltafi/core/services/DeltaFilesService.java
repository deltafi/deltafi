/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.services;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.deltafi.common.action.ActionEventQueue;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.content.ContentUtil;
import org.deltafi.common.converters.KeyValueConverter;
import org.deltafi.common.types.*;
import org.deltafi.common.uuid.UUIDGenerator;
import org.deltafi.core.audit.CoreAuditLogger;
import org.deltafi.core.collect.CollectEntry;
import org.deltafi.core.collect.ScheduledCollectService;
import org.deltafi.core.configuration.DeltaFiProperties;
import org.deltafi.core.exceptions.*;
import org.deltafi.core.generated.types.*;
import org.deltafi.core.metrics.MetricService;
import org.deltafi.core.metrics.MetricsUtil;
import org.deltafi.core.repo.DeltaFileRepo;
import org.deltafi.core.repo.QueuedAnnotationRepo;
import org.deltafi.core.retry.MongoRetryable;
import org.deltafi.core.types.ResumePolicy;
import org.deltafi.core.types.*;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.env.Environment;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.deltafi.common.constant.DeltaFiConstants.*;
import static org.deltafi.core.repo.DeltaFileRepoImpl.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeltaFilesService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private static final String PUBLISH_ACTION_NAME = "Publish";

    static {
        SimpleModule simpleModule = new SimpleModule().addSerializer(OffsetDateTime.class, new JsonSerializer<>() {
            @Override
            public void serialize(OffsetDateTime offsetDateTime, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
                jsonGenerator.writeString(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX").withZone(ZoneOffset.UTC).format(offsetDateTime));
            }
        });
        OBJECT_MAPPER.registerModule(simpleModule);
    }

    private static final ObjectWriter PRETTY_OBJECT_WRITER = OBJECT_MAPPER.writerWithDefaultPrettyPrinter();

    public static final String MISSING_FLOW_CAUSE = "The flow is no longer installed or running";

    private static final int DEFAULT_QUERY_LIMIT = 50;

    private final Clock clock;
    private final TransformFlowService transformFlowService;
    private final EgressFlowService egressFlowService;
    private final DeltaFiPropertiesService deltaFiPropertiesService;
    private final StateMachine stateMachine;
    private final DeltaFileRepo deltaFileRepo;
    private final ActionEventQueue actionEventQueue;
    private final ContentStorageService contentStorageService;
    private final ResumePolicyService resumePolicyService;
    private final MetricService metricService;
    private final CoreAuditLogger coreAuditLogger;
    private final DidMutexService didMutexService;
    private final DeltaFileCacheService deltaFileCacheService;
    private final DataSourceService dataSourceService;
    private final QueueManagementService queueManagementService;
    private final QueuedAnnotationRepo queuedAnnotationRepo;
    private final Environment environment;
    private final ScheduledCollectService scheduledCollectService;
    private final UUIDGenerator uuidGenerator;

    private ExecutorService executor;
    private Semaphore semaphore;

    private boolean processIncomingEvents = true;

    @PostConstruct
    public void init() {
        String scheduleActionEvents = environment.getProperty("schedule.actionEvents");
        if (scheduleActionEvents == null || scheduleActionEvents.equals("true")) {
            DeltaFiProperties properties = getProperties();
            int threadCount = properties.getCoreServiceThreads() > 0 ? properties.getCoreServiceThreads() : 16;
            executor = Executors.newFixedThreadPool(threadCount);
            log.info("Executors pool size: {}", threadCount);
            int internalQueueSize = properties.getCoreInternalQueueSize() > 0 ? properties.getCoreInternalQueueSize() : 64;
            semaphore = new Semaphore(internalQueueSize);
            log.info("Internal queue size: {}", internalQueueSize);
        }

        scheduledCollectService.registerHandlers(this::queueTimedOutCollect, this::failTimedOutCollect);
        scheduledCollectService.scheduleNextCollectCheck();
    }

    @PreDestroy
    public void onShutdown() throws InterruptedException {
        log.info("Shutting down DeltaFilesService");

        // stop processing new events
        processIncomingEvents = false;

        log.info("Waiting for executor threads to finish");

        // give a grace period events to be assigned to executor threads
        Thread.sleep(100);

        if (executor != null) {
            executor.shutdown();
            boolean ignored = executor.awaitTermination(5, TimeUnit.SECONDS);
        }

        if (deltaFileCacheService != null) {
            deltaFileCacheService.flush();
        }

        log.info("Shutdown DeltaFilesService complete");
    }

    public DeltaFile getDeltaFile(String did) {
        return deltaFileRepo.findById(did.toLowerCase()).orElse(null);
    }

    public DeltaFile getCachedDeltaFile(String did) {
        return deltaFileCacheService.get(did);
    }

    public String getRawDeltaFile(String did, boolean pretty) throws JsonProcessingException {
        DeltaFile deltaFile = getDeltaFile(did);

        if (deltaFile == null) {
            return null;
        }

        if (pretty) {
            return PRETTY_OBJECT_WRITER.writeValueAsString(deltaFile);
        }

        return OBJECT_MAPPER.writeValueAsString(deltaFile);
    }

    /**
     * Find the set of annotations that are pending for the DeltaFile
     * with the given did
     *
     * @param did of the DeltaFile to check
     * @return set of annotations this DeltaFile is waiting for
     */
    public Set<String> getPendingAnnotations(String did) {
        DeltaFile deltaFile = getDeltaFile(did);

        if (deltaFile == null) {
            throw new DgsEntityNotFoundException("DeltaFile " + did + " not found.");
        }

        return deltaFile.pendingAnnotationFlows()
                .stream().map(DeltaFileFlow::getPendingAnnotations)
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());
    }

    public DeltaFiles deltaFiles(Integer offset, Integer limit, DeltaFilesFilter filter, DeltaFileOrder orderBy) {
        return deltaFiles(offset, limit, filter, orderBy, null);
    }

    public DeltaFiles deltaFiles(Integer offset, Integer limit, DeltaFilesFilter filter, DeltaFileOrder orderBy,
            List<String> includeFields) {
        return deltaFileRepo.deltaFiles(offset, (Objects.nonNull(limit) && limit > 0) ? limit : DEFAULT_QUERY_LIMIT,
                filter, orderBy, includeFields);
    }

    public Map<String, DeltaFile> deltaFiles(List<String> dids) {
        return deltaFileRepo.findAllById(dids).stream()
                .collect(Collectors.toMap(DeltaFile::getDid, Function.identity()));
    }

    public List<DeltaFile> getLastCreatedDeltaFiles(Integer last) {
        PageRequest pageRequest = PageRequest.of(0, last);
        return deltaFileRepo.findAllByOrderByCreatedDesc(pageRequest).getContent();
    }

    public List<DeltaFile> getLastModifiedDeltaFiles(Integer last) {
        PageRequest pageRequest = PageRequest.of(0, last);
        return deltaFileRepo.findAllByOrderByModifiedDesc(pageRequest).getContent();
    }

    public List<DeltaFile> getLastErroredDeltaFiles(Integer last) {
        PageRequest pageRequest = PageRequest.of(0, last);
        return deltaFileRepo.findByStageOrderByModifiedDesc(DeltaFileStage.ERROR, pageRequest).getContent();
    }

    public DeltaFile getLastWithName(String name) {
        PageRequest pageRequest = PageRequest.of(0, 1);
        List<DeltaFile> matches = deltaFileRepo.findByNameOrderByCreatedDesc(name, pageRequest).getContent();
        return matches.isEmpty() ? null : matches.getFirst();
    }

    public long countUnacknowledgedErrors() {
        return deltaFileRepo.countByStageAndErrorAcknowledgedIsNull(DeltaFileStage.ERROR);
    }

    public DeltaFile ingress(RestDataSource restDataSource, IngressEventItem ingressEventItem, OffsetDateTime ingressStartTime,
            OffsetDateTime ingressStopTime) {
        return ingress(restDataSource, ingressEventItem, Collections.emptyList(), ingressStartTime, ingressStopTime);
    }

    private DeltaFile buildIngressDeltaFile(DataSource dataSource, IngressEventItem ingressEventItem, List<String> parentDids,
                                            OffsetDateTime ingressStartTime, OffsetDateTime ingressStopTime,
                                            String ingressActionName, FlowType flowType) {

        OffsetDateTime now = OffsetDateTime.now(clock);

        Action ingressAction = Action.builder()
                .name(ingressActionName)
                .id(0)
                .type(ActionType.INGRESS)
                .state(ActionState.COMPLETE)
                .created(ingressStartTime)
                .modified(now)
                .content(ingressEventItem.getContent())
                .metadata(ingressEventItem.getMetadata())
                .start(ingressStartTime)
                .stop(ingressStopTime)
                .build();

        DeltaFileFlow ingressFlow = DeltaFileFlow.builder()
                .name(ingressEventItem.getFlowName())
                .id(0)
                .type(flowType)
                .state(DeltaFileFlowState.COMPLETE)
                .created(ingressStartTime)
                .modified(now)
                .actions(new ArrayList<>(List.of(ingressAction)))
                .publishTopics(new ArrayList<>())
                .depth(0)
                .flowPlan(new FlowPlanCoordinates(dataSource.getName(), dataSource.getSourcePlugin().groupAndArtifact(),
                        dataSource.getSourcePlugin().getVersion()))
                .build();

        long contentSize = ContentUtil.computeContentSize(ingressEventItem.getContent());

        return DeltaFile.builder()
                .schemaVersion(DeltaFile.CURRENT_SCHEMA_VERSION)
                .did(ingressEventItem.getDid())
                .dataSource(dataSource.getName())
                .name(ingressEventItem.getDeltaFileName())
                .normalizedName(ingressEventItem.getDeltaFileName().toLowerCase())
                .parentDids(parentDids)
                .childDids(new ArrayList<>())
                .requeueCount(0)
                .ingressBytes(contentSize)
                .totalBytes(contentSize)
                .stage(DeltaFileStage.IN_FLIGHT)
                .inFlight(true)
                .flows(new ArrayList<>(List.of(ingressFlow)))
                .created(ingressStartTime)
                .modified(now)
                .egressed(false)
                .filtered(false)
                .build();
    }

    private DeltaFile ingress(RestDataSource restDataSource, IngressEventItem ingressEventItem, List<String> parentDids, OffsetDateTime ingressStartTime,
            OffsetDateTime ingressStopTime) {
        DeltaFile deltaFile = buildIngressDeltaFile(restDataSource, ingressEventItem, parentDids, ingressStartTime, ingressStopTime,
                INGRESS_ACTION, FlowType.REST_DATA_SOURCE);

        advanceAndSave(List.of(new StateMachineInput(deltaFile, deltaFile.getFlows().getFirst())));
        return deltaFile;
    }

    public DeltaFile buildIngressDeltaFile(DataSource dataSource, ActionEvent parentEvent, IngressEventItem ingressEventItem) {
        ingressEventItem.setFlowName(parentEvent.getFlowName());
        return buildIngressDeltaFile(dataSource, ingressEventItem, Collections.emptyList(), parentEvent.getStart(),
                parentEvent.getStop(), parentEvent.getActionName(), FlowType.TIMED_DATA_SOURCE);
    }

//    public DeltaFile ingressOrErrorOnMissingFlow(IngressEventItem ingressEventItem, String ingressActionName,
//            OffsetDateTime ingressStartTime, OffsetDateTime ingressStopTime) {
//        DeltaFile deltaFile = buildIngressDeltaFile(ingressEventItem, Collections.emptyList(), ingressStartTime,
//                ingressStopTime, ingressActionName, FlowType.TIMED_DATA_SOURCE);
//
//        try {
//            advanceAndSave(List.of(new StateMachineInput(deltaFile, deltaFile.getFlows().get(0))));
//        } catch (MissingFlowException e) {
//            Action ingressAction = deltaFile.getFlows().get(0).getActions().get(0);
//            ingressAction.setState(ActionState.ERROR);
//            ingressAction.setErrorCause(e.getMessage());
//            deltaFile.setStage(DeltaFileStage.ERROR);
//            deltaFileRepo.save(deltaFile);
//        }
//        return deltaFile;
//    }

    public void handleActionEvent(ActionEvent event) {
        if (event.getType() == ActionEventType.INGRESS) {
            handleIngressActionEvent(event);
        } else {
            handleProcessingActionEvent(event);
        }
    }

    public void handleProcessingActionEvent(ActionEvent event) {
        synchronized (didMutexService.getMutex(event.getDid())) {
            DeltaFile deltaFile = getCachedDeltaFile(event.getDid());

            if (deltaFile == null) {
                throw new InvalidActionEventException("DeltaFile " + event.getDid() + " not found.");
            }

            if (deltaFile.getStage() == DeltaFileStage.CANCELLED) {
                log.warn("Received event for cancelled did {}", deltaFile.getDid());
                return;
            }

            DeltaFileFlow flow = deltaFile.getPendingFlow(event.getFlowName(), event.getFlowId());
            Action action = flow.getPendingAction(event.getActionName(), event.getActionId(), event.getDid());

            String validationError = event.validatePayload();
            if (validationError != null) {
                event.setError(ErrorEvent.builder()
                        .cause(INVALID_ACTION_EVENT_RECEIVED)
                        .context(validationError + ": " + event)
                        .build());
                error(deltaFile, flow, action, event);
                return;
            }

            List<Metric> metrics = (event.getMetrics() != null) ? event.getMetrics() : new ArrayList<>();
            metrics.add(new Metric(DeltaFiConstants.FILES_IN, 1));

            switch (event.getType()) {
                case TRANSFORM -> {
                    generateMetrics(metrics, event, deltaFile, flow, action);
                    transform(deltaFile, flow, action, event);
                }
                case EGRESS -> {
                    metrics.add(
                            Metric.builder()
                                    .name(EXECUTION_TIME_MS)
                                    .value(Duration.between(deltaFile.getCreated(), deltaFile.getModified()).toMillis())
                                    .build());
                    generateMetrics(metrics, event, deltaFile, flow, action);
                    egress(deltaFile, flow, action);
                }
                case ERROR -> {
                    generateMetrics(metrics, event, deltaFile, flow, action);
                    error(deltaFile, flow, action, event);
                }
                case FILTER -> {
                    metrics.add(new Metric(DeltaFiConstants.FILES_FILTERED, 1));
                    generateMetrics(metrics, event, deltaFile, flow, action);
                    filter(deltaFile, flow, action, event, OffsetDateTime.now());
                }
                default -> throw new UnknownTypeException(event.getActionName(), deltaFile.getDid(), event.getType());
            }

            completeCollect(event, deltaFile, flow, action, OffsetDateTime.now());
        }
    }

    private void generateMetrics(List<Metric> metrics, ActionEvent event, DeltaFile deltaFile, DeltaFileFlow flow,
                                 Action action) {
        generateMetrics(true, metrics, event, deltaFile, flow, action);
    }

    private void generateMetrics(boolean actionExecuted, List<Metric> metrics, ActionEvent event, DeltaFile deltaFile,
                                 DeltaFileFlow flow, Action action) {
        String egressFlow = flow.getType() == FlowType.EGRESS ? flow.getName() : null;
        Map<String, String> defaultTags = MetricsUtil.tagsFor(event.getType(), action.getName(),
                deltaFile.getDataSource(), egressFlow);
        for (Metric metric : metrics) {
            metric.addTags(defaultTags);
            metricService.increment(metric);
        }

        // Don't track execution times for internally generated error events,
        // or if we've already recorded them
        if (actionExecuted) {
            String actionClass = null;
            ActionConfiguration actionConfiguration = actionConfiguration(flow.getName(), action.getName());
            if (actionConfiguration != null) {
                actionClass = actionConfiguration.getType();
            }
            generateActionExecutionMetric(defaultTags, event, actionClass);
        }
    }

    private void generateIngressMetrics(List<Metric> metrics, ActionEvent event, String flow, String actionClass) {
        Map<String, String> defaultTags = MetricsUtil.tagsFor(event.getType(), event.getActionName(), flow, null);
        for (Metric metric : metrics) {
            metric.addTags(defaultTags);
            metricService.increment(metric);
        }
        generateActionExecutionMetric(defaultTags, event, actionClass);
    }

    private void generateActionExecutionMetric(Map<String, String> tags, ActionEvent event, String actionClass) {
        if (event.getType() != ActionEventType.UNKNOWN && event.getStart() != null && event.getStop() != null) {
            MetricsUtil.extendTagsForAction(tags, actionClass);

            Metric actionMetric = Metric.builder()
                    .name(ACTION_EXECUTION_TIME_MS)
                    .value(Duration.between(event.getStart(), event.getStop()).toMillis())
                    .build();

            actionMetric.addTags(tags);
            metricService.increment(actionMetric);
        }
    }

    public void handleIngressActionEvent(ActionEvent event) {
        TimedDataSource dataSource = dataSourceService.getRunningTimedDataSource(event.getFlowName());
        IngressEvent ingressEvent = event.getIngress();
        boolean completedExecution = dataSourceService.completeExecution(dataSource.getName(),
                event.getDid(), ingressEvent.getMemo(), ingressEvent.isExecuteImmediate(),
                ingressEvent.getStatus(), ingressEvent.getStatusMessage(), dataSource.getCronSchedule());
        if (!completedExecution) {
            log.warn("Received unexpected ingress event for flow {} with did {}", event.getFlowName(), event.getDid());
            return;
        }

        final Counter counter = new Counter();
        List<DeltaFile> deltaFiles = ingressEvent.getIngressItems().stream()
                .map((item) -> buildIngressDeltaFile(dataSource, event, item))
                .toList();
        List<StateMachineInput> stateMachineInputs = deltaFiles.stream()
                .map((deltaFile) -> new StateMachineInput(deltaFile, deltaFile.getFlows().getFirst()))
                .toList();

        advanceAndSave(stateMachineInputs);

        for (DeltaFile deltaFile : deltaFiles) {
            counter.byteCount += deltaFile.getIngressBytes();
            if (deltaFile.getFlows().size() == 1) {
                ActionState lastState = deltaFile.getFlows().getFirst().lastAction().getState();
                if (lastState == ActionState.FILTERED) {
                    counter.filteredFiles++;
                } else if (lastState == ActionState.ERROR) {
                    counter.erroredFiles++;
                }
            }
        }

        String actionClass =
                (dataSource.findActionConfigByName(event.getActionName()) != null)
                        ? dataSource.findActionConfigByName(event.getActionName()).getType()
                        : null;

        List<Metric> metrics = (event.getMetrics() != null) ? event.getMetrics() : new ArrayList<>();
        metrics.add(new Metric(DeltaFiConstants.FILES_IN, ingressEvent.getIngressItems().size()));
        metrics.add(new Metric(DeltaFiConstants.BYTES_IN, counter.byteCount));

        if (counter.erroredFiles > 0) {
            metrics.add(new Metric(FILES_ERRORED, counter.erroredFiles));
        } else if (counter.filteredFiles > 0) {
            metrics.add(new Metric(FILES_FILTERED, counter.filteredFiles));
        }

        generateIngressMetrics(metrics, event, dataSource.getName(), actionClass);
    }

    private void completeSyntheticPublishAction(DeltaFileFlow flow) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        Action action = flow.queueNewAction(PUBLISH_ACTION_NAME, ActionType.PUBLISH, false, now);
        action.setState(ActionState.COMPLETE);
        action.setStart(now);
        action.setStop(now);
    }

    public void transform(DeltaFile deltaFile, DeltaFileFlow flow, Action action, ActionEvent event) {
        List<TransformEvent> transformEvents = event.getTransform();
        try {
            transformFlowService.getRunningFlowByName(event.getFlowName());
        } catch (MissingFlowException missingFlowException) {
            handleMissingFlow(deltaFile, flow, missingFlowException.getMessage());
            return;
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        deltaFile.setModified(now);
        flow.setModified(now);

        if (transformEvents.size() == 1 && transformEvents.getFirst().getName() == null) {
            TransformEvent transformEvent = transformEvents.getFirst();
            deltaFile.addAnnotations(transformEvent.getAnnotations());
            action.complete(event.getStart(), event.getStop(), transformEvent.getContent(),
                    transformEvent.getMetadata(), transformEvent.getDeleteMetadataKeys(), now);
            advanceAndSave(List.of(new StateMachineInput(deltaFile, flow)));
        } else {
            action.changeState(ActionState.SPLIT, event.getStart(), event.getStop(), now);
            List<StateMachineInput> inputs = new ArrayList<>();
            inputs.add(new StateMachineInput(deltaFile, flow));
            inputs.addAll(createChildren(transformEvents, event.getStart(), event.getStop(), deltaFile, flow));
            deltaFile.getChildDids().addAll(inputs.stream().map(input -> input.deltaFile().getDid()).toList());
            advanceAndSave(inputs);
        }
    }

    public List<StateMachineInput> createChildren(List<TransformEvent> transformEvents, OffsetDateTime startTime,
                                          OffsetDateTime stopTime,DeltaFile deltaFile, DeltaFileFlow flow) {
        return transformEvents.stream()
                .map(transformEvent -> createChildDeltaFile(transformEvent, deltaFile, flow, startTime, stopTime))
                .toList();
    }

    public void egress(DeltaFile deltaFile, DeltaFileFlow flow, Action action) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        deltaFile.setModified(now);
        deltaFile.setEgressed(true);
        flow.setModified(now);
        flow.setState(DeltaFileFlowState.COMPLETE);
        action.setModified(now);
        action.setContent(flow.getInput().getContent());
        action.setMetadata(flow.getMetadata());
        action.setState(ActionState.COMPLETE);

        advanceAndSave(List.of(new StateMachineInput(deltaFile, flow)));
    }

    public void filter(DeltaFile deltaFile, DeltaFileFlow flow, Action action, ActionEvent event, OffsetDateTime now) {
        deltaFile.addAnnotations(event.getFilter().getAnnotations());
        deltaFile.setModified(now);
        deltaFile.setFiltered(true);
        action.setFilteredActionState(event.getStart(), event.getStop(), now, event.getFilter().getMessage(),
                event.getFilter().getContext());

        advanceAndSave(List.of(new StateMachineInput(deltaFile, flow)));
    }

    private void error(DeltaFile deltaFile, DeltaFileFlow flow, Action action, ActionEvent event) {
        deltaFile.addAnnotations(event.getError().getAnnotations());

        // If the content was deleted by a delete policy mark as CANCELLED instead of ERROR
        if (deltaFile.getContentDeleted() != null) {
            deltaFile.cancel(OffsetDateTime.now(clock));
            deltaFileCacheService.save(deltaFile);
        } else {
            processErrorEvent(deltaFile, flow, action, event);
            advanceAndSave(List.of(new StateMachineInput(deltaFile, flow)));
        }
    }

    public void processErrorEvent(DeltaFile deltaFile, DeltaFileFlow flow, Action action, ActionEvent event) {
        OffsetDateTime now = OffsetDateTime.now();
        action.error(event.getStart(), event.getStop(), now, event.getError().getCause(), event.getError().getContext());
        Optional<ResumePolicyService.ResumeDetails> resumeDetails =
                resumePolicyService.getAutoResumeDelay(deltaFile, action, event.getError().getCause());
        if (resumeDetails.isPresent()) {
            action.setNextAutoResume(event.getStop().plusSeconds(resumeDetails.get().delay()));
            action.setNextAutoResumeReason(resumeDetails.get().name());
        }
        flow.updateState(now);
        deltaFile.updateState(now);

        // false: we don't want action execution metrics, since they have already been recorded.
        generateMetrics(false, List.of(new Metric(DeltaFiConstants.FILES_ERRORED, 1)), event, deltaFile, flow, action);
    }

    private DeltaFile getTerminalDeltaFileOrCache(String did) {
        if (deltaFileCacheService.isCached(did)) {
            return getCachedDeltaFile(did);
        }
        return deltaFileRepo.findByDidAndStageIn(did,
                        Arrays.asList(DeltaFileStage.COMPLETE, DeltaFileStage.ERROR, DeltaFileStage.CANCELLED))
                .orElse(null);
    }

    public void addAnnotations(String rawDid, Map<String, String> annotations, boolean allowOverwrites) {
        String did = rawDid.toLowerCase();
        synchronized (didMutexService.getMutex(did)) {
            DeltaFile deltaFile = getTerminalDeltaFileOrCache(did);

            if (deltaFile == null) {
                if (deltaFileRepo.existsById(did)) {
                    QueuedAnnotation queuedAnnotation = new QueuedAnnotation(did, annotations, allowOverwrites);
                    queuedAnnotationRepo.insert(queuedAnnotation);
                    return;
                } else {
                    throw new DgsEntityNotFoundException("DeltaFile " + did + " not found.");
                }
            }

            addAnnotations(deltaFile, annotations, allowOverwrites, OffsetDateTime.now());
        }
    }

    public void processQueuedAnnotations() {
        List<QueuedAnnotation> queuedAnnotations = queuedAnnotationRepo.findAllByOrderByTimeAsc();
        for (QueuedAnnotation queuedAnnotation : queuedAnnotations) {
            String did = queuedAnnotation.getDid();
            synchronized (didMutexService.getMutex(did)) {
                DeltaFile deltaFile = getTerminalDeltaFileOrCache(did);

                if (deltaFile == null && deltaFileRepo.existsById(did)) {
                    log.warn("Attempted to apply queued annotation to deltaFile {} that no longer exists", did);
                    continue;
                }

                addAnnotations(deltaFile, queuedAnnotation.getAnnotations(), queuedAnnotation.isAllowOverwrites(), queuedAnnotation.getTime());
                queuedAnnotationRepo.deleteById(queuedAnnotation.getId());
            }
        }
    }

    @MongoRetryable
    private void addAnnotations(DeltaFile deltaFile, Map<String, String> annotations, boolean allowOverwrites, OffsetDateTime annotationTime) {
        if (allowOverwrites) {
            deltaFile.addAnnotations(annotations);
        } else {
            deltaFile.addAnnotationsIfAbsent(annotations);
        }

        deltaFile.updatePendingAnnotations();

        if (deltaFile.getModified().isBefore(annotationTime)) {
            deltaFile.setModified(annotationTime);
        }

        deltaFileCacheService.save(deltaFile);
    }

    @Async
    public void asyncUpdatePendingAnnotationsForFlows(String flowName, Set<String> expectedAnnotations) {
        updatePendingAnnotationsForFlows(flowName, expectedAnnotations);
    }

    /**
     * Find the DeltaFiles that are pending annotations for the given flow and check if they satisfy
     * the new set of expectedAnnotations
     * @param flowName name of the flow
     * @param expectedAnnotations new set of expected annotations for the given flow
     */
    public void updatePendingAnnotationsForFlows(String flowName, Set<String> expectedAnnotations) {
        int batchSize = deltaFiPropertiesService.getDeltaFiProperties().getDelete().getPolicyBatchSize();
        List<DeltaFile> updatedDeltaFiles = new ArrayList<>();
        try (Stream<DeltaFile> deltaFiles = deltaFileRepo.findByTerminalAndFlowsNameAndFlowsState(false, flowName, DeltaFileFlowState.PENDING_ANNOTATIONS)) {
            deltaFiles.forEach(deltaFile -> updatePendingAnnotationsForFlowsAndCollect(deltaFile, flowName, expectedAnnotations, updatedDeltaFiles, batchSize));
        }
        if (!updatedDeltaFiles.isEmpty()) {
            deltaFileRepo.saveAll(updatedDeltaFiles);
        }
    }

    /*
     * Update the pending annotations in the given DeltaFile based on the latest expectedAnnotations for the flow
     * If the collector list hits the batchSize limit, save the updated DeltaFiles and flush the list.
     */
    void updatePendingAnnotationsForFlowsAndCollect(DeltaFile deltaFile, String flowName, Set<String> expectedAnnotations, List<DeltaFile> collector, int batchSize) {
        deltaFile.setPendingAnnotations(flowName, expectedAnnotations, OffsetDateTime.now(clock));
        collector.add(deltaFile);

        if (collector.size() == batchSize) {
            deltaFileRepo.saveAll(collector);
            collector.clear();
        }
    }

    public static ActionEvent buildMissingFlowErrorEvent(DeltaFile deltaFile, OffsetDateTime time, String errorContext) {
        return ActionEvent.builder()
                .did(deltaFile.getDid())
                .flowName("MISSING")
                .actionName(MISSING_FLOW_ACTION)
                .start(time)
                .stop(time)
                .error(ErrorEvent.builder()
                        .cause(MISSING_FLOW_CAUSE)
                        .context(errorContext)
                        .build())
                .type(ActionEventType.UNKNOWN)
                .build();
    }

    private StateMachineInput createChildDeltaFile(TransformEvent transformEvent, DeltaFile deltaFile,
                                                  DeltaFileFlow fromFlow, OffsetDateTime startTime,
                                                  OffsetDateTime stopTime) {
        DeltaFile child = OBJECT_MAPPER.convertValue(deltaFile, DeltaFile.class);
        child.setVersion(0);
        child.setDid(uuidGenerator.generate());
        String eventName = transformEvent.getName();
        child.setName(eventName);
        child.setNormalizedName(eventName != null ? eventName.toLowerCase() : null);
        child.setChildDids(Collections.emptyList());
        child.setParentDids(List.of(deltaFile.getDid()));
        DeltaFileFlow flow = child.getFlow(fromFlow.getName(), fromFlow.getId());
        Action action = flow.lastAction();
        action.changeState(ActionState.COMPLETE, startTime, stopTime, OffsetDateTime.now(clock));

        if (transformEvent.getContent() != null) {
            action.setContent(transformEvent.getContent());
        }

        if (transformEvent.getMetadata() != null) {
            action.setMetadata(transformEvent.getMetadata());
        }

        if (transformEvent.getDeleteMetadataKeys() != null) {
            action.setDeleteMetadataKeys(transformEvent.getDeleteMetadataKeys());
        }

        child.removeFlowsNotDescendedFrom(fromFlow.getId());
        return new StateMachineInput(child, flow);
    }

    public List<RetryResult> resume(@NotNull List<String> dids, @NotNull List<ResumeMetadata> resumeMetadata) {
        Map<String, DeltaFile> deltaFiles = deltaFiles(dids);
        List<StateMachineInput> advanceAndSaveInputs = new ArrayList<>();

        List<RetryResult> retryResults = dids.stream()
                .map(did -> {
                    RetryResult result = RetryResult.newBuilder()
                            .did(did)
                            .success(true)
                            .build();

                    try {
                        DeltaFile deltaFile = deltaFiles.get(did);
                        OffsetDateTime now = OffsetDateTime.now(clock);

                        if (deltaFile == null) {
                            result.setSuccess(false);
                            result.setError("DeltaFile with did " + did + " not found");
                        } else if (deltaFile.getContentDeleted() != null) {
                            result.setSuccess(false);
                            result.setError("Cannot resume DeltaFile " + did + " after content was deleted (" + deltaFile.getContentDeletedReason() + ")");
                        } else {
                            List<DeltaFileFlow> retryFlows = deltaFile.resumeErrors(resumeMetadata, now);
                            if (!retryFlows.isEmpty()) {
                                deltaFile.updateState(now);
                                advanceAndSaveInputs.addAll(retryFlows.stream()
                                        .map(flow -> new StateMachineInput(deltaFile, flow))
                                        .toList());
                            } else {
                                result.setSuccess(false);
                                result.setError("DeltaFile with did " + did + " had no errors");
                            }
                        }
                    } catch (Exception e) {
                        result.setSuccess(false);
                        result.setError(e.getMessage());
                    }
                    return result;
                })
                .toList();

        advanceAndSave(advanceAndSaveInputs);
        return retryResults;
    }

    private static void addRetryAction(DeltaFile deltaFile, @NotNull List<String> removeSourceMetadata,
                                       @NotNull List<KeyValue> replaceSourceMetadata, OffsetDateTime now) {
        List<Content> content = deltaFile.getFlows().getFirst().lastAction().getContent();
        Action replayAction = deltaFile.getFlows().getFirst()
                .addAction("Replay", ActionType.INGRESS, ActionState.COMPLETE, now);
        replayAction.setContent(content);
        if (!removeSourceMetadata.isEmpty()) {
            replayAction.setDeleteMetadataKeys(removeSourceMetadata);
        }
        if (!replaceSourceMetadata.isEmpty()) {
            replayAction.setMetadata(KeyValueConverter.convertKeyValues(replaceSourceMetadata));
        }
    }

    public List<RetryResult> replay(@NotNull List<String> dids, @NotNull List<String> removeSourceMetadata, @NotNull List<KeyValue> replaceSourceMetadata)  {
        Map<String, DeltaFile> deltaFiles = deltaFiles(dids);
        List<StateMachineInput> inputs = new ArrayList<>();
        List<DeltaFile> parents = new ArrayList<>();

        List<RetryResult> results = dids.stream()
                .map(did -> {
                    RetryResult result = RetryResult.newBuilder()
                            .did(did)
                            .success(true)
                            .build();

                    try {
                        DeltaFile deltaFile = deltaFiles.get(did);

                        if (deltaFile == null) {
                            result.setSuccess(false);
                            result.setError("DeltaFile with did " + did + " not found");
                        } else if (deltaFile.getReplayed() != null) {
                            result.setSuccess(false);
                            result.setError("DeltaFile with did " + did + " has already been replayed with child " + deltaFile.getReplayDid());
                        } else if (deltaFile.getContentDeleted() != null) {
                            result.setSuccess(false);
                            result.setError("Cannot replay DeltaFile " + did + " after content was deleted (" + deltaFile.getContentDeletedReason() + ")");
                        } else {
                            OffsetDateTime now = OffsetDateTime.now(clock);
                            DeltaFileFlow firstFlow = deltaFile.getFlows().getFirst();
                            Action firstAction = firstFlow.getActions().getFirst();
                            Action action = Action.builder()
                                    .name(firstAction.getName())
                                    .type(ActionType.INGRESS)
                                    .state(ActionState.COMPLETE)
                                    .created(now)
                                    .modified(now)
                                    .content(firstAction.getContent())
                                    .metadata(firstAction.getMetadata())
                                    .build();

                            DeltaFileFlow flow = DeltaFileFlow.builder()
                                    .name(firstFlow.getName())
                                    .type(firstFlow.getType())
                                    .state(DeltaFileFlowState.COMPLETE)
                                    .input(firstFlow.getInput())
                                    .created(now)
                                    .modified(now)
                                    .actions(new ArrayList<>(List.of(action)))
                                    .build();

                            DeltaFile child = DeltaFile.builder()
                                    .schemaVersion(DeltaFile.CURRENT_SCHEMA_VERSION)
                                    .did(uuidGenerator.generate())
                                    .parentDids(List.of(deltaFile.getDid()))
                                    .childDids(new ArrayList<>())
                                    .requeueCount(0)
                                    .ingressBytes(deltaFile.getIngressBytes())
                                    .stage(DeltaFileStage.IN_FLIGHT)
                                    .inFlight(true)
                                    .terminal(false)
                                    .contentDeletable(false)
                                    .flows(new ArrayList<>(List.of(flow)))
                                    .dataSource(deltaFile.getDataSource())
                                    .name(deltaFile.getName())
                                    .normalizedName(deltaFile.getNormalizedName())
                                    .created(now)
                                    .modified(now)
                                    .egressed(false)
                                    .filtered(false)
                                    .build();

                            addRetryAction(child, removeSourceMetadata, replaceSourceMetadata, now);

                            inputs.add(new StateMachineInput(child, flow));

                            deltaFile.setReplayed(now);
                            deltaFile.setReplayDid(child.getDid());
                            if (Objects.isNull(deltaFile.getChildDids())) {
                                deltaFile.setChildDids(new ArrayList<>());
                            }
                            deltaFile.getChildDids().add(child.getDid());
                            parents.add(deltaFile);
                            result.setDid(child.getDid());
                        }
                    } catch (Exception e) {
                        result.setSuccess(false);
                        result.setError(e.getMessage());
                    }
                    return result;
                })
                .toList();

        advanceAndSave(inputs);
        deltaFileRepo.saveAll(parents);

        return results;
    }

    public List<AcknowledgeResult> acknowledge(List<String> dids, String reason) {
        Map<String, DeltaFile> deltaFiles = deltaFiles(dids);

        OffsetDateTime now = OffsetDateTime.now(clock);
        List<DeltaFile> changedDeltaFiles = new ArrayList<>();

        List<AcknowledgeResult> results = dids.stream()
                .map(did -> {
                    AcknowledgeResult result = AcknowledgeResult.newBuilder()
                            .did(did)
                            .success(true)
                            .build();

                    try {
                        DeltaFile deltaFile = deltaFiles.get(did);

                        if (Objects.isNull(deltaFile)) {
                            result.setSuccess(false);
                            result.setError("DeltaFile with did " + did + " not found");
                        } else {
                            deltaFile.acknowledgeErrors(now, reason);
                            changedDeltaFiles.add(deltaFile);
                        }
                    } catch (Exception e) {
                        result.setSuccess(false);
                        result.setError(e.getMessage());
                    }
                    return result;
                })
                .toList();

        deltaFileRepo.saveAll(changedDeltaFiles);
        return results;
    }

    public List<CancelResult> cancel(List<String> dids) {
        Map<String, DeltaFile> deltaFiles = deltaFiles(dids);
        List<DeltaFile> changedDeltaFiles = new ArrayList<>();

        List<CancelResult> results = dids.stream()
                .map(did -> {
                    CancelResult result = CancelResult.newBuilder()
                            .did(did)
                            .success(true)
                            .build();

                    try {
                        DeltaFile deltaFile = deltaFiles.get(did);

                        if (Objects.isNull(deltaFile)) {
                            result.setSuccess(false);
                            result.setError("DeltaFile with did " + did + " not found");
                        } else if (!deltaFile.canBeCancelled()) {
                            result.setSuccess(false);
                            result.setError("DeltaFile with did " + did + " is no longer active");
                        } else {
                            deltaFile.cancel(OffsetDateTime.now(clock));
                            changedDeltaFiles.add(deltaFile);
                        }
                    } catch (Exception e) {
                        result.setSuccess(false);
                        result.setError(e.getMessage());
                    }
                    return result;
                })
                .toList();

        deltaFileRepo.saveAll(changedDeltaFiles);
        return results;
    }

    public List<PerActionUniqueKeyValues> errorMetadataUnion(List<String> dids) {
        DeltaFilesFilter filter = new DeltaFilesFilter();
        filter.setDids(dids);
        DeltaFiles deltaFiles = deltaFiles(0, dids.size(), filter, null, List.of(FLOWS_INPUT_METADATA, FLOWS_NAME, FLOWS_ACTIONS_NAME, FLOWS_ACTIONS_TYPE, FLOWS_ACTIONS_STATE, FLOWS_ACTIONS_METADATA, FLOWS_ACTIONS_DELETE_METADATA_KEYS));

        Map<Pair<String, String>, PerActionUniqueKeyValues> actionKeyValues = new HashMap<>();
        for (DeltaFile deltaFile : deltaFiles.getDeltaFiles()) {
            for (DeltaFileFlow flow : deltaFile.getFlows()) {
                for (Action action : flow.getActions()) {
                    if (action.getType() == ActionType.UNKNOWN || action.getState() != ActionState.ERROR) {
                        // ignore synthetic actions like NoEgressFlowConfigured
                        continue;
                    }
                    if (!actionKeyValues.containsKey(Pair.of(flow.getName(), action.getName()))) {
                        actionKeyValues.put(Pair.of(flow.getName(), action.getName()), new PerActionUniqueKeyValues(flow.getName(), action.getName()));
                    }
                    flow.getMetadata()
                            .forEach((key, value) -> actionKeyValues.get(Pair.of(flow.getName(), action.getName())).addValue(key, value));
                }
            }
        }
        return new ArrayList<>(actionKeyValues.values());
    }

    public List<UniqueKeyValues> sourceMetadataUnion(List<String> dids) {
        DeltaFilesFilter filter = new DeltaFilesFilter();
        filter.setDids(dids);
        DeltaFiles deltaFiles = deltaFiles(0, dids.size(), filter, null, List.of(FLOWS_INPUT_METADATA));

        Map<String, UniqueKeyValues> keyValues = new HashMap<>();
        deltaFiles.getDeltaFiles().forEach(deltaFile -> deltaFile.getFlows().getFirst().getInput().getMetadata().forEach((key, value) -> {
            if (!keyValues.containsKey(key)) {
                keyValues.put(key, new UniqueKeyValues(key));
            }
            keyValues.get(key).addValue(value);
        }));
        return new ArrayList<>(keyValues.values());
    }

private void advanceAndSave(List<StateMachineInput> inputs) {
        if (inputs.isEmpty()) {
            return;
        }

        List<ActionInput> actionInputs = stateMachine.advance(inputs);

        inputs.stream()
                .filter(input -> input.deltaFile().hasCollectingAction())
                .forEach(input -> deltaFileCacheService.remove(input.deltaFile().getDid()));

        deltaFileCacheService.saveAll(inputs.stream().map(StateMachineInput::deltaFile).distinct().toList());
        enqueueActions(actionInputs);
    }

    private void handleMissingFlow(DeltaFile deltaFile, DeltaFileFlow flow, String errorContext) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        Action action = flow.queueNewAction(MISSING_FLOW_ACTION, ActionType.UNKNOWN, false, now);
        processErrorEvent(deltaFile, flow, action, buildMissingFlowErrorEvent(deltaFile, now, errorContext));
        deltaFile.setStage(DeltaFileStage.ERROR);
    }

    public void deleteContentAndMetadata(String did, Content content) {
        try {
            deltaFileRepo.deleteById(did);
        } catch (Exception e) {
            log.error("Failed to remove the metadata for did {}", did, e);
        }

        try {
            contentStorageService.delete(content);
        } catch (Exception e) {
            log.error("Failed to remove the content for did {}", did, e);
        }
    }

    /**
     * Deletes DeltaFiles that meet the specified criteria.
     *
     * @param  createdBefore   the date and time before which the DeltaFiles were created
     * @param  completedBefore the date and time before which the DeltaFiles were completed
     * @param  minBytes        the minimum number of bytes for a DeltaFile to be deleted
     * @param  flow            the flow of the DeltaFiles to be deleted
     * @param  policy          the policy of the DeltaFiles to be deleted
     * @param  deleteMetadata  whether to delete the metadata of the DeltaFiles in addition to the content
     * @return                 true if there are more DeltaFiles to delete, false otherwise
     */
    public boolean timedDelete(OffsetDateTime createdBefore, OffsetDateTime completedBefore, Long minBytes, String flow, String policy, boolean deleteMetadata) {
        int batchSize = deltaFiPropertiesService.getDeltaFiProperties().getDelete().getPolicyBatchSize();

        logBatch(batchSize, policy);
        List<DeltaFile> deltaFiles = deltaFileRepo.findForTimedDelete(createdBefore, completedBefore, minBytes, flow, deleteMetadata, batchSize);
        delete(deltaFiles, policy, deleteMetadata);

        return deltaFiles.size() == batchSize;
    }

    public List<DeltaFile> diskSpaceDelete(long bytesToDelete, String flow, String policy) {
        int batchSize = deltaFiPropertiesService.getDeltaFiProperties().getDelete().getPolicyBatchSize();

        logBatch(batchSize, policy);
        return delete(deltaFileRepo.findForDiskSpaceDelete(bytesToDelete, flow, batchSize), policy, false);
    }

    public void logBatch(int batchSize, String policy) {
        log.info("Searching for batch of up to {} deltaFiles to delete for policy {}", batchSize, policy);
    }

    public List<DeltaFile> delete(List<DeltaFile> deltaFiles, String policy, boolean deleteMetadata) {
        if (deltaFiles.isEmpty()) {
            log.info("No deltaFiles found to delete for policy {}", policy);
            return deltaFiles;
        }

        log.info("Deleting {} deltaFiles for policy {}", deltaFiles.size(), policy);
        long totalBytes = deltaFiles.stream().filter(d -> d.getContentDeleted() == null).mapToLong(DeltaFile::getTotalBytes).sum();

        deleteContent(deltaFiles, policy, deleteMetadata);
        metricService.increment(new Metric(DELETED_FILES, deltaFiles.size()).addTag("policy", policy));
        metricService.increment(new Metric(DELETED_BYTES, totalBytes).addTag("policy", policy));
        log.info("Finished deleting {} deltaFiles for policy {}", deltaFiles.size(), policy);

        return deltaFiles;
    }

    public void requeue() {
        OffsetDateTime modified = OffsetDateTime.now(clock);
        Set<String> longRunningDids = actionEventQueue.getLongRunningTasks().stream().map(ActionExecution::did).collect(Collectors.toSet());
        List<DeltaFile> requeuedDeltaFiles = deltaFileRepo.updateForRequeue(modified,
                getProperties().getRequeueDuration(),queueManagementService.coldQueueActions(), longRunningDids);
        List<ActionInput> actionInputs = requeuedDeltaFiles.stream()
                .map(deltaFile -> requeuedActionInputs(deltaFile, modified))
                .flatMap(Collection::stream)
                .toList();
        if (!actionInputs.isEmpty()) {
            log.warn(actionInputs.size() + " actions exceeded requeue threshold of " + getProperties().getRequeueDuration() +
                    " seconds, requeuing now");
            enqueueActions(actionInputs, true);
        }
    }

    List<ActionInput> requeuedActionInputs(DeltaFile deltaFile, OffsetDateTime modified) {
        return deltaFile.getFlows().stream()
                .flatMap(flow -> flow.getActions().stream()
                        .filter(action -> action.getState().equals(ActionState.QUEUED) && action.getModified().toInstant().toEpochMilli() == modified.toInstant().toEpochMilli())
                        .map(action -> requeueActionInput(deltaFile, flow, action)))
                .filter(Objects::nonNull)
                .toList();
    }

    public void requeueColdQueueActions(List<String> actionNames, int maxFiles) {
        OffsetDateTime modified = OffsetDateTime.now(clock);
        List<DeltaFile> requeuedDeltaFiles = deltaFileRepo.updateColdQueuedForRequeue(actionNames, maxFiles, modified);
        List<ActionInput> actionInputs = requeuedDeltaFiles.stream()
                .map(deltaFile -> requeuedActionInputs(deltaFile, modified))
                .flatMap(Collection::stream)
                .toList();
        if (!actionInputs.isEmpty()) {
            log.warn("Moving {} from the cold to warm queue", actionInputs.size());
            enqueueActions(actionInputs, true);
        }
    }

    private ActionInput requeueActionInput(DeltaFile deltaFile, DeltaFileFlow flow, Action action) {
        ActionConfiguration actionConfiguration = actionConfiguration(flow.getName(), action.getName());

        if (Objects.isNull(actionConfiguration)) {
            String errorMessage = "Action named " + action.getName() + " is no longer running";
            log.error(errorMessage);
            ActionEvent event = ActionEvent.builder()
                    .did(deltaFile.getDid())
                    .flowName(flow.getName())
                    .flowId(flow.getId())
                    .actionName(action.getName())
                    .actionId(action.getId())
                    .error(ErrorEvent.builder().cause(errorMessage).build())
                    .type(ActionEventType.UNKNOWN)
                    .build();
            error(deltaFile, flow, action, event);

            return null;
        }

        if (deltaFile.isAggregate()) {
            return null;
            // TODO: turn this into an ActionInput
            /*return CollectingActionInput.builder()
                    .actionConfiguration(actionConfiguration)
                    .flow(flow.getName())
                    .deltaFile(deltaFile)
                    .egressFlow(flow.getType() == FlowType.EGRESS ? flow.getName() : null)
                    .actionCreated(action.getCreated())
                    .action(action)
                    .collectedDids(deltaFile.getParentDids())
                    .stage(deltaFile.getStage())
                    .build();*/
        }

        return actionConfiguration.buildActionInput(deltaFile, flow, action, getProperties().getSystemName(), null, null);
    }

    public void autoResume() {
        autoResume(OffsetDateTime.now(clock));
    }

    public int autoResume(OffsetDateTime timestamp) {
        int queued = 0;
        List<DeltaFile> autoResumeDeltaFiles = deltaFileRepo.findReadyForAutoResume(timestamp);
        if (!autoResumeDeltaFiles.isEmpty()) {
            Map<String, String> flowByDid = autoResumeDeltaFiles.stream()
                    .collect(Collectors.toMap(DeltaFile::getDid, DeltaFile::getDataSource));
            List<RetryResult> results = resume(flowByDid.keySet().stream().toList(), Collections.emptyList());
            Map<String, Integer> countByFlow = new HashMap<>();
            for (RetryResult result : results) {
                if (result.getSuccess()) {
                    ++queued;
                    String flow = flowByDid.get(result.getDid());
                    Integer count = 1;
                    if (countByFlow.containsKey(flow)) {
                        count += countByFlow.get(flow);
                    }
                    countByFlow.put(flow, count);
                } else {
                    log.error("Auto-resume: {}", result.getError());
                }
            }
            if (queued > 0) {
                log.info("Queued {} DeltaFiles for auto-resume", queued);
                generateMetricsByName(FILES_AUTO_RESUMED, countByFlow);
            }
        }
        return queued;
    }


    @SuppressWarnings("SameParameterValue")
    private void generateMetricsByName(String name, Map<String, Integer> countByFlow) {
        Set<String> flows = countByFlow.keySet();
        for (String flow : flows) {
            Integer count = countByFlow.get(flow);
            Map<String, String> tags = new HashMap<>();
            tags.put(DeltaFiConstants.DATA_SOURCE, flow);
            Metric metric = new Metric(name, count, tags);
            metricService.increment(metric);
        }
    }

    private ActionConfiguration actionConfiguration(String flow, String actionName) {
        ActionConfiguration actionConfiguration = transformFlowService.findActionConfig(flow, actionName);
        if (actionConfiguration == null) {
            actionConfiguration = egressFlowService.findActionConfig(flow, actionName);
        }
        return actionConfiguration;
    }

    public boolean processActionEvents(String uniqueId) {
        try {
            while (!Thread.currentThread().isInterrupted() && processIncomingEvents) {
                ActionEvent event = actionEventQueue.takeResult(uniqueId);
                validateActionEventHeader(event);
                processResult(event);
            }
        } catch (Throwable e) {
            log.error("Error receiving event: {}", e.getMessage());
            return false;
        }
        return true;
    }

    public void validateActionEventHeader(ActionEvent event) throws JsonProcessingException {
        String validationError = event.validateHeader();
        if (validationError != null) {
            log.error("Received invalid action event: {}: ({})", validationError, event);
            throw new InvalidActionEventException(validationError);
        }
    }

    public void processResult(ActionEvent event) {
        if (event == null) throw new RuntimeException("ActionEventQueue returned null event. This should NEVER happen");

        try {
            semaphore.acquire();
            executor.submit(() -> {
                try {
                    int count = 0;
                    while (true) {
                        try {
                            count += 1;
                            handleActionEvent(event);
                            break;
                        } catch (OptimisticLockingFailureException e) {
                            if (count > 9) {
                                throw e;
                            } else {
                                log.warn("Retrying after OptimisticLockingFailureException caught processing {} for {}", event.getActionName(), event.getDid());
                            }
                        } catch (Throwable e) {
                            StringWriter stackWriter = new StringWriter();
                            e.printStackTrace(new PrintWriter(stackWriter));
                            log.error("Exception processing incoming action event: \n{}\n{}", e.getMessage(), stackWriter);
                            break;
                        }
                    }
                } finally {
                    semaphore.release();
                }
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Preserve interrupt status
            log.error("Thread interrupted while waiting for a permit to process action event: {}", e.getMessage());
        }
    }

    private void enqueueActions(List<ActionInput> actionInputs) throws EnqueueActionException {
        enqueueActions(actionInputs, false);
    }

    private void enqueueActions(List<ActionInput> actionInputs, boolean checkUnique) throws EnqueueActionException {
        if (actionInputs.isEmpty()) {
            return;
        }

        try {
            actionEventQueue.putActions(actionInputs, checkUnique);
        } catch (Exception e) {
            log.error("Failed to queue action(s)", e);
            throw new EnqueueActionException("Failed to queue action(s)", e);
        }
    }

    public boolean taskTimedDataSource(String flowName, String memo, boolean overrideMemo) throws EnqueueActionException {
        TimedDataSource dataSource;
        if (overrideMemo) {
            // use the stored value so the cached memo value is not overwritten
            dataSource = dataSourceService.getTimedDataSource(flowName);
            if (!dataSource.isRunning()) {
                throw new IllegalStateException("Timed ingress flow '" + flowName + "' cannot be tasked while in a state of " + dataSource.getFlowStatus().getState());
            }
            dataSource.setMemo(memo);
        } else {
            dataSource = dataSourceService.getRunningTimedDataSource(flowName);
        }

        return taskTimedDataSource(dataSource);
    }

    public boolean taskTimedDataSource(TimedDataSource dataSource) throws EnqueueActionException {
        ActionInput actionInput = dataSource.buildActionInput(getProperties().getSystemName(), OffsetDateTime.now(clock));
        try {
            if (!actionEventQueue.queueHasTaskingForAction(actionInput)) {
                dataSourceService.setLastRun(dataSource.getName(), OffsetDateTime.now(clock),
                        actionInput.getActionContext().getDid());
                actionEventQueue.putActions(List.of(actionInput), false);
                return true;
            } else {
                log.warn("Skipping queueing on {} for duplicate timed ingress action event: {}",
                        actionInput.getQueueName(), actionInput.getActionContext().getActionName());
                return false;
            }
        } catch (Exception e) {
            log.error("Failed to queue action(s)", e);
            throw new EnqueueActionException("Failed to queue action(s)", e);
        }
    }

    private void deleteContent(List<DeltaFile> deltaFiles, String policy, boolean deleteMetadata) {
        List<DeltaFile> deltaFilesWithContent = deltaFiles.stream().filter(d -> d.getContentDeleted() == null).toList();
        contentStorageService.deleteAll(deltaFilesWithContent.stream()
                .map(DeltaFile::storedSegments)
                .flatMap(Collection::stream)
                .toList());

        if (deleteMetadata) {
            deleteMetadata(deltaFiles);
        } else {
            deltaFileRepo.setContentDeletedByDidIn(
                    deltaFilesWithContent.stream().map(DeltaFile::getDid).distinct().toList(),
                    OffsetDateTime.now(clock),
                    policy);
        }

        coreAuditLogger.logDelete(policy, deltaFiles.stream().map(DeltaFile::getDid).toList(), deleteMetadata);
    }

    private void deleteMetadata(List<DeltaFile> deltaFiles) {
        deltaFileRepo.batchedBulkDeleteByDidIn(deltaFiles.stream().map(DeltaFile::getDid).distinct().toList());

    }

    public SummaryByFlow getErrorSummaryByFlow(Integer offset, Integer limit, ErrorSummaryFilter filter, DeltaFileOrder orderBy) {
        return deltaFileRepo.getErrorSummaryByFlow(offset,
                (Objects.nonNull(limit) && limit > 0) ? limit : DEFAULT_QUERY_LIMIT,
                filter, orderBy);
    }

    public SummaryByFlowAndMessage getErrorSummaryByMessage(Integer offset, Integer limit, ErrorSummaryFilter filter, DeltaFileOrder orderBy) {
        return deltaFileRepo.getErrorSummaryByMessage(offset,
                (Objects.nonNull(limit) && limit > 0) ? limit : DEFAULT_QUERY_LIMIT,
                filter, orderBy);
    }

    public SummaryByFlow getFilteredSummaryByFlow(Integer offset, Integer limit, FilteredSummaryFilter filter, DeltaFileOrder orderBy) {
        return deltaFileRepo.getFilteredSummaryByFlow(offset,
                (Objects.nonNull(limit) && limit > 0) ? limit : DEFAULT_QUERY_LIMIT,
                filter, orderBy);
    }

    public SummaryByFlowAndMessage getFilteredSummaryByMessage(Integer offset, Integer limit, FilteredSummaryFilter filter, DeltaFileOrder orderBy) {
        return deltaFileRepo.getFilteredSummaryByMessage(offset,
                (Objects.nonNull(limit) && limit > 0) ? limit : DEFAULT_QUERY_LIMIT,
                filter, orderBy);
    }

    public List<String> annotationKeys() {
        return deltaFileRepo.annotationKeys();
    }

    private DeltaFiProperties getProperties() {
        return deltaFiPropertiesService.getDeltaFiProperties();
    }

    public Long totalCount() {
        return deltaFileRepo.estimatedCount();
    }

    public DeltaFileStats deltaFileStats() {
        return deltaFileRepo.deltaFileStats();
    }

    public Result applyResumePolicies(List<String> policyNames) {
        List<String> information = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<ResumePolicy> foundPolicies = new ArrayList<>();
        boolean allHaveDataSource = true;

        if (policyNames == null || policyNames.isEmpty()) {
            errors.add("Must provide one or more policy names");
        }

        if (errors.isEmpty()) {
            resumePolicyService.refreshCache();
            for (String policyName : new LinkedHashSet<>(policyNames)) {
                Optional<ResumePolicy> policy = resumePolicyService.findByName(policyName);
                if (policy.isPresent()) {
                    foundPolicies.add(policy.get());
                    if (policy.get().getDataSource() == null) {
                        allHaveDataSource = false;
                    }
                } else {
                    errors.add("Policy name " + policyName + " not found");
                }
            }
        }

        if (errors.isEmpty()) {
            OffsetDateTime now = OffsetDateTime.now(clock);
            Set<String> previousDids = new HashSet<>();

            /*
             * If applying any resume policy that is not flow-specific,
             * then query for all DeltaFiles with an ERROR, and use that
             * result set for all policies without a flow name.
             */
            List<DeltaFile> allErrorDeltaFiles = new ArrayList<>();
            if (!allHaveDataSource) {
                allErrorDeltaFiles = deltaFileRepo.findResumePolicyCandidates(null);
            }

            for (ResumePolicy resumePolicy : foundPolicies) {
                List<DeltaFile> checkFiles = allErrorDeltaFiles;
                if (resumePolicy.getDataSource() != null) {
                    checkFiles = deltaFileRepo.findResumePolicyCandidates(resumePolicy.getDataSource());
                }

                List<String> dids = resumePolicyService.canBeApplied(resumePolicy, checkFiles, previousDids);
                if (dids.isEmpty()) {
                    information.add("No DeltaFile errors can be resumed by policy " + resumePolicy.getName());
                } else {
                    deltaFileRepo.updateForAutoResume(dids, resumePolicy.getName(),
                            now.plusSeconds(resumePolicy.getBackOff().getDelay()));
                    previousDids.addAll(dids);
                    information.add("Applied " + resumePolicy.getName() + " policy to " + dids.size() + " DeltaFiles");
                }
            }
        }

        return Result.builder().success(errors.isEmpty())
                .errors(errors)
                .info(information)
                .build();
    }

    public Result resumePolicyDryRun(ResumePolicy resumePolicy) {
        List<String> information = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        if (resumePolicy == null) {
            errors.add("Resume policy must not be null");
        } else {
            if (StringUtils.isBlank(resumePolicy.getId())) {
                resumePolicy.setId(uuidGenerator.generate());
            }
            errors.addAll(resumePolicy.validate());
        }

        if (errors.isEmpty()) {
            List<DeltaFile> checkFiles =
                    deltaFileRepo.findResumePolicyCandidates(resumePolicy.getDataSource());

            List<String> dids = resumePolicyService.canBeApplied(resumePolicy, checkFiles, Collections.emptySet());
            if (dids.isEmpty()) {
                information.add("No DeltaFile errors can be resumed by policy " + resumePolicy.getName());
            } else {
                information.add("Can apply " + resumePolicy.getName() + " policy to " + dids.size() + " DeltaFiles");
            }
        }

        return Result.builder().success(errors.isEmpty())
                .errors(errors)
                .info(information)
                .build();
    }

    /*private ActionInput buildCollectingActionInput(ActionInput actionInput, String systemName) {
        try {
            List<DeltaFile> collectedDeltaFiles = findDeltaFiles(actionInput.getCollectedDids());

            DeltaFile aggregate = actionInput.getDeltaFile();

            if (aggregate == null) {
                OffsetDateTime now = OffsetDateTime.now(clock);

                aggregate = DeltaFile.builder()
                        .schemaVersion(DeltaFile.CURRENT_SCHEMA_VERSION)
                        .did(uuidGenerator.generate())
                        .parentDids(actionInput.getCollectedDids())
                        .name(AGGREGATE_SOURCE_FILE_NAME)
                        .aggregate(true)
                        .childDids(Collections.emptyList())
                        .dataSource(collectedDeltaFiles.get(0).getDataSource())
                        .created(now)
                        .modified(now)
                        .egressed(false)
                        .filtered(false)
                        .actions(List.of(actionInput.getAction()))
                        .build();

                deltaFileRepo.save(aggregate);
            }

            return actionInput.getActionConfiguration().buildCollectingActionInput(actionInput.getFlow(),
                    aggregate, collectedDeltaFiles, systemName, actionInput.getEgressFlow(),
                    actionInput.getActionCreated(), actionInput.getAction());
        } catch (MissingDeltaFilesException e) {
            throw new EnqueueActionException("Failed to queue collecting action", e);
        }
    }*/

    private List<DeltaFile> findDeltaFiles(List<String> dids) throws MissingDeltaFilesException {
        Map<String, DeltaFile> deltaFileMap = deltaFileRepo.findAllById(dids).stream()
                .collect(Collectors.toMap(DeltaFile::getDid, Function.identity()));

        if (deltaFileMap.size() < dids.size()) {
            List<String> missingDids = dids.stream().filter(did -> !deltaFileMap.containsKey(did)).toList();
            if (!missingDids.isEmpty()) {
                throw new MissingDeltaFilesException(missingDids);
            }
        }

        return dids.stream().map(deltaFileMap::get).toList();
    }

    public static final String AGGREGATE_SOURCE_FILE_NAME = "multiple";

    public void queueTimedOutCollect(CollectEntry collectEntry, List<String> collectedDids) {
        ActionConfiguration actionConfiguration = actionConfiguration(collectEntry.getCollectDefinition().getFlow(),
                collectEntry.getCollectDefinition().getAction());

        if (actionConfiguration == null) {
            log.warn("Time-based collect action couldn't run because action {} in flow {} is no longer running",
                    collectEntry.getCollectDefinition().getAction(), collectEntry.getCollectDefinition().getFlow());
            return;
        }

        OffsetDateTime now = OffsetDateTime.now(clock);

        Action action = Action.builder()
                .name(collectEntry.getCollectDefinition().getAction())
                .type(collectEntry.getCollectDefinition().getActionType())
                .state(ActionState.QUEUED)
                .created(now)
                .queued(now)
                .modified(now)
                .build();

        // TODO: this
        /*CollectingActionInput collectingActionInput = CollectingActionInput.builder()
                .actionConfiguration(actionConfiguration)
                .flow(collectEntry.getCollectDefinition().getFlow())
                .egressFlow(action.getType() == EGRESS ? action.getFlow() : null)
                .actionCreated(now)
                .action(action)
                .collectedDids(collectedDids)
                .stage(collectEntry.getCollectDefinition().getStage())
                .build();

        enqueueActions(List.of(collectingActionInput));*/
    }

    public void failTimedOutCollect(CollectEntry collectEntry, List<String> collectedDids, String reason) {
        log.debug("Failing collect action");

        List<String> missingDids = new ArrayList<>();

        for (String did : collectedDids) {
            try {
                DeltaFile deltaFile = deltaFileRepo.findById(did.toLowerCase()).orElse(null);
                if (deltaFile == null) {
                    missingDids.add(did);
                    continue;
                }
                OffsetDateTime now = OffsetDateTime.now(clock);
                // TODO: this
                /*deltaFile.errorAction(collectEntry.getCollectDefinition().getFlow(),
                        collectEntry.getCollectDefinition().getAction(), now, now, "Failed collect", reason);*/
                deltaFile.setStage(DeltaFileStage.ERROR);
                deltaFileRepo.save(deltaFile);
            } catch (OptimisticLockingFailureException e) {
                log.warn("Unable to save DeltaFile with failed collect action", e);
            }
        }

        if (!missingDids.isEmpty()) {
            log.warn("DeltaFiles with the following ids were missing during failed collect: {}", missingDids);
        }
    }

    private void completeCollect(ActionEvent event, DeltaFile deltaFile, DeltaFileFlow flow, Action action, OffsetDateTime now) {
        ActionConfiguration actionConfiguration = actionConfiguration(flow.getName(), action.getName());
        if ((actionConfiguration != null) && (actionConfiguration.getCollect() != null)) {
            List<ActionInput> actionInputs = new ArrayList<>();
            List<DeltaFile> parentDeltaFiles = deltaFileRepo.findAllById(deltaFile.getParentDids());
            // TODO: this can have conflicts/collisions if the same flow is running multiple times
            // we need ids mixed in.  it's going to be messy.
            for (DeltaFile parentDeltaFile : parentDeltaFiles) {
                parentDeltaFile.getChildDids().add(deltaFile.getDid());
                parentDeltaFile.collectedAction(flow.getName(), action.getName(), event.getStart(), event.getStop(), now);
                // TODO: statemachineinput
                //actionInputs.addAll(advanceOnly(parentDeltaFile));
            }
            deltaFileRepo.saveAll(parentDeltaFiles);
            enqueueActions(actionInputs);
        }
    }

    private static class Counter {
        int filteredFiles = 0;
        int erroredFiles = 0;
        long byteCount = 0L;
    }
}
