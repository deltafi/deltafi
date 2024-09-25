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
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.types.FlowPlan;
import org.deltafi.common.types.FlowType;
import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.core.repo.FlowRepo;
import org.deltafi.core.repo.PluginRepository;
import org.deltafi.core.types.snapshot.FlowSnapshot;
import org.deltafi.core.types.*;
import org.deltafi.core.exceptions.DeltafiConfigurationException;
import org.deltafi.core.types.snapshot.SnapshotRestoreOrder;
import org.deltafi.core.types.snapshot.SystemSnapshot;
import org.deltafi.core.validation.FlowPlanValidator;

import java.util.*;

@AllArgsConstructor
@Slf4j
public abstract class FlowPlanService<FlowPlanT extends FlowPlan, FlowT extends Flow, FlowSnapshotT extends FlowSnapshot, FlowRepoT extends FlowRepo> implements Snapshotter {

    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT)
            .setSerializationInclusion(JsonInclude.Include.ALWAYS);

    private final FlowPlanValidator<FlowPlanT> flowPlanValidator;
    private final PluginRepository pluginRepo;
    private final FlowService<FlowPlanT, FlowT, FlowSnapshotT, FlowRepoT> flowService;
    private FlowType flowType;
    Class<FlowPlanT> flowPlanClass;

    public List<String> validateFlowPlans(List<FlowPlanT> flowPlans, List<FlowPlan> existingFlowPlans) {
        List<String> errors = new ArrayList<>();
        for (FlowPlanT flowPlan : flowPlans) {
            try {
                validateFlowPlan(flowPlan, existingFlowPlans);
            } catch (DeltafiConfigurationException e) {
                errors.add(e.getMessage());
            }
        }

        return errors;
    }

    public void validateFlowPlan(FlowPlanT flowPlan, List<FlowPlan> existingFlowPlans) {
        FlowPlan existingFlowPlan = existingFlowPlans.stream()
                .filter(e -> e.getName().equals(flowPlan.getName()) && e.getType() == flowType)
                .findFirst()
                .orElse(null);

        if (existingFlowPlan != null && !existingFlowPlan.getSourcePlugin().equalsIgnoreVersion(flowPlan.getSourcePlugin())) {
            throw new DeltafiConfigurationException("A flow plan with the name: " + flowPlan.getName() + " already exists from another source plugin: " + existingFlowPlan);
        }

        FlowType otherDataSourceType;
        if (flowPlan.getType() == FlowType.TIMED_DATA_SOURCE) {
            otherDataSourceType = FlowType.REST_DATA_SOURCE;
        } else if (flowPlan.getType() == FlowType.REST_DATA_SOURCE) {
            otherDataSourceType = FlowType.TIMED_DATA_SOURCE;
        } else {
            otherDataSourceType = null;
        }
        if (otherDataSourceType != null) {
            FlowPlan existingFlowPlanOfOtherType = existingFlowPlans.stream()
                    .filter(e -> e.getName().equals(flowPlan.getName()) && e.getType() == otherDataSourceType)
                    .findFirst()
                    .orElse(null);
            if (existingFlowPlanOfOtherType != null) {
                throw new DeltafiConfigurationException("A flow plan with the name: " + flowPlan.getName() + " of type: " + otherDataSourceType + " already exists -- two data sources of the same name cannot exist");
            }
        }

        flowPlanValidator.validate(flowPlan);
    }

    // this is gross but prevents a circular dependency with the plugin service
    public List<FlowPlan> findFlowPlansBySourcePluginAndType(PluginCoordinates sourcePlugin, FlowType flowType) {
        Optional<PluginEntity> plugin = pluginRepo.findById(sourcePlugin);
        if (plugin.isPresent() && plugin.get().getFlowPlans() == null) {
            return Collections.emptyList();
        }
        return plugin.map(pluginEntity -> pluginEntity.getFlowPlans().stream().filter(t -> t.getType() == flowType).toList()).orElse(Collections.emptyList());
    }

    /**
     * Find all the flow plans with the given sourcePlugin and rebuild
     * the flows for each of them.
     * @param sourcePlugin plugin whose flows should be recreated
     */
    public void rebuildFlowsForPlugin(PluginCoordinates sourcePlugin) {
        flowService.rebuildFlows(findFlowPlansBySourcePluginAndType(sourcePlugin, flowType), sourcePlugin);
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
}
