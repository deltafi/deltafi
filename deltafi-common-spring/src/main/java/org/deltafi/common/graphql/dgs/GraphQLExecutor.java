/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.common.graphql.dgs;

import com.netflix.graphql.dgs.client.DgsGraphQLClient;
import com.netflix.graphql.dgs.client.DgsGraphQLResponse;
import com.netflix.graphql.dgs.client.GraphQLError;
import com.netflix.graphql.dgs.client.codegen.GraphQLQueryRequest;
import org.intellij.lang.annotations.Language;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

/** Executes GraphQL queries and extracts typed responses. */
@SuppressWarnings("unused")
public class GraphQLExecutor {
    /**
     * Execute a GraphQL query and extract a typed object from the response.
     * @param graphQLClient the client to execute the query with
     * @param queryRequest the query request to execute
     * @param responseObjectPath the JSON path to extract the result from
     * @param responseObjectClass the class to deserialize the result into
     * @param <T> the response object type
     * @return the extracted and deserialized response object
     * @throws Exception if the query fails or contains errors
     */
    public static <T> T executeQuery(DgsGraphQLClient graphQLClient, GraphQLQueryRequest queryRequest,
            String responseObjectPath, Class<T> responseObjectClass) throws Exception {
        DgsGraphQLResponse response = executeQuery(graphQLClient, queryRequest);

        return response.extractValueAsObject(responseObjectPath, responseObjectClass);
    }

    /**
     * Execute a GraphQL query request and return the raw response.
     * @param graphQLClient the client to execute the query with
     * @param queryRequest the query request to execute
     * @return the GraphQL response
     * @throws Exception if the query fails or contains errors
     */
    public static DgsGraphQLResponse executeQuery(DgsGraphQLClient graphQLClient, GraphQLQueryRequest queryRequest)
            throws Exception {
        return executeQuery(graphQLClient, queryRequest.serialize(), Collections.emptyMap());
    }

    /**
     * Execute a GraphQL query string with variables and return the raw response.
     * @param graphQLClient the client to execute the query with
     * @param query the GraphQL query string
     * @param variables the query variables
     * @return the GraphQL response
     * @throws Exception if the query fails or contains errors
     */
    public static DgsGraphQLResponse executeQuery(DgsGraphQLClient graphQLClient, @Language("graphql") String query,
            Map<String, Object> variables) throws Exception {
        DgsGraphQLResponse response = graphQLClient.executeQuery(query, variables);

        if (response.hasErrors()) {
            String errorMessage = "Error executing GraphQL query:\n\nOriginal query:\n" + query + "\n\n" +
                    response.getErrors().stream().map(GraphQLError::getMessage).collect(Collectors.joining("\n"));
            throw new Exception(errorMessage);
        }

        return response;
    }
}
