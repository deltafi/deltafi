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
package org.deltafi.core.domain.datafetchers;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.core.domain.api.types.ActionType;
import org.deltafi.core.domain.api.types.KeyValue;
import org.deltafi.core.domain.api.types.PluginCoordinates;
import org.deltafi.core.domain.configuration.DeltaFiConfiguration;
import org.deltafi.core.domain.converters.YamlRepresenter;
import org.deltafi.core.domain.generated.types.*;
import org.deltafi.core.domain.plugin.PluginRegistryService;
import org.deltafi.core.domain.services.*;
import org.deltafi.core.domain.types.EgressFlowPlanInput;
import org.deltafi.core.domain.types.EnrichFlowPlanInput;
import org.deltafi.core.domain.types.IngressFlowPlanInput;
import org.deltafi.core.domain.types.*;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.util.*;

@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class FlowPlanDatafetcher {

    public static final ActionFamily INGRESS_FAMILY = ActionFamily.newBuilder().family("ingress").actionNames(List.of(DeltaFiConstants.INGRESS_ACTION)).build();
    private static final Yaml YAML_EXPORTER = new Yaml(new Constructor(), new YamlRepresenter());

    private final IngressFlowPlanService ingressFlowPlanService;
    private final IngressFlowService ingressFlowService;
    private final EgressFlowPlanService egressFlowPlanService;
    private final EgressFlowService egressFlowService;
    private final EnrichFlowService enrichFlowService;
    private final EnrichFlowPlanService enrichFlowPlanService;
    private final PluginVariableService pluginVariableService;
    private final PluginRegistryService pluginRegistryService;

    @DgsMutation
    public IngressFlow saveIngressFlowPlan(IngressFlowPlanInput ingressFlowPlan) {
        return ingressFlowPlanService.saveFlowPlan(ingressFlowPlan);
    }

    @DgsMutation
    public boolean removeIngressFlowPlan(String name) {
        return ingressFlowPlanService.removePlan(name);
    }

    @DgsMutation
    public boolean startIngressFlow(String flowName) {
        return ingressFlowService.startFlow(flowName);
    }

    @DgsMutation
    public boolean stopIngressFlow(String flowName) {
        return ingressFlowService.stopFlow(flowName);
    }

    @DgsMutation
    public EnrichFlow saveEnrichFlowPlan(EnrichFlowPlanInput enrichFlowPlan) {
        return enrichFlowPlanService.saveFlowPlan(enrichFlowPlan);
    }

    @DgsMutation
    public boolean removeEnrichFlowPlan(String name) {
        return enrichFlowPlanService.removePlan(name);
    }

    @DgsMutation
    public boolean startEnrichFlow(String flowName) {
        return enrichFlowService.startFlow(flowName);
    }

    @DgsMutation
    public boolean stopEnrichFlow(String flowName) {
        return enrichFlowService.stopFlow(flowName);
    }

    @DgsMutation
    public EgressFlow saveEgressFlowPlan(EgressFlowPlanInput egressFlowPlan) {
        return egressFlowPlanService.saveFlowPlan(egressFlowPlan);
    }

    @DgsMutation
    public boolean removeEgressFlowPlan(String name) {
        return egressFlowPlanService.removePlan(name);
    }

    @DgsMutation
    public boolean startEgressFlow(String flowName) {
        return egressFlowService.startFlow(flowName);
    }

    @DgsMutation
    public boolean stopEgressFlow(String flowName) {
        return egressFlowService.stopFlow(flowName);
    }

    @DgsMutation
    public boolean savePluginVariables(PluginVariablesInput pluginVariablesInput) {
        pluginVariableService.saveVariables(pluginVariablesInput);
        return true;
    }

    @DgsMutation
    public boolean setPluginVariableValues(PluginCoordinates pluginCoordinates, @InputArgument(collectionType = KeyValue.class) List<KeyValue> variables) {
        boolean updated = pluginVariableService.setVariableValues(pluginCoordinates, variables);
        if (updated) {
            ingressFlowPlanService.rebuildFlowsForPlugin(pluginCoordinates);
            enrichFlowPlanService.rebuildFlowsForPlugin(pluginCoordinates);
            egressFlowPlanService.rebuildFlowsForPlugin(pluginCoordinates);
        }
        return  updated;
    }

    @DgsQuery
    public IngressFlow getIngressFlow(String flowName) {
        return ingressFlowService.getFlowOrThrow(flowName);
    }

    @DgsQuery
    public EnrichFlow getEnrichFlow(String flowName) {
        return enrichFlowService.getFlowOrThrow(flowName);
    }

    @DgsQuery
    public EgressFlow getEgressFlow(String flowName) {
        return egressFlowService.getFlowOrThrow(flowName);
    }

    @DgsQuery
    public IngressFlow validateIngressFlow(String flowName) {
        return ingressFlowService.validateAndSaveFlow(flowName);
    }

    @DgsQuery
    public EnrichFlow validateEnrichFlow(String flowName) {
        return enrichFlowService.validateAndSaveFlow(flowName);
    }

    @DgsQuery
    public EgressFlow validateEgressFlow(String flowName) {
        return egressFlowService.validateAndSaveFlow(flowName);
    }

    @DgsQuery
    public IngressFlowPlan getIngressFlowPlan(String planName) {
        return ingressFlowPlanService.getPlanByName(planName);
    }

    @DgsQuery
    public EnrichFlowPlan getEnrichFlowPlan(String planName) {
        return enrichFlowPlanService.getPlanByName(planName);
    }

    @DgsQuery
    public EgressFlowPlan getEgressFlowPlan(String planName) {
        return egressFlowPlanService.getPlanByName(planName);
    }

    @DgsQuery
    public List<DeltaFiConfiguration> deltaFiConfigs(@InputArgument ConfigQueryInput configQuery) {
        List<DeltaFiConfiguration> matchingConfigs = ingressFlowService.getConfigs(configQuery);
        matchingConfigs.addAll(enrichFlowService.getConfigs(configQuery));
        matchingConfigs.addAll(egressFlowService.getConfigs(configQuery));

        return matchingConfigs;
    }

    @DgsQuery
    public String exportConfigAsYaml() {
        Map<String, List<? extends Flow>> flowMap = new HashMap<>();
        flowMap.put("ingressFlows", ingressFlowService.getAll());
        flowMap.put("enrichFlows", enrichFlowService.getAll());
        flowMap.put("egressFlows", egressFlowService.getAll());

        return YAML_EXPORTER.dumpAsMap(flowMap);
    }

    @DgsQuery
    public SystemFlows getAllFlows() {
        return SystemFlows.newBuilder()
                .ingress(ingressFlowService.getAll())
                .enrich(enrichFlowService.getAll())
                .egress(egressFlowService.getAll()).build();
    }

    @DgsQuery
    public SystemFlowPlans getAllFlowPlans() {
        return SystemFlowPlans.newBuilder()
                .ingressPlans(ingressFlowPlanService.getAll())
                .enrichPlans(enrichFlowPlanService.getAll())
                .egressPlans(egressFlowPlanService.getAll()).build();
    }

    @DgsQuery
    public Collection<Flows> getFlows() {
        return pluginRegistryService.getFlowsByPlugin();
    }

    @DgsQuery
    public Collection<ActionFamily> getActionNamesByFamily() {
        EnumMap<ActionType, ActionFamily> actionFamilyMap = new EnumMap<>(ActionType.class);
        actionFamilyMap.put(ActionType.INGRESS, INGRESS_FAMILY);
        ingressFlowService.getAll().forEach(flow -> flow.updateActionNamesByFamily(actionFamilyMap));
        enrichFlowService.getAll().forEach(flow -> flow.updateActionNamesByFamily(actionFamilyMap));
        egressFlowService.getAll().forEach(flow -> flow.updateActionNamesByFamily(actionFamilyMap));
        return actionFamilyMap.values();
    }
}
