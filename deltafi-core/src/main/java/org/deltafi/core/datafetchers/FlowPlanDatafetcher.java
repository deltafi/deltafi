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
import org.deltafi.core.audit.CoreAuditLogger;
import org.deltafi.core.generated.types.*;
import org.deltafi.core.plugin.PluginRegistryService;
import org.deltafi.core.plugin.SystemPluginService;
import org.deltafi.core.repo.FlowRepo;
import org.deltafi.core.security.NeedsPermission;
import org.deltafi.core.services.*;
import org.deltafi.core.types.snapshot.FlowSnapshot;
import org.deltafi.core.types.*;

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
    private final RestDataSourcePlanService restDataSourcePlanService;
    private final TimedDataSourcePlanService timedDataSourcePlanService;
    private final RestDataSourceService restDataSourceService;
    private final TransformFlowPlanService transformFlowPlanService;
    private final TransformFlowService transformFlowService;
    private final AnnotationService annotationService;
    private final PluginVariableService pluginVariableService;
    private final PluginRegistryService pluginRegistryService;
    private final SystemPluginService systemPluginService;
    private final TimedDataSourceService timedDataSourceService;
    private final CoreAuditLogger auditLogger;
    private final FlowCacheService flowCacheService;

    @DgsMutation
    @NeedsPermission.FlowUpdate
    public boolean setRestDataSourceMaxErrors(@InputArgument String name, @InputArgument Integer maxErrors) {
        if (restDataSourceService.hasFlow(name)) {
            auditLogger.audit("set max errors to {} for data source {}", maxErrors, name);
            return restDataSourceService.setMaxErrors(name, maxErrors);
        } else {
            throw new DgsEntityNotFoundException("No RestDataSource exists with the name: " + name);
        }
    }

    @DgsMutation
    @NeedsPermission.FlowUpdate
    public boolean setTimedDataSourceMaxErrors(@InputArgument String name, @InputArgument Integer maxErrors) {
        if (timedDataSourceService.hasFlow(name)) {
            auditLogger.audit("set max errors to {} for data source {}", maxErrors, name);
            return timedDataSourceService.setMaxErrors(name, maxErrors);
        } else {
            throw new DgsEntityNotFoundException("No TimedDataSource exists with the name: " + name);
        }
    }

    @DgsMutation
    @NeedsPermission.FlowUpdate
    public boolean setEgressFlowExpectedAnnotations(@InputArgument String flowName, @InputArgument Set<String> expectedAnnotations) {
        auditLogger.audit("set expected annotations for flow {} to {}", flowName, expectedAnnotations);
        return annotationService.setExpectedAnnotations(flowName, expectedAnnotations);
    }

    @DgsMutation
    @NeedsPermission.FlowUpdate
    public boolean enableEgressTestMode(@InputArgument String flowName) {
        auditLogger.audit("enabled egress test mode for flow {}", flowName);
        return egressFlowService.enableTestMode(flowName);
    }

    @DgsMutation
    @NeedsPermission.FlowUpdate
    public boolean disableEgressTestMode(@InputArgument String flowName) {
        auditLogger.audit("disabled egress test mode for flow {}", flowName);
        return egressFlowService.disableTestMode(flowName);
    }

    @DgsMutation
    @NeedsPermission.Admin
    public boolean setTimedDataSourceMemo(@InputArgument String name, String memo) {
        auditLogger.audit("set timed source memo for flow {} to {}", name, memo);
        return timedDataSourceService.setMemo(name, memo);
    }

    @DgsMutation
    @NeedsPermission.FlowUpdate
    public boolean setTimedDataSourceCronSchedule(@InputArgument String name, String cronSchedule) {
        auditLogger.audit("set timed source cron schedule for flow {} to {}", name, cronSchedule);
        return timedDataSourceService.setCronSchedule(name, cronSchedule);
    }

    @DgsMutation
    @NeedsPermission.FlowPlanCreate
    public EgressFlow saveEgressFlowPlan(@InputArgument EgressFlowPlanInput egressFlowPlan) {
        auditLogger.audit("saved egress flow plan {}", egressFlowPlan.getName());
        return saveFlowPlan(egressFlowPlanService, egressFlowPlan, EgressFlowPlanEntity.class);
    }

    @DgsMutation
    @NeedsPermission.FlowPlanDelete
    public boolean removeEgressFlowPlan(@InputArgument String name) {
        auditLogger.audit("removed egress flow plan {}", name);
        return removeFlowAndFlowPlan(egressFlowPlanService,name);
    }

    @DgsMutation
    @NeedsPermission.FlowStart
    public boolean startEgressFlow(@InputArgument String flowName) {
        auditLogger.audit("started egress flow {}", flowName);
        return egressFlowService.startFlow(flowName);
    }

    @DgsMutation
    @NeedsPermission.FlowStop
    public boolean stopEgressFlow(@InputArgument String flowName) {
        auditLogger.audit("stopped egress flow {}", flowName);
        return egressFlowService.stopFlow(flowName);
    }

    @DgsMutation
    @NeedsPermission.FlowPlanCreate
    public DataSource saveTimedDataSourcePlan(@InputArgument TimedDataSourcePlanInput dataSourcePlan) {
        auditLogger.audit("saved timed source plan {}", dataSourcePlan.getName());
        return saveFlowPlan(timedDataSourcePlanService, dataSourcePlan, TimedDataSourcePlanEntity.class);
    }

    @DgsMutation
    @NeedsPermission.FlowPlanCreate
    public DataSource saveRestDataSourcePlan(@InputArgument RestDataSourcePlanInput dataSourcePlan) {
        auditLogger.audit("saved rest source plan {}", dataSourcePlan.getName());
        return saveFlowPlan(restDataSourcePlanService, dataSourcePlan, RestDataSourcePlanEntity.class);
    }

    @DgsMutation
    @NeedsPermission.FlowPlanDelete
    public boolean removeRestDataSourcePlan(@InputArgument String name) {
        auditLogger.audit("removed restDataSource plan {}", name);
        return removeFlowAndFlowPlan(restDataSourcePlanService, name);
    }

    @DgsMutation
    @NeedsPermission.FlowPlanDelete
    public boolean removeTimedDataSourcePlan(@InputArgument String name) {
        auditLogger.audit("removed timedDataSource plan {}", name);
        return removeFlowAndFlowPlan(timedDataSourcePlanService, name);
    }

    @DgsMutation
    @NeedsPermission.FlowStart
    public boolean startRestDataSource(@InputArgument String name) {
        auditLogger.audit("started restDataSource {}", name);
        return restDataSourceService.startFlow(name);
    }

    @DgsMutation
    @NeedsPermission.FlowStart
    public boolean startTimedDataSource(@InputArgument String name) {
        auditLogger.audit("started timedDataSource {}", name);
        return timedDataSourceService.startFlow(name);
    }

    @DgsMutation
    @NeedsPermission.FlowStop
    public boolean stopRestDataSource(@InputArgument String name) {
        auditLogger.audit("stopped restDataSource {}", name);
        return restDataSourceService.stopFlow(name);
    }

    @DgsMutation
    @NeedsPermission.FlowStop
    public boolean stopTimedDataSource(@InputArgument String name) {
        auditLogger.audit("stopped timedDataSource {}", name);
        return timedDataSourceService.stopFlow(name);
    }

    @DgsMutation
    @NeedsPermission.FlowUpdate
    public boolean enableRestDataSourceTestMode(@InputArgument String name) {
        auditLogger.audit("enabled test mode for restDataSource {}", name);
        return restDataSourceService.enableTestMode(name);
    }

    @DgsMutation
    @NeedsPermission.FlowUpdate
    public boolean enableTimedDataSourceTestMode(@InputArgument String name) {
        auditLogger.audit("enabled test mode for timedDataSource {}", name);
        return timedDataSourceService.enableTestMode(name);
    }

    @DgsMutation
    @NeedsPermission.FlowUpdate
    public boolean disableRestDataSourceTestMode(@InputArgument String name) {
        auditLogger.audit("disabled test mode for restDataSource {}", name);
        return restDataSourceService.disableTestMode(name);
    }

    @DgsMutation
    @NeedsPermission.FlowUpdate
    public boolean disableTimedDataSourceTestMode(@InputArgument String name) {
        auditLogger.audit("disabled test mode for timedDataSource {}", name);
        return timedDataSourceService.disableTestMode(name);
    }

    @DgsMutation
    @NeedsPermission.FlowPlanCreate
    public TransformFlow saveTransformFlowPlan(@InputArgument TransformFlowPlanInput transformFlowPlan) {
        auditLogger.audit("saved transform flow plan {}", transformFlowPlan.getName());
        return saveFlowPlan(transformFlowPlanService, transformFlowPlan, TransformFlowPlanEntity.class);
    }

    @DgsMutation
    @NeedsPermission.FlowPlanDelete
    public boolean removeTransformFlowPlan(@InputArgument String name) {
        auditLogger.audit("removed transform flow plan {}", name);
        return removeFlowAndFlowPlan(transformFlowPlanService, name);
    }

    @DgsMutation
    @NeedsPermission.FlowStart
    public boolean startTransformFlow(@InputArgument String flowName) {
        auditLogger.audit("started transform flow {}", flowName);
        return transformFlowService.startFlow(flowName);
    }

    @DgsMutation
    @NeedsPermission.FlowStop
    public boolean stopTransformFlow(@InputArgument String flowName) {
        auditLogger.audit("stopped transform flow {}", flowName);
        return transformFlowService.stopFlow(flowName);
    }

    @DgsMutation
    @NeedsPermission.FlowUpdate
    public boolean enableTransformTestMode(@InputArgument String flowName) {
        auditLogger.audit("enabled test mode for transform flow {}", flowName);
        return transformFlowService.enableTestMode(flowName);
    }

    @DgsMutation
    @NeedsPermission.FlowUpdate
    public boolean disableTransformTestMode(@InputArgument String flowName) {
        auditLogger.audit("disabled test mode for transform flow {}", flowName);
        return transformFlowService.disableTestMode(flowName);
    }

    @DgsMutation
    @NeedsPermission.PluginVariableUpdate
    public boolean savePluginVariables(@InputArgument List<Variable> variables) {
        auditLogger.audit("saved plugin variables {}", CoreAuditLogger.listToString(variables, Variable::getName));
        pluginVariableService.validateAndSaveVariables(systemPluginService.getSystemPluginCoordinates(), variables);
        return true;
    }

    @DgsMutation
    @NeedsPermission.PluginVariableUpdate
    public boolean removePluginVariables() {
        auditLogger.audit("removed system plugin variables");
        pluginVariableService.removeVariables(systemPluginService.getSystemPluginCoordinates());
        return true;
    }

    @DgsMutation
    @NeedsPermission.PluginVariableUpdate
    public boolean setPluginVariableValues(@InputArgument PluginCoordinates pluginCoordinates, @InputArgument List<KeyValue> variables) {
        auditLogger.audit("updated plugin variables: {}", CoreAuditLogger.listToString(variables));
        boolean updated = pluginVariableService.setVariableValues(pluginCoordinates, variables);
        if (updated) {
            egressFlowPlanService.rebuildFlowsForPlugin(pluginCoordinates);
            transformFlowPlanService.rebuildFlowsForPlugin(pluginCoordinates);
            restDataSourcePlanService.rebuildFlowsForPlugin(pluginCoordinates);
            timedDataSourcePlanService.rebuildFlowsForPlugin(pluginCoordinates);
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
    public RestDataSource getRestDataSource(@InputArgument String name) {
        return restDataSourceService.getFlowOrThrow(name);
    }

    @DgsQuery
    @NeedsPermission.FlowView
    public TimedDataSource getTimedDataSource(@InputArgument String name) {
        return timedDataSourceService.getFlowOrThrow(name);
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
    public RestDataSource validateRestDataSource(@InputArgument String name) {
        return restDataSourceService.validateAndSaveFlow(name);
    }

    @DgsQuery
    @NeedsPermission.FlowValidate
    public TimedDataSource validateTimedDataSource(@InputArgument String name) {
        return timedDataSourceService.validateAndSaveFlow(name);
    }

    @DgsQuery
    @NeedsPermission.FlowValidate
    public TransformFlow validateTransformFlow(@InputArgument String flowName) {
        return transformFlowService.validateAndSaveFlow(flowName);
    }

    @DgsQuery
    @NeedsPermission.FlowView
    public EgressFlowPlanEntity getEgressFlowPlan(@InputArgument String planName) {
        return egressFlowPlanService.getPlanByName(planName);
    }

    @DgsQuery
    @NeedsPermission.FlowView
    public RestDataSourcePlanEntity getRestDataSourcePlan(@InputArgument String planName) {
        return restDataSourcePlanService.getPlanByName(planName);
    }

    @DgsQuery
    @NeedsPermission.FlowView
    public TimedDataSourcePlanEntity getTimedDataSourcePlan(@InputArgument String planName) {
        return timedDataSourcePlanService.getPlanByName(planName);
    }

    @DgsQuery
    @NeedsPermission.FlowView
    public TransformFlowPlanEntity getTransformFlowPlan(@InputArgument String planName) {
        return transformFlowPlanService.getPlanByName(planName);
    }

    @DgsQuery
    @NeedsPermission.FlowView
    public String exportConfigAsYaml() throws JsonProcessingException {
        Map<String, List<? extends Flow>> flowMap = new HashMap<>();
        flowMap.put("egressFlows", egressFlowService.getAll());
        flowMap.put("transformFlows", transformFlowService.getAll());
        flowMap.put("dataSources", restDataSourceService.getAll());

        return YAML_EXPORTER.writeValueAsString(flowMap);
    }

    @DgsQuery
    @NeedsPermission.FlowView
    public SystemFlows getRunningFlows() {
        return SystemFlows.newBuilder()
                .egress(egressFlowService.getRunningFlows())
                .transform(transformFlowService.getRunningFlows())
                .restDataSource(restDataSourceService.getRunningFlows())
                .timedDataSource(timedDataSourceService.getRunningFlows()).build();
    }

    @DgsQuery
    @NeedsPermission.UIAccess
    public FlowNames getFlowNames(@InputArgument FlowState state) {
        return FlowNames.newBuilder()
                .egress(egressFlowService.getFlowNamesByState(state))
                .transform(transformFlowService.getFlowNamesByState(state))
                .restDataSource(restDataSourceService.getFlowNamesByState(state))
                .timedDataSource(timedDataSourceService.getFlowNamesByState(state)).build();
    }

    @DgsQuery
    @NeedsPermission.FlowView
    public SystemFlows getAllFlows() {
        flowCacheService.refreshCache();
        return SystemFlows.newBuilder()
                .egress(egressFlowService.getAll())
                .transform(transformFlowService.getAll())
                .restDataSource(restDataSourceService.getAll())
                .timedDataSource(timedDataSourceService.getAll()).build();
    }

    @DgsQuery
    @NeedsPermission.FlowView
    public SystemFlowPlans getAllFlowPlans() {
        return SystemFlowPlans.newBuilder()
                .egressPlans(egressFlowPlanService.getAll().stream().map(f -> (EgressFlowPlan) f.toFlowPlan()).toList())
                .transformPlans(transformFlowPlanService.getAll().stream().map(f -> (TransformFlowPlan) f.toFlowPlan()).toList())
                .restDataSources(restDataSourcePlanService.getAll().stream().map(f -> (RestDataSourcePlan) f.toFlowPlan()).toList())
                .timedDataSources(timedDataSourcePlanService.getAll().stream().map(f -> (TimedDataSourcePlan) f.toFlowPlan()).toList()).build();
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
        restDataSourceService.getAll().forEach(flow -> flow.updateActionNamesByFamily(actionFamilyMap));
        timedDataSourceService.getAll().forEach(flow -> flow.updateActionNamesByFamily(actionFamilyMap));
        return actionFamilyMap.values();
    }

    @DgsQuery
    @NeedsPermission.FlowView
    public List<DataSourceErrorState> dataSourceErrorsExceeded() {
        List<DataSourceErrorState> errors = restDataSourceService.dataSourceErrorsExceeded();
        errors.addAll(timedDataSourceService.dataSourceErrorsExceeded());
        return errors;
    }

    private boolean removeFlowAndFlowPlan(FlowPlanService<?, ?, ?, ?> flowPlanService, String flowPlanName) {
        return flowPlanService.removePlan(flowPlanName, systemPluginService.getSystemPluginCoordinates());
    }

    private <T extends FlowPlanEntity, R extends Flow, S extends FlowSnapshot, U extends FlowRepo> R saveFlowPlan(FlowPlanService<T, R, S, U> flowPlanService, Object input, Class<T> clazz) {
        T flowPlan = OBJECT_MAPPER.convertValue(input, clazz);
        flowPlan.setSourcePlugin(systemPluginService.getSystemPluginCoordinates());
        return flowPlanService.saveFlowPlan(flowPlan);
    }
}
