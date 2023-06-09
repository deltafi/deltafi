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
import org.deltafi.core.generated.client.*;
import org.deltafi.core.generated.types.*;
import org.deltafi.core.types.EgressFlow;
import org.deltafi.core.types.IngressFlow;
import org.deltafi.core.types.TransformFlow;

import java.util.List;

public class FlowPlanDatafetcherTestHelper {

    public static final PluginCoordinates PLUGIN_COORDINATES = PluginCoordinates.builder().artifactId("test-plugin").groupId("org.deltafi").version("1.0.0").build();

    public static TransformFlowPlan getTransformFlowPlan(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, GetTransformFlowPlanGraphQLQuery.newRequest().planName("transformPlan").build(), new GetTransformFlowPlanProjectionRoot().name().type().description().egressAction().name().actionType().type(), TransformFlowPlan.class);
    }
    
    public static IngressFlowPlan getIngressFlowPlan(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, GetIngressFlowPlanGraphQLQuery.newRequest().planName("ingressPlan").build(), new GetIngressFlowPlanProjectionRoot().name().type().description().loadAction().name().actionType().type(), IngressFlowPlan.class);
    }

    public static EgressFlowPlan getEgressFlowPlan(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, GetEgressFlowPlanGraphQLQuery.newRequest().planName("egressPlan").build(), new GetEgressFlowPlanProjectionRoot().name().type().description().formatAction().name().actionType().type().requiresDomains().parent().egressAction().name().actionType().type(), EgressFlowPlan.class);
    }

    public static TransformFlow validateTransformFlow(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, ValidateTransformFlowGraphQLQuery.newRequest().flowName("sampleTransform").build(), new ValidateTransformFlowProjectionRoot().name(), TransformFlow.class);
    }

    public static IngressFlow validateIngressFlow(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, ValidateIngressFlowGraphQLQuery.newRequest().flowName("sampleIngress").build(), new ValidateIngressFlowProjectionRoot().name(), IngressFlow.class);
    }

    public static FlowNames getFlowNames(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, GetFlowNamesGraphQLQuery.newRequest().build(),
                new GetFlowNamesProjectionRoot().transform().ingress().enrich().egress(), FlowNames.class);
    }

    public static EgressFlow validateEgressFlow(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, ValidateEgressFlowGraphQLQuery.newRequest().flowName("sampleEgress").build(), new ValidateIngressFlowProjectionRoot().name(), EgressFlow.class);
    }

    public static List<Flows> getFlows(DgsQueryExecutor dgsQueryExecutor) {
        TypeRef<List<Flows>> typeRef = new TypeRef<>() {};
        return executeQuery(dgsQueryExecutor, GetFlowsGraphQLQuery.newRequest().build(), new GetFlowsProjectionRoot().sourcePlugin().artifactId().parent().transformFlows().name().parent().ingressFlows().name().parent().egressFlows().name().root(), typeRef);
    }

    public static SystemFlows getRunningFlows(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, GetRunningFlowsGraphQLQuery.newRequest().build(),
                new GetRunningFlowsProjectionRoot().transform().name().parent().ingress().name().parent().enrich().name().parent().egress().name().root(), SystemFlows.class);
    }

    public static SystemFlows getAllFlows(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, GetAllFlowsGraphQLQuery.newRequest().build(),
                new GetAllFlowsProjectionRoot().transform().name().parent().ingress().name().parent().egress().name().root(), SystemFlows.class);
    }

    public static TransformFlow getTransformFlow(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, GetTransformFlowGraphQLQuery.newRequest().flowName("sampleTransform").build(),
                new GetTransformFlowProjectionRoot().name(), TransformFlow.class);
    }

    public static IngressFlow getIngressFlow(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, GetIngressFlowGraphQLQuery.newRequest().flowName("sampleIngress").build(),
                new GetIngressFlowProjectionRoot().name(), IngressFlow.class);
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
        EgressActionConfigurationInput egressActionConfigurationInput = EgressActionConfigurationInput.newBuilder().name("egress").actionType("EGRESS").type("org.deltafi.action.Egress").build();
        TransformFlowPlanInput input = TransformFlowPlanInput.newBuilder()
                .name("flowPlan")
                .type("TRANSFORM")
                .description("description")
                .sourcePlugin(PLUGIN_COORDINATES)
                .egressAction(egressActionConfigurationInput)
                .build();

        return executeQuery(dgsQueryExecutor, SaveTransformFlowPlanGraphQLQuery.newRequest().transformFlowPlan(input).build(), new SaveTransformFlowPlanProjectionRoot().name().flowStatus().state().parent().parent(), TransformFlow.class);
    }

    public static IngressFlow saveIngressFlowPlan(DgsQueryExecutor dgsQueryExecutor) {
        LoadActionConfigurationInput loadActionConfigurationInput = LoadActionConfigurationInput.newBuilder().name("loader").actionType("LOAD").type("org.deltafi.action.Loader").build();
        IngressFlowPlanInput input = IngressFlowPlanInput.newBuilder()
                .name("flowPlan")
                .type("INGRESS")
                .description("description")
                .sourcePlugin(PLUGIN_COORDINATES)
                .loadAction(loadActionConfigurationInput)
                .build();

        return executeQuery(dgsQueryExecutor, SaveIngressFlowPlanGraphQLQuery.newRequest().ingressFlowPlan(input).build(), new SaveIngressFlowPlanProjectionRoot().name().flowStatus().state().parent().parent(), IngressFlow.class);
    }

    public static EgressFlow saveEgressFlowPlan(DgsQueryExecutor dgsQueryExecutor) {
        FormatActionConfigurationInput format = FormatActionConfigurationInput.newBuilder().name("format").actionType("FORMAT").type("org.deltafi.actions.Formatter").requiresDomains(List.of("domain")).build();
        EgressActionConfigurationInput egress = EgressActionConfigurationInput.newBuilder().name("egress").actionType("EGRESS").type("org.deltafi.actions.EgressAction").build();
        EgressFlowPlanInput input = EgressFlowPlanInput.newBuilder()
                .name("flowPlan")
                .type("EGRESS")
                .description("description")
                .sourcePlugin(PLUGIN_COORDINATES)
                .formatAction(format)
                .egressAction(egress)
                .build();
        return executeQuery(dgsQueryExecutor, SaveEgressFlowPlanGraphQLQuery.newRequest().egressFlowPlan(input).build(), new SaveEgressFlowPlanProjectionRoot().name(), EgressFlow.class);
    }

    public static boolean removeTransformFlowPlan(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, RemoveTransformFlowPlanGraphQLQuery.newRequest().name("flowPlan").build());
    }

    public static boolean removeIngressFlowPlan(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, RemoveIngressFlowPlanGraphQLQuery.newRequest().name("flowPlan").build());
    }

    public static boolean removeEgressFlowPlan(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, RemoveEgressFlowPlanGraphQLQuery.newRequest().name("flowPlan").build());
    }

    public static boolean startTransformFlow(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, StartTransformFlowGraphQLQuery.newRequest().flowName("sampleTransform").build());
    }

    public static boolean stopTransformFlow(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, StopTransformFlowGraphQLQuery.newRequest().flowName("sampleTransform").build());
    }

    public static boolean startIngressFlow(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, StartIngressFlowGraphQLQuery.newRequest().flowName("sampleIngress").build());
    }

    public static boolean stopIngressFlow(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, StopIngressFlowGraphQLQuery.newRequest().flowName("sampleIngress").build());
    }

    public static boolean startEgressFlow(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, StartEgressFlowGraphQLQuery.newRequest().flowName("sampleEgress").build());
    }

    public static boolean stopEgressFlow(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, StopEgressFlowGraphQLQuery.newRequest().flowName("sampleEgress").build());
    }

    public static boolean savePluginVariables(DgsQueryExecutor dgsQueryExecutor) {
        List<Variable> variableInputs = List.of(Variable.builder().name("var").defaultValue("default").required(false).description("description").dataType(VariableDataType.STRING).build());
        PluginVariablesInput pluginVariablesInput = PluginVariablesInput.newBuilder().sourcePlugin(PLUGIN_COORDINATES).variables(variableInputs).build();
        return executeQuery(dgsQueryExecutor, SavePluginVariablesGraphQLQuery.newRequest().pluginVariablesInput(pluginVariablesInput).build());
    }

    public static boolean setPluginVariableValues(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, SetPluginVariableValuesGraphQLQuery.newRequest().pluginCoordinates(PLUGIN_COORDINATES).variables(List.of(new KeyValue("key", "value"))).build());
    }

    static <T> T executeQuery(DgsQueryExecutor dgsQueryExecutor, GraphQLQuery query, BaseProjectionNode projection, TypeRef<T> typeRef) {
        return dgsQueryExecutor.executeAndExtractJsonPathAsObject(new GraphQLQueryRequest(query, projection).serialize(), "data." + query.getOperationName(), typeRef);
    }

    static boolean executeQuery(DgsQueryExecutor dgsQueryExecutor, GraphQLQuery query) {
        return executeQuery(dgsQueryExecutor, query, null, Boolean.class);
    }

    static <T> T executeQuery(DgsQueryExecutor dgsQueryExecutor, GraphQLQuery query, BaseProjectionNode projection, Class<T> clazz) {
        return dgsQueryExecutor.executeAndExtractJsonPathAsObject(new GraphQLQueryRequest(query, projection).serialize(), "data." + query.getOperationName(), clazz);
    }
}
