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
import org.deltafi.core.generated.types.BackOffInput;
import org.deltafi.core.generated.types.RetryPolicyInput;
import org.deltafi.core.types.Result;
import org.deltafi.core.types.RetryPolicy;

import java.util.List;

public class RetryPolicyDatafetcherTestHelper {

    public static final String ERROR_SUBSTRING = "failure";
    public static final String FLOW_NAME1 = "flowName1";
    public static final String FLOW_NAME2 = "flowName2";
    public static final String FLOW_NAME3 = "flowName3";
    public static final String ACTION = "action";
    public static final String ACTION_TYPE = "actionType";
    public static final int MAX_ATTEMPTS = 10;
    public static final int DELAY = 100;
    public static final int MAX_DELAY = 500;
    public static final int MULTIPLIER = 1;
    public static final boolean RANDOM = false;

    static public List<RetryPolicy> getAllRetryPolicies(DgsQueryExecutor dgsQueryExecutor) {
        GetAllRetryPoliciesProjectionRoot projection = new GetAllRetryPoliciesProjectionRoot()
                .id()
                .errorSubstring()
                .flow()
                .action()
                .actionType()
                .maxAttempts()
                .backOff()
                .delay()
                .maxDelay()
                .multiplier()
                .random()
                .parent();

        GetAllRetryPoliciesGraphQLQuery query =
                GetAllRetryPoliciesGraphQLQuery.newRequest().build();
        GraphQLQueryRequest graphQLQueryRequest =
                new GraphQLQueryRequest(query, projection);

        TypeRef<List<RetryPolicy>> policyListType = new TypeRef<>() {
        };

        return dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + query.getOperationName(),
                policyListType);
    }

    static public RetryPolicy getRetryPolicy(DgsQueryExecutor dgsQueryExecutor, String id) {
        GetRetryPolicyProjectionRoot projection = new GetRetryPolicyProjectionRoot()
                .id()
                .errorSubstring()
                .flow()
                .action()
                .actionType()
                .maxAttempts()
                .backOff()
                .delay()
                .maxDelay()
                .multiplier()
                .random()
                .parent();

        GetRetryPolicyGraphQLQuery query =
                GetRetryPolicyGraphQLQuery.newRequest().id(id).build();
        GraphQLQueryRequest graphQLQueryRequest =
                new GraphQLQueryRequest(query, projection);

        return dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + query.getOperationName(),
                RetryPolicy.class);
    }

    static public List<Result> loadRetryPolicyWithDuplicate(DgsQueryExecutor dgsQueryExecutor) {
        RetryPolicyInput input = makeRetryPolicy(ERROR_SUBSTRING, FLOW_NAME1, ACTION, ACTION_TYPE, MAX_ATTEMPTS, DELAY);
        RetryPolicyInput duplicate = makeRetryPolicy(ERROR_SUBSTRING, FLOW_NAME1, ACTION, ACTION_TYPE, 2 * MAX_ATTEMPTS, DELAY);
        RetryPolicyInput third = makeRetryPolicy(ERROR_SUBSTRING, FLOW_NAME2, ACTION, ACTION_TYPE, MAX_ATTEMPTS, DELAY);
        return executeLoadPolicies(dgsQueryExecutor, true, List.of(input, duplicate, third));
    }

    static public BackOffInput makeBackOff(int delay) {
        return BackOffInput.newBuilder()
                .delay(delay)
                .maxDelay(MAX_DELAY)
                .random(RANDOM)
                .multiplier(MULTIPLIER)
                .build();
    }

    static public RetryPolicyInput makeRetryPolicy(
            String errorSubstring,
            String flow,
            String action,
            String actionType,
            int maxAttempts,
            int delay) {
        return RetryPolicyInput.newBuilder()
                .errorSubstring(errorSubstring)
                .flow(flow)
                .action(action)
                .actionType(actionType)
                .maxAttempts(maxAttempts)
                .backOff(makeBackOff(delay))
                .build();
    }

    static public boolean isDefaultFlow(RetryPolicy policy) {
        return policy.getFlow().equals(FLOW_NAME1);
    }

    static public boolean matchesDefault(RetryPolicy policy) {
        return policy.getErrorSubstring().equals(ERROR_SUBSTRING) &&
                policy.getFlow().equals(FLOW_NAME1) &&
                policy.getAction().equals(ACTION) &&
                policy.getActionType().equals(ACTION_TYPE) &&
                policy.getMaxAttempts() == MAX_ATTEMPTS &&
                policy.getBackOff().getDelay() == DELAY &&
                policy.getBackOff().getMaxDelay() == MAX_DELAY &&
                policy.getBackOff().getMultiplier() == MULTIPLIER &&
                policy.getBackOff().getRandom() == RANDOM;
    }

    static public boolean matchesUpdated(RetryPolicy policy) {
        return policy.getErrorSubstring().equals(ERROR_SUBSTRING) &&
                policy.getFlow().equals(FLOW_NAME3) &&
                policy.getAction().equals(ACTION) &&
                policy.getActionType().equals(ACTION_TYPE) &&
                policy.getMaxAttempts() == MAX_ATTEMPTS &&
                policy.getBackOff().getDelay() == (2 * DELAY) &&
                policy.getBackOff().getMaxDelay() == MAX_DELAY &&
                policy.getBackOff().getMultiplier() == MULTIPLIER &&
                policy.getBackOff().getRandom() == RANDOM;
    }

    static private List<Result> executeLoadPolicies(DgsQueryExecutor dgsQueryExecutor, boolean replaceAll, List<RetryPolicyInput> policies) {
        LoadRetryPoliciesGraphQLQuery query = LoadRetryPoliciesGraphQLQuery.newRequest().replaceAll(replaceAll).policies(policies).build();
        LoadRetryPoliciesProjectionRoot projection = new LoadRetryPoliciesProjectionRoot()
                .success()
                .errors();

        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(query, projection);

        TypeRef<List<Result>> resultListType = new TypeRef<>() {
        };

        return dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + query.getOperationName(),
                resultListType);
    }

    static public boolean removeRetryPolicy(DgsQueryExecutor dgsQueryExecutor, String id) {
        RemoveRetryPolicyGraphQLQuery query = RemoveRetryPolicyGraphQLQuery.newRequest().id(id).build();

        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(query, null);

        return dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + query.getOperationName(), Boolean.class);
    }

    static public Result updateRetryPolicy(DgsQueryExecutor dgsQueryExecutor, String id) {
        RetryPolicyInput input = makeRetryPolicy(ERROR_SUBSTRING, FLOW_NAME3, ACTION, ACTION_TYPE, MAX_ATTEMPTS, 2 * DELAY);
        input.setId(id);

        UpdateRetryPolicyProjectionRoot projection = new UpdateRetryPolicyProjectionRoot()
                .success()
                .errors();

        UpdateRetryPolicyGraphQLQuery query =
                UpdateRetryPolicyGraphQLQuery.newRequest().retryPolicy(input).build();
        GraphQLQueryRequest graphQLQueryRequest =
                new GraphQLQueryRequest(query, projection);

        return dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + query.getOperationName(),
                Result.class);
    }

}
