/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
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
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.types.*;
import org.deltafi.core.converters.FlowPlanConverter;
import org.deltafi.core.generated.types.*;
import org.deltafi.core.plugin.PluginUninstallCheck;
import org.deltafi.core.repo.FlowRepo;
import org.deltafi.core.snapshot.SnapshotRestoreOrder;
import org.deltafi.core.snapshot.Snapshotter;
import org.deltafi.core.snapshot.SystemSnapshot;
import org.deltafi.core.snapshot.types.FlowSnapshot;
import org.deltafi.core.types.ConfigType;
import org.deltafi.core.types.Flow;
import org.deltafi.core.types.Result;
import org.deltafi.core.validation.FlowValidator;
import org.springframework.boot.info.BuildProperties;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public abstract class FlowService<FlowPlanT extends FlowPlan, FlowT extends Flow, FlowSnapshotT extends FlowSnapshot> implements PluginUninstallCheck, Snapshotter {

    protected final FlowRepo<FlowT> flowRepo;
    protected final PluginVariableService pluginVariableService;
    private final String flowType;
    private final FlowPlanConverter<FlowPlanT, FlowT> flowPlanConverter;
    private final FlowValidator<FlowT> validator;
    private final BuildProperties buildProperties;

    protected volatile Map<String, FlowT> flowCache = Collections.emptyMap();

    protected FlowService(String flowType, FlowRepo<FlowT> flowRepo, PluginVariableService pluginVariableService, FlowPlanConverter<FlowPlanT, FlowT> flowPlanConverter, FlowValidator<FlowT> validator, BuildProperties buildProperties) {
        this.flowType = flowType;
        this.flowRepo = flowRepo;
        this.pluginVariableService = pluginVariableService;
        this.flowPlanConverter = flowPlanConverter;
        this.validator = validator;
        this.buildProperties = buildProperties;
    }

    @PostConstruct
    public void postConstruct() {
        flowRepo.updateSystemPluginFlowVersions(buildProperties.getVersion());
        refreshCache();
    }

    public synchronized void refreshCache() {
        flowCache = flowRepo.findAll().stream()
                .map(this::migrate)
                .collect(Collectors.toMap(Flow::getName, Function.identity()));
    }

    private FlowT migrate(FlowT flow) {
        if (flow.migrate()) {
            flowRepo.save(flow);
        }
        return flow;
    }

    public List<String> getNamesOfInvalidFlow() {
        return flowCache.values().stream()
                .filter(FlowT::isInvalid)
                .map(FlowT::getName).toList();
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

    public boolean enableTestMode(String flowName) {
        FlowT flow = getFlowOrThrow(flowName);

        if (flow.isTestMode()) {
            log.warn("Tried to enable test mode on {} flow {} when already in test mode", flowType, flowName);
            return false;
        }

        return updateAndRefreshTestMode(flowName, true);
    }

    public boolean disableTestMode(String flowName) {
        FlowT flow = getFlowOrThrow(flowName);

        if (!flow.isTestMode()) {
            log.warn("Tried to disable test mode on {} flow {} when not already in test mode", flowType, flowName);
            return false;
        }

        return updateAndRefreshTestMode(flowName, false);
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
                .toList();

        flowRepo.saveAll(updatedFlows);

        refreshCache();
    }

    public void upgradeFlows(PluginCoordinates sourcePlugin, List<FlowPlanT> flowPlans, Set<String> flowNames) {
        List<FlowT> flows = flowPlans.stream().map(this::buildFlow).toList();
        saveAll(flows);

        Set<String> flowsToRemove = flowRepo.findByGroupIdAndArtifactId(sourcePlugin.getGroupId(), sourcePlugin.getArtifactId()).stream()
                .map(Flow::getName)
                .filter(name -> !flowNames.contains(name))
                .collect(Collectors.toSet());

        if (!flowsToRemove.isEmpty()) {
            flowRepo.deleteAllById(flowsToRemove);
        }

        refreshCache();
    }

    private FlowT buildFlow(FlowPlanT flowPlan) {
        List<Variable> variables = pluginVariableService.getVariablesByPlugin(flowPlan.getSourcePlugin());
        return buildFlow(flowPlan, variables);
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
        return validateAndSaveFlow(getFlowOrThrow(flowName));
    }

    FlowT validateAndSaveFlow(FlowT flow) {
        List<FlowConfigError> errors = new ArrayList<>(flow.getFlowStatus()
                .getErrors().stream().filter(error -> FlowErrorType.UNRESOLVED_VARIABLE.equals(error.getErrorType()))
                .toList());

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
     * Check for flow with the given name.
     * @param flowName name of the flow to find
     * @return whether it is a flow
     */
    public boolean hasFlow(String flowName) {
        return flowCache.get(flowName) != null;
    }

    /**
     * Check for running flow with the given name.
     * @param flowName name of the flow to find
     * @return whether it is a running flow
     */
    public boolean hasRunningFlow(String flowName) {
        FlowT flow = flowCache.get(flowName);
        return flow != null && flow.isRunning();
    }

    /**
     * Find the running flow with the given name.
     * <p>
     * Throws an exception if the given flow is not running
     * @param flowName name of the flow to find
     * @return the flow with the given name
     */
    public FlowT getRunningFlowByName(String flowName) {
        FlowT flow = flowCache.get(flowName);
        if (flow == null || !flow.isRunning()) {
            throw new DgsEntityNotFoundException("Flow of type " + flowType + " named " + flowName + " is not running");
        }

        return flow;
    }

    /**
     * Find the flow with the given name.
     * <p>
     * Throws an exception if the given flow does not exist
     * @param flowName name of the flow to find
     * @return the flow with the given name
     */
    public FlowT getFlowOrThrow(String flowName) {
        return flowRepo.findById(flowName)
                .orElseThrow(() -> new IllegalArgumentException("No " + flowType + " flow exists with the name: " + flowName));
    }

    /**
     * Get all flows in the system
     *
     * @return all flows
     */
    public List<FlowT> getAll() {
        if (flowCache == null) {
            refreshCache();
        }

        return new ArrayList<>(flowCache.values());
    }

    /**
     * Get all flows in the system without caching
     * @return all flows
     */
    public List<FlowT> getAllUncached() {
        refreshCache();
        return new ArrayList<>(flowCache.values());
    }

    /**
     * Get all flows in a running state
     * @return all running flows
     */
    public List<FlowT> getRunningFlows() {
        return getAll().stream().filter(Flow::isRunning).toList();
    }

    public List<String> getFlowNamesByState(FlowState state) {
        List<FlowT> flows = state == null ? getAll() : flowRepo.findByFlowStatusState(state);

        return flows.stream().map(Flow::getName).toList();
    }

    /**
     * Get the name of all running flows
     * @return list of names of the running flows
     */
    public List<String> getRunningFlowNames() {
        return getAll().stream().filter(Flow::isRunning).map(Flow::getName).toList();
    }

    @Override
    public Result resetFromSnapshot(SystemSnapshot systemSnapshot, boolean hardReset) {
        refreshCache();

        if (hardReset) {
            getRunningFlowNames().forEach(this::stopFlow);
        }

        return resetFromSnapshot(systemSnapshot);
    }

    public Result resetFromSnapshot(SystemSnapshot systemSnapshot) {
        Result result = new Result();
        List<FlowSnapshotT> flowSnapshots = getFlowSnapshots(systemSnapshot);

        if (flowSnapshots == null || flowSnapshots.isEmpty()) {
            return result;
        }

        List<FlowT> updatedFlows = new ArrayList<>();
        for (FlowSnapshotT snapshot : flowSnapshots) {
            FlowT existing = flowRepo.findById(snapshot.getName()).orElse(null);

            if (existing == null) {
                result.getErrors().add("Flow " + snapshot.getName() + " is no longer installed");
            } else if (updateFromSnapshot(existing, snapshot, result)) {
                updatedFlows.add(existing);
            }
        }

        if (!updatedFlows.isEmpty()) {
            saveAll(updatedFlows);
            refreshCache();
        }

        result.setSuccess(result.getErrors().isEmpty());
        return result;
    }


    public abstract List<FlowSnapshotT> getFlowSnapshots(SystemSnapshot systemSnapshot);

    public boolean updateFromSnapshot(FlowT flow, FlowSnapshotT flowSnapshot, Result result) {
        boolean changed = false;
        if (flowSnapshot.isRunning()) {
            if (flow.isStopped()) {
                flow.getFlowStatus().setState(FlowState.RUNNING);
                changed = true;
            } else if (flow.isInvalid()) {
                result.getErrors().add("Flow: " + flow.getName() + " is invalid and cannot be started");
            }
        }

        if (flowSnapshot.isTestMode() && !flow.isTestMode()) {
            flow.setTestMode(true);
            changed = true;
        }

        boolean flowSpecificChanges = flowSpecificUpdateFromSnapshot(flow, flowSnapshot, result);
        return changed || flowSpecificChanges;
    }

    public boolean flowSpecificUpdateFromSnapshot(FlowT flow, FlowSnapshotT flowSnapshotT, Result result) {
        // expected annotations are handled in the AnnotationService, currently only used for max errors
        return false;
    }

    @Override
    public int getOrder() {
        return SnapshotRestoreOrder.FLOW_ORDER;
    }

    /**
     * Remove the flow with the given name if it exists
     *
     * @param flowName name of the flow to remove
     */
    public void removeByName(String flowName, PluginCoordinates systemPlugin) {
        FlowT flow = flowRepo.findById(flowName).orElse(null);
        if (flow != null) {
            if (flow.isRunning()) {
                throw new IllegalStateException("Flow " + flowName + " cannot be removed while it is running");
            } else if(!systemPlugin.equalsIgnoreVersion(flow.getSourcePlugin())) {
                throw new IllegalArgumentException("Flow " + flowName + " is not a " + systemPlugin.getArtifactId() + " flow and cannot be removed");
            }
            flowRepo.deleteById(flowName);
            refreshCache();
        }

    }

    /**
     * Remove all the FlowPlans with the given sourcePlugin
     *
     * @param pluginCoordinates sourcePlugin whose FlowPlans should be removed
     */
    public void removeBySourcePlugin(PluginCoordinates pluginCoordinates) {
        flowRepo.deleteBySourcePlugin(pluginCoordinates);
        refreshCache();
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
        FlowT flow = flowCache.get(flowName);

        if (flow == null || !flow.isRunning()) {
            return null;
        }

        return flow.findActionConfigByName(actionName);
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
     * @param configQueryInput search parameters
     * @return an immutable list of matching configurations
     */
    public List<DeltaFiConfiguration> getConfigs(ConfigQueryInput configQueryInput) {
        return Objects.nonNull(configQueryInput) ? findConfigsWithFilter(configQueryInput) : findAllConfigs();
    }

    FlowT buildFlow(FlowPlanT flowPlan, List<Variable> variables) {
        Optional<FlowT> existing = flowRepo.findById(flowPlan.getName());

        boolean flowWasRunning = existing.map(Flow::isRunning).orElse(false);
        boolean flowWasInTestMode = existing.map(Flow::isTestMode).orElse(false);

        FlowT flow = flowPlanConverter.convert(flowPlan, variables);

        flow.getFlowStatus().getErrors().addAll(validator.validate(flow));

        existing.ifPresent(sourceFlow -> copyFlowSpecificFields(sourceFlow, flow));

        if (flow.hasErrors()) {
            flow.getFlowStatus().setState(FlowState.INVALID);
        } else if (flowWasRunning) {
            flow.getFlowStatus().setState(FlowState.RUNNING);
        }

        flow.getFlowStatus().setTestMode(flowWasInTestMode);

        return flow;
    }

    /**
     * Hook to copy fields from an existing flow into the new flow
     * that is getting created from a flow plan
     * @param sourceFlow existing flow that contains fields that should be copied into the targetFlow
     * @param targetFlow flow that is being recreated and should have the fields set from the sourceFlow values
     */
    void copyFlowSpecificFields(FlowT sourceFlow, FlowT targetFlow) {

    }

    private List<DeltaFiConfiguration> findConfigsWithFilter(ConfigQueryInput configQueryInput) {
        ConfigType configType = ConfigType.valueOf(configQueryInput.getConfigType().name());
        String nameFilter = configQueryInput.getName();
        List<DeltaFiConfiguration> allByType = allOfConfigType(configType);
        return Objects.isNull(nameFilter) ? allByType :
                allByType.stream()
                        .filter(actionConfig -> configQueryInput.getName().equals(actionConfig.getName()))
                        .toList();
    }

    private List<DeltaFiConfiguration> allOfConfigType(ConfigType configType) {
        return getAll().stream()
                .map(flow -> flow.findByConfigType(configType))
                .flatMap(Collection::stream)
                .toList();
    }

    private List<DeltaFiConfiguration> findAllConfigs() {
        return getAll().stream()
                .map(Flow::allConfigurations)
                .flatMap(Collection::stream)
                .toList();
    }

    private FlowT save(FlowT flow) {
        FlowT persistedFlow = flowRepo.save(flow);
        refreshCache();
        return persistedFlow;
    }

    private void saveAll(List<FlowT> flows) {
        flowRepo.saveAll(flows);
    }

    private boolean updateAndRefresh(String flowName, FlowState flowState) {
        if (flowRepo.updateFlowState(flowName, flowState)) {
            refreshCache();
            return true;
        }

        return false;
    }

    private boolean updateAndRefreshTestMode(String flowName, boolean testMode) {
        if (flowRepo.updateFlowTestMode(flowName, testMode)) {
            refreshCache();
            return true;
        }

        return false;
    }

    String runningFlowError(List<String> runningFlows) {
        return "The plugin has created the following " + flowType + " flows which are still running: " + String.join(", ", runningFlows);
    }
}
