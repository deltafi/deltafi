/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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
import org.deltafi.core.validation.*;

import java.util.*;
import java.util.stream.Collectors;

@DgsComponent
@RequiredArgsConstructor
@Slf4j
public class FlowPlanDatafetcher {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    public static final ActionFamily INGRESS_FAMILY = ActionFamily.newBuilder().family("INGRESS").actionNames(List.of(DeltaFiConstants.INGRESS_ACTION)).build();

    private static final ObjectMapper YAML_EXPORTER = new ObjectMapper(new YAMLFactory()).registerModule(new JavaTimeModule());

    private final DataSinkService dataSinkService;
    private final RestDataSourceService restDataSourceService;
    private final TransformFlowService transformFlowService;
    private final AnnotationService annotationService;
    private final TimedDataSourceService timedDataSourceService;
    private final OnErrorDataSourceService onErrorDataSourceService;
    private final CoreAuditLogger auditLogger;
    private final FlowCacheService flowCacheService;
    private final PluginService pluginService;
    private final DataSinkPlanValidator dataSinkPlanValidator;
    private final RestDataSourcePlanValidator restDataSourcePlanValidator;
    private final TimedDataSourcePlanValidator timedDataSourcePlanValidator;
    private final OnErrorDataSourcePlanValidator onErrorDataSourcePlanValidator;
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
    public boolean setOnErrorDataSourceMaxErrors(@InputArgument String name, @InputArgument Integer maxErrors) {
        if (onErrorDataSourceService.hasFlow(name)) {
            auditLogger.audit("set max errors to {} for data source {}", maxErrors, name);
            return onErrorDataSourceService.setMaxErrors(name, maxErrors);
        } else {
            throw new DgsEntityNotFoundException("No OnErrorDataSource exists with the name: " + name);
        }
    }

    @DgsMutation
    @NeedsPermission.FlowUpdate
    public boolean setRestDataSourceRateLimit(@InputArgument String name, @InputArgument RateLimitInput rateLimit) {
        if (restDataSourceService.hasFlow(name)) {
            auditLogger.audit("set rate limit to {} for data source {}", rateLimit, name);
            return restDataSourceService.setRateLimit(name, rateLimit);
        } else {
            throw new DgsEntityNotFoundException("No RestDataSource exists with the name: " + name);
        }
    }

    @DgsMutation
    @NeedsPermission.FlowUpdate
    public boolean removeRestDataSourceRateLimit(@InputArgument String name) {
        if (restDataSourceService.hasFlow(name)) {
            auditLogger.audit("remove rate limit for data source {}", name);
            return restDataSourceService.removeRateLimit(name);
        } else {
            throw new DgsEntityNotFoundException("No RestDataSource exists with the name: " + name);
        }
    }

    @DgsMutation
    @NeedsPermission.FlowUpdate
    public boolean setDataSinkExpectedAnnotations(@InputArgument String flowName, @InputArgument Set<String> expectedAnnotations) {
        auditLogger.audit("set expected annotations for dataSource {} to {}", flowName, expectedAnnotations);
        return annotationService.setExpectedAnnotations(flowName, new HashSet<>(expectedAnnotations));
    }

    @DgsMutation
    @NeedsPermission.FlowUpdate
    public boolean enableDataSinkTestMode(@InputArgument String flowName) {
        auditLogger.auditAndEvent("enabled test mode for dataSink {}", flowName);
        return dataSinkService.enableTestMode(flowName);
    }

    @DgsMutation
    @NeedsPermission.FlowUpdate
    public boolean disableDataSinkTestMode(@InputArgument String flowName) {
        auditLogger.auditAndEvent("disabled test mode for dataSink {}", flowName);
        return dataSinkService.disableTestMode(flowName);
    }

    @DgsMutation
    @NeedsPermission.Admin
    public boolean setTimedDataSourceMemo(@InputArgument String name, String memo) {
        auditLogger.audit("set timed source memo for dataSource {} to {}", name, memo);
        return timedDataSourceService.setMemo(name, memo);
    }

