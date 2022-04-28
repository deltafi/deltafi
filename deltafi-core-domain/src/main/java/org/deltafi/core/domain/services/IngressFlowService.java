package org.deltafi.core.domain.services;

import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.core.domain.api.types.PluginCoordinates;
import org.deltafi.core.domain.converters.IngressFlowPlanConverter;
import org.deltafi.core.domain.generated.types.FlowState;
import org.deltafi.core.domain.generated.types.Variable;
import org.deltafi.core.domain.repo.IngressFlowRepo;
import org.deltafi.core.domain.types.IngressFlow;
import org.deltafi.core.domain.types.IngressFlowPlan;
import org.deltafi.core.domain.validation.IngressFlowValidator;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class IngressFlowService extends FlowControllerService<IngressFlow> {

    private static final IngressFlowPlanConverter INGRESS_FLOW_PLAN_CONVERTER = new IngressFlowPlanConverter();

    private final IngressFlowRepo ingressFlowRepo;
    private final PluginVariableService pluginVariableService;
    private final IngressFlowValidator ingressFlowValidator;

    /**
     * For each of the given IngressFlowPlans, rebuild the flow from the plan and latest variables
     * @param ingressFlowPlans list of flow plans that should need flows rebuilt
     * @param sourcePlugin PluginCoordinates used to find the variables
     */
    void rebuildFlows(List<IngressFlowPlan> ingressFlowPlans, PluginCoordinates sourcePlugin) {
        List<Variable> variables = pluginVariableService.getVariablesByPlugin(sourcePlugin);
        List<IngressFlow> updatedFlows = ingressFlowPlans.stream()
                .map(ingressFlowPlan -> buildFlow(ingressFlowPlan, variables))
                .collect(Collectors.toList());

        ingressFlowRepo.saveAll(updatedFlows);
    }

    /**
     * Get the variables associated with this FlowPlan, and create
     * an IngressFlow from the plan and variables. Persist the flow.
     *
     * @param ingressFlowPlan flow plan used to create a new ingress flow
     * @return IngressFlow that was created from the plan
     */
    IngressFlow buildAndSaveFlow(IngressFlowPlan ingressFlowPlan) {
        List<Variable> variables = pluginVariableService.getVariablesByPlugin(ingressFlowPlan.getSourcePlugin());
        return saveFlowAndRefreshCache(buildFlow(ingressFlowPlan, variables));
    }

    IngressFlow buildFlow(IngressFlowPlan ingressFlowPlan, List<Variable> variables) {
        boolean flowWasRunning = ingressFlowRepo.findById(ingressFlowPlan.getName())
                .map(IngressFlow::isRunning)
                .orElse(false);

        IngressFlow ingressFlow = validateFlow(INGRESS_FLOW_PLAN_CONVERTER.toIngressFlow(ingressFlowPlan, variables));

        // if the updated flow is valid and replacing a flow that was previously running, mark the flow as running
        if (ingressFlow.isValid() && flowWasRunning) {
            ingressFlow.getFlowStatus().setState(FlowState.RUNNING);
        }

        return ingressFlow;
    }

    public IngressFlow validateAndSaveFlow(String flowName) {
        return validateAndSaveFlow(getFlowOrThrow(flowName));
    }

    IngressFlow validateFlow(IngressFlow ingressFlow) {
        ingressFlowValidator.validate(ingressFlow);
        return ingressFlow;
    }

    IngressFlow validateAndSaveFlow(IngressFlow ingressFlow) {
        validateFlow(ingressFlow);
        return saveFlowAndRefreshCache(ingressFlow);
    }

    IngressFlow saveFlowAndRefreshCache(IngressFlow egressFlow) {
        IngressFlow persistedFlow = ingressFlowRepo.save(egressFlow);
        refreshCache();
        return persistedFlow;
    }

    @Override
    List<IngressFlow> getRunningFlows() {
        return ingressFlowRepo.findRunning();
    }

    @Override
    public List<IngressFlow> getAll() {
        return ingressFlowRepo.findAll();
    }

    @Override
    boolean updateFlowState(String flowName, FlowState flowState) {
        return ingressFlowRepo.updateFlowState(flowName, flowState);
    }

    @Override
    public IngressFlow getFlowOrThrow(String flowName) {
        return ingressFlowRepo.findById(flowName)
                .orElseThrow(() -> new DgsEntityNotFoundException("No ingress flow exists with the name: " + flowName));
    }

    public boolean removeByName(String flowName) {
        if (ingressFlowRepo.existsById(flowName)) {
            ingressFlowRepo.deleteById(flowName);
            return true;
        }

        return false;
    }

    /**
     * Remove all the IngressFlowPlans with the given sourcePlugin
     * @param pluginCoordinates sourcePlugin whose IngressFlowPlans should be removed
     * @return number of plan that were removed
     */
    public int removeBySourcePlugin(PluginCoordinates pluginCoordinates) {
        return ingressFlowRepo.deleteBySourcePlugin(pluginCoordinates);
    }

    public List<String> findRunningFromPlugin(PluginCoordinates pluginCoordinates) {
        return ingressFlowRepo.findRunningBySourcePlugin(pluginCoordinates);
    }

    @Override
    String flowType() {
        return "ingress";
    }

}
