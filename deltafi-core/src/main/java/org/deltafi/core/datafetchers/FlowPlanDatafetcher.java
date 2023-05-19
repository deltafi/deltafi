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
package org.deltafi.core.datafetchers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.common.types.*;
import org.deltafi.core.converters.YamlRepresenter;
import org.deltafi.core.generated.types.*;
import org.deltafi.core.plugin.PluginRegistryService;
import org.deltafi.core.security.NeedsPermission;
import org.deltafi.core.services.*;
import org.deltafi.core.types.*;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.util.*;

@DgsComponent
@RequiredArgsConstructor
@Slf4j
public class FlowPlanDatafetcher {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static final ActionFamily INGRESS_FAMILY = ActionFamily.newBuilder().family("INGRESS").actionNames(List.of(DeltaFiConstants.INGRESS_ACTION)).build();
    private static final LoaderOptions LOADER_OPTIONS;
    static {
        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setAllowRecursiveKeys(false);
        LOADER_OPTIONS = loaderOptions;
    }
    private static final Yaml YAML_EXPORTER = new Yaml(new SafeConstructor(LOADER_OPTIONS), new YamlRepresenter());

    private final IngressFlowPlanService ingressFlowPlanService;
    private final IngressFlowService ingressFlowService;
    private final EgressFlowPlanService egressFlowPlanService;
    private final EgressFlowService egressFlowService;
    private final EnrichFlowService enrichFlowService;
    private final EnrichFlowPlanService enrichFlowPlanService;
    private final TransformFlowPlanService transformFlowPlanService;
    private final TransformFlowService transformFlowService;
    private final AnnotationService annotationService;
    private final PluginVariableService pluginVariableService;
    private final PluginRegistryService pluginRegistryService;

    @DgsMutation
    @NeedsPermission.FlowPlanCreate
    public IngressFlow saveIngressFlowPlan(@InputArgument IngressFlowPlanInput ingressFlowPlan) {
        return ingressFlowPlanService.saveFlowPlan(OBJECT_MAPPER.convertValue(ingressFlowPlan, IngressFlowPlan.class));
    }

    @DgsMutation
    @NeedsPermission.FlowPlanDelete
    public boolean removeIngressFlowPlan(@InputArgument String name) {
        return ingressFlowPlanService.removePlan(name);
    }

    @DgsMutation
    @NeedsPermission.FlowStart
    public boolean startIngressFlow(@InputArgument String flowName) {
        return ingressFlowService.startFlow(flowName);
    }

    @DgsMutation
    @NeedsPermission.FlowStop
    public boolean stopIngressFlow(@InputArgument String flowName) {
        return ingressFlowService.stopFlow(flowName);
    }

    @DgsMutation
    @NeedsPermission.FlowUpdate
    public boolean setMaxErrors(@InputArgument String flowName, @InputArgument Integer maxErrors) {
        if (ingressFlowService.hasFlow(flowName)) {
            return ingressFlowService.setMaxErrors(flowName, maxErrors);
        } else if (transformFlowService.hasFlow(flowName)) {
            return transformFlowService.setMaxErrors(flowName, maxErrors);
        } else {
            throw new DgsEntityNotFoundException("No ingress or transform flow exists with the name: " + flowName);
        }
    }

    @DgsMutation
    @NeedsPermission.FlowUpdate
    public boolean setTransformFlowExpectedAnnotations(@InputArgument String flowName, @InputArgument Set<String> expectedAnnotations) {
        return annotationService.setExpectedAnnotations(FlowType.TRANSFORM, flowName, expectedAnnotations);
    }

    @DgsMutation
    @NeedsPermission.FlowUpdate
    public boolean setEgressFlowExpectedAnnotations(@InputArgument String flowName, @InputArgument Set<String> expectedAnnotations) {
        return annotationService.setExpectedAnnotations(FlowType.EGRESS, flowName, expectedAnnotations);
    }

    @DgsMutation
    @NeedsPermission.FlowUpdate
    public boolean enableIngressTestMode(@InputArgument String flowName) { return ingressFlowService.enableTestMode(flowName); }

    @DgsMutation
    @NeedsPermission.FlowUpdate
    public boolean disableIngressTestMode(@InputArgument String flowName) { return ingressFlowService.disableTestMode(flowName); }

    @DgsMutation
    @NeedsPermission.FlowUpdate
    public boolean enableEgressTestMode(@InputArgument String flowName) { return egressFlowService.enableTestMode(flowName); }

    @DgsMutation
    @NeedsPermission.FlowUpdate
    public boolean disableEgressTestMode(@InputArgument String flowName) { return egressFlowService.disableTestMode(flowName); }

