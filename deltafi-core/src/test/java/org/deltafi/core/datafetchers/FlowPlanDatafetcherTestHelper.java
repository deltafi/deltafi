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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.TypeRef;
import com.netflix.graphql.dgs.DgsQueryExecutor;
import com.netflix.graphql.dgs.client.codegen.BaseProjectionNode;
import com.netflix.graphql.dgs.client.codegen.GraphQLQuery;
import com.netflix.graphql.dgs.client.codegen.GraphQLQueryRequest;
import org.deltafi.common.types.*;
import org.deltafi.core.converters.DurationScalar;
import org.deltafi.core.generated.client.*;
import org.deltafi.core.generated.types.*;
import org.deltafi.core.types.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.deltafi.core.util.Constants.TIMED_DATA_SOURCE_NAME;

public class FlowPlanDatafetcherTestHelper {

    public static final PluginCoordinates PLUGIN_COORDINATES = PluginCoordinates.builder().artifactId("test-plugin").groupId("org.deltafi").version("1.0.0").build();
    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static TransformFlowPlan getTransformFlowPlan(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, GetTransformFlowPlanGraphQLQuery.newRequest().planName("transformPlan").build(), new GetTransformFlowPlanProjectionRoot<>().name().type().description().type(), TransformFlowPlan.class);
    }

    public static DataSinkPlan getDataSinkPlan(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, GetDataSinkPlanGraphQLQuery.newRequest().planName("dataSinkPlan").build(), new GetDataSinkPlanProjectionRoot<>().name().type().description().egressAction().name().actionType().type(), DataSinkPlan.class);
    }

    public static TimedDataSourcePlan getTimedIngressFlowPlan(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, GetTimedDataSourcePlanGraphQLQuery.newRequest().planName("timedIngressPlan").build(), new GetTimedDataSourcePlanProjectionRoot<>().name().type().description().topic()
                .name().timedIngressAction().name().actionType().type().parent().cronSchedule()
                .metadata().annotationConfig().annotations().metadataPatterns().discardPrefix().parent(),
                TimedDataSourcePlan.class);
    }

    public static TransformFlow validateTransformFlow(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, ValidateTransformFlowGraphQLQuery.newRequest().flowName("sampleTransform").build(), new ValidateTransformFlowProjectionRoot<>().name(), TransformFlow.class);
    }

    public static TimedDataSource validateTimedIngressFlow(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, ValidateTimedDataSourceGraphQLQuery.newRequest().name(TIMED_DATA_SOURCE_NAME).build(), new ValidateTimedDataSourceProjectionRoot<>().name().type(), TimedDataSource.class);
    }

    public static FlowNames getFlowNames(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, GetFlowNamesGraphQLQuery.newRequest().build(),
                new GetFlowNamesProjectionRoot<>().transform().dataSink().timedDataSource().restDataSource().onErrorDataSource(), FlowNames.class);
    }

    public static DataSink validateDataSink(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, ValidateDataSinkGraphQLQuery.newRequest().flowName("sampleEgress").build(), new ValidateDataSinkProjectionRoot<>().name(), DataSink.class);
    }

    public static List<Flows> getFlows(DgsQueryExecutor dgsQueryExecutor) {
        TypeRef<List<Flows>> typeRef = new TypeRef<>() {};
        return executeQuery(dgsQueryExecutor, GetFlowsGraphQLQuery.newRequest().build(), new GetFlowsProjectionRoot<>().sourcePlugin().artifactId().parent().transformFlows().name().parent().dataSinks().name().parent().restDataSources().name().type().parent().timedDataSources().name().type().root().onErrorDataSources().name().type().root(), typeRef);
    }

    public static SystemFlows getRunningFlows(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, GetRunningFlowsGraphQLQuery.newRequest().build(),
                new GetRunningFlowsProjectionRoot<>().transform().name().parent().dataSink().name().root(), SystemFlows.class);
    }

    public static SystemFlowPlans getAllFlowPlans(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, GetAllFlowPlansGraphQLQuery.newRequest().build(),
                new GetAllFlowPlansProjectionRoot<>()
                        .timedDataSources().type().name().description().topic().metadata().annotationConfig().annotations().metadataPatterns().discardPrefix().parent().timedIngressAction().name().type().actionType().parent().cronSchedule().parent()
                        .restDataSources().type().name().description().topic().metadata().annotationConfig().annotations().metadataPatterns().discardPrefix().parent().parent()
                        .onErrorDataSources().type().name().description().topic().metadata().annotationConfig().annotations().metadataPatterns().discardPrefix().parent().parent()
                        .transformPlans().name().description().parent()
                        .dataSinkPlans().name().description().egressAction().name().type().actionType()
                        .root(), SystemFlowPlans.class);
    }

    public static SystemFlows getAllFlows(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, GetAllFlowsGraphQLQuery.newRequest().build(),
                new GetAllFlowsProjectionRoot<>().timedDataSource().type().name().parent().restDataSource().type().name().parent().onErrorDataSource().type().name().parent().transform().name().parent().dataSink().name().root(), SystemFlows.class);
    }

    public static SystemFlowPlans getAllSystemFlowPlans(DgsQueryExecutor dgsQueryExecutor) {
        GetAllSystemFlowPlansProjectionRoot<?, ?> projection = new GetAllSystemFlowPlansProjectionRoot<>()
                .dataSinkPlans()
                .name()
                .type()
                .description()
                .sourcePlugin()
                .groupId()
                .artifactId()
                .version()
                .parent()
                .subscribe()
                .topic()
                .condition()
                .parent()
                .egressAction()
                .name()
                .type()
                .parameters()
                .parent()
                .parent()

                .restDataSources()
                .name()
                .type()
                .description()
                .sourcePlugin()
                .groupId()
                .artifactId()
                .version()
                .parent()
                .topic()
                .metadata()
                .annotationConfig()
                .annotations()
                .metadataPatterns()
                .discardPrefix()
                .parent()
                .parent()

                .timedDataSources()
                .name()
                .type()
                .description()
                .sourcePlugin()
                .groupId()
                .artifactId()
                .version()
                .parent()
                .timedIngressAction()
                .name()
                .type()
                .parameters()
                .parent()
                .topic()
                .metadata()
                .annotationConfig()
                .annotations()
                .metadataPatterns()
                .discardPrefix()
                .parent()
                .cronSchedule()
                .parent()

                .transformPlans()
                .name()
                .type()
                .description()
                .sourcePlugin()
                .groupId()
                .artifactId()
                .version()
                .parent()
                .publish()
                .defaultRule()
                .defaultBehavior()
                .parent()
                .topic()
                .parent()
                .matchingPolicy()
                .parent()
                .parent()

                .subscribe()
                .topic()
                .condition()
                .parent()
                .transformActions()
                .name()
                .type()
                .parameters()
                .parent()
                .parent();

        return executeQuery(dgsQueryExecutor, GetAllSystemFlowPlansGraphQLQuery.newRequest().build(),
                projection, SystemFlowPlans.class);
    }

    @SuppressWarnings("unchecked")
    public static List<Topic> getAllTopics(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, GetAllTopicsGraphQLQuery.newRequest().build(),
                new GetAllTopicsProjectionRoot<>().name().publishers().name().type().parent().state().parent().condition().parent().subscribers().name().type().parent().state().parent().condition().root(),
                List.class).stream()
                .map(map -> OBJECT_MAPPER.convertValue(map, Topic.class))
                .toList();
    }

    @SuppressWarnings("unchecked")
    public static List<Topic> getTopics(DgsQueryExecutor dgsQueryExecutor, List<String> topics) {
        return executeQuery(dgsQueryExecutor, GetTopicsGraphQLQuery.newRequest().names(topics).build(),
                new GetTopicsProjectionRoot<>().name().publishers().name().type().parent().state().parent().condition().parent().subscribers().name().type().parent().state().parent().condition().root(),
                List.class).stream()
                .map(map -> OBJECT_MAPPER.convertValue(map, Topic.class))
                .toList();
    }

    public static Topic getTopic(DgsQueryExecutor dgsQueryExecutor, String topic) {
        return executeQuery(dgsQueryExecutor, GetTopicGraphQLQuery.newRequest().name(topic).build(),
                new GetTopicProjectionRoot<>().name().publishers().name().type().parent().state().parent().condition().parent().subscribers().name().type().parent().state().parent().condition().root(),
                Topic.class);
    }

    public static TransformFlow getTransformFlow(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, GetTransformFlowGraphQLQuery.newRequest().flowName("sampleTransform").build(),
                new GetTransformFlowProjectionRoot<>().name(), TransformFlow.class);
    }

    public static DataSink getDataSink(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, GetDataSinkGraphQLQuery.newRequest().flowName("sampleEgress").build(),
                new GetDataSinkProjectionRoot<>().name(), DataSink.class);
    }

    public static List<ActionFamily> getActionFamilies(DgsQueryExecutor dgsQueryExecutor) {
        TypeRef<List<ActionFamily>> typeRef = new TypeRef<>() {};
        return executeQuery(dgsQueryExecutor, GetActionNamesByFamilyGraphQLQuery.newRequest().build(), new GetActionNamesByFamilyProjectionRoot<>().family().actionNames(), typeRef);
    }

    public static boolean saveSystemFlowPlans(DgsQueryExecutor dgsQueryExecutor, SystemFlowPlansInput input) {
        return executeQuery(dgsQueryExecutor, SaveSystemFlowPlansGraphQLQuery.newRequest().systemFlowPlansInput(input).build());
    }

    public static TransformFlow saveTransformFlowPlan(DgsQueryExecutor dgsQueryExecutor) {
        PublishRules publishRules = new PublishRules();
        publishRules.setDefaultRule(new DefaultRule(DefaultBehavior.ERROR));
        publishRules.setRules(List.of(new Rule("topic")));

        TransformFlowPlanInput input = TransformFlowPlanInput.newBuilder()
                .name("flowPlan")
                .type("TRANSFORM")
                .description("description")
                .subscribe(List.of(new Rule("topic", null)))
                .publish(publishRules)
                .build();

        return executeQuery(dgsQueryExecutor, SaveTransformFlowPlanGraphQLQuery.newRequest().transformFlowPlan(input).build(), new SaveTransformFlowPlanProjectionRoot<>().name().flowStatus().state().parent().parent(), TransformFlow.class);
    }

    public static DataSink saveDataSinkPlan(DgsQueryExecutor dgsQueryExecutor) {
        ActionConfigurationInput egress = ActionConfigurationInput.newBuilder().name("egress").type("org.deltafi.actions.EgressAction").build();
        DataSinkPlanInput input = DataSinkPlanInput.newBuilder()
                .name("flowPlan")
                .type("DATA_SINK")
                .description("description")
                .egressAction(egress)
                .subscribe(List.of(new Rule("topic", null)))
                .build();
        return executeQuery(dgsQueryExecutor, SaveDataSinkPlanGraphQLQuery.newRequest().dataSinkPlan(input).build(), new SaveDataSinkPlanProjectionRoot<>().name(), DataSink.class);
    }

    public static TimedDataSource saveTimedDataSourcePlan(DgsQueryExecutor dgsQueryExecutor) {
        ActionConfigurationInput timedIngress = ActionConfigurationInput.newBuilder().name("timedIngress").type("org.deltafi.actions.TimedIngress").build();

        TimedDataSourcePlanInput input = TimedDataSourcePlanInput.newBuilder()
                .name("flowPlan")
                .type("TIMED_DATA_SOURCE")
                .description("description")
                .timedIngressAction(timedIngress)
                .cronSchedule("*/5 * * * * *")
                .topic("topic")
                .build();
        return executeQuery(dgsQueryExecutor, SaveTimedDataSourcePlanGraphQLQuery.newRequest().dataSourcePlan(input).build(), new SaveTimedDataSourcePlanProjectionRoot<>().name().description().type(), TimedDataSource.class);
    }

    public static boolean removeTransformFlowPlan(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, RemoveTransformFlowPlanGraphQLQuery.newRequest().name("flowPlan").build());
    }

    public static boolean removeDataSinkPlan(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, RemoveDataSinkPlanGraphQLQuery.newRequest().name("flowPlan").build());
    }

    public static boolean removeTimedDataSourcePlan(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, RemoveTimedDataSourcePlanGraphQLQuery.newRequest().name("flowPlan").build());
    }

    public static boolean startTransformFlow(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, SetFlowStateGraphQLQuery.newRequest().flowType(FlowType.TRANSFORM).flowName("sampleTransform").flowState(FlowState.RUNNING).build());
    }

    public static boolean stopTransformFlow(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, SetFlowStateGraphQLQuery.newRequest().flowType(FlowType.TRANSFORM).flowName("sampleTransform").flowState(FlowState.STOPPED).build());
    }

    public static boolean pauseTransformFlow(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, SetFlowStateGraphQLQuery.newRequest().flowType(FlowType.TRANSFORM).flowName("sampleTransform").flowState(FlowState.PAUSED).build());
    }

    public static boolean startDataSink(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, SetFlowStateGraphQLQuery.newRequest().flowType(FlowType.DATA_SINK).flowName("sampleEgress").flowState(FlowState.RUNNING).build());
    }

    public static boolean stopDataSink(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, SetFlowStateGraphQLQuery.newRequest().flowType(FlowType.DATA_SINK).flowName("sampleEgress").flowState(FlowState.STOPPED).build());
    }

    public static boolean pauseDataSink(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, SetFlowStateGraphQLQuery.newRequest().flowType(FlowType.DATA_SINK).flowName("sampleEgress").flowState(FlowState.PAUSED).build());
    }

    public static boolean startTimedDataSource(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, SetFlowStateGraphQLQuery.newRequest().flowType(FlowType.TIMED_DATA_SOURCE).flowName(TIMED_DATA_SOURCE_NAME).flowState(FlowState.RUNNING).build());
    }

    public static boolean stopTimedDataSource(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, SetFlowStateGraphQLQuery.newRequest().flowType(FlowType.TIMED_DATA_SOURCE).flowName(TIMED_DATA_SOURCE_NAME).flowState(FlowState.STOPPED).build());
    }

    public static boolean setTimedDataSourceMemo(DgsQueryExecutor dgsQueryExecutor, String memo) {
        return executeQuery(dgsQueryExecutor, SetTimedDataSourceMemoGraphQLQuery.newRequest().name(TIMED_DATA_SOURCE_NAME).memo(memo).build());
    }

    public static boolean savePluginVariables(DgsQueryExecutor dgsQueryExecutor) {
        List<Variable> variableInputs = List.of(Variable.builder().name("var").defaultValue("default").required(false).description("description").dataType(VariableDataType.STRING).build());
        return executeQuery(dgsQueryExecutor, SavePluginVariablesGraphQLQuery.newRequest().variables(variableInputs).build());
    }

    public static boolean removePluginVariables(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, RemovePluginVariablesGraphQLQuery.newRequest().build());
    }

    public static boolean setPluginVariableValues(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, SetPluginVariableValuesGraphQLQuery.newRequest().pluginCoordinates(PLUGIN_COORDINATES).variables(List.of(new KeyValue("key", "value"))).build());
    }

    static <T> T executeQuery(DgsQueryExecutor dgsQueryExecutor, GraphQLQuery query, BaseProjectionNode projection, TypeRef<T> typeRef) {
        return dgsQueryExecutor.executeAndExtractJsonPathAsObject(new GraphQLQueryRequest(query, projection).serialize(), "data." + query.getOperationName(), typeRef);
    }

    static boolean executeQuery(DgsQueryExecutor dgsQueryExecutor, GraphQLQuery query) {
        return executeQuery(dgsQueryExecutor, query, new EmptyProjection(), Boolean.class);
    }

    static <T> T executeQuery(DgsQueryExecutor dgsQueryExecutor, GraphQLQuery query, BaseProjectionNode projection, Class<T> clazz) {
        return dgsQueryExecutor.executeAndExtractJsonPathAsObject(new GraphQLQueryRequest(query, projection, Map.of(Duration.class, new DurationScalar())).serialize(), "data." + query.getOperationName(), clazz);
    }

    private static class EmptyProjection extends BaseProjectionNode {}
}
