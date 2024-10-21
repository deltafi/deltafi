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
package org.deltafi.core.services;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.collect.Lists;
import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.deltafi.common.content.Segment;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.content.ContentUtil;
import org.deltafi.common.converters.KeyValueConverter;
import org.deltafi.common.types.*;
import org.deltafi.common.uuid.UUIDGenerator;
import org.deltafi.core.configuration.DeltaFiProperties;
import org.deltafi.core.exceptions.*;
import org.deltafi.core.generated.types.*;
import org.deltafi.core.metrics.MetricService;
import org.deltafi.core.metrics.MetricsUtil;
import org.deltafi.core.repo.*;
import org.deltafi.core.services.analytics.AnalyticEventService;
import org.deltafi.core.types.*;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.env.Environment;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
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
import static org.deltafi.common.types.ActionState.COLD_QUEUED;
import static org.deltafi.common.types.ActionState.QUEUED;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeltaFilesService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    public static final String REPLAY_ACTION_NAME = "Replay";
    public static final int REQUEUE_BATCH_SIZE = 5000;

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

    public static final String MISSING_FLOW_CAUSE = "The dataSource is no longer installed or running";

    private static final int DEFAULT_QUERY_LIMIT = 50;

    private final Clock clock;
    private final TransformFlowService transformFlowService;
    private final EgressFlowService egressFlowService;
    private final DeltaFiPropertiesService deltaFiPropertiesService;
    private final StateMachine stateMachine;
    private final AnnotationRepo annotationRepo;
    private final DeltaFileRepo deltaFileRepo;
    private final DeltaFileFlowRepo deltaFileFlowRepo;
    private final CoreEventQueue coreEventQueue;
    private final ContentStorageService contentStorageService;
    private final ResumePolicyService resumePolicyService;
    private final MetricService metricService;
    private final AnalyticEventService analyticEventService;
    private final DidMutexService didMutexService;
    private final DeltaFileCacheService deltaFileCacheService;
    private final TimedDataSourceService timedDataSourceService;
    private final QueueManagementService queueManagementService;
    private final QueuedAnnotationRepo queuedAnnotationRepo;
    private final Environment environment;
    private final UUIDGenerator uuidGenerator;
    private final IdentityService identityService;

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
    }

    @PreDestroy
    public void onShutdown() throws InterruptedException {
        log.info("Shutting down DeltaFilesService");

        // stop processing new events
        processIncomingEvents = false;

        log.info("Waiting for executor threads to finish");

        // give a grace period for events to be assigned to executor threads
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

    public DeltaFile getDeltaFile(UUID did) {
        return deltaFileRepo.findById(did).orElse(null);
    }

    public DeltaFile getCachedDeltaFile(UUID did) {
        return deltaFileCacheService.get(did);
    }

    public String getRawDeltaFile(UUID did, boolean pretty) throws JsonProcessingException {
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
    public Set<String> getPendingAnnotations(UUID did) {
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

    public Map<UUID, DeltaFile> deltaFiles(List<UUID> dids) {
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
        return deltaFileFlowRepo.countUnacknowledgedErrors();
    }

    public DeltaFile ingress(RestDataSource restDataSource, IngressEventItem ingressEventItem, OffsetDateTime ingressStartTime,
            OffsetDateTime ingressStopTime) {
        return ingress(restDataSource, ingressEventItem, Collections.emptyList(), ingressStartTime, ingressStopTime);
    }

    private DeltaFile buildIngressDeltaFile(DataSource dataSource, IngressEventItem ingressEventItem, List<UUID> parentDids,
                                            OffsetDateTime ingressStartTime, OffsetDateTime ingressStopTime,
                                            String ingressActionName, FlowType flowType) {

        OffsetDateTime now = OffsetDateTime.now(clock);

        Action ingressAction = Action.builder()
                .name(ingressActionName)
                .number(0)
                .replayStart(true)
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
                .number(0)
                .type(flowType)
                .state(DeltaFileFlowState.COMPLETE)
                .testMode(dataSource.isTestMode())
                .created(ingressStartTime)
                .modified(now)
                .actions(new ArrayList<>(List.of(ingressAction)))
                .publishTopics(new ArrayList<>())
                .depth(0)
                .build();

        long contentSize = ContentUtil.computeContentSize(ingressEventItem.getContent());

        return DeltaFile.builder()
                .did(ingressEventItem.getDid())
                .dataSource(dataSource.getName())
                .name(ingressEventItem.getDeltaFileName())
                .parentDids(parentDids)
                .childDids(new ArrayList<>())
                .requeueCount(0)
                .ingressBytes(contentSize)
                .totalBytes(contentSize)
                .stage(DeltaFileStage.IN_FLIGHT)
                .flows(new LinkedHashSet<>(List.of(ingressFlow)))
                .created(ingressStartTime)
                .modified(now)
                .egressed(false)
                .filtered(false)
                .build();
    }

    private DeltaFile ingress(RestDataSource restDataSource, IngressEventItem ingressEventItem, List<UUID> parentDids, OffsetDateTime ingressStartTime,
            OffsetDateTime ingressStopTime) {
        DeltaFile deltaFile = buildIngressDeltaFile(restDataSource, ingressEventItem, parentDids, ingressStartTime, ingressStopTime,
                INGRESS_ACTION, FlowType.REST_DATA_SOURCE);

        advanceAndSave(List.of(new StateMachineInput(deltaFile, deltaFile.firstFlow())), false);
        return deltaFile;
    }

    public DeltaFile buildIngressDeltaFile(DataSource dataSource, ActionEvent parentEvent, IngressEventItem ingressEventItem) {
        ingressEventItem.setFlowName(parentEvent.getFlowName());
        return buildIngressDeltaFile(dataSource, ingressEventItem, Collections.emptyList(), parentEvent.getStart(),
                parentEvent.getStop(), parentEvent.getActionName(), FlowType.TIMED_DATA_SOURCE);
    }

    public void handleActionEvent(ActionEvent event) {
        if (event.getType() == ActionEventType.INGRESS) {
            handleIngressActionEvent(event);
        } else {
            handleProcessingActionEvent(event);
        }
    }

    public void handleProcessingActionEvent(ActionEvent event) {
        didMutexService.executeWithLock(event.getDid(), () -> {
            DeltaFile deltaFile = getCachedDeltaFile(event.getDid());

            if (deltaFile == null) {
                throw new InvalidActionEventException("DeltaFile " + event.getDid() + " not found.");
            }

            if (deltaFile.getStage() == DeltaFileStage.CANCELLED) {
                log.warn("Received event for cancelled did {}", deltaFile.getDid());
                return;
            }

            DeltaFileFlow flow = deltaFile.getPendingFlow(event.getFlowName(), event.getFlowId());
            Action action = flow.getPendingAction(event.getActionName(), event.getDid());
            ActionConfiguration actionConfiguration = actionConfiguration(flow.getName(), flow.getType(), action.getName());

            if (event.getType() != ActionEventType.ERROR) {
                flow.removePendingAction(action.getName());
            }

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
                    transform(deltaFile, flow, action, event);
                    generateMetrics(metrics, event, deltaFile, flow, action, actionConfiguration);
                }
                case EGRESS -> {
                    egress(deltaFile, flow, action, event.getStart(), event.getStop());
                    metrics.add(
                            Metric.builder()
                                    .name(EXECUTION_TIME_MS)
                                    .value(Duration.between(deltaFile.getCreated(), deltaFile.getModified()).toMillis())
                                    .build());
                    generateMetrics(metrics, event, deltaFile, flow, action, actionConfiguration);
                    if (flow.getState() == DeltaFileFlowState.COMPLETE) {
                        analyticEventService.recordEgress(deltaFile, flow);
                    }
                }
                case ERROR -> {
                    error(deltaFile, flow, action, event);
                    generateMetrics(metrics, event, deltaFile, flow, action, actionConfiguration);
                }
                case FILTER -> {
                    filter(deltaFile, flow, action, event, OffsetDateTime.now());
                    metrics.add(new Metric(DeltaFiConstants.FILES_FILTERED, 1));
                    generateMetrics(metrics, event, deltaFile, flow, action, actionConfiguration);
                }
                default -> throw new UnknownTypeException(event.getActionName(), deltaFile.getDid(), event.getType());
            }

            completeJoin(event, deltaFile, action, actionConfiguration);
        });
    }

    private void generateMetrics(List<Metric> metrics, ActionEvent event, DeltaFile deltaFile, DeltaFileFlow flow,
                                 Action action, ActionConfiguration actionConfiguration) {
        generateMetrics(true, metrics, event, deltaFile, flow, action, actionConfiguration);
    }

    private void generateMetrics(boolean actionExecuted, List<Metric> metrics, ActionEvent event, DeltaFile deltaFile,
                                 DeltaFileFlow flow, Action action, ActionConfiguration actionConfiguration) {
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
        TimedDataSource dataSource = timedDataSourceService.getRunningFlowByName(event.getFlowName());
        IngressEvent ingressEvent = event.getIngress();
        boolean completedExecution = timedDataSourceService.completeExecution(dataSource.getName(),
                event.getDid(), ingressEvent.getMemo(), ingressEvent.isExecuteImmediate(),
                ingressEvent.getStatus(), ingressEvent.getStatusMessage(), dataSource.getCronSchedule());
        if (!completedExecution) {
            log.warn("Received unexpected ingress event for dataSource {} with did {}", event.getFlowName(), event.getDid());
            return;
        }

        final Counter counter = new Counter();
        List<StateMachineInput> stateMachineInputs = ingressEvent.getIngressItems().stream()
                .map((item) -> buildIngressDeltaFile(dataSource, event, item))
                .map((deltaFile) -> new StateMachineInput(deltaFile, deltaFile.firstFlow()))
                .toList();

        advanceAndSave(stateMachineInputs, false);

        for (StateMachineInput stateMachineInput : stateMachineInputs) {
            DeltaFile deltaFile = stateMachineInput.deltaFile();
            counter.byteCount += deltaFile.getIngressBytes();
            if (deltaFile.getFlows().size() == 1) {
                ActionState lastState = deltaFile.firstFlow().lastActionState();
                if (lastState == ActionState.FILTERED) {
                    counter.filteredFiles++;
                } else if (lastState == ActionState.ERROR) {
                    logErrorAnalytics(deltaFile, event, "Ingress error. Data source: " + dataSource.getName());
                    counter.erroredFiles++;
                }
            }
            analyticEventService.recordIngress(deltaFile.getDid(), deltaFile.getCreated(), deltaFile.getDataSource(),
                    deltaFile.getIngressBytes(), deltaFile.annotationMap());
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
            advanceAndSave(List.of(new StateMachineInput(deltaFile, flow)), false);
            if (!transformEvent.getAnnotations().isEmpty()) {
                analyticEventService.recordAnnotations(deltaFile.getDid(), deltaFile.getCreated(),
                        deltaFile.getDataSource(), deltaFile.getIngressBytes(), deltaFile.annotationMap());
            }
        } else {
            action.changeState(ActionState.SPLIT, event.getStart(), event.getStop(), now);
            List<StateMachineInput> inputs = new ArrayList<>();
            inputs.add(new StateMachineInput(deltaFile, flow));
            List<StateMachineInput> childInputs =
                    createChildren(transformEvents, event.getStart(), event.getStop(), deltaFile, flow);
            inputs.addAll(childInputs);
            deltaFile.getChildDids().addAll(childInputs.stream().map(input -> input.deltaFile().getDid()).toList());
            advanceAndSave(inputs, false);
        }
    }

    public List<StateMachineInput> createChildren(List<TransformEvent> transformEvents, OffsetDateTime startTime,
            OffsetDateTime stopTime, DeltaFile deltaFile, DeltaFileFlow flow) {
        List<StateMachineInput> inputs = transformEvents.stream()
                .map(transformEvent -> createChildDeltaFile(transformEvent, deltaFile, flow, startTime, stopTime))
                .toList();

        flow.getPendingActions().clear(); // clear remaining pending actions out of the parent
        return inputs;
    }

    public void egress(DeltaFile deltaFile, DeltaFileFlow flow, Action action, OffsetDateTime start, OffsetDateTime stop) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        deltaFile.setModified(now);
        deltaFile.setEgressed(true);
        flow.setModified(now);
        flow.setState(DeltaFileFlowState.COMPLETE);
        action.setModified(now);
        action.setContent(flow.getInput().getContent());
        action.setMetadata(flow.getMetadata());
        action.setState(ActionState.COMPLETE);
        action.setStart(start);
        action.setStop(stop);

        advanceAndSave(List.of(new StateMachineInput(deltaFile, flow)), false);
    }

    public void filter(DeltaFile deltaFile, DeltaFileFlow flow, Action action, ActionEvent event, OffsetDateTime now) {
        deltaFile.addAnnotations(event.getFilter().getAnnotations());
        deltaFile.setModified(now);
        deltaFile.setFiltered(true);
        action.setFilteredActionState(event.getStart(), event.getStop(), now, event.getFilter().getMessage(),
                event.getFilter().getContext());

        advanceAndSave(List.of(new StateMachineInput(deltaFile, flow)), false);
        logFilterAnalytics(deltaFile, event);
    }

    private void error(DeltaFile deltaFile, DeltaFileFlow flow, Action action, ActionEvent event) {
        deltaFile.addAnnotations(event.getError().getAnnotations());

        // If the content was deleted by a delete policy mark as CANCELLED instead of ERROR
        if (deltaFile.getContentDeleted() != null) {
            final DeltaFileStage startingStage = deltaFile.getStage();
            OffsetDateTime now = OffsetDateTime.now(clock);
            deltaFile.cancel(now);
            final DeltaFileStage endingStage = deltaFile.getStage();
            if (!startingStage.equals(endingStage) && endingStage.equals(DeltaFileStage.CANCELLED)) {
                analyticEventService.recordCancel(deltaFile, now);
            }
            deltaFileCacheService.save(deltaFile);
        } else {
            processErrorEvent(deltaFile, flow, action, event);
            advanceAndSave(List.of(new StateMachineInput(deltaFile, flow)), false);
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
        flow.updateState();
        deltaFile.updateState(now);

        // false: we don't want action execution metrics, since they have already been recorded.
        generateMetrics(false, List.of(new Metric(DeltaFiConstants.FILES_ERRORED, 1)), event, deltaFile, flow, action, actionConfiguration(flow.getName(), flow.getType(), action.getName()));
        logErrorAnalytics(deltaFile, event);
    }

    private void logFilterAnalytics(DeltaFile deltaFile, ActionEvent event) {
        analyticEventService.recordFilter(deltaFile, event.getFlowName(), event.getActionName(), event.getFilter().getMessage(), event.getStop());
        if (!event.getFilter().getAnnotations().isEmpty()) {
            analyticEventService.recordAnnotations(deltaFile.getDid(), deltaFile.getCreated(),
                    deltaFile.getDataSource(), deltaFile.getIngressBytes(), deltaFile.annotationMap());
        }
    }

    private void logErrorAnalytics(DeltaFile deltaFile, ActionEvent event) {
        analyticEventService.recordError(deltaFile, event.getFlowName(), event.getActionName(), event.getError().getCause(), event.getStop());
        if (!event.getError().getAnnotations().isEmpty()) {
            analyticEventService.recordAnnotations(deltaFile.getDid(), deltaFile.getCreated(),
                    deltaFile.getDataSource(), deltaFile.getIngressBytes(), deltaFile.annotationMap());
        }
    }

    private void logErrorAnalytics(DeltaFile deltaFile, ActionEvent action, String message) {
        analyticEventService.recordError(deltaFile, action.getFlowName(), action.getActionName(), message, action.getStop());
    }

    private DeltaFile getTerminalDeltaFileOrCache(UUID did) {
        if (deltaFileCacheService.isCached(did)) {
            return getCachedDeltaFile(did);
        }
        return deltaFileRepo.findByDidAndStageIn(did,
                        Arrays.asList(DeltaFileStage.COMPLETE, DeltaFileStage.ERROR, DeltaFileStage.CANCELLED))
                .orElse(null);
    }

    public void addAnnotations(UUID did, Map<String, String> annotations, boolean allowOverwrites) {
        didMutexService.executeWithLock(did, () -> {
            DeltaFile deltaFile = getTerminalDeltaFileOrCache(did);

            if (deltaFile == null) {
                if (deltaFileRepo.existsById(did)) {
                    QueuedAnnotation queuedAnnotation = new QueuedAnnotation(did, annotations, allowOverwrites);
                    queuedAnnotationRepo.save(queuedAnnotation);
                    return;
                } else {
                    throw new DgsEntityNotFoundException("DeltaFile " + did + " not found.");
                }
            }

            addAnnotations(deltaFile, annotations, allowOverwrites, OffsetDateTime.now());
        });
    }

    public void processQueuedAnnotations() {
        List<QueuedAnnotation> queuedAnnotations = queuedAnnotationRepo.findAllByOrderByTimeAsc();
        for (QueuedAnnotation queuedAnnotation : queuedAnnotations) {
            UUID did = queuedAnnotation.getDid();
            didMutexService.executeWithLock(did, () -> {
                DeltaFile deltaFile = getTerminalDeltaFileOrCache(did);

                if (deltaFile == null && deltaFileRepo.existsById(did)) {
                    log.warn("Attempted to apply queued annotation to deltaFile {} that no longer exists", did);
                } else {
                    addAnnotations(deltaFile, queuedAnnotation.getAnnotations(), queuedAnnotation.isAllowOverwrites(), queuedAnnotation.getTime());
                    queuedAnnotationRepo.deleteById(queuedAnnotation.getId());
                }
            });
        }
    }

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

        analyticEventService.recordAnnotations(deltaFile.getDid(), deltaFile.getCreated(),
                deltaFile.getDataSource(), deltaFile.getIngressBytes(), deltaFile.annotationMap());
    }

    /**
     * Find the DeltaFiles that are pending annotations for the given dataSource and check if they satisfy
     * the new set of expectedAnnotations
     * @param flowName name of the dataSource
     * @param expectedAnnotations new set of expected annotations for the given dataSource
     */
    @Transactional
    public void updatePendingAnnotationsForFlows(String flowName, Set<String> expectedAnnotations) {
        int batchSize = deltaFiPropertiesService.getDeltaFiProperties().getDeletePolicyBatchSize();
        List<DeltaFile> updatedDeltaFiles = new ArrayList<>();
        try (Stream<DeltaFile> deltaFiles = deltaFileRepo.findByTerminalAndFlowsNameAndFlowsState(false, flowName, DeltaFileFlowState.PENDING_ANNOTATIONS)) {
            deltaFiles.forEach(deltaFile -> updatePendingAnnotationsForFlowsAndCollect(deltaFile, flowName, expectedAnnotations, updatedDeltaFiles, batchSize));
        }
    }

    /*
     * Update the pending annotations in the given DeltaFile based on the latest expectedAnnotations for the dataSource
     * If the collector list hits the batchSize limit, save the updated DeltaFiles and flush the list.
     */
    void updatePendingAnnotationsForFlowsAndCollect(DeltaFile deltaFile, String flowName, Set<String> expectedAnnotations, List<DeltaFile> collector, int batchSize) {
        deltaFile.setPendingAnnotations(flowName, expectedAnnotations);
        collector.add(deltaFile);

        if (collector.size() == batchSize) {
            deltaFileRepo.insertBatch(collector);
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
        OffsetDateTime now = OffsetDateTime.now(clock);

        List<Action> inheritedActions = fromFlow.getActions().stream()
                .map(Action::createChildAction)
                .collect(Collectors.toCollection(ArrayList::new));

        Action childAction = inheritedActions.getLast();
        childAction.complete(startTime, stopTime, transformEvent.getContent(), transformEvent.getMetadata(), transformEvent.getDeleteMetadataKeys(), now);
        childAction.setReplayStart(true);

        DeltaFileFlow childFlow = DeltaFileFlow.builder()
                .name(fromFlow.getName())
                .number(0)
                .type(fromFlow.getType())
                .state(DeltaFileFlowState.IN_FLIGHT)
                .created(startTime)
                .modified(now)
                .actions(inheritedActions)
                .publishTopics(new ArrayList<>())
                .depth(fromFlow.getDepth())
                .testMode(fromFlow.isTestMode())
                .testModeReason(fromFlow.getTestModeReason())
                .pendingActions(new ArrayList<>(fromFlow.getPendingActions()))
                .build();

        DeltaFile child = DeltaFile.builder()
                .version(0)
                .did(uuidGenerator.generate())
                .dataSource(deltaFile.getDataSource())
                .parentDids(List.of(deltaFile.getDid()))
                .childDids(new ArrayList<>())
                .requeueCount(0)
                .ingressBytes(deltaFile.getIngressBytes())
                .totalBytes(deltaFile.getTotalBytes())
                .stage(DeltaFileStage.IN_FLIGHT)
                .flows(new LinkedHashSet<>(List.of(childFlow)))
                .created(now)
                .modified(now)
                .egressed(false)
                .filtered(false)
                .build();

        child.setName(transformEvent.getName());
        return new StateMachineInput(child, childFlow);
    }

    public List<RetryResult> resume(@NotNull List<UUID> dids, @NotNull List<ResumeMetadata> resumeMetadata) {
        Map<UUID, DeltaFile> deltaFiles = deltaFiles(dids);
        return resumeDeltaFiles(dids.stream()
                        // Collectors.toMap does not allow null values, so build a Map manually
                        .collect(LinkedHashMap::new,
                                (map, did) -> map.put(did, deltaFiles.getOrDefault(did, null)),
                                HashMap::putAll),
                resumeMetadata);
    }

    public List<RetryResult> resumeDeltaFiles(@NotNull Map<UUID, DeltaFile> deltaFiles, @NotNull List<ResumeMetadata> resumeMetadata) {
        List<StateMachineInput> advanceAndSaveInputs = new ArrayList<>();

        List<RetryResult> retryResults = deltaFiles.keySet().stream()
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

        advanceAndSave(advanceAndSaveInputs, false);
        return retryResults;
    }

    public List<RetryResult> replay(@NotNull List<UUID> dids, @NotNull List<String> removeSourceMetadata, @NotNull List<KeyValue> replaceSourceMetadata)  {
        Map<UUID, DeltaFile> deltaFiles = deltaFiles(dids);
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
                            DeltaFileFlow firstFlow = deltaFile.firstFlow();

                            DeltaFileFlow flow = DeltaFileFlow.builder()
                                    .name(firstFlow.getName())
                                    .type(firstFlow.getType())
                                    .state(DeltaFileFlowState.COMPLETE)
                                    .input(firstFlow.getInput())
                                    .created(now)
                                    .modified(now)
                                    .build();

                            List<UUID> parentDids = new ArrayList<>(List.of(deltaFile.getDid()));
                            Action replayAction;
                            if (deltaFile.getJoinId() != null) {
                                setNextActionsInAggregateFlow(flow, firstFlow);
                                replayAction = flow.addAction(REPLAY_ACTION_NAME, ActionType.TRANSFORM, ActionState.COMPLETE, now);
                                parentDids.addAll(deltaFile.getParentDids());
                            } else {
                                Action startFromAction = findFirstActionUpdateFlow(flow, firstFlow, now);
                                flow.getActions().add(startFromAction);

                                List<Content> content = startFromAction.getContent();
                                replayAction = flow.addAction(REPLAY_ACTION_NAME, startFromAction.getType(), ActionState.COMPLETE, now);
                                replayAction.setContent(content);
                            }

                            if (!removeSourceMetadata.isEmpty()) {
                                replayAction.setDeleteMetadataKeys(removeSourceMetadata);
                            }
                            if (!replaceSourceMetadata.isEmpty()) {
                                replayAction.setMetadata(KeyValueConverter.convertKeyValues(replaceSourceMetadata));
                            }

                            DeltaFile child = DeltaFile.builder()
                                    .did(uuidGenerator.generate())
                                    .parentDids(parentDids)
                                    .childDids(new ArrayList<>())
                                    .requeueCount(0)
                                    .ingressBytes(deltaFile.getIngressBytes())
                                    .stage(DeltaFileStage.IN_FLIGHT)
                                    .terminal(false)
                                    .contentDeletable(false)
                                    .flows(new LinkedHashSet<>(List.of(flow)))
                                    .dataSource(deltaFile.getDataSource())
                                    .name(deltaFile.getName())
                                    .created(now)
                                    .modified(now)
                                    .egressed(false)
                                    .filtered(false)
                                    .joinId(deltaFile.getJoinId())
                                    .build();

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

        advanceAndSave(inputs, true);
        deltaFileRepo.saveAll(parents);

        return results;
    }

    private Action findFirstActionUpdateFlow(DeltaFileFlow flow, DeltaFileFlow firstFlow, OffsetDateTime now) {
        Action startFromAction = null;
        List<ActionConfiguration> nextActions = new ArrayList<>();

        if (firstFlow.getType() == FlowType.TRANSFORM) {
            TransformFlow flowConfig = transformFlowService.getFlowOrThrow(firstFlow.getName());
            List<String> inheritedActions = new ArrayList<>();
            List<String> expectedInheritedActions = new ArrayList<>();

            List<ActionConfiguration> childActionConfigurations = new ArrayList<>();
            for (ActionConfiguration action : flowConfig.getTransformActions()) {
                expectedInheritedActions.add(action.getName());
                childActionConfigurations.add(action);
            }

            for (Action originalAction : firstFlow.getActions()) {
                startFromAction = new Action(originalAction);
                final String checkName = startFromAction.getName();
                if (!inheritedActions.contains(checkName)) {
                    inheritedActions.add(checkName);
                }
                childActionConfigurations.removeIf(a -> a.getName().equals(checkName));
                if (originalAction.isReplayStart()) {
                    int indexOf = expectedInheritedActions.indexOf(checkName);
                    if (indexOf >= 0) {
                        expectedInheritedActions = expectedInheritedActions.subList(0, indexOf + 1);
                    } else {
                        throw new IllegalStateException("The dataSource " + flow.getName() + " no longer contains an action named " + startFromAction.getName() + " where the replay would be begin");
                    }
                    break;
                } else {
                    flow.getActions().add(new Action(originalAction));
                }
            }

            if (!expectedInheritedActions.equals(inheritedActions)) {
                throw new IllegalStateException("The actions inherited from the parent DeltaFile for dataSource " + flow.getName() + " do not match the latest dataSource");
            }

            nextActions.addAll(childActionConfigurations);
        } else {
            startFromAction = new Action(firstFlow.firstAction());
        }

        flow.setPendingActions(nextActions.stream().map(ActionConfiguration::getName).toList());
        flow.setState(nextActions.isEmpty() ? DeltaFileFlowState.COMPLETE : DeltaFileFlowState.IN_FLIGHT);

        if (startFromAction == null) {
            throw new IllegalStateException("No start action found to inherit for dataSource " + flow.getName() + " where replay should begin");
        }
        return Action.builder()
                .name(startFromAction.getName())
                .number(startFromAction.getNumber())
                .type(startFromAction.getType())
                .state(ActionState.COMPLETE)
                .created(now)
                .modified(now)
                .content(startFromAction.getContent())
                .metadata(startFromAction.getMetadata())
                .replayStart(true)
                .build();
    }

    private void setNextActionsInAggregateFlow(DeltaFileFlow aggregateFlow, DeltaFileFlow firstFlow) {
        List<ActionConfiguration> nextActions = new ArrayList<>();
        if (firstFlow.getType() == FlowType.TRANSFORM) {
            String joinActionName = firstFlow.getActions().stream()
                    .filter(action -> !REPLAY_ACTION_NAME.equals(action.getName()))
                    .findFirst().orElseThrow(() -> new IllegalStateException("Could not find the join action to replay"))
                    .getName();
            TransformFlow flowConfig = transformFlowService.getFlowOrThrow(firstFlow.getName());
            boolean addConfig = false;
            for (ActionConfiguration actionConfiguration : flowConfig.getTransformActions()) {
                if (actionConfiguration.getName().equals(joinActionName) && actionConfiguration.getJoin() != null) {
                    addConfig = true;
                }
                if (addConfig) {
                    nextActions.add(actionConfiguration);
                }
            }
            if (!addConfig) {
                throw new IllegalStateException("Flow " + aggregateFlow.getName() + " no longer has a join action named " + joinActionName);
            }
        } else if (firstFlow.getType() == FlowType.EGRESS) {
            EgressFlow flowConfig = egressFlowService.getFlowOrThrow(firstFlow.getName());
            nextActions.add(flowConfig.getEgressAction());
        }

        aggregateFlow.setPendingActions(nextActions.stream().map(ActionConfiguration::getName).toList());
    }

    public List<AcknowledgeResult> acknowledge(List<UUID> dids, String reason) {
        Map<UUID, DeltaFile> deltaFiles = deltaFiles(dids);

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

    public List<CancelResult> cancel(List<UUID> dids) {
        Map<UUID, DeltaFile> deltaFiles = deltaFiles(dids);
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
                            final DeltaFileStage startingStage = deltaFile.getStage();
                            OffsetDateTime now = OffsetDateTime.now(clock);
                            deltaFile.cancel(now);
                            final DeltaFileStage endingStage = deltaFile.getStage();
                            if (!startingStage.equals(endingStage) && endingStage.equals(DeltaFileStage.CANCELLED)) {
                                analyticEventService.recordCancel(deltaFile, now);
                            }

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

    public List<PerActionUniqueKeyValues> errorMetadataUnion(List<UUID> dids) {
        // TODO: limit fields returned
        List<DeltaFileFlow> deltaFileFlows = deltaFileFlowRepo.findAllByDeltaFileIds(dids);

        Map<Pair<String, String>, PerActionUniqueKeyValues> actionKeyValues = new HashMap<>();
        for (DeltaFileFlow flow : deltaFileFlows) {
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
        return new ArrayList<>(actionKeyValues.values());
    }

    public List<UniqueKeyValues> sourceMetadataUnion(List<UUID> dids) {
        // TODO: limit fields returned
        List<DeltaFileFlow> deltaFileFlows = deltaFileFlowRepo.findAllByDeltaFileIdsAndFlowZero(dids);

        Map<String, UniqueKeyValues> keyValues = new HashMap<>();
        deltaFileFlows.stream()
                .map(f -> f.getInput().getMetadata())
                .forEach(map -> map.forEach((key, value) -> keyValues.computeIfAbsent(key, UniqueKeyValues::new).addValue(value)));
        return new ArrayList<>(keyValues.values());
    }

    private void advanceAndSave(List<StateMachineInput> inputs, boolean insertAndForget) {
        if (inputs.isEmpty()) {
            return;
        }

        List<WrappedActionInput> actionInputs = stateMachine.advance(inputs);
        inputs.stream()
            .filter(input -> input.deltaFile().hasJoiningAction())
            .forEach(input -> deltaFileCacheService.remove(input.deltaFile().getDid()));

        Set<DeltaFile> deltaFiles = inputs.stream().map(StateMachineInput::deltaFile).collect(Collectors.toSet());
        if (insertAndForget) {
            deltaFileRepo.insertBatch(new ArrayList<>(deltaFiles));
        } else {
            deltaFileCacheService.saveAll(deltaFiles);
        }
        enqueueActions(actionInputs);
    }

    private void handleMissingFlow(DeltaFile deltaFile, DeltaFileFlow flow, String errorContext) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        Action action = flow.queueNewAction(MISSING_FLOW_ACTION, ActionType.UNKNOWN, false, now);
        processErrorEvent(deltaFile, flow, action, buildMissingFlowErrorEvent(deltaFile, now, errorContext));
        deltaFile.setStage(DeltaFileStage.ERROR);
    }

    public void deleteContentAndMetadata(UUID did, Content content) {
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
     * @param  flow            the dataSource of the DeltaFiles to be deleted
     * @param  policy          the policy of the DeltaFiles to be deleted
     * @param  deleteMetadata  whether to delete the metadata of the DeltaFiles in addition to the content
     * @return                 true if there are more DeltaFiles to delete, false otherwise
     */
    public boolean timedDelete(OffsetDateTime createdBefore, OffsetDateTime completedBefore, Long minBytes, String flow, String policy, boolean deleteMetadata) {
        int batchSize = deltaFiPropertiesService.getDeltaFiProperties().getDeletePolicyBatchSize();

        int alreadyDeleted = 0;
        if (deleteMetadata) {
            boolean hasMore = true;
            while (hasMore) {
                int deletedInBatch = deltaFileRepo.deleteIfNoContent(createdBefore, completedBefore, minBytes, flow, batchSize);
                hasMore = deletedInBatch == batchSize;
                alreadyDeleted += deletedInBatch;
            }

            if (alreadyDeleted > 0) {
                log.info("Deleted {} deltaFiles with no content for policy {}", alreadyDeleted, policy);
            }
        }
        logBatch(batchSize, policy);
        List<DeltaFileDeleteDTO> deltaFiles = deltaFileRepo.findForTimedDelete(createdBefore, completedBefore, minBytes, flow, deleteMetadata, batchSize);
        delete(deltaFiles, policy, deleteMetadata, alreadyDeleted);

        return deltaFiles.size() == batchSize;
    }

    public List<DeltaFileDeleteDTO> diskSpaceDelete(long bytesToDelete, String flow, String policy) {
        int batchSize = deltaFiPropertiesService.getDeltaFiProperties().getDeletePolicyBatchSize();

        logBatch(batchSize, policy);
        return delete(deltaFileRepo.findForDiskSpaceDelete(bytesToDelete, flow, batchSize), policy, false, 0);
    }

    public void logBatch(int batchSize, String policy) {
        log.info("Searching for batch of up to {} deltaFiles to delete for policy {}", batchSize, policy);
    }

    public List<DeltaFileDeleteDTO> delete(List<DeltaFileDeleteDTO> deltaFiles, String policy, boolean deleteMetadata, int alreadyDeleted) {
        if (deltaFiles.isEmpty()) {
            log.info("No deltaFiles found to delete for policy {}", policy);
            if (alreadyDeleted > 0) {
                metricService.increment(new Metric(DELETED_FILES, alreadyDeleted).addTag("policy", policy));
            }
            return deltaFiles;
        }

        log.info("Deleting {} deltaFiles for policy {}", deltaFiles.size(), policy);
        long totalBytes = deltaFiles.stream().filter(d -> d.getContentDeleted() == null).mapToLong(DeltaFileDeleteDTO::getTotalBytes).sum();

        deleteContent(deltaFiles, policy, deleteMetadata);
        metricService.increment(new Metric(DELETED_FILES, deltaFiles.size() + alreadyDeleted).addTag("policy", policy));
        metricService.increment(new Metric(DELETED_BYTES, totalBytes).addTag("policy", policy));
        log.info("Finished deleting {} deltaFiles for policy {}", deltaFiles.size(), policy);

        return deltaFiles;
    }

    public void requeue() {
        Integer numFound = null;
        while (numFound == null || numFound == REQUEUE_BATCH_SIZE) {
            OffsetDateTime modified = OffsetDateTime.now(clock);
            Set<UUID> longRunningDids = coreEventQueue.getLongRunningTasks().stream().map(ActionExecution::did).collect(Collectors.toSet());
            Set<String> skipActions = queueManagementService.coldQueueActions();

            List<DeltaFile> filesToRequeue = deltaFileRepo.findForRequeue(modified,
                    getProperties().getRequeueDuration(), skipActions, longRunningDids, REQUEUE_BATCH_SIZE);
            numFound = filesToRequeue.size();
            if (numFound > 0) {
                log.info("requeuing {}", numFound);
            }

            List<WrappedActionInput> actionInputs = new ArrayList<>();
            filesToRequeue.forEach(deltaFile -> {
                deltaFile.setRequeueCount(deltaFile.getRequeueCount() + 1);
                deltaFile.setModified(modified);
                deltaFile.getFlows().stream()
                        .filter(f -> f.getState() == DeltaFileFlowState.IN_FLIGHT)
                        .forEach(flow -> {
                            Action action = flow.lastAction();
                            if ((action.getState() == QUEUED) &&
                                    action.getModified().isBefore(modified.minus(getProperties().getRequeueDuration())) &&
                                    (skipActions == null || !skipActions.contains(action.getName()))) {
                                WrappedActionInput actionInput = requeueActionInput(deltaFile, flow, action);
                                if (actionInput != null) {
                                    action.setModified(modified);
                                    action.setQueued(modified);
                                    flow.updateState();
                                    actionInputs.add(actionInput);
                                }
                            }
                        });
            });

            if (!actionInputs.isEmpty()) {
                log.warn("{} actions exceeded requeue threshold of {} seconds, requeuing now", actionInputs.size(), getProperties().getRequeueDuration());
                enqueueActions(actionInputs, true);
            }
            deltaFileRepo.saveAll(filesToRequeue);
        }
    }

    public void requeueColdQueueActions(List<String> actionNames, int maxFiles) {
        OffsetDateTime modified = OffsetDateTime.now(clock);
        List<DeltaFile> filesToRequeue = deltaFileRepo.findColdQueuedForRequeue(actionNames, maxFiles, modified);

        List<WrappedActionInput> actionInputs = new ArrayList<>();
        filesToRequeue.forEach(deltaFile -> {
            deltaFile.setRequeueCount(deltaFile.getRequeueCount() + 1);
            deltaFile.setModified(modified);
            deltaFile.getFlows().stream()
                    .filter(f -> f.getState() == DeltaFileFlowState.IN_FLIGHT)
                    .forEach(flow -> {
                        Action action = flow.lastAction();
                        if ((action.getState() == COLD_QUEUED) && actionNames.contains(action.getName())) {
                            WrappedActionInput actionInput = requeueActionInput(deltaFile, flow, action);
                            if (actionInput != null) {
                                action.setState(QUEUED);
                                action.setModified(modified);
                                action.setQueued(modified);
                                flow.updateState();
                                actionInputs.add(actionInput);
                            }
                        }
                    });
        });

        if (!actionInputs.isEmpty()) {
            log.warn("Moving {} from the cold to warm queue", actionInputs.size());
            enqueueActions(actionInputs, true);
        }
        deltaFileRepo.saveAll(filesToRequeue);
    }

    private WrappedActionInput requeueActionInput(DeltaFile deltaFile, DeltaFileFlow flow, Action action) {
        ActionConfiguration actionConfiguration = actionConfiguration(flow.getName(), flow.getType(), action.getName());

        if (actionConfiguration == null) {
            String errorMessage = "Action named " + action.getName() + " is no longer running";
            log.error(errorMessage);
            ActionEvent event = ActionEvent.builder()
                    .did(deltaFile.getDid())
                    .flowName(flow.getName())
                    .flowId(flow.getId())
                    .actionName(action.getName())
                    .error(ErrorEvent.builder().cause(errorMessage).build())
                    .type(ActionEventType.UNKNOWN)
                    .build();
            processErrorEvent(deltaFile, flow, action, event);

            return null;
        }

        if (deltaFile.getJoinId() != null) {
            return deltaFile.buildActionInput(actionConfiguration, flow, deltaFile.getParentDids(), action, getProperties().getSystemName(), identityService.getUniqueId(), null);
        }

        return deltaFile.buildActionInput(actionConfiguration, flow, action, getProperties().getSystemName(), identityService.getUniqueId(), null);
    }

    public void autoResume() {
        autoResume(OffsetDateTime.now(clock));
    }

    public int autoResume(OffsetDateTime timestamp) {
        int queued = 0;
        List<DeltaFile> autoResumeDeltaFiles = deltaFileRepo.findReadyForAutoResume(timestamp);
        if (!autoResumeDeltaFiles.isEmpty()) {
            Map<UUID, String> flowByDid = autoResumeDeltaFiles.stream()
                    .collect(Collectors.toMap(DeltaFile::getDid, DeltaFile::getDataSource));
            List<RetryResult> results = resumeDeltaFiles(autoResumeDeltaFiles.stream()
                    .collect(Collectors.toMap(DeltaFile::getDid, Function.identity())), Collections.emptyList());
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

    private ActionConfiguration actionConfiguration(String flow, FlowType flowType, String actionName) {
        return switch (flowType) {
            case TIMED_DATA_SOURCE -> timedDataSourceService.findRunningActionConfig(flow, actionName);
            case TRANSFORM -> transformFlowService.findRunningActionConfig(flow, actionName);
            case EGRESS -> egressFlowService.findRunningActionConfig(flow, actionName);
            default -> null;
        };
    }

    public boolean processActionEvents(String uniqueId) {
        try {
            while (!Thread.currentThread().isInterrupted() && processIncomingEvents) {
                ActionEvent event = coreEventQueue.takeResult(uniqueId);
                validateActionEventHeader(event);
                processResult(event);
            }
        } catch (Throwable e) {
            log.error("Error receiving event: {}", e.getMessage());
            return false;
        }
        return true;
    }

    public void validateActionEventHeader(ActionEvent event) {
        String validationError = event.validateHeader();
        if (validationError != null) {
            log.error("Received invalid action event: {}: ({})", validationError, event);
            throw new InvalidActionEventException(validationError);
        }
    }

    public void processResult(ActionEvent event) {
        if (event == null) throw new RuntimeException("CoreEventQueue returned null event. This should NEVER happen");

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
                                log.warn("Retrying after OptimisticLockingFailureException caught processing {} for {}. Error: {}", event.getActionName(), event.getDid(), e.getMessage(), e);
                                deltaFileCacheService.remove(event.getDid());
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

    private void enqueueActions(List<WrappedActionInput> actionInputs) throws EnqueueActionException {
        enqueueActions(actionInputs, false);
    }

    private void enqueueActions(List<WrappedActionInput> actionInputs, boolean checkUnique) throws EnqueueActionException {
        if (actionInputs.isEmpty()) {
            return;
        }

        try {
            for (WrappedActionInput actionInput : actionInputs) {
                populateBatchInput(actionInput);
            }

            coreEventQueue.putActions(actionInputs, checkUnique);
        } catch (Exception e) {
            log.error("Failed to queue action(s)", e);
            throw new EnqueueActionException("Failed to queue action(s)", e);
        }
    }

    private void populateBatchInput(WrappedActionInput actionInput) throws MissingDeltaFilesException {
        List<UUID> joinedDids = actionInput.getActionContext().getJoinedDids();
        if (!joinedDids.isEmpty() && actionInput.getDeltaFileMessages() == null) {
            UUID did = actionInput.getActionContext().getDid();
            if (!deltaFileRepo.existsById(did)) {
                deltaFileRepo.saveAndFlush(actionInput.getDeltaFile());
            }

            DeltaFile deltaFile = actionInput.getDeltaFile();
            Action action = deltaFile.firstFlow().firstAction();
            List<String> deleteMetadataKeys = new ArrayList<>();
            Map<String, String> addMetadata = new HashMap<>();
            boolean isReplay = action.getName().equals(REPLAY_ACTION_NAME);
            if (isReplay) {
                deleteMetadataKeys.addAll(action.getDeleteMetadataKeys());
                addMetadata.putAll(action.getMetadata());
            }

            List<DeltaFileMessage> deltaFileMessages = new ArrayList<>();
            List<DeltaFile> parents = findDeltaFiles(joinedDids);

            UUID joinId = actionInput.getDeltaFile().getJoinId();
            for (DeltaFile parent : parents) {
                for (DeltaFileFlow deltaFileFlow : parent.getFlows()) {
                    if (joinId.equals(deltaFileFlow.getJoinId())) {
                        if (isReplay) {
                            DeltaFileFlow tmpFlow = new DeltaFileFlow();
                            tmpFlow.setActions(new ArrayList<>(deltaFileFlow.getActions()));
                            Action tmpAction = tmpFlow.addAction("tmp", ActionType.TRANSFORM, ActionState.COMPLETE, OffsetDateTime.now(clock));
                            tmpAction.setDeleteMetadataKeys(deleteMetadataKeys);
                            tmpAction.setMetadata(addMetadata);
                            deltaFileMessages.add(new DeltaFileMessage(tmpFlow.getMetadata(), deltaFileFlow.getImmutableContent()));
                        } else {
                            deltaFileMessages.add(new DeltaFileMessage(deltaFileFlow.getMetadata(), deltaFileFlow.getImmutableContent()));
                        }
                    }
                }
            }
            actionInput.setDeltaFileMessages(deltaFileMessages);
        }
    }

    public boolean taskTimedDataSource(String flowName, String memo, boolean overrideMemo) throws EnqueueActionException {
        TimedDataSource dataSource;
        if (overrideMemo) {
            // use the stored value so the cached memo value is not overwritten
            dataSource = timedDataSourceService.getFlowOrThrow(flowName);
            if (!dataSource.isRunning()) {
                throw new IllegalStateException("Timed ingress dataSource '" + flowName + "' cannot be tasked while in a state of " + dataSource.getFlowStatus().getState());
            }
            dataSource.setMemo(memo);
        } else {
            dataSource = timedDataSourceService.getRunningFlowByName(flowName);
        }

        return taskTimedDataSource(dataSource);
    }

    public boolean taskTimedDataSource(TimedDataSource dataSource) throws EnqueueActionException {
        WrappedActionInput actionInput = dataSource.buildActionInput(getProperties().getSystemName(), OffsetDateTime.now(clock), identityService.getUniqueId());
        try {
            if (!coreEventQueue.queueHasTaskingForAction(actionInput)) {
                timedDataSourceService.setLastRun(dataSource.getName(), OffsetDateTime.now(clock),
                        actionInput.getActionContext().getDid());
                coreEventQueue.putActions(List.of(actionInput), false);
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

    private void deleteContent(List<DeltaFileDeleteDTO> deltaFiles, String policy, boolean deleteMetadata) {
        List<DeltaFileDeleteDTO> deltaFilesWithContent = deltaFiles.stream().filter(d -> d.getContentDeleted() == null).toList();
        contentStorageService.deleteAllByObjectName(deltaFilesWithContent.stream()
                .flatMap(d -> d.getContentObjectIds().stream()
                        .map(contentId -> Segment.objectName(d.getDid(), contentId))
                )
                .toList());

        if (deleteMetadata) {
            deleteMetadata(deltaFiles);
        } else {
            deltaFileRepo.setContentDeletedByDidIn(
                    deltaFilesWithContent.stream().map(DeltaFileDeleteDTO::getDid).distinct().toList(),
                    OffsetDateTime.now(clock),
                    policy);
        }
    }

    private void deleteMetadata(List<DeltaFileDeleteDTO> deltaFiles) {
        for (List<DeltaFileDeleteDTO> batch : Lists.partition(deltaFiles, 1000)) {
            deltaFileRepo.batchedBulkDeleteByDidIn(batch.stream().map(DeltaFileDeleteDTO::getDid).distinct().toList());
        }
    }

    public SummaryByFlow getErrorSummaryByFlow(Integer offset, Integer limit, ErrorSummaryFilter filter, DeltaFileDirection direction) {
        return deltaFileFlowRepo.getErrorSummaryByFlow(offset,
                (Objects.nonNull(limit) && limit > 0) ? limit : DEFAULT_QUERY_LIMIT,
                filter,
                Objects.nonNull(direction) ? direction : DeltaFileDirection.ASC);
    }

    public SummaryByFlowAndMessage getErrorSummaryByMessage(Integer offset, Integer limit, ErrorSummaryFilter filter, DeltaFileDirection direction) {
        return deltaFileFlowRepo.getErrorSummaryByMessage(offset,
                (Objects.nonNull(limit) && limit > 0) ? limit : DEFAULT_QUERY_LIMIT,
                filter,
                Objects.nonNull(direction) ? direction : DeltaFileDirection.ASC);
    }

    public SummaryByFlow getFilteredSummaryByFlow(Integer offset, Integer limit, FilteredSummaryFilter filter, DeltaFileDirection direction) {
        return deltaFileFlowRepo.getFilteredSummaryByFlow(offset,
                (Objects.nonNull(limit) && limit > 0) ? limit : DEFAULT_QUERY_LIMIT,
                filter,
                Objects.nonNull(direction) ? direction : DeltaFileDirection.ASC);
    }

    public SummaryByFlowAndMessage getFilteredSummaryByMessage(Integer offset, Integer limit, FilteredSummaryFilter filter, DeltaFileDirection direction) {
        return deltaFileFlowRepo.getFilteredSummaryByMessage(offset,
                (Objects.nonNull(limit) && limit > 0) ? limit : DEFAULT_QUERY_LIMIT,
                filter,
                Objects.nonNull(direction) ? direction : DeltaFileDirection.ASC);
    }

    public List<String> annotationKeys() {
        return annotationRepo.findDistinctAnnotationKeys().stream().sorted().toList();
    }

    private DeltaFiProperties getProperties() {
        return deltaFiPropertiesService.getDeltaFiProperties();
    }

    public Long totalCount() {
        return deltaFileRepo.count();
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
            Set<UUID> previousDids = new HashSet<>();

            /*
             * If applying any resume policy that is not dataSource-specific,
             * then query for all DeltaFiles with an ERROR, and use that
             * result set for all policies without a dataSource name.
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

                List<UUID> dids = resumePolicyService.canBeApplied(resumePolicy, checkFiles, previousDids);
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
            if (resumePolicy.getId() == null) {
                resumePolicy.setId(uuidGenerator.generate());
            }
            errors.addAll(resumePolicy.validate());
        }

        if (errors.isEmpty()) {
            List<DeltaFile> checkFiles =
                    deltaFileRepo.findResumePolicyCandidates(resumePolicy.getDataSource());

            List<UUID> dids = resumePolicyService.canBeApplied(resumePolicy, checkFiles, Collections.emptySet());
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

    private List<DeltaFile> findDeltaFiles(List<UUID> dids) throws MissingDeltaFilesException {
        Map<UUID, DeltaFile> deltaFileMap = deltaFileRepo.findAllById(dids).stream()
                .collect(Collectors.toMap(DeltaFile::getDid, Function.identity()));

        if (deltaFileMap.size() < dids.size()) {
            List<UUID> missingDids = dids.stream().filter(did -> !deltaFileMap.containsKey(did)).toList();
            if (!missingDids.isEmpty()) {
                throw new MissingDeltaFilesException(missingDids);
            }
        }

        return dids.stream().map(deltaFileMap::get).toList();
    }

    public void queueTimedOutJoin(JoinEntry joinEntry, List<UUID> joinDids) {
        ActionConfiguration actionConfiguration = actionConfiguration(joinEntry.getJoinDefinition().getFlow(), joinEntry.getJoinDefinition().getActionType() == ActionType.TRANSFORM ? FlowType.TRANSFORM : FlowType.EGRESS,
                joinEntry.getJoinDefinition().getAction());

        if (actionConfiguration == null) {
            log.warn("Time-based join action couldn't run because action {} in dataSource {} is no longer running",
                    joinEntry.getJoinDefinition().getAction(), joinEntry.getJoinDefinition().getFlow());
            return;
        }

        DeltaFile parent = getDeltaFile(joinDids.getLast());
        DeltaFileFlow deltaFileFlow = parent.getFlows().stream().filter(flow -> joinEntry.getId().equals(flow.getJoinId())).findFirst().orElse(null);
        if (deltaFileFlow == null) {
            log.warn("Time-based join action couldn't run because a dataSource with a joinId of {} was not found in the parent with a did of {}. Failed executing action {} from dataSource dataSource {}",
                    joinEntry.getId(), parent.getDid(), joinEntry.getJoinDefinition().getAction(), joinEntry.getJoinDefinition().getFlow());
            return;
        }

        WrappedActionInput input = DeltaFileUtil.createAggregateInput(actionConfiguration, deltaFileFlow, joinEntry, joinDids, ActionState.QUEUED, getProperties().getSystemName(),  null);

        enqueueActions(List.of(input));
    }

    public void failTimedOutJoin(JoinEntry joinEntry, List<UUID> joinedDids, String reason) {
        log.debug("Failing join action");

        List<UUID> missingDids = new ArrayList<>();

        for (UUID did : joinedDids) {
            try {
                DeltaFile deltaFile = deltaFileRepo.findById(did).orElse(null);
                if (deltaFile == null) {
                    missingDids.add(did);
                    continue;
                }

                deltaFile.timeoutJoinAction(joinEntry.getId(), joinEntry.getJoinDefinition().getAction(),  OffsetDateTime.now(clock), reason);
                deltaFileRepo.save(deltaFile);
            } catch (OptimisticLockingFailureException e) {
                log.warn("Unable to save DeltaFile with failed join action", e);
            }
        }

        if (!missingDids.isEmpty()) {
            log.warn("DeltaFiles with the following ids were missing during failed join: {}", missingDids);
        }
    }

    private void completeJoin(ActionEvent event, DeltaFile deltaFile, Action action, ActionConfiguration actionConfiguration) {
        if ((actionConfiguration != null) && (actionConfiguration.getJoin() != null)) {
            List<WrappedActionInput> actionInputs = new ArrayList<>();
            List<DeltaFile> parentDeltaFiles = deltaFileRepo.findAllById(deltaFile.getParentDids());
            OffsetDateTime now = OffsetDateTime.now(clock);
            for (DeltaFile parentDeltaFile : parentDeltaFiles) {
                parentDeltaFile.getChildDids().add(deltaFile.getDid());
                parentDeltaFile.joinedAction(event.getDid(), action.getName(), event.getStart(), event.getStop(), now);
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
