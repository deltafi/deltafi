package org.deltafi.core.domain.datafetchers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jayway.jsonpath.TypeRef;
import com.netflix.graphql.dgs.DgsQueryExecutor;
import com.netflix.graphql.dgs.client.codegen.GraphQLQueryRequest;
import org.deltafi.common.resource.Resource;
import org.deltafi.core.domain.generated.client.*;
import org.deltafi.core.domain.generated.types.*;
import org.deltafi.core.domain.repo.FlowPlanRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@TestPropertySource(properties = {"enableScheduling=false"})
class FlowPlanDatafetcherTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final SaveFlowPlanProjectionRoot FLOW_PLAN_PROJECTION_ROOT = new SaveFlowPlanProjectionRoot()
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

    private static final String DESCRIPTION = "description";
    private static final String VERSION = "version";
    @Autowired
    DgsQueryExecutor dgsQueryExecutor;

    @Autowired
    FlowPlanRepo flowPlanRepo;

    @BeforeEach
    public void setup() {
        flowPlanRepo.deleteAll();
    }

    @Test
    void testDeleteFlowPlan() {
        saveFlowPlan("planA", false);
        saveFlowPlan("planB", true);
        saveFlowPlan("planA", true);

        assertEquals(2, flowPlanRepo.count());
        assertTrue(removeFlowPlan("planB"));
        assertEquals(1, flowPlanRepo.count());
        assertEquals("planA", getFlowPlans().get(0).getName());
    }

    @Test
    void testExportFlowPlan() throws IOException {
        saveFlowPlan("planA", false);
        saveFlowPlan("planB", true);

        String exportedJson = exportFlowPlan("planA");
        String expected = Resource.read("/flowPlans/flow-plan-export-test.json");
        assertEquals(expected, exportedJson);
    }

    @Test
    public void testImportFlowPlan() throws IOException {
        /*
         * Another "Save FlowPlan" test with a more complete
         * FlowPlanInput and verify, using a JSON file in a
         * similar manner as the CLI.
         */
        FlowPlanInput flowPlanInput = OBJECT_MAPPER.readValue(Resource.read("/flowPlans/flow-plan-1.json"), FlowPlanInput.class);
        SaveFlowPlanGraphQLQuery saveFlowPlanGraphQLQuery = SaveFlowPlanGraphQLQuery.newRequest().flowPlan(flowPlanInput).build();

        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(saveFlowPlanGraphQLQuery, FLOW_PLAN_PROJECTION_ROOT);

        FlowPlan flowPlan = dgsQueryExecutor.executeAndExtractJsonPathAsObject(graphQLQueryRequest.serialize(),
                "data." + saveFlowPlanGraphQLQuery.getOperationName(), FlowPlan.class);

        assertEquals(1, flowPlanRepo.count());

        assertEquals("stix crossbar plan", flowPlan.getName());
        assertEquals("stix2_1", flowPlan.getIngressFlowConfigurations().get(0).getName());
        assertEquals("stix1_x", flowPlan.getIngressFlowConfigurations().get(1).getName());
        assertEquals("Stix2_1", flowPlan.getEgressFlowConfigurations().get(0).getName());
        assertEquals("Stix1_x", flowPlan.getEgressFlowConfigurations().get(1).getName());
        assertEquals("Stix1_xTo2_1TransformAction", flowPlan.getTransformActionConfigurations().get(0).getName());
        assertEquals("Stix2_1LoadAction", flowPlan.getLoadActionConfigurations().get(0).getName());
        assertEquals("Stix2_1FormatAction", flowPlan.getFormatActionConfigurations().get(0).getName());
        assertEquals("Stix1_xFormatAction", flowPlan.getFormatActionConfigurations().get(1).getName());
        assertEquals("Stix2_1ValidateAction", flowPlan.getValidateActionConfigurations().get(0).getName());
        assertEquals("Stix1_xValidateAction", flowPlan.getValidateActionConfigurations().get(1).getName());
        assertEquals("Stix2_1EgressAction", flowPlan.getEgressActionConfigurations().get(0).getName());
        assertEquals("Stix1_xEgressAction", flowPlan.getEgressActionConfigurations().get(1).getName());
        assertEquals("org.deltafi.stix", flowPlan.getSourcePlugin().getGroupId());
        assertEquals("deltafi-stix", flowPlan.getSourcePlugin().getArtifactId());
        assertEquals("0.17.0", flowPlan.getSourcePlugin().getVersion());

        Variable variable = Variable.newBuilder()
                .dataType(DATA_TYPE.STRING)
                .defaultValue("http://deltafi-egress-sink-service")
                .description("The URL to post the DeltaFile to")
                .name("egressUrl")
                .required(true).build();

        assertEquals(variable, flowPlan.getVariables().get(0));
    }

    @Test
    void testUpdateFlowPlan() {
        FlowPlan created = saveFlowPlan("planA", true);
        assertTrue(verifyFlowPlan(created, "planA", true));

        FlowPlan updated = saveFlowPlan("planA", false);
        assertEquals(1, flowPlanRepo.count());
        assertTrue(verifyFlowPlan(updated, "planA", false));
    }

    @Test
    void testGetFlowPlans() {
        saveFlowPlan("flowPlan1", true);
        saveFlowPlan("flowPlan2", true);

        List<FlowPlan> flowPlans = getFlowPlans();
        assertEquals(2, flowPlans.size());

        Set<String> names = new HashSet<>();
        flowPlans.stream().forEach(f -> names.add(f.getName()));
        assertTrue(names.containsAll(Arrays.asList("flowPlan1", "flowPlan2")));
    }

    @Test
    void testCreateFlowPlan() {
        FlowPlan flowPlan = saveFlowPlan("flowPlan", true);
        assertEquals(1, flowPlanRepo.count());
        assertTrue(verifyFlowPlan(flowPlan, "flowPlan", true));
    }

    private List<FlowPlan> getFlowPlans() {
        FlowPlansProjectionRoot projection = new FlowPlansProjectionRoot().name();

        FlowPlansGraphQLQuery flowPlansQuery = FlowPlansGraphQLQuery.newRequest().build();

        GraphQLQueryRequest graphQLQueryRequest =
                new GraphQLQueryRequest(flowPlansQuery, projection);

        TypeRef<List<FlowPlan>> listOfFlowPlans = new TypeRef<>() {
        };
        List<FlowPlan> flowPlans = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + flowPlansQuery.getOperationName(),
                listOfFlowPlans);

        return flowPlans;
    }

    private String exportFlowPlan(String planName) {
        ExportFlowPlanGraphQLQuery exportFlowPlan = ExportFlowPlanGraphQLQuery.newRequest().name(planName).build();

        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(exportFlowPlan, null);

        String exportedJson = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + exportFlowPlan.getOperationName(),
                String.class);

        return exportedJson;
    }

    private boolean removeFlowPlan(String planName) {
        RemoveFlowPlanGraphQLQuery removeFlowPlan = RemoveFlowPlanGraphQLQuery.newRequest().name(planName).build();

        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(removeFlowPlan, null);

        Boolean removed = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + removeFlowPlan.getOperationName(),
                Boolean.class);

        return removed.booleanValue();
    }

    private boolean verifyFlowPlan(FlowPlan plan, String name, boolean draft) {
        return plan.getName().equals(name) &&
                plan.getDescription().equals(DESCRIPTION) &&
                plan.getVersion().equals(VERSION) &&
                plan.getDraft() == draft &&
                plan.getIngressFlowConfigurations().size() == 1 &&
                plan.getEgressFlowConfigurations() == null;
    }

    private FlowPlan saveFlowPlan(String planName, boolean draft) {
        IngressFlowConfigurationInput ingressInput = IngressFlowConfigurationInput.newBuilder()
                .name("name")
                .loadActions(singletonList("loader"))
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
