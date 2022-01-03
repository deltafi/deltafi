package org.deltafi.core.domain.datafetchers;

import com.jayway.jsonpath.TypeRef;
import com.netflix.graphql.dgs.DgsQueryExecutor;
import com.netflix.graphql.dgs.client.codegen.BaseProjectionNode;
import com.netflix.graphql.dgs.client.codegen.GraphQLQuery;
import com.netflix.graphql.dgs.client.codegen.GraphQLQueryRequest;
import org.deltafi.core.domain.configuration.DeltafiRuntimeConfiguration;
import org.deltafi.core.domain.generated.client.*;
import org.deltafi.core.domain.generated.types.*;
import org.deltafi.core.domain.repo.DeltaFiRuntimeConfigRepo;
import org.deltafi.core.domain.validation.DeltafiRuntimeConfigurationValidator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertTrue;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@TestPropertySource(properties = "enableScheduling=false")
class DeltafiConfigDatafetcherTest {

    public static final String NAME = "myAction";

    @Autowired
    DgsQueryExecutor dgsQueryExecutor;

    @Autowired
    DeltaFiRuntimeConfigRepo deltaFiConfigRepo;

    @MockBean
    DeltafiRuntimeConfigurationValidator configValidator;

    @BeforeEach
    public void setup() {
        deltaFiConfigRepo.save(new DeltafiRuntimeConfiguration());
    }

    @Test
    void addIngressFlowConfigTest() {
        IngressFlowConfigurationInput input = IngressFlowConfigurationInput.newBuilder()
                .name(NAME)
                .loadActions(singletonList("loader"))
                .transformActions(singletonList("transformer"))
                .type("test-type")
                .build();

        DeltaFiConfigsProjectionRoot projection = rootProject()
                .name()
                .onIngressFlowConfiguration()
                    .loadActions()
                    .transformActions()
                    .parent();

        AddIngressFlowGraphQLQuery addFlow = AddIngressFlowGraphQLQuery.newRequest().ingressFlowConfiguration(input).build();
        IngressFlowConfiguration config = executeRequest(addFlow, projection, IngressFlowConfiguration.class);

        assertEquals(NAME, config.getName());
        assertNotNull(config.getCreated());
        assertNotNull(config.getModified());
        assertEquals("loader", config.getLoadActions().get(0));
        assertEquals("transformer", config.getTransformActions().get(0));
    }

    @Test
    void addEgressFlowConfigTest() {
        EgressFlowConfigurationInput input = EgressFlowConfigurationInput.newBuilder()
                .name(NAME)
                .includeIngressFlows(singletonList("flow"))
                .excludeIngressFlows(singletonList("x-flow"))
                .formatAction("formatter")
                .validateActions(singletonList("validator")).build();

        DeltaFiConfigsProjectionRoot projection = rootProject()
                .onEgressFlowConfiguration()
                .excludeIngressFlows()
                .includeIngressFlows()
                .formatAction()
                .validateActions()
                .egressAction()
                .parent();

        AddEgressFlowGraphQLQuery addFlow = AddEgressFlowGraphQLQuery.newRequest().egressFlowConfiguration(input).build();
        EgressFlowConfiguration config = executeRequest(addFlow, projection, EgressFlowConfiguration.class);

        assertEquals(NAME, config.getName());
        assertNotNull(config.getCreated());
        assertNotNull(config.getModified());
        assertEquals("MyActionEgressAction", config.getEgressAction());
        assertEquals("formatter", config.getFormatAction());
        assertEquals("validator", config.getValidateActions().get(0));
        assertEquals("flow", config.getIncludeIngressFlows().get(0));
        assertEquals("x-flow", config.getExcludeIngressFlows().get(0));
    }

    @Test
    void findConfigsTest() {
        ConfigQueryInput configQueryInput = ConfigQueryInput.newBuilder().configType(ConfigType.LOAD_ACTION).name(NAME).build();

        LoadActionConfigurationInput input = LoadActionConfigurationInput.newBuilder()
                .name(NAME)
                .apiVersion("1.0.0")
                .consumes("json").type("org.deltafi.passthrough.action.RoteLoadAction").build();

        DeltaFiConfigsProjectionRoot projection = rootProject()
                .onLoadActionConfiguration()
                .consumes()
                .parent();

        RegisterLoadActionGraphQLQuery register = RegisterLoadActionGraphQLQuery.newRequest().loadActionConfiguration(input).build();
        executeRequest(register, projection, LoadActionConfiguration.class);

        DeltaFiConfigsGraphQLQuery findConfig = DeltaFiConfigsGraphQLQuery.newRequest().configQuery(configQueryInput).build();

        TypeRef<List<DeltaFiConfiguration>> listOfConfigs = new TypeRef<>() {};
        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(findConfig, projection);
        List<DeltaFiConfiguration> configs = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + findConfig.getOperationName(),
                listOfConfigs);

        assertTrue(configs.get(0) instanceof LoadActionConfiguration);

        LoadActionConfiguration loadActionConfiguration = (LoadActionConfiguration) configs.get(0);
        assertEquals(NAME, loadActionConfiguration.getName());
        assertEquals("json", loadActionConfiguration.getConsumes());
        Assertions.assertNull(loadActionConfiguration.getType()); // not in the projection should be null
    }

    @Test
    void deleteConfigsTest() {
        LoadActionConfigurationInput input = LoadActionConfigurationInput.newBuilder()
                .name(NAME)
                .apiVersion("1.0.0")
                .consumes("json").type("org.deltafi.passthrough.action.RoteLoadAction").build();

        DeltaFiConfigsProjectionRoot projection = rootProject();

        RegisterLoadActionGraphQLQuery register = RegisterLoadActionGraphQLQuery.newRequest().loadActionConfiguration(input).build();
        executeRequest(register, projection, LoadActionConfiguration.class);

        RemoveDeltaFiConfigsGraphQLQuery remove = RemoveDeltaFiConfigsGraphQLQuery.newRequest().build();
        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(remove, null);
        Integer removed = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + remove.getOperationName(),
                Integer.class);
        assertEquals(1, removed.intValue());
    }

    DeltaFiConfigsProjectionRoot rootProject() {
        return new DeltaFiConfigsProjectionRoot()
                .name()
                .created()
                .modified()
                .apiVersion();
    }

    <C extends DeltaFiConfiguration> C executeRequest(GraphQLQuery query, BaseProjectionNode projection, Class<C> clazz) {
        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(query, projection);
        return dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + query.getOperationName(),
                clazz);
    }
}
