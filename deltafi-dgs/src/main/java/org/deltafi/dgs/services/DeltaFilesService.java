package org.deltafi.dgs.services;

import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException;
import org.deltafi.dgs.api.types.DeltaFile;
import org.deltafi.dgs.configuration.DeltaFiProperties;
import org.deltafi.dgs.configuration.EgressConfiguration;
import org.deltafi.dgs.configuration.IngressFlowConfiguration;
import org.deltafi.dgs.converters.DeltaFileConverter;
import org.deltafi.dgs.exceptions.UnexpectedActionException;
import org.deltafi.dgs.generated.types.*;
import org.deltafi.dgs.repo.DeltaFileRepo;
import org.deltafi.dgs.retry.MongoRetryable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DeltaFilesService {

    private static final Logger log = LoggerFactory.getLogger(DeltaFilesService.class);

    final DeltaFiConfigService configService;
    final DeltaFiProperties properties;
    final StateMachine stateMachine;
    final DeltaFileRepo deltaFileRepo;
    final RedisService redisService;

    public DeltaFilesService(DeltaFiConfigService configService, DeltaFiProperties properties, StateMachine stateMachine, DeltaFileRepo deltaFileRepo, RedisService redisService) {
        this.configService = configService;
        this.properties = properties;
        this.stateMachine = stateMachine;
        this.deltaFileRepo = deltaFileRepo;
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

    public DeltaFile addDeltaFile(IngressInput ingressInput) {
        String flow = ingressInput.getSourceInfo().getFlow();
        IngressFlowConfiguration flowConfiguration = configService.getIngressFlow(flow).orElseThrow(() -> new DgsEntityNotFoundException("Ingress flow " + flow + " is not configured."));

        DeltaFile deltaFile = DeltaFileConverter.convert(ingressInput.getDid(), ingressInput.getSourceInfo(), ingressInput.getObjectReference(), ingressInput.getCreated(), flowConfiguration.getType());

        return advanceAndSave(deltaFile);
    }

    @MongoRetryable
    public DeltaFile transform(String did, String fromTransformAction, ProtocolLayerInput protocolLayer) {
        DeltaFile deltaFile = getDeltaFile(did);

        if (deltaFile.noPendingAction(fromTransformAction)) {
            throw new UnexpectedActionException(fromTransformAction, did, deltaFile.queuedActions());
        }
        if (protocolLayer != null) {
            deltaFile.getProtocolStack().add(DeltaFileConverter.convert(protocolLayer));
        }
        deltaFile.completeAction(fromTransformAction);

        return advanceAndSave(deltaFile);
    }

    @MongoRetryable
    public DeltaFile load(String did, String fromLoadAction, List<String> domains) {
        DeltaFile deltaFile = getDeltaFile(did);

        if (deltaFile.noPendingAction(fromLoadAction)) {
            throw new UnexpectedActionException(fromLoadAction, did, deltaFile.queuedActions());
        }

        deltaFile.completeAction(fromLoadAction);

        deltaFile.getDomains().setDomainTypes(domains);

        return advanceAndSave(deltaFile);
    }

    @MongoRetryable
    public DeltaFile enrich(String did, String fromEnrichAction, List<String> enrichments) {
        DeltaFile deltaFile = getDeltaFile(did);

        if (deltaFile.noPendingAction(fromEnrichAction)) {
            throw new UnexpectedActionException(fromEnrichAction, did, deltaFile.queuedActions());
        }

        deltaFile.completeAction(fromEnrichAction);

        deltaFile.getEnrichment().setEnrichmentTypes(enrichments);

        return advanceAndSave(deltaFile);
    }

    @MongoRetryable
    public DeltaFile format(String did, String fromFormatAction, FormatResultInput formatResult) {
        DeltaFile deltaFile = getDeltaFile(did);

        if (deltaFile.noPendingAction(fromFormatAction)) {
            throw new UnexpectedActionException(fromFormatAction, did, deltaFile.queuedActions());
        }

        deltaFile.completeAction(fromFormatAction);

        FormattedData formattedData = FormattedData.newBuilder()
                .formatAction(fromFormatAction)
                .filename(formatResult.getFilename())
                .metadata(DeltaFileConverter.convertKeyValueInputs(formatResult.getMetadata()))
                .objectReference(DeltaFileConverter.convert(formatResult.getObjectReference()))
                .egressActions(properties.getEgress().getEgressFlows().keySet().stream()
                        .filter(k -> properties.getEgress().getEgressFlows().get(k).getFormatAction().equals(fromFormatAction))
                        .map(EgressConfiguration::egressActionName)
                        .collect(Collectors.toList()))
                .build();
        deltaFile.getFormattedData().add(formattedData);

        return advanceAndSave(deltaFile);
    }

    @MongoRetryable
    public DeltaFile completeActionAndAdvance(String did, String fromAction) {
        DeltaFile deltaFile = getDeltaFile(did);

        if (deltaFile.noPendingAction(fromAction)) {
            throw new UnexpectedActionException(fromAction, did, deltaFile.queuedActions());
        }

        deltaFile.completeAction(fromAction);

        return advanceAndSave(deltaFile);
    }

    @MongoRetryable
    public DeltaFile error(String did, String fromAction, String message) {
        DeltaFile deltaFile = getDeltaFile(did);

        if (deltaFile.noPendingAction(fromAction)) {
            throw new UnexpectedActionException(fromAction, did, deltaFile.queuedActions());
        }

        deltaFile.errorAction(fromAction, message);

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
                redisService.enqueue(enqueueActions, deltaFile);
            }
        }
        return deltaFile;
    }

    @MongoRetryable
    public void markForDelete(OffsetDateTime createdBefore, OffsetDateTime completedBefore, String flow, String policy) {
        deltaFileRepo.markForDelete(createdBefore, completedBefore, flow, policy);
    }

    @MongoRetryable
    public void delete(List<String> dids) {
        deltaFileRepo.deleteByDidIn(dids);
    }

    @MongoRetryable
    public void requeue() {
        OffsetDateTime modified = OffsetDateTime.now();
        List<DeltaFile> requeuedDeltaFiles = deltaFileRepo.updateForRequeue(modified);
        requeuedDeltaFiles.forEach(deltaFile -> redisService.enqueue(requeuedActions(deltaFile, modified), deltaFile));
    }

    private List<String> requeuedActions(DeltaFile deltaFile, OffsetDateTime modified) {
        return deltaFile.getActions().stream().filter(a -> a.getState().equals(ActionState.QUEUED) && a.getModified().toInstant().toEpochMilli() == modified.toInstant().toEpochMilli()).map(Action::getName).collect(Collectors.toList());
    }

    public void getIngressResponses() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                IngressInput ingressInput = redisService.ingressFeed();
                addDeltaFile(ingressInput);
            }
        } catch (Throwable e) {
            log.error("Error receiving ingress: " + e.getMessage());
        }
    }
}