    @DgsMutation
    @NeedsPermission.FlowUpdate
    public boolean setTimedDataSourceCronSchedule(@InputArgument String name, String cronSchedule) {
        auditLogger.audit("set timed source cron schedule for dataSource {} to {}", name, cronSchedule);
        return timedDataSourceService.setCronSchedule(name, cronSchedule);
    }

    @DgsMutation
    @NeedsPermission.FlowPlanCreate
    public DataSink saveDataSinkPlan(@InputArgument DataSinkPlanInput dataSinkPlan) {
        auditLogger.audit("saved data sink plan {}", dataSinkPlan.getName());
        return (DataSink) saveFlowPlanToSystemPlugin(dataSinkPlan, DataSinkPlan.class, dataSinkService, dataSinkPlanValidator);
    }

    private <P extends FlowPlan> Flow saveFlowPlanToSystemPlugin(Object input, Class<P> flowPlanClass,
            FlowService<P, ?, ?, ?> flowService, FlowPlanValidator<P> flowPlanValidator) {
        P flowPlan = OBJECT_MAPPER.convertValue(input, flowPlanClass);
        return pluginService.addFlowPlanToSystemPlugin(flowPlan, flowService, flowPlanValidator);
    }

    @DgsMutation
    @NeedsPermission.FlowPlanDelete
    public boolean removeDataSinkPlan(@InputArgument String name) {
        auditLogger.audit("removed data sink plan {}", name);
        return pluginService.removeFlowPlanFromSystemPlugin(name, FlowType.DATA_SINK);
    }

    @DgsMutation
    @NeedsPermission.FlowUpdate
    public boolean setFlowState(@InputArgument FlowType flowType, @InputArgument String flowName, @InputArgument FlowState flowState) {
        auditLogger.auditAndEvent("set {} {} to state {}", flowType.getDisplayName(), flowName, flowState);

        FlowService<?, ?, ?, ?> flowService = switch(flowType) {
            case FlowType.REST_DATA_SOURCE -> restDataSourceService;
            case FlowType.TIMED_DATA_SOURCE -> timedDataSourceService;
            case FlowType.ON_ERROR_DATA_SOURCE -> onErrorDataSourceService;
            case FlowType.TRANSFORM -> transformFlowService;
            case FlowType.DATA_SINK -> dataSinkService;
        };

        if (flowState == FlowState.RUNNING) {
            return flowService.startFlow(flowName);
        } else if (flowState == FlowState.STOPPED) {
            return flowService.stopFlow(flowName);
        } else if (flowState == FlowState.PAUSED) {
            return flowService.pauseFlow(flowName);
        } else {
            throw new IllegalArgumentException("Unsupported flow state: " + flowState);
        }
    }

    @DgsMutation
    @NeedsPermission.FlowPlanCreate
    public DataSource saveTimedDataSourcePlan(@InputArgument TimedDataSourcePlanInput dataSourcePlan) {
        auditLogger.audit("saved timed source plan {}", dataSourcePlan.getName());
        return (DataSource) saveFlowPlanToSystemPlugin(dataSourcePlan, TimedDataSourcePlan.class, timedDataSourceService, timedDataSourcePlanValidator);
    }

    @DgsMutation
    @NeedsPermission.FlowPlanCreate
    public DataSource saveRestDataSourcePlan(@InputArgument RestDataSourcePlanInput dataSourcePlan) {
        auditLogger.audit("saved rest source plan {}", dataSourcePlan.getName());
        return (DataSource) saveFlowPlanToSystemPlugin(dataSourcePlan, RestDataSourcePlan.class, restDataSourceService, restDataSourcePlanValidator);
    }

