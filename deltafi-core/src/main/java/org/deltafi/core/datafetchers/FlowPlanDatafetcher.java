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
import org.deltafi.core.security.NeedsPermission;
import org.deltafi.core.services.*;
import org.deltafi.core.types.*;
import org.deltafi.core.validation.EgressFlowPlanValidator;
import org.deltafi.core.validation.RestDataSourcePlanValidator;
import org.deltafi.core.validation.TimedDataSourcePlanValidator;
import org.deltafi.core.validation.TransformFlowPlanValidator;

import java.util.*;

@DgsComponent
@RequiredArgsConstructor
@Slf4j
public class FlowPlanDatafetcher {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    public static final ActionFamily INGRESS_FAMILY = ActionFamily.newBuilder().family("INGRESS").actionNames(List.of(DeltaFiConstants.INGRESS_ACTION)).build();

    private static final ObjectMapper YAML_EXPORTER = new ObjectMapper(new YAMLFactory()).registerModule(new JavaTimeModule());

    private final EgressFlowService egressFlowService;
    private final RestDataSourceService restDataSourceService;
    private final TransformFlowService transformFlowService;
    private final AnnotationService annotationService;
    private final PluginVariableService pluginVariableService;
    private final TimedDataSourceService timedDataSourceService;
    private final CoreAuditLogger auditLogger;
    private final FlowCacheService flowCacheService;
    private final PluginService pluginService;
    private final EgressFlowPlanValidator egressFlowPlanValidator;
    private final RestDataSourcePlanValidator restDataSourcePlanValidator;
    private final TimedDataSourcePlanValidator timedDataSourcePlanValidator;
    private final TransformFlowPlanValidator transformFlowPlanValidator;

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
        EgressFlowPlan flowPlan = OBJECT_MAPPER.convertValue(egressFlowPlan, EgressFlowPlan.class);
        flowPlan.setSourcePlugin(pluginService.getSystemPluginCoordinates());
        egressFlowPlanValidator.validate(flowPlan);
        pluginService.addFlowPlanToSystemPlugin(flowPlan);
        return egressFlowService.buildAndSaveFlow(flowPlan);
    }

    @DgsMutation
    @NeedsPermission.FlowPlanDelete
    public boolean removeEgressFlowPlan(@InputArgument String name) {
        auditLogger.audit("removed egress flow plan {}", name);
        return pluginService.removeFlowPlanFromSystemPlugin(name, FlowType.EGRESS);
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
        TimedDataSourcePlan flowPlan = OBJECT_MAPPER.convertValue(dataSourcePlan, TimedDataSourcePlan.class);
        flowPlan.setSourcePlugin(pluginService.getSystemPluginCoordinates());
        timedDataSourcePlanValidator.validate(flowPlan);
        pluginService.addFlowPlanToSystemPlugin(flowPlan);
        return timedDataSourceService.buildAndSaveFlow(flowPlan);
    }

    @DgsMutation
    @NeedsPermission.FlowPlanCreate
    public DataSource saveRestDataSourcePlan(@InputArgument RestDataSourcePlanInput dataSourcePlan) {
        auditLogger.audit("saved rest source plan {}", dataSourcePlan.getName());
        RestDataSourcePlan flowPlan = OBJECT_MAPPER.convertValue(dataSourcePlan, RestDataSourcePlan.class);
        flowPlan.setSourcePlugin(pluginService.getSystemPluginCoordinates());
        restDataSourcePlanValidator.validate(flowPlan);
        pluginService.addFlowPlanToSystemPlugin(flowPlan);
        return restDataSourceService.buildAndSaveFlow(flowPlan);
    }

    @DgsMutation
    @NeedsPermission.FlowPlanDelete
    public boolean removeRestDataSourcePlan(@InputArgument String name) {
        auditLogger.audit("removed restDataSource plan {}", name);
        return pluginService.removeFlowPlanFromSystemPlugin(name, FlowType.REST_DATA_SOURCE);
    }

    @DgsMutation
    @NeedsPermission.FlowPlanDelete
    public boolean removeTimedDataSourcePlan(@InputArgument String name) {
        auditLogger.audit("removed timedDataSource plan {}", name);
        return pluginService.removeFlowPlanFromSystemPlugin(name, FlowType.TIMED_DATA_SOURCE);
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
        TransformFlowPlan flowPlan = OBJECT_MAPPER.convertValue(transformFlowPlan, TransformFlowPlan.class);
        flowPlan.setSourcePlugin(pluginService.getSystemPluginCoordinates());
        transformFlowPlanValidator.validate(flowPlan);
        pluginService.addFlowPlanToSystemPlugin(flowPlan);
        return transformFlowService.buildAndSaveFlow(flowPlan);
    }

    @DgsMutation
    @NeedsPermission.FlowPlanDelete
    public boolean removeTransformFlowPlan(@InputArgument String name) {
        auditLogger.audit("removed transform flow plan {}", name);
        return pluginService.removeFlowPlanFromSystemPlugin(name, FlowType.TRANSFORM);
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
        pluginVariableService.validateAndSaveVariables(pluginService.getSystemPluginCoordinates(), variables);
        return true;
    }

    @DgsMutation
    @NeedsPermission.PluginVariableUpdate
    public boolean removePluginVariables() {
        auditLogger.audit("removed system plugin variables");
        pluginVariableService.removeVariables(pluginService.getSystemPluginCoordinates());
        return true;
    }

    @DgsMutation
    @NeedsPermission.PluginVariableUpdate
    public boolean setPluginVariableValues(@InputArgument PluginCoordinates pluginCoordinates, @InputArgument List<KeyValue> variables) {
        auditLogger.audit("updated plugin variables: {}", CoreAuditLogger.listToString(variables));
        return pluginService.setPluginVariableValues(pluginCoordinates, variables);
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
    public EgressFlowPlan getEgressFlowPlan(@InputArgument String planName) {
        return (EgressFlowPlan) pluginService.getPlanByName(planName, FlowType.EGRESS);
    }

    @DgsQuery
    @NeedsPermission.FlowView
    public RestDataSourcePlan getRestDataSourcePlan(@InputArgument String planName) {
        return (RestDataSourcePlan) pluginService.getPlanByName(planName, FlowType.REST_DATA_SOURCE);
    }

    @DgsQuery
    @NeedsPermission.FlowView
    public TimedDataSourcePlan getTimedDataSourcePlan(@InputArgument String planName) {
        return (TimedDataSourcePlan) pluginService.getPlanByName(planName, FlowType.TIMED_DATA_SOURCE);
    }

    @DgsQuery
    @NeedsPermission.FlowView
    public TransformFlowPlan getTransformFlowPlan(@InputArgument String planName) {
        return (TransformFlowPlan) pluginService.getPlanByName(planName, FlowType.TRANSFORM);
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
                .egressPlans(getFlowPlansByType(FlowType.EGRESS, EgressFlowPlan.class))
                .transformPlans(getFlowPlansByType(FlowType.TRANSFORM, TransformFlowPlan.class))
                .restDataSources(getFlowPlansByType(FlowType.REST_DATA_SOURCE, RestDataSourcePlan.class))
                .timedDataSources(getFlowPlansByType(FlowType.TIMED_DATA_SOURCE, TimedDataSourcePlan.class))
                .build();
    }

    private <T extends FlowPlan> List<T> getFlowPlansByType(FlowType flowType, Class<T> klass) {
        return pluginService.getFlowPlansByType(flowType).stream()
                .filter(klass::isInstance)
                .map(klass::cast)
                .toList();
    }

    @DgsQuery
    @NeedsPermission.FlowView
    public Collection<Flows> getFlows() {
        return pluginService.getFlowsByPlugin();
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
}
