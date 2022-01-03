package org.deltafi.core.domain.services;

import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException;
import graphql.com.google.common.collect.Iterables;
import org.deltafi.common.trace.ZipkinService;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.configuration.EgressFlowConfiguration;
import org.deltafi.core.domain.configuration.EnrichActionConfiguration;
import org.deltafi.core.domain.configuration.FormatActionConfiguration;
import org.deltafi.core.domain.configuration.IngressFlowConfiguration;
import org.deltafi.core.domain.converters.KeyValueConverter;
import org.deltafi.core.domain.generated.types.DeltaFileStage;
import org.deltafi.core.domain.generated.types.ProtocolLayer;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class StateMachine {

    private final DeltaFiConfigService configService;
    private final ZipkinService zipkinService;

    @SuppressWarnings("CdiInjectionPointsInspection")
    public StateMachine(DeltaFiConfigService configService, ZipkinService zipkinService) {
        this.configService = configService;
        this.zipkinService = zipkinService;
    }

    /**
     * Advance the state of the given DeltaFile
     *
     * @param deltaFile The deltaFile to advance
     * @return a list of actions that should receive this DeltaFile next
     */
    public List<String> advance(DeltaFile deltaFile) {
        List<String> enqueueActions = new ArrayList<>();
        switch (deltaFile.getStage()) {
            case INGRESS:
                if (deltaFile.hasErroredAction()) {
                    break;
                }

                // transform
                String nextTransformAction = getTransformAction(deltaFile);
                if (nextTransformAction != null) {
                    deltaFile.queueAction(nextTransformAction);
                    enqueueActions.add(nextTransformAction);
                    break;
                }

                // load
                String loadAction = getLoadAction(deltaFile);
                if (loadAction != null && !deltaFile.hasTerminalAction(loadAction)) {
                    deltaFile.queueAction(loadAction);
                    enqueueActions.add(loadAction);
                    break;
                }

                // if transform and load are complete, move to egress stage
                deltaFile.setStage(DeltaFileStage.EGRESS);
            case EGRESS:
                // enrich
                List<String> enrichActions = getEnrichActions(deltaFile);
                List<String> newActions = new ArrayList<>(deltaFile.queueActionsIfNew(enrichActions));

                // format
                List<String> formatActions = getFormatActions(deltaFile);
                newActions.addAll(deltaFile.queueActionsIfNew(formatActions));

                // validate
                List<String> validateActions = getValidateActions(deltaFile);
                newActions.addAll(deltaFile.queueActionsIfNew(validateActions));

                // egress
                List<String> egressActions = getEgressActions(deltaFile);
                newActions.addAll(deltaFile.queueActionsIfNew(egressActions));

                enqueueActions.addAll(newActions);
                break;
        }

        if (!deltaFile.hasPendingActions()) {
            deltaFile.setStage(deltaFile.hasErroredAction() ? DeltaFileStage.ERROR : DeltaFileStage.COMPLETE);
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

    private String getLoadAction(DeltaFile deltaFile) {
        ProtocolLayer lastProtocolLayer = Iterables.getLast(deltaFile.getProtocolStack());
        Map<String, String> metadataMap = KeyValueConverter.convertKeyValues(lastProtocolLayer.getMetadata());
        for (String loadActionOrGroup : flowConfiguration(deltaFile).getLoadActions()) {
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

    List<String> getEnrichActions(DeltaFile deltaFile) {
        return configService.getEgressFlows().stream()
                .filter(egressFlow -> matchesFlowFilter(egressFlow, deltaFile))
                .flatMap(f -> f.getEnrichActions().stream())
                .filter(e -> enrichActionReady(e, deltaFile))
                .distinct()
                .collect(Collectors.toList());
    }

    private boolean enrichActionReady(String enrichActionName, DeltaFile deltaFile) {
        EnrichActionConfiguration config = configService.getEnrichAction(enrichActionName);
        return !deltaFile.hasTerminalAction(enrichActionName) &&
                deltaFile.hasDomains(config.getRequiresDomains()) &&
                deltaFile.hasEnrichments(config.getRequiresEnrichment());
    }

    List<String> getFormatActions(DeltaFile deltaFile) {
        return configService.getEgressFlows().stream()
                .filter(egressFlow -> matchesFlowFilter(egressFlow, deltaFile))
                .map(e -> e.getFormatAction())
                .filter(f -> formatActionReady(f, deltaFile))
                .collect(Collectors.toList());
    }

    private boolean formatActionReady(String formatActionName, DeltaFile deltaFile) {
        FormatActionConfiguration config = configService.getFormatAction(formatActionName);
        return !deltaFile.hasTerminalAction(formatActionName) &&
                deltaFile.hasDomains(config.getRequiresDomains()) &&
                deltaFile.hasEnrichments(config.getRequiresEnrichment());
    }

    List<String> getValidateActions(DeltaFile deltaFile) {
        return configService.getEgressFlows().stream()
                .filter(egressFlow -> validateActionsReady(egressFlow, deltaFile))
                .flatMap(f -> f.getValidateActions().stream())
                .filter(v -> !deltaFile.hasTerminalAction(v))
                .distinct()
                .collect(Collectors.toList());
    }

    private boolean validateActionsReady(EgressFlowConfiguration egressFlow, DeltaFile deltaFile) {
        return deltaFile.hasCompletedAction(egressFlow.getFormatAction()) &&
                matchesFlowFilter(egressFlow, deltaFile);
    }

    List<String> getEgressActions(DeltaFile deltaFile) {
        return configService.getEgressFlows().stream()
                .filter(egressFlow -> egressActionReady(egressFlow, deltaFile))
                .map(org.deltafi.core.domain.generated.types.EgressFlowConfiguration::getEgressAction)
                .filter(egressAction -> !deltaFile.hasTerminalAction(egressAction))
                .collect(Collectors.toList());
    }

    private boolean matchesFlowFilter(EgressFlowConfiguration egressFlow, DeltaFile deltaFile) {
        return (egressFlow.getIncludeIngressFlows() == null ||
                egressFlow.getIncludeIngressFlows().isEmpty() ||
                egressFlow.getIncludeIngressFlows().contains(deltaFile.getSourceInfo().getFlow())) &&
                (egressFlow.getExcludeIngressFlows() == null ||
                        egressFlow.getExcludeIngressFlows().isEmpty() ||
                        !egressFlow.getExcludeIngressFlows().contains(deltaFile.getSourceInfo().getFlow()));
    }

    private boolean egressActionReady(EgressFlowConfiguration egressFlow, DeltaFile deltaFile) {
        return deltaFile.hasCompletedAction(egressFlow.getFormatAction()) &&
                deltaFile.hasCompletedActions(egressFlow.getValidateActions()) &&
                matchesFlowFilter(egressFlow, deltaFile);
    }

    private void sendTrace(DeltaFile deltaFile) {
        zipkinService.createAndSendRootSpan(deltaFile.getDid(), deltaFile.getCreated(), deltaFile.getSourceInfo().getFilename(), deltaFile.getSourceInfo().getFlow());
    }
}
