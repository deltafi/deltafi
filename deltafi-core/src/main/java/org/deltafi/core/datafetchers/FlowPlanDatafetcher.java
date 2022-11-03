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
package org.deltafi.core.datafetchers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.common.types.*;
import org.deltafi.core.converters.YamlRepresenter;
import org.deltafi.core.generated.types.*;
import org.deltafi.core.plugin.PluginRegistryService;
import org.deltafi.core.security.NeedsPermission;
import org.deltafi.core.services.*;
import org.deltafi.core.types.EgressFlow;
import org.deltafi.core.types.EnrichFlow;
import org.deltafi.core.types.Flow;
import org.deltafi.core.types.IngressFlow;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.util.*;

@DgsComponent
@RequiredArgsConstructor
@Slf4j
public class FlowPlanDatafetcher {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static final ActionFamily INGRESS_FAMILY = ActionFamily.newBuilder().family("INGRESS").actionNames(List.of(DeltaFiConstants.INGRESS_ACTION)).build();
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
    @NeedsPermission.FlowPlanCreate
    public IngressFlow saveIngressFlowPlan(IngressFlowPlanInput ingressFlowPlan) {
        return ingressFlowPlanService.saveFlowPlan(OBJECT_MAPPER.convertValue(ingressFlowPlan, IngressFlowPlan.class));
    }

    @DgsMutation
    @NeedsPermission.FlowPlanDelete
    public boolean removeIngressFlowPlan(String name) {
        return ingressFlowPlanService.removePlan(name);
    }

    @DgsMutation
    @NeedsPermission.FlowStart
    public boolean startIngressFlow(String flowName) {
        return ingressFlowService.startFlow(flowName);
    }

    @DgsMutation
    @NeedsPermission.FlowStop
    public boolean stopIngressFlow(String flowName) {
        return ingressFlowService.stopFlow(flowName);
    }

    @DgsMutation
    @NeedsPermission.FlowUpdate
    public boolean enableIngressTestMode(String flowName) { return ingressFlowService.enableTestMode(flowName); }

    @DgsMutation
    @NeedsPermission.FlowUpdate
    public boolean disableIngressTestMode(String flowName) { return ingressFlowService.disableTestMode(flowName); }

    @DgsMutation
    @NeedsPermission.FlowUpdate
    public boolean enableEgressTestMode(String flowName) { return egressFlowService.enableTestMode(flowName); }

    @DgsMutation
    @NeedsPermission.FlowUpdate
    public boolean disableEgressTestMode(String flowName) { return egressFlowService.disableTestMode(flowName); }

    @DgsMutation
    @NeedsPermission.FlowPlanCreate
    public EnrichFlow saveEnrichFlowPlan(EnrichFlowPlanInput enrichFlowPlan) {
        return enrichFlowPlanService.saveFlowPlan(OBJECT_MAPPER.convertValue(enrichFlowPlan, EnrichFlowPlan.class));
    }

    @DgsMutation
    @NeedsPermission.FlowPlanDelete
    public boolean removeEnrichFlowPlan(String name) {
        return enrichFlowPlanService.removePlan(name);
    }

    @DgsMutation
    @NeedsPermission.FlowStart
    public boolean startEnrichFlow(String flowName) {
        return enrichFlowService.startFlow(flowName);
    }

    @DgsMutation
    @NeedsPermission.FlowStop
    public boolean stopEnrichFlow(String flowName) {
        return enrichFlowService.stopFlow(flowName);
    }

    @DgsMutation
    @NeedsPermission.FlowPlanCreate
    public EgressFlow saveEgressFlowPlan(EgressFlowPlanInput egressFlowPlan) {
        return egressFlowPlanService.saveFlowPlan(OBJECT_MAPPER.convertValue(egressFlowPlan, EgressFlowPlan.class));
    }

    @DgsMutation
    @NeedsPermission.FlowPlanDelete
    public boolean removeEgressFlowPlan(String name) {
        return egressFlowPlanService.removePlan(name);
    }

    @DgsMutation
    @NeedsPermission.FlowStart
    public boolean startEgressFlow(String flowName) {
        return egressFlowService.startFlow(flowName);
    }

    @DgsMutation
    @NeedsPermission.FlowStop
    public boolean stopEgressFlow(String flowName) {
        return egressFlowService.stopFlow(flowName);
    }

    @DgsMutation
    @NeedsPermission.PluginVariableUpdate
    public boolean savePluginVariables(PluginVariablesInput pluginVariablesInput) {
        pluginVariableService.saveVariables(pluginVariablesInput.getSourcePlugin(), pluginVariablesInput.getVariables());
        return true;
    }