    @DgsMutation
    @NeedsPermission.FlowPlanCreate
    public DataSource saveOnErrorDataSourcePlan(@InputArgument OnErrorDataSourcePlanInput dataSourcePlan) {
        auditLogger.audit("saved onError source plan {}", dataSourcePlan.getName());
        return (DataSource) saveFlowPlanToSystemPlugin(dataSourcePlan, OnErrorDataSourcePlan.class, onErrorDataSourceService, onErrorDataSourcePlanValidator);
    }

    @DgsMutation
    @NeedsPermission.FlowPlanDelete
    public boolean removeRestDataSourcePlan(@InputArgument String name) {
        auditLogger.audit("removed restDataSource plan {}", name);
        return pluginService.removeFlowPlanFromSystemPlugin(name, FlowType.REST_DATA_SOURCE);
    }

    @DgsMutation
    @NeedsPermission.FlowPlanDelete
    public boolean removeOnErrorDataSourcePlan(@InputArgument String name) {
        auditLogger.audit("removed onErrorDataSource plan {}", name);
        return pluginService.removeFlowPlanFromSystemPlugin(name, FlowType.ON_ERROR_DATA_SOURCE);
    }

    @DgsMutation
    @NeedsPermission.FlowPlanDelete
    public boolean removeTimedDataSourcePlan(@InputArgument String name) {
        auditLogger.audit("removed timedDataSource plan {}", name);
        return pluginService.removeFlowPlanFromSystemPlugin(name, FlowType.TIMED_DATA_SOURCE);
    }

    @DgsMutation
    @NeedsPermission.FlowUpdate
    public boolean enableRestDataSourceTestMode(@InputArgument String name) {
        auditLogger.auditAndEvent("enabled test mode for restDataSource {}", name);
        return restDataSourceService.enableTestMode(name);
    }

    @DgsMutation
    @NeedsPermission.FlowUpdate
    public boolean enableTimedDataSourceTestMode(@InputArgument String name) {
        auditLogger.auditAndEvent("enabled test mode for timedDataSource {}", name);
        return timedDataSourceService.enableTestMode(name);
    }

    @DgsMutation
    @NeedsPermission.FlowUpdate
    public boolean disableRestDataSourceTestMode(@InputArgument String name) {
        auditLogger.auditAndEvent("disabled test mode for restDataSource {}", name);
        return restDataSourceService.disableTestMode(name);
    }

    @DgsMutation
    @NeedsPermission.FlowUpdate
    public boolean disableTimedDataSourceTestMode(@InputArgument String name) {
        auditLogger.auditAndEvent("disabled test mode for timedDataSource {}", name);
        return timedDataSourceService.disableTestMode(name);
    }

    @DgsMutation
    @NeedsPermission.FlowUpdate
    public boolean enableOnErrorDataSourceTestMode(@InputArgument String name) {
        auditLogger.auditAndEvent("enabled test mode for onErrorDataSource {}", name);
        return onErrorDataSourceService.enableTestMode(name);
    }

    @DgsMutation
    @NeedsPermission.FlowUpdate
    public boolean disableOnErrorDataSourceTestMode(@InputArgument String name) {
        auditLogger.auditAndEvent("disabled test mode for onErrorDataSource {}", name);
        return onErrorDataSourceService.disableTestMode(name);
    }

    @DgsMutation
    @NeedsPermission.FlowPlanCreate
    public TransformFlow saveTransformFlowPlan(@InputArgument TransformFlowPlanInput transformFlowPlan) {
        auditLogger.audit("saved transform plan {}", transformFlowPlan.getName());
        return (TransformFlow) saveFlowPlanToSystemPlugin(transformFlowPlan, TransformFlowPlan.class, transformFlowService, transformFlowPlanValidator);
    }

    @DgsMutation
    @NeedsPermission.FlowPlanDelete
    public boolean removeTransformFlowPlan(@InputArgument String name) {
        auditLogger.audit("removed transform plan {}", name);
        return pluginService.removeFlowPlanFromSystemPlugin(name, FlowType.TRANSFORM);
    }

