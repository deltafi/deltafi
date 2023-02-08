/**
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
package org.deltafi.core.datafetchers;

import com.jayway.jsonpath.TypeRef;
import com.netflix.graphql.dgs.DgsQueryExecutor;
import com.netflix.graphql.dgs.client.codegen.GraphQLQueryRequest;
import org.deltafi.core.generated.client.*;
import org.deltafi.core.types.*;

import java.util.Collections;
import java.util.List;

public class DeletePolicyDatafetcherTestHelper {

    public static final String DISK_SPACE_PERCENT_POLICY = "diskSpacePercent";
    public static final String AFTER_COMPLETE_POLICY = "afterCompletePolicy";
    public static final String OFFLINE_POLICY = "offlinePolicy";

    private static final List<DiskSpaceDeletePolicy> DISK_POLICY_LIST = List.of(
            DiskSpaceDeletePolicy.newBuilder()
                    .name(DISK_SPACE_PERCENT_POLICY)
                    .enabled(true)
                    .maxPercent(99)
                    .build());

    private static final List<TimedDeletePolicy> TIMED_POLICY_LIST = List.of(
            TimedDeletePolicy.newBuilder()
                    .name(AFTER_COMPLETE_POLICY)
                    .enabled(true)
                    .afterComplete("PT2S")
                    .build(),
            TimedDeletePolicy.newBuilder()
                    .name(OFFLINE_POLICY)
                    .flow("bogus")
                    .enabled(false)
                    .afterCreate("PT2S")
                    .minBytes(1000L)
                    .build());

    private static final GetDeletePoliciesProjectionRoot projection = new GetDeletePoliciesProjectionRoot()
            .id()
            .name()
            .enabled()
            .flow()
            .onDiskSpaceDeletePolicy()
            .maxPercent()
            .parent()
            .onTimedDeletePolicy()
            .afterComplete()
            .afterCreate()
            .minBytes()
            .parent();

    static public List<DeletePolicy> getDeletePolicies(DgsQueryExecutor dgsQueryExecutor) {

        GetDeletePoliciesGraphQLQuery query =
                GetDeletePoliciesGraphQLQuery.newRequest().build();
        GraphQLQueryRequest graphQLQueryRequest =
                new GraphQLQueryRequest(query, projection);

        TypeRef<List<DeletePolicy>> policiesListType = new TypeRef<>() {
        };

        return dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + query.getOperationName(),
                policiesListType);
    }

    static public Result loadOneDeletePolicy(DgsQueryExecutor dgsQueryExecutor) {
        return executeLoad(dgsQueryExecutor, true, DISK_POLICY_LIST, Collections.emptyList());
    }

    static public Result addOnePolicy(DgsQueryExecutor dgsQueryExecutor) {
        return executeLoad(dgsQueryExecutor, false, DISK_POLICY_LIST, Collections.emptyList());
    }

    static public Result replaceAllDeletePolicies(DgsQueryExecutor dgsQueryExecutor) {
        return executeLoad(dgsQueryExecutor, true, DISK_POLICY_LIST, TIMED_POLICY_LIST);
    }

    static private Result executeLoad(DgsQueryExecutor dgsQueryExecutor,
                                      boolean replace,
                                      List<DiskSpaceDeletePolicy> diskPolicies,
                                      List<TimedDeletePolicy> timedPolicies) {

        DeletePolicies input = DeletePolicies.newBuilder()
                .diskSpacePolicies(diskPolicies)
                .timedPolicies(timedPolicies)
                .build();

        LoadDeletePoliciesGraphQLQuery query = LoadDeletePoliciesGraphQLQuery.newRequest()
                .replaceAll(replace)
                .policies(input)
                .build();

        LoadDeletePoliciesProjectionRoot projection = new LoadDeletePoliciesProjectionRoot()
                .success()
                .errors();

        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(query, projection);

        return dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + query.getOperationName(),
                Result.class);
    }

    static public boolean enablePolicy(DgsQueryExecutor dgsQueryExecutor,
                                       String id,
                                       boolean enabled) {
        EnablePolicyGraphQLQuery query = EnablePolicyGraphQLQuery.newRequest()
                .id(id).enabled(enabled).build();

        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(query, null);

        return dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + query.getOperationName(), Boolean.class);
    }

    static public boolean removeDeletePolicy(DgsQueryExecutor dgsQueryExecutor, String id) {
        RemoveDeletePolicyGraphQLQuery query = RemoveDeletePolicyGraphQLQuery.newRequest().id(id).build();

        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(query, null);

        return dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + query.getOperationName(), Boolean.class);
    }

    static public Result updateDiskSpaceDeletePolicy(DgsQueryExecutor dgsQueryExecutor,
                                                     DiskSpaceDeletePolicy input) {
        UpdateDiskSpaceDeletePolicyGraphQLQuery query = UpdateDiskSpaceDeletePolicyGraphQLQuery.newRequest()
                .policyUpdate(input)
                .build();

        UpdateDiskSpaceDeletePolicyProjectionRoot projection = new UpdateDiskSpaceDeletePolicyProjectionRoot()
                .success()
                .errors();

        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(query, projection);

        return dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + query.getOperationName(),
                Result.class);
    }

    static public Result updateTimedDeletePolicy(DgsQueryExecutor dgsQueryExecutor,
                                                 TimedDeletePolicy input) {
        UpdateTimedDeletePolicyGraphQLQuery query = UpdateTimedDeletePolicyGraphQLQuery.newRequest()
                .policyUpdate(input)
                .build();

        UpdateTimedDeletePolicyProjectionRoot projection = new UpdateTimedDeletePolicyProjectionRoot()
                .success()
                .errors();

        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(query, projection);

        return dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + query.getOperationName(),
                Result.class);
    }

}
