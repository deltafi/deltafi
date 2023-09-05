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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException;
import lombok.AllArgsConstructor;
import org.deltafi.common.types.FlowPlan;
import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.core.snapshot.types.FlowSnapshot;
import org.deltafi.core.types.*;
import org.deltafi.core.exceptions.DeltafiConfigurationException;
import org.deltafi.common.types.Plugin;
import org.deltafi.core.plugin.PluginCleaner;
import org.deltafi.core.repo.FlowPlanRepo;
import org.deltafi.core.snapshot.SnapshotRestoreOrder;
import org.deltafi.core.snapshot.Snapshotter;
import org.deltafi.core.snapshot.SystemSnapshot;
import org.deltafi.core.validation.FlowPlanValidator;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@AllArgsConstructor
public abstract class FlowPlanService<FlowPlanT extends FlowPlan, FlowT extends Flow, FlowSnapshotT extends FlowSnapshot> implements PluginCleaner, Snapshotter {

    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT)
            .setSerializationInclusion(JsonInclude.Include.ALWAYS);

    private final FlowPlanValidator<FlowPlanT> flowPlanValidator;
    private final FlowPlanRepo<FlowPlanT> flowPlanRepo;
    private final FlowService<FlowPlanT, FlowT, FlowSnapshotT> flowService;

    /**
     * Save the given list of flow plans. Find and remove any flow plans for the
     * source plugin that were not in the list of flow plans to save.
     * @param sourcePlugin plugin that provided the flow plans
     * @param flowPlans flow plans to save
     */
    public void upgradeFlowPlans(PluginCoordinates sourcePlugin, List<FlowPlanT> flowPlans) {
        flowPlanRepo.saveAll(flowPlans);

        Set<String> flowPlanNames = flowPlans.stream().map(FlowPlan::getName).collect(Collectors.toSet());
        Set<String> flowPlansToRemove = flowPlanRepo.findByGroupIdAndArtifactId(sourcePlugin.getGroupId(), sourcePlugin.getArtifactId()).stream()
                .map(FlowPlan::getName)
                .filter(name -> !flowPlanNames.contains(name))
                .collect(Collectors.toSet());

        if (!flowPlansToRemove.isEmpty()) {
            flowPlanRepo.deleteAllById(flowPlansToRemove);
        }

        flowService.upgradeFlows(sourcePlugin, flowPlans, flowPlanNames);
    }

    public List<String> validateFlowPlans(List<FlowPlanT> flowPlans) {
        List<String> errors = new ArrayList<>();
        for (FlowPlanT flowPlan : flowPlans) {
            try {
                validateFlowPlan(flowPlan);
            } catch (DeltafiConfigurationException e) {
                errors.add(e.getMessage());
            }
        }

        return errors;
    }

    public void validateFlowPlan(FlowPlanT flowPlan) {
        PluginCoordinates existingSourcePlugin = flowPlanRepo
                .findById(flowPlan.getName())
                .map(FlowPlan::getSourcePlugin)
                .orElse(flowPlan.getSourcePlugin());

        if (!existingSourcePlugin.equalsIgnoreVersion(flowPlan.getSourcePlugin())) {
            throw new DeltafiConfigurationException("A flow plan with the name: " + flowPlan.getName() + " already exists from another source plugin: " + existingSourcePlugin);
        }

        flowPlanValidator.validate(flowPlan);
    }

    /**
     * Persist the FlowPlan and create a flow from the plan.
     * @param flowPlan flow plan used to create a new flow
     * @return Flow that was created from the plan
     */
    public FlowT saveFlowPlan(FlowPlanT flowPlan) {
        validateFlowPlan(flowPlan);
        return flowService.buildAndSaveFlow(flowPlanRepo.save(flowPlan));
    }

    /**
     * Find all the flow plans with the given sourcePlugin and rebuild
     * the flows for each of them.
     * @param sourcePlugin plugin whose flows should be recreated
     */
    public void rebuildFlowsForPlugin(PluginCoordinates sourcePlugin) {
        flowService.rebuildFlows(flowPlanRepo.findBySourcePlugin(sourcePlugin), sourcePlugin);
    }

    /**
     * Get all the flow plans
     * @return all flow plans
     */
    public List<FlowPlanT> getAll() {
        return flowPlanRepo.findAll();
    }

    /**
     * Find the flow plan with the given name, or throw an exception if it does not exist
     * @param flowPlanName name of the plan to find
     * @return flow plan with the given name
     */
    public FlowPlanT getPlanByName(String flowPlanName) {
        return flowPlanRepo.findById(flowPlanName)
                .orElseThrow(() -> new DgsEntityNotFoundException("Could not find a flow plan named " + flowPlanName));
    }

    /**
     * Remove all the flows and flow plans with the given sourcePlugin
     *
     * @param pluginCoordinates sourcePlugin whose flows and flow plans should be removed
     */
    void removeFlowsAndPlansBySourcePlugin(PluginCoordinates pluginCoordinates) {
        flowService.removeBySourcePlugin(pluginCoordinates);
        flowPlanRepo.deleteBySourcePlugin(pluginCoordinates);
    }

    /**
     * Remove the flow plan with the given name
     * @param flowPlanName name of the flow plan to remove
     * @return true if the flow plan was removed otherwise false
     */
    public boolean removePlan(String flowPlanName) {
        flowService.removeByName(flowPlanName);
        if (flowPlanRepo.existsById(flowPlanName)) {
            flowPlanRepo.deleteById(flowPlanName);
            return true;
        }
        return false;
    }

    @Override
    public void updateSnapshot(SystemSnapshot systemSnapshot) {
        // nothing to do here, flow plans should be packaged in the plugin
    }

    @Override
    public Result resetFromSnapshot(SystemSnapshot systemSnapshot, boolean hardReset) {
        // rebuild flows if there were any plugin variable changes
        systemSnapshot.getPluginVariables().stream()
                .map(PluginVariables::getSourcePlugin)
                .forEach(this::rebuildFlowsForPlugin);
        return new Result();
    }

    @Override
    public int getOrder() {
        return SnapshotRestoreOrder.FLOW_PLAN_ORDER;
    }

    @Override
    public void cleanupFor(Plugin plugin) {
        removeFlowsAndPlansBySourcePlugin(plugin.getPluginCoordinates());
    }
}
