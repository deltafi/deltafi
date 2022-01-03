package org.deltafi.core.domain.datafetchers;

import com.jayway.jsonpath.TypeRef;
import com.netflix.graphql.dgs.DgsQueryExecutor;
import com.netflix.graphql.dgs.client.codegen.BaseProjectionNode;
import com.netflix.graphql.dgs.client.codegen.GraphQLQuery;
import com.netflix.graphql.dgs.client.codegen.GraphQLQueryRequest;
import org.deltafi.core.domain.api.types.ActionSchema;
import org.deltafi.core.domain.generated.client.*;
import org.deltafi.core.domain.generated.types.*;
import org.deltafi.core.domain.repo.ActionSchemaRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.Collections;
import java.util.List;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@TestPropertySource(properties = "enableScheduling=false")
class ActionSchemaDatafetcherTest {

    public static final String DELETE_ACTION = "org.deltafi.DeleteAction";
    public static final String EGRESS_ACTION = "org.deltafi.EgressAction";
    public static final String ENRICH_ACTION = "org.deltafi.EnrichAction";
    public static final String FORMAT_ACTION = "org.deltafi.FormatAction";
    public static final String LOAD_ACTION = "org.deltafi.LoadAction";
    public static final String TRANSFORM_ACTION = "org.deltafi.TransformAction";
    public static final String VALIDATE_ACTION = "org.deltafi.ValidatreAction";
    public static final String CONSUMES = "consumes";
    public static final String PRODUCES = "produces";
    public static final String PARAM_CLASS = "paramClass";
    public static final String DOMAIN = "domain";
    public static final String VERSION = "1.2.3";

    @Autowired
    DgsQueryExecutor dgsQueryExecutor;

    @Autowired
    ActionSchemaRepo actionSchemaRepo;

    @BeforeEach
    public void setup() {
        actionSchemaRepo.deleteAll();
    }

    @Test
    void testRegisterDelete() {
        DeleteActionSchema schema = saveDelete();
        assertEquals(DELETE_ACTION, schema.getId());
        assertEquals(PARAM_CLASS, schema.getParamClass());
        assertNotNull(schema.getLastHeard());
    }

    @Test
    void testRegisterEgress() {
        EgressActionSchema schema = saveEgress();
        assertEquals(EGRESS_ACTION, schema.getId());
        assertEquals(PARAM_CLASS, schema.getParamClass());
        assertNotNull(schema.getLastHeard());
    }

    @Test
    void testRegisterEnrich() {
        EnrichActionSchema schema = saveEnrich();
        assertEquals(ENRICH_ACTION, schema.getId());
        assertEquals(PARAM_CLASS, schema.getParamClass());
        assertNotNull(schema.getLastHeard());
        assertEquals(DOMAIN, schema.getRequiresDomains().get(0));
    }

    @Test
    void testRegisterFormat() {
        FormatActionSchema schema = saveFormat();
        assertEquals(FORMAT_ACTION, schema.getId());
        assertEquals(PARAM_CLASS, schema.getParamClass());
        assertNotNull(schema.getLastHeard());
        assertEquals(DOMAIN, schema.getRequiresDomains().get(0));
    }

    @Test
    void testRegisterLoad() {
        LoadActionSchema schema = saveLoad();
        assertEquals(LOAD_ACTION, schema.getId());
        assertEquals(PARAM_CLASS, schema.getParamClass());
        assertNotNull(schema.getLastHeard());
        assertEquals(CONSUMES, schema.getConsumes());
    }

    @Test
    void testRegisterTransform() {
        TransformActionSchema schema = saveTransform();
        assertEquals(TRANSFORM_ACTION, schema.getId());
        assertEquals(PARAM_CLASS, schema.getParamClass());
        assertNotNull(schema.getLastHeard());
        assertEquals(CONSUMES, schema.getConsumes());
        assertEquals(PRODUCES, schema.getProduces());
    }

    @Test
    void testRegisterValidate() {
        ValidateActionSchema schema = saveValidate();
        assertEquals(VALIDATE_ACTION, schema.getId());
        assertEquals(PARAM_CLASS, schema.getParamClass());
        assertNotNull(schema.getLastHeard());
    }

