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
package org.deltafi.core.domain.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.deltafi.common.content.ContentReference;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.api.types.*;
import org.deltafi.core.domain.configuration.ActionConfiguration;
import org.deltafi.core.domain.configuration.DeltaFiProperties;
import org.deltafi.core.domain.exceptions.UnexpectedActionException;
import org.deltafi.core.domain.exceptions.UnknownTypeException;
import org.deltafi.core.domain.generated.types.*;
import org.deltafi.core.domain.repo.DeltaFileRepo;
import org.deltafi.core.domain.retry.MongoRetryable;
import org.deltafi.core.domain.types.EgressFlow;
import org.deltafi.core.domain.types.IngressFlow;
import org.jetbrains.annotations.NotNull;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import redis.clients.jedis.exceptions.JedisConnectionException;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static org.deltafi.common.constant.DeltaFiConstants.INGRESS_ACTION;
import static org.deltafi.core.domain.api.Constants.ERROR_DOMAIN;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeltaFilesService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private static final int DEFAULT_QUERY_LIMIT = 50;

    final IngressFlowService ingressFlowService;
    final EnrichFlowService enrichFlowService;
    final EgressFlowService egressFlowService;
    final DeltaFiProperties properties;
    final StateMachine stateMachine;
    final DeltaFileRepo deltaFileRepo;
    final RedisService redisService;
    final ContentStorageService contentStorageService;

    public static final int EXECUTOR_THREADS = 16;

    private final ExecutorService executor = Executors.newFixedThreadPool(EXECUTOR_THREADS);

    public DeltaFile getDeltaFile(String did) {
        return deltaFileRepo.findById(did).orElse(null);
    }

    public DeltaFiles getDeltaFiles(Integer offset, Integer limit, DeltaFilesFilter filter, DeltaFileOrder orderBy) {
        return getDeltaFiles(offset, limit, filter, orderBy, null);
    }

    public DeltaFiles getDeltaFiles(Integer offset, Integer limit, DeltaFilesFilter filter, DeltaFileOrder orderBy, List<String> includeFields) {
        return deltaFileRepo.deltaFiles(offset, (Objects.nonNull(limit) && limit > 0) ? limit : DEFAULT_QUERY_LIMIT, filter, orderBy, includeFields);
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
        IngressFlow ingressFlow = ingressFlowService.getRunningFlowByName(input.getSourceInfo().getFlow());

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
                .stage(DeltaFileStage.INGRESS)
                .actions(new ArrayList<>(List.of(ingressAction)))
                .sourceInfo(input.getSourceInfo())
                .protocolStack(List.of(new ProtocolLayer(ingressFlow.getType(), INGRESS_ACTION, input.getContent(), null)))
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

    @MongoRetryable
    public DeltaFile handleActionEvent(ActionEventInput event) throws JsonProcessingException {
        DeltaFile deltaFile = getDeltaFile(event.getDid());

        if (deltaFile == null) {
            throw new DgsEntityNotFoundException("Received event for unknown did: " + event);
        }

        if (deltaFile.noPendingAction(event.getAction())) {
            throw new UnexpectedActionException(event.getAction(), event.getDid(), deltaFile.queuedActions());
        }

        switch (event.getType()) {
            case TRANSFORM:
                return transform(deltaFile, event);
            case LOAD:
                return load(deltaFile, event);
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
            deltaFile.getProtocolStack().add(event.getTransform().getProtocolLayer());
        }
        deltaFile.completeAction(event);

        return advanceAndSave(deltaFile);
    }

    public DeltaFile load(DeltaFile deltaFile, ActionEventInput event) {
        deltaFile.completeAction(event);

        if (event.getLoad() != null) {
            if (event.getLoad().getProtocolLayer() != null) {
                deltaFile.getProtocolStack().add(event.getLoad().getProtocolLayer());
            }
            if (event.getLoad().getDomains() != null) {
                for (DomainInput domain : event.getLoad().getDomains()) {
                    deltaFile.addDomain(domain.getName(), domain.getValue(), domain.getMediaType());
                }
            }
        }

        return advanceAndSave(deltaFile);
    }

    public DeltaFile enrich(DeltaFile deltaFile, ActionEventInput event) {
        deltaFile.completeAction(event);

        if (event.getEnrich() != null && event.getEnrich().getEnrichments() != null) {
            for (EnrichmentInput enrichment : event.getEnrich().getEnrichments()) {
                deltaFile.addEnrichment(enrichment.getName(), enrichment.getValue(), enrichment.getMediaType());
            }
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
    public DeltaFile filter(DeltaFile deltaFile, ActionEventInput event) {
        if (deltaFile.noPendingAction(event.getAction())) {
            throw new UnexpectedActionException(event.getAction(), event.getDid(), deltaFile.queuedActions());
        }

        deltaFile.filterAction(event, event.getFilter().getMessage());
        deltaFile.setFiltered(true);

        return advanceAndSave(deltaFile);
    }

    @MongoRetryable
    public DeltaFile error(DeltaFile deltaFile, ActionEventInput event) throws JsonProcessingException {
        if (deltaFile.noPendingAction(event.getAction())) {
            throw new UnexpectedActionException(event.getAction(), deltaFile.getDid(), deltaFile.queuedActions());
        }

        ErrorInput errorInput = event.getError();

        if (deltaFile.hasErrorDomain()) {
            log.error("DeltaFile with error domain has thrown an error:\n" +
                    "Error DID: " + deltaFile.getDid() + "\n" +
                    "Errored in action : " + event.getAction() + "\n" +
                    "Inception Error cause: " + errorInput.getCause() + "\n" +
                    "Inception Error context: " + errorInput.getContext() + "\n");
            deltaFile.errorAction(event, errorInput.getCause(), errorInput.getContext());

            return deltaFile;
        }

        deltaFile.errorAction(event, errorInput.getCause(), errorInput.getContext());

        DeltaFile errorDeltaFile = buildErrorDeltaFile(deltaFile, event);
        advanceAndSave(errorDeltaFile);

        return advanceAndSave(deltaFile);
    }

    private static DeltaFile buildErrorDeltaFile(DeltaFile deltaFile, ActionEventInput event) throws JsonProcessingException {
        String did = UUID.randomUUID().toString();

        if (deltaFile.getChildDids() == null) {
            deltaFile.setChildDids(List.of(did));
        } else {
            deltaFile.getChildDids().add(did);
        }

        Domain errorDomain = buildErrorDomain(deltaFile, event);

        OffsetDateTime now = OffsetDateTime.now();

        return DeltaFile.newBuilder()
                .did(did)
                .parentDids(List.of(deltaFile.getDid()))
                .childDids(Collections.emptyList())
                .stage(DeltaFileStage.EGRESS)
                .actions(new ArrayList<>())
                .sourceInfo(new SourceInfo(deltaFile.getSourceInfo().getFilename() + ".error",
                        deltaFile.getSourceInfo().getFlow(), deltaFile.getSourceInfo().getMetadata()))
                .protocolStack(deltaFile.getProtocolStack())
                .domains(List.of(errorDomain))
                .enrichment(new ArrayList<>())
                .formattedData(Collections.emptyList())
                .created(now)
                .modified(now)
                .egressed(false)
                .filtered(false)
                .build();
    }

    private static Domain buildErrorDomain(DeltaFile deltaFile, ActionEventInput event) throws JsonProcessingException {
        ErrorDomain errorDomain = ErrorDomain.newBuilder()
                .cause(event.getError().getCause())
                .context(event.getError().getContext())
                .fromAction(event.getAction())
                .originatorDid(event.getDid())
                .originator(deltaFile)
                .build();
        return new Domain(ERROR_DOMAIN, OBJECT_MAPPER.writeValueAsString(errorDomain), MediaType.APPLICATION_JSON_VALUE);
    }

    @MongoRetryable
    public DeltaFile split(DeltaFile deltaFile, ActionEventInput event) {
        List<SplitInput> splits = event.getSplit();
        List<DeltaFile> childDeltaFiles = Collections.emptyList();

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
                String ingressType = ingressFlowService.getRunningFlowByName(split.getSourceInfo().getFlow()).getType();
                DeltaFile child = DeltaFile.newBuilder()
                        .did(UUID.randomUUID().toString())
                        .parentDids(List.of(deltaFile.getDid()))
                        .childDids(Collections.emptyList())
                        .stage(DeltaFileStage.INGRESS)
                        .actions(new ArrayList<>(List.of(action)))
                        .sourceInfo(split.getSourceInfo())
                        .protocolStack(List.of(new ProtocolLayer(ingressType, INGRESS_ACTION, split.getContent(), Collections.emptyList())))
                        .domains(Collections.emptyList())
                        .enrichment(Collections.emptyList())
                        .formattedData(Collections.emptyList())
                        .created(now)
                        .modified(now)
                        .egressed(false)
                        .filtered(false)
                        .build();

                enqueueActions.addAll(stateMachine.advance(child));

                calculateTotalBytes(child);

                return child;
            }).collect(Collectors.toList());

            deltaFile.setChildDids(childDeltaFiles.stream().map(DeltaFile::getDid).collect(Collectors.toList()));

            deltaFile.splitAction(event);
        }

        stateMachine.advance(deltaFile);

        // do this in two shots.  saveAll performs a bulk insert, but only if all the entries are new
        deltaFileRepo.save(deltaFile);
        deltaFileRepo.saveAll(childDeltaFiles);

        enqueueActions(enqueueActions);

        return deltaFile;
    }

    public static void calculateTotalBytes(DeltaFile deltaFile) {
        List<ContentReference> contentReferences = deltaFile.getProtocolStack().stream().flatMap(p -> p.getContent().stream()).map(Content::getContentReference).collect(Collectors.toList());
        contentReferences.addAll(deltaFile.getFormattedData().stream().map(FormattedData::getContentReference).collect(Collectors.toList()));

        // keep track of the first and last offset for each unique did + uuid contentReference
        // make an assumption that we won't have disjoint segments
        Map<String, Pair<Long, Long>> segments = new HashMap<>();
        contentReferences.forEach(c -> {
            if (segments.containsKey(c.getDid() + c.getUuid())) {
                Pair<Long, Long> segment = segments.get(c.getDid() + c.getUuid());
                segments.put(c.getDid() + c.getUuid(), Pair.of(Math.min(segment.getLeft(), c.getOffset()), Math.max(segment.getRight(), c.getOffset() + c.getSize())));
            } else {
                segments.put(c.getDid() + c.getUuid(), Pair.of(c.getOffset(), c.getOffset() + c.getSize()));
            }
        });

        deltaFile.setTotalBytes(segments.values().stream().map(p -> p.getRight() - p.getLeft()).mapToLong(Long::longValue).sum());
    }

    @MongoRetryable
    public DeltaFile formatMany(DeltaFile deltaFile, ActionEventInput event) {
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

                enqueueActions.addAll(stateMachine.advance(child));

                calculateTotalBytes(child);

                return child;
            }).collect(Collectors.toList());

            deltaFile.setChildDids(childDeltaFiles.stream().map(DeltaFile::getDid).collect(Collectors.toList()));

            deltaFile.splitAction(event);
        }

        stateMachine.advance(deltaFile);

        // do this in two shots.  saveAll performs a bulk insert, but only if all the entries are new
        deltaFileRepo.save(deltaFile);
        deltaFileRepo.saveAll(childDeltaFiles);

        enqueueActions(enqueueActions);

        return deltaFile;
    }

    public List<RetryResult> retry(@NotNull List<String> dids, String replaceFilename, String replaceFlow, @NotNull List<String> removeSourceMetadata, @NotNull List<KeyValue> replaceSourceMetadata) {
        return dids.stream()
                .map(did -> {
                    RetryResult result = RetryResult.newBuilder()
                            .did(did)
                            .success(true)
                            .build();

                    try {
                        DeltaFile deltaFile = getDeltaFile(did);

                        if (deltaFile == null) {
                            result.setSuccess(false);
                            result.setError("DeltaFile with did " + did + " not found");
                        } else {
                            List<String> requeueActions = deltaFile.retryErrors();
                            if (requeueActions.isEmpty()) {
                                result.setSuccess(false);
                                result.setError("DeltaFile with did " + did + " had no errors");
                            } else {
                                deltaFile.setStage(DeltaFileStage.INGRESS);
                                deltaFile.setErrorAcknowledged(null);
                                deltaFile.setErrorAcknowledgedReason(null);

                                applyRetryOverrides(deltaFile, replaceFilename, replaceFlow, removeSourceMetadata, replaceSourceMetadata);

                                advanceAndSave(deltaFile);
                            }
                        }
                    } catch (Exception e) {
                        result.setSuccess(false);
                        result.setError(e.getMessage());
                    }
                    return result;
                })
                .collect(Collectors.toList());
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

    public List<RetryResult> replay(@NotNull List<String> dids, String replaceFilename, String replaceFlow, @NotNull List<String> removeSourceMetadata, @NotNull List<KeyValue> replaceSourceMetadata) {
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
                        DeltaFile deltaFile = getDeltaFile(did);

                        if (deltaFile == null) {
                            result.setSuccess(false);
                            result.setError("DeltaFile with did " + did + " not found");
                        } else if (deltaFile.getReplayed() != null) {
                            result.setSuccess(false);
                            result.setError("DeltaFile with did " + did + " has already been replayed with child " + deltaFile.getReplayDid());
                        } else {
                            OffsetDateTime now = OffsetDateTime.now();
                            Action action = Action.newBuilder()
                                    .name(INGRESS_ACTION)
                                    .state(ActionState.COMPLETE)
                                    .created(now)
                                    .modified(now)
                                    .build();

                            String flow = replaceFlow == null ? deltaFile.getSourceInfo().getFlow() : replaceFlow;
                            String ingressType = ingressFlowService.getRunningFlowByName(flow).getType();

                            DeltaFile child = DeltaFile.newBuilder()
                                    .did(UUID.randomUUID().toString())
                                    .parentDids(List.of(deltaFile.getDid()))
                                    .childDids(Collections.emptyList())
                                    .stage(DeltaFileStage.INGRESS)
                                    .actions(new ArrayList<>(List.of(action)))
                                    .sourceInfo(deltaFile.getSourceInfo())
                                    .protocolStack(List.of(new ProtocolLayer(ingressType, INGRESS_ACTION, deltaFile.getProtocolStack().get(0).getContent(), null)))
                                    .domains(Collections.emptyList())
                                    .enrichment(Collections.emptyList())
                                    .formattedData(Collections.emptyList())
                                    .created(now)
                                    .modified(now)
                                    .egressed(false)
                                    .filtered(false)
                                    .build();

                            applyRetryOverrides(child, replaceFilename, replaceFlow, removeSourceMetadata, replaceSourceMetadata);

                            enqueueActions.addAll(stateMachine.advance(child));

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
        OffsetDateTime now = OffsetDateTime.now();
        List<DeltaFile> changedDeltaFiles = new ArrayList<>();

        List<AcknowledgeResult> results = dids.stream()
                .map(did -> {
                    AcknowledgeResult result = AcknowledgeResult.newBuilder()
                            .did(did)
                            .success(true)
                            .build();

                    try {
                        DeltaFile deltaFile = getDeltaFile(did);

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

    public DeltaFile advanceAndSave(DeltaFile deltaFile) {
        List<ActionInput> enqueueActions = stateMachine.advance(deltaFile);
        if (properties.getDelete().isOnCompletion() && deltaFile.getStage().equals(DeltaFileStage.COMPLETE)) {
            delete(Collections.singletonList(deltaFile), "on completion", false);
        } else {
            deltaFileRepo.save(deltaFile);
            if (!enqueueActions.isEmpty()) {
                enqueueActions(enqueueActions);
            }
        }
        return deltaFile;
    }

    public void delete(OffsetDateTime createdBefore, OffsetDateTime completedBefore, Long minBytes, String flow, String policy, boolean deleteMetadata) {
        delete(deltaFileRepo.findForDelete(createdBefore, completedBefore, minBytes, flow, policy, deleteMetadata), policy, deleteMetadata);
    }

    public void delete(long bytesToDelete, String flow, String policy, boolean deleteMetadata) {
        delete(deltaFileRepo.findForDelete(bytesToDelete, flow, policy), policy, deleteMetadata);
    }

    public void delete(List<DeltaFile> deltaFiles, String policy, boolean deleteMetadata) {
        deleteContent(deltaFiles, policy);
        if (deleteMetadata) {
            deleteMetadata(deltaFiles);
        } else {
            deltaFileRepo.saveAll(deltaFiles);
        }
    }

    public void requeue() {
        OffsetDateTime modified = OffsetDateTime.now();
        List<DeltaFile> requeuedDeltaFiles = deltaFileRepo.updateForRequeue(modified, properties.getRequeueSeconds());
        requeuedDeltaFiles.forEach(deltaFile -> enqueueActions(requeuedActionInput(deltaFile, modified)));
    }

    List<ActionInput> requeuedActionInput(DeltaFile deltaFile, OffsetDateTime modified) {
        return deltaFile.getActions().stream()
                .filter(a -> a.getState().equals(ActionState.QUEUED) && a.getModified().toInstant().toEpochMilli() == modified.toInstant().toEpochMilli())
                .map(action -> toActionInput(action, deltaFile))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private ActionInput toActionInput(Action action, DeltaFile deltaFile) {
        ActionConfiguration actionConfiguration = null;
        if (DeltaFileStage.INGRESS.equals(deltaFile.getStage())) {
            actionConfiguration = ingressFlowService.findActionConfig(deltaFile.getSourceInfo().getFlow(), action.getName());
        } else if (DeltaFileStage.ENRICH.equals(deltaFile.getStage())) {
            actionConfiguration = enrichFlowService.findActionConfig(action.getName());
        } else if (DeltaFileStage.EGRESS.equals(deltaFile.getStage())){
            actionConfiguration = egressFlowService.findActionConfig(action.getName());
        }

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

        return actionConfiguration.buildActionInput(deltaFile);
    }

    public void processActionEvents() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                ActionEventInput event = redisService.dgsFeed();
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
            redisService.enqueue(enqueueActions);
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
        contentStorageService.deleteAll(deltaFiles.stream().map(DeltaFile::getDid).collect(Collectors.toList()));
    }

    private void deleteMetadata(List<DeltaFile> deltaFiles) {
        deltaFileRepo.deleteAll(deltaFiles);
    }
}
