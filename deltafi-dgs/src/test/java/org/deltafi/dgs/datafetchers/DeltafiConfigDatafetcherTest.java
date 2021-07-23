package org.deltafi.dgs.datafetchers;

import com.jayway.jsonpath.TypeRef;
import com.netflix.graphql.dgs.DgsQueryExecutor;
import com.netflix.graphql.dgs.client.codegen.BaseProjectionNode;
import com.netflix.graphql.dgs.client.codegen.GraphQLQuery;
import com.netflix.graphql.dgs.client.codegen.GraphQLQueryRequest;
import org.deltafi.dgs.generated.client.*;
import org.deltafi.dgs.generated.types.*;
import org.deltafi.dgs.repo.DeltaFiConfigRepo;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
    DeltaFiConfigRepo deltaFiConfigRepo;

    @BeforeEach
    public void setup() {
        deltaFiConfigRepo.deleteAll();
    }

    @Test
    void addLoadActionGroupConfigTest() {
        LoadActionGroupConfigurationInput input = LoadActionGroupConfigurationInput.newBuilder()
                .name(NAME)
                .loadActions(singletonList("loader")).build();

        DeltaFiConfigsProjectionRoot projection = rootProject().onLoadActionGroupConfiguration()
                .loadActions()
                .parent();

        AddLoadActionGroupGraphQLQuery addLoadActionGroup = AddLoadActionGroupGraphQLQuery.newRequest().loadActionGroupConfiguration(input).build();
        LoadActionGroupConfiguration config = executeRequest(addLoadActionGroup, projection, LoadActionGroupConfiguration.class);

        assertEquals(NAME, config.getName());
        assertNotNull(config.getCreated());
        assertNotNull(config.getModified());
        assertEquals("loader", config.getLoadActions().get(0));
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
    void registerDomainEndpointConfigurationTest() {
        DomainEndpointConfigurationInput input = DomainEndpointConfigurationInput.newBuilder()
                .name(NAME)
                .apiVersion("1.0.0")
                .domainVersion("1.0.2")
                .url("url").build();

        DeltaFiConfigsProjectionRoot projection = rootProject()
                .onDomainEndpointConfiguration()
                    .url()
                    .domainVersion()
                    .parent();

        RegisterDomainEndpointGraphQLQuery register = RegisterDomainEndpointGraphQLQuery.newRequest().domainEndpointConfiguration(input).build();
        DomainEndpointConfiguration config = executeRequest(register, projection, DomainEndpointConfiguration.class);

        assertEquals(NAME, config.getName());
        assertNotNull(config.getCreated());
        assertNotNull(config.getModified());
        assertEquals("1.0.0", config.getApiVersion());
        assertEquals("1.0.2", config.getDomainVersion());
        assertEquals("url", config.getUrl());
    }

    @Test
    void findConfigsTest() {
        ConfigQueryInput configQueryInput = ConfigQueryInput.newBuilder().configType(ConfigType.DOMAIN_ENDPOINT).name(NAME).build();

        DomainEndpointConfigurationInput input = DomainEndpointConfigurationInput.newBuilder()
                .name(NAME)
                .apiVersion("1.0.0")
                .domainVersion("1.0.2")
                .url("url").build();

        DeltaFiConfigsProjectionRoot projection = rootProject()
                .onDomainEndpointConfiguration()
                .url()
                .parent();

        RegisterDomainEndpointGraphQLQuery register = RegisterDomainEndpointGraphQLQuery.newRequest().domainEndpointConfiguration(input).build();
        executeRequest(register, projection, DomainEndpointConfiguration.class);

        DeltaFiConfigsGraphQLQuery findConfig = DeltaFiConfigsGraphQLQuery.newRequest().configQuery(configQueryInput).build();

        TypeRef<List<DeltaFiConfiguration>> listOfConfigs = new TypeRef<>() {};
        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(findConfig, projection);
        List<DeltaFiConfiguration> configs = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + findConfig.getOperationName(),
                listOfConfigs);

        assertTrue(configs.get(0) instanceof DomainEndpointConfiguration);

        DomainEndpointConfiguration domainConfig = (DomainEndpointConfiguration) configs.get(0);
        assertEquals(NAME, domainConfig.getName());
        assertEquals("url", domainConfig.getUrl());
        Assertions.assertNull(domainConfig.getDomainVersion()); // not in the projection should be null
    }

    @Test
    void deleteConfigsTest() {
        DomainEndpointConfigurationInput input = DomainEndpointConfigurationInput.newBuilder()
                .name(NAME)
                .apiVersion("1.0.0")
                .domainVersion("1.0.2")
                .url("url").build();

        DeltaFiConfigsProjectionRoot projection = rootProject();

        RegisterDomainEndpointGraphQLQuery register = RegisterDomainEndpointGraphQLQuery.newRequest().domainEndpointConfiguration(input).build();
        executeRequest(register, projection, DomainEndpointConfiguration.class);

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
