/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.types.*;
import org.deltafi.core.converters.FlowPlanConverter;
import org.deltafi.core.exceptions.MissingFlowException;
import org.deltafi.core.generated.types.*;
import org.deltafi.core.types.*;
import org.deltafi.core.repo.FlowRepo;
import org.deltafi.core.types.snapshot.SnapshotRestoreOrder;
import org.deltafi.core.types.snapshot.SystemSnapshot;
import org.deltafi.core.types.snapshot.FlowSnapshot;
import org.deltafi.core.validation.FlowValidator;
import org.springframework.boot.info.BuildProperties;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public abstract class FlowService<FlowPlanT extends FlowPlan, FlowT extends Flow, FlowSnapshotT extends FlowSnapshot, FlowRepoT extends FlowRepo> implements PluginCleaner, PluginUninstallCheck, Snapshotter {

    protected final FlowRepoT flowRepo;
    protected final PluginVariableService pluginVariableService;
    private final FlowType flowType;
    private final FlowPlanConverter<FlowPlanT, FlowT> flowPlanConverter;
    private final FlowValidator validator;
    private final BuildProperties buildProperties;
    protected final FlowCacheService flowCacheService;
    private final Class<FlowT> flowClass;
    private final Class<FlowPlanT> flowPlanClass;

    protected FlowService(FlowType flowType, FlowRepoT flowRepo, PluginVariableService pluginVariableService, FlowPlanConverter<FlowPlanT, FlowT> flowPlanConverter, FlowValidator validator, BuildProperties buildProperties, FlowCacheService flowCacheService, Class<FlowT> flowClass, Class<FlowPlanT> flowPlanClass) {
        this.flowType = flowType;
        this.flowRepo = flowRepo;
        this.pluginVariableService = pluginVariableService;
        this.flowPlanConverter = flowPlanConverter;
        this.validator = validator;
        this.buildProperties = buildProperties;
        this.flowCacheService = flowCacheService;
        this.flowClass = flowClass;
        this.flowPlanClass = flowPlanClass;
    }

    @PostConstruct
    public void postConstruct() {
        flowRepo.updateSystemPluginFlowVersions(buildProperties.getVersion(), this.flowType);
        refreshCache();
    }

    public List<String> getNamesOfInvalidFlows() {
        return flowCacheService.getNamesOfInvalidFlows(flowType);
    }

    /**
     * Find the given dataSource and move it to a running state if it is currently stopped.
     * @param flowName name of the dataSource that should be started
     * @return true if the dataSource was successfully started
     */
    public boolean startFlow(String flowName) {
        FlowT flow = getFlowOrThrow(flowName);

        FlowStatus flowStatus = flow.getFlowStatus();

        if (FlowState.INVALID.equals(flowStatus.getState())) {
            log.warn("Tried to start {} dataSource: {} when it was in an invalid state", flowType, flowName);
            throw new IllegalStateException("Flow: " + flowName + " cannot be started until configuration errors are resolved");
        }

        if (FlowState.RUNNING.equals(flowStatus.getState())) {
            log.warn("Tried to start {} dataSource: {} when it was already running", flowType, flowName);
            return false;
        }

        return updateAndRefresh(flowName, FlowState.RUNNING);
    }

    /**
     * Find the given dataSource and move it to a stopped state if it is currently running.
     * @param flowName name of the dataSource that should be stopped
     * @return true if the dataSource was successfully stopped
     */
    public boolean stopFlow(String flowName) {
        FlowT flow = getFlowOrThrow(flowName);

        if (!flow.isRunning()) {
            log.warn("Tried to stop {} dataSource {} which was not running", flowType, flowName);
            return false;
        }

        return updateAndRefresh(flowName, FlowState.STOPPED);
    }

    public boolean enableTestMode(String flowName) {
        FlowT flow = getFlowOrThrow(flowName);

        if (flow.isTestMode()) {
            log.warn("Tried to enable test mode on {} dataSource {} when already in test mode", flowType, flowName);
            return false;
        }

        return updateAndRefreshTestMode(flowName, true);
    }

    public boolean disableTestMode(String flowName) {
        FlowT flow = getFlowOrThrow(flowName);

        if (!flow.isTestMode()) {
            log.warn("Tried to disable test mode on {} dataSource {} when not already in test mode", flowType, flowName);
            return false;
        }

        return updateAndRefreshTestMode(flowName, false);
    }

    public void onRefreshCache() {}

    protected void refreshCache() {
        flowCacheService.refreshCache();
        onRefreshCache();
    }

    /**
     * For each of the given dataSource plans, rebuild the dataSource from the plan and latest variables
     * @param flowPlans list of dataSource plans that need flows rebuilt
     * @param sourcePlugin PluginCoordinates used to find the variables
     */
    public void rebuildFlows(List<FlowPlan> flowPlans, PluginCoordinates sourcePlugin) {
        List<Variable> variables = pluginVariableService.getVariablesByPlugin(sourcePlugin);
        List<FlowT> updatedFlows = flowPlans.stream()
                .map(flowPlan -> buildFlow(flowPlan, variables))
                .toList();

        updatedFlows.forEach(f -> flowRepo.deleteByNameAndType(f.getName(), f.getType()));
        flowRepo.saveAll(updatedFlows);

        refreshCache();
    }

    public void upgradeFlows(PluginCoordinates sourcePlugin, List<FlowPlanT> flowPlans) {
        List<Flow> existingFlows = flowRepo.findBySourcePluginGroupIdAndSourcePluginArtifactIdAndType(
                sourcePlugin.getGroupId(), sourcePlugin.getArtifactId(), flowType);
        List<String> existingFlowNames = existingFlows.stream().map(Flow::getName).toList();
        List<FlowT> flows = flowPlans.stream().map(this::buildFlow).toList();
        List<String> incomingFlowNames = flows.stream().map(Flow::getName).toList();
        List<FlowT> newFlows = flows.stream().filter(f -> !existingFlowNames.contains(f.getName())).toList();
        saveAll(newFlows);

        List<Flow> deleteFlows = existingFlows.stream().filter(e -> !incomingFlowNames.contains(e.getName())).toList();
        flowRepo.deleteAll(deleteFlows);

        List<FlowT> incomingExistingFlows = flows.stream().filter(f -> existingFlowNames.contains(f.getName())).toList();
        for (Flow incomingExistingFlow : incomingExistingFlows) {
            Flow existingFlow = existingFlows.stream().filter(f -> f.getName().equals(incomingExistingFlow.getName())).findFirst().orElse(null);
            if (existingFlow != null) {
                incomingExistingFlow.setId(existingFlow.getId());
                incomingExistingFlow.copyFlowState(existingFlow);
            }
            flowRepo.save(incomingExistingFlow);
        }

        refreshCache();
    }

    private FlowT buildFlow(FlowPlanT flowPlan) {
        List<Variable> variables = pluginVariableService.getVariablesByPlugin(flowPlan.getSourcePlugin());
        return buildFlow(flowPlan, variables);
    }

    /**
     * Get the variables associated with this dataSource plan, and create
     * a dataSource from the plan and variables.
     *
     * @param flowPlan used to create a new dataSource
     * @return dataSource that was created from the plan
     */
    public FlowT buildAndSaveFlow(FlowPlanT flowPlan) {
        List<Variable> variables = pluginVariableService.getVariablesByPlugin(flowPlan.getSourcePlugin());
        return save(buildFlow(flowPlan, variables));
    }

    /**
     * Get all the flows and rerun validation
     */
    public void validateAllFlows() {
        getAll().forEach(this::validateAndSaveFlow);
    }

    /**
     * Find the given dataSource by name and rerun validation
     * @param flowName name of the dataSource to validate
     * @return updated dataSource after validation is run
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
     * Check for dataSource with the given name.
     * @param flowName name of the dataSource to find
     * @return whether it is a dataSource
     */
    public boolean hasFlow(String flowName) {
        return flowCacheService.getFlow(flowType, flowName) != null;
    }

    /**
     * Check for running dataSource with the given name.
     * @param flowName name of the dataSource to find
     * @return whether it is a running dataSource
     */
    public boolean hasRunningFlow(String flowName) {
        return flowCacheService.getRunningFlow(flowType, flowName) != null;
    }

    /**
     * Find the running dataSource with the given name.
     * <p>
     * Throws an exception if the given dataSource is not running
     * @param flowName name of the dataSource to find
     * @return the dataSource with the given name
     */
    public FlowT getRunningFlowByName(String flowName) {
        Flow flow = flowCacheService.getFlow(flowType, flowName);
        if (flow == null) {
            throw new MissingFlowException("Flow of type " + flowType + " named " + flowName + " is not installed");
        } else if (!flow.isRunning()){
            throw new MissingFlowException("Flow of type " + flowType + " named " + flowName + " is not running");
        }

        return flowClass.cast(flow);
    }

    /**
     * Find the dataSource with the given name.
     * <p>
     * Throws an exception if the given dataSource does not exist
     * @param flowName name of the dataSource to find
     * @return the dataSource with the given name
     */
    public FlowT getFlowOrThrow(String flowName) {
        return flowClass.cast(flowCacheService.getFlowOrThrow(flowType, flowName));
    }

    /**
     * Get all flows in the system
     *
     * @return all flows
     */
    public List<FlowT> getAll() {
        return new ArrayList<>(flowCacheService.flowsOfType(flowType).stream().map(flowClass::cast).toList());
    }

    public List<FlowT> getAllInvalidFlows() {
        return new ArrayList<>(flowCacheService.flowsOfType(flowType).stream().filter(Flow::isInvalid).map(flowClass::cast).toList());
    }

    /**
     * Get all flows in a running state
     * @return all running flows
     */
    public List<FlowT> getRunningFlows() {
        return getAll().stream().filter(Flow::isRunning).toList();
    }

    public List<String> getFlowNamesByState(FlowState state) {
        List<Flow> flows = state == null ? getAll().stream().map(f -> (Flow) f).toList() :
                flowRepo.findByFlowStatusStateAndType(state, flowType);

        return flows.stream().map(Flow::getName).toList();
    }

    @Override
    public Result resetFromSnapshot(SystemSnapshot systemSnapshot, boolean hardReset) {
        refreshCache();

        List<FlowT> flows = getAll();
        Map<String, FlowT> updatedFlows = new HashMap<>();
        Map<String, FlowT> allFlows = new HashMap<>();

        // build map of the dataSource name to flows. Reset the state fields on a hard reset
        for (FlowT flow : flows) {
            if (hardReset) {
                if (flow.isRunning() || flow.isTestMode()) {
                    flow.getFlowStatus().setState(FlowState.STOPPED);
                    flow.getFlowStatus().setTestMode(false);
                    updatedFlows.put(flow.getName(), flow);
                }

                if (flow instanceof DataSource ds && ds.getMaxErrors() != -1) {
                    ds.setMaxErrors(-1);
                    updatedFlows.put(flow.getName(), flow);
                }
            }

            allFlows.put(flow.getName(), flow);
        }

        return resetFromSnapshot(systemSnapshot, allFlows, updatedFlows);
    }

    private Result resetFromSnapshot(SystemSnapshot systemSnapshot, Map<String, FlowT> allFlows, Map<String, FlowT> updatedFlows) {
        Result result = new Result();
        List<FlowSnapshotT> flowSnapshots = getFlowSnapshots(systemSnapshot);

        if (flowSnapshots == null || flowSnapshots.isEmpty()) {
            return result;
        }

        for (FlowSnapshotT snapshot : flowSnapshots) {
            FlowT existing = allFlows.get(snapshot.getName());

            if (existing == null) {
                result.getErrors().add("Flow " + snapshot.getName() + " is no longer installed");
            } else if (updateFromSnapshot(existing, snapshot, result)) {
                updatedFlows.put(existing.getName(), existing);
            }
        }

        if (!updatedFlows.isEmpty()) {
            saveAll(updatedFlows.values());
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
     * Remove the dataSource with the given name if it exists
     *
     * @param flowName name of the dataSource to remove
     */
    public void removeByName(String flowName, PluginCoordinates systemPlugin) {
        FlowT flow = flowRepo.findByNameAndType(flowName, flowType, flowClass).orElse(null);
        if (flow != null) {
            if (flow.isRunning()) {
                throw new IllegalStateException("Flow " + flowName + " cannot be removed while it is running");
            } else if (!systemPlugin.equalsIgnoreVersion(flow.getSourcePlugin())) {
                throw new IllegalArgumentException("Flow " + flowName + " is not a " + systemPlugin.getArtifactId() + " flow and cannot be removed");
            }
            flowRepo.deleteById(flow.getId());
            refreshCache();
        }
    }

    /**
     * Remove all the FlowPlans with the given sourcePlugin
     *
     * @param pluginCoordinates sourcePlugin whose FlowPlans should be removed
     */
    public void removeBySourcePlugin(PluginCoordinates pluginCoordinates) {
        flowRepo.deleteBySourcePluginAndType(pluginCoordinates, flowType);
        refreshCache();
    }

    /**
     * Determine if there are any running flows created from this plugin
     * @param plugin that will be removed if there are no blockers
     * @return null if there are no running flows, otherwise an error message with list
     * of running flows
     */
    @Override
    public String uninstallBlockers(PluginEntity plugin) {
        List<String> runningFlows = flowRepo.findRunningBySourcePlugin(plugin.getPluginCoordinates().getGroupId(), plugin.getPluginCoordinates().getArtifactId(), plugin.getPluginCoordinates().getVersion(), this.flowType);
        return runningFlows.isEmpty() ? null : runningFlowError(runningFlows);
    }

    public ActionConfiguration findActionConfig(String flowName, String actionName) {
        Flow flow = flowCacheService.getFlow(flowType, flowName);

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

    FlowT buildFlow(FlowPlan flowPlan, List<Variable> variables) {
        Optional<FlowT> existing = flowRepo.findByNameAndType(flowPlan.getName(), flowType, flowClass);
        FlowPlanT typedFlowPlan = flowPlanClass.cast(flowPlan);
        FlowT flow = flowPlanConverter.convert(typedFlowPlan, variables);

        flow.getFlowStatus().getErrors().addAll(validator.validate(flow));

        existing.ifPresent(flow::copyFlowState);

        if (flow.hasErrors()) {
            flow.getFlowStatus().setState(FlowState.INVALID);
        }

        return flow;
    }

    private FlowT save(FlowT flow) {
        Flow existingFlow = flowCacheService.getFlow(flowType, flow.getName());
        if (existingFlow != null) {
            flow.setId(existingFlow.getId());
        }
        FlowT persistedFlow = flowRepo.save(flow);
        refreshCache();
        return persistedFlow;
    }

    private void saveAll(Collection<FlowT> flows) {
        flowRepo.saveAll(flows);
    }

    private boolean updateAndRefresh(String flowName, FlowState flowState) {
        if (flowRepo.updateFlowStatusState(flowName, flowState, flowType) > 0) {
            refreshCache();
            return true;
        }

        return false;
    }

    private boolean updateAndRefreshTestMode(String flowName, boolean testMode) {
        if (flowRepo.updateFlowStatusTestMode(flowName, testMode, flowType) > 0) {
            refreshCache();
            return true;
        }

        return false;
    }

    String runningFlowError(List<String> runningFlows) {
        return "The plugin has created the following " + flowType + " flows which are still running: " + String.join(", ", runningFlows);
    }

    @Override
    public void cleanupFor(PluginEntity plugin) {
        removeBySourcePlugin(plugin.getPluginCoordinates());
    }
}
