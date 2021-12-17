package org.deltafi.core.domain.datafetchers;

import com.jayway.jsonpath.TypeRef;
import com.netflix.graphql.dgs.DgsQueryExecutor;
import com.netflix.graphql.dgs.client.codegen.GraphQLQueryRequest;
import org.deltafi.core.domain.generated.client.*;
import org.deltafi.core.domain.generated.types.FlowPlan;
import org.deltafi.core.domain.generated.types.FlowPlanInput;
import org.deltafi.core.domain.generated.types.IngressFlowConfigurationInput;
import org.deltafi.core.domain.repo.FlowPlanRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@TestPropertySource(properties = "enableScheduling=false")
class FlowPlanDatafetcherTest {

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

        FlowPlanInput input = FlowPlanInput.newBuilder()
                .name(planName)
                .description(DESCRIPTION)
                .version(VERSION)
                .draft(draft)
                .ingressFlowConfigurations(singletonList(ingressInput))
                .build();

        SaveFlowPlanProjectionRoot projection = new SaveFlowPlanProjectionRoot()
                .name()
                .description()
                .version()
                .draft()
                .ingressFlowConfigurations()
                .name()
                .parent()
                .egressFlowConfigurations()
                .name()
                .parent();

        SaveFlowPlanGraphQLQuery saveFlowPlan = SaveFlowPlanGraphQLQuery
                .newRequest().flowPlan(input).build();

        GraphQLQueryRequest graphQLQueryRequest =
                new GraphQLQueryRequest(saveFlowPlan, projection);

        return dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + saveFlowPlan.getOperationName(),
                FlowPlan.class);
    }
}
