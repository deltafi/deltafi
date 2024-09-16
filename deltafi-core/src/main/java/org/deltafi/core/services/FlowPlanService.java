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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.types.FlowType;
import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.core.plugin.PluginEntity;
import org.deltafi.core.repo.FlowRepo;
import org.deltafi.core.types.snapshot.FlowSnapshot;
import org.deltafi.core.types.*;
import org.deltafi.core.exceptions.DeltafiConfigurationException;
import org.deltafi.core.plugin.PluginCleaner;
import org.deltafi.core.repo.FlowPlanRepo;
import org.deltafi.core.types.snapshot.SnapshotRestoreOrder;
import org.deltafi.core.types.snapshot.SystemSnapshot;
import org.deltafi.core.validation.FlowPlanValidator;
import org.springframework.boot.info.BuildProperties;
import org.springframework.security.core.parameters.P;

import java.util.*;
import java.util.stream.Collectors;

@AllArgsConstructor
@Slf4j
public abstract class FlowPlanService<FlowPlanT extends FlowPlanEntity, FlowT extends Flow, FlowSnapshotT extends FlowSnapshot, FlowRepoT extends FlowRepo> implements PluginCleaner, Snapshotter {

    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT)
            .setSerializationInclusion(JsonInclude.Include.ALWAYS);

    private final FlowPlanValidator<FlowPlanT> flowPlanValidator;
    private final FlowPlanRepo flowPlanRepo;
    private final FlowService<FlowPlanT, FlowT, FlowSnapshotT, FlowRepoT> flowService;
    private final BuildProperties buildProperties;

    @PostConstruct
    public void updateSystemPluginFlowPlans() {
        flowPlanRepo.updateSystemPluginFlowPlanVersions(buildProperties.getVersion(), getFlowType());
    }

    /**
     * Save the given list of flow plans. Find and remove any flow plans for the
     * source plugin that were not in the list of flow plans to save.
     * @param sourcePlugin plugin that provided the flow plans
     * @param flowPlans flow plans to save
     */
    public void upgradeFlowPlans(PluginCoordinates sourcePlugin, List<FlowPlanT> flowPlans) {
        flowPlanRepo.deleteBySourcePluginGroupIdAndSourcePluginArtifactIdAndType(sourcePlugin.getGroupId(), sourcePlugin.getArtifactId(), getFlowType());
        flowPlanRepo.saveAll(flowPlans);
        flowService.upgradeFlows(sourcePlugin, flowPlans);
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

    public FlowPlanEntity validateFlowPlan(FlowPlanT flowPlan) {
        FlowPlanEntity existingFlowPlan = flowPlanRepo
                .findByNameAndType(flowPlan.getName(), getFlowType())
                .orElse(null);

        if (existingFlowPlan != null && !existingFlowPlan.getSourcePlugin().equalsIgnoreVersion(flowPlan.getSourcePlugin())) {
            throw new DeltafiConfigurationException("A flow plan with the name: " + flowPlan.getName() + " already exists from another source plugin: " + existingFlowPlan);
        }

        FlowType otherDataSourceType = null;
        if (flowPlan.getType() == FlowType.TIMED_DATA_SOURCE) {
            otherDataSourceType = FlowType.REST_DATA_SOURCE;
        } else if (flowPlan.getType() == FlowType.REST_DATA_SOURCE) {
            otherDataSourceType = FlowType.TIMED_DATA_SOURCE;
        }
        if (otherDataSourceType != null) {
            FlowPlanEntity existingFlowPlanOfOtherType = flowPlanRepo
                    .findByNameAndType(flowPlan.getName(), otherDataSourceType)
                    .orElse(null);
            if (existingFlowPlanOfOtherType != null) {
                throw new DeltafiConfigurationException("A flow plan with the name: " + flowPlan.getName() + " of type: " + otherDataSourceType + " already exists -- two data sources of the same name cannot exist");
            }
        }

        flowPlanValidator.validate(flowPlan);

        return existingFlowPlan;
    }

    /**
     * Persist the FlowPlan and create a flow from the plan.
     * @param flowPlan flow plan used to create a new flow
     * @return Flow that was created from the plan
     */
    public FlowT saveFlowPlan(FlowPlanT flowPlan) {
        FlowPlanEntity existingFlowPlan = validateFlowPlan(flowPlan);
        if (existingFlowPlan != null) {
            log.info("setting ID");
            flowPlan.setId(existingFlowPlan.getId());
        }
        return flowService.buildAndSaveFlow(flowPlanRepo.save(flowPlan));
    }

    protected abstract FlowType getFlowType();

    public void rebuildInvalidFlows() {
        List<String> invalidFlows = flowService.getNamesOfInvalidFlows();

        Map<PluginCoordinates, List<FlowPlanEntity>> flowPlans = invalidFlows.stream()
                .map(name -> flowPlanRepo.findByNameAndType(name, getFlowType()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.groupingBy(FlowPlanEntity::getSourcePlugin));

        flowPlans.forEach((pluginCoordinates, plans) -> flowService.rebuildFlows(plans, pluginCoordinates));
    }

    /**
     * Find all the flow plans with the given sourcePlugin and rebuild
     * the flows for each of them.
     * @param sourcePlugin plugin whose flows should be recreated
     */
    public void rebuildFlowsForPlugin(PluginCoordinates sourcePlugin) {
        flowService.rebuildFlows(flowPlanRepo.findBySourcePluginAndType(sourcePlugin, getFlowType()), sourcePlugin);
    }

    protected abstract Class<FlowPlanT> getFlowPlanClass();

    /**
     * Get all the flow plans
     * @return all flow plans
     */
    public List<FlowPlanT> getAll() {
        return flowPlanRepo.findByType(getFlowType()).stream()
                .filter(getFlowPlanClass()::isInstance)
                .map(getFlowPlanClass()::cast)
                .toList();
    }

    /**
     * Find the flow plan with the given name, or throw an exception if it does not exist
     * @param flowPlanName name of the plan to find
     * @return flow plan with the given name
     */
    public FlowPlanT getPlanByName(String flowPlanName) {
        return flowPlanRepo.findByNameAndType(flowPlanName, getFlowType())
                .map(flowPlan -> {
                    if (getFlowPlanClass().isInstance(flowPlan)) {
                        return getFlowPlanClass().cast(flowPlan);
                    } else {
                        throw new IllegalStateException("Found flow plan is not of the expected type: " + getFlowPlanClass().getSimpleName());
                    }
                })
                .orElseThrow(() -> new DgsEntityNotFoundException("Could not find a flow plan named " + flowPlanName));
    }

    /**
     * Remove all the flows and flow plans with the given sourcePlugin
     *
     * @param pluginCoordinates sourcePlugin whose flows and flow plans should be removed
     */
    void removeFlowsAndPlansBySourcePlugin(PluginCoordinates pluginCoordinates) {
        flowService.removeBySourcePlugin(pluginCoordinates);
        flowPlanRepo.deleteBySourcePluginAndType(pluginCoordinates, getFlowType());
    }

    /**
     * Remove the flow plan with the given name
     * @param flowPlanName name of the flow plan to remove
     * @return true if the flow plan was removed otherwise false
     */
    public boolean removePlan(String flowPlanName, PluginCoordinates systemPlugin) {
        FlowPlanEntity flowPlanT = flowPlanRepo.findByNameAndType(flowPlanName, getFlowType()).orElse(null);
        if (flowPlanT != null && !systemPlugin.equalsIgnoreVersion(flowPlanT.getSourcePlugin())) {
            throw new IllegalArgumentException("Flow plan " + flowPlanName + " is not a " + systemPlugin.getArtifactId() + " flow plan and cannot be removed");
        }

        flowService.removeByName(flowPlanName, systemPlugin);
        if (flowPlanT != null) {
            flowPlanRepo.deleteById(flowPlanT.getId());
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
    public void cleanupFor(PluginEntity plugin) {
        removeFlowsAndPlansBySourcePlugin(plugin.getPluginCoordinates());
    }
}