    @DgsMutation
    @NeedsPermission.FlowUpdate
    public boolean enableTransformTestMode(@InputArgument String flowName) {
        auditLogger.auditAndEvent("enabled test mode for transform {}", flowName);
        return transformFlowService.enableTestMode(flowName);
    }

    @DgsMutation
    @NeedsPermission.FlowUpdate
    public boolean disableTransformTestMode(@InputArgument String flowName) {
        auditLogger.auditAndEvent("disabled test mode for transform {}", flowName);
        return transformFlowService.disableTestMode(flowName);
    }

    @DgsMutation
    @NeedsPermission.PluginVariableUpdate
    public boolean savePluginVariables(@InputArgument List<Variable> variables) {
        auditLogger.audit("saved plugin variables {}", CoreAuditLogger.listToString(variables, Variable::getName));
        pluginService.saveSystemVariables(variables);
        return true;
    }

    @DgsMutation
    @NeedsPermission.PluginVariableUpdate
    public boolean removePluginVariables() {
        auditLogger.audit("removed system plugin variables");
        pluginService.removeSystemVariables();
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
    public SystemFlowPlans getAllSystemFlowPlans() {
        return pluginService.getSystemFlowPlans();
    }

    @DgsMutation
    @NeedsPermission.FlowPlanCreate
    public boolean saveSystemFlowPlans(@InputArgument SystemFlowPlansInput systemFlowPlansInput) {
        SystemFlowPlans flowPlans = OBJECT_MAPPER.convertValue(systemFlowPlansInput, SystemFlowPlans.class);
        auditLogger.audit("saved system flow plans: dataSink[{}] rest[{}] timed[{}] transform[{}]",
                CoreAuditLogger.listToString(flowPlans.getDataSinkPlans(), DataSinkPlan::getName),
                CoreAuditLogger.listToString(flowPlans.getRestDataSources(), DataSourcePlan::getName),
                CoreAuditLogger.listToString(flowPlans.getTimedDataSources(), DataSourcePlan::getName),
                CoreAuditLogger.listToString(flowPlans.getTransformPlans(), TransformFlowPlan::getName));
        pluginService.importSystemFlows(flowPlans);
        return true;
    }

    @DgsQuery
    @NeedsPermission.FlowView
    public DataSink getDataSink(@InputArgument String flowName) {
        return dataSinkService.getFlowOrThrow(flowName);
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
    public OnErrorDataSource getOnErrorDataSource(@InputArgument String name) {
        return onErrorDataSourceService.getFlowOrThrow(name);
    }

    @DgsQuery
    @NeedsPermission.FlowView
    public TransformFlow getTransformFlow(@InputArgument String flowName) {
        return transformFlowService.getFlowOrThrow(flowName);
    }

    @DgsQuery
    @NeedsPermission.FlowValidate
    public DataSink validateDataSink(@InputArgument String flowName) {
        return dataSinkService.validateAndSaveFlow(flowName);
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
    public OnErrorDataSource validateOnErrorDataSource(@InputArgument String name) {
        return onErrorDataSourceService.validateAndSaveFlow(name);
    }

    @DgsQuery
    @NeedsPermission.FlowValidate
    public TransformFlow validateTransformFlow(@InputArgument String flowName) {
        return transformFlowService.validateAndSaveFlow(flowName);
    }

    @DgsQuery
    @NeedsPermission.FlowView
    public DataSinkPlan getDataSinkPlan(@InputArgument String planName) {
        return (DataSinkPlan) pluginService.getPlanByName(planName, FlowType.DATA_SINK);
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
    public OnErrorDataSourcePlan getOnErrorDataSourcePlan(@InputArgument String planName) {
        return (OnErrorDataSourcePlan) pluginService.getPlanByName(planName, FlowType.ON_ERROR_DATA_SOURCE);
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
        flowMap.put("dataSinks", dataSinkService.getAll());
        flowMap.put("transformFlows", transformFlowService.getAll());
        flowMap.put("dataSources", restDataSourceService.getAll());

        return YAML_EXPORTER.writeValueAsString(flowMap);
    }

    @DgsQuery
    @NeedsPermission.FlowView
    public SystemFlows getRunningFlows() {
        return SystemFlows.newBuilder()
                .dataSink(dataSinkService.getRunningFlows())
                .transform(transformFlowService.getRunningFlows())
                .restDataSource(restDataSourceService.getRunningFlows())
                .timedDataSource(timedDataSourceService.getRunningFlows())
                .onErrorDataSource(onErrorDataSourceService.getRunningFlows()).build();
    }

    @DgsQuery
    @NeedsPermission.UIAccess
    public FlowNames getFlowNames(@InputArgument FlowState state) {
        return FlowNames.newBuilder()
                .dataSink(dataSinkService.getFlowNamesByState(state))
                .transform(transformFlowService.getFlowNamesByState(state))
                .restDataSource(restDataSourceService.getFlowNamesByState(state))
                .timedDataSource(timedDataSourceService.getFlowNamesByState(state))
                .onErrorDataSource(onErrorDataSourceService.getFlowNamesByState(state)).build();
    }

    @DgsQuery
    @NeedsPermission.FlowView
    public SystemFlows getAllFlows() {
        flowCacheService.refreshCache();
        return SystemFlows.newBuilder()
                .dataSink(dataSinkService.getAll())
                .transform(transformFlowService.getAll())
                .restDataSource(restDataSourceService.getAll())
                .timedDataSource(timedDataSourceService.getAll())
                .onErrorDataSource(onErrorDataSourceService.getAll()).build();
    }

    @DgsQuery
    @NeedsPermission.FlowView
    public List<Topic> getAllTopics() {
        flowCacheService.refreshCache();
        return flowCacheService.getTopics();
    }

    @DgsQuery
    @NeedsPermission.FlowView
    public List<Topic> getTopics(@InputArgument List<String> names) {
        flowCacheService.refreshCache();
        List<Topic> topics = flowCacheService.getTopics().stream().filter(t -> names.contains(t.getName())).toList();
        if (names.size() == topics.size()) {
            return topics;
        }

        List<String> foundNames = topics.stream().map(Topic::getName).toList();
        throw new DgsEntityNotFoundException("No Topics exist with the names: " + names.stream().filter(name -> !foundNames.contains(name)).collect(Collectors.joining(", ")));
    }

    @DgsQuery
    @NeedsPermission.FlowView
    public Topic getTopic(@InputArgument String name) {
        flowCacheService.refreshCache();
        Optional<Topic> topic = flowCacheService.getTopics().stream().filter(t -> name.equals(t.getName())).findFirst();
        return topic.orElseThrow(() -> new DgsEntityNotFoundException("No Topic exists with the name " + name));
    }

    @DgsQuery
    @NeedsPermission.FlowView
    public SystemFlowPlans getAllFlowPlans() {
        return SystemFlowPlans.newBuilder()
                .dataSinkPlans(getFlowPlansByType(FlowType.DATA_SINK, DataSinkPlan.class))
                .transformPlans(getFlowPlansByType(FlowType.TRANSFORM, TransformFlowPlan.class))
                .restDataSources(getFlowPlansByType(FlowType.REST_DATA_SOURCE, RestDataSourcePlan.class))
                .timedDataSources(getFlowPlansByType(FlowType.TIMED_DATA_SOURCE, TimedDataSourcePlan.class))
                .onErrorDataSources(getFlowPlansByType(FlowType.ON_ERROR_DATA_SOURCE, OnErrorDataSourcePlan.class))
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
        dataSinkService.getAll().forEach(flow -> flow.updateActionNamesByFamily(actionFamilyMap));
        transformFlowService.getAll().forEach(flow -> flow.updateActionNamesByFamily(actionFamilyMap));
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