    @DgsMutation
    @NeedsPermission.FlowPlanCreate
    public EnrichFlow saveEnrichFlowPlan(@InputArgument EnrichFlowPlanInput enrichFlowPlan) {
        return enrichFlowPlanService.saveFlowPlan(OBJECT_MAPPER.convertValue(enrichFlowPlan, EnrichFlowPlan.class));
    }

    @DgsMutation
    @NeedsPermission.FlowPlanDelete
    public boolean removeEnrichFlowPlan(@InputArgument String name) {
        return enrichFlowPlanService.removePlan(name);
    }

    @DgsMutation
    @NeedsPermission.FlowStart
    public boolean startEnrichFlow(@InputArgument String flowName) {
        return enrichFlowService.startFlow(flowName);
    }

    @DgsMutation
    @NeedsPermission.FlowStop
    public boolean stopEnrichFlow(@InputArgument String flowName) {
        return enrichFlowService.stopFlow(flowName);
    }

    @DgsMutation
    @NeedsPermission.FlowPlanCreate
    public EgressFlow saveEgressFlowPlan(@InputArgument EgressFlowPlanInput egressFlowPlan) {
        return egressFlowPlanService.saveFlowPlan(OBJECT_MAPPER.convertValue(egressFlowPlan, EgressFlowPlan.class));
    }

    @DgsMutation
    @NeedsPermission.FlowPlanDelete
    public boolean removeEgressFlowPlan(@InputArgument String name) {
        return egressFlowPlanService.removePlan(name);
    }

    @DgsMutation
    @NeedsPermission.FlowStart
    public boolean startEgressFlow(@InputArgument String flowName) {
        return egressFlowService.startFlow(flowName);
    }

    @DgsMutation
    @NeedsPermission.FlowStop
    public boolean stopEgressFlow(@InputArgument String flowName) {
        return egressFlowService.stopFlow(flowName);
    }

    @DgsMutation
    @NeedsPermission.FlowPlanCreate
    public TransformFlow saveTransformFlowPlan(@InputArgument TransformFlowPlanInput transformFlowPlan) {
        return transformFlowPlanService.saveFlowPlan(OBJECT_MAPPER.convertValue(transformFlowPlan, TransformFlowPlan.class));
    }

    @DgsMutation
    @NeedsPermission.FlowPlanDelete
    public boolean removeTransformFlowPlan(@InputArgument String name) {
        return transformFlowPlanService.removePlan(name);
    }

    @DgsMutation
    @NeedsPermission.FlowStart
    public boolean startTransformFlow(@InputArgument String flowName) {
        return transformFlowService.startFlow(flowName);
    }

    @DgsMutation
    @NeedsPermission.FlowStop
    public boolean stopTransformFlow(@InputArgument String flowName) {
        return transformFlowService.stopFlow(flowName);
    }

    @DgsMutation
    @NeedsPermission.FlowUpdate
    public boolean enableTransformTestMode(@InputArgument String flowName) { return transformFlowService.enableTestMode(flowName); }

    @DgsMutation
    @NeedsPermission.FlowUpdate
    public boolean disableTransformTestMode(@InputArgument String flowName) { return transformFlowService.disableTestMode(flowName); }

    @DgsMutation
    @NeedsPermission.PluginVariableUpdate
    public boolean savePluginVariables(@InputArgument PluginVariablesInput pluginVariablesInput) {
        pluginVariableService.validateAndSaveVariables(pluginVariablesInput.getSourcePlugin(), pluginVariablesInput.getVariables());
        return true;
    }

    @DgsMutation
    @NeedsPermission.PluginVariableUpdate
    public boolean setPluginVariableValues(@InputArgument PluginCoordinates pluginCoordinates, @InputArgument List<KeyValue> variables) {
        boolean updated = pluginVariableService.setVariableValues(pluginCoordinates, variables);
        if (updated) {
            ingressFlowPlanService.rebuildFlowsForPlugin(pluginCoordinates);
            enrichFlowPlanService.rebuildFlowsForPlugin(pluginCoordinates);
            egressFlowPlanService.rebuildFlowsForPlugin(pluginCoordinates);
            transformFlowPlanService.rebuildFlowsForPlugin(pluginCoordinates);
        }
        return updated;
    }

    @DgsQuery
    @NeedsPermission.FlowView
    public IngressFlow getIngressFlow(@InputArgument String flowName) {
        return ingressFlowService.getFlowOrThrow(flowName);
    }

    @DgsQuery
    @NeedsPermission.FlowView
    public EnrichFlow getEnrichFlow(@InputArgument String flowName) {
        return enrichFlowService.getFlowOrThrow(flowName);
    }