    @Test
    void testGetAll() {
        saveEgress();
        saveFormat();
        saveLoad();
        assertEquals(3, actionSchemaRepo.count());

        List<ActionSchema> schemas = getSchemas();
        assertEquals(3, schemas.size());

        boolean foundEgress = false;
        boolean foundFormat = false;
        boolean foundLoad = false;

        for (ActionSchema schema : schemas) {
            if (schema instanceof EgressActionSchema) {
                foundEgress = true;
                EgressActionSchema e = (EgressActionSchema) schema;
                assertEquals(EGRESS_ACTION, e.getId());
            } else if (schema instanceof FormatActionSchema) {
                foundFormat = true;
                FormatActionSchema f = (FormatActionSchema) schema;
                assertEquals(FORMAT_ACTION, f.getId());
                assertEquals(DOMAIN, f.getRequiresDomains().get(0));
            } else if (schema instanceof LoadActionSchema) {
                foundLoad = true;
                LoadActionSchema l = (LoadActionSchema) schema;
                assertEquals(LOAD_ACTION, l.getId());
                assertEquals(CONSUMES, l.getConsumes());
            }
        }
        assertTrue(foundEgress);
        assertTrue(foundFormat);
        assertTrue(foundLoad);
    }

    <C extends ActionSchema> C executeRequest(GraphQLQuery query, BaseProjectionNode projection, Class<C> clazz) {
        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(query, projection);
        return dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + query.getOperationName(),
                clazz);
    }

    List<ActionSchema> getSchemas() {
        ActionSchemasProjectionRoot projection = new ActionSchemasProjectionRoot()
                .onEgressActionSchema()
                .id()
                .lastHeard()
                .paramClass()
                .parent()

                .onFormatActionSchema()
                .id()
                .lastHeard()
                .paramClass()
                .requiresDomains()
                .parent()

                .onLoadActionSchema()
                .id()
                .lastHeard()
                .paramClass()
                .consumes()
                .parent();

        ActionSchemasGraphQLQuery actionSchemasQuery = ActionSchemasGraphQLQuery.newRequest().build();

        GraphQLQueryRequest graphQLQueryRequest =
                new GraphQLQueryRequest(actionSchemasQuery, projection);

        TypeRef<List<ActionSchema>> listOfActionSchemas = new TypeRef<>() {
        };
        List<ActionSchema> actionSchemas = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + actionSchemasQuery.getOperationName(),
                listOfActionSchemas);

        return actionSchemas;
    }

    DeleteActionSchema saveDelete() {
        DeleteActionSchemaInput input = DeleteActionSchemaInput.newBuilder()
                .id(DELETE_ACTION)
                .paramClass(PARAM_CLASS)
                .actionKitVersion(VERSION)
                .build();

        RegisterDeleteSchemaProjectionRoot projection = new RegisterDeleteSchemaProjectionRoot()
                .id()
                .lastHeard()
                .onDeleteActionSchema()
                .paramClass()
                .parent();

        RegisterDeleteSchemaGraphQLQuery registerQuery = RegisterDeleteSchemaGraphQLQuery
                .newRequest().actionSchema(input).build();

        DeleteActionSchema schema = executeRequest(registerQuery,
                projection, DeleteActionSchema.class);

        return schema;
    }

    EgressActionSchema saveEgress() {
        EgressActionSchemaInput input = EgressActionSchemaInput.newBuilder()
                .id(EGRESS_ACTION)
                .paramClass(PARAM_CLASS)
                .actionKitVersion(VERSION)
                .build();

        RegisterEgressSchemaProjectionRoot projection = new RegisterEgressSchemaProjectionRoot()
                .id()
                .lastHeard()
                .onEgressActionSchema()
                .paramClass()
                .parent();

        RegisterEgressSchemaGraphQLQuery registerQuery = RegisterEgressSchemaGraphQLQuery
                .newRequest().actionSchema(input).build();

        EgressActionSchema schema = executeRequest(registerQuery,
                projection, EgressActionSchema.class);

        return schema;
    }

    EnrichActionSchema saveEnrich() {
        EnrichActionSchemaInput input = EnrichActionSchemaInput.newBuilder()
                .id(ENRICH_ACTION)
                .paramClass(PARAM_CLASS)
                .actionKitVersion(VERSION)
                .requiresDomains(Collections.singletonList(DOMAIN))
                .build();

        RegisterEnrichSchemaProjectionRoot projection = new RegisterEnrichSchemaProjectionRoot()
                .id()
                .lastHeard()
                .onEnrichActionSchema()
                .paramClass()
                .requiresDomains()
                .parent();

        RegisterEnrichSchemaGraphQLQuery registerQuery = RegisterEnrichSchemaGraphQLQuery
                .newRequest().actionSchema(input).build();

        EnrichActionSchema schema = executeRequest(registerQuery,
                projection, EnrichActionSchema.class);

        return schema;
    }

    FormatActionSchema saveFormat() {
        FormatActionSchemaInput input = FormatActionSchemaInput.newBuilder()
                .id(FORMAT_ACTION)
                .paramClass(PARAM_CLASS)
                .actionKitVersion(VERSION)
                .requiresDomains(Collections.singletonList(DOMAIN))
                .build();

        RegisterFormatSchemaProjectionRoot projection = new RegisterFormatSchemaProjectionRoot()
                .id()
                .lastHeard()
                .onFormatActionSchema()
                .paramClass()
                .requiresDomains()
                .parent();

        RegisterFormatSchemaGraphQLQuery registerQuery = RegisterFormatSchemaGraphQLQuery
                .newRequest().actionSchema(input).build();

        FormatActionSchema schema = executeRequest(registerQuery,
                projection, FormatActionSchema.class);

        return schema;
    }

    LoadActionSchema saveLoad() {
        LoadActionSchemaInput input = LoadActionSchemaInput.newBuilder()
                .id(LOAD_ACTION)
                .paramClass(PARAM_CLASS)
                .actionKitVersion(VERSION)
                .consumes(CONSUMES)
                .build();

        RegisterLoadSchemaProjectionRoot projection = new RegisterLoadSchemaProjectionRoot()
                .id()
                .lastHeard()
                .onLoadActionSchema()
                .paramClass()
                .consumes()
                .parent();

        RegisterLoadSchemaGraphQLQuery registerQuery = RegisterLoadSchemaGraphQLQuery
                .newRequest().actionSchema(input).build();

        LoadActionSchema schema = executeRequest(registerQuery,
                projection, LoadActionSchema.class);

        return schema;
    }

    TransformActionSchema saveTransform() {
        TransformActionSchemaInput input = TransformActionSchemaInput.newBuilder()
                .id(TRANSFORM_ACTION)
                .paramClass(PARAM_CLASS)
                .actionKitVersion(VERSION)
                .consumes(CONSUMES)
                .produces(PRODUCES)
                .build();

        RegisterTransformSchemaProjectionRoot projection = new RegisterTransformSchemaProjectionRoot()
                .id()
                .lastHeard()
                .onTransformActionSchema()
                .paramClass()
                .consumes()
                .produces()
                .parent();

        RegisterTransformSchemaGraphQLQuery registerQuery = RegisterTransformSchemaGraphQLQuery
                .newRequest().actionSchema(input).build();

        TransformActionSchema schema = executeRequest(registerQuery,
                projection, TransformActionSchema.class);

        return schema;
    }

    ValidateActionSchema saveValidate() {
        ValidateActionSchemaInput input = ValidateActionSchemaInput.newBuilder()
                .id(VALIDATE_ACTION)
                .paramClass(PARAM_CLASS)
                .actionKitVersion(VERSION)
                .build();

        RegisterValidateSchemaProjectionRoot projection = new RegisterValidateSchemaProjectionRoot()
                .id()
                .lastHeard()
                .onValidateActionSchema()
                .paramClass()
                .parent();

        RegisterValidateSchemaGraphQLQuery registerQuery = RegisterValidateSchemaGraphQLQuery
                .newRequest().actionSchema(input).build();

        ValidateActionSchema schema = executeRequest(registerQuery,
                projection, ValidateActionSchema.class);

        return schema;
    }
}
