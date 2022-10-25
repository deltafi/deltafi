/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
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
import org.apache.commons.lang3.tuple.Pair;
import org.deltafi.common.action.ActionEventQueue;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.common.content.ContentReference;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.metrics.MetricRepository;
import org.deltafi.common.metrics.MetricsUtil;
import org.deltafi.common.types.*;
import org.deltafi.core.types.EgressFlow;
import org.deltafi.core.types.UniqueKeyValues;
import org.deltafi.core.generated.types.*;
import org.deltafi.core.configuration.DeltaFiProperties;
import org.deltafi.core.exceptions.MissingEgressFlowException;
import org.deltafi.core.exceptions.UnexpectedActionException;
import org.deltafi.core.exceptions.UnknownTypeException;
import org.deltafi.core.repo.DeltaFileRepo;
import org.deltafi.core.retry.MongoRetryable;
import org.deltafi.core.types.DeltaFiles;
import org.jetbrains.annotations.NotNull;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import redis.clients.jedis.exceptions.JedisConnectionException;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.deltafi.common.constant.DeltaFiConstants.INGRESS_ACTION;
import static org.deltafi.common.metrics.MetricsUtil.FILES_ERRORED;
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
    public static final String NO_CHILD_INGRESS_CONFIGURED_CONTEXT = "This DeltaFile split does not match any running ingress flows: ";

    private static final int DEFAULT_QUERY_LIMIT = 50;

    final IngressFlowService ingressFlowService;
    final EnrichFlowService enrichFlowService;
    final EgressFlowService egressFlowService;
    final DeltaFiProperties properties;
    final FlowAssignmentService flowAssignmentService;
    final StateMachine stateMachine;
    final DeltaFileRepo deltaFileRepo;
    final ActionEventQueue actionEventQueue;
    final ContentStorageService contentStorageService;
    private final MetricRepository metricService;
    private ExecutorService executor;

    @PostConstruct
    private void initializeExecutor() {
        int threadCount = properties.getCoreServiceThreads() > 0 ? properties.getCoreServiceThreads() : 16;
        executor = Executors.newFixedThreadPool(threadCount);
        log.info("Executors pool size: " + threadCount);
    }

    public DeltaFile getDeltaFile(String did) {
        return deltaFileRepo.findById(did.toLowerCase()).orElse(null);
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

    public DeltaFiles getDeltaFiles(Integer offset, Integer limit, DeltaFilesFilter filter, DeltaFileOrder orderBy) {
        return getDeltaFiles(offset, limit, filter, orderBy, null);
    }

    public DeltaFiles getDeltaFiles(Integer offset, Integer limit, DeltaFilesFilter filter, DeltaFileOrder orderBy, List<String> includeFields) {
        return deltaFileRepo.deltaFiles(offset, (Objects.nonNull(limit) && limit > 0) ? limit : DEFAULT_QUERY_LIMIT, filter, orderBy, includeFields);
    }

    public Map<String, DeltaFile> getDeltaFiles(List<String> dids) {
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

    public DeltaFile ingress(IngressInput input) {
        return ingress(input, Collections.emptyList());
    }

    @MongoRetryable
    public DeltaFile ingress(IngressInput input, List<String> parentDids) {
        SourceInfo sourceInfo = input.getSourceInfo();
        if (sourceInfo.getFlow().equals(DeltaFiConstants.AUTO_RESOLVE_FLOW_NAME)) {
            String flow = flowAssignmentService.findFlow(sourceInfo);
            if (Objects.isNull(flow)) {
                throw new DgsEntityNotFoundException(
                        "Unable to resolve flow name based on source metadata and current flow assignment rules");
            }
            sourceInfo.setFlow(flow);
        }

        // ensure flow is running before excepting ingress
        ingressFlowService.getRunningFlowByName(sourceInfo.getFlow());

        OffsetDateTime now = OffsetDateTime.now();

        Action ingressAction = Action.newBuilder()
                .name(INGRESS_ACTION)
                .state(ActionState.COMPLETE)
                .created(input.getCreated())
                .modified(now)
                .build();

        DeltaFile deltaFile = DeltaFile.newBuilder()
                .did(input.getDid())
                .parentDids(parentDids)
                .childDids(Collections.emptyList())
                .requeueCount(0)
                .ingressBytes(computeContentSize(input.getContent()))
                .stage(DeltaFileStage.INGRESS)
                .actions(new ArrayList<>(List.of(ingressAction)))
                .sourceInfo(sourceInfo)
                .protocolStack(List.of(new ProtocolLayer(INGRESS_ACTION, input.getContent(), null)))
                .domains(Collections.emptyList())
                .enrichment(Collections.emptyList())
                .formattedData(Collections.emptyList())
                .created(input.getCreated())
                .modified(now)
                .egressed(false)
                .filtered(false)
                .build();

        return advanceAndSave(deltaFile);
    }

    private Long computeContentSize(List<Content> content) {
        if (content == null || content.isEmpty()) {
            return 0L;
        }
        return content.stream()
                .map(c -> c.getContentReference().getSize())
                .reduce(0L, Long::sum);
    }

    @MongoRetryable
    public DeltaFile handleActionEvent(ActionEventInput event) throws JsonProcessingException {
        DeltaFile deltaFile = getDeltaFile(event.getDid());

        if (deltaFile == null) {
            throw new DgsEntityNotFoundException("Received event for unknown did: " + event);
        }

        if (deltaFile.getStage() == DeltaFileStage.CANCELLED) {
            log.warn("Received event for cancelled did " + deltaFile.getDid());
            return deltaFile;
        }

        if (deltaFile.noPendingAction(event.getAction())) {
            throw new UnexpectedActionException(event.getAction(), event.getDid(), deltaFile.queuedActions());
        }

        switch (event.getType()) {
            case TRANSFORM:
                return transform(deltaFile, event);
            case LOAD:
                return load(deltaFile, event);
            case DOMAIN:
                return domain(deltaFile, event);
            case ENRICH:
                return enrich(deltaFile, event);
            case FORMAT:
                return format(deltaFile, event);
            case VALIDATE:
                return validate(deltaFile, event);
            case EGRESS:
                return egress(deltaFile, event);
            case ERROR:
                return error(deltaFile, event);
            case FILTER:
                return filter(deltaFile, event);
            case SPLIT:
                return split(deltaFile, event);
            case FORMAT_MANY:
                return formatMany(deltaFile, event);
        }

        throw new UnknownTypeException(event.getAction(), event.getDid(), event.getType());
    }

    public DeltaFile transform(DeltaFile deltaFile, ActionEventInput event) {
        if (event.getTransform().getProtocolLayer() != null) {
            ProtocolLayer protocolLayer = event.getTransform().getProtocolLayer();
            protocolLayer.setAction(event.getAction());
            deltaFile.getProtocolStack().add(protocolLayer);
        }
        deltaFile.completeAction(event);

        return advanceAndSave(deltaFile);
    }

    public DeltaFile load(DeltaFile deltaFile, ActionEventInput event) {
        deltaFile.completeAction(event);

        if (event.getLoad() != null) {
            ProtocolLayer protocolLayer = event.getLoad().getProtocolLayer();
            if (protocolLayer != null) {
                protocolLayer.setAction(event.getAction());
                deltaFile.getProtocolStack().add(protocolLayer);
            }
            if (event.getLoad().getDomains() != null) {
                for (Domain domain : event.getLoad().getDomains()) {
                    deltaFile.addDomain(domain.getName(), domain.getValue(), domain.getMediaType());
                }
            }
        }

        return advanceAndSave(deltaFile);
    }

    public DeltaFile domain(DeltaFile deltaFile, ActionEventInput event) {
        deltaFile.completeAction(event);

        if (event.getDomain() != null) {
            deltaFile.addIndexedMetadata(event.getDomain().getIndexedMetadata());
        }

        return advanceAndSave(deltaFile);
    }

    public DeltaFile enrich(DeltaFile deltaFile, ActionEventInput event) {
        deltaFile.completeAction(event);

        if (event.getEnrich() != null) {
            if (null != event.getEnrich().getEnrichments()) {
                for (Enrichment enrichment : event.getEnrich().getEnrichments()) {
                    deltaFile.addEnrichment(enrichment.getName(), enrichment.getValue(), enrichment.getMediaType());
                }
            }

            deltaFile.addIndexedMetadata(event.getEnrich().getIndexedMetadata());
        }

        return advanceAndSave(deltaFile);
    }

    public DeltaFile format(DeltaFile deltaFile, ActionEventInput event) {
        EgressFlow egressFlow = egressFlowService.withFormatActionNamed(event.getAction());
        FormattedData formattedData = FormattedData.newBuilder()
                .formatAction(event.getAction())
                .filename(event.getFormat().getFilename())
                .metadata(event.getFormat().getMetadata())
                .contentReference(event.getFormat().getContentReference())
                .egressActions(List.of(egressFlow.getEgressAction().getName()))
                .validateActions(egressFlow.validateActionNames())
                .build();
        deltaFile.getFormattedData().add(formattedData);
        deltaFile.completeAction(event);

        return advanceAndSave(deltaFile);
    }

    public DeltaFile validate(DeltaFile deltaFile, ActionEventInput event) {
        deltaFile.completeAction(event);

        return advanceAndSave(deltaFile);
    }

    public DeltaFile egress(DeltaFile deltaFile, ActionEventInput event) {
        deltaFile.completeAction(event);
        deltaFile.setEgressed(true);

        return advanceAndSave(deltaFile);
    }

    @MongoRetryable
    public DeltaFile filter(DeltaFile deltaFile, ActionEventInput event) throws JsonProcessingException {
        if (deltaFile.noPendingAction(event.getAction())) {
            throw new UnexpectedActionException(event.getAction(), event.getDid(), deltaFile.queuedActions());
        }

        ActionConfiguration actionConfiguration = actionConfiguration(event.getAction(), deltaFile);
        ActionType actionType = ActionType.UNKNOWN;
        if (actionConfiguration != null) {
            actionType = actionConfiguration.getActionType();
        }

        // Treat filter events from Domain and Enrich actions as errors
        if (actionType.equals(ActionType.DOMAIN) || actionType.equals(ActionType.ENRICH)) {
            event.setError(ErrorInput.newBuilder().cause("Illegal operation FILTER received from " + actionType + "Action " + event.getAction()).build());
            return error(deltaFile, event);
        } else {
            deltaFile.filterAction(event, event.getFilter().getMessage());
            deltaFile.setFiltered(true);
        }

        return advanceAndSave(deltaFile);
    }

    @MongoRetryable
    public DeltaFile error(DeltaFile deltaFile, ActionEventInput event) throws JsonProcessingException {
        return advanceAndSave(processErrorEvent(deltaFile, event));
    }

    @MongoRetryable
    public DeltaFile processErrorEvent(DeltaFile deltaFile, ActionEventInput event) throws JsonProcessingException {
        if (deltaFile.noPendingAction(event.getAction())) {
            throw new UnexpectedActionException(event.getAction(), deltaFile.getDid(), deltaFile.queuedActions());
        }

        deltaFile.errorAction(event);

        ActionConfiguration actionConfiguration = actionConfiguration(event.getAction(), deltaFile);
        ActionType actionType = ActionType.UNKNOWN;
        if (actionConfiguration != null) {
            actionType = actionConfiguration.getActionType();
        }
        String egressFlow = egressFlow(event.getAction(), deltaFile);
        metricService.increment(FILES_ERRORED,
                MetricsUtil.tagsFor(actionType, event.getAction(), deltaFile.getSourceInfo().getFlow(), egressFlow),
                1);

        return deltaFile;
    }

    public static ActionEventInput buildNoEgressConfiguredErrorEvent(DeltaFile deltaFile) {
        OffsetDateTime now = OffsetDateTime.now();
        return ActionEventInput.newBuilder()
                .did(deltaFile.getDid())
                .action(DeltaFiConstants.NO_EGRESS_FLOW_CONFIGURED_ACTION)
                .start(now)
                .stop(now)
                .error(ErrorInput.newBuilder()
                        .cause(NO_EGRESS_CONFIGURED_CAUSE)
                        .context(NO_EGRESS_CONFIGURED_CONTEXT)
                        .build())
                .build();
    }

    public static ActionEventInput buildNoChildFlowErrorEvent(DeltaFile deltaFile, String action, String flow) {
        final OffsetDateTime now = OffsetDateTime.now();
        return ActionEventInput.newBuilder()
                .did(deltaFile.getDid())
                .action(action)
                .start(now)
                .stop(now)
                .error(ErrorInput.newBuilder()
                        .cause(NO_CHILD_INGRESS_CONFIGURED_CAUSE)
                        .context(NO_CHILD_INGRESS_CONFIGURED_CONTEXT + flow)
                        .build())
                .build();
    }

    @MongoRetryable
    public DeltaFile split(DeltaFile deltaFile, ActionEventInput event) throws MissingEgressFlowException {
        List<SplitInput> splits = event.getSplit();
        List<DeltaFile> childDeltaFiles = Collections.emptyList();
        List<String> encounteredError = new ArrayList<>();
        List<ActionInput> enqueueActions = new ArrayList<>();

        String loadActionName = ingressFlowService.getRunningFlowByName(deltaFile.getSourceInfo().getFlow()).getLoadAction().getName();
        if (!event.getAction().equals(loadActionName)) {
            deltaFile.errorAction(event, "Attempted to split from an Action that is not a LoadAction: " + event.getAction(), "");
        } else if (Objects.isNull(splits) || splits.isEmpty()) {
            deltaFile.errorAction(event, "Attempted to split DeltaFile into 0 children", "");
        } else {
            if (Objects.isNull(deltaFile.getChildDids())) {
                deltaFile.setChildDids(new ArrayList<>());
            }

            OffsetDateTime now = OffsetDateTime.now();
            Action action = Action.newBuilder()
                    .name(INGRESS_ACTION)
                    .state(ActionState.COMPLETE)
                    .created(now)
                    .modified(now)
                    .build();

            childDeltaFiles = splits.stream().map(split -> {
                if (!encounteredError.isEmpty()) {
                    // Fail fast on first error
                    return null;
                }

                // Before we build a DeltaFile, make sure the split makes sense to do--i.e. the flow is
                // enabled and valid
                try {
                    ingressFlowService.getRunningFlowByName(split.getSourceInfo().getFlow());
                } catch (DgsEntityNotFoundException notFound) {
                    deltaFile.errorAction(buildNoChildFlowErrorEvent(deltaFile, event.getAction(), split.getSourceInfo().getFlow()));
                    encounteredError.add(deltaFile.getDid());
                    return null;
                }

                DeltaFile child = DeltaFile.newBuilder()
                        .did(UUID.randomUUID().toString())
                        .parentDids(List.of(deltaFile.getDid()))
                        .childDids(Collections.emptyList())
                        .requeueCount(0)
                        .ingressBytes(computeContentSize(split.getContent()))
                        .stage(DeltaFileStage.INGRESS)
                        .actions(new ArrayList<>(List.of(action)))
                        .sourceInfo(split.getSourceInfo())
                        .protocolStack(List.of(new ProtocolLayer(INGRESS_ACTION, split.getContent(), Collections.emptyList())))
                        .domains(Collections.emptyList())
                        .enrichment(Collections.emptyList())
                        .formattedData(Collections.emptyList())
                        .created(now)
                        .modified(now)
                        .egressed(false)
                        .filtered(false)
                        .build();

                enqueueActions.addAll(advanceOnly(child));

                calculateTotalBytes(child);

                return child;
            }).collect(Collectors.toList());

            if (encounteredError.isEmpty()) {
                deltaFile.setChildDids(childDeltaFiles.stream().map(DeltaFile::getDid).collect(Collectors.toList()));
            }

            deltaFile.splitAction(event);
        }

        advanceOnly(deltaFile);

        // do this in two shots.  saveAll performs a bulk insert, but only if all the entries are new
        deltaFileRepo.save(deltaFile);
        if (encounteredError.isEmpty()) {
            deltaFileRepo.saveAll(childDeltaFiles);

            enqueueActions(enqueueActions);
        }

        return deltaFile;
    }

    public static void calculateTotalBytes(DeltaFile deltaFile) {
        List<ContentReference> contentReferences = deltaFile.storedContentReferences();

        // keep track of the first and last offset for each unique uuid contentReference
        // make an assumption that we won't have disjoint segments
        Map<String, Pair<Long, Long>> segments = new HashMap<>();
        contentReferences.forEach(c -> {
            if (segments.containsKey(c.getUuid())) {
                Pair<Long, Long> segment = segments.get(c.getUuid());
                segments.put(c.getUuid(), Pair.of(Math.min(segment.getLeft(), c.getOffset()), Math.max(segment.getRight(), c.getOffset() + c.getSize())));
            } else {
                segments.put(c.getUuid(), Pair.of(c.getOffset(), c.getOffset() + c.getSize()));
            }
        });

        deltaFile.setTotalBytes(segments.values().stream().map(p -> p.getRight() - p.getLeft()).mapToLong(Long::longValue).sum());
    }

    @MongoRetryable
    public DeltaFile formatMany(DeltaFile deltaFile, ActionEventInput event) throws MissingEgressFlowException {
        List<FormatInput> formatInputs = event.getFormatMany();
        List<DeltaFile> childDeltaFiles = Collections.emptyList();

        List<ActionInput> enqueueActions = new ArrayList<>();

        List<String> formatActions = egressFlowService.getAll().stream().map(ef -> ef.getFormatAction().getName()).collect(Collectors.toList());

        if (!formatActions.contains(event.getAction())) {
            deltaFile.errorAction(event, "Attempted to split from an Action that is not a current FormatAction: " + event.getAction(), "");
        } else if (Objects.isNull(formatInputs) || formatInputs.isEmpty()) {
            deltaFile.errorAction(event, "Attempted to split DeltaFile into 0 children with formatMany", "");
        } else {
            if (Objects.isNull(deltaFile.getChildDids())) {
                deltaFile.setChildDids(new ArrayList<>());
            }

            EgressFlow egressFlow = egressFlowService.withFormatActionNamed(event.getAction());

            childDeltaFiles = formatInputs.stream().map(formatInput -> {
                DeltaFile child = OBJECT_MAPPER.convertValue(deltaFile, DeltaFile.class);
                child.setVersion(0);
                child.setDid(UUID.randomUUID().toString());
                child.setChildDids(Collections.emptyList());
                child.setParentDids(List.of(deltaFile.getDid()));
                child.completeAction(event);

                FormattedData formattedData = FormattedData.newBuilder()
                        .formatAction(event.getAction())
                        .filename(formatInput.getFilename())
                        .metadata(formatInput.getMetadata())
                        .contentReference(formatInput.getContentReference())
                        .egressActions(List.of(egressFlow.getEgressAction().getName()))
                        .validateActions(egressFlow.getValidateActions().stream().map(ValidateActionConfiguration::getName).collect(Collectors.toList()))
                        .build();

                child.setFormattedData(List.of(formattedData));

                enqueueActions.addAll(advanceOnly(child));

                calculateTotalBytes(child);

                return child;
            }).collect(Collectors.toList());

            deltaFile.setChildDids(childDeltaFiles.stream().map(DeltaFile::getDid).collect(Collectors.toList()));

            deltaFile.splitAction(event);
        }

        advanceOnly(deltaFile);

        // do this in two shots.  saveAll performs a bulk insert, but only if all the entries are new
        deltaFileRepo.save(deltaFile);
        deltaFileRepo.saveAll(childDeltaFiles);

        enqueueActions(enqueueActions);

        return deltaFile;
    }

    public List<RetryResult> resume(@NotNull List<String> dids, @NotNull List<String> removeSourceMetadata, @NotNull List<KeyValue> replaceSourceMetadata) {
        Map<String, DeltaFile> deltaFiles = getDeltaFiles(dids);
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
                .collect(Collectors.toList());

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

        for (KeyValue keyValue : replaceSourceMetadata) {
            if (sourceInfo.containsKey(keyValue.getKey())) {
                sourceInfo.addMetadata(keyValue.getKey() + ".original", sourceInfo.getMetadata(keyValue.getKey()));
            }
            sourceInfo.addMetadata(keyValue.getKey(), keyValue.getValue());
        }
    }

    public List<RetryResult> replay(@NotNull List<String> dids, String replaceFilename, String replaceFlow, @NotNull List<String> removeSourceMetadata, @NotNull List<KeyValue> replaceSourceMetadata)  {
        Map<String, DeltaFile> deltaFiles = getDeltaFiles(dids);

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
                            OffsetDateTime now = OffsetDateTime.now();
                            Action action = Action.newBuilder()
                                    .name(INGRESS_ACTION)
                                    .state(ActionState.COMPLETE)
                                    .created(now)
                                    .modified(now)
                                    .build();

                            DeltaFile child = DeltaFile.newBuilder()
                                    .did(UUID.randomUUID().toString())
                                    .parentDids(List.of(deltaFile.getDid()))
                                    .childDids(Collections.emptyList())
                                    .requeueCount(0)
                                    .ingressBytes(deltaFile.getIngressBytes())
                                    .stage(DeltaFileStage.INGRESS)
                                    .actions(new ArrayList<>(List.of(action)))
                                    .sourceInfo(deltaFile.getSourceInfo())
                                    .protocolStack(List.of(new ProtocolLayer(INGRESS_ACTION, deltaFile.getProtocolStack().get(0).getContent(), null)))
                                    .domains(Collections.emptyList())
                                    .enrichment(Collections.emptyList())
                                    .formattedData(Collections.emptyList())
                                    .created(now)
                                    .modified(now)
                                    .egressed(false)
                                    .filtered(false)
                                    .build();

                            applyRetryOverrides(child, replaceFilename, replaceFlow, removeSourceMetadata, replaceSourceMetadata);

                            enqueueActions.addAll(advanceOnly(child));

                            if (properties.getDelete().isOnCompletion() && child.getStage().equals(DeltaFileStage.COMPLETE)) {
                                delete(Collections.singletonList(deltaFile), "on completion", false);
                            }

                            calculateTotalBytes(child);
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
                .collect(Collectors.toList());

        deltaFileRepo.saveAll(childDeltaFiles);
        deltaFileRepo.saveAll(parentDeltaFiles);
        enqueueActions(enqueueActions);

        return results;
    }

    public List<AcknowledgeResult> acknowledge(List<String> dids, String reason) {
        Map<String, DeltaFile> deltaFiles = getDeltaFiles(dids);

        OffsetDateTime now = OffsetDateTime.now();
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
                .collect(Collectors.toList());

        deltaFileRepo.saveAll(changedDeltaFiles);
        return results;
    }

    public List<CancelResult> cancel(List<String> dids) {
        Map<String, DeltaFile> deltaFiles = getDeltaFiles(dids);
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
                .collect(Collectors.toList());

        deltaFileRepo.saveAll(changedDeltaFiles);
        return results;
    }

    public List<UniqueKeyValues> sourceMetadataUnion(List<String> dids) {
        DeltaFilesFilter filter = new DeltaFilesFilter();
        filter.setDids(dids);
        DeltaFiles deltaFiles = getDeltaFiles(0, dids.size(), filter, null, List.of(SOURCE_INFO_METADATA));

        Map<String, UniqueKeyValues> keyValues = new HashMap<>();
        deltaFiles.getDeltaFiles().forEach(deltaFile -> {
            List<KeyValue> deltaFileMeta = deltaFile.getSourceInfo().getMetadata();
            for (KeyValue meta : deltaFileMeta) {
                if (!keyValues.containsKey(meta.getKey())) {
                    keyValues.put(meta.getKey(), new UniqueKeyValues(meta.getKey()));
                }
                keyValues.get(meta.getKey()).addValue(meta.getValue());
            }
        });
        return new ArrayList<>(keyValues.values());
    }

    /**
     * Advance the DeltaFile to the next step using the state machine.
     *
     * @param deltaFile the DeltaFile to advance through the state machine
     * @return list of next pending action(s)
     * @throws MissingEgressFlowException if state machine would advance DeltaFile into EGRESS stage but no EgressFlow was configured.
     */
    private List<ActionInput> advanceOnly(DeltaFile deltaFile) throws MissingEgressFlowException {
        // MissingEgressFlowException is not expected when a DeltaFile is entering the INGRESS stage
        // such as from replay or split, since an ingress flow requires at least the Load action to
        // be queued, nor when handling an event for any egress flow action, e.g. format.
        return stateMachine.advance(deltaFile);
    }

    public DeltaFile advanceAndSave(DeltaFile deltaFile) {
        try {
            List<ActionInput> enqueueActions = stateMachine.advance(deltaFile);
            if (properties.getDelete().isOnCompletion() && deltaFile.getStage().equals(DeltaFileStage.COMPLETE)) {
                delete(Collections.singletonList(deltaFile), "on completion", false);
            } else {
                deltaFileRepo.save(deltaFile);
                if (!enqueueActions.isEmpty()) {
                    enqueueActions(enqueueActions);
                }
            }
        } catch (MissingEgressFlowException e) {
            handleMissingEgressFlow(Collections.singletonList(deltaFile));
        }
        return deltaFile;
    }

    public void advanceAndSave(List<DeltaFile> deltaFiles) {
        List<DeltaFile> saveDeltaFiles = new ArrayList<>();
        List<DeltaFile> deleteDeltaFiles = new ArrayList<>();
        List<DeltaFile> missingEgressFlowDeltaFiles = new ArrayList<>();
        List<ActionInput> enqueueActions = new ArrayList<>();

        deltaFiles.forEach(deltaFile -> {
            try {
                enqueueActions.addAll(stateMachine.advance(deltaFile));
                if (properties.getDelete().isOnCompletion() && deltaFile.getStage().equals(DeltaFileStage.COMPLETE)) {
                    deleteDeltaFiles.add(deltaFile);
                } else {
                    saveDeltaFiles.add(deltaFile);
                }
            } catch (MissingEgressFlowException e) {
                missingEgressFlowDeltaFiles.add(deltaFile);
            }
        });

        if (!deltaFiles.isEmpty()) {
            deltaFileRepo.saveAll(saveDeltaFiles);
        }

        if (!deleteDeltaFiles.isEmpty()) {
            delete(deleteDeltaFiles, "on completion", false);
        }

        if (!missingEgressFlowDeltaFiles.isEmpty()) {
            handleMissingEgressFlow(missingEgressFlowDeltaFiles);
        }

        if (!enqueueActions.isEmpty()) {
            enqueueActions(enqueueActions);
        }
    }

    private void handleMissingEgressFlow(List<DeltaFile> deltaFiles) {
        for (DeltaFile deltaFile : deltaFiles) {
            deltaFile.queueNewAction(DeltaFiConstants.NO_EGRESS_FLOW_CONFIGURED_ACTION);
            try {
                processErrorEvent(deltaFile, buildNoEgressConfiguredErrorEvent(deltaFile));
            } catch (JsonProcessingException e) {
                log.error("Unable to create error file: " + e.getMessage());
            }
            deltaFile.setStage(DeltaFileStage.ERROR);
        }
        deltaFileRepo.saveAll(deltaFiles);
    }

    public void delete(OffsetDateTime createdBefore, OffsetDateTime completedBefore, Long minBytes, String flow, String policy, boolean deleteMetadata, int batchSize) {
        int found;
        do {
            List<DeltaFile> deltaFiles = deltaFileRepo.findForDelete(createdBefore, completedBefore, minBytes, flow, policy, deleteMetadata, batchSize);
            found = deltaFiles.size();
            delete(deltaFiles, policy, deleteMetadata);
        } while (found == batchSize);
    }

    public List<DeltaFile> delete(long bytesToDelete, String flow, String policy, boolean deleteMetadata, int batchSize) {
        return delete(deltaFileRepo.findForDelete(bytesToDelete, flow, policy, batchSize), policy, deleteMetadata);
    }

    public List<DeltaFile> delete(List<DeltaFile> deltaFiles, String policy, boolean deleteMetadata) {
        if (deltaFiles.isEmpty()) {
            return deltaFiles;
        }

        log.info("Deleting " + deltaFiles.size() + " files for policy " + policy);
        deleteContent(deltaFiles, policy);
        if (deleteMetadata) {
            deleteMetadata(deltaFiles);
        } else {
            deltaFileRepo.saveAll(deltaFiles);
        }
        log.info("Finished deleting " + deltaFiles.size() + " files for policy " + policy);

        return deltaFiles;
    }

    public void requeue() {
        OffsetDateTime modified = OffsetDateTime.now();
        List<DeltaFile> requeuedDeltaFiles = deltaFileRepo.updateForRequeue(modified, properties.getRequeueSeconds());
        List <ActionInput> actions = requeuedDeltaFiles.stream()
                .map(deltaFile -> requeuedActionInput(deltaFile, modified))
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
        if (!actions.isEmpty()) {
            log.warn(actions.size() + " actions exceeded requeue threshold of " + properties.getRequeueSeconds() + " seconds, requeuing now");
            enqueueActions(actions);
        }
    }

    List<ActionInput> requeuedActionInput(DeltaFile deltaFile, OffsetDateTime modified) {
        return deltaFile.getActions().stream()
                .filter(a -> a.getState().equals(ActionState.QUEUED) && a.getModified().toInstant().toEpochMilli() == modified.toInstant().toEpochMilli())
                .map(action -> toActionInput(action, deltaFile))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private ActionConfiguration actionConfiguration(String actionName, DeltaFile deltaFile) {
        try {
            if (DeltaFileStage.INGRESS.equals(deltaFile.getStage())) {
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
        if (DeltaFileStage.EGRESS.equals(deltaFile.getStage())) {
            try {
                return egressFlowService.getFlowName(action.getName());
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
            ErrorInput error = ErrorInput.newBuilder().cause(errorMessage).build();
            ActionEventInput event = ActionEventInput.newBuilder().did(deltaFile.getDid()).action(action.getName()).error(error).build();
            try {
                this.error(deltaFile, event);
            } catch (JsonProcessingException ex) {
                log.error("Failed to create error for " + deltaFile.getDid() + " with event " + event + ": " + ex.getMessage());
            }

            return null;
        }

        return actionConfiguration.buildActionInput(deltaFile, properties.getSystemName(), egressFlow);
    }

    public void processActionEvents() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                ActionEventInput event = actionEventQueue.takeResult();
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
        } catch (Throwable e) {
            log.error("Error receiving event: " + e.getMessage());
        }
    }

    private void enqueueActions(List<ActionInput> enqueueActions) {
        try {
            actionEventQueue.putActions(enqueueActions);
        } catch (JedisConnectionException e) {
            // This scenario is most likely due to all Redis instances being unavailable
            // Eating this exception.  Subsequent timeout/retry will ensure the DeltaFile is processed
            log.error("Unable to post action(s) to Redis queue", e);
        }
    }

    private void deleteContent(List<DeltaFile> deltaFiles, String policy) {
        for (DeltaFile deltaFile : deltaFiles) {
            deltaFile.markForDelete(policy);
        }
        contentStorageService.deleteAll(deltaFiles.stream()
                .map(DeltaFile::storedContentReferences)
                .flatMap(Collection::stream)
                .collect(Collectors.toList()));
    }

    private void deleteMetadata(List<DeltaFile> deltaFiles) {
        deltaFileRepo.deleteAll(deltaFiles);
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

    public List<String> indexedMetadataKeys(String domain) {
        return deltaFileRepo.indexedMetadataKeys(domain);
    }
}
