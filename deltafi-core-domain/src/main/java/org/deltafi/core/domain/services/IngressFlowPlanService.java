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
import org.deltafi.core.domain.generated.types.*;
import org.deltafi.core.domain.types.IngressFlow;
import org.deltafi.core.domain.types.IngressFlowPlan;
import org.deltafi.core.domain.repo.IngressFlowPlanRepo;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class IngressFlowPlanService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT)
            .setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private final IngressFlowPlanRepo ingressFlowPlanRepo;
    private final IngressFlowService ingressFlowService;

    /**
     * Persist the IngressFlowPlan and create a flow from the plan.
     * @param ingressFlowPlanInput flow plan used to create a new ingress flow
     * @return IngressFlow that was created from the plan
     */
    public IngressFlow saveIngressFlowPlan(IngressFlowPlanInput ingressFlowPlanInput) {
        PluginCoordinates existingSourcePlugin = ingressFlowPlanRepo
                .findById(ingressFlowPlanInput.getName())
                .map(IngressFlowPlan::getSourcePlugin)
                .orElse(ingressFlowPlanInput.getSourcePlugin());

        if (!existingSourcePlugin.equalsIgnoreVersion(ingressFlowPlanInput.getSourcePlugin())) {
            throw new IllegalArgumentException("An Ingress Flow Plan with the name: " + ingressFlowPlanInput.getName() + " already exists from another source plugin: " + existingSourcePlugin);
        }

        IngressFlowPlan flowPlan = ingressFlowPlanRepo.save(mapFromInput(ingressFlowPlanInput));
        return ingressFlowService.buildAndSaveFlow(flowPlan);
    }

    /**
     * Find all the IngressFlowPlans with the given sourcePlugin and rebuild
     * the flows for each of them.
     * @param sourcePlugin plugin whose flows should be recreated
     */
    public void rebuildFlowsForPlugin(PluginCoordinates sourcePlugin) {
        ingressFlowService.rebuildFlows(ingressFlowPlanRepo.findBySourcePlugin(sourcePlugin), sourcePlugin);
    }

    /**
     * Get all the ingress flow plans
     * @return all ingress flow plans
     */
    public List<IngressFlowPlan> getAll() {
        return ingressFlowPlanRepo.findAll();
    }

    /**
     * Find the ingress flow plan with the given name, or throw an exception if it does not exist
     * @param flowPlanName name of the plan to find
     * @return flow plan with the given name
     */
    public IngressFlowPlan getPlanByName(String flowPlanName) {
        return ingressFlowPlanRepo.findById(flowPlanName)
                .orElseThrow(() -> new DgsEntityNotFoundException("Could not find an Ingress Flow Plan named " + flowPlanName));
    }

    /**
     * Remove all the IngressFlows and IngressFlowPlans with the given sourcePlugin
     * @param pluginCoordinates sourcePlugin whose IngressFlowPlans should be removed
     * @return number of plan that were removed
     */
    public int removeFlowsAndPlansBySourcePlugin(PluginCoordinates pluginCoordinates) {
        ingressFlowService.removeBySourcePlugin(pluginCoordinates);
        return ingressFlowPlanRepo.deleteBySourcePlugin(pluginCoordinates);
    }

    /**
     * Remove the Flow Plan with the given name
     * @param flowPlanName name of the flow plan to remove
     * @return true if the flow plan was removed otherwise false
     */
    public boolean removePlan(String flowPlanName) {
        ingressFlowService.removeByName(flowPlanName);
        if (ingressFlowPlanRepo.existsById(flowPlanName)) {
            ingressFlowPlanRepo.deleteById(flowPlanName);
            return true;
        }
        return false;
    }

    private IngressFlowPlan mapFromInput(IngressFlowPlanInput ingressFlowPlanInput) {
        return OBJECT_MAPPER.convertValue(ingressFlowPlanInput, IngressFlowPlan.class);
    }

}
