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
package org.deltafi.core.domain.services;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException;
import lombok.AllArgsConstructor;
import org.deltafi.core.domain.api.types.PluginCoordinates;
import org.deltafi.core.domain.generated.types.EgressFlowPlanInput;
import org.deltafi.core.domain.repo.EgressFlowPlanRepo;
import org.deltafi.core.domain.types.EgressFlow;
import org.deltafi.core.domain.types.EgressFlowPlan;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class EgressFlowPlanService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT)
            .setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private final EgressFlowPlanRepo egressFlowPlanRepo;
    private final EgressFlowService egressFlowService;

    /**
     * Persist the EgressFlowPlan and create a flow from the plan.
     * @param egressFlowPlanInput flow plan used to create a new egress flow
     * @return EgressFlow that was created from the plan
     */
    public EgressFlow saveEgressFlowPlan(EgressFlowPlanInput egressFlowPlanInput) {
        PluginCoordinates existingSourcePlugin = egressFlowPlanRepo
                .findById(egressFlowPlanInput.getName())
                .map(EgressFlowPlan::getSourcePlugin)
                .orElse(egressFlowPlanInput.getSourcePlugin());

        if (!existingSourcePlugin.equalsIgnoreVersion(egressFlowPlanInput.getSourcePlugin())) {
            throw new IllegalArgumentException("An Egress Flow Plan with the name: " + egressFlowPlanInput.getName() + " already exists from another source plugin: " + existingSourcePlugin);
        }

        EgressFlowPlan flowPlan = egressFlowPlanRepo.save(mapFromInput(egressFlowPlanInput));
        return egressFlowService.buildAndSaveFlow(flowPlan);
    }

    /**
     * Find all the EgressFlowPlans with the given sourcePlugin and rebuild
     * the flows for each of them.
     * @param sourcePlugin plugin whose flows should be recreated
     */
    public void rebuildFlowsForPlugin(PluginCoordinates sourcePlugin) {
        egressFlowService.rebuildFlows(egressFlowPlanRepo.findBySourcePlugin(sourcePlugin), sourcePlugin);
    }

    /**
     * Get all the egress flow plans
     * @return all egress flow plans
     */
    public List<EgressFlowPlan> getAll() {
        return egressFlowPlanRepo.findAll();
    }

    /**
     * Find the egress flow plan with the given name, or throw an exception if it does not exist
     * @param flowPlanName name of the plan to find
     * @return flow plan with the given name
     */
    public EgressFlowPlan getPlanByName(String flowPlanName) {
        return egressFlowPlanRepo.findById(flowPlanName)
                .orElseThrow(() -> new DgsEntityNotFoundException("Could not find an Egress Flow Plan named " + flowPlanName));
    }

    /**
     * Remove all the EgressFlows and EgressFlowPlans with the given sourcePlugin
     * @param pluginCoordinates sourcePlugin whose EgressFlowPlans should be removed
     * @return number of plan that were removed
     */
    public int removeFlowsAndPlansBySourcePlugin(PluginCoordinates pluginCoordinates) {
        egressFlowService.removeBySourcePlugin(pluginCoordinates);
        return egressFlowPlanRepo.deleteBySourcePlugin(pluginCoordinates);
    }

    /**
     * Remove the Flow Plan with the given name
     * @param flowPlanName name of the flow plan to remove
     * @return true if the flow plan was removed otherwise false
     */
    public boolean removePlan(String flowPlanName) {
        egressFlowService.removeByName(flowPlanName);
        if (egressFlowPlanRepo.existsById(flowPlanName)) {
            egressFlowPlanRepo.deleteById(flowPlanName);
            return true;
        }
        return false;
    }

    private EgressFlowPlan mapFromInput(EgressFlowPlanInput egressFlowPlanInput) {
        return OBJECT_MAPPER.convertValue(egressFlowPlanInput, EgressFlowPlan.class);
    }

}
