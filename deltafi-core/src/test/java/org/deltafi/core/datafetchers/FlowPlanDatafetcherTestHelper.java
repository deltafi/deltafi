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

import com.jayway.jsonpath.TypeRef;
import com.netflix.graphql.dgs.DgsQueryExecutor;
import com.netflix.graphql.dgs.client.codegen.BaseProjectionNode;
import com.netflix.graphql.dgs.client.codegen.GraphQLQuery;
import com.netflix.graphql.dgs.client.codegen.GraphQLQueryRequest;
import org.deltafi.common.types.*;
import org.deltafi.core.converters.DurationScalar;
import org.deltafi.core.generated.client.*;
import org.deltafi.core.generated.types.*;
import org.deltafi.core.types.EgressFlow;
import org.deltafi.core.types.NormalizeFlow;
import org.deltafi.core.types.TimedIngressFlow;
import org.deltafi.core.types.TransformFlow;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.deltafi.core.util.Constants.NORMALIZE_FLOW_NAME;
import static org.deltafi.core.util.Constants.TIMED_INGRESS_FLOW_NAME;

public class FlowPlanDatafetcherTestHelper {

    public static final PluginCoordinates PLUGIN_COORDINATES = PluginCoordinates.builder().artifactId("test-plugin").groupId("org.deltafi").version("1.0.0").build();

