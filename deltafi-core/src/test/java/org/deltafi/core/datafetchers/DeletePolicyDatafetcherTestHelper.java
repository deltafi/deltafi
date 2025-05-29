/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.datafetchers;

import com.jayway.jsonpath.TypeRef;
import com.netflix.graphql.dgs.DgsQueryExecutor;
import com.netflix.graphql.dgs.client.codegen.GraphQLQueryRequest;
import org.deltafi.core.generated.client.*;
import org.deltafi.core.types.DeletePolicies;
import org.deltafi.core.types.DeletePolicy;
import org.deltafi.core.types.Result;
import org.deltafi.core.types.TimedDeletePolicy;

import java.util.List;
import java.util.UUID;

import static org.deltafi.core.util.Constants.SCALARS;

public class DeletePolicyDatafetcherTestHelper {

    public static final String AFTER_COMPLETE_POLICY = "afterCompletePolicy";
    public static final String OFFLINE_POLICY = "offlinePolicy";

    private static final List<DeletePolicy> TIMED_POLICY_LIST = List.of(
            TimedDeletePolicy.builder()
                    .id(UUID.randomUUID())
                    .name(AFTER_COMPLETE_POLICY)
                    .enabled(true)
                    .afterComplete("PT2S")
                    .deleteMetadata(false)
                    .build(),
            TimedDeletePolicy.builder()
                    .id(UUID.randomUUID())
                    .name(OFFLINE_POLICY)
                    .flow("bogus")
                    .enabled(false)
                    .afterCreate("PT2S")
                    .minBytes(1000L)
                    .deleteMetadata(false)
                    .build());

    private static final GetDeletePoliciesProjectionRoot<?, ?> projection = new GetDeletePoliciesProjectionRoot<>()
            .id()
            .name()
            .enabled()
            .flow()
            .onTimedDeletePolicy()
            .afterComplete()
            .afterCreate()
            .minBytes()
            .parent();

    public static List<DeletePolicy> getDeletePolicies(DgsQueryExecutor dgsQueryExecutor) {

        GetDeletePoliciesGraphQLQuery query =
                GetDeletePoliciesGraphQLQuery.newRequest().build();
        GraphQLQueryRequest graphQLQueryRequest =
                new GraphQLQueryRequest(query, projection, SCALARS);

        TypeRef<List<DeletePolicy>> policiesListType = new TypeRef<>() {
        };

        return dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + query.getOperationName(),
                policiesListType);
    }

    public static void loadOneDeletePolicy(DgsQueryExecutor dgsQueryExecutor) {
        executeLoad(dgsQueryExecutor, List.of(TIMED_POLICY_LIST.getFirst()));
    }

    public static Result replaceAllDeletePolicies(DgsQueryExecutor dgsQueryExecutor) {
        return executeLoad(dgsQueryExecutor, TIMED_POLICY_LIST);
    }

    private static Result executeLoad(DgsQueryExecutor dgsQueryExecutor, List<DeletePolicy> timedPolicies) {
        DeletePolicies input = new DeletePolicies(timedPolicies);

        LoadDeletePoliciesGraphQLQuery query = LoadDeletePoliciesGraphQLQuery.newRequest()
                .replaceAll(true)
                .policies(input)
                .build();

        LoadDeletePoliciesProjectionRoot<?, ?> projection = new LoadDeletePoliciesProjectionRoot<>()
                .success()
                .info()
                .errors();

        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(query, projection, SCALARS);

        return dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + query.getOperationName(),
                Result.class);
    }

    public static boolean enablePolicy(DgsQueryExecutor dgsQueryExecutor,
                                       UUID id,
                                       boolean enabled) {
        EnablePolicyGraphQLQuery query = EnablePolicyGraphQLQuery.newRequest()
                .id(id).enabled(enabled).build();

        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(query, new UpdateResumePolicyProjectionRoot<>(), SCALARS);

        return dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + query.getOperationName(), Boolean.class);
    }

    public static boolean removeDeletePolicy(DgsQueryExecutor dgsQueryExecutor, UUID id) {
        RemoveDeletePolicyGraphQLQuery query = RemoveDeletePolicyGraphQLQuery.newRequest().id(id).build();

        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(query, new UpdateResumePolicyProjectionRoot<>(), SCALARS);

        return dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + query.getOperationName(), Boolean.class);
    }

    public static Result updateTimedDeletePolicy(DgsQueryExecutor dgsQueryExecutor,
                                                 TimedDeletePolicy input) {
        UpdateTimedDeletePolicyGraphQLQuery query = UpdateTimedDeletePolicyGraphQLQuery.newRequest()
                .policyUpdate(input)
                .build();

        UpdateTimedDeletePolicyProjectionRoot<?, ?> projection = new UpdateTimedDeletePolicyProjectionRoot<>()
                .success()
                .errors();

        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(query, projection, SCALARS);

        return dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + query.getOperationName(),
                Result.class);
    }

}
