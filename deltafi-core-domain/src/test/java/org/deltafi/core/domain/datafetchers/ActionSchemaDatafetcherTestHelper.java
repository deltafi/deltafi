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
import com.netflix.graphql.dgs.client.codegen.GraphQLQueryRequest;
import org.deltafi.core.domain.api.types.ActionSchema;
import org.deltafi.core.domain.generated.client.ActionSchemasGraphQLQuery;
import org.deltafi.core.domain.generated.client.ActionSchemasProjectionRoot;
import org.deltafi.core.domain.generated.client.RegisterActionsGraphQLQuery;
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
    public static final String PARAM_CLASS = "paramClass";
    public static final String DOMAIN = "domain";

    static public List<ActionSchema> getSchemas(DgsQueryExecutor dgsQueryExecutor) {
        ActionSchemasProjectionRoot projection = new ActionSchemasProjectionRoot()
                .id()
                .lastHeard()
                .paramClass()
                .schema()

                .onDeleteActionSchema()
                .parent()

                .onEgressActionSchema()
                .parent()

                .onEnrichActionSchema()
                .requiresDomains()
                .requiresEnrichment()
                .parent()

                .onFormatActionSchema()
                .requiresDomains()
                .requiresEnrichment()
                .parent()

                .onLoadActionSchema()
                .parent()

                .onTransformActionSchema()
                .parent()

                .onValidateActionSchema()
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

    static private int executeRegister(DgsQueryExecutor dgsQueryExecutor, ActionRegistrationInput input) {
        RegisterActionsGraphQLQuery query = RegisterActionsGraphQLQuery.newRequest().actionRegistration(input).build();

        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(query, null);
        return dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + query.getOperationName(),
                Integer.class);
    }

    static public int saveAll(DgsQueryExecutor dgsQueryExecutor) {
        ActionRegistrationInput input = ActionRegistrationInput.newBuilder()
                .transformActions(Collections.singletonList(getTransformInput()))
                .loadActions(Collections.singletonList(getLoadInput()))
                .enrichActions(Collections.singletonList(getEnrichInput()))
                .formatActions(Collections.singletonList(getFormatInput()))
                .validateActions(Collections.singletonList(getValidateInput()))
                .egressActions(Collections.singletonList(getEgressInput()))
                .deleteActions(Collections.singletonList(getDeleteInput()))
                .build();

        return executeRegister(dgsQueryExecutor, input);
    }

    static private DeleteActionSchemaInput getDeleteInput() {
        return DeleteActionSchemaInput.newBuilder()
                .id(DELETE_ACTION)
                .paramClass(PARAM_CLASS)
                .build();
    }

    static public int saveDelete(DgsQueryExecutor dgsQueryExecutor) {
        ActionRegistrationInput input = ActionRegistrationInput.newBuilder()
                .transformActions(Collections.emptyList())
                .loadActions(Collections.emptyList())
                .enrichActions(Collections.emptyList())
                .formatActions(Collections.emptyList())
                .validateActions(Collections.emptyList())
                .egressActions(Collections.emptyList())
                .deleteActions(Collections.singletonList(getDeleteInput()))
                .build();

        return executeRegister(dgsQueryExecutor, input);
    }

    static private EgressActionSchemaInput getEgressInput() {
        return EgressActionSchemaInput.newBuilder()
                .id(EGRESS_ACTION)
                .paramClass(PARAM_CLASS)
                .build();
    }

    static public int saveEgress(DgsQueryExecutor dgsQueryExecutor) {
        ActionRegistrationInput input = ActionRegistrationInput.newBuilder()
                .transformActions(Collections.emptyList())
                .loadActions(Collections.emptyList())
                .enrichActions(Collections.emptyList())
                .formatActions(Collections.emptyList())
                .validateActions(Collections.emptyList())
                .egressActions(Collections.singletonList(getEgressInput()))
                .deleteActions(Collections.emptyList())
                .build();

        return executeRegister(dgsQueryExecutor, input);
    }

    static private EnrichActionSchemaInput getEnrichInput() {
        return EnrichActionSchemaInput.newBuilder()
                .id(ENRICH_ACTION)
                .paramClass(PARAM_CLASS)
                .requiresDomains(Collections.singletonList(DOMAIN))
                .build();
    }

    static public int saveEnrich(DgsQueryExecutor dgsQueryExecutor) {
        ActionRegistrationInput input = ActionRegistrationInput.newBuilder()
                .transformActions(Collections.emptyList())
                .loadActions(Collections.emptyList())
                .enrichActions(Collections.singletonList(getEnrichInput()))
                .formatActions(Collections.emptyList())
                .validateActions(Collections.emptyList())
                .egressActions(Collections.emptyList())
                .deleteActions(Collections.emptyList())
                .build();

        return executeRegister(dgsQueryExecutor, input);
    }

    static private FormatActionSchemaInput getFormatInput() {
        return FormatActionSchemaInput.newBuilder()
                .id(FORMAT_ACTION)
                .paramClass(PARAM_CLASS)
                .requiresDomains(Collections.singletonList(DOMAIN))
                .build();
    }

    static public int saveFormat(DgsQueryExecutor dgsQueryExecutor) {
        ActionRegistrationInput input = ActionRegistrationInput.newBuilder()
                .transformActions(Collections.emptyList())
                .loadActions(Collections.emptyList())
                .enrichActions(Collections.emptyList())
                .formatActions(Collections.singletonList(getFormatInput()))
                .validateActions(Collections.emptyList())
                .egressActions(Collections.emptyList())
                .deleteActions(Collections.emptyList())
                .build();

        return executeRegister(dgsQueryExecutor, input);
    }

    static private LoadActionSchemaInput getLoadInput() {
        return LoadActionSchemaInput.newBuilder()
                .id(LOAD_ACTION)
                .paramClass(PARAM_CLASS)
                .build();
    }

    static public int saveLoad(DgsQueryExecutor dgsQueryExecutor) {
        ActionRegistrationInput input = ActionRegistrationInput.newBuilder()
                .transformActions(Collections.emptyList())
                .loadActions(Collections.singletonList(getLoadInput()))
                .enrichActions(Collections.emptyList())
                .formatActions(Collections.emptyList())
                .validateActions(Collections.emptyList())
                .egressActions(Collections.emptyList())
                .deleteActions(Collections.emptyList())
                .build();

        return executeRegister(dgsQueryExecutor, input);
    }

    static private TransformActionSchemaInput getTransformInput() {
        return TransformActionSchemaInput.newBuilder()
                .id(TRANSFORM_ACTION)
                .paramClass(PARAM_CLASS)
                .build();
    }

    static public int saveTransform(DgsQueryExecutor dgsQueryExecutor) {
        ActionRegistrationInput input = ActionRegistrationInput.newBuilder()
                .transformActions(Collections.singletonList(getTransformInput()))
                .loadActions(Collections.emptyList())
                .enrichActions(Collections.emptyList())
                .formatActions(Collections.emptyList())
                .validateActions(Collections.emptyList())
                .egressActions(Collections.emptyList())
                .deleteActions(Collections.emptyList())
                .build();

        return executeRegister(dgsQueryExecutor, input);
    }

    static private ValidateActionSchemaInput getValidateInput() {
        return ValidateActionSchemaInput.newBuilder()
                .id(VALIDATE_ACTION)
                .paramClass(PARAM_CLASS)
                .build();
    }

    static public int saveValidate(DgsQueryExecutor dgsQueryExecutor) {
        ActionRegistrationInput input = ActionRegistrationInput.newBuilder()
                .transformActions(Collections.emptyList())
                .loadActions(Collections.emptyList())
                .enrichActions(Collections.emptyList())
                .formatActions(Collections.emptyList())
                .validateActions(Collections.singletonList(getValidateInput()))
                .egressActions(Collections.emptyList())
                .deleteActions(Collections.emptyList())
                .build();

        return executeRegister(dgsQueryExecutor, input);
    }

}
