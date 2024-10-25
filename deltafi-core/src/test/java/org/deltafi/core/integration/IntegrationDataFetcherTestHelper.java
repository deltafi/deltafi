/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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
import org.deltafi.core.generated.client.*;
import org.deltafi.core.types.Result;
import org.deltafi.core.types.integration.IntegrationTest;
import org.deltafi.core.types.integration.TestResult;

import java.util.List;

public class IntegrationDataFetcherTestHelper {

    private static final GetIntegrationTestsProjectionRoot GET_ALL_INT_TESTS = new GetIntegrationTestsProjectionRoot()
            .name()
            .description()
            .plugins()
            .groupId()
            .artifactId()
            .version()
            .parent()
            .dataSources()
            .transformationFlows()
            .dataSinks()
            .inputs()
            .flow()
            .contentType()
            .ingressFileName()
            .base64Encoded()
            .data()
            .metadata()
            .key()
            .value()
            .parent()
            .parent()
            .timeout()
            .expectedDeltaFiles()
            // ExpectedDeltaFile (0)
            .stage()
            .parent()
            .childCount()
            .parentCount()

            .expectedFlows()
            // ExpectedFlow (0)
            .flow()
            .type()
            .parent()
            .state()
            .parent()
            .actions()
            .parent()

            .expectedContent()
            // ExpectedContentList (0)
            .flow()
            .type()
            .parent()
            .action()
            .data()
            .name()
            .mediaType()
            .value()
            .contains()
            .parent()
            .parent()

            .children()
            // ExpectedDeltaFile (1)

            .stage()
            .parent()
            .childCount()
            .parentCount()

            .expectedFlows()
            // ExpectedFlow (1)
            .flow()
            .type()
            .parent()
            .state()
            .parent()
            .actions()
            .parent()

            .expectedContent()
            // ExpectedContentList (1)
            .flow()
            .type()
            .parent()
            .action()
            .data()
            .name()
            .mediaType()
            .value()
            .contains()
            .parent()

            .parent()
            .children()
            // ExpectedDeltaFile (2)

            .stage()
            .parent()
            .childCount()
            .parentCount()

            .expectedFlows()
            // ExpectedFlow (2)
            .flow()
            .type()
            .parent()
            .state()
            .parent()
            .actions()
            .parent()

            .expectedContent()
            // ExpectedContentList (2)
            .flow()
            .type()
            .parent()
            .action()
            .data()
            .name()
            .mediaType()
            .value()
            .contains()
            .parent()

            .parent()

            .parent()

            .parent()
            .parent();

    private static final GetIntegrationTestProjectionRoot GET_INT_TEST = new GetIntegrationTestProjectionRoot()
            .name()
            .description()
            .plugins()
            .groupId()
            .artifactId()
            .version()
            .parent()
            .dataSources()
            .transformationFlows()
            .dataSinks()
            .inputs()
            .flow()
            .contentType()
            .ingressFileName()
            .base64Encoded()
            .data()
            .metadata()
            .key()
            .value()
            .parent()
            .parent()
            .timeout()
            .expectedDeltaFiles()
            // ExpectedDeltaFile (0)
            .stage()
            .parent()
            .childCount()
            .parentCount()

            .expectedFlows()
            // ExpectedFlow (0)
            .flow()
            .type()
            .parent()
            .state()
            .parent()
            .actions()
            .parent()

            .expectedContent()
            // ExpectedContentList (0)
            .flow()
            .type()
            .parent()
            .action()
            .data()
            .name()
            .mediaType()
            .value()
            .contains()
            .parent()
            .parent()

            .children()
            // ExpectedDeltaFile (1)

            .stage()
            .parent()
            .childCount()
            .parentCount()

            .expectedFlows()
            // ExpectedFlow (1)
            .flow()
            .type()
            .parent()
            .state()
            .parent()
            .actions()
            .parent()

            .expectedContent()
            // ExpectedContentList (1)
            .flow()
            .type()
            .parent()
            .action()
            .data()
            .name()
            .mediaType()
            .value()
            .contains()
            .parent()

            .parent()
            .children()
            // ExpectedDeltaFile (2)

            .stage()
            .parent()
            .childCount()
            .parentCount()

            .expectedFlows()
            // ExpectedFlow (2)
            .flow()
            .type()
            .parent()
            .state()
            .parent()
            .actions()
            .parent()

            .expectedContent()
            // ExpectedContentList (2)
            .flow()
            .type()
            .parent()
            .action()
            .data()
            .name()
            .mediaType()
            .value()
            .contains()
            .parent()

