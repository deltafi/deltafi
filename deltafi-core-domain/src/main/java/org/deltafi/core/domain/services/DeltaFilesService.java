package org.deltafi.core.domain.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.core.domain.api.types.*;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.configuration.DeltaFiProperties;
import org.deltafi.core.domain.configuration.IngressFlowConfiguration;
import org.deltafi.core.domain.converters.ErrorConverter;
import org.deltafi.core.domain.delete.DeleteConstants;
import org.deltafi.core.domain.exceptions.ActionConfigException;
import org.deltafi.core.domain.exceptions.UnexpectedActionException;
import org.deltafi.core.domain.exceptions.UnknownTypeException;
import org.deltafi.core.domain.generated.types.*;
import org.deltafi.core.domain.repo.DeltaFileRepo;
import org.deltafi.core.domain.retry.MongoRetryable;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

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

    final DeltaFiConfigService configService;
    final DeltaFiProperties properties;
    final StateMachine stateMachine;
    final DeltaFileRepo deltaFileRepo;
    final RedisService redisService;

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
        String flow = input.getSourceInfo().getFlow();
        IngressFlowConfiguration flowConfiguration = configService.getIngressFlow(flow).orElseThrow(() -> new DgsEntityNotFoundException("Ingress flow " + flow + " is not configured."));

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
                .protocolStack(List.of(new ProtocolLayer(flowConfiguration.getType(), INGRESS_ACTION, input.getContent(), null)))
                .domains(Collections.emptyList())
                .enrichment(Collections.emptyList())
                .formattedData(Collections.emptyList())
                .created(input.getCreated())
                .modified(now)
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
            case DELETE:
                delete(deltaFile);
                return deltaFile;
            case SPLIT:
                return split(deltaFile, event);
        }

        throw new UnknownTypeException(event.getAction(), event.getDid(), event.getType());
    }

    public DeltaFile transform(DeltaFile deltaFile, ActionEventInput event) {
        if (event.getTransform().getProtocolLayer() != null) {
            deltaFile.getProtocolStack().add(event.getTransform().getProtocolLayer());
        }
        deltaFile.completeAction(event.getAction());

        return advanceAndSave(deltaFile);
    }

    public DeltaFile load(DeltaFile deltaFile, ActionEventInput event) {
        deltaFile.completeAction(event.getAction());

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
        deltaFile.completeAction(event.getAction());

        if (event.getEnrich() != null && event.getEnrich().getEnrichments() != null) {
            for (EnrichmentInput enrichment : event.getEnrich().getEnrichments()) {
                deltaFile.addEnrichment(enrichment.getName(), enrichment.getValue(), enrichment.getMediaType());
            }
        }

        return advanceAndSave(deltaFile);
    }

    public DeltaFile format(DeltaFile deltaFile, ActionEventInput event) {
        FormattedData formattedData = FormattedData.newBuilder()
                .formatAction(event.getAction())
                .filename(event.getFormat().getFilename())
                .metadata(event.getFormat().getMetadata())
                .contentReference(event.getFormat().getContentReference())
                .egressActions(configService.getEgressActionsWithFormatAction(event.getAction()))
                .validateActions(configService.getValidateActionsWithFormatAction(event.getAction()))
                .build();
        deltaFile.getFormattedData().add(formattedData);
        deltaFile.completeAction(event.getAction());

        return advanceAndSave(deltaFile);
    }

    public DeltaFile validate(DeltaFile deltaFile, ActionEventInput event) {
        deltaFile.completeAction(event.getAction());

        return advanceAndSave(deltaFile);
    }

    public DeltaFile egress(DeltaFile deltaFile, ActionEventInput event) {
        deltaFile.completeAction(event.getAction());

        return advanceAndSave(deltaFile);
    }

    @MongoRetryable
    public DeltaFile filter(DeltaFile deltaFile, ActionEventInput event) {
        if (deltaFile.noPendingAction(event.getAction())) {
            throw new UnexpectedActionException(event.getAction(), event.getDid(), deltaFile.queuedActions());
        }

        deltaFile.filterAction(event.getAction(), event.getFilter().getMessage());

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
            deltaFile.errorAction(event.getAction(), errorInput.getCause(), errorInput.getContext());

            return deltaFile;
        }

        deltaFile.errorAction(event.getAction(), errorInput.getCause(), errorInput.getContext());

        ErrorDomain errorDomain = ErrorConverter.convert(event, deltaFile);
        DeltaFile errorDeltaFile = convert(deltaFile, OBJECT_MAPPER.writeValueAsString(errorDomain));
        errorDeltaFile.getSourceInfo().setFilename(errorDeltaFile.getSourceInfo().getFilename() + ".error");

        advanceAndSave(errorDeltaFile);

        return advanceAndSave(deltaFile);
    }

    @MongoRetryable
    public DeltaFile split(DeltaFile deltaFile, ActionEventInput event) {
        if (deltaFile.noPendingAction(event.getAction())) {
            throw new UnexpectedActionException(event.getAction(), event.getDid(), deltaFile.queuedActions());
        }

        List<SplitInput> splits = event.getSplit();
        List<DeltaFile> deltaFilesToSave = new ArrayList<>();
        deltaFilesToSave.add(deltaFile);

        Map<String, List<DeltaFile>> enqueueActionMap = new HashMap<>();

        if (Objects.isNull(configService.getLoadAction(event.getAction()))) {
            deltaFile.errorAction(event.getAction(), "Attempted to split from an Action that is not a LoadAction: " + event.getAction(), "");
        } else if (Objects.isNull(splits) || splits.isEmpty()) {
            deltaFile.errorAction(event.getAction(), "Attempted to split DeltaFile into 0 children", "");
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
            List<String> parentDids = Collections.singletonList(deltaFile.getDid());

            List<DeltaFile> childDeltaFiles = splits.stream().map(split -> {
                String flow = split.getSourceInfo().getFlow();
                IngressFlowConfiguration flowConfiguration = configService.getIngressFlow(flow).orElseThrow(() -> new DgsEntityNotFoundException("Ingress flow " + flow + " is not configured."));

                DeltaFile child = DeltaFile.newBuilder()
                        .did(UUID.randomUUID().toString())
                        .parentDids(parentDids)
                        .childDids(Collections.emptyList())
                        .stage(DeltaFileStage.INGRESS)
                        .actions(new ArrayList<>(List.of(action)))
                        .sourceInfo(split.getSourceInfo())
                        .protocolStack(List.of(new ProtocolLayer(flowConfiguration.getType(), INGRESS_ACTION, split.getContent(), Collections.emptyList())))
                        .domains(Collections.emptyList())
                        .enrichment(Collections.emptyList())
                        .formattedData(Collections.emptyList())
                        .created(now)
                        .modified(now)
                        .build();

                List<String> enqueueActions = stateMachine.advance(child);
                enqueueActions.forEach(a -> {
                    if (!enqueueActionMap.containsKey(a)) {
                        enqueueActionMap.put(a, new ArrayList<>());
                    }
                    enqueueActionMap.get(a).add(child);
                });

                if (properties.getDelete().isOnCompletion() && deltaFile.getStage().equals(DeltaFileStage.COMPLETE)) {
                    deltaFile.markForDelete("on completion");

                    if (!enqueueActionMap.containsKey(DeleteConstants.DELETE_ACTION)) {
                        enqueueActionMap.put(DeleteConstants.DELETE_ACTION, new ArrayList<>());
                    }
                    enqueueActionMap.get(DeleteConstants.DELETE_ACTION).add(child);
                }

                return child;
            }).collect(Collectors.toList());

            deltaFilesToSave.addAll(childDeltaFiles);
            deltaFile.setChildDids(childDeltaFiles.stream().map(DeltaFile::getDid).collect(Collectors.toList()));

            deltaFile.splitAction(event.getAction());
        }

        stateMachine.advance(deltaFile);
        deltaFileRepo.saveAll(deltaFilesToSave);
        enqueueActions(enqueueActionMap);

        return deltaFile;
    }

    private DeltaFile convert(DeltaFile originator, String errorDomain) {
        OffsetDateTime now = OffsetDateTime.now();
        return DeltaFile.newBuilder()
                .did(UUID.randomUUID().toString())
                .stage(DeltaFileStage.EGRESS)
                .actions(new ArrayList<>())
                .sourceInfo(new SourceInfo(originator.getSourceInfo().getFilename(),
                        originator.getSourceInfo().getFlow(),
                        originator.getSourceInfo().getMetadata()))
                .protocolStack(originator.getProtocolStack())
                .domains(Collections.singletonList(new Domain(ERROR_DOMAIN, errorDomain, MediaType.APPLICATION_JSON_VALUE)))
                .enrichment(new ArrayList<>())
                .formattedData(Collections.emptyList())
                .created(now)
                .modified(now)
                .build();
    }

    public List<RetryResult> retry(List<String> dids) {
        return dids.stream()
                .map(did -> {
                    RetryResult result = RetryResult.newBuilder()
                            .did(did)
                            .success(true)
                            .build();

                    try {
                        DeltaFile deltaFile = getDeltaFile(did);

                        if (Objects.isNull(deltaFile)) {
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
        List<String> enqueueActions = stateMachine.advance(deltaFile);
        if (properties.getDelete().isOnCompletion() && deltaFile.getStage().equals(DeltaFileStage.COMPLETE)) {
            deltaFile.markForDelete("on completion");
            deltaFileRepo.save(deltaFile);
            enqueueDeleteAction(deltaFile);
        } else {
            deltaFileRepo.save(deltaFile);
            if (!enqueueActions.isEmpty()) {
                enqueueActions(enqueueActions, deltaFile);
            }
        }
        return deltaFile;
    }

    public void markForDelete(OffsetDateTime createdBefore, OffsetDateTime completedBefore, String flow, String policy) {
        List<DeltaFile> deltaFilesMarkedForDelete = deltaFileRepo.markForDelete(createdBefore, completedBefore, flow, policy);
        deltaFilesMarkedForDelete.forEach(this::enqueueDeleteAction);
    }

    private void enqueueDeleteAction(DeltaFile deltaFile) {
        enqueueActions(List.of(DeleteConstants.DELETE_ACTION), deltaFile);
    }

    public void delete(DeltaFile deltaFile) {
        deltaFileRepo.deleteById(deltaFile.getDid());
    }

    public void requeue() {
        OffsetDateTime modified = OffsetDateTime.now();
        List<DeltaFile> requeuedDeltaFiles = deltaFileRepo.updateForRequeue(modified, properties.getRequeueSeconds());
        requeuedDeltaFiles.forEach(deltaFile -> enqueueActions(requeuedActions(deltaFile, modified), deltaFile));
    }

    private List<String> requeuedActions(DeltaFile deltaFile, OffsetDateTime modified) {
        return deltaFile.getActions().stream().filter(a -> a.getState().equals(ActionState.QUEUED) && a.getModified().toInstant().toEpochMilli() == modified.toInstant().toEpochMilli()).map(Action::getName).collect(Collectors.toList());
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

    private void enqueueActions(List<String> actionNames, DeltaFile deltaFile) {
        try {
            redisService.enqueue(actionNames, deltaFile);
        } catch (ActionConfigException e) {
            log.error("Failed to enqueue {} with error {}", deltaFile.getDid(), e.getMessage());
            ErrorInput error = ErrorInput.newBuilder().cause(e.getMessage()).build();
            ActionEventInput event = ActionEventInput.newBuilder().did(deltaFile.getDid()).action(e.getActionName()).error(error).build();
            try {
                this.error(deltaFile, event);
            } catch (JsonProcessingException ex) {
                log.error("Failed to create error for " + deltaFile.getDid() + " with event " + event + ": " + e.getMessage());
            }
        }
    }

    private void enqueueActions(Map<String, List<DeltaFile>> enqueueActions) {
        try {
            redisService.enqueue(enqueueActions);
        } catch (ActionConfigException e) {
            log.error("Failed to enqueue to action {} with error {}", e.getActionName(), e.getMessage());
            ErrorInput error = ErrorInput.newBuilder().cause(e.getMessage()).build();
            enqueueActions.get(e.getActionName()).forEach(deltaFile -> {
                ActionEventInput event = ActionEventInput.newBuilder().did(deltaFile.getDid()).action(e.getActionName()).error(error).build();
                try {
                    this.error(deltaFile, event);
                } catch (JsonProcessingException ex) {
                    log.error("Failed to create error for " + deltaFile.getDid() + " with event " + event + ": " + e.getMessage());
                }
            });
        }
    }
}