    @DgsQuery
    @NeedsPermission.FlowView
    public EgressFlow getEgressFlow(@InputArgument String flowName) {
        return egressFlowService.getFlowOrThrow(flowName);
    }

    @DgsQuery
    @NeedsPermission.FlowView
    public TransformFlow getTransformFlow(@InputArgument String flowName) {
        return transformFlowService.getFlowOrThrow(flowName);
    }

    @DgsQuery
    @NeedsPermission.FlowValidate
    public IngressFlow validateIngressFlow(@InputArgument String flowName) {
        return ingressFlowService.validateAndSaveFlow(flowName);
    }

    @DgsQuery
    @NeedsPermission.FlowValidate
    public EnrichFlow validateEnrichFlow(@InputArgument String flowName) {
        return enrichFlowService.validateAndSaveFlow(flowName);
    }

    @DgsQuery
    @NeedsPermission.FlowValidate
    public EgressFlow validateEgressFlow(@InputArgument String flowName) {
        return egressFlowService.validateAndSaveFlow(flowName);
    }

    @DgsQuery
    @NeedsPermission.FlowValidate
    public TransformFlow validateTransformFlow(@InputArgument String flowName) {
        return transformFlowService.validateAndSaveFlow(flowName);
    }

    @DgsQuery
    @NeedsPermission.FlowView
    public IngressFlowPlan getIngressFlowPlan(@InputArgument String planName) {
        return ingressFlowPlanService.getPlanByName(planName);
    }

    @DgsQuery
    @NeedsPermission.FlowView
    public EnrichFlowPlan getEnrichFlowPlan(@InputArgument String planName) {
        return enrichFlowPlanService.getPlanByName(planName);
    }

    @DgsQuery
    @NeedsPermission.FlowView
    public EgressFlowPlan getEgressFlowPlan(@InputArgument String planName) {
        return egressFlowPlanService.getPlanByName(planName);
    }

    @DgsQuery
    @NeedsPermission.FlowView
    public TransformFlowPlan getTransformFlowPlan(@InputArgument String planName) {
        return transformFlowPlanService.getPlanByName(planName);
    }

    @DgsQuery
    @NeedsPermission.FlowView
    public List<DeltaFiConfiguration> deltaFiConfigs(@InputArgument ConfigQueryInput configQuery) {
        List<DeltaFiConfiguration> matchingConfigs = new ArrayList<>(ingressFlowService.getConfigs(configQuery));
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
        flowMap.put("transformFlows", transformFlowService.getAll());

        return YAML_EXPORTER.dumpAsMap(flowMap);
    }

    @DgsQuery
    @NeedsPermission.FlowView
    public SystemFlows getRunningFlows() {
        return SystemFlows.newBuilder()
                .ingress(ingressFlowService.getRunningFlows())
                .enrich(enrichFlowService.getRunningFlows())
                .egress(egressFlowService.getRunningFlows())
                .transform(transformFlowService.getRunningFlows()).build();
    }

    @DgsQuery
    @NeedsPermission.UIAccess
    public FlowNames getFlowNames(@InputArgument FlowState state) {
        return FlowNames.newBuilder()
                .ingress(ingressFlowService.getFlowNamesByState(state))
                .enrich(enrichFlowService.getFlowNamesByState(state))
                .egress(egressFlowService.getFlowNamesByState(state))
                .transform(transformFlowService.getFlowNamesByState(state)).build();
    }

    @DgsQuery
    @NeedsPermission.FlowView
    public SystemFlows getAllFlows() {
        return SystemFlows.newBuilder()
                .ingress(ingressFlowService.getAll())
                .enrich(enrichFlowService.getAll())
                .egress(egressFlowService.getAll())
                .transform(transformFlowService.getAll()).build();
    }

    @DgsQuery
    @NeedsPermission.FlowView
    public SystemFlowPlans getAllFlowPlans() {
        return SystemFlowPlans.newBuilder()
                .ingressPlans(ingressFlowPlanService.getAll())
                .enrichPlans(enrichFlowPlanService.getAll())
                .egressPlans(egressFlowPlanService.getAll())
                .transformPlans(transformFlowPlanService.getAll()).build();
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
        transformFlowService.getAll().forEach(flow -> flow.updateActionNamesByFamily(actionFamilyMap));
        return actionFamilyMap.values();
    }

    @DgsQuery
    @NeedsPermission.FlowView
    public List<IngressFlowErrorState> ingressFlowErrorsExceeded() {
        return ingressFlowService.ingressFlowErrorsExceeded();
    }
}
