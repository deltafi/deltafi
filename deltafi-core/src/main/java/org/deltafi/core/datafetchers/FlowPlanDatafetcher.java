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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.common.types.*;
import org.deltafi.core.generated.types.*;
import org.deltafi.core.plugin.PluginRegistryService;
import org.deltafi.core.plugin.SystemPluginService;
import org.deltafi.core.security.NeedsPermission;
import org.deltafi.core.services.*;
import org.deltafi.core.snapshot.types.FlowSnapshot;
import org.deltafi.core.types.*;
import org.deltafi.core.types.DataSource;

import java.util.*;

@DgsComponent
@RequiredArgsConstructor
@Slf4j
public class FlowPlanDatafetcher {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    public static final ActionFamily INGRESS_FAMILY = ActionFamily.newBuilder().family("INGRESS").actionNames(List.of(DeltaFiConstants.INGRESS_ACTION)).build();

    private static final ObjectMapper YAML_EXPORTER = new ObjectMapper(new YAMLFactory()).registerModule(new JavaTimeModule());

    private final EgressFlowPlanService egressFlowPlanService;
    private final EgressFlowService egressFlowService;
    private final DataSourcePlanService dataSourcePlanService;
    private final DataSourceService dataSourceService;
    private final TransformFlowPlanService transformFlowPlanService;
    private final TransformFlowService transformFlowService;
    private final AnnotationService annotationService;
    private final PluginVariableService pluginVariableService;
    private final PluginRegistryService pluginRegistryService;
    private final SystemPluginService systemPluginService;

    @DgsMutation
    @NeedsPermission.FlowUpdate
    public boolean setMaxErrors(@InputArgument String flowName, @InputArgument Integer maxErrors) {
        if (transformFlowService.hasFlow(flowName)) {
            return transformFlowService.setMaxErrors(flowName, maxErrors);
        } else {
            throw new DgsEntityNotFoundException("No normalize or transform flow exists with the name: " + flowName);
        }
    }

    @DgsMutation
    @NeedsPermission.FlowUpdate
    public boolean setEgressFlowExpectedAnnotations(@InputArgument String flowName, @InputArgument Set<String> expectedAnnotations) {
        return annotationService.setExpectedAnnotations(flowName, expectedAnnotations);
    }

    @DgsMutation
    @NeedsPermission.FlowUpdate
    public boolean enableEgressTestMode(@InputArgument String flowName) { return egressFlowService.enableTestMode(flowName); }

    @DgsMutation
    @NeedsPermission.FlowUpdate
    public boolean disableEgressTestMode(@InputArgument String flowName) { return egressFlowService.disableTestMode(flowName); }

    @DgsMutation
    @NeedsPermission.Admin
    public boolean setTimedDataSourceMemo(@InputArgument String name, String memo) {
        return dataSourceService.setMemo(name, memo);
    }

    @DgsMutation
    @NeedsPermission.FlowUpdate
    public boolean setTimedDataSourceCronSchedule(@InputArgument String name, String cronSchedule) {
        return dataSourceService.setCronSchedule(name, cronSchedule);
    }

    @DgsMutation
    @NeedsPermission.FlowPlanCreate
    public EgressFlow saveEgressFlowPlan(@InputArgument EgressFlowPlanInput egressFlowPlan) {
        return saveFlowPlan(egressFlowPlanService, egressFlowPlan, EgressFlowPlan.class);
    }

