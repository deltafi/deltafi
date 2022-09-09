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

import com.jayway.jsonpath.TypeRef;
import com.netflix.graphql.dgs.DgsQueryExecutor;
import com.netflix.graphql.dgs.client.codegen.BaseProjectionNode;
import com.netflix.graphql.dgs.client.codegen.GraphQLQuery;
import com.netflix.graphql.dgs.client.codegen.GraphQLQueryRequest;
import org.deltafi.common.types.KeyValue;
import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.common.types.Variable;
import org.deltafi.common.types.VariableDataType;
import org.deltafi.core.domain.generated.client.*;
import org.deltafi.core.domain.generated.types.*;
import org.deltafi.core.domain.types.EgressFlow;
import org.deltafi.core.domain.types.EgressFlowPlan;
import org.deltafi.core.domain.types.IngressFlow;
import org.deltafi.core.domain.types.IngressFlowPlan;

import java.util.List;

public class FlowPlanDatafetcherTestHelper {

    public static final PluginCoordinates PLUGIN_COORDINATES = PluginCoordinates.builder().artifactId("test-plugin").groupId("org.deltafi").version("1.0.0").build();

    public static IngressFlowPlan getIngressFlowPlan(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, GetIngressFlowPlanGraphQLQuery.newRequest().planName("ingressPlan").build(), new GetIngressFlowPlanProjectionRoot().name(), IngressFlowPlan.class);
    }

    public static EgressFlowPlan getEgressFlowPlan(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, GetEgressFlowPlanGraphQLQuery.newRequest().planName("egressPlan").build(), new GetEgressFlowPlanProjectionRoot().name(), EgressFlowPlan.class);
    }

    public static IngressFlow validateIngressFlow(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, ValidateIngressFlowGraphQLQuery.newRequest().flowName("ingressFlow").build(), new ValidateIngressFlowProjectionRoot().name(), IngressFlow.class);
    }

    public static EgressFlow validateEgressFlow(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, ValidateEgressFlowGraphQLQuery.newRequest().flowName("egressFlow").build(), new ValidateIngressFlowProjectionRoot().name(), EgressFlow.class);
    }

    public static List<Flows> getFlows(DgsQueryExecutor dgsQueryExecutor) {
        TypeRef<List<Flows>> typeRef = new TypeRef<>() {};
        return executeQuery(dgsQueryExecutor, GetFlowsGraphQLQuery.newRequest().build(), new GetFlowsProjectionRoot().sourcePlugin().artifactId().parent().ingressFlows().name().parent().egressFlows().name().root(), typeRef);
    }

    public static SystemFlows getRunningFlows(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, GetRunningFlowsGraphQLQuery.newRequest().build(),
                new GetRunningFlowsProjectionRoot().ingress().name().parent().enrich().name().parent().egress().name().root(), SystemFlows.class);
    }

    public static SystemFlows getAllFlows(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, GetAllFlowsGraphQLQuery.newRequest().build(),
                new GetAllFlowsProjectionRoot().ingress().name().parent().egress().name().root(), SystemFlows.class);
    }

    public static IngressFlow getIngressFlow(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, GetIngressFlowGraphQLQuery.newRequest().flowName("ingressFlow").build(),
                new GetIngressFlowProjectionRoot().name(), IngressFlow.class);
    }

    public static EgressFlow getEgressFlow(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, GetEgressFlowGraphQLQuery.newRequest().flowName("egressFlow").build(),
                new GetEgressFlowProjectionRoot().name(), EgressFlow.class);
    }

    public static List<ActionFamily> getActionFamilies(DgsQueryExecutor dgsQueryExecutor) {
        TypeRef<List<ActionFamily>> typeRef = new TypeRef<>() {};
        return executeQuery(dgsQueryExecutor, GetActionNamesByFamilyGraphQLQuery.newRequest().build(), new GetActionNamesByFamilyProjectionRoot().family().actionNames(), typeRef);
    }

    public static IngressFlow saveIngressFlowPlan(DgsQueryExecutor dgsQueryExecutor) {
        LoadActionConfigurationInput loadActionConfigurationInput = LoadActionConfigurationInput.newBuilder().name("loader").type("org.deltafi.action.Loader").build();
        IngressFlowPlanInput input = IngressFlowPlanInput.newBuilder()
                .sourcePlugin(PLUGIN_COORDINATES)
                .name("flowPlan")
                .description("description")
                .loadAction(loadActionConfigurationInput)
                .build();

        return executeQuery(dgsQueryExecutor, SaveIngressFlowPlanGraphQLQuery.newRequest().ingressFlowPlan(input).build(), new SaveIngressFlowPlanProjectionRoot().name().flowStatus().state().parent().parent(), IngressFlow.class);
    }

    public static EgressFlow saveEgressFlowPlan(DgsQueryExecutor dgsQueryExecutor) {
        FormatActionConfigurationInput format = FormatActionConfigurationInput.newBuilder().name("format").type("org.deltafi.actions.Formatter").requiresDomains(List.of("domain")).build();
        EgressActionConfigurationInput egress = EgressActionConfigurationInput.newBuilder().name("egress").type("org.deltafi.actions.EgressAction").build();
        EgressFlowPlanInput input = EgressFlowPlanInput.newBuilder()
                .name("flowPlan")
                .sourcePlugin(PLUGIN_COORDINATES)
                .description("description")
                .formatAction(format)
                .egressAction(egress)
                .build();
        return executeQuery(dgsQueryExecutor, SaveEgressFlowPlanGraphQLQuery.newRequest().egressFlowPlan(input).build(), new SaveEgressFlowPlanProjectionRoot().name(), EgressFlow.class);
    }

    public static boolean removeIngressFlowPlan(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, RemoveIngressFlowPlanGraphQLQuery.newRequest().name("flowPlan").build());
    }

    public static boolean removeEgressFlowPlan(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, RemoveEgressFlowPlanGraphQLQuery.newRequest().name("flowPlan").build());
    }

    public static boolean startIngressFlow(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, StartIngressFlowGraphQLQuery.newRequest().flowName("ingressFlow").build());
    }

    public static boolean stopIngressFlow(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, StopIngressFlowGraphQLQuery.newRequest().flowName("ingressFlow").build());
    }

    public static boolean startEgressFlow(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, StartEgressFlowGraphQLQuery.newRequest().flowName("egressFlow").build());
    }

    public static boolean stopEgressFlow(DgsQueryExecutor dgsQueryExecutor) {
        return executeQuery(dgsQueryExecutor, StopEgressFlowGraphQLQuery.newRequest().flowName("egressFlow").build());
    }

    public static boolean savePluginVariables(DgsQueryExecutor dgsQueryExecutor) {
        List<Variable> variableInputs = List.of(Variable.newBuilder().name("var").defaultValue("default").required(false).description("description").dataType(VariableDataType.STRING).build());
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
