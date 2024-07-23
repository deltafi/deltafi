/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.integration;

import com.jayway.jsonpath.TypeRef;
import com.netflix.graphql.dgs.DgsQueryExecutor;
import com.netflix.graphql.dgs.client.codegen.GraphQLQueryRequest;
import org.deltafi.core.types.TestResult;
import org.deltafi.core.generated.client.*;

import java.util.List;

public class IntegrationDataFetcherTestHelper {

    private static final GetAllIntegrationTestsProjectionRoot GET_ALL_PROJECTION = new GetAllIntegrationTestsProjectionRoot()
            .id()
            .description()
            .start()
            .stop()
            .errors()
            .status()
            .parent();

    private static final GetIntegrationTestProjectionRoot GET_UNT_TEST_PROJECTION = new GetIntegrationTestProjectionRoot()
            .id()
            .description()
            .start()
            .stop()
            .errors()
            .status()
            .parent();

    private static final LaunchIntegrationTestProjectionRoot LAUNCH_INT_TEST_PROJECTION = new LaunchIntegrationTestProjectionRoot()
            .id()
            .description()
            .start()
            .stop()
            .errors()
            .status()
            .parent();

    static public TestResult launchIntegrationTest(DgsQueryExecutor dgsQueryExecutor, String config) {
        LaunchIntegrationTestGraphQLQuery query = LaunchIntegrationTestGraphQLQuery.newRequest()
                .configYaml(config)
                .build();

        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(query, LAUNCH_INT_TEST_PROJECTION);

        return dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + query.getOperationName(),
                TestResult.class);
    }

    static public List<TestResult> getAllIntegrationTests(DgsQueryExecutor dgsQueryExecutor) {
        GetAllIntegrationTestsGraphQLQuery query = GetAllIntegrationTestsGraphQLQuery.newRequest().build();
        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(query, GET_ALL_PROJECTION);

        TypeRef<List<TestResult>> resultsListType = new TypeRef<>() {
        };

        return dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + query.getOperationName(),
                resultsListType);
    }

    static public TestResult getIntegrationTest(DgsQueryExecutor dgsQueryExecutor, String id) {
        GetIntegrationTestGraphQLQuery query = GetIntegrationTestGraphQLQuery.newRequest().id(id).build();
        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(query, GET_UNT_TEST_PROJECTION);

        return dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + query.getOperationName(),
                TestResult.class);
    }

    static public boolean removeIntegrationTest(DgsQueryExecutor dgsQueryExecutor, String id) {
        RemoveIntegrationTestGraphQLQuery query = RemoveIntegrationTestGraphQLQuery.newRequest().id(id).build();
        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(query, null);

        return dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + query.getOperationName(), Boolean.class);
    }
}
