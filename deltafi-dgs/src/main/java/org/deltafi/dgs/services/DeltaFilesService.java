package org.deltafi.dgs.services;

import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException;
import org.deltafi.dgs.api.types.DeltaFile;
import org.deltafi.dgs.api.types.ErrorDomain;
import org.deltafi.dgs.configuration.DeltaFiProperties;
import org.deltafi.dgs.configuration.IngressFlowConfiguration;
import org.deltafi.dgs.converters.DeltaFileConverter;
import org.deltafi.dgs.converters.ErrorConverter;
import org.deltafi.dgs.exceptions.ActionConfigException;
import org.deltafi.dgs.exceptions.UnexpectedActionException;
import org.deltafi.dgs.exceptions.UnknownTypeException;
import org.deltafi.dgs.generated.types.*;
import org.deltafi.dgs.repo.DeltaFileRepo;
import org.deltafi.dgs.repo.ErrorRepo;
import org.deltafi.dgs.retry.MongoRetryable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
public class DeltaFilesService {

    private static final Logger log = LoggerFactory.getLogger(DeltaFilesService.class);

    final DeltaFiConfigService configService;
    final DeltaFiProperties properties;
    final StateMachine stateMachine;
    final DeltaFileRepo deltaFileRepo;
    final ErrorRepo errorRepo;
    final RedisService redisService;
    final ExecutorService executor = Executors.newFixedThreadPool(16);

    @SuppressWarnings("CdiInjectionPointsInspection")
    public DeltaFilesService(DeltaFiConfigService configService, DeltaFiProperties properties, StateMachine stateMachine, DeltaFileRepo deltaFileRepo, ErrorRepo errorRepo, RedisService redisService) {
        this.configService = configService;
        this.properties = properties;
        this.stateMachine = stateMachine;
        this.deltaFileRepo = deltaFileRepo;
        this.errorRepo = errorRepo;
        this.redisService = redisService;
    }

    public void addDeltaFile(DeltaFile deltaFile) {
        deltaFileRepo.save(deltaFile);
    }

    public DeltaFile getDeltaFile(String did) {
        return deltaFileRepo.findById(did).orElse(null);
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
        return deltaFileRepo.findByStageOrderByModifiedDesc(DeltaFileStage.ERROR.name(), pageRequest).getContent();
    }

    public DeltaFile getLastWithFilename(String filename) {
        PageRequest pageRequest = PageRequest.of(0, 1);
        List<DeltaFile> matches = deltaFileRepo.findBySourceInfoFilenameOrderByCreatedDesc(filename, pageRequest).getContent();
        return matches.isEmpty() ? null : matches.get(0);
    }

    @MongoRetryable
    public DeltaFile handleActionEvent(ActionEventInput event) {
        if (event.getType().equals(ActionEventType.INGRESS)) {
            return addDeltaFile(event);
        }

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
        }

