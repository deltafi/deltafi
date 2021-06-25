package org.deltafi.dgs.services;

import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException;
import org.deltafi.dgs.api.types.DeltaFile;
import org.deltafi.dgs.configuration.DeltaFiProperties;
import org.deltafi.dgs.configuration.EgressFlowConfiguration;
import org.deltafi.dgs.configuration.IngressFlowConfiguration;
import org.deltafi.dgs.converters.DeltaFileConverter;
import org.deltafi.dgs.exceptions.UnexpectedActionException;
import org.deltafi.dgs.generated.types.*;
import org.deltafi.dgs.repo.DeltaFileRepo;
import org.deltafi.dgs.retry.MongoRetryable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DeltaFilesService {

    final DeltaFiConfigService configService;
    final DeltaFiProperties properties;
    final StateMachine stateMachine;
    final DeltaFileRepo deltaFileRepo;

    final Map<String, FeedStats> feedStats = new ConcurrentHashMap<>();

    public DeltaFilesService(DeltaFiConfigService configService, DeltaFiProperties properties, StateMachine stateMachine, DeltaFileRepo deltaFileRepo) {
        this.configService = configService;
        this.properties = properties;
        this.stateMachine = stateMachine;
        this.deltaFileRepo = deltaFileRepo;
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

    public List<FeedStats> getFeedStats(String actionName) {
        if (Objects.isNull(actionName)) {
            return new ArrayList<>(feedStats.values());
        } else {
            return Collections.singletonList(feedStats.get(actionName));
        }
    }

    public DeltaFile addDeltaFile(SourceInfoInput sourceInfoInput, ObjectReferenceInput objectReferenceInput) {
        String flow = sourceInfoInput.getFlow();
        IngressFlowConfiguration flowConfiguration = configService.getIngressFlow(flow).orElseThrow(() -> new DgsEntityNotFoundException("Ingress flow " + flow + " is not configured."));

        DeltaFile deltaFile = DeltaFileConverter.convert(sourceInfoInput, objectReferenceInput, flowConfiguration.getType());
        stateMachine.advance(deltaFile);
        return deltaFileRepo.save(deltaFile);
    }

    public void addFeedStats(String actionName) {
        FeedStats actionFeedStats = feedStats.get(actionName);
        if (Objects.isNull(actionFeedStats)) {
            actionFeedStats = new FeedStats(actionName);
            feedStats.put(actionName, actionFeedStats);
        }
        actionFeedStats.addQuery();
    }

    public List<DeltaFile> actionFeed(String action, Integer limit, Boolean dryRun) {
        if (!dryRun) {
            addFeedStats(action);
        }
        List<DeltaFile> deltaFiles = deltaFileRepo.findAndDispatchForAction(action, limit, dryRun);

        if (action.endsWith("TransformAction") || action.endsWith("LoadAction")) {
            deltaFiles.forEach(DeltaFile::trimProtocolLayers);
        } else if (action.endsWith("EgressAction")) {
            EgressFlowConfiguration egressFlowConfig = configService.getEgressFlowForAction(action);
            deltaFiles.forEach(d -> d.trimFormats(egressFlowConfig.getFormatAction()));
        }

        return deltaFiles;
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

        stateMachine.advance(deltaFile);

        return save(deltaFile);
    }

    @MongoRetryable
    public DeltaFile load(String did, String fromLoadAction, List<String> domains) {
        DeltaFile deltaFile = getDeltaFile(did);

        if (deltaFile.noPendingAction(fromLoadAction)) {
            throw new UnexpectedActionException(fromLoadAction, did, deltaFile.queuedActions());
        }

        deltaFile.completeAction(fromLoadAction);

        deltaFile.getDomains().setDomainTypes(domains);

        stateMachine.advance(deltaFile);

        return save(deltaFile);
    }

    @MongoRetryable
    public DeltaFile enrich(String did, String fromEnrichAction, List<String> enrichments) {
        DeltaFile deltaFile = getDeltaFile(did);

        if (deltaFile.noPendingAction(fromEnrichAction)) {
            throw new UnexpectedActionException(fromEnrichAction, did, deltaFile.queuedActions());
        }

        deltaFile.completeAction(fromEnrichAction);

        deltaFile.getEnrichment().setEnrichmentTypes(enrichments);

        stateMachine.advance(deltaFile);

        return save(deltaFile);
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
                .build();
        deltaFile.getFormattedData().add(formattedData);

        stateMachine.advance(deltaFile);

        return save(deltaFile);
    }

    @MongoRetryable
    public DeltaFile completeActionAndAdvance(String did, String fromAction) {
        DeltaFile deltaFile = getDeltaFile(did);

        if (deltaFile.noPendingAction(fromAction)) {
            throw new UnexpectedActionException(fromAction, did, deltaFile.queuedActions());
        }

        deltaFile.completeAction(fromAction);

        stateMachine.advance(deltaFile);

        if (properties.getDelete().isOnCompletion() && deltaFile.getStage().equals(DeltaFileStage.COMPLETE.toString())) {
            deltaFileRepo.deleteById(deltaFile.getDid());
            return deltaFile;
        }

        return save(deltaFile);
    }

    @MongoRetryable
    public DeltaFile error(String did, String fromAction, String message) {
        DeltaFile deltaFile = getDeltaFile(did);

        if (deltaFile.noPendingAction(fromAction)) {
            throw new UnexpectedActionException(fromAction, did, deltaFile.queuedActions());
        }

        deltaFile.errorAction(fromAction, message);

        stateMachine.advance(deltaFile);

        return save(deltaFile);
    }

    @MongoRetryable
    public DeltaFile retry(String did) {
        DeltaFile deltaFile = getDeltaFile(did);

        deltaFile.retryErrors();
        deltaFile.setStage(DeltaFileStage.INGRESS.name());

        stateMachine.advance(deltaFile);

        return save(deltaFile);
    }

    public DeltaFile save(DeltaFile deltaFile) {
        return deltaFileRepo.save(deltaFile);
    }

    public void markForDelete(OffsetDateTime createdBefore, OffsetDateTime completedBefore, String flow, String policy) {
        deltaFileRepo.markForDelete(createdBefore, completedBefore, flow, policy);
    }

    public void delete(List<String> dids) {
        deltaFileRepo.deleteByDidIn(dids);
    }
}
