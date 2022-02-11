package org.deltafi.core.domain.datafetchers;

import com.jayway.jsonpath.TypeRef;
import com.netflix.graphql.dgs.DgsQueryExecutor;
import com.netflix.graphql.dgs.client.codegen.BaseProjectionNode;
import com.netflix.graphql.dgs.client.codegen.GraphQLQuery;
import com.netflix.graphql.dgs.client.codegen.GraphQLQueryRequest;
import org.deltafi.core.domain.api.types.ActionSchema;
import org.deltafi.core.domain.generated.client.*;
import org.deltafi.core.domain.generated.types.*;

import java.util.Collections;
import java.util.List;

public class ActionSchemaDatafetcherTestHelper {

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

    static public <C extends ActionSchema> C executeRequest(GraphQLQuery query, BaseProjectionNode projection, Class<C> clazz, DgsQueryExecutor dgsQueryExecutor) {
        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(query, projection);
        return dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + query.getOperationName(),
                clazz);
    }

    static public List<ActionSchema> getSchemas(DgsQueryExecutor dgsQueryExecutor) {
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

        return dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + actionSchemasQuery.getOperationName(),
                listOfActionSchemas);
    }

    static public DeleteActionSchema saveDelete(DgsQueryExecutor dgsQueryExecutor) {
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

        return executeRequest(registerQuery,
                projection, DeleteActionSchema.class, dgsQueryExecutor);
    }

    static public EgressActionSchema saveEgress(DgsQueryExecutor dgsQueryExecutor) {
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

        return executeRequest(registerQuery,
                projection, EgressActionSchema.class, dgsQueryExecutor);
    }

    static public EnrichActionSchema saveEnrich(DgsQueryExecutor dgsQueryExecutor) {
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

        return executeRequest(registerQuery,
                projection, EnrichActionSchema.class, dgsQueryExecutor);
    }

    static public FormatActionSchema saveFormat(DgsQueryExecutor dgsQueryExecutor) {
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

        return executeRequest(registerQuery,
                projection, FormatActionSchema.class, dgsQueryExecutor);
    }

    static public LoadActionSchema saveLoad(DgsQueryExecutor dgsQueryExecutor) {
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

        return executeRequest(registerQuery,
                projection, LoadActionSchema.class, dgsQueryExecutor);
    }

    static public TransformActionSchema saveTransform(DgsQueryExecutor dgsQueryExecutor) {
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

        return executeRequest(registerQuery,
                projection, TransformActionSchema.class, dgsQueryExecutor);
    }

    static public ValidateActionSchema saveValidate(DgsQueryExecutor dgsQueryExecutor) {
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

        return executeRequest(registerQuery,
                projection, ValidateActionSchema.class, dgsQueryExecutor);
    }
}
