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
import org.deltafi.core.domain.generated.client.*;
import org.deltafi.core.domain.generated.types.DiskSpaceDeletePolicyInput;
import org.deltafi.core.domain.generated.types.LoadDeletePoliciesInput;
import org.deltafi.core.domain.generated.types.Result;
import org.deltafi.core.domain.generated.types.TimedDeletePolicyInput;

import java.util.List;

public class DeletePolicyDatafetcherTestHelper {

    public static final String DISK_SPACE_PERCENT_POLICY = "diskSpacePercent";
    public static final String AFTER_COMPLETE_LOCKED_POLICY = "afterCompleteLockedPolicy";
    public static final String OFFLINE_POLICY = "offlinePolicy";

    private static final List<DiskSpaceDeletePolicyInput> DISK_POLICY_LIST = List.of(
            DiskSpaceDeletePolicyInput.newBuilder()
                    .id(DISK_SPACE_PERCENT_POLICY)
                    .enabled(true)
                    .locked(false)
                    .maxPercent(99)
                    .build());

    private static final List<TimedDeletePolicyInput> TIMED_POLICY_LIST = List.of(
            TimedDeletePolicyInput.newBuilder()
                    .id(AFTER_COMPLETE_LOCKED_POLICY)
                    .enabled(true)
                    .locked(true)
                    .afterComplete("PT2S")
                    .build(),
            TimedDeletePolicyInput.newBuilder()
                    .id(OFFLINE_POLICY)
                    .flow("bogus")
                    .enabled(false)
                    .locked(false)
                    .afterCreate("PT2S")
                    .minBytes(new Long(1000))
                    .build());

    private static final GetDeletePoliciesProjectionRoot projection = new GetDeletePoliciesProjectionRoot()
            .id()
            .enabled()
            .locked()
            .flow()
            .onDiskSpaceDeletePolicy()
            .maxPercent()
            .parent()
            .onTimedDeletePolicy()
            .afterComplete()
            .afterCreate()
            .minBytes()
            .parent();

    static public List<org.deltafi.core.domain.api.types.DeletePolicy> getDeletePolicies(DgsQueryExecutor dgsQueryExecutor) {

        GetDeletePoliciesGraphQLQuery query =
                GetDeletePoliciesGraphQLQuery.newRequest().build();
        GraphQLQueryRequest graphQLQueryRequest =
                new GraphQLQueryRequest(query, projection);

        TypeRef<List<org.deltafi.core.domain.api.types.DeletePolicy>> policiesListType = new TypeRef<>() {
        };

        return dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + query.getOperationName(),
                policiesListType);
    }

    static public Result replaceAllDeletePolicies(DgsQueryExecutor dgsQueryExecutor) {
        return executeLoad(dgsQueryExecutor, DISK_POLICY_LIST, TIMED_POLICY_LIST);
    }

    static private Result executeLoad(DgsQueryExecutor dgsQueryExecutor,
                                      List<DiskSpaceDeletePolicyInput> diskPolicies,
                                      List<TimedDeletePolicyInput> timedPolicies) {

        LoadDeletePoliciesInput input = LoadDeletePoliciesInput.newBuilder()
                .diskSpacePolicies(diskPolicies)
                .timedPolicies(timedPolicies)
                .build();

        LoadDeletePoliciesGraphQLQuery query = LoadDeletePoliciesGraphQLQuery.newRequest()
                .replaceAll(true)
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

}
