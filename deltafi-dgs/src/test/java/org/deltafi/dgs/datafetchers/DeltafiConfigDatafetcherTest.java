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

import java.util.List;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertTrue;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class DeltafiConfigDatafetcherTest {

    public static final String NAME = "myAction";
    public static final String CONSUMES = "xml";
    public static final String PRODUCES = "json";
    public static final String DOMAIN = "domain";
    public static final String ENRICHMENT = "enrichment";

    @Autowired
    DgsQueryExecutor dgsQueryExecutor;

    @Autowired
    DeltaFiConfigRepo deltaFiConfigRepo;

    @BeforeEach
    public void setup() {
        deltaFiConfigRepo.deleteAll();
    }

    @Test
    void addTransformConfigTest() {
        TransformActionConfigurationInput transformInput = TransformActionConfigurationInput.newBuilder()
                .name(NAME).consumes(CONSUMES).produces(PRODUCES).build();

        DeltaFiConfigProjectionRoot projection = rootProject().onTransformActionConfiguration()
                .consumes()
                .produces()
                .parent();

        RegisterTransformActionGraphQLQuery transformRequest = RegisterTransformActionGraphQLQuery.newRequest().transformActionConfiguration(transformInput).build();

        TransformActionConfiguration config = executeRequest(transformRequest, projection, TransformActionConfiguration.class);

        assertEquals(NAME, config.getName());
        assertNotNull(config.getCreated());
        assertNotNull(config.getModified());
        assertEquals(CONSUMES, config.getConsumes());
        assertEquals(PRODUCES, config.getProduces());
    }

    @Test
    void addLoadActionConfigTest() {
        KeyValueInput keyValueInput = KeyValueInput.newBuilder().key("version").value("1").build();

        LoadActionConfigurationInput loadConfigInput = LoadActionConfigurationInput.newBuilder().name(NAME)
                .consumes(CONSUMES).requiresMetadataKeyValues(singletonList(keyValueInput)).build();

        DeltaFiConfigProjectionRoot projection = rootProject().onLoadActionConfiguration()
                .consumes()
                .requiresMetadataKeyValues()
                    .key()
                    .value()
                    .parent()
                .parent();

        RegisterLoadActionGraphQLQuery loadActionGraphQLQuery = RegisterLoadActionGraphQLQuery.newRequest().loadActionConfiguration(loadConfigInput).build();
        LoadActionConfiguration config = executeRequest(loadActionGraphQLQuery, projection, LoadActionConfiguration.class);

        assertEquals(NAME, config.getName());
        assertNotNull(config.getCreated());
        assertNotNull(config.getModified());
        assertEquals(CONSUMES, config.getConsumes());
        assertEquals("version", config.getRequiresMetadataKeyValues().get(0).getKey());
        assertEquals("1", config.getRequiresMetadataKeyValues().get(0).getValue());
    }

    @Test
    void addLoadActionGroupConfigTest() {
        LoadActionGroupConfigurationInput input = LoadActionGroupConfigurationInput.newBuilder()
                .name(NAME)
                .loadActions(singletonList("loader")).build();

        DeltaFiConfigProjectionRoot projection = rootProject().onLoadActionGroupConfiguration()
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
    void addEnrichActionConfigTest() {
        EnrichActionConfigurationInput input = EnrichActionConfigurationInput.newBuilder().name(NAME)
                .requiresDomains(singletonList(DOMAIN))
                .requiresEnrichment(singletonList(ENRICHMENT)).build();

        DeltaFiConfigProjectionRoot projection = rootProject()
                .onEnrichActionConfiguration()
                .requiresEnrichment()
                .requiresDomains()
                .parent();

        RegisterEnrichActionGraphQLQuery register = RegisterEnrichActionGraphQLQuery.newRequest().enrichActionConfiguration(input).build();
        EnrichActionConfiguration config = executeRequest(register, projection, EnrichActionConfiguration.class);

        assertEquals(NAME, config.getName());
        assertNotNull(config.getCreated());
        assertNotNull(config.getModified());
        assertEquals(DOMAIN, config.getRequiresDomains().get(0));
        assertEquals(ENRICHMENT, config.getRequiresEnrichment().get(0));
    }

    @Test
    void addFormatActionConfigTest() {
        FormatActionConfigurationInput input = FormatActionConfigurationInput.newBuilder().name(NAME)
                .requiresDomains(singletonList(DOMAIN))
                .requiresEnrichment(singletonList(ENRICHMENT)).build();

        DeltaFiConfigProjectionRoot projection = rootProject()
                .onFormatActionConfiguration()
                .requiresEnrichment()
                .requiresDomains()
                .parent();

        RegisterFormatActionGraphQLQuery register = RegisterFormatActionGraphQLQuery.newRequest().formatActionConfiguration(input).build();
        FormatActionConfiguration config = executeRequest(register, projection, FormatActionConfiguration.class);

        assertEquals(NAME, config.getName());
        assertNotNull(config.getCreated());
        assertNotNull(config.getModified());
        assertEquals(DOMAIN, config.getRequiresDomains().get(0));
        assertEquals(ENRICHMENT, config.getRequiresEnrichment().get(0));
    }

    @Test
    void addValidateActionConfigTest() {
        ValidateActionConfigurationInput input = ValidateActionConfigurationInput.newBuilder().name(NAME).build();

        RegisterValidateActionGraphQLQuery register = RegisterValidateActionGraphQLQuery.newRequest().validateActionConfiguration(input).build();
        ValidateActionConfiguration config = executeRequest(register, rootProject(), ValidateActionConfiguration.class);

        assertEquals(NAME, config.getName());
        assertNotNull(config.getCreated());
        assertNotNull(config.getModified());
    }

    @Test
    void addEgressActionConfigTest() {
        EgressActionConfigurationInput input = EgressActionConfigurationInput.newBuilder().name(NAME).build();

        RegisterEgressActionGraphQLQuery register = RegisterEgressActionGraphQLQuery.newRequest().egressActionConfiguration(input).build();
        EgressActionConfiguration config = executeRequest(register, rootProject(), EgressActionConfiguration.class);

        assertEquals(NAME, config.getName());
        assertNotNull(config.getCreated());
        assertNotNull(config.getModified());
    }

    @Test
    void addIngressFlowConfigTest() {
        IngressFlowConfigurationInput input = IngressFlowConfigurationInput.newBuilder()
                .name(NAME)
                .loadActions(singletonList("loader"))
                .transformActions(singletonList("transformer"))
                .type("test-type")
                .build();

        DeltaFiConfigProjectionRoot projection = rootProject()
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

        DeltaFiConfigProjectionRoot projection = rootProject()
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

        DeltaFiConfigProjectionRoot projection = rootProject()
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

        DeltaFiConfigProjectionRoot projection = rootProject()
                .onDomainEndpointConfiguration()
                .url()
                .parent();

        RegisterDomainEndpointGraphQLQuery register = RegisterDomainEndpointGraphQLQuery.newRequest().domainEndpointConfiguration(input).build();
        executeRequest(register, projection, DomainEndpointConfiguration.class);

        DeltaFiConfigGraphQLQuery findConfig = DeltaFiConfigGraphQLQuery.newRequest().configQuery(configQueryInput).build();

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

        DeltaFiConfigProjectionRoot projection = rootProject();

        RegisterDomainEndpointGraphQLQuery register = RegisterDomainEndpointGraphQLQuery.newRequest().domainEndpointConfiguration(input).build();
        executeRequest(register, projection, DomainEndpointConfiguration.class);

        RemoveConfigGraphQLQuery remove = RemoveConfigGraphQLQuery.newRequest().build();
        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(remove, null);
        Integer removed = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + remove.getOperationName(),
                Integer.class);
        assertEquals(1, removed.intValue());
    }

    DeltaFiConfigProjectionRoot rootProject() {
        return new DeltaFiConfigProjectionRoot()
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