    @DgsMutation
    @NeedsPermission.FlowPlanDelete
    public boolean removeEgressFlowPlan(@InputArgument String name) {
        return removeFlowAndFlowPlan(egressFlowPlanService,name);
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
    public DataSource saveTimedDataSourcePlan(@InputArgument TimedDataSourcePlanInput dataSourcePlan) {
        return saveFlowPlan(dataSourcePlanService, dataSourcePlan, DataSourcePlan.class);
    }

    @DgsMutation
    @NeedsPermission.FlowPlanCreate
    public DataSource saveRestDataSourcePlan(@InputArgument RestDataSourcePlanInput dataSourcePlan) {
        return saveFlowPlan(dataSourcePlanService, dataSourcePlan, DataSourcePlan.class);
    }

    @DgsMutation
    @NeedsPermission.FlowPlanDelete
    public boolean removeDataSourcePlan(@InputArgument String name) {
        return removeFlowAndFlowPlan(dataSourcePlanService, name);
    }

    @DgsMutation
    @NeedsPermission.FlowStart
    public boolean startDataSource(@InputArgument String name) {
        return dataSourceService.startFlow(name);
    }

    @DgsMutation
    @NeedsPermission.FlowStop
    public boolean stopDataSource(@InputArgument String name) {
        return dataSourceService.stopFlow(name);
    }

    @DgsMutation
    @NeedsPermission.FlowUpdate
    public boolean enableDataSourceTestMode(@InputArgument String name) { return dataSourceService.enableTestMode(name); }

    @DgsMutation
    @NeedsPermission.FlowUpdate
    public boolean disableDataSourceTestMode(@InputArgument String name) { return dataSourceService.disableTestMode(name); }

    @DgsMutation
    @NeedsPermission.FlowPlanCreate
    public TransformFlow saveTransformFlowPlan(@InputArgument TransformFlowPlanInput transformFlowPlan) {
        return saveFlowPlan(transformFlowPlanService, transformFlowPlan, TransformFlowPlan.class);
    }

    @DgsMutation
    @NeedsPermission.FlowPlanDelete
    public boolean removeTransformFlowPlan(@InputArgument String name) {
        return removeFlowAndFlowPlan(transformFlowPlanService, name);
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
    public boolean savePluginVariables(@InputArgument List<Variable> variables) {
        pluginVariableService.validateAndSaveVariables(systemPluginService.getSystemPluginCoordinates(), variables);
        return true;
    }

    @DgsMutation
    @NeedsPermission.PluginVariableUpdate
    public boolean removePluginVariables() {
        pluginVariableService.removeVariables(systemPluginService.getSystemPluginCoordinates());
        return true;
    }

    @DgsMutation
    @NeedsPermission.PluginVariableUpdate
    public boolean setPluginVariableValues(@InputArgument PluginCoordinates pluginCoordinates, @InputArgument List<KeyValue> variables) {
        boolean updated = pluginVariableService.setVariableValues(pluginCoordinates, variables);
        if (updated) {
            egressFlowPlanService.rebuildFlowsForPlugin(pluginCoordinates);
            transformFlowPlanService.rebuildFlowsForPlugin(pluginCoordinates);
            dataSourcePlanService.rebuildFlowsForPlugin(pluginCoordinates);
        }
        return updated;
    }

    @DgsQuery
    @NeedsPermission.FlowView
    public EgressFlow getEgressFlow(@InputArgument String flowName) {
        return egressFlowService.getFlowOrThrow(flowName);
    }

    @DgsQuery
    @NeedsPermission.FlowView
    public DataSource getDataSource(@InputArgument String name) {
        return dataSourceService.getFlowOrThrow(name);
    }

    @DgsQuery
    @NeedsPermission.FlowView
    public TransformFlow getTransformFlow(@InputArgument String flowName) {
        return transformFlowService.getFlowOrThrow(flowName);
    }

    @DgsQuery
    @NeedsPermission.FlowValidate
    public EgressFlow validateEgressFlow(@InputArgument String flowName) {
        return egressFlowService.validateAndSaveFlow(flowName);
    }

    @DgsQuery
    @NeedsPermission.FlowValidate
    public DataSource validateDataSource(@InputArgument String name) {
        return dataSourceService.validateAndSaveFlow(name);
    }

    @DgsQuery
    @NeedsPermission.FlowValidate
    public TransformFlow validateTransformFlow(@InputArgument String flowName) {
        return transformFlowService.validateAndSaveFlow(flowName);
    }

    @DgsQuery
    @NeedsPermission.FlowView
    public EgressFlowPlan getEgressFlowPlan(@InputArgument String planName) {
        return egressFlowPlanService.getPlanByName(planName);
    }

    @DgsQuery
    @NeedsPermission.FlowView
    public DataSourcePlan getDataSourcePlan(@InputArgument String planName) {
        return dataSourcePlanService.getPlanByName(planName);
    }

    @DgsQuery
    @NeedsPermission.FlowView
    public TransformFlowPlan getTransformFlowPlan(@InputArgument String planName) {
        return transformFlowPlanService.getPlanByName(planName);
    }

    @DgsQuery
    @NeedsPermission.FlowView
    public List<DeltaFiConfiguration> deltaFiConfigs(@InputArgument ConfigQueryInput configQuery) {
        List<DeltaFiConfiguration> matchingConfigs = new ArrayList<>(egressFlowService.getConfigs(configQuery));
        matchingConfigs.addAll(transformFlowService.getConfigs(configQuery));
        matchingConfigs.addAll(dataSourceService.getConfigs(configQuery));

        return matchingConfigs;
    }

    @DgsQuery
    @NeedsPermission.FlowView
    public String exportConfigAsYaml() throws JsonProcessingException {
        Map<String, List<? extends Flow>> flowMap = new HashMap<>();
        flowMap.put("egressFlows", egressFlowService.getAll());
        flowMap.put("transformFlows", transformFlowService.getAll());
        flowMap.put("dataSources", dataSourceService.getAll());

        return YAML_EXPORTER.writeValueAsString(flowMap);
    }

    @DgsQuery
    @NeedsPermission.FlowView
    public SystemFlows getRunningFlows() {
        return SystemFlows.newBuilder()
                .egress(egressFlowService.getRunningFlows())
                .transform(transformFlowService.getRunningFlows())
                .dataSource(dataSourceService.getRunningFlows()).build();
    }

    @DgsQuery
    @NeedsPermission.UIAccess
    public FlowNames getFlowNames(@InputArgument FlowState state) {
        return FlowNames.newBuilder()
                .egress(egressFlowService.getFlowNamesByState(state))
                .transform(transformFlowService.getFlowNamesByState(state))
                .dataSource(dataSourceService.getFlowNamesByState(state)).build();
    }

    @DgsQuery
    @NeedsPermission.FlowView
    public SystemFlows getAllFlows() {
        return SystemFlows.newBuilder()
                .egress(egressFlowService.getAllUncached())
                .transform(transformFlowService.getAllUncached())
                .dataSource(dataSourceService.getAllUncached()).build();
    }

    @DgsQuery
    @NeedsPermission.FlowView
    public SystemFlowPlans getAllFlowPlans() {
        return SystemFlowPlans.newBuilder()
                .egressPlans(egressFlowPlanService.getAll())
                .transformPlans(transformFlowPlanService.getAll())
                .dataSources(dataSourcePlanService.getAll()).build();
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
        egressFlowService.getAll().forEach(flow -> flow.updateActionNamesByFamily(actionFamilyMap));
        transformFlowService.getAll().forEach(flow -> flow.updateActionNamesByFamily(actionFamilyMap));
        dataSourceService.getAll().forEach(flow -> flow.updateActionNamesByFamily(actionFamilyMap));
        return actionFamilyMap.values();
    }

    @DgsQuery
    @NeedsPermission.FlowView
    public List<IngressFlowErrorState> ingressFlowErrorsExceeded() {
        return transformFlowService.ingressFlowErrorsExceeded();
    }

    private boolean removeFlowAndFlowPlan(FlowPlanService<?, ?, ?> flowPlanService, String flowPlanName) {
        return flowPlanService.removePlan(flowPlanName, systemPluginService.getSystemPluginCoordinates());
    }

    private <T extends FlowPlan, R extends Flow, S extends FlowSnapshot> R saveFlowPlan(FlowPlanService<T, R, S> flowPlanService, Object input, Class<T> clazz) {
        T flowPlan = OBJECT_MAPPER.convertValue(input, clazz);
        flowPlan.setSourcePlugin(systemPluginService.getSystemPluginCoordinates());
        return flowPlanService.saveFlowPlan(flowPlan);
    }
}
