/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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
import org.apache.commons.lang3.StringUtils;
import org.deltafi.common.types.*;
import org.deltafi.common.util.MarkdownBuilder;
import org.deltafi.core.converters.FlowPlanConverter;
import org.deltafi.core.exceptions.MissingActionConfigException;
import org.deltafi.core.exceptions.MissingFlowException;
import org.deltafi.core.generated.types.*;
import org.deltafi.core.types.*;
import org.deltafi.core.repo.FlowRepo;
import org.deltafi.core.types.Event.Severity;
import org.deltafi.core.types.snapshot.FlowSnapshot;
import org.deltafi.core.types.snapshot.SnapshotRestoreOrder;
import org.deltafi.core.types.snapshot.Snapshot;
import org.deltafi.core.validation.FlowValidator;
import org.springframework.boot.info.BuildProperties;

import java.time.OffsetDateTime;
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
    private final EventService eventService;

    protected FlowService(FlowType flowType, FlowRepoT flowRepo, PluginVariableService pluginVariableService,
                          FlowPlanConverter<FlowPlanT, FlowT> flowPlanConverter, FlowValidator validator,
                          BuildProperties buildProperties, FlowCacheService flowCacheService, EventService eventService,
                          Class<FlowT> flowClass, Class<FlowPlanT> flowPlanClass) {
        this.flowType = flowType;
        this.flowRepo = flowRepo;
        this.pluginVariableService = pluginVariableService;
        this.flowPlanConverter = flowPlanConverter;
        this.validator = validator;
        this.buildProperties = buildProperties;
        this.flowCacheService = flowCacheService;
        this.flowClass = flowClass;
        this.flowPlanClass = flowPlanClass;
        this.eventService = eventService;
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
     * Find the given flow and move it to a running state if it is currently stopped.
     * @param flowName name of the flow that should be started
     * @return true if the flow was successfully started
     */
    public boolean startFlow(String flowName) {
        FlowT flow = getFlowOrThrow(flowName);

        if (flow.isInvalid()) {
            log.warn("Tried to start {} {} when it was in an invalid state", flowType, flowName);
            throw new IllegalStateException("Flow: " + flowName + " cannot be started until configuration errors are resolved");
        }

        if (flow.isRunning()) {
            log.warn("Tried to start {} {} when it was already running", flowType, flowName);
            return false;
        }

        return updateAndRefresh(flowName, FlowState.RUNNING);
    }

    /**
     * Find the given flow and move it to a stopped state if it is not currently stopped.
     * @param flowName name of the flow that should be stopped
     * @return true if the flow was successfully stopped
     */
    public boolean stopFlow(String flowName) {
        FlowT flow = getFlowOrThrow(flowName);

        if (flow.isStopped()) {
            log.warn("Tried to stop {} {} which was already stopped", flowType, flowName);
            return false;
        }

        return updateAndRefresh(flowName, FlowState.STOPPED);
    }

    /**
     * Find the given flow and move it to a paused state if it is not currently paused.
     * @param flowName name of the flow that should be paused
     * @return true if the flow was successfully paused
     */
    public boolean pauseFlow(String flowName) {
        FlowT flow = getFlowOrThrow(flowName);

        if (flow.isPaused()) {
            log.warn("Tried to pause {} {} which was already paused", flowType, flowName);
            return false;
        }

        return updateAndRefresh(flowName, FlowState.PAUSED);
    }

    public boolean enableTestMode(String flowName) {
        FlowT flow = getFlowOrThrow(flowName);

        if (flow.isTestMode()) {
            log.warn("Tried to enable test mode on {} {} when already in test mode", flowType, flowName);
            return false;
        }

        return updateAndRefreshTestMode(flowName, true);
    }

    public boolean disableTestMode(String flowName) {
        FlowT flow = getFlowOrThrow(flowName);

        if (!flow.isTestMode()) {
            log.warn("Tried to disable test mode on {} {} when not already in test mode", flowType, flowName);
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
     * For each of the given flow plans, rebuild the flow from the plan and latest variables
     * @param flowPlans list of flow plans that need flows rebuilt
     * @param sourcePlugin PluginCoordinates used to find the variables
     */
    public void rebuildFlows(List<FlowPlan> flowPlans, PluginCoordinates sourcePlugin) {
        rebuildFlows(flowPlans, sourcePlugin, pluginVariableService.getVariablesByPlugin(sourcePlugin));
    }

    public void rebuildFlows(List<FlowPlan> flowPlans, PluginCoordinates sourcePlugin, List<Variable> variables) {
        Map<String, FlowT> existingFlows = getByPlugin(sourcePlugin);
        List<FlowT> updatedFlows = flowPlans.stream()
                .map(flowPlan -> buildFlow(existingFlows, flowPlan, variables))
                .toList();

        updatedFlows.forEach(f -> flowRepo.deleteByNameAndType(f.getName(), f.getType()));
        flowRepo.saveAll(updatedFlows);

        refreshCache();
    }

    public void upgradeFlows(PluginCoordinates sourcePlugin, List<FlowPlanT> flowPlans) {
        Map<String, FlowT> existingFlows = getByPlugin(sourcePlugin);

        // Also check for system-plugin placeholders that can be claimed by this plugin
        List<String> incomingFlowNames = flowPlans.stream().map(FlowPlan::getName).toList();
        Map<String, FlowT> systemPluginPlaceholders = getSystemPluginPlaceholders(incomingFlowNames);

        // Merge: prefer existing plugin flows, fall back to system-plugin placeholders
        Map<String, FlowT> allExistingFlows = new HashMap<>(systemPluginPlaceholders);
        allExistingFlows.putAll(existingFlows);

        // recreates each flow maintaining the original flow id and state for pre-existing flows
        List<FlowT> flows = flowPlans.stream().map(flow -> buildFlow(allExistingFlows, flow)).toList();

        // delete the old versions of the flow prior to saving the new versions
        List<FlowT> deleteFlows = existingFlows.values().stream()
                .filter(e -> !incomingFlowNames.contains(e.getName())).toList();
        flowRepo.deleteAll(deleteFlows);

        // delete any system-plugin placeholders that were claimed
        flowRepo.deleteAll(systemPluginPlaceholders.values());

        // save the replacement flows
        flowRepo.saveAll(flows);

        refreshCache();
    }

    private Map<String, FlowT> getSystemPluginPlaceholders(List<String> flowNames) {
        PluginCoordinates systemPlugin = getSystemPluginCoordinates();
        return getByPlugin(systemPlugin).entrySet().stream()
                .filter(e -> e.getValue().getFlowStatus().getPlaceholder())
                .filter(e -> flowNames.contains(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private FlowT buildFlow(Map<String, FlowT> existingFlows, FlowPlanT flowPlan) {
        List<Variable> variables = pluginVariableService.getVariablesByPlugin(flowPlan.getSourcePlugin());
        return buildFlow(existingFlows, flowPlan, variables);
    }

    /**
     * Get the variables associated with this flow plan, and create
     * a flow from the plan and variables.
     *
     * @param flowPlan used to create a new flow
     * @return flow that was created from the plan
     */
    public FlowT buildAndSaveFlow(FlowPlanT flowPlan) {
        Optional<FlowT> flow = flowRepo.findByNameAndType(flowPlan.getName(), flowType, flowClass);
        Map<String, FlowT> existingFlows = flow.map(f -> Map.of(f.getName(), f)).orElse(Map.of());
        List<Variable> variables = pluginVariableService.getVariablesByPlugin(flowPlan.getSourcePlugin());
        return save(buildFlow(existingFlows, flowPlan, variables));
    }

    /**
     * Get all the flows and rerun validation
     */
    public void validateAllFlows() {
        getAll().forEach(this::validateAndSaveFlow);
    }

    /**
     * Find the given flow by name and rerun validation
     * @param flowName name of the flow to validate
     * @return updated flow after validation is run
     */
    public FlowT validateAndSaveFlow(String flowName) {
        return validateAndSaveFlow(getFlowOrThrow(flowName));
    }

    public void validateSystemPlanName(String planName, PluginCoordinates systemPlugin) {
        Flow flow = flowRepo.findByNameAndType(planName, flowType, flowClass).orElse(null);

        if (flow != null && !systemPlugin.equalsIgnoreVersion(flow.getSourcePlugin())) {
            throw new IllegalArgumentException("A " + flowType.getDisplayName() + " named " + planName + " exists in plugin " + flow.getSourcePlugin());
        }
    }

    FlowT validateAndSaveFlow(FlowT flow) {
        FlowState originalState = flow.getFlowStatus().getState();
        List<FlowConfigError> errors = new ArrayList<>(flow.getFlowStatus()
                .getErrors().stream().filter(error -> FlowErrorType.UNRESOLVED_VARIABLE.equals(error.getErrorType()))
                .toList());

        errors.addAll(validator.validate(flow));
        flow.getFlowStatus().setErrors(errors);

        if (!errors.isEmpty()) {
            if (flow.getFlowStatus().getValid()) {
                invalidFlowEvent(flow, originalState);
            }
            flow.getFlowStatus().setValid(false);
        } else {
            flow.getFlowStatus().setValid(true);
        }

        return save(flow);
    }

    /**
     * Check for flow with the given name.
     * @param flowName name of the flow to find
     * @return whether it is a flow
     */
    public boolean hasFlow(String flowName) {
        return flowCacheService.getFlow(flowType, flowName) != null;
    }

    /**
     * Check for running flow with the given name.
     * @param flowName name of the flow to find
     * @return whether it is a running flow
     */
    public boolean hasRunningFlow(String flowName) {
        return flowCacheService.getRunningFlow(flowType, flowName) != null;
    }

    /**
     * Find the running flow with the given name.
     * <p>
     * Throws an exception if the flow does not exist or is explicitly stopped.
     * Note: Invalid flows and flows with unavailable plugins are not rejected here -
     * they are handled at the ingress/publish level to queue data instead of rejecting it.
     * @param flowName name of the flow to find
     * @return the flow with the given name
     */
    public FlowT getActiveFlowByName(String flowName) {
        Flow flow = flowCacheService.getFlow(flowType, flowName);
        if (flow == null) {
            throw MissingFlowException.notFound(flowName, flowType);
        } else if (flow.isStopped()){
            throw MissingFlowException.stopped(flowName, flowType);
        }
        // Note: isInvalid() check removed - invalid flows should queue data, not reject it

        return flowClass.cast(flow);
    }

    /**
     * Find the flow with the given name.
     * <p>
     * Throws an exception if the given flow does not exist
     * @param flowName name of the flow to find
     * @return the flow with the given name
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

    /**
     * Get all flows in a running or paused state
     * @return all running or paused flows
     */
    public List<FlowT> getActiveFlows() {
        List<FlowT> flows = new ArrayList<>(getRunningFlows());
        flows.addAll(getPausedFlows());
        return flows;
    }

    /**
     * Get all flows in a paused state
     * @return all paused flows
     */
    public List<FlowT> getPausedFlows() {
        return getAll().stream().filter(Flow::isPaused).toList();
    }

    public List<String> getFlowNamesByState(FlowState state) {
        List<Flow> flows = state == null ? getAll().stream().map(f -> (Flow) f).toList() :
                flowRepo.findByFlowStatusStateAndType(state, flowType);

        return flows.stream().map(Flow::getName).toList();
    }

    @Override
    public Result resetFromSnapshot(Snapshot snapshot, boolean hardReset) {
        refreshCache();

        List<FlowT> flows = getAll();
        Map<String, FlowT> updatedFlows = new HashMap<>();
        Map<String, FlowT> allFlows = new HashMap<>();

        // build map of the flow name to flows. Reset the state fields on a hard reset
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

        return resetFromSnapshot(snapshot, allFlows, updatedFlows);
    }

    private Result resetFromSnapshot(Snapshot snapshot, Map<String, FlowT> allFlows, Map<String, FlowT> updatedFlows) {
        Result result = new Result();
        List<FlowSnapshotT> flowSnapshots = getFlowSnapshots(snapshot);

        if (flowSnapshots == null || flowSnapshots.isEmpty()) {
            return result;
        }

        List<FlowT> placeholdersToCreate = new ArrayList<>();

        for (FlowSnapshotT flowSnapshotT : flowSnapshots) {
            FlowT existing = allFlows.get(flowSnapshotT.getName());

            if (existing != null) {
                // Flow exists - update state
                if (updateFromSnapshot(existing, flowSnapshotT, result)) {
                    updatedFlows.put(existing.getName(), existing);
                }
            } else {
                // Flow doesn't exist - create placeholder
                // For old snapshots without sourcePlugin, use system-plugin so it can be claimed later
                if (flowSnapshotT.getSourcePlugin() == null) {
                    flowSnapshotT.setSourcePlugin(getSystemPluginCoordinates());
                }
                FlowT placeholder = createPlaceholderFlow(flowSnapshotT);
                placeholdersToCreate.add(placeholder);
                result.getInfo().add("Created placeholder for flow " + flowSnapshotT.getName() +
                    " (waiting for plugin " + flowSnapshotT.getSourcePlugin() + ")");
            }
        }

        if (!updatedFlows.isEmpty()) {
            saveAll(updatedFlows.values());
        }

        if (!placeholdersToCreate.isEmpty()) {
            flowRepo.saveAll(placeholdersToCreate);
        }

        if (!updatedFlows.isEmpty() || !placeholdersToCreate.isEmpty()) {
            refreshCache();
        }

        result.setSuccess(result.getErrors().isEmpty());
        return result;
    }


    public abstract List<FlowSnapshotT> getFlowSnapshots(Snapshot snapshot);

    /**
     * Create a placeholder flow from a snapshot. The placeholder will have the correct
     * sourcePlugin, name, running state, and testMode, but will be marked as invalid
     * with empty actions/config until the plugin registers and claims it.
     *
     * @param snapshot the flow snapshot to create a placeholder from
     * @return the placeholder flow
     */
    protected abstract FlowT createPlaceholderFlow(FlowSnapshotT snapshot);

    protected PluginCoordinates getSystemPluginCoordinates() {
        return new PluginCoordinates(PluginService.SYSTEM_PLUGIN_GROUP_ID,
                PluginService.SYSTEM_PLUGIN_ARTIFACT_ID, buildProperties.getVersion());
    }

    public boolean updateFromSnapshot(FlowT flow, FlowSnapshotT flowSnapshot, Result result) {
        boolean changed = false;
        if (flowSnapshot.isRunning() && flow.isStopped()) {
            flow.getFlowStatus().setState(FlowState.RUNNING);
            changed = true;
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
        FlowT flow = flowRepo.findByNameAndType(flowName, flowType, flowClass).orElse(null);
        if (flow != null) {
            if (flow.isRunning() && !flow.isInvalid()) {
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

    public ActionConfiguration findRunningActionConfig(String flowName, String actionName) {
        Flow flow = flowCacheService.getFlow(flowType, flowName);

        if (flow == null || !flow.isRunning()) {
            return null;
        }

        return flow.findActionConfigByName(actionName);
    }

    public ActionConfiguration findRunningActionConfigOrError(String flowName, String actionName) {
        Flow flow = getActiveFlowByName(flowName);

        ActionConfiguration actionConfiguration = flow.findActionConfigByName(actionName);
        if (actionConfiguration == null) {
            throw new MissingActionConfigException("Flow of type " + flowType + " named " + flowName + " no longer contains an action named " + actionName);
        }

        return actionConfiguration;
    }

    /**
     * Find all flows grouped by their source plugin
     * @return map of plugin coordinates to the list of associated flows
     */
    public Map<PluginCoordinates, List<FlowT>> getFlowsGroupedByPlugin() {
        return getAll().stream()
                .collect(Collectors.groupingBy(FlowT::getSourcePlugin));
    }

    FlowT buildFlow(Map<String, FlowT> existingFlows, FlowPlan flowPlan, List<Variable> variables) {
        Optional<FlowT> existing = Optional.ofNullable(existingFlows.get(flowPlan.getName()));
        FlowPlanT typedFlowPlan = flowPlanClass.cast(flowPlan);
        FlowT flow = flowPlanConverter.convert(typedFlowPlan, variables);

        flow.getFlowStatus().getErrors().addAll(validator.validate(flow));
        flow.getFlowStatus().setValid(!flow.hasErrors());

        existing.ifPresent(existingFlow -> copyFlowState(flow, existingFlow));

        return flow;
    }

    /**
     * Copy the source flow id and state to the target flow
     * @param targetFlow the new flow that should inherit the sourceFlow state
     * @param sourceFlow the existing flow whose state should be copied
     */
    private void copyFlowState(FlowT targetFlow, FlowT sourceFlow) {
        targetFlow.setId(sourceFlow.getId());

        // flow was not invalid before; fire an event
        if (targetFlow.isInvalid() && !sourceFlow.isInvalid()) {
            invalidFlowEvent(targetFlow, sourceFlow.getFlowStatus().getState());
        }

        targetFlow.getFlowStatus().setState(sourceFlow.getFlowStatus().getState());
        targetFlow.getFlowStatus().setTestMode(sourceFlow.getFlowStatus().getTestMode());
        targetFlow.copyFlowSpecificState(sourceFlow);
    }

    private void invalidFlowEvent(FlowT invalidFlow, FlowState lastState) {
        List<String> errors = invalidFlow.getFlowStatus().getErrors().stream().map(this::flowError).toList();

        MarkdownBuilder markdownBuilder = new MarkdownBuilder();
        markdownBuilder.addList("Errors:", errors);
        String content = markdownBuilder.build();

        Event event = Event.builder()
                .source("core")
                .timestamp(OffsetDateTime.now())
                .summary(capitalizedType(invalidFlow) + " " + invalidFlow.getName() + " was " + lastState.name().toLowerCase() + " but is now invalid")
                .content(content)
                .severity(Severity.ERROR)
                .notification(true)
                .build();
        eventService.createEvent(event);
    }

    private String capitalizedType(Flow flow) {
        return StringUtils.capitalize(flow.getType().getDisplayName());
    }

    private String flowError(FlowConfigError flowConfigError) {
        return flowConfigError.getConfigName() + ": " + flowConfigError.getMessage();
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

    private Map<String, FlowT> getByPlugin(PluginCoordinates sourcePlugin) {
        List<Flow> existingFlows = flowRepo.findBySourcePluginGroupIdAndSourcePluginArtifactIdAndType(
                sourcePlugin.getGroupId(), sourcePlugin.getArtifactId(), flowType);

        return existingFlows.stream()
                .map(flowClass::cast)
                .collect(Collectors.toMap(FlowT::getName, f -> f));
    }

    String runningFlowError(List<String> runningFlows) {
        return "The plugin has created the following " + flowType + " flows which are still running: " + String.join(", ", runningFlows);
    }

    @Override
    public void cleanupFor(PluginEntity plugin) {
        removeBySourcePlugin(plugin.getPluginCoordinates());
    }
}
