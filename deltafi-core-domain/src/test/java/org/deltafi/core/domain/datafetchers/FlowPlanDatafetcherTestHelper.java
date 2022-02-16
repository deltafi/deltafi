package org.deltafi.core.domain.datafetchers;

import com.jayway.jsonpath.TypeRef;
import com.netflix.graphql.dgs.DgsQueryExecutor;
import com.netflix.graphql.dgs.client.codegen.GraphQLQueryRequest;
import org.deltafi.core.domain.generated.client.*;
import org.deltafi.core.domain.generated.types.*;

import java.util.List;

import static java.util.Collections.singletonList;

public class FlowPlanDatafetcherTestHelper {

    public static final SaveFlowPlanProjectionRoot FLOW_PLAN_PROJECTION_ROOT = new SaveFlowPlanProjectionRoot()
            .name()
            .description()
            .version()
            .draft()
            .ingressFlowConfigurations()
            .name()
            .parent()
            .egressFlowConfigurations()
            .name()
            .parent()
            .transformActionConfigurations()
            .name()
            .parent()
            .loadActionConfigurations()
            .name()
            .parent()
            .enrichActionConfigurations()
            .name()
            .parent()
            .formatActionConfigurations()
            .name()
            .parent()
            .validateActionConfigurations()
            .name()
            .parent()
            .egressActionConfigurations()
            .name()
            .parent()
            .sourcePlugin()
            .groupId()
            .artifactId()
            .version()
            .parent()
            .variables()
            .name()
            .dataType()
            .parent()
            .description()
            .defaultValue()
            .required()
            .parent();

    public static final String DESCRIPTION = "description";
    public static final String VERSION = "version";

    public static List<FlowPlan> getFlowPlans(DgsQueryExecutor dgsQueryExecutor) {
        FlowPlansProjectionRoot projection = new FlowPlansProjectionRoot().name();

        FlowPlansGraphQLQuery flowPlansQuery = FlowPlansGraphQLQuery.newRequest().build();

        GraphQLQueryRequest graphQLQueryRequest =
                new GraphQLQueryRequest(flowPlansQuery, projection);

        TypeRef<List<FlowPlan>> listOfFlowPlans = new TypeRef<>() {
        };

        return dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + flowPlansQuery.getOperationName(),
                listOfFlowPlans);
    }

    public static String exportFlowPlan(String planName, DgsQueryExecutor dgsQueryExecutor) {
        ExportFlowPlanGraphQLQuery exportFlowPlan = ExportFlowPlanGraphQLQuery.newRequest().name(planName).build();

        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(exportFlowPlan, null);

        return dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + exportFlowPlan.getOperationName(),
                String.class);
    }

    public static boolean removeFlowPlan(String planName, DgsQueryExecutor dgsQueryExecutor) {
        RemoveFlowPlanGraphQLQuery removeFlowPlan = RemoveFlowPlanGraphQLQuery.newRequest().name(planName).build();

        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(removeFlowPlan, null);

        return dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + removeFlowPlan.getOperationName(),
                Boolean.class);
    }

    public static boolean verifyFlowPlan(FlowPlan plan, String name, boolean draft) {
        return plan.getName().equals(name) &&
                plan.getDescription().equals(DESCRIPTION) &&
                plan.getVersion().equals(VERSION) &&
                plan.getDraft() == draft &&
                plan.getIngressFlowConfigurations().size() == 1 &&
                plan.getEgressFlowConfigurations() == null;
    }

    public static FlowPlan saveFlowPlan(String planName, boolean draft, DgsQueryExecutor dgsQueryExecutor) {
        IngressFlowConfigurationInput ingressInput = IngressFlowConfigurationInput.newBuilder()
                .name("name")
                .loadAction("loader")
                .transformActions(singletonList("transformer"))
                .type("test-type")
                .build();

        PluginCoordinatesInput plugin = PluginCoordinatesInput.newBuilder()
                .artifactId("plugin-a").groupId("dev.plugin").version("1.0").build();

        VariableInput variable = VariableInput.newBuilder()
                .dataType(DATA_TYPE.STRING).name("egressUrl")
                .description("The URL to post the DeltaFile to")
                .defaultValue("http://deltafi-egress-sink-service")
                .required(true).build();

        FlowPlanInput input = FlowPlanInput.newBuilder()
                .name(planName)
                .description(DESCRIPTION)
                .version(VERSION)
                .draft(draft)
                .ingressFlowConfigurations(singletonList(ingressInput))
                .sourcePlugin(plugin)
                .variables(List.of(variable))
                .build();

        SaveFlowPlanGraphQLQuery saveFlowPlan = SaveFlowPlanGraphQLQuery
                .newRequest().flowPlan(input).build();

        GraphQLQueryRequest graphQLQueryRequest =
                new GraphQLQueryRequest(saveFlowPlan, FLOW_PLAN_PROJECTION_ROOT);

        return dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + saveFlowPlan.getOperationName(),
                FlowPlan.class);
    }
}