            .parent()

            .parent()

            .parent()
            .parent();


    private static final GetTestResultProjectionRoot GET_TEST_RESULT = new GetTestResultProjectionRoot()
            .id()
            .testName()
            .start()
            .stop()
            .errors()
            .status()
            .parent();

    private static final GetTestResultsProjectionRoot GET_ALL_TEST_RESULTS = new GetTestResultsProjectionRoot()
            .id()
            .testName()
            .start()
            .stop()
            .errors()
            .status()
            .parent();

    private static final LoadIntegrationTestProjectionRoot LOAD_INT_TEST_PROJECTION = new LoadIntegrationTestProjectionRoot()
            .info()
            .errors()
            .success();

    private static final SaveIntegrationTestProjectionRoot SAVE_INT_TEST_PROJECTION = new SaveIntegrationTestProjectionRoot()
            .info()
            .errors()
            .success();

    private static final StartIntegrationTestProjectionRoot START_INT_TEST_PROJECTION = new StartIntegrationTestProjectionRoot()
            .id()
            .testName()
            .start()
            .stop()
            .errors()
            .status()
            .parent();

    static public Result loadIntegrationTest(DgsQueryExecutor dgsQueryExecutor, String configYaml) {
        LoadIntegrationTestGraphQLQuery query = LoadIntegrationTestGraphQLQuery.newRequest()
                .configYaml(configYaml)
                .build();

        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(query, LOAD_INT_TEST_PROJECTION);

        return dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + query.getOperationName(),
                Result.class);
    }

    static public Result saveIntegrationTest(DgsQueryExecutor dgsQueryExecutor, IntegrationTest testCase) {
        SaveIntegrationTestGraphQLQuery query = SaveIntegrationTestGraphQLQuery.newRequest()
                .testCase(testCase)
                .build();

        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(query, SAVE_INT_TEST_PROJECTION);

        return dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + query.getOperationName(),
                Result.class);
    }

    static public TestResult startIntegrationTest(DgsQueryExecutor dgsQueryExecutor, String name) {
        StartIntegrationTestGraphQLQuery query = StartIntegrationTestGraphQLQuery.newRequest()
                .name(name)
                .build();

        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(query, START_INT_TEST_PROJECTION);

        return dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + query.getOperationName(),
                TestResult.class);
    }

    static public List<TestResult> getAllTestResults(DgsQueryExecutor dgsQueryExecutor) {
        GetTestResultsGraphQLQuery query = GetTestResultsGraphQLQuery.newRequest().build();
        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(query, GET_ALL_TEST_RESULTS);

        TypeRef<List<TestResult>> resultsListType = new TypeRef<>() {
        };

        return dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + query.getOperationName(),
                resultsListType);
    }

    static public List<IntegrationTest> getAllIntegrationTests(DgsQueryExecutor dgsQueryExecutor) {
        GetIntegrationTestsGraphQLQuery query = GetIntegrationTestsGraphQLQuery.newRequest().build();
        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(query, GET_ALL_INT_TESTS);

        TypeRef<List<IntegrationTest>> resultsListType = new TypeRef<>() {
        };

        return dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + query.getOperationName(),
                resultsListType);
    }

    static public TestResult getTestResult(DgsQueryExecutor dgsQueryExecutor, String id) {
        GetTestResultGraphQLQuery query = GetTestResultGraphQLQuery.newRequest().id(id).build();
        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(query, GET_TEST_RESULT);

        return dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + query.getOperationName(),
                TestResult.class);
    }

    static public IntegrationTest getIntegrationTest(DgsQueryExecutor dgsQueryExecutor, String name) {
        GetIntegrationTestGraphQLQuery query = GetIntegrationTestGraphQLQuery.newRequest().name(name).build();
        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(query, GET_INT_TEST);

        return dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + query.getOperationName(),
                IntegrationTest.class);
    }

    static public boolean removeTestResult(DgsQueryExecutor dgsQueryExecutor, String id) {
        RemoveTestResultGraphQLQuery query = RemoveTestResultGraphQLQuery.newRequest().id(id).build();
        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(query, null);

        return dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + query.getOperationName(), Boolean.class);
    }

    static public boolean removeIntegrationTest(DgsQueryExecutor dgsQueryExecutor, String name) {
        RemoveIntegrationTestGraphQLQuery query = RemoveIntegrationTestGraphQLQuery.newRequest().name(name).build();
        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(query, null);

        return dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + query.getOperationName(), Boolean.class);
    }
}
