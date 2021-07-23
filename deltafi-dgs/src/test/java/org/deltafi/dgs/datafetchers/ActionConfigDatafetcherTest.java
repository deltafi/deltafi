package org.deltafi.dgs.datafetchers;

import com.jayway.jsonpath.TypeRef;
import com.netflix.graphql.dgs.DgsQueryExecutor;
import com.netflix.graphql.dgs.client.codegen.BaseProjectionNode;
import com.netflix.graphql.dgs.client.codegen.GraphQLQuery;
import com.netflix.graphql.dgs.client.codegen.GraphQLQueryRequest;
import org.deltafi.dgs.generated.client.*;
import org.deltafi.dgs.generated.types.*;
import org.deltafi.dgs.repo.ActionConfigRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static graphql.Assert.*;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@TestPropertySource(properties = "enableScheduling=false")
class ActionConfigDatafetcherTest {

    public static final String NAME = "myAction";
    public static final String CONSUMES = "xml";
    public static final String PRODUCES = "json";
    public static final String DOMAIN = "domain";
    public static final String ENRICHMENT = "enrichment";
    public static final String CLASS_NAME = "className";

    @Autowired
    DgsQueryExecutor dgsQueryExecutor;

    @Autowired
    ActionConfigRepo actionConfigRepo;

    @BeforeEach
    public void setup() {
        actionConfigRepo.deleteAll();
    }

    @Test
    void addTransformConfigTest() {
        TransformActionConfigurationInput transformInput = TransformActionConfigurationInput.newBuilder()
                .name(NAME).consumes(CONSUMES).produces(PRODUCES).type(CLASS_NAME).build();

        ActionConfigsProjectionRoot projection = rootProject().onTransformActionConfiguration()
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
                .consumes(CONSUMES).requiresMetadataKeyValues(singletonList(keyValueInput)).type(CLASS_NAME).build();

        ActionConfigsProjectionRoot projection = rootProject().onLoadActionConfiguration()
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
    void addEnrichActionConfigTest() {
        EnrichActionConfigurationInput input = EnrichActionConfigurationInput.newBuilder().name(NAME)
                .requiresDomains(singletonList(DOMAIN))
                .requiresEnrichment(singletonList(ENRICHMENT))
                .type(CLASS_NAME).build();

        ActionConfigsProjectionRoot projection = rootProject()
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
                .requiresEnrichment(singletonList(ENRICHMENT))
                .type(CLASS_NAME).build();

        ActionConfigsProjectionRoot projection = rootProject()
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
        ValidateActionConfigurationInput input = ValidateActionConfigurationInput.newBuilder().name(NAME).type(CLASS_NAME).build();

        RegisterValidateActionGraphQLQuery register = RegisterValidateActionGraphQLQuery.newRequest().validateActionConfiguration(input).build();
        ValidateActionConfiguration config = executeRequest(register, rootProject(), ValidateActionConfiguration.class);

        assertEquals(NAME, config.getName());
        assertNotNull(config.getCreated());
        assertNotNull(config.getModified());
    }

    @Test
    void addEgressActionConfigTest() {
        EgressActionConfigurationInput input = EgressActionConfigurationInput.newBuilder().name(NAME).type(CLASS_NAME).build();

        RegisterEgressActionGraphQLQuery register = RegisterEgressActionGraphQLQuery.newRequest().egressActionConfiguration(input).build();
        EgressActionConfiguration config = executeRequest(register, rootProject(), EgressActionConfiguration.class);

        assertEquals(NAME, config.getName());
        assertNotNull(config.getCreated());
        assertNotNull(config.getModified());
    }

    @Test
    void findConfigsTest() {
        ActionQueryInput actionQueryInput = ActionQueryInput.newBuilder().actionType(ActionType.TRANSFORM_ACTION).name(NAME).build();

        TransformActionConfigurationInput transformInput = TransformActionConfigurationInput.newBuilder()
                .name(NAME).consumes(CONSUMES).produces(PRODUCES).type(CLASS_NAME).build();

        ActionConfigsProjectionRoot projection = rootProject().onTransformActionConfiguration()
                .consumes()
                .parent();

        RegisterTransformActionGraphQLQuery transformRequest = RegisterTransformActionGraphQLQuery.newRequest().transformActionConfiguration(transformInput).build();
        executeRequest(transformRequest, projection, TransformActionConfiguration.class);

        ActionConfigsGraphQLQuery findConfig = ActionConfigsGraphQLQuery.newRequest().actionQuery(actionQueryInput).build();

        TypeRef<List<ActionConfiguration>> listOfConfigs = new TypeRef<>() {};
        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(findConfig, projection);
        List<ActionConfiguration> configs = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + findConfig.getOperationName(),
                listOfConfigs);

        assertTrue(configs.get(0) instanceof TransformActionConfiguration);

        TransformActionConfiguration transformConfig = (TransformActionConfiguration) configs.get(0);
        assertEquals(NAME, transformConfig.getName());
        assertNotNull(transformConfig.getCreated());
        assertNotNull(transformConfig.getModified());
        assertEquals(CONSUMES, transformConfig.getConsumes());
        assertNull(transformConfig.getProduces()); // not in the projection should be null
    }

    @Test
    void deleteConfigsTest() {
        TransformActionConfigurationInput transformInput = TransformActionConfigurationInput.newBuilder()
                .name(NAME).consumes(CONSUMES).produces(PRODUCES).type(CLASS_NAME).build();

        ActionConfigsProjectionRoot projection = rootProject().onTransformActionConfiguration()
                .consumes()
                .parent();

        RegisterTransformActionGraphQLQuery transformRequest = RegisterTransformActionGraphQLQuery.newRequest().transformActionConfiguration(transformInput).build();
        executeRequest(transformRequest, projection, TransformActionConfiguration.class);

        RemoveActionConfigsGraphQLQuery remove = RemoveActionConfigsGraphQLQuery.newRequest().build();
        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(remove, null);
        Integer removed = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + remove.getOperationName(),
                Integer.class);
        assertEquals(1, removed.intValue());
    }

    ActionConfigsProjectionRoot rootProject() {
        return new ActionConfigsProjectionRoot()
                .name()
                .created()
                .modified()
                .apiVersion();
    }

    <C extends ActionConfiguration> C executeRequest(GraphQLQuery query, BaseProjectionNode projection, Class<C> clazz) {
        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(query, projection);
        return dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + query.getOperationName(),
                clazz);
    }
}
