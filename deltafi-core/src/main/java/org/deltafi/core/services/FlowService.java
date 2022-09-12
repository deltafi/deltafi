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
package org.deltafi.core.services;

import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.common.types.Variable;
import org.deltafi.core.configuration.ActionConfiguration;
import org.deltafi.core.configuration.DeltaFiConfiguration;
import org.deltafi.core.converters.FlowPlanConverter;
import org.deltafi.core.generated.types.*;
import org.deltafi.core.plugin.Plugin;
import org.deltafi.core.plugin.PluginUninstallCheck;
import org.deltafi.core.repo.FlowRepo;
import org.deltafi.core.snapshot.SnapshotRestoreOrder;
import org.deltafi.core.snapshot.Snapshotter;
import org.deltafi.core.snapshot.SystemSnapshot;
import org.deltafi.core.types.ConfigType;
import org.deltafi.core.types.Flow;
import org.deltafi.core.types.FlowPlan;
import org.deltafi.core.types.Result;
import org.deltafi.core.validation.FlowValidator;

import javax.annotation.PostConstruct;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public abstract class FlowService<FlowPlanT extends FlowPlan, FlowT extends Flow> implements PluginUninstallCheck, Snapshotter {

    private static final char FLOW_DELIMITER = '.';

    protected final FlowRepo<FlowT> flowRepo;
    protected final PluginVariableService pluginVariableService;
    private final String flowType;
    private final FlowPlanConverter<FlowPlanT, FlowT> flowPlanConverter;
    private final FlowValidator<FlowT> validator;

    protected Map<String, FlowT> flowCache;

    protected FlowService(String flowType, FlowRepo<FlowT> flowRepo, PluginVariableService pluginVariableService, FlowPlanConverter<FlowPlanT, FlowT> flowPlanConverter, FlowValidator<FlowT> validator) {
        this.flowType = flowType;
        this.flowRepo = flowRepo;
        this.pluginVariableService = pluginVariableService;
        this.flowPlanConverter = flowPlanConverter;
        this.validator = validator;
    }

    @PostConstruct
    public void refreshCache() {
        flowCache = flowRepo.findRunning().stream()
                .collect(Collectors.toMap(Flow::getName, Function.identity()));
    }

    /**
     * Find the given flow and move it to a running state if it is currently stopped.
     * @param flowName name of the flow that should be started
     * @return true if the flow was successfully started
     */
    public boolean startFlow(String flowName) {
        FlowT flow = getFlowOrThrow(flowName);

        FlowStatus flowStatus = flow.getFlowStatus();

        if (FlowState.INVALID.equals(flowStatus.getState())) {
            log.warn("Tried to start " + flowType+ " flow: " + flowName + " when it was in an invalid state");
            throw new IllegalStateException("Flow: " + flowName + " cannot be started until configuration errors are resolved");
        }

        if (FlowState.RUNNING.equals(flowStatus.getState())) {
            log.warn("Tried to start " + flowType + " flow: " + flowName + " when it was already running");
            return false;
        }

        return updateAndRefresh(flowName, FlowState.RUNNING);
    }

    /**
     * Find the given flow and move it to a stopped state if it is currently running.
     * @param flowName name of the flow that should be stopped
     * @return true if the flow was successfully stopped
     */
    public boolean stopFlow(String flowName) {
        FlowT flow = getFlowOrThrow(flowName);

        if (!flow.isRunning()) {
            log.warn("Tried to stop {} flow {} which was not running", flowType, flowName);
            return false;
        }

        return updateAndRefresh(flowName, FlowState.STOPPED);
    }

    /**
     * For each of the given flow plans, rebuild the flow from the plan and latest variables
     * @param flowPlans list of flow plans that need flows rebuilt
     * @param sourcePlugin PluginCoordinates used to find the variables
     */
    public void rebuildFlows(List<FlowPlanT> flowPlans, PluginCoordinates sourcePlugin) {
        List<Variable> variables = pluginVariableService.getVariablesByPlugin(sourcePlugin);
        List<FlowT> updatedFlows = flowPlans.stream()
                .map(flowPlan -> buildFlow(flowPlan, variables))
                .collect(Collectors.toList());

        flowRepo.saveAll(updatedFlows);

        refreshCache();
    }

    /**
     * Get the variables associated with this flow plan, and create
     * a flow from the plan and variables.
     *
     * @param flowPlan used to create a new flow
     * @return flow that was created from the plan
     */
    public FlowT buildAndSaveFlow(FlowPlanT flowPlan) {
        List<Variable> variables = pluginVariableService.getVariablesByPlugin(flowPlan.getSourcePlugin());
        return save(buildFlow(flowPlan, variables));
    }

    /**
     * Find the given flow by name and rerun validation
     * @param flowName name of the flow to validate
     * @return updated flow after validation is run
     */
    public FlowT validateAndSaveFlow(String flowName) {
        FlowT flow = getFlowOrThrow(flowName);

        List<FlowConfigError> errors = flow.getFlowStatus()
                .getErrors().stream().filter(error -> FlowErrorType.UNRESOLVED_VARIABLE.equals(error.getErrorType()))
                .collect(Collectors.toList());

        errors.addAll(validator.validate(flow));

        if (!errors.isEmpty()) {
            flow.getFlowStatus().setState(FlowState.INVALID);
        } else if(FlowState.INVALID.equals(flow.getFlowStatus().getState())) {
            flow.getFlowStatus().setState(FlowState.STOPPED);
        }

        flow.getFlowStatus().setErrors(errors);

        return save(flow);
    }

    /**
     * Find the running flow with the given name.
     *
     * Throws an exception if the given flow is not running
     * @param flowName name of the flow to find
     * @return the flow with the given name
     */
    public FlowT getRunningFlowByName(String flowName) {
        if (!flowCache.containsKey(flowName)) {
            refreshCache();
        }

        if (!flowCache.containsKey(flowName)) {
            throw new DgsEntityNotFoundException("Flow of type " + flowType + " named " + flowName + " is not running");
        }

        return flowCache.get(flowName);
    }

    /**
     * Find the flow with the given name.
     *
     * Throws an exception if the given flow does not exist
     * @param flowName name of the flow to find
     * @return the flow with the given name
     */
    public FlowT getFlowOrThrow(String flowName) {
        return flowRepo.findById(flowName)
                .orElseThrow(() -> new DgsEntityNotFoundException("No " + flowType + " flow exists with the name: " + flowName));
    }

    /**
     * Get all flows in the system
     *
     * @return all flows
     */
    public List<FlowT> getAll() {
        return flowRepo.findAll();
    }

    /**
     * Get all flows in a running state
     * @return all running flows
     */
    public List<FlowT> getRunningFlows() {
        if (flowCache.isEmpty()) {
            refreshCache();
        }

        return flowCache.values().stream().collect(Collectors.toList());
    }

    /**
     * Get the name of all running flows
     * @return list of names of the running flows
     */
    public List<String> getRunningFlowNames() {
        return getAll().stream().filter(Flow::isRunning).map(Flow::getName).collect(Collectors.toList());
    }

    abstract List<String> getRunningFromSnapshot(SystemSnapshot systemSnapshot);

    @Override
    public Result resetFromSnapshot(SystemSnapshot systemSnapshot, boolean hardReset) {
        Result result = new Result();
        List<String> runningFlowsInSnapshot = getRunningFromSnapshot(systemSnapshot);

        if (hardReset) {
            getRunningFlowNames().forEach(this::stopFlow);
        }

        for (String flow : runningFlowsInSnapshot) {
            flowRepo.findById(flow).ifPresentOrElse(existingFlow -> resetFlowState(existingFlow, result),
                    () -> result.getErrors().add("Flow: " + flow + " is no longer installed"));
        }

        result.setSuccess(result.getErrors().isEmpty());
        return result;
    }

    @Override
    public int getOrder() {
        return SnapshotRestoreOrder.FLOW_ORDER;
    }

    void resetFlowState(FlowT flow, Result result) {
        if (flow.isStopped()) {
            startFlow(flow.getName());
        } else if (flow.isInvalid()) {
            result.getErrors().add("Flow: " + flow.getName() + " is invalid and cannot be started");
        }
    }

    /**
     * Remove the flow with the given name if it exists
     * @param flowName name of the flow to remove
     * @return true if the flow was removed
     */
    public boolean removeByName(String flowName) {
        if (flowRepo.existsById(flowName)) {
            flowRepo.deleteById(flowName);
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
        return flowRepo.deleteBySourcePlugin(pluginCoordinates);
    }

    /**
     * Determine if there are any running flows created from this plugin
     * @param plugin that will be removed if there are no blockers
     * @return null if there are no running flows, otherwise an error message with list
     * of running flows
     */
    @Override
    public String uninstallBlockers(Plugin plugin) {
        List<String> runningFlows = flowRepo.findRunningBySourcePlugin(plugin.getPluginCoordinates());
        return runningFlows.isEmpty() ? null : runningFlowError(runningFlows);
    }

    public ActionConfiguration findActionConfig(String flowName, String actionName) {
        ActionConfiguration maybeFound = doFindActionConfig(flowName, actionName);

        if (Objects.isNull(maybeFound)) {
            refreshCache();
            maybeFound = doFindActionConfig(flowName, actionName);
        }

        return maybeFound;
    }

    public ActionConfiguration findActionConfig(String actionName) {
        return findActionConfig(getFlowName(actionName), actionName);
    }

    /**
     * Find all flows grouped by their source plugin
     * @return map of plugin coordinates to the list of associated flows
     */
    public Map<PluginCoordinates, List<FlowT>> getFlowsGroupedByPlugin() {
        return getAll().stream()
                .collect(Collectors.groupingBy(FlowT::getSourcePlugin));
    }

    /**
     * Search the flows for a configuration configurations
     * @param actionQueryInput search parameters
     * @return list of matching configurations
     */
    public List<DeltaFiConfiguration> getConfigs(ConfigQueryInput actionQueryInput) {
        return Objects.nonNull(actionQueryInput) ? findConfigsWithFilter(actionQueryInput) : findAllConfigs();
    }

    ActionConfiguration doFindActionConfig(String flowName, String actionName) {
        FlowT flow = flowCache.get(flowName);

        if (Objects.isNull(flow)) {
            return null;
        }

        return flow.findActionConfigByName(actionName);
    }

    FlowT buildFlow(FlowPlanT flowPlan, List<Variable> variables) {
        boolean flowWasRunning = flowRepo.findById(flowPlan.getName())
                .map(Flow::isRunning)
                .orElse(false);

        FlowT flow = flowPlanConverter.convert(flowPlan, variables);

        flow.getFlowStatus().getErrors().addAll(validator.validate(flow));

        if (flow.hasErrors()) {
            flow.getFlowStatus().setState(FlowState.INVALID);
        } else if (flowWasRunning) {
            flow.getFlowStatus().setState(FlowState.RUNNING);
        }

        return flow;
    }

    String getFlowName(String actionName) {
        int delimiterIdx = actionName.indexOf(FLOW_DELIMITER);
        if (delimiterIdx == -1 || delimiterIdx == actionName.length() - 1) {
            throw new IllegalArgumentException("Unable to get the flow name from the actionName: " + actionName);
        }

        return actionName.substring(0, delimiterIdx);
    }

    private List<DeltaFiConfiguration> findConfigsWithFilter(ConfigQueryInput actionQueryInput) {
        ConfigType configType = ConfigType.valueOf(actionQueryInput.getConfigType().name());
        String nameFilter = actionQueryInput.getName();
        List<DeltaFiConfiguration> allByType = allOfConfigType(configType);
        return Objects.isNull(nameFilter) ? allByType :
                allByType.stream().filter(actionConfig -> actionQueryInput.getName().equals(actionConfig.getName())).collect(Collectors.toList());
    }

    private List<DeltaFiConfiguration> allOfConfigType(ConfigType configType) {
        return getAll().stream()
                .map(flow -> flow.findByConfigType(configType))
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    private List<DeltaFiConfiguration> findAllConfigs() {
        return getAll().stream()
                .map(Flow::allConfigurations)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    private FlowT save(FlowT flow) {
        FlowT persistedFlow = flowRepo.save(flow);
        refreshCache();
        return persistedFlow;
    }

    private boolean updateAndRefresh(String flowName, FlowState flowState) {
        if (flowRepo.updateFlowState(flowName, flowState)) {
            refreshCache();
            return true;
        }

        return false;
    }

    String runningFlowError(List<String> runningFlows) {
        return "The plugin has created the following " + flowType + " flows which are still running: " + String.join(", ", runningFlows);
    }

}
