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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.common.content.*;
import org.deltafi.common.converters.KeyValueConverter;
import org.deltafi.common.types.*;
import org.deltafi.common.util.ParameterTemplateException;
import org.deltafi.common.util.ParameterUtil;
import org.deltafi.common.uuid.UUIDGenerator;
import org.deltafi.core.configuration.DeltaFiProperties;
import org.deltafi.core.delete.DiskSpaceDelete;
import org.deltafi.core.exceptions.*;
import org.deltafi.core.generated.types.*;
import org.deltafi.core.metrics.MetricService;
import org.deltafi.core.metrics.MetricsUtil;
import org.deltafi.core.repo.*;
import org.deltafi.core.services.analytics.AnalyticEventService;
import org.deltafi.core.types.Flow;
import org.deltafi.core.types.*;
import org.deltafi.core.util.ParameterResolver;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.env.Environment;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.deltafi.common.constant.DeltaFiConstants.*;
import static org.deltafi.common.types.ActionState.COLD_QUEUED;
import static org.deltafi.common.types.ActionState.QUEUED;
import static org.deltafi.core.services.DeletePolicyService.TTL_SYSTEM_POLICY;

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

    private static final int DEFAULT_QUERY_LIMIT = 50;

    private final Clock clock;
    private final TransformFlowService transformFlowService;
    private final DataSinkService dataSinkService;
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
    private final RestDataSourceService restDataSourceService;
    private final TimedDataSourceService timedDataSourceService;
    private final QueueManagementService queueManagementService;
    private final QueuedAnnotationRepo queuedAnnotationRepo;
    private final Environment environment;
    private final UUIDGenerator uuidGenerator;
    private final IdentityService identityService;
    private final FlowDefinitionService flowDefinitionService;
    private final ParameterResolver parameterResolver;
    private final LocalContentStorageService localContentStorageService;

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

    private DeltaFile buildIngressDeltaFile(DataSource dataSource, IngressEventItem ingressEventItem,
                                            OffsetDateTime ingressStartTime, OffsetDateTime ingressStopTime,
                                            String ingressActionName, FlowType flowType) {

        OffsetDateTime now = OffsetDateTime.now(clock);

        Map<String, String> combinedDataSourceMetadata = dataSource.getMetadata() != null ?
                new HashMap<>(dataSource.getMetadata()) : new HashMap<>();

        if (ingressEventItem.getMetadata() != null) {
            combinedDataSourceMetadata.putAll(ingressEventItem.getMetadata());
        }

        Action ingressAction = Action.builder()
                .name(ingressActionName)
                .replayStart(true)
                .type(ActionType.INGRESS)
                .state(ActionState.COMPLETE)
                .created(ingressStartTime)
                .modified(now)
                .content(ingressEventItem.getContent())
                .metadata(combinedDataSourceMetadata)
                .start(ingressStartTime)
                .stop(ingressStopTime)
                .build();

        if (dataSource instanceof TimedDataSource timedDataSource) {
            ingressAction.setActionClass(timedDataSource.getTimedIngressAction().getType());
        }

        DeltaFileFlow ingressFlow = DeltaFileFlow.builder()
                .flowDefinition(flowDefinitionService.getOrCreateFlow(ingressEventItem.getFlowName(), flowType))
                .number(0)
                .state(DeltaFileFlowState.COMPLETE)
                .testMode(dataSource.isTestMode())
                .created(ingressStartTime)
                .modified(now)
                .actions(new ArrayList<>(List.of(ingressAction)))
                .publishTopics(new ArrayList<>())
                .depth(0)
                .build();

        if (dataSource.isTestMode()) {
            ingressFlow.setTestModeReason(dataSource.getName());
        }

        long contentSize = ContentUtil.computeContentSize(ingressEventItem.getContent());

        DeltaFile deltaFile = DeltaFile.builder()
                .did(ingressEventItem.getDid())
                .dataSource(dataSource.getName())
                .name(ingressEventItem.getDeltaFileName())
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
        ingressFlow.setOwner(deltaFile);
        ingressFlow.getInput().setFlow(ingressFlow);
        deltaFile.addAnnotations(ingressEventItem.getAnnotations());
        deltaFile.addAnnotationsIfAbsent(dataSource.getAnnotationConfig());
        return deltaFile;
    }

    public DeltaFile ingressRest(RestDataSource restDataSource, IngressEventItem ingressEventItem,
                                  OffsetDateTime ingressStartTime, OffsetDateTime ingressStopTime) {
        DeltaFile deltaFile = buildIngressDeltaFile(restDataSource, ingressEventItem, ingressStartTime, ingressStopTime,
                INGRESS_ACTION, FlowType.REST_DATA_SOURCE);

        if (restDataSource.isPaused()) {
            deltaFile.firstFlow().setState(DeltaFileFlowState.PAUSED);
            deltaFile.setPaused(true);
            deltaFileRepo.insertOne(deltaFile);
        } else {
            advanceAndSave(List.of(new StateMachineInput(deltaFile, deltaFile.firstFlow())), false);
        }
        return deltaFile;
    }

    public DeltaFile buildTimedDataSourceDeltaFile(DataSource dataSource, ActionEvent parentEvent, IngressEventItem ingressEventItem) {
        ingressEventItem.setFlowName(parentEvent.getFlowName());
        return buildIngressDeltaFile(dataSource, ingressEventItem, parentEvent.getStart(), parentEvent.getStop(),
                parentEvent.getActionName(), FlowType.TIMED_DATA_SOURCE);
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

            deltaFile.wireBackPointers();
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
                    analyticEventService.recordEgress(deltaFile, flow);
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
        String dataSink = flow.getType() == FlowType.DATA_SINK ? flow.getName() : null;
        Map<String, String> defaultTags = MetricsUtil.tagsFor(event.getType(), action.getName(),
                deltaFile.getDataSource(), dataSink);
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

            metricService.increment(Metric.builder()
                    .name(ACTION_EXECUTION_TIME_MS)
                    .value(Duration.between(event.getStart(), event.getStop()).toMillis())
                    .tags(tags)
                    .build());

            metricService.increment(Metric.builder()
                    .name(ACTION_EXECUTION)
                    .value(1)
                    .tags(tags)
                    .build());
        }
    }

    public void handleIngressActionEvent(ActionEvent event) {
        TimedDataSource dataSource = timedDataSourceService.getActiveFlowByName(event.getFlowName());
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
                .map((item) -> buildTimedDataSourceDeltaFile(dataSource, event, item))
                .map((deltaFile) -> new StateMachineInput(deltaFile, deltaFile.firstFlow()))
                .toList();

        if (dataSource.isPaused()) {
            for (StateMachineInput stateMachineInput : stateMachineInputs) {
                stateMachineInput.deltaFile().firstFlow().setState(DeltaFileFlowState.PAUSED);
                stateMachineInput.deltaFile().setPaused(true);
            }
            deltaFileRepo.insertBatch(stateMachineInputs.stream().map(StateMachineInput::deltaFile).toList(), deltaFiPropertiesService.getDeltaFiProperties().getInsertBatchSize());
        } else {
            advanceAndSave(stateMachineInputs, false);
        }

        for (StateMachineInput stateMachineInput : stateMachineInputs) {
            DeltaFile deltaFile = stateMachineInput.deltaFile();
            counter.byteCount += deltaFile.getIngressBytes();
            if (deltaFile.getFlows().size() == 1) {
                ActionState lastState = deltaFile.firstFlow().lastActionState();
                if (lastState == ActionState.FILTERED) {
                    counter.filteredFiles++;
                } else if (lastState == ActionState.ERROR) {
                    logTimedDataSourceErrorAnalytics(deltaFile, event, "Ingress error. Data source: " + dataSource.getName());
                    counter.erroredFiles++;
                }
            }
            analyticEventService.recordIngress(deltaFile.getDid(), deltaFile.getCreated(), deltaFile.getDataSource(),
                    FlowType.TIMED_DATA_SOURCE, deltaFile.getIngressBytes(), deltaFile.annotationMap(), AnalyticIngressTypeEnum.DATA_SOURCE);
        }

        String actionClass =
                (dataSource.findActionConfigByName(event.getActionName()) != null)
                        ? dataSource.findActionConfigByName(event.getActionName()).getType()
                        : null;

        List<Metric> metrics = (event.getMetrics() != null) ? event.getMetrics() : new ArrayList<>();

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
            transformFlowService.getActiveFlowByName(event.getFlowName());
        } catch (MissingFlowException missingFlowException) {
            handleMissingFlow(deltaFile, flow, missingFlowException);
            return;
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        deltaFile.setModified(now);
        flow.setModified(now);

        if (transformEvents.size() == 1 && deltaFile.getDid().equals(transformEvents.getFirst().getDid())) {
            TransformEvent transformEvent = transformEvents.getFirst();
            deltaFile.addAnnotations(transformEvent.getAnnotations());
            action.complete(event.getStart(), event.getStop(), transformEvent.getContent(),
                    transformEvent.getMetadata(), transformEvent.getDeleteMetadataKeys(), now);
            advanceAndSave(List.of(new StateMachineInput(deltaFile, flow)), false);
            if (!transformEvent.getAnnotations().isEmpty()) {
                analyticEventService.queueAnnotations(deltaFile.getDid(), transformEvent.getAnnotations());
            }
        } else {
            // remove the parent from the cache to ensure it is not marked completed until the children are persisted
            deltaFileCacheService.remove(deltaFile.getDid());
            action.changeState(ActionState.SPLIT, event.getStart(), event.getStop(), now);
            List<StateMachineInput> inputs = new ArrayList<>();
            inputs.add(new StateMachineInput(deltaFile, flow));
            List<StateMachineInput> childInputs =
                    createChildren(transformEvents, event.getStart(), event.getStop(), deltaFile, flow);
            inputs.addAll(childInputs);
            List<UUID> updatedChildDids = new ArrayList<>(deltaFile.getChildDids());
            updatedChildDids.addAll(childInputs.stream()
                    .map(input -> input.deltaFile().getDid())
                    .toList());
            deltaFile.setChildDids(updatedChildDids);
            deltaFile.setWaitingForChildren(true);
            advanceAndSave(inputs, false);
        }
    }

    private List<StateMachineInput> createChildren(List<TransformEvent> transformEvents, OffsetDateTime startTime,
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
        action.setState(ActionState.COMPLETE);
        action.setStart(start);
        action.setStop(stop);

        advanceAndSave(List.of(new StateMachineInput(deltaFile, flow)), false);
        Map<String, String> tags = Map.of(
                "dataSource", deltaFile.getDataSource(),
                "dataSink", flow.getName());
        long bytes = Segment.calculateTotalSize(flow.lastAction().getContent().stream().flatMap(s -> s.getSegments().stream()).collect(Collectors.toSet()));
        metricService.increment(new Metric(BYTES_TO_SINK, bytes, tags));
        metricService.increment(new Metric(FILES_TO_SINK, 1, tags));
    }

    public void filter(DeltaFile deltaFile, DeltaFileFlow flow, Action action, ActionEvent event, OffsetDateTime now) {
        deltaFile.addAnnotations(event.getFilter().getAnnotations());
        deltaFile.setModified(now);
        deltaFile.setFiltered(true);
        action.setFilteredActionState(event.getStart(), event.getStop(), now, event.getFilter().getMessage(),
                event.getFilter().getContext());

        advanceAndSave(List.of(new StateMachineInput(deltaFile, flow)), false);
        logFilterAnalytics(deltaFile, flow, event);
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
                analyticEventService.recordCancel(deltaFile);
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
        logErrorAnalytics(deltaFile, flow, event);
    }

    private void logFilterAnalytics(DeltaFile deltaFile, DeltaFileFlow flow, ActionEvent event) {
        analyticEventService.recordFilter(deltaFile, flow.getName(), flow.getType(), flow.lastAction().getName(), flow.getErrorOrFilterCause(), flow.getModified());
        if (!event.getFilter().getAnnotations().isEmpty()) {
            analyticEventService.queueAnnotations(deltaFile.getDid(), event.getFilter().getAnnotations());
        }
    }

    private void logErrorAnalytics(DeltaFile deltaFile, DeltaFileFlow flow, ActionEvent event) {
        analyticEventService.recordError(deltaFile, flow.getName(), flow.getType(), flow.lastAction().getName(), flow.getErrorOrFilterCause(), flow.getModified());
        if (!event.getError().getAnnotations().isEmpty()) {
            analyticEventService.queueAnnotations(deltaFile.getDid(), event.getError().getAnnotations());
        }
    }

    private void logTimedDataSourceErrorAnalytics(DeltaFile deltaFile, ActionEvent action, String message) {
        analyticEventService.recordError(deltaFile, action.getFlowName(), FlowType.TIMED_DATA_SOURCE, action.getActionName(), message, action.getStop());
    }

    private DeltaFile getTerminalStageDeltaFileOrCache(UUID did) {
        if (deltaFileCacheService.isCached(did)) {
            return getCachedDeltaFile(did);
        }
        return deltaFileRepo.findByDidAndStageIn(did,
                        Arrays.asList(DeltaFileStage.COMPLETE, DeltaFileStage.ERROR, DeltaFileStage.CANCELLED))
                .orElse(null);
    }

    public void annotateMatching(DeltaFilesFilter filter, Map<String, String> annotations, boolean allowOverwrites) {
        // there are no restrictions on the annotation filters, just make sure filter is capped by the modified time
        ensureModifiedBeforeNow(filter);

        int numFound = REQUEUE_BATCH_SIZE;
        while (numFound == REQUEUE_BATCH_SIZE) {
            List<DeltaFile> toAnnotate = deltaFileRepo.deltaFiles(filter, REQUEUE_BATCH_SIZE);
            annotateMatching(toAnnotate, annotations, allowOverwrites);
            numFound = toAnnotate.size();
        }
    }

    void annotateMatching(List<DeltaFile> deltaFiles, Map<String, String> annotations, boolean allowOverwrites) {
        List<QueuedAnnotation> queuedAnnotations = new ArrayList<>();
        for (DeltaFile deltaFile : deltaFiles) {
            if (deltaFile.isTerminal()) {
                addAnnotations(deltaFile, annotations, allowOverwrites, OffsetDateTime.now(clock));
            } else if (deltaFileCacheService.isCached(deltaFile.getDid())) {
                didMutexService.executeWithLock(deltaFile.getDid(), () -> {
                    // make sure we are acting on the latest cached value with the lock
                    DeltaFile deltaFileFromCache = getCachedDeltaFile(deltaFile.getDid());
                    if (deltaFileFromCache != null) {
                        addAnnotations(deltaFileFromCache, annotations, allowOverwrites, OffsetDateTime.now(clock));
                    } else {
                        queuedAnnotations.add(new QueuedAnnotation(deltaFile.getDid(), annotations, allowOverwrites));
                    }
                });
            } else {
                queuedAnnotations.add(new QueuedAnnotation(deltaFile.getDid(), annotations, allowOverwrites));
            }
        }

        if (!queuedAnnotations.isEmpty()) {
            queuedAnnotationRepo.saveAll(queuedAnnotations);
        }
    }

    public void addAnnotations(UUID did, Map<String, String> annotations, boolean allowOverwrites) {
        didMutexService.executeWithLock(did, () -> {
            DeltaFile deltaFile = getTerminalStageDeltaFileOrCache(did);

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
                DeltaFile deltaFile = getTerminalStageDeltaFileOrCache(did);

                if (deltaFile == null) {
                    if (!deltaFileRepo.existsById(did)) {
                        log.warn("Attempted to apply queued annotation to deltaFile {} that no longer exists", did);
                    }
                    // else: not in a terminal stage yet; try again later
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

        analyticEventService.queueAnnotations(deltaFile.getDid(), deltaFile.annotationMap());
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
        try (Stream<DeltaFile> deltaFiles = deltaFileRepo.findByTerminalAndFlows_FlowDefinition_NameAndFlows_State(false, flowName, DeltaFileFlowState.PENDING_ANNOTATIONS)) {
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
            deltaFileRepo.insertBatch(collector, deltaFiPropertiesService.getDeltaFiProperties().getInsertBatchSize());
            collector.clear();
        }
    }

    public static ActionEvent buildMissingFlowErrorEvent(DeltaFile deltaFile, OffsetDateTime time, MissingFlowException missingFlowException) {
        return ActionEvent.builder()
                .did(deltaFile.getDid())
                .flowName("MISSING")
                .actionName(MISSING_FLOW_ACTION)
                .start(time)
                .stop(time)
                .error(ErrorEvent.builder()
                        .cause(missingFlowException.getMissingCause())
                        .context(missingFlowException.getMessage())
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
                .flowDefinition(fromFlow.getFlowDefinition())
                .number(0)
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
                .did(transformEvent.getDid())
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
        child.addAnnotations(transformEvent.getAnnotations());

        child.setName(transformEvent.getName());
        child.recalculateBytes();
        analyticEventService.recordIngress(child.getDid(), child.getCreated(), child.getDataSource(), deltaFile.firstFlow().getType(),
                child.getReferencedBytes(), Annotation.toMap(child.getAnnotations()), AnalyticIngressTypeEnum.CHILD);
        return new StateMachineInput(child, childFlow);
    }

    public List<RetryResult> resume(@NotNull DeltaFilesFilter filter, List<ResumeMetadata> resumeMetadata) {
        // check for filters set to values that would return DeltaFiles that cannot be resumed
        if ((filter.getStage() != null && filter.getStage() != DeltaFileStage.ERROR) || Boolean.TRUE.equals(filter.getContentDeleted())) {
            return List.of();
        }
        ensureModifiedBeforeNow(filter);

        // make sure only resumable DeltaFiles are pulled back
        filter.setStage(DeltaFileStage.ERROR);
        filter.setContentDeleted(Boolean.FALSE);

        List<RetryResult> retryResults = new ArrayList<>();

        int numFound = REQUEUE_BATCH_SIZE;
        while (numFound == REQUEUE_BATCH_SIZE) {
            List<DeltaFile> toResume = deltaFileRepo.deltaFiles(filter, REQUEUE_BATCH_SIZE);
            retryResults.addAll(resumeDeltaFiles(toResume, resumeMetadata));
            numFound = toResume.size();
        }

        return retryResults;
    }

    public List<RetryResult> resume(@NotNull List<UUID> dids, List<ResumeMetadata> resumeMetadata) {
        List<DeltaFile> deltaFiles = deltaFileCacheService.get(dids);
        Map<UUID, DeltaFile> deltaFilesMap = deltaFiles.stream().collect(Collectors.toMap(DeltaFile::getDid, Function.identity()));
        Map<UUID, DeltaFile> inputDidsToDeltaFiles = dids.stream().collect(LinkedHashMap::new, (map, did) -> map.put(did, deltaFilesMap.get(did)), HashMap::putAll);
        return resumeDeltaFiles(inputDidsToDeltaFiles, resumeMetadata);
    }

    @Transactional
    public List<RetryResult> resumeByFlowTypeAndName(FlowType flowType, String name, List<ResumeMetadata> resumeMetadata, boolean includeAcknowledged) {
        List<RetryResult> retryResults = new ArrayList<>();
        int numFound = REQUEUE_BATCH_SIZE;
        while (numFound == REQUEUE_BATCH_SIZE) {
            List<DeltaFile> deltaFiles = deltaFileRepo.findForResumeByFlowTypeAndName(flowType, name, includeAcknowledged, REQUEUE_BATCH_SIZE);
            numFound = deltaFiles.size();
            retryResults.addAll(this.resumeDeltaFiles(deltaFiles, resumeMetadata));
        }
        return retryResults;
    }

    @Transactional
    public List<RetryResult> resumeByErrorCause(String errorCause, List<ResumeMetadata> resumeMetadata, boolean includeAcknowledged) {
        List<RetryResult> retryResults = new ArrayList<>();
        int numFound = REQUEUE_BATCH_SIZE;
        while (numFound == REQUEUE_BATCH_SIZE) {
            List<DeltaFile> deltaFiles = deltaFileRepo.findForResumeByErrorCause(errorCause, includeAcknowledged, REQUEUE_BATCH_SIZE);
            numFound = deltaFiles.size();
            retryResults.addAll(this.resumeDeltaFiles(deltaFiles, resumeMetadata));
        }
        return retryResults;
    }

    public List<RetryResult> resumeDeltaFiles(@NotNull List<DeltaFile> deltaFiles, List<ResumeMetadata> resumeMetadata) {
        return resumeDeltaFiles(deltaFiles.stream().collect(Collectors.toMap(DeltaFile::getDid, d -> d)), resumeMetadata);
    }

    public List<RetryResult> resumeDeltaFiles(@NotNull Map<UUID, DeltaFile> deltaFiles, List<ResumeMetadata> resumeMetadata) {
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
                                deltaFile.wireBackPointers();
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

        if (!advanceAndSaveInputs.isEmpty()) {
            // force the cache to persist the deltaFile
            advanceAndSaveInputs.stream()
                    .map(StateMachineInput::deltaFile)
                    .forEach(deltaFile -> deltaFile.setCacheTime(OffsetDateTime.MAX));

            advanceAndSave(advanceAndSaveInputs, false);
        }

        return retryResults;
    }

    public List<RetryResult> replay(@NotNull DeltaFilesFilter filter, List<String> removeSourceMetadata, List<KeyValue> replaceSourceMetadata)  {
        // check for filters set to values that would return DeltaFiles that cannot be replayed
        if (Boolean.TRUE.equals(filter.getContentDeleted()) || Boolean.TRUE.equals(filter.getReplayed()) || Boolean.FALSE.equals(filter.getReplayable())) {
            return List.of();
        }

        // make sure only replayable DeltaFiles are pulled back
        filter.setReplayable(true);
        ensureModifiedBeforeNow(filter);

        List<RetryResult> retryResults = new ArrayList<>();

        int numFound = REQUEUE_BATCH_SIZE;
        while (numFound == REQUEUE_BATCH_SIZE) {
            Map<UUID, DeltaFile> toReplay = deltaFileRepo.deltaFiles(filter, REQUEUE_BATCH_SIZE)
                    .stream().collect(Collectors.toMap(DeltaFile::getDid, d -> d));
            retryResults.addAll(replay(toReplay, removeSourceMetadata, replaceSourceMetadata));
            numFound = toReplay.size();
        }

        return retryResults;
    }

    public List<RetryResult> replay(@NotNull List<UUID> dids, List<String> removeSourceMetadata, List<KeyValue> replaceSourceMetadata)  {
        Map<UUID, DeltaFile> deltaFileMap = didsToDeltaFiles(dids, deltaFiles(dids));
        return replay(deltaFileMap, removeSourceMetadata, replaceSourceMetadata);
    }

    public List<RetryResult> replay(@NotNull Map<UUID, DeltaFile> deltaFiles, List<String> removeSourceMetadata, List<KeyValue> replaceSourceMetadata)  {
        List<StateMachineInput> inputs = new ArrayList<>();
        List<DeltaFile> parents = new ArrayList<>();

        List<RetryResult> results = deltaFiles.entrySet().stream()
                .map(entry -> {
                    UUID did = entry.getKey();
                    DeltaFile deltaFile = entry.getValue();
                    RetryResult result = RetryResult.newBuilder()
                            .did(did)
                            .success(true)
                            .build();

                    try {
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
                            Flow flowConfig = getFlowConfig(firstFlow);

                            DeltaFileFlow flow = DeltaFileFlow.builder()
                                    .flowDefinition(firstFlow.getFlowDefinition())
                                    .state(flowConfig.isPaused() ? DeltaFileFlowState.PAUSED : DeltaFileFlowState.COMPLETE)
                                    .created(now)
                                    .modified(now)
                                    .testMode(firstFlow.getType().isDataSource() ? flowConfig.isTestMode() : firstFlow.isTestMode())
                                    .build();
                            if (flow.isTestMode()) {
                                flow.setTestModeReason(firstFlow.getType().isDataSource() ? firstFlow.getName() : firstFlow.getTestModeReason());
                            }

                            List<UUID> parentDids = new ArrayList<>(List.of(deltaFile.getDid()));
                            Action replayAction;
                            if (deltaFile.getJoinId() != null) {
                                setNextActionsInAggregateFlow(flow, firstFlow);
                                replayAction = flow.addAction(REPLAY_ACTION_NAME, null, ActionType.TRANSFORM, ActionState.COMPLETE, now);
                                parentDids.addAll(deltaFile.getParentDids());
                            } else {
                                Action startFromAction = findFirstActionUpdateFlow(flow, firstFlow, now);
                                flow.getActions().add(startFromAction);

                                List<Content> content = startFromAction.getContent();
                                replayAction = flow.addAction(REPLAY_ACTION_NAME, startFromAction.getActionClass(), startFromAction.getType(), ActionState.COMPLETE, now);
                                replayAction.setContent(content);
                            }

                            if (removeSourceMetadata != null && !removeSourceMetadata.isEmpty()) {
                                replayAction.setDeleteMetadataKeys(removeSourceMetadata);
                            }
                            if (replaceSourceMetadata != null && !replaceSourceMetadata.isEmpty()) {
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

                            child.updateFlags();
                            child.recalculateBytes();
                            child.wireBackPointers();
                            analyticEventService.recordIngress(child.getDid(), child.getCreated(), child.getDataSource(), deltaFile.firstFlow().getType(),
                                    child.getReferencedBytes(), Annotation.toMap(child.getAnnotations()), AnalyticIngressTypeEnum.CHILD);

                            if (flow.getState() == DeltaFileFlowState.PAUSED) {
                                deltaFileCacheService.save(child);
                            } else {
                                inputs.add(new StateMachineInput(child, flow));
                            }

                            deltaFile.setReplayed(now);
                            deltaFile.setReplayDid(child.getDid());
                            if (deltaFile.getChildDids() == null) {
                                deltaFile.setChildDids(new ArrayList<>());
                            } else {
                                deltaFile.setChildDids(new ArrayList<>(deltaFile.getChildDids()));
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
        if (flow.getState() != DeltaFileFlowState.PAUSED) {
            flow.setState(nextActions.isEmpty() ? DeltaFileFlowState.COMPLETE : DeltaFileFlowState.IN_FLIGHT);
        }

        if (startFromAction == null) {
            throw new IllegalStateException("No start action found to inherit for dataSource " + flow.getName() + " where replay should begin");
        }
        return Action.builder()
                .name(startFromAction.getName())
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
        } else if (firstFlow.getType() == FlowType.DATA_SINK) {
            DataSink flowConfig = dataSinkService.getFlowOrThrow(firstFlow.getName());
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

                        if (deltaFile == null) {
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

    public List<CancelResult> cancel(DeltaFilesFilter filter) {
        // if the stage is not null or IN_FLIGHT there is nothing to cancel
        if ((filter.getStage() != null && filter.getStage() != DeltaFileStage.IN_FLIGHT)) {
            return List.of();
        }

        // make sure only IN_FLIGHT stage is set in the filter
        filter.setStage(DeltaFileStage.IN_FLIGHT);
        ensureModifiedBeforeNow(filter);

        List<CancelResult> cancelResults = new ArrayList<>();

        int numFound = REQUEUE_BATCH_SIZE;
        while (numFound == REQUEUE_BATCH_SIZE) {
            Map<UUID, DeltaFile> toCancel = deltaFileRepo.deltaFiles(filter, REQUEUE_BATCH_SIZE)
                            .stream().collect(Collectors.toMap(DeltaFile::getDid, d->d));
            cancelResults.addAll(cancel(toCancel));
            numFound = toCancel.size();
        }

        return cancelResults;
    }

    public List<CancelResult> cancel(List<UUID> dids) {
        return cancel(didsToDeltaFiles(dids, deltaFiles(dids)));
    }

    public List<CancelResult> cancel(Map<UUID, DeltaFile> deltaFiles) {
        List<DeltaFile> changedDeltaFiles = new ArrayList<>();

        List<CancelResult> results = deltaFiles.entrySet().stream()
                .map(entry -> {
                    UUID did = entry.getKey();
                    DeltaFile deltaFile = entry.getValue();
                    CancelResult result = CancelResult.newBuilder()
                            .did(did)
                            .success(true)
                            .build();

                    try {
                        if (deltaFile == null) {
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
                                analyticEventService.recordCancel(deltaFile);
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

    public List<PinResult> setPinned(DeltaFilesFilter filter, boolean pinned) {
        // if the stage is not null or COMPLETE there is nothing to pin or unpin
        // if the filter includes pinned and the new value is the same there is nothing to do
        if ((filter.getStage() != null && filter.getStage() != DeltaFileStage.COMPLETE) ||
                (filter.getPinned() != null && filter.getPinned() == pinned)) {
            return List.of();
        }

        // make sure the stage is set to COMPLETE
        filter.setStage(DeltaFileStage.COMPLETE);
        // only return DeltaFiles where pinned is different from the new value
        filter.setPinned(!pinned);
        // make sure the query is capped by a modified before date
        ensureModifiedBeforeNow(filter);

        List<PinResult> pinResults = new ArrayList<>();

        int numFound = REQUEUE_BATCH_SIZE;
        while (numFound == REQUEUE_BATCH_SIZE) {
            List<DeltaFile> toSetPin = deltaFileRepo.deltaFiles(filter, REQUEUE_BATCH_SIZE);

            for (DeltaFile deltaFile : toSetPin) {
                deltaFile.setPinned(pinned);
                pinResults.add(PinResult.newBuilder().did(deltaFile.getDid()).success(true).build());
            }

            deltaFileRepo.saveAll(toSetPin);
            numFound = toSetPin.size();
        }

        return pinResults;
    }

    public List<Result> pin(List<UUID> dids) {
        return dids.stream().map(this::pin).toList();
    }

    private Result pin(UUID did) {
        return setPinned(did, true);
    }

    public List<Result> unpin(List<UUID> dids) {
        return dids.stream().map(this::unpin).toList();
    }

    private Result unpin(UUID did) {
        return setPinned(did, false);
    }

    private Result setPinned(UUID did, boolean pinned) {
        DeltaFile deltaFile = getDeltaFile(did);
        if (deltaFile == null) {
            return Result.builder().success(false).info(List.of(String.format("DeltaFile with did %s doesn't exist", did))).build();
        }
        if (deltaFile.getStage() != DeltaFileStage.COMPLETE) {
            return Result.builder().success(false).info(List.of(String.format("DeltaFile with did %s hasn't completed", did))).build();
        }
        deltaFile.setPinned(pinned);
        deltaFileRepo.save(deltaFile);
        return Result.successResult();
    }

    public List<PerActionUniqueKeyValues> errorMetadataUnion(List<UUID> dids) {
        // TODO: limit fields returned
        List<DeltaFile> deltaFiles = deltaFileRepo.findByIdsIn(dids);

        Map<Pair<String, String>, PerActionUniqueKeyValues> actionKeyValues = new HashMap<>();
        deltaFiles.stream()
                .map(DeltaFile::getFlows)
                .flatMap(Collection::stream)
                .filter(flow -> flow.getState() == DeltaFileFlowState.ERROR && flow.lastAction().getType() != ActionType.UNKNOWN && flow.lastAction().getState() == ActionState.ERROR)
                .forEach(flow -> {
                    Action action = flow.lastAction();
                    if (!actionKeyValues.containsKey(Pair.of(flow.getName(), action.getName()))) {
                        actionKeyValues.put(Pair.of(flow.getName(), action.getName()), new PerActionUniqueKeyValues(flow.getName(), action.getName()));
                    }
                    flow.getMetadata()
                            .forEach((key, value) -> actionKeyValues.get(Pair.of(flow.getName(), action.getName())).addValue(key, value));
                });

        return new ArrayList<>(actionKeyValues.values());
    }

    public List<UniqueKeyValues> sourceMetadataUnion(List<UUID> dids) {
        // TODO: limit fields returned
        List<DeltaFileFlow> deltaFileFlows = deltaFileFlowRepo.findAllByDeltaFileIdsAndFlowZero(dids);

        Map<String, UniqueKeyValues> keyValues = new HashMap<>();
        deltaFileFlows.stream()
                .map(DeltaFileFlow::getMetadata)
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
            deltaFileRepo.insertBatch(new ArrayList<>(deltaFiles), deltaFiPropertiesService.getDeltaFiProperties().getInsertBatchSize());
        } else {
            deltaFileCacheService.saveAll(deltaFiles);
        }
        enqueueActions(actionInputs);
    }

    void handleMissingFlow(DeltaFile deltaFile, DeltaFileFlow flow, MissingFlowException missingFlowException) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        Action action = flow.queueNewAction(MISSING_FLOW_ACTION, null, ActionType.UNKNOWN, false, now);
        processErrorEvent(deltaFile, flow, action, buildMissingFlowErrorEvent(deltaFile, now, missingFlowException));
        deltaFileCacheService.save(deltaFile);
    }

    public void deleteContentAndMetadata(UUID did, Content content) {
        try {
            deltaFileRepo.deleteById(did);
        } catch (Exception e) {
            log.error("Failed to remove the metadata for did {}", did, e);
        }

        try {
            if (isLocalStorage()) {
                localContentStorageService.deleteContent(List.of(did), false);
            } else {
                contentStorageService.delete(content);
            }
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
     * @param batchSize        maximum size of the batch to delete
     * @return                 true if there are more DeltaFiles to delete, false otherwise
     */
    public boolean timedDelete(OffsetDateTime createdBefore, OffsetDateTime completedBefore, long minBytes, String flow,
                               String policy, boolean deleteMetadata, int batchSize, boolean ordered) {
        int alreadyDeleted = 0;

        logBatch(batchSize, policy);

        if (deleteMetadata) {
            alreadyDeleted = deltaFileRepo.deleteIfNoContent(createdBefore, completedBefore, minBytes, flow, batchSize, ordered);

            if (alreadyDeleted > 0) {
                log.info("Deleted {} deltaFiles with no content for policy {}", alreadyDeleted, policy);
            }
            if (alreadyDeleted == batchSize) {
                return true;
            }
        }

        List<DeltaFileDeleteDTO> deltaFiles = deltaFileRepo.findForTimedDelete(
                createdBefore, completedBefore, minBytes, flow, deleteMetadata,
                policy.equals(TTL_SYSTEM_POLICY), batchSize - alreadyDeleted, !isLocalStorage(), ordered);
        delete(deltaFiles, policy, deleteMetadata, alreadyDeleted, false);

        return deltaFiles.size() == batchSize;
    }

    public List<DeltaFileDeleteDTO> diskSpaceDelete(long bytesToDelete, int batchSize) {
        logBatch(batchSize, DiskSpaceDelete.POLICY_NAME);
        return delete(deltaFileRepo.findForDiskSpaceDelete(bytesToDelete, batchSize, !isLocalStorage()), DiskSpaceDelete.POLICY_NAME, false, 0, true);
    }

    public void logBatch(int batchSize, String policy) {
        log.info("Searching for batch of up to {} deltaFiles to delete for policy {}", batchSize, policy);
    }

    private List<DeltaFileDeleteDTO> delete(List<DeltaFileDeleteDTO> deltaFiles, String policy, boolean deleteMetadata, int alreadyDeleted, boolean blockUntilDeleted) {
        if (deltaFiles.isEmpty()) {
            log.info("No deltaFiles found to delete for policy {}", policy);
            if (alreadyDeleted > 0) {
                metricService.increment(new Metric(DELETED_FILES, alreadyDeleted).addTag("policy", policy));
            }
            return deltaFiles;
        }

        log.info("Deleting {} deltaFiles for policy {}", deltaFiles.size(), policy);
        long totalBytes = deltaFiles.stream().filter(d -> d.getContentDeleted() == null).mapToLong(DeltaFileDeleteDTO::getTotalBytes).sum();

        deleteContent(deltaFiles, policy, deleteMetadata, blockUntilDeleted);
        metricService.increment(new Metric(DELETED_FILES, deltaFiles.size() + alreadyDeleted).addTag("policy", policy));
        metricService.increment(new Metric(DELETED_BYTES, totalBytes).addTag("policy", policy));
        log.info("Finished deleting {} deltaFiles for policy {}", deltaFiles.size(), policy);

        return deltaFiles;
    }

    private void deleteContent(List<DeltaFileDeleteDTO> deltaFiles, String policy, boolean deleteMetadata, boolean blockUntilDeleted) {
        List<DeltaFileDeleteDTO> deltaFilesWithContent = deltaFiles.stream().filter(d -> d.getContentDeleted() == null).toList();
        if (isLocalStorage()) {
            localContentStorageService.deleteContent(deltaFilesWithContent.stream().map(DeltaFileDeleteDTO::getDid).toList(), blockUntilDeleted);
        } else {
            contentStorageService.deleteAllByObjectName(deltaFilesWithContent.stream()
                    .flatMap(d -> d.getContentObjectIds().stream()
                            .map(contentId -> Segment.objectName(d.getDid(), contentId))
                    )
                    .toList());
        }

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
                            if (flow.getActions().isEmpty()) {
                                StateMachineInput stateMachineInput = new StateMachineInput(deltaFile, flow);
                                actionInputs.addAll(stateMachine.advance(List.of(stateMachineInput)));
                                log.warn("Requeued malformed DeltaFile {} which had a flow with no actions.", deltaFile.getDid());
                            } else {
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
                            }
                        });
            });

            if (!actionInputs.isEmpty()) {
                log.warn("{} actions exceeded requeue threshold of {} seconds, requeuing now", actionInputs.size(), getProperties().getRequeueDuration());
                deltaFileRepo.saveAll(filesToRequeue);
                enqueueActions(actionInputs, true);
            }
        }
    }

    public void requeueColdQueueActions(String actionClass, int maxFiles) {
        OffsetDateTime modified = OffsetDateTime.now(clock);
        List<DeltaFile> filesToRequeue = deltaFileRepo.findColdQueuedForRequeue(actionClass, maxFiles);

        List<WrappedActionInput> actionInputs = new ArrayList<>();
        filesToRequeue.forEach(deltaFile -> {
            deltaFile.setRequeueCount(deltaFile.getRequeueCount() + 1);
            deltaFile.setModified(modified);
            deltaFile.getFlows().stream()
                    .filter(f -> f.getState() == DeltaFileFlowState.IN_FLIGHT)
                    .forEach(flow -> {
                        Action action = flow.lastAction();
                        if ((action.getState() == COLD_QUEUED) && actionClass.equals(action.getActionClass())) {
                            WrappedActionInput actionInput = requeueActionInput(deltaFile, flow, action);
                            if (actionInput != null) {
                                actionInput.setColdQueued(false);
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
            log.info("Moving {} from the cold to warm queue", actionInputs.size());
            deltaFileRepo.saveAll(filesToRequeue);
            enqueueActions(actionInputs, true);
        }
    }

    public void requeuePausedFlows() {
        Integer numFound = null;
        while (numFound == null || numFound == REQUEUE_BATCH_SIZE) {
            Map<FlowType, Set<String>> pausedFlows = Map.of(
                    FlowType.REST_DATA_SOURCE, restDataSourceService.getPausedFlows().stream().map(Flow::getName).collect(Collectors.toSet()),
                    FlowType.TIMED_DATA_SOURCE, timedDataSourceService.getPausedFlows().stream().map(Flow::getName).collect(Collectors.toSet()),
                    FlowType.TRANSFORM, transformFlowService.getPausedFlows().stream().map(Flow::getName).collect(Collectors.toSet()),
                    FlowType.DATA_SINK, dataSinkService.getPausedFlows().stream().map(Flow::getName).collect(Collectors.toSet())
            );

            OffsetDateTime modified = OffsetDateTime.now(clock);
            List<DeltaFile> filesToRequeue = deltaFileRepo.findPausedForRequeue(
                    pausedFlows.get(FlowType.REST_DATA_SOURCE),
                    pausedFlows.get(FlowType.TIMED_DATA_SOURCE),
                    pausedFlows.get(FlowType.TRANSFORM),
                    pausedFlows.get(FlowType.DATA_SINK),
                    REQUEUE_BATCH_SIZE);
            numFound = filesToRequeue.size();

            List<StateMachineInput> inputs = new ArrayList<>();
            filesToRequeue.forEach(deltaFile -> {
                deltaFile.setRequeueCount(deltaFile.getRequeueCount() + 1);
                deltaFile.setModified(modified);
                inputs.addAll(deltaFile.getFlows().stream()
                        .filter(f -> f.getState() == DeltaFileFlowState.PAUSED &&
                                !pausedFlows.get(f.getType()).contains(f.getName()))
                        .map(f -> {
                            f.setState(DeltaFileFlowState.IN_FLIGHT);
                            return new StateMachineInput(deltaFile, f);
                        })
                        .toList());
            });

            if (!inputs.isEmpty()) {
                log.info("Unpausing {} DeltaFile flows", inputs.size());
                List<WrappedActionInput> actionInputs = stateMachine.advance(inputs);
                deltaFileRepo.saveAll(filesToRequeue);
                enqueueActions(actionInputs, false);
            }
        }
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
        autoResume(OffsetDateTime.now(clock), REQUEUE_BATCH_SIZE);
    }

    public int autoResume(OffsetDateTime timestamp, int batchSize) {
        int queued = 0;
        int currentBatchSize = batchSize;
        while (currentBatchSize == batchSize) {
            Map<String, Integer> countByFlow = new HashMap<>();
            List<DeltaFile> autoResumeDeltaFiles = deltaFileRepo.findReadyForAutoResume(timestamp, batchSize);
            currentBatchSize = autoResumeDeltaFiles.size();
            if (!autoResumeDeltaFiles.isEmpty()) {
                Map<UUID, String> flowByDid = autoResumeDeltaFiles.stream()
                        .collect(Collectors.toMap(DeltaFile::getDid, DeltaFile::getDataSource));
                List<RetryResult> results = resumeDeltaFiles(autoResumeDeltaFiles, Collections.emptyList());
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

                log.info("Queued batch of {} DeltaFiles for auto-resume", currentBatchSize);
                generateMetricsByName(FILES_AUTO_RESUMED, countByFlow);
            }
        }
        if (queued > 0) {
            log.info("Queued total of {} DeltaFiles for auto-resume", queued);
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

    private Flow getFlowConfig(DeltaFileFlow deltaFileFlow) {
        return switch (deltaFileFlow.getType()) {
            case REST_DATA_SOURCE -> restDataSourceService.getFlowOrThrow(deltaFileFlow.getName());
            case TIMED_DATA_SOURCE -> timedDataSourceService.getFlowOrThrow(deltaFileFlow.getName());
            case TRANSFORM -> transformFlowService.getFlowOrThrow(deltaFileFlow.getName());
            case DATA_SINK -> dataSinkService.getFlowOrThrow(deltaFileFlow.getName());
        };
    }

    private ActionConfiguration actionConfiguration(String flow, FlowType flowType, String actionName) {
        return switch (flowType) {
            case TIMED_DATA_SOURCE -> timedDataSourceService.findRunningActionConfig(flow, actionName);
            case TRANSFORM -> transformFlowService.findRunningActionConfig(flow, actionName);
            case DATA_SINK -> dataSinkService.findRunningActionConfig(flow, actionName);
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
                            deltaFileCacheService.remove(event.getDid());
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
            List<WrappedActionInput> toQueue = finalizeInput(actionInputs);
            coreEventQueue.putActions(toQueue, checkUnique);
        } catch (Exception e) {
            log.error("Failed to queue action(s)", e);
            throw new EnqueueActionException("Failed to queue action(s)", e);
        }
    }

    // populate joined content, only return actionInput where batched input was successfully populated

    /**
     * Populate joined content if necessary. Attempt to resolve any parameters that include templates.
     * Any invalid input will be filtered out. DeltaFiles that were part of invalid input are marked errored
     * and persisted.
     * @param actionInputs to populate and attempt to resolve parameters
     * @return the list of valid ActionInput that should be added to the queue
     */
    private List<WrappedActionInput> finalizeInput(List<WrappedActionInput> actionInputs) {
        List<WrappedActionInput> filteredList = new ArrayList<>();
        for (WrappedActionInput actionInput : actionInputs) {
            try {
                populateBatchInput(actionInput);
                parameterResolver.resolve(actionInput);
                filteredList.add(actionInput);
            } catch (MissingDeltaFilesException e) {
                handleMissingParent(actionInput, e.getMessage());
            } catch (ParameterTemplateException e) {
                handleParamResolverError(actionInput, ParameterUtil.toErrorContext(e, actionInput.getActionParams()));
            }
        }
        return filteredList;
    }

    private void handleMissingParent(WrappedActionInput input, String errorContext) {
        // pull latest version to avoid StaleObject exception
        DeltaFile deltaFile = deltaFileRepo.findById(input.getDeltaFile().getDid()).orElse(null);
        if (deltaFile == null) {
            return;
        }
        DeltaFileFlow deltaFileFlow = deltaFile.getFlow(input.getActionContext().getFlowId());
        Action failedAction = deltaFileFlow.getAction(input.getActionContext().getActionName());

        OffsetDateTime now = OffsetDateTime.now(clock);
        failedAction.error(now, now, now, "Missing one or more parent DeltaFiles", errorContext);
        deltaFileFlow.updateState();
        deltaFile.updateState(now);

        deltaFileCacheService.remove(deltaFile.getDid());
        deltaFileRepo.saveAndFlush(deltaFile);
    }

    private void handleParamResolverError(WrappedActionInput input, String errorContext) {
        // use the cache service to get the latest (this will handle pulling from the repo is needed for joins)
        DeltaFile deltaFile = deltaFileCacheService.get(input.getDeltaFile().getDid());
        if (deltaFile == null) {
            return;
        }
        DeltaFileFlow deltaFileFlow = deltaFile.getFlow(input.getActionContext().getFlowId());
        Action failedAction = deltaFileFlow.getAction(input.getActionContext().getActionName());

        OffsetDateTime now = OffsetDateTime.now(clock);
        failedAction.error(now, now, now, "Unable to resolve templated action parameter", errorContext);
        deltaFileFlow.updateState();
        deltaFile.updateState(now);

        deltaFileCacheService.remove(deltaFile.getDid());
        deltaFileRepo.saveAndFlush(deltaFile);
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
                            Action tmpAction = tmpFlow.addAction("tmp", null, ActionType.TRANSFORM, ActionState.COMPLETE, OffsetDateTime.now(clock));
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
            dataSource = timedDataSourceService.getActiveFlowByName(flowName);
        }

        return taskTimedDataSource(dataSource);
    }

    public boolean taskTimedDataSource(TimedDataSource dataSource) throws EnqueueActionException {
        WrappedActionInput actionInput = dataSource.buildActionInput(getProperties().getSystemName(),
                OffsetDateTime.now(clock), identityService.getUniqueId(),
                flowDefinitionService.getOrCreateFlow(dataSource.getName(), FlowType.TIMED_DATA_SOURCE));

        try {
            if (!coreEventQueue.queueHasTaskingForAction(actionInput)) {
                timedDataSourceService.setLastRun(dataSource.getName(), OffsetDateTime.now(clock),
                        actionInput.getActionContext().getDid());
                enqueueActions(List.of(actionInput), false);
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

    public SummaryByFlow getErrorSummaryByFlow(Integer offset, Integer limit, ErrorSummaryFilter filter, DeltaFileDirection direction, SummaryByFlowSort sortField) {
        return deltaFileFlowRepo.getErrorSummaryByFlow(offset,
                (limit != null && limit > 0) ? limit : DEFAULT_QUERY_LIMIT,
                filter,
                direction != null ? direction : DeltaFileDirection.ASC,
                sortField != null ? sortField : SummaryByFlowSort.NAME);
    }

    public SummaryByFlowAndMessage getErrorSummaryByMessage(Integer offset, Integer limit, ErrorSummaryFilter filter, DeltaFileDirection direction, SummaryByMessageSort sortField) {
        return deltaFileFlowRepo.getErrorSummaryByMessage(offset,
                (limit != null && limit > 0) ? limit : DEFAULT_QUERY_LIMIT,
                filter,
                direction != null ? direction : DeltaFileDirection.ASC,
                sortField != null ? sortField : SummaryByMessageSort.NAME);
    }

    public SummaryByFlow getFilteredSummaryByFlow(Integer offset, Integer limit, FilteredSummaryFilter filter, DeltaFileDirection direction, SummaryByFlowSort sortField) {
        return deltaFileFlowRepo.getFilteredSummaryByFlow(offset,
                (limit != null && limit > 0) ? limit : DEFAULT_QUERY_LIMIT,
                filter,
                direction != null ? direction : DeltaFileDirection.ASC,
                sortField != null ? sortField : SummaryByFlowSort.NAME);
    }

    public SummaryByFlowAndMessage getFilteredSummaryByMessage(Integer offset, Integer limit, FilteredSummaryFilter filter, DeltaFileDirection direction, SummaryByMessageSort sortField) {
        return deltaFileFlowRepo.getFilteredSummaryByMessage(offset,
                (limit != null && limit > 0) ? limit : DEFAULT_QUERY_LIMIT,
                filter,
                direction != null ? direction : DeltaFileDirection.ASC,
                sortField != null ? sortField : SummaryByMessageSort.NAME);
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

                List<DeltaFile> deltaFiles = resumePolicyService.canBeApplied(resumePolicy, checkFiles, previousDids);
                if (deltaFiles.isEmpty()) {
                    information.add("No DeltaFile errors can be resumed by policy " + resumePolicy.getName());
                } else {
                    OffsetDateTime nextResume = now.plusSeconds(resumePolicy.getBackOff().getDelay());
                    deltaFiles.stream()
                            .map(DeltaFile::erroredFlows)
                            .flatMap(List::stream)
                            .forEach(deltaFileFlow -> deltaFileFlow.enableAutoResume(nextResume, resumePolicy.getName()));
                    deltaFileRepo.saveAll(deltaFiles);
                    previousDids.addAll(deltaFiles.stream().map(DeltaFile::getDid).toList());
                    information.add("Applied " + resumePolicy.getName() + " policy to " + deltaFiles.size() + " DeltaFiles");
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

            List<DeltaFile> deltaFiles = resumePolicyService.canBeApplied(resumePolicy, checkFiles, Collections.emptySet());
            if (deltaFiles.isEmpty()) {
                information.add("No DeltaFile errors can be resumed by policy " + resumePolicy.getName());
            } else {
                information.add("Can apply " + resumePolicy.getName() + " policy to " + deltaFiles.size() + " DeltaFiles");
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

    public List<UUID> queueTimedOutJoin(JoinEntry joinEntry, List<UUID> joinDids) {
        ActionConfiguration actionConfiguration = actionConfiguration(joinEntry.getJoinDefinition().getFlow(), joinEntry.getJoinDefinition().getActionType() == ActionType.TRANSFORM ? FlowType.TRANSFORM : FlowType.DATA_SINK,
                joinEntry.getJoinDefinition().getAction());

        if (actionConfiguration == null) {
            String reason = "Time-based join action couldn't run because action %s in transform %s is no longer running"
                    .formatted(joinEntry.getJoinDefinition().getAction(), joinEntry.getJoinDefinition().getFlow());
            log.warn(reason);
            return handleQueueTimedJoinError(joinEntry, joinDids, reason);
        }

        UUID lastParent = joinDids.getLast();
        DeltaFile parent = getDeltaFile(lastParent);
        DeltaFileFlow deltaFileFlow = parent != null ? parent.getFlows().stream().filter(flow -> joinEntry.getId().equals(flow.getJoinId())).findFirst().orElse(null) : null;
        if (deltaFileFlow == null) {
            log.warn("Time-based join action couldn't run because a dataSource with a joinId of {} was not found in the parent with a did of {}. Failed executing action {} from dataSource dataSource {}",
                    joinEntry.getId(), lastParent, joinEntry.getJoinDefinition().getAction(), joinEntry.getJoinDefinition().getFlow());
            String reason = "Timed-based join action couldn't run due to an invalid parent state (parent with did %s was missing the join flow)".formatted(lastParent);
            log.warn(reason);
            return handleQueueTimedJoinError(joinEntry, joinDids, reason);
        }

        WrappedActionInput input = DeltaFileUtil.createAggregateInput(actionConfiguration, deltaFileFlow, joinEntry, joinDids, ActionState.QUEUED, getProperties().getSystemName(),  null);

        enqueueActions(List.of(input));
        return joinDids;
    }

    private List<UUID> handleQueueTimedJoinError(JoinEntry joinEntry, List<UUID> joinDids, String reason) {
        List<DeltaFile> deltaFiles = deltaFileCacheService.get(joinDids);
        deltaFiles.forEach(deltaFile -> deltaFile.errorJoinAction(joinEntry.getId(), joinEntry.getJoinDefinition().getAction(), OffsetDateTime.now(clock), reason));
        deltaFileCacheService.saveAll(deltaFiles);
        return joinDids;
    }

    public List<UUID> failTimedOutJoin(JoinEntry joinEntry, List<UUID> joinedDids, String reason) {
        log.debug("Failing join action");

        List<UUID> processed = new ArrayList<>();

        for (UUID did : joinedDids) {
            try {
                DeltaFile deltaFile = deltaFileRepo.findById(did).orElse(null);
                if (deltaFile == null) {
                    continue;
                }

                deltaFile.errorJoinAction(joinEntry.getId(), joinEntry.getJoinDefinition().getAction(),  OffsetDateTime.now(clock), reason);
                deltaFileRepo.save(deltaFile);
                processed.add(did);
            } catch (OptimisticLockingFailureException e) {
                log.warn("Unable to save DeltaFile with failed join action", e);
            }
        }

        return processed;
    }

    public List<UUID> handleOrphanedJoins(List<JoinEntryDid> orphans) {
        List<DeltaFile> updatedDeltaFiles = new ArrayList<>();
        List<UUID> completedJoinIds = new ArrayList<>();
        Map<UUID, DeltaFile> deltaFiles = deltaFiles(orphans.stream().map(JoinEntryDid::getDid).toList());
        for (JoinEntryDid orphan : orphans) {
            DeltaFile deltaFile = deltaFiles.get(orphan.getDid());
            if (deltaFile == null) {
                continue;
            }

            deltaFile.errorJoinAction(orphan.getJoinEntryId(), orphan.getActionName(), OffsetDateTime.now(clock), orphan.getErrorReason());
            updatedDeltaFiles.add(deltaFile);
            completedJoinIds.add(orphan.getId());
        }
        deltaFileRepo.saveAll(updatedDeltaFiles);
        return completedJoinIds;
    }

    private void completeJoin(ActionEvent event, DeltaFile deltaFile, Action action, ActionConfiguration actionConfiguration) {
        if ((actionConfiguration != null) && (actionConfiguration.getJoin() != null)) {
            List<DeltaFile> parentDeltaFiles = deltaFileRepo.findAllById(deltaFile.getParentDids());
            OffsetDateTime now = OffsetDateTime.now(clock);
            for (DeltaFile parentDeltaFile : parentDeltaFiles) {
                if (parentDeltaFile.getChildDids() == null) {
                    parentDeltaFile.setChildDids(new ArrayList<>());
                } else {
                    parentDeltaFile.setChildDids(new ArrayList<>(parentDeltaFile.getChildDids()));
                }
                parentDeltaFile.getChildDids().add(deltaFile.getDid());
                parentDeltaFile.joinedAction(event.getDid(), action.getName(), event.getStart(), event.getStop(), now);
            }
            deltaFileRepo.saveAll(parentDeltaFiles);
            deltaFile.recalculateBytes();
            analyticEventService.recordIngress(deltaFile.getDid(), deltaFile.getCreated(), deltaFile.getDataSource(), deltaFile.firstFlow().getType(),
                    deltaFile.getReferencedBytes(), Annotation.toMap(deltaFile.getAnnotations()), AnalyticIngressTypeEnum.CHILD);
        }
    }

    private Map<UUID, DeltaFile> didsToDeltaFiles(List<UUID> dids, Map<UUID, DeltaFile> deltaFiles) {
        return dids.stream()
                .collect(LinkedHashMap::new,
                        (map, did) -> map.put(did, deltaFiles.get(did)),
                        HashMap::putAll);
    }

    void ensureModifiedBeforeNow(DeltaFilesFilter filter) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (filter.getModifiedBefore() == null || filter.getModifiedBefore().isAfter(now)) {
            filter.setModifiedBefore(now);
        }
    }

    public int completeParents() {
        return deltaFileRepo.completeParents();
    }

    private static class Counter {
        int filteredFiles = 0;
        int erroredFiles = 0;
        long byteCount = 0L;
    }

    private boolean isLocalStorage() {
        return environment.matchesProfiles("localContentStorage");
    }
}
