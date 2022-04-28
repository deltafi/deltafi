package org.deltafi.core.domain.services;

import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException;
import lombok.AllArgsConstructor;
import org.deltafi.core.domain.api.types.PluginCoordinates;
import org.deltafi.core.domain.configuration.ActionConfiguration;
import org.deltafi.core.domain.converters.EgressFlowPlanConverter;
import org.deltafi.core.domain.exceptions.DeltafiConfigurationException;
import org.deltafi.core.domain.generated.types.FlowState;
import org.deltafi.core.domain.generated.types.Variable;
import org.deltafi.core.domain.repo.EgressFlowRepo;
import org.deltafi.core.domain.types.EgressFlow;
import org.deltafi.core.domain.types.EgressFlowPlan;
import org.deltafi.core.domain.validation.EgressFlowValidator;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class EgressFlowService extends FlowControllerService<EgressFlow> {

    private static final EgressFlowPlanConverter EGRESS_FLOW_PLAN_CONVERTER = new EgressFlowPlanConverter();
    private static final char FLOW_DELIMITER = '.';

    private final EgressFlowRepo egressFlowRepo;
    private final EgressFlowValidator egressFlowValidator;
    private final PluginVariableService pluginVariableService;

    /**
     * For each of the given EgressFlowPlans, rebuild the flow from the plan and latest variables
     * @param egressFlowPlans list of flow plans that should need flows rebuilt
     * @param sourcePlugin PluginCoordinates used to find the variables
     */
    void rebuildFlows(List<EgressFlowPlan> egressFlowPlans, PluginCoordinates sourcePlugin) {
        List<Variable> variables = pluginVariableService.getVariablesByPlugin(sourcePlugin);
        List<EgressFlow> updatedFlows = egressFlowPlans.stream()
                .map(egressFlowPlan -> buildFlow(egressFlowPlan, variables))
                .collect(Collectors.toList());

        egressFlowRepo.saveAll(updatedFlows);
    }

    EgressFlow buildAndSaveFlow(EgressFlowPlan egressFlowPlan) {
        List<Variable> variables = pluginVariableService.getVariablesByPlugin(egressFlowPlan.getSourcePlugin());
        return saveFlowAndRefreshCache(buildFlow(egressFlowPlan, variables));
    }

    EgressFlow buildFlow(EgressFlowPlan egressFlowPlan, List<Variable> variables) {
        boolean flowWasRunning = egressFlowRepo.findById(egressFlowPlan.getName())
                .map(EgressFlow::isRunning)
                .orElse(false);

        EgressFlow egressFlow = validateFlow(EGRESS_FLOW_PLAN_CONVERTER.toEgressFlow(egressFlowPlan, variables));

        // if the updated flow is valid and replacing a flow that was previously running, mark the flow as running
        if (egressFlow.isValid() && flowWasRunning) {
            egressFlow.getFlowStatus().setState(FlowState.RUNNING);
        }

        return egressFlow;
    }

    public EgressFlow validateAndSaveFlow(String flowName) {
        return validateAndSaveFlow(getFlowOrThrow(flowName));
    }

    EgressFlow validateFlow(EgressFlow egressFlow) {
        egressFlowValidator.validate(egressFlow);
        return egressFlow;
    }

    EgressFlow validateAndSaveFlow(EgressFlow egressFlow) {
        validateFlow(egressFlow);
        return saveFlowAndRefreshCache(egressFlow);
    }

    EgressFlow saveFlowAndRefreshCache(EgressFlow egressFlow) {
        EgressFlow persistedFlow = egressFlowRepo.save(egressFlow);
        refreshCache();
        return persistedFlow;
    }

    public List<EgressFlow> getMatchingFlows(String ingressFlow) {
        List<EgressFlow> flows = findMatchingFlows(ingressFlow);

        if (flows.isEmpty()) {
            refreshCache();
            flows = findMatchingFlows(ingressFlow);
        }

        return flows;
    }

    List<EgressFlow> findMatchingFlows(String ingressFlow) {
        return flowCache.values().stream()
                .filter(egressFlow -> egressFlow.flowMatches(ingressFlow))
                .collect(Collectors.toList());
    }

    public ActionConfiguration findActionConfig(String actionName) {
        return findActionConfig(getFlowName(actionName), actionName);
    }

    public EgressFlow withFormatActionNamed(String formatActionName) {
        EgressFlow egressFlow = getRunningFlowByName(getFlowName(formatActionName));

        if (!formatActionName.equals(egressFlow.getFormatAction().getName())) {
            throw new DeltafiConfigurationException("Egress flow " + egressFlow + " no longer contains a format action with the name " + formatActionName);
        }

        return egressFlow;
    }

    @Override
    public List<EgressFlow> getRunningFlows() {
        return egressFlowRepo.findRunning();
    }

    @Override
    public List<EgressFlow> getAll() {
        return egressFlowRepo.findAll();
    }

    @Override
    public EgressFlow getFlowOrThrow(String flowName) {
        return egressFlowRepo.findById(flowName)
                .orElseThrow(() -> new DgsEntityNotFoundException("No egress flow exists with the name: " + flowName));
    }

    @Override
    boolean updateFlowState(String flowName, FlowState flowState) {
        return egressFlowRepo.updateFlowState(flowName, flowState);
    }

    /**
     * Remove all the EgressFlows with the given sourcePlugin
     * @param pluginCoordinates sourcePlugin whose EgressFlows should be removed
     * @return number of flows that were removed
     */
    public int removeBySourcePlugin(PluginCoordinates pluginCoordinates) {
        return egressFlowRepo.deleteBySourcePlugin(pluginCoordinates);
    }

    public boolean removeByName(String flowName) {
        if (egressFlowRepo.existsById(flowName)) {
            egressFlowRepo.deleteById(flowName);
            return true;
        }

        return false;
    }

    public List<String> findRunningFromPlugin(PluginCoordinates pluginCoordinates) {
        return egressFlowRepo.findRunningBySourcePlugin(pluginCoordinates);
    }

    @Override
    String flowType() {
        return "egress";
    }

    private String getFlowName(String actionName) {
        int delimiterIdx = actionName.indexOf(FLOW_DELIMITER);
        if (delimiterIdx == -1 || delimiterIdx == actionName.length() - 1) {
            throw new IllegalArgumentException("Unable to get the flow name from the actionName: " + actionName);
        }

        return actionName.substring(0, delimiterIdx);

    }
}
