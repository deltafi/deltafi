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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.deltafi.common.action.ActionEventQueue;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.content.ContentUtil;
import org.deltafi.common.types.*;
import org.deltafi.core.audit.CoreAuditLogger;
import org.deltafi.core.collect.CollectDefinition;
import org.deltafi.core.collect.CollectEntry;
import org.deltafi.core.collect.CollectService;
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
import org.springframework.context.annotation.ConditionContext;
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
import java.util.concurrent.*;
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
    private final NormalizeFlowService normalizeFlowService;
    private final EnrichFlowService enrichFlowService;
    private final EgressFlowService egressFlowService;

    private final TransformFlowService transformFlowService;
    private final DeltaFiPropertiesService deltaFiPropertiesService;
    private final StateMachine stateMachine;
    private final DeltaFileRepo deltaFileRepo;
    private final ActionEventQueue actionEventQueue;
    private final ContentStorageService contentStorageService;
    private final ResumePolicyService resumePolicyService;
    private final MetricService metricService;
    private final CoreAuditLogger coreAuditLogger;
    private final CollectService collectService;
    private final IdentityService identityService;
    private final DidMutexService didMutexService;
    private final DeltaFileCacheService deltaFileCacheService;
    private final TimedIngressFlowService timedIngressFlowService;
    private final QueueManagementService queueManagementService;
    private final QueuedAnnotationRepo queuedAnnotationRepo;
    private final Environment environment;

    private ExecutorService executor;
    private Semaphore semaphore;

    private final ScheduledExecutorService timedOutCollectExecutor = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> timedOutCollectFuture;

    @PostConstruct
    private void init() {
        String scheduleActionEvents = environment.getProperty("schedule.actionEvents");
        if (scheduleActionEvents == null || scheduleActionEvents.equals("true")) {
            DeltaFiProperties properties = getProperties();
            int threadCount = properties.getCoreServiceThreads() > 0 ? properties.getCoreServiceThreads() : 16;
            executor = Executors.newFixedThreadPool(threadCount);
            log.info("Executors pool size: " + threadCount);
            int internalQueueSize = properties.getCoreInternalQueueSize() > 0 ? properties.getCoreInternalQueueSize() : 64;
            semaphore = new Semaphore(internalQueueSize);
            log.info("Internal queue size: " + internalQueueSize);

            scheduleCollectCheckForSoonestInRepository();
        }
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

    private List<DeltaFile> findDeltaFiles(List<String> dids) throws MissingDeltaFilesException {
        Map<String, DeltaFile> deltaFileMap = deltaFiles(dids);

        if (deltaFileMap.size() < dids.size()) {
            List<String> missingDids = dids.stream().filter(did -> !deltaFileMap.containsKey(did)).toList();
            if (!missingDids.isEmpty()) {
                throw new MissingDeltaFilesException(missingDids);
            }
        }

        return dids.stream().map(deltaFileMap::get).toList();
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
                .childDids(Collections.emptyList())
                .requeueCount(0)
                .ingressBytes(contentSize)
                .totalBytes(contentSize)
                .stage(DeltaFileStage.INGRESS)
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
            ActionConfiguration actionConfiguration = actionConfiguration(event.getFlow(), event.getAction(),
                    deltaFile.getSourceInfo().getProcessingType(), deltaFile.getStage());
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

            List<Metric> actionMetrics = new ArrayList<>();
            actionMetrics.add(Metric.builder()
                    .name(ACTION_EXECUTION_TIME_MS)
                    .value(Duration.between(event.getStart(), event.getStop()).toMillis())
                    .build());

            for (Metric actionMetric : actionMetrics) {
                actionMetric.addTags(tags);
                metricService.increment(actionMetric);
            }
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

        // array of length 1 is used to store the size in bytes
        final long[] bytes = {0L};
        ingressEvent.getIngressItems().forEach(ingressItem -> {
                    ingressItem.setFlow(timedIngressFlow.getTargetFlow());
                    ingressItem.setProcessingType(null);
                    DeltaFile deltaFile = ingressOrErrorOnMissingFlow(ingressItem, event.getAction(), event.getFlow(),
                            event.getStart(), event.getStop());
                    bytes[0] += deltaFile.getIngressBytes();
                });

        String actionClass =
         (timedIngressFlow.findActionConfigByName(event.getAction()) != null)
                ? timedIngressFlow.findActionConfigByName(event.getAction()).getType()
                 : null;

        List<Metric> metrics = (event.getMetrics() != null) ? event.getMetrics() : new ArrayList<>();
        metrics.add(new Metric(DeltaFiConstants.FILES_IN, ingressEvent.getIngressItems().size()));
        metrics.add(new Metric(DeltaFiConstants.BYTES_IN, bytes[0]));
        generateIngressMetrics(metrics, event, timedIngressFlow.getTargetFlow(), actionClass);
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
        ActionConfiguration actionConfiguration = actionConfiguration(event.getFlow(), event.getAction(),
                deltaFile.getSourceInfo().getProcessingType(), deltaFile.getStage());
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

        deltaFile.filterAction(event, event.getFilter().getMessage());
        deltaFile.setFiltered(true);

        advanceAndSave(deltaFile);
    }

    private void error(DeltaFile deltaFile, ActionEvent event) {
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
        ActionConfiguration actionConfiguration = actionConfiguration(event.getFlow(), event.getAction(),
                deltaFile.getSourceInfo().getProcessingType(), deltaFile.getStage());
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
                    .map(childLoadEvent -> buildLoadManyChildAndEnqueue(deltaFile, event, childLoadEvent, actionInvocations, now))
                    .toList();

            deltaFile.reinjectAction(event);
        }

        advanceOnly(deltaFile, false);

        // do this in two shots.  saveAll performs a bulk insert, but only if all the entries are new
        deltaFileCacheService.save(deltaFile);
        deltaFileRepo.saveAll(childDeltaFiles);

        actionInvocations.addAll(processReadyToCollectActions(deltaFile));
        childDeltaFiles.forEach(childDeltaFile -> actionInvocations.addAll(processReadyToCollectActions(childDeltaFile)));

        enqueueActions(actionInvocations);
    }

    private DeltaFile buildLoadManyChildAndEnqueue(DeltaFile parentDeltaFile, ActionEvent actionEvent,
            ChildLoadEvent childLoadEvent, List<ActionInvocation> actionInvocations, OffsetDateTime now) {
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

        actionInvocations.addAll(advanceOnly(child, true));

        child.recalculateBytes();

        return child;
    }

    public void reinject(DeltaFile deltaFile, ActionEvent event) throws MissingEgressFlowException {
        List<ReinjectEvent> reinjects = event.getReinject();
        List<DeltaFile> childDeltaFiles = new ArrayList<>();
        boolean encounteredError = false;
        List<ActionInvocation> actionInvocations = new ArrayList<>();

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

                actionInvocations.addAll(advanceOnly(child, true));

                child.recalculateBytes();

                childDeltaFiles.add(child);
            }

            if (!encounteredError) {
                deltaFile.setChildDids(childDeltaFiles.stream().map(DeltaFile::getDid).toList());
            }

            deltaFile.reinjectAction(event);
        }

        advanceOnly(deltaFile, false);

        // do this in two shots.  saveAll performs a bulk insert, but only if all the entries are new
        deltaFileCacheService.save(deltaFile);
        if (!encounteredError) {
            deltaFileRepo.saveAll(childDeltaFiles);

            actionInvocations.addAll(processReadyToCollectActions(deltaFile));
            childDeltaFiles.forEach(childDeltaFile -> actionInvocations.addAll(processReadyToCollectActions(childDeltaFile)));

            enqueueActions(actionInvocations);
        }
    }

    public void formatMany(DeltaFile deltaFile, ActionEvent event) throws MissingEgressFlowException {
        List<ChildFormatEvent> childFormatEvents = event.getFormatMany();
        List<DeltaFile> childDeltaFiles = Collections.emptyList();
        List<ActionInvocation> actionInvocations = new ArrayList<>();

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

                actionInvocations.addAll(advanceOnly(child, true));

                child.recalculateBytes();

                return child;
            }).toList();

            deltaFile.reinjectAction(event);
        }

        advanceOnly(deltaFile, false);

        // do this in two shots.  saveAll performs a bulk insert, but only if all the entries are new
        deltaFileCacheService.save(deltaFile);
        deltaFileRepo.saveAll(childDeltaFiles);

        actionInvocations.addAll(processReadyToCollectActions(deltaFile));
        childDeltaFiles.forEach(childDeltaFile -> actionInvocations.addAll(processReadyToCollectActions(childDeltaFile)));

        enqueueActions(actionInvocations);
    }

    public void splitForTransformationProcessingEgress(DeltaFile deltaFile) throws MissingEgressFlowException {
        List<ActionInvocation> actionInvocations = new ArrayList<>();

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

            actionInvocations.addAll(advanceOnly(child, true));

            child.recalculateBytes();

            return child;
        }).toList();

        deltaFile.setLastActionReinjected();

        advanceOnly(deltaFile, false);

        // do this in two shots.  saveAll performs a bulk insert, but only if all the entries are new
        deltaFileCacheService.save(deltaFile);
        deltaFileRepo.saveAll(childDeltaFiles);

        actionInvocations.addAll(processReadyToCollectActions(deltaFile));
        childDeltaFiles.forEach(childDeltaFile -> actionInvocations.addAll(processReadyToCollectActions(childDeltaFile)));

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

                                advanceAndSaveDeltaFiles.add(deltaFile);
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
                                    .childDids(Collections.emptyList())
                                    .requeueCount(0)
                                    .ingressBytes(deltaFile.getIngressBytes())
                                    .stage(DeltaFileStage.INGRESS)
                                    .actions(new ArrayList<>(List.of(action)))
                                    .sourceInfo(deltaFile.getSourceInfo())
                                    .created(now)
                                    .modified(now)
                                    .egressed(false)
                                    .filtered(false)
                                    .build();

                            applyRetryOverrides(child, replaceFilename, replaceFlow, removeSourceMetadata, replaceSourceMetadata);

                            actionInvocations.addAll(advanceOnly(child, true));

                            child.recalculateBytes();
                            deltaFile.setReplayed(now);
                            deltaFile.setReplayDid(child.getDid());
                            if (Objects.isNull(deltaFile.getChildDids())) {
                                deltaFile.setChildDids(new ArrayList<>());
                            }
                            deltaFile.getChildDids().add(child.getDid());
                            childDeltaFiles.add(child);
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

        parentDeltaFiles.forEach(parentDeltaFile -> actionInvocations.addAll(processReadyToCollectActions(parentDeltaFile)));
        childDeltaFiles.forEach(childDeltaFile -> actionInvocations.addAll(processReadyToCollectActions(childDeltaFile)));

        enqueueActions(actionInvocations);

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
                        } else if (deltaFile.inactiveStage()) {
                            result.setSuccess(false);
                            result.setError("DeltaFile with did " + did + " is no longer active");
                        } else {
                            deltaFile.cancelQueuedActions();
                            deltaFile.setStage(DeltaFileStage.CANCELLED);
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
     * @return list of next pending action(s)
     * @throws MissingEgressFlowException if state machine would advance DeltaFile into EGRESS stage but no EgressFlow was configured.
     */
    private List<ActionInvocation> advanceOnly(DeltaFile deltaFile, boolean newDeltaFile) throws MissingEgressFlowException {
        // MissingEgressFlowException is not expected when a DeltaFile is entering the INGRESS stage
        // such as from replay or reinject, since an ingress flow requires at least the Load action to
        // be queued, nor when handling an event for any egress flow action, e.g. format.

        return stateMachine.advance(deltaFile, newDeltaFile);
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

        deltaFileCacheService.save(deltaFile);

        actionInvocations.addAll(processReadyToCollectActions(deltaFile));

        if (!actionInvocations.isEmpty()) {
            enqueueActions(actionInvocations);
        }
    }

    public void advanceAndSave(DeltaFile deltaFile) {
        try {
            List<ActionInvocation> actionInvocations = new ArrayList<>(stateMachine.advance(deltaFile));

            deltaFileCacheService.save(deltaFile);

            actionInvocations.addAll(processReadyToCollectActions(deltaFile));

            if (!actionInvocations.isEmpty()) {
                enqueueActions(actionInvocations);
            }
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
            } catch (MissingEgressFlowException e) {
                handleMissingEgressFlow(deltaFile);
            }
        });

        deltaFileRepo.saveAll(deltaFiles);

        deltaFiles.forEach(deltaFile -> actionInvocations.addAll(processReadyToCollectActions(deltaFile)));

        if (!actionInvocations.isEmpty()) {
            enqueueActions(actionInvocations);
        }
    }

    private List<ActionInvocation> processReadyToCollectActions(DeltaFile deltaFile) {
        return deltaFile.readyToCollectActions().stream()
                .map(readyToCollectAction -> processReadyToCollectAction(readyToCollectAction, deltaFile))
                .filter(Objects::nonNull)
                .toList();
    }

    private ActionInvocation processReadyToCollectAction(Action action, DeltaFile deltaFile) {
        log.debug("Collecting DeltaFile with id {} for action {} of flow {}", deltaFile.getDid(), action.getName(),
                action.getFlow());

        ActionConfiguration actionConfiguration = actionConfiguration(action.getFlow(), action.getName(),
                deltaFile.getSourceInfo().getProcessingType(), deltaFile.getStage());

        if (actionConfiguration == null) {
            return null;
        }

        String collectGroup = actionConfiguration.getCollect().metadataKey() == null ? "DEFAULT" :
                deltaFile.getMetadata().getOrDefault(actionConfiguration.getCollect().metadataKey(), "DEFAULT");

        CollectDefinition collectDefinition = new CollectDefinition(deltaFile.getSourceInfo().getProcessingType(),
                deltaFile.getStage(), action.getFlow(), action.getName(), collectGroup);

        CollectEntry collectEntry = collectService.upsertAndLock(collectDefinition,
                OffsetDateTime.now(clock).plus(actionConfiguration.getCollect().maxAge()),
                actionConfiguration.getCollect().minNum(), actionConfiguration.getCollect().maxNum(),
                deltaFile.getDid());

        if (collectEntry == null) {
            log.debug("Timed out trying to lock collect entry {}", collectDefinition);
            deltaFile.setStage(DeltaFileStage.ERROR);
            deltaFileCacheService.save(deltaFile);
            return null;
        }

        log.debug("Updated collect entry with {} DeltaFiles", collectEntry.getCount());

        deltaFile.collectingAction(action.getFlow(), action.getName(), OffsetDateTime.now(clock),
                OffsetDateTime.now(clock));
        deltaFileRepo.save(deltaFile);
        deltaFileCacheService.remove(deltaFile.getDid());

        if (collectEntry.getCount() < actionConfiguration.getCollect().maxNum()) {
            if (collectEntry.getCount() == 1) { // Only update collect check for new collect entries
                updateCollectCheck(collectEntry.getCollectDate());
            }
            collectService.unlock(collectEntry.getId());
            return null;
        }

        ActionInvocation actionInvocation = buildCollectActionInvocation(collectEntry);

        scheduleCollectCheckForSoonestInRepository();

        return actionInvocation;
    }

    private void updateCollectCheck(OffsetDateTime collectDate) {
        if ((timedOutCollectFuture == null) || timedOutCollectFuture.isDone() || collectDate.isBefore(
                OffsetDateTime.now(clock).plusSeconds(timedOutCollectFuture.getDelay(TimeUnit.SECONDS)))) {
            scheduleCollectCheck(collectDate);
        }
    }

    private ActionInvocation buildCollectActionInvocation(CollectEntry collectEntry) {
        List<String> collectedDids = collectService.findCollectedDids(collectEntry.getId());

        List<DeltaFile> collectedDeltaFiles;
        try {
            collectedDeltaFiles = findDeltaFiles(collectedDids);
        } catch (MissingDeltaFilesException e) {
            failCollectAction(collectEntry, collectedDids, e.getMessage());
            return null;
        }

        ActionConfiguration actionConfiguration = actionConfiguration(collectEntry.getCollectDefinition().getFlow(),
                collectEntry.getCollectDefinition().getAction(),
                collectEntry.getCollectDefinition().getProcessingType(),
                collectEntry.getCollectDefinition().getStage());
        if (actionConfiguration == null) {
            failCollectAction(collectEntry, collectedDids, String.format("Action configuration for collecting action " +
                    "(%s) in flow (%s) removed", collectEntry.getCollectDefinition().getAction(),
                    collectEntry.getCollectDefinition().getFlow()));
            return null;
        }

        log.debug("Queuing collect action with {} entries", collectedDids.size());

        OffsetDateTime now = OffsetDateTime.now(clock);

        DeltaFile aggregate = DeltaFile.builder()
                .schemaVersion(DeltaFile.CURRENT_SCHEMA_VERSION)
                .did(UUID.randomUUID().toString())
                .parentDids(collectedDids)
                .aggregate(true)
                .childDids(Collections.emptyList())
                .stage(collectEntry.getCollectDefinition().getStage())
                .sourceInfo(buildAggregateSourceInfo(collectedDeltaFiles.get(0).getSourceInfo()))
                .created(now)
                .modified(now)
                .egressed(false)
                .filtered(false)
                .build();

        Action action = aggregate.queueNewAction(collectEntry.getCollectDefinition().getFlow(),
                collectEntry.getCollectDefinition().getAction(), actionConfiguration.getActionType(),
                queueManagementService.coldQueue(actionConfiguration.getType()));
        action.setMetadata(collectedDeltaFiles.stream()
                .map(DeltaFile::getMetadata)
                .flatMap(m -> m.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (first, second) -> first)));

        deltaFileRepo.save(aggregate);

        for (DeltaFile collectedDeltaFile : collectedDeltaFiles) {
            collectedDeltaFile.getChildDids().add(aggregate.getDid());
            collectedDeltaFile.collectedAction(collectEntry.getCollectDefinition().getFlow(),
                    collectEntry.getCollectDefinition().getAction(), OffsetDateTime.now(clock),
                    OffsetDateTime.now(clock));
            advanceAndSave(collectedDeltaFile);
        }

        collectService.delete(collectEntry.getId());

        return ActionInvocation.builder()
                .actionConfiguration(actionConfiguration)
                .flow(collectEntry.getCollectDefinition().getFlow())
                .deltaFile(aggregate)
                .deltaFiles(collectedDeltaFiles)
                .egressFlow(collectEntry.getCollectDefinition().getStage() == DeltaFileStage.EGRESS ?
                        collectEntry.getCollectDefinition().getFlow() : null)
                .returnAddress(identityService.getUniqueId())
                .actionCreated(now)
                .action(action)
                .build();
    }

    public static final String AGGREGATE_SOURCE_FILE_NAME = "multiple";

    private SourceInfo buildAggregateSourceInfo(SourceInfo sourceInfo) {
        SourceInfo aggregateSourceInfo = new SourceInfo();
        aggregateSourceInfo.setFilename(AGGREGATE_SOURCE_FILE_NAME);
        aggregateSourceInfo.setFlow(sourceInfo.getFlow());
        aggregateSourceInfo.setProcessingType(sourceInfo.getProcessingType());
        return aggregateSourceInfo;
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
    public boolean delete(OffsetDateTime createdBefore, OffsetDateTime completedBefore, Long minBytes, String flow, String policy, boolean deleteMetadata) {
        int batchSize = deltaFiPropertiesService.getDeltaFiProperties().getDelete().getPolicyBatchSize();
        List<DeltaFile> deltaFiles = deltaFileRepo.findForDelete(createdBefore, completedBefore, minBytes, flow, policy, deleteMetadata, batchSize);
        delete(deltaFiles, policy, deleteMetadata);

        return deltaFiles.size() == batchSize;
    }

    public List<DeltaFile> delete(long bytesToDelete, String flow, String policy, boolean deleteMetadata) {
        int batchSize = deltaFiPropertiesService.getDeltaFiProperties().getDelete().getPolicyBatchSize();

        log.info("Searching for batch of up to " + batchSize + " deltaFiles to delete for policy " + policy);
        return delete(deltaFileRepo.findForDelete(bytesToDelete, flow, policy, batchSize), policy, deleteMetadata);
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
        List<DeltaFile> requeuedDeltaFiles = deltaFileRepo.updateForRequeue(modified, getProperties().getRequeueSeconds(), queueManagementService.coldQueueActions(), longRunningDids);
        List<ActionInvocation> actions = requeuedDeltaFiles.stream()
                .map(deltaFile -> requeuedActionInvocations(deltaFile, modified))
                .flatMap(Collection::stream)
                .toList();
        if (!actions.isEmpty()) {
            log.warn(actions.size() + " actions exceeded requeue threshold of " + getProperties().getRequeueSeconds() + " seconds, requeuing now");
            enqueueActions(actions, true);
        }
    }

    List<ActionInvocation> requeuedActionInvocations(DeltaFile deltaFile, OffsetDateTime modified) {
        return deltaFile.getActions().stream()
                .filter(action -> action.getState().equals(ActionState.QUEUED) && action.getModified().toInstant().toEpochMilli() == modified.toInstant().toEpochMilli())
                .map(action -> requeueActionInvocation(action, deltaFile))
                .filter(Objects::nonNull)
                .toList();
    }

    public void requeueColdQueueActions(List<String> actionNames, int maxFiles) {
        OffsetDateTime modified = OffsetDateTime.now(clock);
        List<DeltaFile> requeuedDeltaFiles = deltaFileRepo.updateColdQueuedForRequeue(actionNames, maxFiles, modified);
        List<ActionInvocation> actions = requeuedDeltaFiles.stream()
                .map(deltaFile -> requeuedColdQueueActionInvocations(deltaFile, modified))
                .flatMap(Collection::stream)
                .toList();
        if (!actions.isEmpty()) {
            log.warn("Moving " + actions.size() + " from the cold to warm queue");
            enqueueActions(actions, true);
        }
    }

    List<ActionInvocation> requeuedColdQueueActionInvocations(DeltaFile deltaFile, OffsetDateTime modified) {
        return deltaFile.getActions().stream()
                .filter(a -> a.getState().equals(ActionState.QUEUED) && a.getModified().toInstant().toEpochMilli() == modified.toInstant().toEpochMilli())
                .map(action -> requeueActionInvocation(action, deltaFile))
                .filter(Objects::nonNull)
                .toList();
    }

    private ActionInvocation requeueActionInvocation(Action action, DeltaFile deltaFile) {
        ActionConfiguration actionConfiguration = actionConfiguration(action.getFlow(), action.getName(),
                deltaFile.getSourceInfo().getProcessingType(), deltaFile.getStage());

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
            while (!Thread.currentThread().isInterrupted()) {
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
        String systemName = getProperties().getSystemName();

        List<ActionInput> actionInputs = actionInvocations.stream()
                .map(actionInvocation -> {
                    ActionConfiguration config = actionInvocation.getActionConfiguration();
                    String flow = actionInvocation.getFlow();
                    DeltaFile deltaFile = actionInvocation.getDeltaFile();
                    String egressFlow = actionInvocation.getEgressFlow();
                    String returnAddress = actionInvocation.getReturnAddress();
                    OffsetDateTime actionCreated = actionInvocation.getActionCreated();
                    Action action = actionInvocation.getAction();

                    if (actionInvocation.getDeltaFiles() != null) {
                        return config.buildCollectionActionInput(flow, deltaFile, actionInvocation.getDeltaFiles(), systemName, egressFlow, returnAddress, actionCreated, action);
                    }

                    if (deltaFile.isAggregate() && (config.getCollect() != null)) {
                        try {
                            List<DeltaFile> deltaFiles = findDeltaFiles(deltaFile.getParentDids());
                            return config.buildCollectionActionInput(flow, deltaFile, deltaFiles, systemName, egressFlow, returnAddress, actionCreated, action);
                        } catch (MissingDeltaFilesException e) {
                            throw new EnqueueActionException("Failed to queue collecting action", e);
                        }
                    }

                    ActionInput input = config.buildActionInput(flow, deltaFile, systemName, egressFlow, returnAddress, actionCreated, action, null);
                    input.getActionContext().setName(flow + "." + input.getActionContext().getName());
                    return input;
                })
                .toList();

        try {
            actionEventQueue.putActions(actionInputs, checkUnique);
        } catch (Exception e) {
            log.error("Failed to queue action(s)", e);
            throw new EnqueueActionException("Failed to queue action(s)", e);
        }
    }

    public void taskTimedIngress(TimedIngressFlow timedIngressFlow) throws EnqueueActionException {
        ActionInput actionInput = timedIngressFlow.buildActionInput(getProperties().getSystemName());
        // Maintain compatibility with legacy actions by combining flow and name
        actionInput.getActionContext().setName(actionInput.getActionContext().getFlow() + "." +
                    actionInput.getActionContext().getName());

        try {
            if (!actionEventQueue.queueHasTaskingForAction(actionInput)) {
                timedIngressFlowService.setLastRun(timedIngressFlow.getName(), OffsetDateTime.now(clock),
                        actionInput.getActionContext().getDid());
                actionEventQueue.putActions(List.of(actionInput), false);
            } else {
                log.warn("Skipping queueing on {} for duplicate timed ingress action event: {}",
                        actionInput.getQueueName(), actionInput.getActionContext().getName());
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

        for (DeltaFile deltaFile : deltaFiles) {
            coreAuditLogger.logDelete(policy, deltaFile.getDid(), deleteMetadata);
        }
    }

    private void deleteMetadata(List<DeltaFile> deltaFiles) {
        deltaFileRepo.deleteByDidIn(deltaFiles.stream().map(DeltaFile::getDid).distinct().toList());

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

    private void scheduleCollectCheckForSoonestInRepository() {
        List<CollectEntry> collectEntries = collectService.findAllByOrderByCollectDate();
        if (collectEntries.isEmpty()) {
            cancelCollectCheck();
            return;
        }

        scheduleCollectCheck(collectEntries.get(0).getCollectDate());
    }

    private void cancelCollectCheck() {
        if (timedOutCollectFuture != null) {
            timedOutCollectFuture.cancel(false);
        }
    }

    private void scheduleCollectCheck(OffsetDateTime collectDate) {
        cancelCollectCheck();
        log.debug("Scheduling next collect check in {} seconds",
                Math.max(collectDate.toEpochSecond() - OffsetDateTime.now(clock).toEpochSecond(), 1));
        timedOutCollectFuture = timedOutCollectExecutor.schedule(this::handleTimedOutCollects,
                Math.max(collectDate.toEpochSecond() - OffsetDateTime.now(clock).toEpochSecond(), 1), TimeUnit.SECONDS);
    }

    private void handleTimedOutCollects() {
        log.debug("Handling timed out collects");

        CollectEntry collectEntry = collectService.lockOneBefore(OffsetDateTime.now(clock));
        while (collectEntry != null) {
            if ((collectEntry.getMinNum() != null) && (collectEntry.getCount() < collectEntry.getMinNum())) {
                failCollectAction(collectEntry, collectService.findCollectedDids(collectEntry.getId()),
                        String.format("Collect incomplete: Timed out after receiving %s of %s files",
                                collectEntry.getCount(), collectEntry.getMinNum()));
            } else {
                ActionInvocation actionInvocation = buildCollectActionInvocation(collectEntry);
                if (actionInvocation != null) {
                    enqueueActions(List.of(actionInvocation));
                }
            }
            collectEntry = collectService.lockOneBefore(OffsetDateTime.now(clock));
        }

        scheduleCollectCheckForSoonestInRepository();
    }

    private void failCollectAction(CollectEntry collectEntry, List<String> collectedDids, String reason) {
        log.debug("Failing collect action");

        List<String> missingDids = new ArrayList<>();

        for (String did : collectedDids) {
            try {
                if (!saveFailedCollectAction(collectEntry, did, reason)) {
                    missingDids.add(did);
                }
            } catch (OptimisticLockingFailureException e) {
                log.warn("Unable to save DeltaFile with failed collect action", e);
            }
        }

        if (!missingDids.isEmpty()) {
            log.warn("DeltaFiles with the following ids were missing during failed collect: {}", missingDids);
        }

        collectService.delete(collectEntry.getId());
    }

    public boolean saveFailedCollectAction(CollectEntry collectEntry, String did, String reason) {
        DeltaFile deltaFileToCollect = getDeltaFile(did);

        if (deltaFileToCollect == null) {
            return false;
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        deltaFileToCollect.errorAction(collectEntry.getCollectDefinition().getFlow(),
                collectEntry.getCollectDefinition().getAction(), now, now, "Failed collect", reason);
        deltaFileToCollect.setStage(DeltaFileStage.ERROR);
        deltaFileRepo.save(deltaFileToCollect);

        return true;
    }

    public void unlockTimedOutCollectEntryLocks() {
        long numUnlocked = collectService.unlockBefore(
                OffsetDateTime.now(clock).minus(getProperties().getCollect().getMaxLockDuration()));

        if (numUnlocked > 0) {
            log.warn("Unlocked {} timed out collect entries", numUnlocked);

            scheduleCollectCheckForSoonestInRepository();
        }
    }
}