    public static TransformFlowPlan getTransformFlowPlan(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, GetTransformFlowPlanGraphQLQuery.newRequest().planName("transformPlan").build(), new GetTransformFlowPlanProjectionRoot().name().type().description().egressAction().name().actionType().type(), TransformFlowPlan.class);
    }
    
    public static NormalizeFlowPlan getNormalizeFlowPlan(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, GetNormalizeFlowPlanGraphQLQuery.newRequest().planName("normalizePlan").build(), new GetNormalizeFlowPlanProjectionRoot().name().type().description().loadAction().name().actionType().type(), NormalizeFlowPlan.class);
    }

    public static EgressFlowPlan getEgressFlowPlan(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, GetEgressFlowPlanGraphQLQuery.newRequest().planName("egressPlan").build(), new GetEgressFlowPlanProjectionRoot().name().type().description().formatAction().name().actionType().type().requiresDomains().parent().egressAction().name().actionType().type(), EgressFlowPlan.class);
    }

    public static TimedIngressFlowPlan getTimedIngressFlowPlan(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, GetTimedIngressFlowPlanGraphQLQuery.newRequest().planName("timedIngressPlan").build(), new GetTimedIngressFlowPlanProjectionRoot().name().type().description().timedIngressAction().name().actionType().type().parent().targetFlow().cronSchedule(), TimedIngressFlowPlan.class);
    }

    public static TransformFlow validateTransformFlow(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, ValidateTransformFlowGraphQLQuery.newRequest().flowName("sampleTransform").build(), new ValidateTransformFlowProjectionRoot().name(), TransformFlow.class);
    }

    public static NormalizeFlow validateNormalizeFlow(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, ValidateNormalizeFlowGraphQLQuery.newRequest().flowName(NORMALIZE_FLOW_NAME).build(), new ValidateNormalizeFlowProjectionRoot().name(), NormalizeFlow.class);
    }

    public static TimedIngressFlow validateTimedIngressFlow(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, ValidateTimedIngressFlowGraphQLQuery.newRequest().flowName(TIMED_INGRESS_FLOW_NAME).build(), new ValidateTimedIngressFlowProjectionRoot().name(), TimedIngressFlow.class);
    }

    public static FlowNames getFlowNames(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, GetFlowNamesGraphQLQuery.newRequest().build(),
                new GetFlowNamesProjectionRoot().transform().normalize().enrich().egress(), FlowNames.class);
    }

    public static EgressFlow validateEgressFlow(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, ValidateEgressFlowGraphQLQuery.newRequest().flowName("sampleEgress").build(), new ValidateNormalizeFlowProjectionRoot().name(), EgressFlow.class);
    }

    public static List<Flows> getFlows(DgsQueryExecutor dgsQueryExecutor) {
        TypeRef<List<Flows>> typeRef = new TypeRef<>() {};
        return executeQuery(dgsQueryExecutor, GetFlowsGraphQLQuery.newRequest().build(), new GetFlowsProjectionRoot().sourcePlugin().artifactId().parent().transformFlows().name().parent().normalizeFlows().name().parent().egressFlows().name().parent().timedIngressFlows().name().root(), typeRef);
    }

    public static SystemFlows getRunningFlows(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, GetRunningFlowsGraphQLQuery.newRequest().build(),
                new GetRunningFlowsProjectionRoot().transform().name().parent().normalize().name().parent().enrich().name().parent().egress().name().root(), SystemFlows.class);
    }

    public static SystemFlows getAllFlows(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, GetAllFlowsGraphQLQuery.newRequest().build(),
                new GetAllFlowsProjectionRoot().transform().name().parent().normalize().name().parent().egress().name().root(), SystemFlows.class);
    }

    public static TransformFlow getTransformFlow(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, GetTransformFlowGraphQLQuery.newRequest().flowName("sampleTransform").build(),
                new GetTransformFlowProjectionRoot().name(), TransformFlow.class);
    }

    public static NormalizeFlow getNormalizeFlow(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, GetNormalizeFlowGraphQLQuery.newRequest().flowName(NORMALIZE_FLOW_NAME).build(),
                new GetNormalizeFlowProjectionRoot().name(), NormalizeFlow.class);
    }

    public static EgressFlow getEgressFlow(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, GetEgressFlowGraphQLQuery.newRequest().flowName("sampleEgress").build(),
                new GetEgressFlowProjectionRoot().name(), EgressFlow.class);
    }

    public static List<ActionFamily> getActionFamilies(DgsQueryExecutor dgsQueryExecutor) {
        TypeRef<List<ActionFamily>> typeRef = new TypeRef<>() {};
        return executeQuery(dgsQueryExecutor, GetActionNamesByFamilyGraphQLQuery.newRequest().build(), new GetActionNamesByFamilyProjectionRoot().family().actionNames(), typeRef);
    }

    public static TransformFlow saveTransformFlowPlan(DgsQueryExecutor dgsQueryExecutor) {
        EgressActionConfigurationInput egressActionConfigurationInput = EgressActionConfigurationInput.newBuilder().name("egress").type("org.deltafi.action.Egress").build();
        TransformFlowPlanInput input = TransformFlowPlanInput.newBuilder()
                .name("flowPlan")
                .type("TRANSFORM")
                .description("description")
                .egressAction(egressActionConfigurationInput)
                .build();

        return executeQuery(dgsQueryExecutor, SaveTransformFlowPlanGraphQLQuery.newRequest().transformFlowPlan(input).build(), new SaveTransformFlowPlanProjectionRoot().name().flowStatus().state().parent().parent(), TransformFlow.class);
    }

    public static NormalizeFlow saveNormalizeFlowPlan(DgsQueryExecutor dgsQueryExecutor) {
        LoadActionConfigurationInput loadActionConfigurationInput = LoadActionConfigurationInput.newBuilder().name("loader").type("org.deltafi.action.Loader").build();
        NormalizeFlowPlanInput input = NormalizeFlowPlanInput.newBuilder()
                .name("flowPlan")
                .type("INGRESS")
                .description("description")
                .loadAction(loadActionConfigurationInput)
                .build();

        return executeQuery(dgsQueryExecutor, SaveNormalizeFlowPlanGraphQLQuery.newRequest().normalizeFlowPlan(input).build(), new SaveNormalizeFlowPlanProjectionRoot().name().flowStatus().state().parent().parent(), NormalizeFlow.class);
    }

    public static EgressFlow saveEgressFlowPlan(DgsQueryExecutor dgsQueryExecutor) {
        FormatActionConfigurationInput format = FormatActionConfigurationInput.newBuilder().name("format").type("org.deltafi.actions.Formatter").requiresDomains(List.of("domain")).build();
        EgressActionConfigurationInput egress = EgressActionConfigurationInput.newBuilder().name("egress").type("org.deltafi.actions.EgressAction").build();
        EgressFlowPlanInput input = EgressFlowPlanInput.newBuilder()
                .name("flowPlan")
                .type("EGRESS")
                .description("description")
                .formatAction(format)
                .egressAction(egress)
                .build();
        return executeQuery(dgsQueryExecutor, SaveEgressFlowPlanGraphQLQuery.newRequest().egressFlowPlan(input).build(), new SaveEgressFlowPlanProjectionRoot().name(), EgressFlow.class);
    }

    public static TimedIngressFlow saveTimedIngressFlowPlan(DgsQueryExecutor dgsQueryExecutor) {
        TimedIngressActionConfigurationInput timedIngress = TimedIngressActionConfigurationInput.newBuilder().name("timedIngress").type("org.deltafi.actions.TimedIngress").build();
        TimedIngressFlowPlanInput input = TimedIngressFlowPlanInput.newBuilder()
                .name("flowPlan")
                .type("TIMED_INGRESS")
                .description("description")
                .timedIngressAction(timedIngress)
                .targetFlow("target")
                .cronSchedule("*/5 * * * * *")
                .build();
        return executeQuery(dgsQueryExecutor, SaveTimedIngressFlowPlanGraphQLQuery.newRequest().timedIngressFlowPlan(input).build(), new SaveTimedIngressFlowPlanProjectionRoot().name(), TimedIngressFlow.class);
    }

    public static boolean removeTransformFlowPlan(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, RemoveTransformFlowPlanGraphQLQuery.newRequest().name("flowPlan").build());
    }

    public static boolean removeNormalizeFlowPlan(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, RemoveNormalizeFlowPlanGraphQLQuery.newRequest().name("flowPlan").build());
    }

    public static boolean removeEgressFlowPlan(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, RemoveEgressFlowPlanGraphQLQuery.newRequest().name("flowPlan").build());
    }

    public static boolean removeTimedIngressFlowPlan(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, RemoveTimedIngressFlowPlanGraphQLQuery.newRequest().name("flowPlan").build());
    }

    public static boolean startTransformFlow(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, StartTransformFlowGraphQLQuery.newRequest().flowName("sampleTransform").build());
    }

    public static boolean stopTransformFlow(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, StopTransformFlowGraphQLQuery.newRequest().flowName("sampleTransform").build());
    }

    public static boolean startNormalizeFlow(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, StartNormalizeFlowGraphQLQuery.newRequest().flowName(NORMALIZE_FLOW_NAME).build());
    }

    public static boolean stopNormalizeFlow(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, StopNormalizeFlowGraphQLQuery.newRequest().flowName(NORMALIZE_FLOW_NAME).build());
    }

    public static boolean startEgressFlow(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, StartEgressFlowGraphQLQuery.newRequest().flowName("sampleEgress").build());
    }

    public static boolean stopEgressFlow(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, StopEgressFlowGraphQLQuery.newRequest().flowName("sampleEgress").build());
    }

    public static boolean startTimedIngressFlow(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, StartTimedIngressFlowGraphQLQuery.newRequest().flowName("sampleTimedIngress").build());
    }

    public static boolean stopTimedIngressFlow(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, StopTimedIngressFlowGraphQLQuery.newRequest().flowName("sampleTimedIngress").build());
    }

    public static boolean setTimedIngressMemo(DgsQueryExecutor dgsQueryExecutor, String memo) {
        return executeQuery(dgsQueryExecutor, SetTimedIngressMemoGraphQLQuery.newRequest().flowName("sampleTimedIngress").memo(memo).build());
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

    private static class EmptyProjection extends BaseProjectionNode {

    }
}
