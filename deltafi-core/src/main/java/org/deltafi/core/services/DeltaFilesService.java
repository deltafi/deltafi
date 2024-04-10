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
import org.deltafi.common.types.*;
import org.deltafi.common.types.Egress;
import org.deltafi.common.uuid.UUIDGenerator;
import org.deltafi.core.audit.CoreAuditLogger;
import org.deltafi.core.collect.CollectEntry;
import org.deltafi.core.collect.CollectingActionInvocation;
import org.deltafi.core.collect.ScheduledCollectService;
import org.deltafi.core.configuration.DeltaFiProperties;
import org.deltafi.core.exceptions.*;
import org.deltafi.core.generated.types.*;
import org.deltafi.core.metrics.MetricService;
import org.deltafi.core.metrics.MetricsUtil;
import org.deltafi.core.repo.DeltaFileRepo;
import org.deltafi.core.repo.QueuedAnnotationRepo;
import org.deltafi.core.retry.MongoRetryable;
import org.deltafi.core.services.pubsub.PublisherService;
import org.deltafi.common.types.Publisher;
import org.deltafi.common.types.Subscriber;
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

    public static final String CHILD_FLOW_INGRESS_DISABLED_CAUSE = "Child ingress flow disabled";
    public static final String CHILD_FLOW_INGRESS_DISABLED_CONTEXT = "Child ingress flow disabled due to max errors exceeded: ";
    public static final String NO_EGRESS_CONFIGURED_CAUSE = "No egress flow configured";
    public static final String NO_EGRESS_CONFIGURED_CONTEXT = "This DeltaFile does not match the criteria of any running egress flows";
    public static final String NO_CHILD_INGRESS_CONFIGURED_CAUSE = "No child ingress flow configured";
    public static final String NO_CHILD_INGRESS_CONFIGURED_CONTEXT = "This DeltaFile reinject does not match any running ingress flows: ";

    private static final int DEFAULT_QUERY_LIMIT = 50;

    private final Clock clock;
    private final TransformFlowService transformFlowService;
    private final NormalizeFlowService normalizeFlowService;
    private final EnrichFlowService enrichFlowService;
    private final EgressFlowService egressFlowService;
    private final PublisherService publisherService;
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
    private final TimedIngressFlowService timedIngressFlowService;
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
            log.info("Executors pool size: " + threadCount);
            int internalQueueSize = properties.getCoreInternalQueueSize() > 0 ? properties.getCoreInternalQueueSize() : 64;
            semaphore = new Semaphore(internalQueueSize);
            log.info("Internal queue size: " + internalQueueSize);
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

        if (deltaFile.getPendingAnnotationsForFlows() == null || deltaFile.getPendingAnnotationsForFlows().isEmpty()) {
            return Set.of();
        }

        Set<String> allExpectedAnnotations = deltaFile.getPendingAnnotationsForFlows().stream()
                .map(flow -> getPendingAnnotations(deltaFile.getSourceInfo().getProcessingType(), flow))
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());


        return deltaFile.pendingAnnotations(allExpectedAnnotations);
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

    public DeltaFile getLastWithFilename(String filename) {
        PageRequest pageRequest = PageRequest.of(0, 1);
        List<DeltaFile> matches = deltaFileRepo.findBySourceInfoFilenameOrderByCreatedDesc(filename, pageRequest).getContent();
        return matches.isEmpty() ? null : matches.get(0);
    }

    public long countUnacknowledgedErrors() {
        return deltaFileRepo.countByStageAndErrorAcknowledgedIsNull(DeltaFileStage.ERROR);
    }

    public DeltaFile ingress(IngressEventItem ingressEventItem, OffsetDateTime ingressStartTime,
            OffsetDateTime ingressStopTime) {
        return ingress(ingressEventItem, Collections.emptyList(), ingressStartTime, ingressStopTime);
    }

    private DeltaFile buildIngressDeltaFile(IngressEventItem ingressEventItem, List<String> parentDids,
            OffsetDateTime ingressStartTime, OffsetDateTime ingressStopTime) {

        OffsetDateTime now = OffsetDateTime.now(clock);

        Action ingressAction = Action.builder()
                .name(INGRESS_ACTION)
                .flow(ingressEventItem.getFlow())
                .type(ActionType.INGRESS)
                .state(ActionState.COMPLETE)
                .created(ingressStartTime)
                .modified(now)
                .content(ingressEventItem.getContent())
                .metadata(ingressEventItem.getMetadata())
                .start(ingressStartTime)
                .stop(ingressStopTime)
                .build();

        long contentSize = ContentUtil.computeContentSize(ingressEventItem.getContent());

        return DeltaFile.builder()
                .schemaVersion(DeltaFile.CURRENT_SCHEMA_VERSION)
                .did(ingressEventItem.getDid())
                .parentDids(parentDids)
                .childDids(new ArrayList<>())
                .requeueCount(0)
                .ingressBytes(contentSize)
                .totalBytes(contentSize)
                .stage(DeltaFileStage.INGRESS)
                .inFlight(true)
                .actions(new ArrayList<>(List.of(ingressAction)))
                .sourceInfo(SourceInfo.builder()
                        .filename(ingressEventItem.getFilename())
                        .flow(ingressEventItem.getFlow())
                        .metadata(ingressEventItem.getMetadata())
                        .processingType(ingressEventItem.getProcessingType())
                        .build())
                .created(ingressStartTime)
                .modified(now)
                .egressed(false)
                .filtered(false)
                .build();
    }

    public DeltaFile ingress(IngressEventItem ingressEventItem, List<String> parentDids, OffsetDateTime ingressStartTime,
            OffsetDateTime ingressStopTime) {
        DeltaFile deltaFile = buildIngressDeltaFile(ingressEventItem, parentDids, ingressStartTime, ingressStopTime);

        advanceAndSave(deltaFile);
        return deltaFile;
    }

    public DeltaFile buildIngressDeltaFile(ActionEvent parentEvent, IngressEventItem ingressEventItem) {
        DeltaFile deltaFile = buildIngressDeltaFile(ingressEventItem, Collections.emptyList(), parentEvent.getStart(),
                parentEvent.getStop());
        deltaFile.lastAction().setName(parentEvent.getAction());
        deltaFile.lastAction().setFlow(parentEvent.getFlow());
        return deltaFile;
    }

    public DeltaFile ingressOrErrorOnMissingFlow(IngressEventItem ingressEventItem, String ingressActionName,
            String ingressFlow, OffsetDateTime ingressStartTime,
            OffsetDateTime ingressStopTime) {
        DeltaFile deltaFile = buildIngressDeltaFile(ingressEventItem, Collections.emptyList(), ingressStartTime,
                ingressStopTime);
        deltaFile.lastAction().setName(ingressActionName);
        deltaFile.lastAction().setFlow(ingressFlow);

        try {
            advanceAndSave(deltaFile);
        } catch (MissingFlowException e) {
            Action ingressAction = deltaFile.getActions().get(0);
            ingressAction.setState(ActionState.ERROR);
            ingressAction.setErrorCause(e.getMessage());
            deltaFile.setStage(DeltaFileStage.ERROR);
            deltaFileRepo.save(deltaFile);
        }
        return deltaFile;
    }

    public void handleActionEvent(ActionEvent event) throws JsonProcessingException {
        if (StringUtils.isEmpty(event.getDid())) {
            throw new InvalidActionEventException("Missing did: " + OBJECT_MAPPER.writeValueAsString(event));
        }
        if (StringUtils.isEmpty(event.getAction())) {
            throw new InvalidActionEventException("Missing action: " + OBJECT_MAPPER.writeValueAsString(event));
        }

        // To maintain compatibility with legacy actions, flow and name were combined in the name field of the
        // ActionContext sent to the action in the ActionInput (See enqueueActions()). Put them in their own fields.
        if (event.getAction().contains(".")) {
            String[] actionNameParts = event.getAction().split("\\.");
            if (actionNameParts.length < 2) {
                throw new InvalidActionEventException("Invalid action (" + event.getAction() + "). " +
                        "Expected flow.action: " + OBJECT_MAPPER.writeValueAsString(event));
            }
            event.setFlow(actionNameParts[0]);
            event.setAction(actionNameParts[1]);
        }

        if (event.getType() == ActionEventType.INGRESS) {
            handleIngressActionEvent(event);
        } else {
            handleProcessingActionEvent(event);
        }
    }

    public void handleIngressActionEvent(ActionEvent event) {
        String validationError = event.validate();
        if (validationError == null) {
            ingressFromAction(event);
        } else {
            log.error("Invalid ingress event received from {}: {}", event.getAction(), validationError);
        }
    }

    public void handleProcessingActionEvent(ActionEvent event) throws JsonProcessingException {
        synchronized (didMutexService.getMutex(event.getDid())) {
            DeltaFile deltaFile = getCachedDeltaFile(event.getDid());

            if (deltaFile == null) {
                throw new InvalidActionEventException("DeltaFile " + event.getDid() + " not found.");
            }

            if (deltaFile.getStage() == DeltaFileStage.CANCELLED) {
                log.warn("Received event for cancelled did " + deltaFile.getDid());
                return;
            }

            String validationError = event.validate();
            if (validationError != null) {
                event.setError(ErrorEvent.builder()
                        .cause(INVALID_ACTION_EVENT_RECEIVED)
                        .context(validationError + ": " + OBJECT_MAPPER.writeValueAsString(event))
                        .build());
                error(deltaFile, event);
                return;
            }

            DeltaFileStage eventStage = deltaFile.getStage();

            deltaFile.ensurePendingAction(event.getFlow(), event.getAction());

            List<Metric> metrics = (event.getMetrics() != null) ? event.getMetrics() : new ArrayList<>();
            metrics.add(new Metric(DeltaFiConstants.FILES_IN, 1));

            switch (event.getType()) {
                case TRANSFORM -> {
                    generateMetrics(metrics, event, deltaFile);
                    transform(deltaFile, event);
                }
                case LOAD -> {
                    generateMetrics(metrics, event, deltaFile);
                    load(deltaFile, event);
                }
                case LOAD_MANY -> {
                    generateMetrics(metrics, event, deltaFile);
                    loadMany(deltaFile, event);
                }
                case DOMAIN -> {
                    generateMetrics(metrics, event, deltaFile);
                    domain(deltaFile, event);
                }
                case ENRICH -> {
                    generateMetrics(metrics, event, deltaFile);
                    enrich(deltaFile, event);
                }
                case FORMAT -> {
                    generateMetrics(metrics, event, deltaFile);
                    format(deltaFile, event);
                }
                case VALIDATE -> {
                    generateMetrics(metrics, event, deltaFile);
                    validate(deltaFile, event);
                }
                case EGRESS -> {
                    metrics.add(
                            Metric.builder()
                                    .name(EXECUTION_TIME_MS)
                                    .value(Duration.between(deltaFile.getCreated(), deltaFile.getModified()).toMillis())
                                    .build());
                    generateMetrics(metrics, event, deltaFile);
                    egress(deltaFile, event);
                }
                case ERROR -> {
                    generateMetrics(metrics, event, deltaFile);
                    error(deltaFile, event);
                }
                case FILTER -> {
                    metrics.add(new Metric(DeltaFiConstants.FILES_FILTERED, 1));
                    generateMetrics(metrics, event, deltaFile);
                    filter(deltaFile, event);
                }
                case REINJECT -> {
                    generateMetrics(metrics, event, deltaFile);
                    reinject(deltaFile, event);
                }
                case FORMAT_MANY -> {
                    generateMetrics(metrics, event, deltaFile);
                    formatMany(deltaFile, event);
                }
                default -> throw new UnknownTypeException(event.getAction(), deltaFile.getDid(), event.getType());
            }

            completeCollect(event, eventStage, deltaFile);
        }
    }

    private void generateMetrics(List<Metric> metrics, ActionEvent event, DeltaFile deltaFile) {
        generateMetrics(true, metrics, event, deltaFile);
    }

    private void generateMetrics(boolean actionExecuted, List<Metric> metrics, ActionEvent event, DeltaFile deltaFile) {
        String egressFlow = egressFlow(event.getFlow(), event.getAction(), deltaFile);
        Map<String, String> defaultTags = MetricsUtil.tagsFor(event.getType(), event.getAction(), deltaFile.getSourceInfo().getFlow(), egressFlow);
        for (Metric metric : metrics) {
            metric.addTags(defaultTags);
            metricService.increment(metric);
        }

        // Don't track execution times for internally generated error events,
        // or if we've already recorded them
        if (actionExecuted) {
            String actionClass = null;
            ActionConfiguration actionConfiguration = actionConfiguration(event.getFlow(),
                    event.getAction(), deltaFile.getSourceInfo().getProcessingType(), deltaFile.getStage());
            if (actionConfiguration != null) {
                actionClass = actionConfiguration.getType();
            }
            generateActionExecutionMetric(defaultTags, event, actionClass);
        }
    }

    private void generateIngressMetrics(List<Metric> metrics, ActionEvent event, String flow, String actionClass) {
        Map<String, String> defaultTags = MetricsUtil.tagsFor(event.getType(), event.getAction(), flow, null);
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

    private String egressFlow(String flow, String actionName, DeltaFile deltaFile) {
        Optional<Action> action = deltaFile.actionNamed(flow, actionName);
        return action.map(value -> egressFlow(value, deltaFile)).orElse(null);
    }

    private String egressFlow(Action action, DeltaFile deltaFile) {
        if (deltaFile.getStage().equals(DeltaFileStage.EGRESS) || (deltaFile.getStage().equals(DeltaFileStage.COMPLETE) && deltaFile.getEgressed())) {
            return action.getFlow();
        }

        return null;
    }

    public void ingressFromAction(ActionEvent event) {
        TimedIngressFlow timedIngressFlow = timedIngressFlowService.getRunningFlowByName(event.getFlow());
        IngressEvent ingressEvent = event.getIngress();
        boolean completedExecution = timedIngressFlowService.completeExecution(timedIngressFlow.getName(),
                event.getDid(), ingressEvent.getMemo(), ingressEvent.isExecuteImmediate(),
                ingressEvent.getStatus(), ingressEvent.getStatusMessage(), timedIngressFlow.getCronSchedule());
        if (!completedExecution) {
            log.warn("Received unexpected ingress event with did " + event.getDid());
            return;
        }

        final Counter counter = new Counter();
        if (timedIngressFlow.publishRules() != null) {
            ingressEvent.getIngressItems().stream()
                    .map((item) -> buildIngressDeltaFile(event, item))
                    .forEach(deltaFile -> publishDeltaFile(timedIngressFlow, deltaFile, counter));
        } else if (timedIngressFlow.getTargetFlow() != null) {
            ingressEvent.getIngressItems().forEach(ingressItem -> {
                ingressItem.setFlow(timedIngressFlow.getTargetFlow());
                ingressItem.setProcessingType(null);
                DeltaFile deltaFile = ingressOrErrorOnMissingFlow(ingressItem, event.getAction(), event.getFlow(),
                        event.getStart(), event.getStop());
                if (deltaFile.getStage() == DeltaFileStage.ERROR) {
                    counter.erroredFiles++;
                }
                counter.byteCount += deltaFile.getIngressBytes();
            });
        }

        String actionClass =
                (timedIngressFlow.findActionConfigByName(event.getAction()) != null)
                        ? timedIngressFlow.findActionConfigByName(event.getAction()).getType()
                        : null;

        List<Metric> metrics = (event.getMetrics() != null) ? event.getMetrics() : new ArrayList<>();
        metrics.add(new Metric(DeltaFiConstants.FILES_IN, ingressEvent.getIngressItems().size()));
        metrics.add(new Metric(DeltaFiConstants.BYTES_IN, counter.byteCount));

        if (counter.erroredFiles > 0) {
            metrics.add(new Metric(FILES_ERRORED, counter.erroredFiles));
        } else if (counter.filteredFiles > 0) {
            metrics.add(new Metric(FILES_FILTERED, counter.filteredFiles));
        }

        String flowName = timedIngressFlow.getTargetFlow() != null ? timedIngressFlow.getTargetFlow() :  timedIngressFlow.getName();
        generateIngressMetrics(metrics, event, flowName, actionClass);
    }

    private void publishDeltaFile(Publisher publisher, DeltaFile deltaFile, Counter counter) {
        // need a default source flow to handle no matches off an ingress action
        deltaFile.getSourceInfo().setFlow(publisher.getName());
        counter.byteCount += deltaFile.getIngressBytes();

        Set<Subscriber> subscribers = publisherService.subscribers(publisher, deltaFile);
        int count = subscribers.size();
        if (count == 1) {
            Subscriber subscriber = subscribers.iterator().next();
            deltaFile.getSourceInfo().setFlow(subscriber.getName());
            advanceAndSave(subscriber, deltaFile, false);
        } else if (count > 1) {
            List<DeltaFile> childDeltaFiles = new ArrayList<>();
            List<ActionInvocation> actionInvocations = new ArrayList<>();
            for (Subscriber subscriber : subscribers) {
                DeltaFile child = createChildDeltaFile(deltaFile, uuidGenerator.generate());
                child.getSourceInfo().setFlow(subscriber.getName());
                actionInvocations.addAll(advanceOnly(subscriber, child, false));
                deltaFile.getChildDids().add(child.getDid());
                childDeltaFiles.add(child);
            }

            // mark the parent as published, so it can be replayed properly
            completeSyntheticPublishAction(deltaFile, publisher.getName());
            deltaFile.setStage(DeltaFileStage.COMPLETE);
            deltaFile.recalculateBytes();
            deltaFileCacheService.save(deltaFile);
            deltaFileRepo.saveAll(childDeltaFiles);

            if (!actionInvocations.isEmpty()) {
                enqueueActions(actionInvocations);
            }
        } else {
            // no subscribers were found, save the DeltaFile with the synthetic actions added by the publisherService
            deltaFileCacheService.save(deltaFile);
            ActionState lastState = deltaFile.lastAction().getState();
            if (lastState == ActionState.FILTERED) {
                counter.filteredFiles++;
            } else if (lastState == ActionState.ERROR) {
                counter.erroredFiles++;
            }
        }
    }

    private void completeSyntheticPublishAction(DeltaFile deltaFile, String publisherName) {
        deltaFile.queueNewAction(publisherName, PUBLISH_ACTION_NAME, ActionType.PUBLISH, false);
        deltaFile.completeAction(syntheticPublishEvent(deltaFile, publisherName));
    }

    private ActionEvent syntheticPublishEvent(DeltaFile deltaFile, String publisherName) {
            OffsetDateTime now = OffsetDateTime.now(clock);
            return ActionEvent.builder()
                    .did(deltaFile.getDid())
                    .flow(publisherName)
                    .action(PUBLISH_ACTION_NAME)
                    .start(now)
                    .stop(now)
                    .type(ActionEventType.PUBLISH).build();
    }

    public void transform(DeltaFile deltaFile, ActionEvent event) {
        TransformEvent transformEvent = event.getTransform();
        deltaFile.completeAction(event, transformEvent.getContent(), transformEvent.getMetadata(),
                transformEvent.getDeleteMetadataKeys(), Collections.emptyList(), Collections.emptyList());

        deltaFile.addAnnotations(transformEvent.getAnnotations());

        if (deltaFile.getSourceInfo().getProcessingType() == ProcessingType.TRANSFORMATION) {
            advanceAndSaveTransformationProcessing(deltaFile);
        } else {
            advanceAndSave(deltaFile);
        }
    }

    public void load(DeltaFile deltaFile, ActionEvent event) {
        LoadEvent loadEvent = event.getLoad();
        deltaFile.completeAction(event, loadEvent.getContent(), loadEvent.getMetadata(),
                loadEvent.getDeleteMetadataKeys(), loadEvent.getDomains(), Collections.emptyList());

        deltaFile.addAnnotations(loadEvent.getAnnotations());

        advanceAndSave(deltaFile);
    }

    public void domain(DeltaFile deltaFile, ActionEvent event) {
        deltaFile.completeAction(event);

        deltaFile.addAnnotations(event.getDomain().getAnnotations());

        advanceAndSave(deltaFile);
    }

    public void enrich(DeltaFile deltaFile, ActionEvent event) {
        Action action = deltaFile.completeAction(event);

        if (event.getEnrich().getEnrichments() != null) {
            for (Enrichment enrichment : event.getEnrich().getEnrichments()) {
                action.addEnrichment(enrichment.getName(), enrichment.getValue(), enrichment.getMediaType());
            }
        }

        deltaFile.addAnnotations(event.getEnrich().getAnnotations());

        advanceAndSave(deltaFile);
    }

    public void format(DeltaFile deltaFile, ActionEvent event) {
        if (event.getFormat().getContent() == null) {
            event.setError(ErrorEvent.builder()
                    .cause("Received format event with no content from " + event.getAction())
                    .build());
            error(deltaFile, event);
            return;
        }

        FormatEvent formatEvent = event.getFormat();
        deltaFile.completeAction(event, List.of(formatEvent.getContent()), formatEvent.getMetadata(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());

        advanceAndSave(deltaFile);
    }

    public void validate(DeltaFile deltaFile, ActionEvent event) {
        deltaFile.completeAction(event);

        advanceAndSave(deltaFile);
    }

    public void egress(DeltaFile deltaFile, ActionEvent event) {
        // replicate the message that was sent to this action so we can republish the content and metadata that was processed
        DeltaFileMessage sentMessage = deltaFile.forQueue(event.getFlow());

        deltaFile.completeAction(event, sentMessage.getContentList(), sentMessage.getMetadata(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        deltaFile.setEgressed(true);

        Set<String> expectedAnnotations = getPendingAnnotations(deltaFile.getSourceInfo().getProcessingType(), event.getFlow());

        if (expectedAnnotations != null && !expectedAnnotations.isEmpty()) {
            deltaFile.addPendingAnnotationsForFlow(event.getFlow());
        }

        advanceAndSave(deltaFile);
    }

    public void filter(DeltaFile deltaFile, ActionEvent event) {
        ActionConfiguration actionConfiguration = actionConfiguration(event.getFlow(),
                event.getAction(), deltaFile.getSourceInfo().getProcessingType(), deltaFile.getStage());
        ActionType actionType = ActionType.UNKNOWN;
        if (actionConfiguration != null) {
            actionType = actionConfiguration.getActionType();
        }

        // Treat filter events from Domain and Enrich actions as errors
        if (actionType.equals(ActionType.DOMAIN) || actionType.equals(ActionType.ENRICH)) {
            event.setError(ErrorEvent.builder()
                    .cause("Illegal operation FILTER received from " + actionType + "Action " + event.getAction())
                    .build());
            error(deltaFile, event);
            return;
        }

        deltaFile.addAnnotations(event.getFilter().getAnnotations());
        deltaFile.filterAction(event);
        deltaFile.setFiltered(true);

        advanceAndSave(deltaFile);
    }

    private void error(DeltaFile deltaFile, ActionEvent event) {
        deltaFile.addAnnotations(event.getError().getAnnotations());

        // If the content was deleted by a delete policy mark as CANCELLED instead of ERROR
        if (deltaFile.getContentDeleted() != null) {
            deltaFile.cancelQueuedActions();
            deltaFile.setStage(DeltaFileStage.CANCELLED);
            deltaFileCacheService.save(deltaFile);
        } else {
            advanceAndSave(processErrorEvent(deltaFile, event));
        }
    }

    public DeltaFile processErrorEvent(DeltaFile deltaFile, ActionEvent event) {
        deltaFile.ensurePendingAction(event.getFlow(), event.getAction());

        Optional<ResumePolicyService.ResumeDetails> resumeDetails = Optional.empty();
        ActionConfiguration actionConfiguration = actionConfiguration(event.getFlow(),
                event.getAction(), deltaFile.getSourceInfo().getProcessingType(), deltaFile.getStage());
        if (actionConfiguration != null) {
            resumeDetails = resumePolicyService.getAutoResumeDelay(deltaFile, event, actionConfiguration.getActionType().name());
        } else if (event.getAction().equals(NO_EGRESS_FLOW_CONFIGURED_ACTION)) {
            resumeDetails = resumePolicyService.getAutoResumeDelay(deltaFile, event, ActionEventType.UNKNOWN.name());
        }
        resumeDetails.ifPresentOrElse(
                details -> deltaFile.errorAction(event, details.name(), details.delay()),
                () -> deltaFile.errorAction(event));
        // false: we don't want action execution metrics, since they have already been recorded.
        generateMetrics(false, List.of(new Metric(DeltaFiConstants.FILES_ERRORED, 1)), event, deltaFile);

        return deltaFile;
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

        deltaFile.getEgress().forEach(egress -> updatePendingAnnotations(deltaFile, egress));

        deltaFile.setModified(annotationTime);
        deltaFileCacheService.save(deltaFile);
    }

    void updatePendingAnnotations(DeltaFile deltaFile, Egress egress) {
        updatePendingAnnotations(deltaFile, egress.getFlow());
    }

    void updatePendingAnnotations(DeltaFile deltaFile, String flowName) {
        Set<String> expectedAnnotations = getPendingAnnotations(deltaFile.getSourceInfo().getProcessingType(), flowName);
        if (expectedAnnotations != null) {
            deltaFile.updatePendingAnnotationsForFlows(flowName, expectedAnnotations);
        }
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
        if (expectedAnnotations == null) {
            deltaFileRepo.removePendingAnnotationsForFlow(flowName);
        } else {
            int batchSize = deltaFiPropertiesService.getDeltaFiProperties().getDelete().getPolicyBatchSize();
            List<DeltaFile> updatedDeltaFiles = new ArrayList<>();
            try (Stream<DeltaFile> deltaFiles = deltaFileRepo.findByPendingAnnotationsForFlows(flowName)) {
                deltaFiles.forEach(deltaFile -> updatePendingAnnotationsForFlowsAndCollect(deltaFile, flowName, expectedAnnotations, updatedDeltaFiles, batchSize));
            }
            if (!updatedDeltaFiles.isEmpty()) {
                deltaFileRepo.saveAll(updatedDeltaFiles);
            }
        }
    }

    void updatePendingAnnotationsForFlowsAndCollect(DeltaFile deltaFile, String flowName, Set<String> expectedAnnotations, List<DeltaFile> collector, int batchSize) {
        deltaFile.updatePendingAnnotationsForFlows(flowName, expectedAnnotations);
        collector.add(deltaFile);

        if (collector.size() == batchSize) {
            deltaFileRepo.saveAll(collector);
            collector.clear();
        }
    }

    /*
     * Find the flow with the given egress action and return expected annotations associated with the flow
     */
    private Set<String> getPendingAnnotations(ProcessingType processingType, String flowName) {
        Set<String> expectedAnnotations = new HashSet<>();
        try {
            if (ProcessingType.TRANSFORMATION.equals(processingType)) {
                expectedAnnotations = transformFlowService.getRunningFlowByName(flowName).getExpectedAnnotations();
            } else {
                expectedAnnotations = egressFlowService.getRunningFlowByName(flowName).getExpectedAnnotations();
            }
        } catch (DgsEntityNotFoundException e) {
            log.warn("Flow {} is no longer running or no longer installed", flowName);
        }

        return expectedAnnotations;
    }

    public static ActionEvent buildNoEgressConfiguredErrorEvent(DeltaFile deltaFile, OffsetDateTime time) {
        return ActionEvent.builder()
                .did(deltaFile.getDid())
                .flow("MISSING")
                .action(DeltaFiConstants.NO_EGRESS_FLOW_CONFIGURED_ACTION)
                .start(time)
                .stop(time)
                .error(ErrorEvent.builder()
                        .cause(NO_EGRESS_CONFIGURED_CAUSE)
                        .context(NO_EGRESS_CONFIGURED_CONTEXT)
                        .build())
                .type(ActionEventType.UNKNOWN)
                .build();
    }

    public static ActionEvent buildNoChildFlowErrorEvent(DeltaFile deltaFile, String flow, String action,
            String reinjectFlow, OffsetDateTime time) {
        return ActionEvent.builder()
                .did(deltaFile.getDid())
                .flow(flow)
                .action(action)
                .start(time)
                .stop(time)
                .error(ErrorEvent.builder()
                        .cause(NO_CHILD_INGRESS_CONFIGURED_CAUSE)
                        .context(NO_CHILD_INGRESS_CONFIGURED_CONTEXT + reinjectFlow)
                        .build())
                .build();
    }

    public static ActionEvent buildFlowIngressDisabledErrorEvent(DeltaFile deltaFile, String flow, String action,
            String reinjectFlow, OffsetDateTime time) {
        return ActionEvent.builder()
                .did(deltaFile.getDid())
                .flow(flow)
                .action(action)
                .start(time)
                .stop(time)
                .error(ErrorEvent.builder()
                        .cause(CHILD_FLOW_INGRESS_DISABLED_CAUSE)
                        .context(CHILD_FLOW_INGRESS_DISABLED_CONTEXT + reinjectFlow)
                        .build())
                .build();
    }

    public void loadMany(DeltaFile deltaFile, ActionEvent event) throws MissingEgressFlowException {
        List<ChildLoadEvent> childLoadEvents = event.getLoadMany();
        List<DeltaFile> childDeltaFiles = Collections.emptyList();

        List<ActionInvocation> actionInvocations = new ArrayList<>();
        Map<String, Long> pendingActions = new HashMap<>();

        String loadActionName = normalizeFlowService.getRunningFlowByName(deltaFile.getSourceInfo().getFlow()).getLoadAction().getName();
        if (!event.getAction().equals(loadActionName)) {
            deltaFile.errorAction(event, "Attempted to split using a LoadMany result in an Action that is not a LoadAction: " + event.getAction(), "");
        } else if (childLoadEvents.isEmpty()) {
            deltaFile.errorAction(event, "Attempted to split a DeltaFile into 0 children using a LoadMany result", "");
        } else {
            if (deltaFile.getChildDids() == null) {
                deltaFile.setChildDids(new ArrayList<>());
            }

            OffsetDateTime now = OffsetDateTime.now(clock);
            childDeltaFiles = childLoadEvents.stream()
                    .map(childLoadEvent -> buildLoadManyChildAndEnqueue(deltaFile, event, childLoadEvent, actionInvocations, now, pendingActions))
                    .toList();

            deltaFile.reinjectAction(event);
        }

        advanceOnly(deltaFile, false, pendingActions);

        // do this in two shots.  saveAll performs a bulk insert, but only if all the entries are new
        deltaFileCacheService.save(deltaFile);
        deltaFileRepo.saveAll(childDeltaFiles);

        enqueueActions(actionInvocations);
    }

    private DeltaFile buildLoadManyChildAndEnqueue(DeltaFile parentDeltaFile, ActionEvent actionEvent,
            ChildLoadEvent childLoadEvent, List<ActionInvocation> actionInvocations, OffsetDateTime now,
            Map<String, Long> pendingActions) {
        DeltaFile child = createChildDeltaFile(parentDeltaFile, childLoadEvent.getDid());
        child.setModified(now);

        parentDeltaFile.getChildDids().add(child.getDid());

        Action action = child.getPendingAction(actionEvent.getFlow(), actionEvent.getAction());
        if (childLoadEvent.getContent() != null) {
            action.setContent(childLoadEvent.getContent());
        }
        if (childLoadEvent.getMetadata() != null) {
            action.setMetadata(childLoadEvent.getMetadata());
        }
        if (childLoadEvent.getDeleteMetadataKeys() != null) {
            action.setDeleteMetadataKeys(childLoadEvent.getDeleteMetadataKeys());
        }
        if (childLoadEvent.getDomains() != null) {
            for (Domain domain : childLoadEvent.getDomains()) {
                action.addDomain(domain.getName(), domain.getValue(), domain.getMediaType());
            }
        }
        child.completeAction(actionEvent);

        child.addAnnotations(childLoadEvent.getAnnotations());

        actionInvocations.addAll(advanceOnly(child, true, pendingActions));

        child.recalculateBytes();

        return child;
    }

    public void reinject(DeltaFile deltaFile, ActionEvent event) throws MissingEgressFlowException {
        List<ReinjectEvent> reinjects = event.getReinject();
        List<DeltaFile> childDeltaFiles = new ArrayList<>();
        boolean encounteredError = false;
        List<ActionInvocation> actionInvocations = new ArrayList<>();
        Map<String, Long> pendingQueued = new HashMap<>();

        List<String> allowedActions = new ArrayList<>();
        if (normalizeFlowService.hasRunningFlow(deltaFile.getSourceInfo().getFlow())) {
            NormalizeFlow normalizeFlow = normalizeFlowService.getRunningFlowByName(deltaFile.getSourceInfo().getFlow());
            allowedActions.add(normalizeFlow.getLoadAction().getName());
            allowedActions.addAll(normalizeFlow.getTransformActions().stream().map(TransformActionConfiguration::getName).toList());
        }
        if (transformFlowService.hasRunningFlow(deltaFile.getSourceInfo().getFlow())) {
            TransformFlow transformFlow = transformFlowService.getRunningFlowByName(deltaFile.getSourceInfo().getFlow());
            allowedActions.addAll(transformFlow.getTransformActions().stream().map(TransformActionConfiguration::getName).toList());
        }

        if (!allowedActions.contains(event.getAction())) {
            deltaFile.errorAction(event, "Attempted to reinject from an Action that is not a TransformAction or LoadAction: " + event.getAction(), "");
        } else if (reinjects.isEmpty()) {
            deltaFile.errorAction(event, "Attempted to reinject DeltaFile into 0 children", "");
        } else {
            if (deltaFile.getChildDids() == null) {
                deltaFile.setChildDids(new ArrayList<>());
            }

            OffsetDateTime now = OffsetDateTime.now(clock);

            Set<String> normalizationFlowsDisabled = normalizeFlowService.flowErrorsExceeded();
            Set<String> transformationFlowsDisabled = transformFlowService.flowErrorsExceeded();
            Map<String, ProcessingType> evaluatedFlows = new HashMap<>();

            for (ReinjectEvent reinject : reinjects) {
                // Before we build a DeltaFile, make sure the reinject makes sense to do--i.e. the flow is enabled and valid
                ProcessingType processingType;
                if (evaluatedFlows.containsKey(reinject.getFlow())) {
                    processingType = evaluatedFlows.get(reinject.getFlow());
                } else if (normalizeFlowService.hasRunningFlow(reinject.getFlow())) {
                    if (normalizationFlowsDisabled.contains(reinject.getFlow())) {
                        deltaFile.errorAction(buildFlowIngressDisabledErrorEvent(deltaFile, event.getFlow(),
                                event.getAction(), reinject.getFlow(), now));
                        encounteredError = true;
                        break;
                    } else {
                        processingType = ProcessingType.NORMALIZATION;
                        evaluatedFlows.put(reinject.getFlow(), processingType);
                    }
                } else if (transformFlowService.hasRunningFlow(reinject.getFlow())) {
                    if (transformationFlowsDisabled.contains(reinject.getFlow())) {
                        deltaFile.errorAction(buildFlowIngressDisabledErrorEvent(deltaFile, event.getFlow(),
                                event.getAction(), reinject.getFlow(), now));
                        encounteredError = true;
                        break;
                    } else {
                        processingType = ProcessingType.TRANSFORMATION;
                        evaluatedFlows.put(reinject.getFlow(), processingType);
                    }
                } else {
                    deltaFile.errorAction(buildNoChildFlowErrorEvent(deltaFile, event.getFlow(), event.getAction(),
                            reinject.getFlow(), now));
                    encounteredError = true;
                    break;
                }

                Action action = Action.builder()
                        .name(INGRESS_ACTION)
                        .type(ActionType.INGRESS)
                        .flow(reinject.getFlow())
                        .state(ActionState.COMPLETE)
                        .created(now)
                        .modified(now)
                        .content(reinject.getContent())
                        .metadata(reinject.getMetadata())
                        .build();

                DeltaFile child = DeltaFile.builder()
                        .schemaVersion(DeltaFile.CURRENT_SCHEMA_VERSION)
                        .did(UUID.randomUUID().toString())
                        .parentDids(List.of(deltaFile.getDid()))
                        .childDids(Collections.emptyList())
                        .requeueCount(0)
                        .ingressBytes(ContentUtil.computeContentSize(reinject.getContent()))
                        .stage(DeltaFileStage.INGRESS)
                        .inFlight(true)
                        .terminal(false)
                        .contentDeletable(false)
                        .actions(new ArrayList<>(List.of(action)))
                        .sourceInfo(SourceInfo.builder()
                                .flow(reinject.getFlow())
                                .filename(reinject.getFilename())
                                .metadata(reinject.getMetadata())
                                .processingType(processingType)
                                .build())
                        .created(now)
                        .modified(now)
                        .egressed(false)
                        .filtered(false)
                        .build();

                actionInvocations.addAll(advanceOnly(child, true, pendingQueued));

                child.recalculateBytes();

                childDeltaFiles.add(child);
            }

            if (!encounteredError) {
                deltaFile.setChildDids(childDeltaFiles.stream().map(DeltaFile::getDid).toList());
            }

            deltaFile.reinjectAction(event);
        }

        advanceOnly(deltaFile, false, pendingQueued);

        // do this in two shots.  saveAll performs a bulk insert, but only if all the entries are new
        deltaFileCacheService.save(deltaFile);
        if (!encounteredError) {
            deltaFileRepo.saveAll(childDeltaFiles);

            enqueueActions(actionInvocations);
        }
    }

    public void formatMany(DeltaFile deltaFile, ActionEvent event) throws MissingEgressFlowException {
        List<ChildFormatEvent> childFormatEvents = event.getFormatMany();
        List<DeltaFile> childDeltaFiles = Collections.emptyList();
        List<ActionInvocation> actionInvocations = new ArrayList<>();
        Map<String, Long> pendingQueued = new HashMap<>();

        List<String> formatActions = egressFlowService.getAll().stream().map(ef -> ef.getFormatAction().getName()).toList();
        if (!formatActions.contains(event.getAction())) {
            deltaFile.errorAction(event, "Attempted to split from an Action that is not a current FormatAction: " + event.getAction(), "");
        } else if (childFormatEvents.isEmpty()) {
            deltaFile.errorAction(event, "Attempted to split DeltaFile into 0 children with formatMany", "");
        } else {
            if (deltaFile.getChildDids() == null) {
                deltaFile.setChildDids(new ArrayList<>());
            }

            EgressFlow egressFlow = egressFlowService.withFormatActionNamed(event.getFlow(), event.getAction());

            childDeltaFiles = childFormatEvents.stream().map(childFormatEvent -> {
                // Maintain backwards compatibility with FORMAT_MANY events not supplying child dids
                String childDid = childFormatEvent.getDid() != null ? childFormatEvent.getDid() : UUID.randomUUID().toString();
                DeltaFile child = createChildDeltaFile(deltaFile, event, childDid);
                deltaFile.getChildDids().add(child.getDid());
                Action formatAction = child.lastFormatAction(egressFlow.getName());
                formatAction.setContent(List.of(childFormatEvent.getContent()));
                formatAction.setMetadata(childFormatEvent.getMetadata());

                actionInvocations.addAll(advanceOnly(child, true, pendingQueued));

                child.recalculateBytes();

                return child;
            }).toList();

            deltaFile.reinjectAction(event);
        }

        advanceOnly(deltaFile, false, pendingQueued);

        // do this in two shots.  saveAll performs a bulk insert, but only if all the entries are new
        deltaFileCacheService.save(deltaFile);
        deltaFileRepo.saveAll(childDeltaFiles);

        enqueueActions(actionInvocations);
    }

    public void splitForTransformationProcessingEgress(DeltaFile deltaFile) throws MissingEgressFlowException {
        List<ActionInvocation> actionInvocations = new ArrayList<>();
        Map<String, Long> pendingQueued = new HashMap<>();

        if (Objects.isNull(deltaFile.getChildDids())) {
            deltaFile.setChildDids(new ArrayList<>());
        }

        // remove the egress action, since we want the last transform to show SPLIT
        deltaFile.removeLastAction();

        List<Content> contentList = deltaFile.lastCompleteDataAmendedAction().getContent();

        List<DeltaFile> childDeltaFiles = contentList.stream().map(content -> {
            DeltaFile child = createChildDeltaFile(deltaFile, UUID.randomUUID().toString());
            child.lastCompleteDataAmendedAction().setContent(Collections.singletonList(content));
            deltaFile.getChildDids().add(child.getDid());

            actionInvocations.addAll(advanceOnly(child, true, pendingQueued));

            child.recalculateBytes();

            return child;
        }).toList();

        deltaFile.setLastActionReinjected();

        advanceOnly(deltaFile, false, pendingQueued);

        // do this in two shots.  saveAll performs a bulk insert, but only if all the entries are new
        deltaFileCacheService.save(deltaFile);
        deltaFileRepo.saveAll(childDeltaFiles);

        enqueueActions(actionInvocations);
    }

    private static DeltaFile createChildDeltaFile(DeltaFile deltaFile, ActionEvent event, String childDid) {
        DeltaFile child = createChildDeltaFile(deltaFile, childDid);
        child.completeAction(event);
        return child;
    }

    private static DeltaFile createChildDeltaFile(DeltaFile deltaFile, String childDid) {
        DeltaFile child = OBJECT_MAPPER.convertValue(deltaFile, DeltaFile.class);
        child.setVersion(0);
        child.setDid(childDid);
        child.setChildDids(Collections.emptyList());
        child.setParentDids(List.of(deltaFile.getDid()));
        return child;
    }

    public List<RetryResult> resume(@NotNull List<String> dids, @NotNull List<ResumeMetadata> resumeMetadata) {
        Map<String, DeltaFile> deltaFiles = deltaFiles(dids);
        List<DeltaFile> advanceAndSaveDeltaFiles = new ArrayList<>();

        List<RetryResult> retryResults = dids.stream()
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
                        } else if (deltaFile.getContentDeleted() != null) {
                            result.setSuccess(false);
                            result.setError("Cannot resume DeltaFile " + did + " after content was deleted (" + deltaFile.getContentDeletedReason() + ")");
                        } else {
                            List<String> requeueActions = deltaFile.retryErrors(resumeMetadata);
                            if (requeueActions.isEmpty()) {
                                result.setSuccess(false);
                                result.setError("DeltaFile with did " + did + " had no errors");
                            } else {
                                deltaFile.setStage(DeltaFileStage.INGRESS);
                                deltaFile.clearErrorAcknowledged();
                                if (deltaFile.lastAction().getType() == ActionType.PUBLISH) {
                                    rerunPublish(deltaFile);
                                } else {
                                    advanceAndSaveDeltaFiles.add(deltaFile);
                                }
                            }
                        }
                    } catch (Exception e) {
                        result.setSuccess(false);
                        result.setError(e.getMessage());
                    }
                    return result;
                })
                .toList();

        advanceAndSave(advanceAndSaveDeltaFiles);
        return retryResults;
    }

    private static void applyRetryOverrides(DeltaFile deltaFile, String replaceFilename, String replaceFlow, @NotNull List<String> removeSourceMetadata, @NotNull List<KeyValue> replaceSourceMetadata) {
        if (replaceFilename != null) {
            deltaFile.getSourceInfo().addMetadata("sourceInfo.filename.original", deltaFile.getSourceInfo().getFilename());
            deltaFile.getSourceInfo().setFilename(replaceFilename);
        }

        if (replaceFlow != null) {
            deltaFile.getSourceInfo().addMetadata("sourceInfo.flow.original", deltaFile.getSourceInfo().getFlow());
            deltaFile.getSourceInfo().setFlow(replaceFlow);
        }

        SourceInfo sourceInfo = deltaFile.getSourceInfo();

        for (String removeKey : removeSourceMetadata) {
            if (sourceInfo.containsKey(removeKey)) {
                sourceInfo.addMetadata(removeKey + ".original", sourceInfo.getMetadata(removeKey));
                sourceInfo.removeMetadata(removeKey);
            }
        }

        replaceSourceMetadata.forEach( keyValue -> {
            if (sourceInfo.containsKey(keyValue.getKey())) {
                sourceInfo.addMetadata(keyValue.getKey() + ".original", sourceInfo.getMetadata(keyValue.getKey()));
            }
            sourceInfo.addMetadata(keyValue.getKey(), keyValue.getValue());
        });
    }

    public List<RetryResult> replay(@NotNull List<String> dids, String replaceFilename, String replaceFlow, @NotNull List<String> removeSourceMetadata, @NotNull List<KeyValue> replaceSourceMetadata)  {
        Map<String, DeltaFile> deltaFiles = deltaFiles(dids);

        List<DeltaFile> parentDeltaFiles = new ArrayList<>();
        List<DeltaFile> childDeltaFiles = new ArrayList<>();
        List<ActionInvocation> actionInvocations = new ArrayList<>();
        Map<String, Long> pendingQueued = new HashMap<>();

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
                            Action action = Action.builder()
                                    .name(INGRESS_ACTION)
                                    .type(ActionType.INGRESS)
                                    .flow(deltaFile.getSourceInfo().getFlow())
                                    .state(ActionState.COMPLETE)
                                    .created(now)
                                    .modified(now)
                                    .content(deltaFile.getActions().get(0).getContent())
                                    .metadata(deltaFile.getSourceInfo().getMetadata())
                                    .build();

                            DeltaFile child = DeltaFile.builder()
                                    .schemaVersion(DeltaFile.CURRENT_SCHEMA_VERSION)
                                    .did(UUID.randomUUID().toString())
                                    .parentDids(List.of(deltaFile.getDid()))
                                    .childDids(new ArrayList<>())
                                    .requeueCount(0)
                                    .ingressBytes(deltaFile.getIngressBytes())
                                    .stage(DeltaFileStage.INGRESS)
                                    .inFlight(true)
                                    .terminal(false)
                                    .contentDeletable(false)
                                    .actions(new ArrayList<>(List.of(action)))
                                    .sourceInfo(deltaFile.getSourceInfo())
                                    .created(now)
                                    .modified(now)
                                    .egressed(false)
                                    .filtered(false)
                                    .build();

                            applyRetryOverrides(child, replaceFilename, replaceFlow, removeSourceMetadata, replaceSourceMetadata);

                            if (deltaFile.lastAction().getType() == ActionType.PUBLISH) {
                                rerunPublish(child);
                            } else {
                                actionInvocations.addAll(advanceOnly(child, true, pendingQueued));
                                child.recalculateBytes();
                                childDeltaFiles.add(child);
                            }

                            deltaFile.setReplayed(now);
                            deltaFile.setReplayDid(child.getDid());
                            if (Objects.isNull(deltaFile.getChildDids())) {
                                deltaFile.setChildDids(new ArrayList<>());
                            }
                            deltaFile.getChildDids().add(child.getDid());
                            parentDeltaFiles.add(deltaFile);
                            result.setDid(child.getDid());
                        }
                    } catch (Exception e) {
                        result.setSuccess(false);
                        result.setError(e.getMessage());
                    }
                    return result;
                })
                .toList();

        deltaFileRepo.saveAll(childDeltaFiles);
        deltaFileRepo.saveAll(parentDeltaFiles);

        enqueueActions(actionInvocations);

        return results;
    }

    private void rerunPublish(DeltaFile deltaFile) {
        TimedIngressFlow timedIngressFlow = timedIngressFlowService.getRunningFlowByName(deltaFile.lastAction().getFlow());
        publishDeltaFile(timedIngressFlow, deltaFile, new Counter());
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
                            deltaFile.acknowledgeError(now, reason);
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
                            deltaFile.cancel();
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
        DeltaFiles deltaFiles = deltaFiles(0, dids.size(), filter, null, List.of(ACTIONS_NAME, ACTIONS_TYPE, ACTIONS_FLOW, ACTIONS_STATE, ACTIONS_METADATA, ACTIONS_DELETE_METADATA_KEYS));

        Map<Pair<String, String>, PerActionUniqueKeyValues> actionKeyValues = new HashMap<>();
        for (DeltaFile deltaFile : deltaFiles.getDeltaFiles()) {
            for (Action action : deltaFile.erroredActions()) {
                if (action.getType() == ActionType.UNKNOWN) {
                    // ignore synthetic actions like NoEgressFlowConfigured
                    continue;
                }
                if (!actionKeyValues.containsKey(Pair.of(action.getFlow(), action.getName()))) {
                    actionKeyValues.put(Pair.of(action.getFlow(), action.getName()), new PerActionUniqueKeyValues(action.getFlow(), action.getName()));
                }
                deltaFile.getErrorMetadata(action)
                        .forEach((key, value) -> actionKeyValues.get(Pair.of(action.getFlow(), action.getName())).addValue(key, value));
            }
        }
        return new ArrayList<>(actionKeyValues.values());
    }

    public List<UniqueKeyValues> sourceMetadataUnion(List<String> dids) {
        DeltaFilesFilter filter = new DeltaFilesFilter();
        filter.setDids(dids);
        DeltaFiles deltaFiles = deltaFiles(0, dids.size(), filter, null, List.of(SOURCE_INFO_METADATA));

        Map<String, UniqueKeyValues> keyValues = new HashMap<>();
        deltaFiles.getDeltaFiles().forEach(deltaFile -> deltaFile.getSourceInfo().getMetadata().forEach((key, value) -> {
            if (!keyValues.containsKey(key)) {
                keyValues.put(key, new UniqueKeyValues(key));
            }
            keyValues.get(key).addValue(value);
        }));
        return new ArrayList<>(keyValues.values());
    }

    /**
     * Advance the DeltaFile to the next step using the state machine.
     *
     * @param deltaFile the DeltaFile to advance through the state machine
     * @param newDeltaFile Whether this is a new DeltaFile. Used to determine whether routing affinity is needed
     * @param pendingQueued A map of queue names to number of times to be queued so far
     * @return list of next pending action(s)
     * @throws MissingEgressFlowException if state machine would advance DeltaFile into EGRESS stage but no EgressFlow was configured.
     */
    private List<ActionInvocation> advanceOnly(DeltaFile deltaFile, boolean newDeltaFile, Map<String, Long> pendingQueued) throws MissingEgressFlowException {
        // MissingEgressFlowException is not expected when a DeltaFile is entering the INGRESS stage
        // such as from replay or reinject, since an ingress flow requires at least the Load action to
        // be queued, nor when handling an event for any egress flow action, e.g. format.

        List<ActionInvocation> actionInvocations = stateMachine.advance(deltaFile, newDeltaFile, pendingQueued);

        if (deltaFile.hasCollectingAction()) {
            deltaFileCacheService.remove(deltaFile.getDid());
        }

        return actionInvocations;
    }

    /** A version of advanceAndSave specialized for Transformation Processing
     * splits similarly to a formatMany if there are multiple pieces of content
     * @param deltaFile the transformation deltaFile to advance
     */
    private void advanceAndSaveTransformationProcessing(DeltaFile deltaFile) {
        List<ActionInvocation> actionInvocations = new ArrayList<>(stateMachine.advance(deltaFile));

        if (deltaFile.getStage() == DeltaFileStage.EGRESS) {
            // this is our first time having egress assigned
            // determine if the deltaFile needs to be split
            if (deltaFile.lastCompleteDataAmendedAction().getContent().size() > 1) {
                splitForTransformationProcessingEgress(deltaFile);
                return;
            }
        }

        if (deltaFile.hasCollectingAction()) {
            deltaFileCacheService.remove(deltaFile.getDid());
            deltaFileRepo.save(deltaFile);
        } else {
            deltaFileCacheService.save(deltaFile);
        }

        enqueueActions(actionInvocations);
    }

    private List<ActionInvocation> advanceOnly(Subscriber subscriber, DeltaFile deltaFile, boolean newDeltaFile) {
        List<ActionInvocation> actionInvocations = new ArrayList<>(stateMachine.advanceSubscriber(subscriber, deltaFile, newDeltaFile));

        if (deltaFile.hasCollectingAction()) {
            deltaFileCacheService.remove(deltaFile.getDid());
        }

        return actionInvocations;
    }

    private void advanceAndSave(Subscriber subscriber, DeltaFile deltaFile, boolean newDeltaFile) {
        List<ActionInvocation> actionInvocations = new ArrayList<>(stateMachine.advanceSubscriber(subscriber, deltaFile, newDeltaFile));

        if (deltaFile.hasCollectingAction()) {
            deltaFileCacheService.remove(deltaFile.getDid());
            deltaFileRepo.save(deltaFile);
        } else {
            deltaFileCacheService.save(deltaFile);
        }

        if (!actionInvocations.isEmpty()) {
            enqueueActions(actionInvocations);
        }
    }

    public void advanceAndSave(DeltaFile deltaFile) {
        try {
            List<ActionInvocation> actionInvocations = new ArrayList<>(stateMachine.advance(deltaFile));

            if (deltaFile.hasCollectingAction()) {
                deltaFileCacheService.remove(deltaFile.getDid());
                deltaFileRepo.save(deltaFile);
            } else {
                deltaFileCacheService.save(deltaFile);
            }

            enqueueActions(actionInvocations);
        } catch (MissingEgressFlowException e) {
            handleMissingEgressFlow(deltaFile);
            deltaFileCacheService.save(deltaFile);
        }
    }

    private void advanceAndSave(List<DeltaFile> deltaFiles) {
        if (deltaFiles.isEmpty()) {
            return;
        }

        List<ActionInvocation> actionInvocations = new ArrayList<>();

        deltaFiles.forEach(deltaFile -> {
            try {
                actionInvocations.addAll(stateMachine.advance(deltaFile));

                if (deltaFile.hasCollectingAction()) {
                    deltaFileCacheService.remove(deltaFile.getDid());
                }
            } catch (MissingEgressFlowException e) {
                handleMissingEgressFlow(deltaFile);
            }
        });

        deltaFileRepo.saveAll(deltaFiles);

        enqueueActions(actionInvocations);
    }

    private void handleMissingEgressFlow(DeltaFile deltaFile) {
        deltaFile.queueNewAction("MISSING", DeltaFiConstants.NO_EGRESS_FLOW_CONFIGURED_ACTION, ActionType.UNKNOWN, false);
        processErrorEvent(deltaFile, buildNoEgressConfiguredErrorEvent(deltaFile, OffsetDateTime.now(clock)));
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

        log.info("Searching for batch of up to " + batchSize + " deltaFiles to delete for policy " + policy);
        List<DeltaFile> deltaFiles = deltaFileRepo.findForTimedDelete(createdBefore, completedBefore, minBytes, flow, deleteMetadata, batchSize);
        delete(deltaFiles, policy, deleteMetadata);

        return deltaFiles.size() == batchSize;
    }

    public List<DeltaFile> diskSpaceDelete(long bytesToDelete, String flow, String policy) {
        int batchSize = deltaFiPropertiesService.getDeltaFiProperties().getDelete().getPolicyBatchSize();

        log.info("Searching for batch of up to " + batchSize + " deltaFiles to delete for policy " + policy);
        return delete(deltaFileRepo.findForDiskSpaceDelete(bytesToDelete, flow, batchSize), policy, false);
    }

    public List<DeltaFile> delete(List<DeltaFile> deltaFiles, String policy, boolean deleteMetadata) {
        if (deltaFiles.isEmpty()) {
            log.info("No deltaFiles found to delete for policy " + policy);
            return deltaFiles;
        }

        log.info("Deleting " + deltaFiles.size() + " deltaFiles for policy " + policy);
        long totalBytes = deltaFiles.stream().filter(d -> d.getContentDeleted() == null).mapToLong(DeltaFile::getTotalBytes).sum();

        deleteContent(deltaFiles, policy, deleteMetadata);
        metricService.increment(new Metric(DELETED_FILES, deltaFiles.size()).addTag("policy", policy));
        metricService.increment(new Metric(DELETED_BYTES, totalBytes).addTag("policy", policy));
        log.info("Finished deleting " + deltaFiles.size() + " deltaFiles for policy " + policy);

        return deltaFiles;
    }

    public void requeue() {
        OffsetDateTime modified = OffsetDateTime.now(clock);
        Set<String> longRunningDids = actionEventQueue.getLongRunningTasks().stream().map(ActionExecution::did).collect(Collectors.toSet());
        List<DeltaFile> requeuedDeltaFiles = deltaFileRepo.updateForRequeue(modified,
                getProperties().getRequeueDuration(), queueManagementService.coldQueueActions(), longRunningDids);
        List<ActionInvocation> actionInvocations = requeuedDeltaFiles.stream()
                .map(deltaFile -> requeuedActionInvocations(deltaFile, modified))
                .flatMap(Collection::stream)
                .toList();
        if (!actionInvocations.isEmpty()) {
            log.warn(actionInvocations.size() + " actions exceeded requeue threshold of " + getProperties().getRequeueDuration() +
                    " seconds, requeuing now");
            enqueueActions(actionInvocations, true);
        }
    }

    List<ActionInvocation> requeuedActionInvocations(DeltaFile deltaFile, OffsetDateTime modified) {
        return deltaFile.getActions().stream()
                .filter(action -> action.getState().equals(ActionState.QUEUED) &&
                        action.getModified().toInstant().toEpochMilli() == modified.toInstant().toEpochMilli())
                .map(action -> requeueActionInvocation(action, deltaFile))
                .filter(Objects::nonNull)
                .toList();
    }

    public void requeueColdQueueActions(List<String> actionNames, int maxFiles) {
        OffsetDateTime modified = OffsetDateTime.now(clock);
        List<DeltaFile> requeuedDeltaFiles = deltaFileRepo.updateColdQueuedForRequeue(actionNames, maxFiles, modified);
        List<ActionInvocation> actionInvocations = requeuedDeltaFiles.stream()
                .map(deltaFile -> requeuedActionInvocations(deltaFile, modified))
                .flatMap(Collection::stream)
                .toList();
        if (!actionInvocations.isEmpty()) {
            log.warn("Moving " + actionInvocations.size() + " from the cold to warm queue");
            enqueueActions(actionInvocations, true);
        }
    }

    private ActionInvocation requeueActionInvocation(Action action, DeltaFile deltaFile) {
        ActionConfiguration actionConfiguration = actionConfiguration(action.getFlow(),
                action.getName(), deltaFile.getSourceInfo().getProcessingType(), deltaFile.getStage());

        if (Objects.isNull(actionConfiguration)) {
            String errorMessage = "Action named " + action.getName() + " is no longer running";
            log.error(errorMessage);
            ActionEvent event = ActionEvent.builder()
                    .did(deltaFile.getDid())
                    .flow(action.getFlow())
                    .action(action.getName())
                    .error(ErrorEvent.builder().cause(errorMessage).build())
                    .type(ActionEventType.UNKNOWN)
                    .build();
            error(deltaFile, event);

            return null;
        }

        if (deltaFile.isAggregate()) {
            return CollectingActionInvocation.builder()
                    .actionConfiguration(actionConfiguration)
                    .flow(action.getFlow())
                    .deltaFile(deltaFile)
                    .egressFlow(deltaFile.getStage() == DeltaFileStage.EGRESS ? action.getFlow() : null)
                    .actionCreated(action.getCreated())
                    .action(action)
                    .collectedDids(deltaFile.getParentDids())
                    .processingType(deltaFile.getSourceInfo().getProcessingType())
                    .stage(deltaFile.getStage())
                    .build();
        }

        return ActionInvocation.builder()
                .actionConfiguration(actionConfiguration)
                .flow(action.getFlow())
                .deltaFile(deltaFile)
                .egressFlow(egressFlow(action, deltaFile))
                .actionCreated(action.getCreated())
                .action(action)
                .build();
    }

    public void autoResume() {
        autoResume(OffsetDateTime.now(clock));
    }

    public int autoResume(OffsetDateTime timestamp) {
        int queued = 0;
        List<DeltaFile> autoResumeDeltaFiles = deltaFileRepo.findReadyForAutoResume(timestamp);
        if (!autoResumeDeltaFiles.isEmpty()) {
            Map<String, String> flowByDid = autoResumeDeltaFiles.stream()
                    .collect(Collectors.toMap(DeltaFile::getDid, d -> d.getSourceInfo().getFlow()));
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
                    log.error("Auto-resume: " + result.getError());
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
            tags.put(DeltaFiConstants.INGRESS_FLOW, flow);
            Metric metric = new Metric(name, count, tags);
            metricService.increment(metric);
        }
    }

    private ActionConfiguration actionConfiguration(String flow, String actionName, ProcessingType processingType,
            DeltaFileStage stage) {
        if (actionName.equals(NO_EGRESS_FLOW_CONFIGURED_ACTION)) {
            return null;
        }

        if (processingType == ProcessingType.TRANSFORMATION) {
            return transformFlowService.findActionConfig(flow, actionName);
        }

        return switch (stage) {
            case INGRESS -> normalizeFlowService.findActionConfig(flow, actionName);
            case ENRICH -> enrichFlowService.findActionConfig(flow, actionName);
            case EGRESS -> egressFlowService.findActionConfig(flow, actionName);
            default -> null;
        };
    }

    public boolean processActionEvents(String uniqueId) {
        try {
            while (!Thread.currentThread().isInterrupted() && processIncomingEvents) {
                processResult(actionEventQueue.takeResult(uniqueId));
            }
        } catch (Throwable e) {
            log.error("Error receiving event: " + e.getMessage());
            return false;
        }
        return true;
    }

    public void processResult(ActionEvent event) {
        if (event == null) throw new RuntimeException("ActionEventQueue returned null event.  This should NEVER happen");

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
                                log.warn("Retrying after OptimisticLockingFailureException caught processing " + event.getAction() + " for " + event.getDid());
                            }
                        } catch (Throwable e) {
                            StringWriter stackWriter = new StringWriter();
                            e.printStackTrace(new PrintWriter(stackWriter));
                            log.error("Exception processing incoming action event: " + "\n" + e.getMessage() + "\n" + stackWriter);
                            break;
                        }
                    }
                } finally {
                    semaphore.release();
                }
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Preserve interrupt status
            log.error("Thread interrupted while waiting for a permit to process action event: " + e.getMessage());
        }
    }

    private void enqueueActions(List<ActionInvocation> actionInvocations) throws EnqueueActionException {
        enqueueActions(actionInvocations, false);
    }

    private void enqueueActions(List<ActionInvocation> actionInvocations, boolean checkUnique) throws EnqueueActionException {
        if (actionInvocations.isEmpty()) {
            return;
        }

        String systemName = deltaFiPropertiesService.getDeltaFiProperties().getSystemName();

        List<ActionInput> actionInputs = actionInvocations.stream()
                .map(actionInvocation -> actionInvocation instanceof CollectingActionInvocation ?
                        buildCollectingActionInput((CollectingActionInvocation) actionInvocation, systemName) :
                        buildActionInput(actionInvocation, systemName))
                .toList();

        // Maintain backwards compatibility with legacy actions by combining flow and action
        actionInputs.forEach(actionInput -> actionInput.getActionContext().setName(
                actionInput.getActionContext().getFlow() + "." + actionInput.getActionContext().getName()));

        try {
            actionEventQueue.putActions(actionInputs, checkUnique);
        } catch (Exception e) {
            log.error("Failed to queue action(s)", e);
            throw new EnqueueActionException("Failed to queue action(s)", e);
        }
    }

    private ActionInput buildActionInput(ActionInvocation actionInvocation, String systemName) {
        return actionInvocation.getActionConfiguration().buildActionInput(actionInvocation.getFlow(),
                actionInvocation.getDeltaFile(), systemName, actionInvocation.getEgressFlow(),
                actionInvocation.getReturnAddress(), actionInvocation.getActionCreated(), actionInvocation.getAction(),
                null);
    }

    public boolean taskTimedIngress(String flowName, String memo, boolean overrideMemo) throws EnqueueActionException {
        TimedIngressFlow timedIngressFlow;
        if (overrideMemo) {
            // use the stored value so the cached memo value is not overwritten
            timedIngressFlow = timedIngressFlowService.getFlowOrThrow(flowName);
            if (!timedIngressFlow.isRunning()) {
                throw new IllegalStateException("Timed ingress flow '" + flowName + "' cannot be tasked while in a state of " + timedIngressFlow.getFlowStatus().getState());
            }
            timedIngressFlow.setMemo(memo);
        } else {
            timedIngressFlow = timedIngressFlowService.getRunningFlowByName(flowName);
        }

        return taskTimedIngress(timedIngressFlow);
    }

    public boolean taskTimedIngress(TimedIngressFlow timedIngressFlow) throws EnqueueActionException {
        ActionInput actionInput = timedIngressFlow.buildActionInput(getProperties().getSystemName());
        // Maintain compatibility with legacy actions by combining flow and name
        actionInput.getActionContext().setName(actionInput.getActionContext().getFlow() + "." +
                actionInput.getActionContext().getName());

        try {
            if (!actionEventQueue.queueHasTaskingForAction(actionInput)) {
                timedIngressFlowService.setLastRun(timedIngressFlow.getName(), OffsetDateTime.now(clock),
                        actionInput.getActionContext().getDid());
                actionEventQueue.putActions(List.of(actionInput), false);
                return true;
            } else {
                log.warn("Skipping queueing on {} for duplicate timed ingress action event: {}",
                        actionInput.getQueueName(), actionInput.getActionContext().getName());
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

    public List<String> domains() {
        return deltaFileRepo.domains();
    }

    public List<String> annotationKeys(String domain) {
        return deltaFileRepo.annotationKeys(domain);
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
        boolean allHaveFlowName = true;

        if (policyNames == null || policyNames.isEmpty()) {
            errors.add("Must provide one or more policy names");
        }

        if (errors.isEmpty()) {
            resumePolicyService.refreshCache();
            for (String policyName : new LinkedHashSet<>(policyNames)) {
                Optional<ResumePolicy> policy = resumePolicyService.findByName(policyName);
                if (policy.isPresent()) {
                    foundPolicies.add(policy.get());
                    if (policy.get().getFlow() == null) {
                        allHaveFlowName = false;
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
            if (!allHaveFlowName) {
                allErrorDeltaFiles = deltaFileRepo.findResumePolicyCandidates(null);
            }

            for (ResumePolicy resumePolicy : foundPolicies) {
                List<DeltaFile> checkFiles = allErrorDeltaFiles;
                if (resumePolicy.getFlow() != null) {
                    checkFiles = deltaFileRepo.findResumePolicyCandidates(resumePolicy.getFlow());
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
                resumePolicy.setId(UUID.randomUUID().toString());
            }
            errors.addAll(resumePolicy.validate());
        }

        if (errors.isEmpty()) {
            List<DeltaFile> checkFiles =
                    deltaFileRepo.findResumePolicyCandidates(resumePolicy.getFlow());

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

    private ActionInput buildCollectingActionInput(CollectingActionInvocation actionInvocation, String systemName) {
        try {
            List<DeltaFile> collectedDeltaFiles = findDeltaFiles(actionInvocation.getCollectedDids());

            DeltaFile aggregate = actionInvocation.getDeltaFile();

            if (aggregate == null) {
                OffsetDateTime now = OffsetDateTime.now(clock);

                aggregate = DeltaFile.builder()
                        .schemaVersion(DeltaFile.CURRENT_SCHEMA_VERSION)
                        .did(UUID.randomUUID().toString())
                        .parentDids(actionInvocation.getCollectedDids())
                        .aggregate(true)
                        .childDids(Collections.emptyList())
                        .stage(actionInvocation.getStage())
                        .sourceInfo(buildAggregateSourceInfo(collectedDeltaFiles.get(0).getSourceInfo()))
                        .created(now)
                        .modified(now)
                        .egressed(false)
                        .filtered(false)
                        .actions(List.of(actionInvocation.getAction()))
                        .build();

                deltaFileRepo.save(aggregate);
            }

            return actionInvocation.getActionConfiguration().buildCollectingActionInput(actionInvocation.getFlow(),
                    aggregate, collectedDeltaFiles, systemName, actionInvocation.getEgressFlow(),
                    actionInvocation.getActionCreated(), actionInvocation.getAction());
        } catch (MissingDeltaFilesException e) {
            throw new EnqueueActionException("Failed to queue collecting action", e);
        }
    }

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

    private SourceInfo buildAggregateSourceInfo(SourceInfo sourceInfo) {
        SourceInfo aggregateSourceInfo = new SourceInfo();
        aggregateSourceInfo.setFilename(AGGREGATE_SOURCE_FILE_NAME);
        aggregateSourceInfo.setFlow(sourceInfo.getFlow());
        aggregateSourceInfo.setProcessingType(sourceInfo.getProcessingType());
        return aggregateSourceInfo;
    }

    public void queueTimedOutCollect(CollectEntry collectEntry, List<String> collectedDids) {
        ActionConfiguration actionConfiguration = actionConfiguration(
                collectEntry.getCollectDefinition().getFlow(),
                collectEntry.getCollectDefinition().getAction(),
                collectEntry.getCollectDefinition().getProcessingType(),
                collectEntry.getCollectDefinition().getStage());

        if (actionConfiguration == null) {
            log.warn("Time-based collect action couldn't run because action {} in flow {} is no longer running",
                    collectEntry.getCollectDefinition().getAction(), collectEntry.getCollectDefinition().getFlow());
            return;
        }

        OffsetDateTime now = OffsetDateTime.now(clock);

        Action action = Action.builder()
                .name(collectEntry.getCollectDefinition().getAction())
                .type(collectEntry.getCollectDefinition().getActionType())
                .flow(collectEntry.getCollectDefinition().getFlow())
                .state(ActionState.QUEUED)
                .created(now)
                .queued(now)
                .modified(now)
                .build();

        CollectingActionInvocation collectingActionInvocation = CollectingActionInvocation.builder()
                .actionConfiguration(actionConfiguration)
                .flow(collectEntry.getCollectDefinition().getFlow())
                .egressFlow(collectEntry.getCollectDefinition().getStage() == DeltaFileStage.EGRESS ?
                        collectEntry.getCollectDefinition().getFlow() : null)
                .actionCreated(now)
                .action(action)
                .collectedDids(collectedDids)
                .processingType(collectEntry.getCollectDefinition().getProcessingType())
                .stage(collectEntry.getCollectDefinition().getStage())
                .build();

        enqueueActions(List.of(collectingActionInvocation));
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
                deltaFile.errorAction(collectEntry.getCollectDefinition().getFlow(),
                        collectEntry.getCollectDefinition().getAction(), now, now, "Failed collect", reason);
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

    private void completeCollect(ActionEvent event, DeltaFileStage eventStage, DeltaFile deltaFile) {
        ActionConfiguration actionConfiguration = actionConfiguration(event.getFlow(), event.getAction(),
                deltaFile.getSourceInfo().getProcessingType(), eventStage);
        if ((actionConfiguration != null) && (actionConfiguration.getCollect() != null)) {
            List<ActionInvocation> actionInvocations = new ArrayList<>();
            List<DeltaFile> parentDeltaFiles = deltaFileRepo.findAllById(deltaFile.getParentDids());
            for (DeltaFile parentDeltaFile : parentDeltaFiles) {
                parentDeltaFile.getChildDids().add(deltaFile.getDid());
                parentDeltaFile.collectedAction(event.getFlow(), event.getAction(), OffsetDateTime.now(clock),
                        OffsetDateTime.now(clock));
                actionInvocations.addAll(advanceOnly(parentDeltaFile, false, new HashMap<>()));
            }
            deltaFileRepo.saveAll(parentDeltaFiles);
            enqueueActions(actionInvocations);
        }
    }

    private static class Counter {
        int filteredFiles = 0;
        int erroredFiles = 0;
        long byteCount = 0L;
    }
}