        throw new UnknownTypeException(event.getAction(), event.getDid(), event.getType());
    }

    public DeltaFile addDeltaFile(ActionEventInput event) {
        String flow = event.getIngress().getSourceInfo().getFlow();
        IngressFlowConfiguration flowConfiguration = configService.getIngressFlow(flow).orElseThrow(() -> new DgsEntityNotFoundException("Ingress flow " + flow + " is not configured."));

        DeltaFile deltaFile = DeltaFileConverter.convert(event.getDid(), event.getIngress().getSourceInfo(), event.getIngress().getObjectReference(), event.getIngress().getCreated(), flowConfiguration.getType());

        return advanceAndSave(deltaFile);
    }

    public DeltaFile transform(DeltaFile deltaFile, ActionEventInput event) {
        if (event.getTransform().getProtocolLayer() != null) {
            deltaFile.getProtocolStack().add(DeltaFileConverter.convert(event.getTransform().getProtocolLayer()));
        }
        deltaFile.completeAction(event.getAction());

        return advanceAndSave(deltaFile);
    }

    public DeltaFile load(DeltaFile deltaFile, ActionEventInput event) {
        deltaFile.getDomains().setDomainTypes(event.getLoad().getDomains());
        deltaFile.completeAction(event.getAction());

        return advanceAndSave(deltaFile);
    }

    public DeltaFile enrich(DeltaFile deltaFile, ActionEventInput event) {
        deltaFile.getEnrichment().setEnrichmentTypes(event.getEnrich().getEnrichments());
        deltaFile.completeAction(event.getAction());

        return advanceAndSave(deltaFile);
    }

    public DeltaFile format(DeltaFile deltaFile, ActionEventInput event) {
        FormattedData formattedData = FormattedData.newBuilder()
                .formatAction(event.getAction())
                .filename(event.getFormat().getFilename())
                .metadata(DeltaFileConverter.convertKeyValueInputs(event.getFormat().getMetadata()))
                .objectReference(DeltaFileConverter.convert(event.getFormat().getObjectReference()))
                .egressActions(configService.getEgressFlowsWithFormatAction(event.getAction()))
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
    public DeltaFile error(DeltaFile deltaFile, ActionEventInput event) {
        ErrorInput errorInput = event.getError();
        if(deltaFile.hasErrorDomain()) {
            log.error("DeltaFile with error domain has thrown an error:\n" +
                    "Error DID: " + deltaFile.getDid() + "\n" +
                    "Errored in action : " + event.getAction() + "\n" +
                    "Inception Error cause: " + errorInput.getCause() + "\n" +
                    "Inception Error context: " + errorInput.getContext() + "\n");
            if (!deltaFile.noPendingAction(event.getAction())) {
                deltaFile.errorAction(event.getAction(), errorInput.getCause(), errorInput.getContext());
            }
            return deltaFile;
        }

        if (deltaFile.noPendingAction(event.getAction())) {
            throw new UnexpectedActionException(event.getAction(), deltaFile.getDid(), deltaFile.queuedActions());
        }

        deltaFile.errorAction(event.getAction(), errorInput.getCause(), errorInput.getContext());

        ErrorDomain errorDomain = ErrorConverter.convert(errorInput, deltaFile);
        DeltaFile errorDeltaFile = DeltaFileConverter.convert(deltaFile, errorDomain);

        errorRepo.save(errorDomain);

        advanceAndSave(errorDeltaFile);

        return advanceAndSave(deltaFile);
    }

    @MongoRetryable
    public DeltaFile retry(String did) {
        DeltaFile deltaFile = getDeltaFile(did);

        deltaFile.retryErrors();
        deltaFile.setStage(DeltaFileStage.INGRESS.name());

        return advanceAndSave(deltaFile);
    }

    public DeltaFile advanceAndSave(DeltaFile deltaFile) {
        List<String> enqueueActions = stateMachine.advance(deltaFile);
        if (properties.getDelete().isOnCompletion() && deltaFile.getStage().equals(DeltaFileStage.COMPLETE.toString())) {
            deltaFileRepo.deleteById(deltaFile.getDid());
        } else {
            deltaFileRepo.save(deltaFile);
            if (!enqueueActions.isEmpty()) {
                enqueueActions(enqueueActions, deltaFile);
            }
        }
        return deltaFile;
    }

    public void markForDelete(OffsetDateTime createdBefore, OffsetDateTime completedBefore, String flow, String policy) {
        deltaFileRepo.markForDelete(createdBefore, completedBefore, flow, policy);
    }

    public void delete(List<String> dids) {
        deltaFileRepo.deleteByDidIn(dids);
    }

    public void requeue() {
        OffsetDateTime modified = OffsetDateTime.now();
        List<DeltaFile> requeuedDeltaFiles = deltaFileRepo.updateForRequeue(modified);
        requeuedDeltaFiles.forEach(deltaFile -> enqueueActions(requeuedActions(deltaFile, modified), deltaFile));
    }

    private List<String> requeuedActions(DeltaFile deltaFile, OffsetDateTime modified) {
        return deltaFile.getActions().stream().filter(a -> a.getState().equals(ActionState.QUEUED) && a.getModified().toInstant().toEpochMilli() == modified.toInstant().toEpochMilli()).map(Action::getName).collect(Collectors.toList());
    }

    public void getActionEvents() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                ActionEventInput event = redisService.dgsFeed();
                executor.submit(() -> {
                    int count = 0;
                    while(true) {
                        try {
                            count += 1;
                            handleActionEvent(event);
                            break;
                        } catch (OptimisticLockingFailureException e) {
                            if (count < 10) {
                                // retry manually
                                handleActionEvent(event);
                            } else {
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
        } catch (OptimisticLockingFailureException e) {
            // rethrow this exception so that @MongoRetryable works
            throw e;
        } catch (Throwable e) {
            log.error("Error receiving event: " + e.getMessage());
        }
    }

    public void enqueueActions(List<String> actions, DeltaFile deltaFile) {
        try {
            redisService.enqueue(actions, deltaFile);
        } catch (ActionConfigException e) {
            log.error("Failed to enqueue {} with error {}", deltaFile.getDid(), e.getMessage());
            ErrorInput error = ErrorInput.newBuilder().cause(e.getMessage()).build();
            ActionEventInput event = ActionEventInput.newBuilder().did(deltaFile.getDid()).action(e.getActionName()).error(error).build();
            this.error(deltaFile, event);
        }
    }
}

