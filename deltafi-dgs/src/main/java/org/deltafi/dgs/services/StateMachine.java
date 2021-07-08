package org.deltafi.dgs.services;

import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException;
import graphql.com.google.common.collect.Iterables;
import org.deltafi.common.trace.ZipkinService;
import org.deltafi.dgs.api.types.DeltaFile;
import org.deltafi.dgs.configuration.*;
import org.deltafi.dgs.converters.KeyValueConverter;
import org.deltafi.dgs.generated.types.DeltaFileStage;
import org.deltafi.dgs.generated.types.KeyValue;
import org.deltafi.dgs.generated.types.ProtocolLayer;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class StateMachine {

    private final DeltaFiConfigService configService;
    private final ZipkinService zipkinService;

    public StateMachine(DeltaFiConfigService configService, ZipkinService zipkinService) {
        this.configService = configService;
        this.zipkinService = zipkinService;
    }

    /** Advance the state of the given DeltaFile
     *
     * @param deltaFile The deltaFile to advance
     * @return a list of actions that should receive this DeltaFile next
     */
    public List<String> advance(DeltaFile deltaFile) {
        List<String> enqueueActions = new ArrayList<>();
        switch (DeltaFileStage.valueOf(deltaFile.getStage())) {
            case INGRESS:
                deltaFile.setStage(DeltaFileStage.TRANSFORM.name());
            case TRANSFORM:
                if (deltaFile.hasErroredAction()) {
                    break;
                }

                String nextTransformAction = getTransformAction(deltaFile);
                if (nextTransformAction == null) {
                    deltaFile.setStage(DeltaFileStage.LOAD.name());
                    ProtocolLayer lastProtocolLayer = Iterables.getLast(deltaFile.getProtocolStack());
                    String loadAction = getLoadAction(flowConfiguration(deltaFile).getLoadActions(), lastProtocolLayer.getMetadata());
                    if (!deltaFile.hasTerminalAction(loadAction)) {
                        if (loadAction != null) {
                            deltaFile.queueAction(loadAction);
                            enqueueActions.add(loadAction);
                        }
                        break;
                    }
                } else {
                    deltaFile.queueAction(nextTransformAction);
                    enqueueActions.add(nextTransformAction);
                    break;
                }
            case LOAD:
                deltaFile.setStage(DeltaFileStage.ENRICH.name());
            case ENRICH:
                List<String> enrichActions = getEnrichActions(deltaFile);
                List<String> newEnrichActions = deltaFile.queueActionsIfNew(enrichActions);
                enqueueActions.addAll(newEnrichActions);
                if (enrichActions.isEmpty()) {
                    deltaFile.setStage(DeltaFileStage.FORMAT.name());
                } else {
                    break;
                }
            case FORMAT:
                List<String> formatActions = getFormatActions(deltaFile);
                List<String> newFormatActions = deltaFile.queueActionsIfNew(formatActions);
                enqueueActions.addAll(newFormatActions);
                if (formatActions.isEmpty()) {
                    deltaFile.setStage(DeltaFileStage.VALIDATE.name());
                } else {
                    break;
                }
            case VALIDATE:
                List<String> validateActions = getValidateActions(deltaFile);
                List<String> newValidateActions = deltaFile.queueActionsIfNew(validateActions);
                enqueueActions.addAll(newValidateActions);
                if (validateActions.isEmpty()) {
                    deltaFile.setStage(DeltaFileStage.EGRESS.name());
                } else {
                    break;
                }
            case EGRESS:
                List<String> egressActions = getEgressActions(deltaFile);
                List<String> newEgressActions = deltaFile.queueActionsIfNew(egressActions);
                enqueueActions.addAll(newEgressActions);
                if (egressActions.isEmpty() && !deltaFile.hasErroredAction()) {
                    deltaFile.setStage(DeltaFileStage.COMPLETE.name());
                } else {
                    break;
                }
        }

        if (!deltaFile.hasPendingActions() && !deltaFile.getStage().equals(DeltaFileStage.COMPLETE.name())) {
            deltaFile.setStage(DeltaFileStage.ERROR.name());
            sendTrace(deltaFile);
        } else if (DeltaFileStage.COMPLETE.name().equals(deltaFile.getStage())) {
            sendTrace(deltaFile);
        }

        return enqueueActions;
    }

    private IngressFlowConfiguration flowConfiguration(DeltaFile deltaFile) {
        String flow = deltaFile.getSourceInfo().getFlow();
        return configService.getIngressFlow(flow).orElseThrow(() -> new DgsEntityNotFoundException("Ingress flow " + flow + " is not configured."));
    }

    private String getTransformAction(DeltaFile deltaFile) {
        Optional<String> nextAction = flowConfiguration(deltaFile).getTransformActions().stream().filter(a -> !deltaFile.hasTerminalAction(a)).findFirst();

        return nextAction.orElse(null);
    }

    private String getLoadAction(List<String> loadActionsOrGroups, List<KeyValue> metadata) {
        Map<String, String> metadataMap = KeyValueConverter.convertKeyValues(metadata);
        for (String loadActionOrGroup : loadActionsOrGroups) {
            if (loadActionOrGroup.endsWith("Group")) {
                for (String loadAction : configService.getLoadGroupActions(loadActionOrGroup)) {
                    if (loadMetadataMatches(loadAction, metadataMap)) {
                        return loadAction;
                    }
                }
            } else {
                if (loadMetadataMatches(loadActionOrGroup, metadataMap)) {
                    return loadActionOrGroup;
                }
            }
        }

        return null;
    }

    private boolean loadMetadataMatches(String loadAction, Map<String, String> metadata) {
        Map<String, String> requiresMetadata = configService.getLoadAction(loadAction).getRequiresMetadata();
        return requiresMetadata.keySet().stream().allMatch(k -> requiresMetadata.get(k).equals(metadata.get(k)));
    }

    private List<String> getEnrichActions(DeltaFile deltaFile) {
        return configService.getEnrichActions().stream().filter(key -> enrichActionReady(key, deltaFile)).collect(Collectors.toList());
    }

    private boolean enrichActionReady(String enrichActionName, DeltaFile deltaFile) {
        EnrichActionConfiguration config = configService.getEnrichAction(enrichActionName);
        return !deltaFile.hasTerminalAction(enrichActionName) &&
                deltaFile.getDomains().getDomainTypes().containsAll(config.getRequiresDomains()) &&
                deltaFile.getEnrichment().getEnrichmentTypes().containsAll(config.getRequiresEnrichment());
    }

    private List<String> getFormatActions(DeltaFile deltaFile) {
        return configService.getFormatActions().stream().filter(key -> formatActionReady(key, deltaFile)).collect(Collectors.toList());
    }

    private boolean formatActionReady(String formatActionName, DeltaFile deltaFile) {
        FormatActionConfiguration config = configService.getFormatAction(formatActionName);
        return !deltaFile.hasTerminalAction(formatActionName) &&
                deltaFile.getDomains().getDomainTypes().containsAll(config.getRequiresDomains()) &&
                deltaFile.getEnrichment().getEnrichmentTypes().containsAll(config.getRequiresEnrichment());
    }

    private List<String> getValidateActions(DeltaFile deltaFile) {
        return configService.getEgressFlows().stream()
                .filter(f -> deltaFile.hasTerminalAction(f.getFormatAction()))
                .flatMap(f -> f.getValidateActions().stream())
                .filter(v -> !deltaFile.hasTerminalAction(v))
                .distinct()
                .collect(Collectors.toList());
    }

    public List<String> getEgressActions(DeltaFile deltaFile) {
        return configService.getEgressFlows().stream()
                .filter(egressFlow -> egressActionReady(egressFlow, deltaFile))
                .map(org.deltafi.dgs.generated.types.EgressFlowConfiguration::getEgressAction)
                .filter(egressAction -> !deltaFile.hasTerminalAction(egressAction))
                .collect(Collectors.toList());
    }

    private boolean egressActionReady(EgressFlowConfiguration egressFlow, DeltaFile deltaFile) {
        return deltaFile.hasCompletedAction(egressFlow.getFormatAction()) &&
                deltaFile.hasCompletedActions(egressFlow.getValidateActions()) &&
                (egressFlow.getIncludeIngressFlows() == null ||
                        egressFlow.getIncludeIngressFlows().isEmpty() ||
                        egressFlow.getIncludeIngressFlows().contains(deltaFile.getSourceInfo().getFlow())) &&
                (egressFlow.getExcludeIngressFlows() == null ||
                        egressFlow.getExcludeIngressFlows().isEmpty() ||
                        !egressFlow.getExcludeIngressFlows().contains(deltaFile.getSourceInfo().getFlow()));
    }

    private void sendTrace(DeltaFile deltaFile) {
        zipkinService.createAndSendRootSpan(deltaFile.getDid(), deltaFile.getCreated(), deltaFile.getSourceInfo().getFilename(), deltaFile.getSourceInfo().getFlow());
    }
}
