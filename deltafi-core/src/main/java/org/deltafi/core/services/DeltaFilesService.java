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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.deltafi.common.action.ActionEventQueue;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.content.ContentUtil;
import org.deltafi.common.types.*;
import org.deltafi.core.exceptions.*;
import org.deltafi.core.metrics.MetricService;
import org.deltafi.core.metrics.MetricsUtil;
import org.deltafi.core.audit.CoreAuditLogger;
import org.deltafi.core.configuration.DeltaFiProperties;
import org.deltafi.core.generated.types.*;
import org.deltafi.core.repo.DeltaFileRepo;
import org.deltafi.core.retry.MongoRetryable;
import org.deltafi.core.types.*;
import org.jetbrains.annotations.NotNull;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.deltafi.common.constant.DeltaFiConstants.*;
import static org.deltafi.core.repo.DeltaFileRepoImpl.SOURCE_INFO_METADATA;

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

    public static final String NO_EGRESS_CONFIGURED_CAUSE = "No egress flow configured";
    public static final String NO_EGRESS_CONFIGURED_CONTEXT = "This DeltaFile does not match the criteria of any running egress flows";
    public static final String NO_CHILD_INGRESS_CONFIGURED_CAUSE = "No child ingress flow configured";
    public static final String NO_CHILD_INGRESS_CONFIGURED_CONTEXT = "This DeltaFile reinject does not match any running ingress flows: ";

    private static final int DEFAULT_QUERY_LIMIT = 50;

    private final Clock clock;
    private final IngressFlowService ingressFlowService;
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
    private final IdentityService identityService;
    private final DidMutexService didMutexService;
    private final DeltaFileCacheService deltaFileCacheService;

    private ExecutorService executor;

    @PostConstruct
    private void init() {
        DeltaFiProperties properties = getProperties();
        int threadCount = properties.getCoreServiceThreads() > 0 ? properties.getCoreServiceThreads() : 16;
        executor = Executors.newFixedThreadPool(threadCount);
        log.info("Executors pool size: " + threadCount);
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

    public DeltaFiles deltaFiles(Integer offset, Integer limit, DeltaFilesFilter filter, DeltaFileOrder orderBy) {
        return deltaFiles(offset, limit, filter, orderBy, null);
    }

    public DeltaFiles deltaFiles(Integer offset, Integer limit, DeltaFilesFilter filter, DeltaFileOrder orderBy, List<String> includeFields) {
        return deltaFileRepo.deltaFiles(offset, (Objects.nonNull(limit) && limit > 0) ? limit : DEFAULT_QUERY_LIMIT, filter, orderBy, includeFields);
    }

    public Map<String, DeltaFile> deltaFiles(List<String> dids) {
        Iterable<DeltaFile> deltaFilesIter = deltaFileRepo.findAllById(dids);
        return StreamSupport.stream(deltaFilesIter.spliterator(), false)
                .collect(Collectors.toMap(DeltaFile::getDid, d -> d));
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

    public DeltaFile ingress(IngressEvent ingressEvent) {
        return ingress(ingressEvent, Collections.emptyList());
    }

    public DeltaFile ingress(IngressEvent ingressEvent, List<String> parentDids) {
        SourceInfo sourceInfo = ingressEvent.getSourceInfo();

        OffsetDateTime now = OffsetDateTime.now(clock);

        Action ingressAction = Action.newBuilder()
                .name(INGRESS_ACTION)
                .flow(ingressEvent.getSourceInfo().getFlow())
                .type(ActionType.INGRESS)
                .state(ActionState.COMPLETE)
                .created(ingressEvent.getCreated())
                .modified(now)
                .content(ingressEvent.getContent())
                .metadata(ingressEvent.getSourceInfo().getMetadata())
                .build();

        long contentSize = ContentUtil.computeContentSize(ingressEvent.getContent());

        DeltaFile deltaFile = DeltaFile.newBuilder()
                .schemaVersion(DeltaFile.CURRENT_SCHEMA_VERSION)
                .did(ingressEvent.getDid())
                .parentDids(parentDids)
                .childDids(Collections.emptyList())
                .requeueCount(0)
                .ingressBytes(contentSize)
                .totalBytes(contentSize)
                .stage(DeltaFileStage.INGRESS)
                .actions(new ArrayList<>(List.of(ingressAction)))
                .sourceInfo(sourceInfo)
                .domains(Collections.emptyList())
                .enrichments(Collections.emptyList())
                .created(ingressEvent.getCreated())
                .modified(now)
                .egressed(false)
                .filtered(false)
                .build();

        advanceAndSave(deltaFile);
        return deltaFile;
    }

    public void handleActionEvent(ActionEvent event) throws JsonProcessingException {
        if (StringUtils.isEmpty(event.getDid())) {
            throw new InvalidActionEventException("Missing did: " + OBJECT_MAPPER.writeValueAsString(event));
        } else if (StringUtils.isEmpty(event.getAction())) {
            throw new InvalidActionEventException("Missing action: " + OBJECT_MAPPER.writeValueAsString(event));
        }
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
                event.setError(ErrorEvent.newBuilder().cause(INVALID_ACTION_EVENT_RECEIVED)
                        .context(validationError + ": " +
                                OBJECT_MAPPER.writeValueAsString(event)).build());
                error(deltaFile, event);
                return;
            }

            deltaFile.ensurePendingAction(event.getAction());

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
                    egress(deltaFile, event);
                    metrics.add(
                            Metric.builder()
                                    .name(EXECUTION_TIME_MS)
                                    .value(Duration.between(deltaFile.getCreated(), deltaFile.getModified()).toMillis())
                                    .build());
                    generateMetrics(metrics, event, deltaFile);
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
                default -> throw new UnknownTypeException(event.getAction(), event.getDid(), event.getType());
            }
        }
    }

    private void generateMetrics(List<Metric> metrics, ActionEvent event, DeltaFile deltaFile) {
        String egressFlow = egressFlow(event.getAction(), deltaFile);
        Map<String, String> defaultTags = MetricsUtil.tagsFor(event.getType(), event.getAction(), deltaFile.getSourceInfo().getFlow(), egressFlow);
        for(Metric metric : metrics) {
            metric.addTags(defaultTags);
            metricService.increment(metric);
        }
    }

    public void transform(DeltaFile deltaFile, ActionEvent event) {
        TransformEvent transformEvent = event.getTransform();
        deltaFile.completeAction(event, transformEvent.getContent(), transformEvent.getMetadata(), transformEvent.getDeleteMetadataKeys());

        deltaFile.addAnnotations(transformEvent.getAnnotations());

        if (deltaFile.getSourceInfo().getProcessingType() == ProcessingType.TRANSFORMATION) {
            advanceAndSaveTransformationProcessing(deltaFile);
        } else {
            advanceAndSave(deltaFile);
        }
    }

    public void load(DeltaFile deltaFile, ActionEvent event) {
        LoadEvent loadEvent = event.getLoad();
        deltaFile.completeAction(event, loadEvent.getContent(), loadEvent.getMetadata(), loadEvent.getDeleteMetadataKeys());

        if (loadEvent.getDomains() != null) {
            for (Domain domain : loadEvent.getDomains()) {
                deltaFile.addDomain(domain.getName(), domain.getValue(), domain.getMediaType());
            }
        }
        deltaFile.addAnnotations(loadEvent.getAnnotations());

        advanceAndSave(deltaFile);
    }

    public void domain(DeltaFile deltaFile, ActionEvent event) {
        deltaFile.completeAction(event);

        deltaFile.addAnnotations(event.getDomain().getAnnotations());

        advanceAndSave(deltaFile);
    }

    public void enrich(DeltaFile deltaFile, ActionEvent event) {
        deltaFile.completeAction(event);

        if (null != event.getEnrich().getEnrichments()) {
            for (Enrichment enrichment : event.getEnrich().getEnrichments()) {
                deltaFile.addEnrichment(enrichment.getName(), enrichment.getValue(), enrichment.getMediaType());
            }
        }

        deltaFile.addAnnotations(event.getEnrich().getAnnotations());

        advanceAndSave(deltaFile);
    }

    public void format(DeltaFile deltaFile, ActionEvent event) {
        if (event.getFormat().getContent() == null) {
            event.setError(ErrorEvent.newBuilder().cause("Received format event with no content from " + event.getAction()).build());
            error(deltaFile, event);
            return;
        }

        FormatEvent formatEvent = event.getFormat();
        deltaFile.completeAction(event, List.of(formatEvent.getContent()), formatEvent.getMetadata(), Collections.emptyList());

        advanceAndSave(deltaFile);
    }

    public void validate(DeltaFile deltaFile, ActionEvent event) {
        deltaFile.completeAction(event);

        advanceAndSave(deltaFile);
    }

    public void egress(DeltaFile deltaFile, ActionEvent event) {
        String flowName = FlowService.getFlowName(event.getAction());

        // replicate the message that was sent to this action so we can republish the content and metadata that was processed
        DeltaFileMessage sentMessage = deltaFile.forQueue(flowName);

        deltaFile.completeAction(event, sentMessage.getContentList(), sentMessage.getMetadata(), Collections.emptyList());
        deltaFile.setEgressed(true);

        Set<String> expectedAnnotations = getPendingAnnotations(deltaFile.getSourceInfo().getProcessingType(), flowName);

        if (expectedAnnotations != null && !expectedAnnotations.isEmpty()) {
            deltaFile.addPendingAnnotationsForFlow(flowName);
        }

        advanceAndSave(deltaFile);
    }

    public void filter(DeltaFile deltaFile, ActionEvent event) {
        ActionConfiguration actionConfiguration = actionConfiguration(event.getAction(), deltaFile);
        ActionType actionType = ActionType.UNKNOWN;
        if (actionConfiguration != null) {
            actionType = actionConfiguration.getActionType();
        }

        // Treat filter events from Domain and Enrich actions as errors
        if (actionType.equals(ActionType.DOMAIN) || actionType.equals(ActionType.ENRICH)) {
            event.setError(ErrorEvent.newBuilder().cause("Illegal operation FILTER received from " + actionType + "Action " + event.getAction()).build());
            error(deltaFile, event);
            return;
        } else {
            deltaFile.filterAction(event, event.getFilter().getMessage());
            deltaFile.setFiltered(true);
        }

        advanceAndSave(deltaFile);
    }

    @MongoRetryable
    public void error(DeltaFile deltaFile, ActionEvent event) {
        // If the content was deleted by a delete policy mark as CANCELLED instead of ERROR
        if (deltaFile.getContentDeleted() != null) {
            deltaFile.cancelQueuedActions();
            deltaFile.setStage(DeltaFileStage.CANCELLED);
            deltaFileCacheService.save(deltaFile);
        } else {
            advanceAndSave(processErrorEvent(deltaFile, event));
        }
    }

    @MongoRetryable
    public DeltaFile processErrorEvent(DeltaFile deltaFile, ActionEvent event) {
        deltaFile.ensurePendingAction(event.getAction());

        Optional<ResumePolicyService.ResumeDetails> resumeDetails = Optional.empty();
        ActionConfiguration actionConfiguration = actionConfiguration(event.getAction(), deltaFile);
        if (actionConfiguration != null) {
            resumeDetails = resumePolicyService.getAutoResumeDelay(deltaFile, event, actionConfiguration.getActionType().name());
        }
        resumeDetails.ifPresentOrElse(
                details -> deltaFile.errorAction(event, details.name(), details.delay()),
                () -> deltaFile.errorAction(event));
        generateMetrics(List.of(new Metric(DeltaFiConstants.FILES_ERRORED, 1)), event, deltaFile);

        return deltaFile;
    }

    @MongoRetryable
    public void addAnnotations(String did, Map<String, String> annotations, boolean allowOverwrites) {
        synchronized(didMutexService.getMutex(did)) {
            DeltaFile deltaFile = getCachedDeltaFile(did);

            if (deltaFile == null) {
                throw new DgsEntityNotFoundException("DeltaFile " + did + " not found.");
            }

            if (allowOverwrites) {
                deltaFile.addAnnotations(annotations);
            } else {
                deltaFile.addAnnotationsIfAbsent(annotations);
            }

            deltaFile.getEgress().forEach(egress -> updatePendingAnnotations(deltaFile, egress));

            deltaFile.setModified(OffsetDateTime.now());
            deltaFileCacheService.save(deltaFile);
        }
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
        return ActionEvent.newBuilder()
                .did(deltaFile.getDid())
                .action(DeltaFiConstants.NO_EGRESS_FLOW_CONFIGURED_ACTION)
                .start(time)
                .stop(time)
                .error(ErrorEvent.newBuilder()
                        .cause(NO_EGRESS_CONFIGURED_CAUSE)
                        .context(NO_EGRESS_CONFIGURED_CONTEXT)
                        .build())
                .type(ActionEventType.UNKNOWN)
                .build();
    }

    public static ActionEvent buildNoChildFlowErrorEvent(DeltaFile deltaFile, String action, String flow,
                                                         OffsetDateTime time) {
        return ActionEvent.newBuilder()
                .did(deltaFile.getDid())
                .action(action)
                .start(time)
                .stop(time)
                .error(ErrorEvent.newBuilder()
                        .cause(NO_CHILD_INGRESS_CONFIGURED_CAUSE)
                        .context(NO_CHILD_INGRESS_CONFIGURED_CONTEXT + flow)
                        .build())
                .build();
    }

    public void loadMany(DeltaFile deltaFile, ActionEvent event) throws MissingEgressFlowException {
        List<LoadEvent> loadEvents = event.getLoadMany();
        List<DeltaFile> childDeltaFiles = Collections.emptyList();
        List<ActionInput> enqueueActions = new ArrayList<>();

        String loadActionName = ingressFlowService.getRunningFlowByName(deltaFile.getSourceInfo().getFlow()).getLoadAction().getName();
        if (!event.getAction().equals(loadActionName)) {
            deltaFile.errorAction(event, "Attempted to split using a LoadMany result in an Action that is not a LoadAction: " + event.getAction(), "");
        } else if (loadEvents.isEmpty()) {
            deltaFile.errorAction(event, "Attempted to split a DeltaFile into 0 children using a LoadMany result", "");
        } else {
            if (deltaFile.getChildDids() == null) {
                deltaFile.setChildDids(new ArrayList<>());
            }

            OffsetDateTime now = OffsetDateTime.now();
            childDeltaFiles = loadEvents.stream()
                    .map(loadEvent -> this.buildLoadManyChildAndEnqueue(deltaFile, event, loadEvent, enqueueActions, now))
                    .toList();

            deltaFile.reinjectAction(event);
        }

        advanceOnly(deltaFile, false);

        // do this in two shots.  saveAll performs a bulk insert, but only if all the entries are new
        deltaFileCacheService.save(deltaFile);
        deltaFileRepo.saveAll(childDeltaFiles);
        enqueueActions(enqueueActions);
    }

    private DeltaFile buildLoadManyChildAndEnqueue(DeltaFile parentDeltaFile, ActionEvent actionEvent, LoadEvent loadEvent, List<ActionInput> enqueueActions, OffsetDateTime now) {
        DeltaFile child = createChildDeltaFile(parentDeltaFile, loadEvent.getDid());
        child.setModified(now);

        parentDeltaFile.getChildDids().add(child.getDid());

        Action action = child.getPendingAction(actionEvent.getAction());

        if (loadEvent.getContent() != null) {
            action.setContent(loadEvent.getContent());
        }

        if (loadEvent.getMetadata() != null) {
            action.setMetadata(loadEvent.getMetadata());
        }

        if (loadEvent.getDeleteMetadataKeys() != null) {
            action.setDeleteMetadataKeys(loadEvent.getDeleteMetadataKeys());
        }

        if (loadEvent.getDomains() != null) {
            for (Domain domain : loadEvent.getDomains()) {
                child.addDomain(domain.getName(), domain.getValue(), domain.getMediaType());
            }
        }

        child.completeAction(actionEvent);
        child.addAnnotations(loadEvent.getAnnotations());

        enqueueActions.addAll(advanceOnly(child, true));

        child.recalculateBytes();

        return child;
    }

    public void reinject(DeltaFile deltaFile, ActionEvent event) throws MissingEgressFlowException {
        List<ReinjectEvent> reinjects = event.getReinject();
        List<DeltaFile> childDeltaFiles = Collections.emptyList();
        List<String> encounteredError = new ArrayList<>();
        List<ActionInput> enqueueActions = new ArrayList<>();

        String loadActionName = ingressFlowService.getRunningFlowByName(deltaFile.getSourceInfo().getFlow()).getLoadAction().getName();
        if (!event.getAction().equals(loadActionName)) {
            deltaFile.errorAction(event, "Attempted to reinject from an Action that is not a LoadAction: " + event.getAction(), "");
        } else if (reinjects.isEmpty()) {
            deltaFile.errorAction(event, "Attempted to reinject DeltaFile into 0 children", "");
        } else {
            if (deltaFile.getChildDids() == null) {
                deltaFile.setChildDids(new ArrayList<>());
            }

            OffsetDateTime now = OffsetDateTime.now(clock);

            childDeltaFiles = reinjects.stream().map(reinject -> {
                if (!encounteredError.isEmpty()) {
                    // Fail fast on first error
                    return null;
                }

                // Before we build a DeltaFile, make sure the reinject makes sense to do--i.e. the flow is enabled and valid
                ProcessingType processingType;
                if (ingressFlowService.hasRunningFlow(reinject.getFlow())) {
                    processingType = ProcessingType.NORMALIZATION;
                } else if (transformFlowService.hasRunningFlow(reinject.getFlow())) {
                    processingType = ProcessingType.TRANSFORMATION;
                } else {
                    deltaFile.errorAction(buildNoChildFlowErrorEvent(deltaFile, event.getAction(),
                            reinject.getFlow(), OffsetDateTime.now(clock)));
                    encounteredError.add(deltaFile.getDid());
                    return null;
                }

                Action action = Action.newBuilder()
                        .name(INGRESS_ACTION)
                        .type(ActionType.INGRESS)
                        .flow(reinject.getFlow())
                        .state(ActionState.COMPLETE)
                        .created(now)
                        .modified(now)
                        .content(reinject.getContent())
                        .metadata(reinject.getMetadata())
                        .build();

                DeltaFile child = DeltaFile.newBuilder()
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
                        .domains(Collections.emptyList())
                        .enrichments(Collections.emptyList())
                        .created(now)
                        .modified(now)
                        .egressed(false)
                        .filtered(false)
                        .build();

                enqueueActions.addAll(advanceOnly(child, true));

                child.recalculateBytes();

                return child;
            }).toList();

            if (encounteredError.isEmpty()) {
                deltaFile.setChildDids(childDeltaFiles.stream().map(DeltaFile::getDid).toList());
            }

            deltaFile.reinjectAction(event);
        }

        advanceOnly(deltaFile, false);

        // do this in two shots.  saveAll performs a bulk insert, but only if all the entries are new
        deltaFileCacheService.save(deltaFile);
        if (encounteredError.isEmpty()) {
            deltaFileRepo.saveAll(childDeltaFiles);

            enqueueActions(enqueueActions);
        }
    }

    public void formatMany(DeltaFile deltaFile, ActionEvent event) throws MissingEgressFlowException {
        List<FormatEvent> formatInputs = event.getFormatMany();
        List<DeltaFile> childDeltaFiles = Collections.emptyList();

        List<ActionInput> enqueueActions = new ArrayList<>();

        List<String> formatActions = egressFlowService.getAll().stream().map(ef -> ef.getFormatAction().getName()).toList();

        if (!formatActions.contains(event.getAction())) {
            deltaFile.errorAction(event, "Attempted to split from an Action that is not a current FormatAction: " + event.getAction(), "");
        } else if (formatInputs.isEmpty()) {
            deltaFile.errorAction(event, "Attempted to split DeltaFile into 0 children with formatMany", "");
        } else {
            if (deltaFile.getChildDids() == null) {
                deltaFile.setChildDids(new ArrayList<>());
            }

            EgressFlow egressFlow = egressFlowService.withFormatActionNamed(event.getAction());

            childDeltaFiles = formatInputs.stream().map(formatInput -> {
                DeltaFile child = createChildDeltaFile(deltaFile, event, UUID.randomUUID().toString());
                deltaFile.getChildDids().add(child.getDid());
                Action formatAction = child.formatActionFor(egressFlow.getName());
                formatAction.setContent(List.of(formatInput.getContent()));
                formatAction.setMetadata(formatInput.getMetadata());

                enqueueActions.addAll(advanceOnly(child, true));

                child.recalculateBytes();

                return child;
            }).toList();

            deltaFile.reinjectAction(event);
        }

        advanceOnly(deltaFile, false);

        // do this in two shots.  saveAll performs a bulk insert, but only if all the entries are new
        deltaFileCacheService.save(deltaFile);
        deltaFileRepo.saveAll(childDeltaFiles);

        enqueueActions(enqueueActions);
    }

    public void splitForTransformationProcessingEgress(DeltaFile deltaFile) throws MissingEgressFlowException {
        List<ActionInput> enqueueActions = new ArrayList<>();

        if (Objects.isNull(deltaFile.getChildDids())) {
            deltaFile.setChildDids(new ArrayList<>());
        }

        // remove the egress action, since we want the last transform to show SPLIT
        deltaFile.removeLastAction();

        List<Content> contentList = deltaFile.getLastDataAmendedAction().getContent();

        List<DeltaFile> childDeltaFiles = contentList.stream().map(content -> {
            DeltaFile child = createChildDeltaFile(deltaFile, UUID.randomUUID().toString());
            child.getLastDataAmendedAction().setContent(Collections.singletonList(content));
            deltaFile.getChildDids().add(child.getDid());

            enqueueActions.addAll(advanceOnly(child, true));

            child.recalculateBytes();

            return child;
        }).toList();

        deltaFile.setLastActionReinjected();

        advanceOnly(deltaFile, false);

        // do this in two shots.  saveAll performs a bulk insert, but only if all the entries are new
        deltaFileCacheService.save(deltaFile);
        deltaFileRepo.saveAll(childDeltaFiles);

        enqueueActions(enqueueActions);
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

    public List<RetryResult> resume(@NotNull List<String> dids, @NotNull List<String> removeSourceMetadata, @NotNull List<KeyValue> replaceSourceMetadata) {
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
                            List<String> requeueActions = deltaFile.retryErrors();
                            if (requeueActions.isEmpty()) {
                                result.setSuccess(false);
                                result.setError("DeltaFile with did " + did + " had no errors");
                            } else {
                                deltaFile.setStage(DeltaFileStage.INGRESS);
                                deltaFile.setErrorAcknowledged(null);
                                deltaFile.setErrorAcknowledgedReason(null);

                                applyRetryOverrides(deltaFile, null, null, removeSourceMetadata, replaceSourceMetadata);

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
        List<ActionInput> enqueueActions = new ArrayList<>();

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
                            Action action = Action.newBuilder()
                                    .name(INGRESS_ACTION)
                                    .type(ActionType.INGRESS)
                                    .flow(deltaFile.getSourceInfo().getFlow())
                                    .state(ActionState.COMPLETE)
                                    .created(now)
                                    .modified(now)
                                    .content(deltaFile.getActions().get(0).getContent())
                                    .metadata(deltaFile.getSourceInfo().getMetadata())
                                    .build();

                            DeltaFile child = DeltaFile.newBuilder()
                                    .schemaVersion(DeltaFile.CURRENT_SCHEMA_VERSION)
                                    .did(UUID.randomUUID().toString())
                                    .parentDids(List.of(deltaFile.getDid()))
                                    .childDids(Collections.emptyList())
                                    .requeueCount(0)
                                    .ingressBytes(deltaFile.getIngressBytes())
                                    .stage(DeltaFileStage.INGRESS)
                                    .actions(new ArrayList<>(List.of(action)))
                                    .sourceInfo(deltaFile.getSourceInfo())
                                    .domains(Collections.emptyList())
                                    .enrichments(Collections.emptyList())
                                    .created(now)
                                    .modified(now)
                                    .egressed(false)
                                    .filtered(false)
                                    .build();

                            applyRetryOverrides(child, replaceFilename, replaceFlow, removeSourceMetadata, replaceSourceMetadata);

                            enqueueActions.addAll(advanceOnly(child, true));

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
        enqueueActions(enqueueActions);

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
                            deltaFile.setErrorAcknowledged(now);
                            deltaFile.setErrorAcknowledgedReason(reason);
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
    private List<ActionInput> advanceOnly(DeltaFile deltaFile, boolean newDeltaFile) throws MissingEgressFlowException {
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
        List<ActionInput> enqueueActions = stateMachine.advance(deltaFile);

        if (deltaFile.getStage() == DeltaFileStage.EGRESS) {
            // this is our first time having egress assigned
            // determine if the deltaFile needs to be split
            if (deltaFile.getLastDataAmendedAction().getContent().size() > 1) {
                splitForTransformationProcessingEgress(deltaFile);
                return;
            }
        }

        deltaFileCacheService.save(deltaFile);
        if (!enqueueActions.isEmpty()) {
            enqueueActions(enqueueActions);
        }
    }

    public void advanceAndSave(DeltaFile deltaFile) {
        try {
            List<ActionInput> enqueueActions = stateMachine.advance(deltaFile);
            deltaFileCacheService.save(deltaFile);
            if (!enqueueActions.isEmpty()) {
                enqueueActions(enqueueActions);
            }
        } catch (MissingEgressFlowException e) {
            handleMissingEgressFlow(deltaFile);
            deltaFileCacheService.save(deltaFile);
        }
    }

    public void advanceAndSave(List<DeltaFile> deltaFiles) {
        if (deltaFiles.isEmpty()) {
            return;
        }

        List<ActionInput> enqueueActions = new ArrayList<>();

        deltaFiles.forEach(deltaFile -> {
            try {
                enqueueActions.addAll(stateMachine.advance(deltaFile));
            } catch (MissingEgressFlowException e) {
                handleMissingEgressFlow(deltaFile);
            }
        });

        deltaFileRepo.saveAll(deltaFiles);

        if (!enqueueActions.isEmpty()) {
            enqueueActions(enqueueActions);
        }
    }

    private void handleMissingEgressFlow(DeltaFile deltaFile) {
        deltaFile.queueNewAction(DeltaFiConstants.NO_EGRESS_FLOW_CONFIGURED_ACTION, ActionType.UNKNOWN, "MISSING");
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

    public void delete(OffsetDateTime createdBefore, OffsetDateTime completedBefore, Long minBytes, String flow, String policy, boolean deleteMetadata) {
        int found;
        int batchSize = deltaFiPropertiesService.getDeltaFiProperties().getDelete().getPolicyBatchSize();
        do {
            List<DeltaFile> deltaFiles = deltaFileRepo.findForDelete(createdBefore, completedBefore, minBytes, flow, policy, deleteMetadata, batchSize);
            found = deltaFiles.size();
            delete(deltaFiles, policy, deleteMetadata);
        } while (found == batchSize);
    }

    public List<DeltaFile> delete(long bytesToDelete, String flow, String policy, boolean deleteMetadata) {
        List<DeltaFile> allDeleted = new ArrayList<>();
        long bytesLeft = bytesToDelete;
        int batchSize = deltaFiPropertiesService.getDeltaFiProperties().getDelete().getPolicyBatchSize();

        int found;
        do {
            log.info("Searching for batch of up to " + batchSize + " deltaFiles to delete for policy " + policy);
            List<DeltaFile> deltaFiles = delete(deltaFileRepo.findForDelete(bytesToDelete, flow, policy, batchSize), policy, deleteMetadata);
            found = deltaFiles.size();
            allDeleted.addAll(deltaFiles);
            bytesLeft = bytesLeft - deltaFiles.stream().map(DeltaFile::getTotalBytes).reduce(0L, Long::sum);
        } while (found == batchSize && bytesLeft > 0);

        return allDeleted;
    }

    public List<DeltaFile> delete(List<DeltaFile> deltaFiles, String policy, boolean deleteMetadata) {
        if (deltaFiles.isEmpty()) {
            log.info("No deltaFiles found to delete for policy " + policy);
            return deltaFiles;
        }

        log.info("Deleting " + deltaFiles.size() + " deltaFiles for policy " + policy);
        long totalBytes = deltaFiles.stream().mapToLong(DeltaFile::getTotalBytes).sum();

        deleteContent(deltaFiles, policy, deleteMetadata);
        metricService.increment(new Metric(DELETED_FILES, deltaFiles.size()).addTag("policy", policy));
        metricService.increment(new Metric(DELETED_BYTES, totalBytes).addTag("policy", policy));
        log.info("Finished deleting " + deltaFiles.size() + " deltaFiles for policy " + policy);

        return deltaFiles;
    }

    public void requeue() {
        OffsetDateTime modified = OffsetDateTime.now(clock);
        List<DeltaFile> requeuedDeltaFiles = deltaFileRepo.updateForRequeue(modified, getProperties().getRequeueSeconds());
        List<ActionInput> actions = requeuedDeltaFiles.stream()
                .map(deltaFile -> requeuedActionInput(deltaFile, modified))
                .flatMap(Collection::stream)
                .toList();
        if (!actions.isEmpty()) {
            log.warn(actions.size() + " actions exceeded requeue threshold of " + getProperties().getRequeueSeconds() + " seconds, requeuing now");
            enqueueActions(actions, true);
        }
    }

    List<ActionInput> requeuedActionInput(DeltaFile deltaFile, OffsetDateTime modified) {
        return deltaFile.getActions().stream()
                .filter(a -> a.getState().equals(ActionState.QUEUED) && a.getModified().toInstant().toEpochMilli() == modified.toInstant().toEpochMilli())
                .map(action -> toActionInput(action, deltaFile))
                .filter(Objects::nonNull)
                .toList();
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
            List<RetryResult> results = resume(flowByDid.keySet().stream().toList(),
                    Collections.emptyList(), Collections.emptyList());
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
                generateMetrics(FILES_AUTO_RESUMED, countByFlow);
            }
        }
        return queued;
    }

    @SuppressWarnings("SameParameterValue")
    private void generateMetrics(String name, Map<String, Integer> countByFlow) {
        Set<String> flows = countByFlow.keySet();
        for (String flow : flows) {
            Integer count = countByFlow.get(flow);
            Map<String, String> tags = new HashMap<>();
            tags.put(DeltaFiConstants.INGRESS_FLOW, flow);
            Metric metric = new Metric(name, count, tags);
            metricService.increment(metric);
        }
    }

    private ActionConfiguration actionConfiguration(String actionName, DeltaFile deltaFile) {
        try {
            if (ProcessingType.TRANSFORMATION.equals(deltaFile.getSourceInfo().getProcessingType())) {
                return transformFlowService.findActionConfig(deltaFile.getSourceInfo().getFlow(), actionName);
            } else if (DeltaFileStage.INGRESS.equals(deltaFile.getStage())) {
                return ingressFlowService.findActionConfig(deltaFile.getSourceInfo().getFlow(), actionName);
            } else if (DeltaFileStage.ENRICH.equals(deltaFile.getStage())) {
                return enrichFlowService.findActionConfig(actionName);
            } else if (DeltaFileStage.EGRESS.equals(deltaFile.getStage())) {
                return egressFlowService.findActionConfig(actionName);
            }
        } catch (IllegalArgumentException ignored) {}

        return null;
    }

    private String egressFlow(String actionName, DeltaFile deltaFile) {
        Optional<Action> action = deltaFile.actionNamed(actionName);
        return action.map(value -> egressFlow(value, deltaFile)).orElse(null);
    }

    private String egressFlow(Action action, DeltaFile deltaFile) {
        if (DeltaFileStage.EGRESS.equals(deltaFile.getStage()) ||
                (DeltaFileStage.COMPLETE.equals(deltaFile.getStage()) && deltaFile.getEgressed())) {
            try {
                return FlowService.getFlowName(action.getName());
            } catch (IllegalArgumentException ignored) {}
        }

        return null;
    }

    private ActionInput toActionInput(Action action, DeltaFile deltaFile) {
        ActionConfiguration actionConfiguration = actionConfiguration(action.getName(), deltaFile);
        String egressFlow = egressFlow(action, deltaFile);

        if (Objects.isNull(actionConfiguration)) {
            String errorMessage = "Action named " + action.getName() + " is no longer running";
            log.error(errorMessage);
            ErrorEvent error = ErrorEvent.newBuilder().cause(errorMessage).build();
            ActionEvent event = ActionEvent.newBuilder().did(deltaFile.getDid()).action(action.getName()).type(ActionEventType.UNKNOWN).error(error).build();
            error(deltaFile, event);

            return null;
        }

        return actionConfiguration.buildActionInput(deltaFile, getProperties().getSystemName(), egressFlow, identityService.getUniqueId());
    }

    public void processActionEvents() {
        processActionEvents(null);
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
        executor.submit(() -> {
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
        });
    }

    private void enqueueActions(List<ActionInput> enqueueActions) throws EnqueueActionException {
        enqueueActions(enqueueActions, false);
    }

    private void enqueueActions(List<ActionInput> enqueueActions, boolean checkUnique) throws EnqueueActionException {
        try {
            actionEventQueue.putActions(enqueueActions, checkUnique);
        } catch (Exception e) {
            log.error("Failed to queue action(s)", e);
            throw new EnqueueActionException("Failed to queue action(s)", e);
        }
    }

    private void deleteContent(List<DeltaFile> deltaFiles, String policy, boolean deleteMetadata) {
        contentStorageService.deleteAll(deltaFiles.stream()
                .map(DeltaFile::storedSegments)
                .flatMap(Collection::stream)
                .toList());

        if (deleteMetadata) {
            deleteMetadata(deltaFiles);
        } else {
            deltaFileRepo.setContentDeletedByDidIn(
                    deltaFiles.stream().map(DeltaFile::getDid).distinct().toList(),
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

    public ErrorsByFlow getErrorSummaryByFlow(Integer offset, Integer limit, ErrorSummaryFilter filter, DeltaFileOrder orderBy) {
        return deltaFileRepo.getErrorSummaryByFlow(offset,
                (Objects.nonNull(limit) && limit > 0) ? limit : DEFAULT_QUERY_LIMIT,
                filter, orderBy);
    }

    public ErrorsByMessage getErrorSummaryByMessage(Integer offset, Integer limit, ErrorSummaryFilter filter, DeltaFileOrder orderBy) {
        return deltaFileRepo.getErrorSummaryByMessage(offset,
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

    public DeltaFileStats deltaFileStats(boolean inFlightOnly, boolean includeDeletedContent) {
        return deltaFileRepo.deltaFileStats(inFlightOnly, includeDeletedContent);
    }
}