    @DgsMutation
    @NeedsPermission.PluginVariableUpdate
    public boolean setPluginVariableValues(PluginCoordinates pluginCoordinates, @InputArgument List<KeyValue> variables) {
        boolean updated = pluginVariableService.setVariableValues(pluginCoordinates, variables);
        if (updated) {
            ingressFlowPlanService.rebuildFlowsForPlugin(pluginCoordinates);
            enrichFlowPlanService.rebuildFlowsForPlugin(pluginCoordinates);
            egressFlowPlanService.rebuildFlowsForPlugin(pluginCoordinates);
        }
        return updated;
    }

    @DgsQuery
    @NeedsPermission.FlowView
    public IngressFlow getIngressFlow(String flowName) {
        return ingressFlowService.getFlowOrThrow(flowName);
    }

    @DgsQuery
    @NeedsPermission.FlowView
    public EnrichFlow getEnrichFlow(String flowName) {
        return enrichFlowService.getFlowOrThrow(flowName);
    }

    @DgsQuery
    @NeedsPermission.FlowView
    public EgressFlow getEgressFlow(String flowName) {
        return egressFlowService.getFlowOrThrow(flowName);
    }

    @DgsQuery
    @NeedsPermission.FlowValidate
    public IngressFlow validateIngressFlow(String flowName) {
        return ingressFlowService.validateAndSaveFlow(flowName);
    }

    @DgsQuery
    @NeedsPermission.FlowValidate
    public EnrichFlow validateEnrichFlow(String flowName) {
        return enrichFlowService.validateAndSaveFlow(flowName);
    }

    @DgsQuery
    @NeedsPermission.FlowValidate
    public EgressFlow validateEgressFlow(String flowName) {
        return egressFlowService.validateAndSaveFlow(flowName);
    }

    @DgsQuery
    @NeedsPermission.FlowView
    public IngressFlowPlan getIngressFlowPlan(String planName) {
        return ingressFlowPlanService.getPlanByName(planName);
    }

    @DgsQuery
    @NeedsPermission.FlowView
    public EnrichFlowPlan getEnrichFlowPlan(String planName) {
        return enrichFlowPlanService.getPlanByName(planName);
    }

    @DgsQuery
    @NeedsPermission.FlowView
    public EgressFlowPlan getEgressFlowPlan(String planName) {
        return egressFlowPlanService.getPlanByName(planName);
    }

    @DgsQuery
    @NeedsPermission.FlowView
    public List<DeltaFiConfiguration> deltaFiConfigs(@InputArgument ConfigQueryInput configQuery) {
        List<DeltaFiConfiguration> matchingConfigs = ingressFlowService.getConfigs(configQuery);
        matchingConfigs.addAll(enrichFlowService.getConfigs(configQuery));
        matchingConfigs.addAll(egressFlowService.getConfigs(configQuery));

        return matchingConfigs;
    }

    @DgsQuery
    @NeedsPermission.FlowView
    public String exportConfigAsYaml() {
        Map<String, List<? extends Flow>> flowMap = new HashMap<>();
        flowMap.put("ingressFlows", ingressFlowService.getAll());
        flowMap.put("enrichFlows", enrichFlowService.getAll());
        flowMap.put("egressFlows", egressFlowService.getAll());

        return YAML_EXPORTER.dumpAsMap(flowMap);
    }

    @DgsQuery
    @NeedsPermission.FlowView
    public SystemFlows getRunningFlows() {
        return SystemFlows.newBuilder()
                .ingress(ingressFlowService.getRunningFlows())
                .enrich(enrichFlowService.getRunningFlows())
                .egress(egressFlowService.getRunningFlows()).build();
    }

    @DgsQuery
    @NeedsPermission.FlowView
    public SystemFlows getAllFlows() {
        return SystemFlows.newBuilder()
                .ingress(ingressFlowService.getAll())
                .enrich(enrichFlowService.getAll())
                .egress(egressFlowService.getAll()).build();
    }

    @DgsQuery
    @NeedsPermission.FlowView
    public SystemFlowPlans getAllFlowPlans() {
        return SystemFlowPlans.newBuilder()
                .ingressPlans(ingressFlowPlanService.getAll())
                .enrichPlans(enrichFlowPlanService.getAll())
                .egressPlans(egressFlowPlanService.getAll()).build();
    }

    @DgsQuery
    @NeedsPermission.FlowView
    public Collection<Flows> getFlows() {
        return pluginRegistryService.getFlowsByPlugin();
    }

    @DgsQuery
    @NeedsPermission.FlowView
    public Collection<ActionFamily> getActionNamesByFamily() {
        EnumMap<ActionType, ActionFamily> actionFamilyMap = new EnumMap<>(ActionType.class);
        actionFamilyMap.put(ActionType.INGRESS, INGRESS_FAMILY);
        ingressFlowService.getAll().forEach(flow -> flow.updateActionNamesByFamily(actionFamilyMap));
        enrichFlowService.getAll().forEach(flow -> flow.updateActionNamesByFamily(actionFamilyMap));
        egressFlowService.getAll().forEach(flow -> flow.updateActionNamesByFamily(actionFamilyMap));
        return actionFamilyMap.values();
    }
}
