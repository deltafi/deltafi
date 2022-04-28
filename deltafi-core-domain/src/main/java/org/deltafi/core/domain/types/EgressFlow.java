package org.deltafi.core.domain.types;


import lombok.Data;
import org.deltafi.core.domain.api.types.ConfigType;
import org.deltafi.core.domain.api.types.PluginCoordinates;
import org.deltafi.core.domain.configuration.*;
import org.deltafi.core.domain.generated.types.ActionFamily;
import org.deltafi.core.domain.generated.types.FlowState;
import org.deltafi.core.domain.generated.types.FlowStatus;
import org.deltafi.core.domain.generated.types.Variable;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.*;
import java.util.stream.Collectors;

@Data
@Document("egressFlow")
public class EgressFlow implements Flow {

    @Id
    private String name;
    private String description;
    private PluginCoordinates sourcePlugin;
    private FlowStatus flowStatus = new FlowStatus(FlowState.STOPPED, new ArrayList<>());
    private EgressActionConfiguration egressAction;
    private FormatActionConfiguration formatAction;
    private List<EnrichActionConfiguration> enrichActions = new ArrayList<>();
    private List<ValidateActionConfiguration> validateActions = new ArrayList<>();
    private List<String> includeIngressFlows = new ArrayList<>();
    private List<String> excludeIngressFlows = new ArrayList<>();
    // list of variables that are applicable to this flow
    private Set<Variable> variables = new HashSet<>();

    public List<String> validateActionNames() {
        return validateActions.stream()
                .map(ValidateActionConfiguration::getName)
                .collect(Collectors.toList());
    }

    @Override
    public List<DeltaFiConfiguration> allConfigurations() {
        List<DeltaFiConfiguration> allConfigurations = new ArrayList<>();
        allConfigurations.add(formatAction);
        allConfigurations.add(egressAction);
        allConfigurations.addAll(enrichActions);
        allConfigurations.addAll(validateActions);
        allConfigurations.add(asEgressFlowConfiguration());
        return allConfigurations;
    }

    @Override
    public List<ActionConfiguration> allActionConfigurations() {
        List<ActionConfiguration> actionConfigurations = new ArrayList<>();
        actionConfigurations.add(formatAction);
        actionConfigurations.add(egressAction);
        actionConfigurations.addAll(enrichActions);
        actionConfigurations.addAll(validateActions);
        return actionConfigurations;
    }

    @Override
    public List<DeltaFiConfiguration> findByConfigType(ConfigType configType) {
        switch (configType) {
            case EGRESS_FLOW:
                return List.of(asEgressFlowConfiguration());
            case ENRICH_ACTION:
                return Objects.nonNull(enrichActions) ? new ArrayList<>(enrichActions) : Collections.emptyList();
            case FORMAT_ACTION:
                return List.of(formatAction);
            case VALIDATE_ACTION:
                return Objects.nonNull(validateActions) ? new ArrayList<>(validateActions) : Collections.emptyList();
            case EGRESS_ACTION:
                return List.of(egressAction);
            default:
                return Collections.emptyList();
        }
    }

    @Override
    public ActionConfiguration findActionConfigByName(String actionName) {
        if (actionName.equals(egressAction.getName())) {
            return egressAction;
        }

        if (actionName.equals(formatAction.getName())) {
            return formatAction;
        }

        ActionConfiguration enrichAction = enrichActions.stream()
                .filter(enrichActionConfiguration -> actionName.equals(enrichActionConfiguration.getName()))
                .findFirst().orElse(null);

        if (Objects.nonNull(enrichAction)) {
            return enrichAction;
        }

        return validateActions.stream()
                .filter(validateActionConfiguration -> actionName.equals(validateActionConfiguration.getName()))
                .findFirst().orElse(null);
    }

    public void updateActionNamesByFamily(Map<String, ActionFamily> actionFamilyMap) {
        updateActionNamesByFamily(actionFamilyMap, "enrich", enrichActionNames());
        updateActionNamesByFamily(actionFamilyMap, "format", formatAction.getName());
        updateActionNamesByFamily(actionFamilyMap, "validate", validateActionNames());
        updateActionNamesByFamily(actionFamilyMap, "egress", egressAction.getName());
    }

    List<String> enrichActionNames() {
        return enrichActions.stream()
                .map(EnrichActionConfiguration::getName)
                .collect(Collectors.toList());
    }

    public DeltaFiConfiguration asEgressFlowConfiguration() {
        EgressFlowConfiguration egressFlowConfiguration = new EgressFlowConfiguration();
        egressFlowConfiguration.setName(this.name);
        egressFlowConfiguration.setEnrichActions(enrichActions.stream().map(ActionConfiguration::getName).collect(Collectors.toList()));
        egressFlowConfiguration.setFormatAction(this.formatAction.getName());
        egressFlowConfiguration.setValidateActions(validateActionNames());
        egressFlowConfiguration.setEgressAction(this.egressAction.getName());
        egressFlowConfiguration.setIncludeIngressFlows(this.includeIngressFlows);
        egressFlowConfiguration.setExcludeIngressFlows(this.excludeIngressFlows);

        return egressFlowConfiguration;
    }

    public boolean flowMatches(String flow) {
        return includesFlow(flow) && notExcludedFlow(flow);
    }

    public boolean isValid() {
        return !FlowState.INVALID.equals(flowStatus.getState());
    }

    public boolean isRunning() {
        return FlowState.RUNNING.equals(flowStatus.getState());
    }

    private boolean includesFlow(String flow) {
        return nullOrEmpty(getIncludeIngressFlows()) || getIncludeIngressFlows().contains(flow);
    }

    private boolean notExcludedFlow(String flow) {
        return nullOrEmpty(getExcludeIngressFlows()) || !getExcludeIngressFlows().contains(flow);
    }

    private boolean nullOrEmpty(List<String> list) {
        return Objects.isNull(list) || list.isEmpty();
    }

}
