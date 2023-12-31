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
package org.deltafi.core.datafetchers;

import com.jayway.jsonpath.TypeRef;
import com.netflix.graphql.dgs.DgsQueryExecutor;
import com.netflix.graphql.dgs.client.codegen.GraphQLQueryRequest;
import org.deltafi.core.generated.client.*;
import org.deltafi.core.generated.types.BackOffInput;
import org.deltafi.core.generated.types.ResumePolicyInput;
import org.deltafi.core.types.Result;
import org.deltafi.core.types.ResumePolicy;

import java.util.List;

public class ResumePolicyDatafetcherTestHelper {

    public static final String ERROR_SUBSTRING = "failure";
    public static final String FLOW_NAME1 = "flowName1";
    public static final String FLOW_NAME2 = "flowName2";
    public static final String FLOW_NAME3 = "flowName3";
    public static final String POLICY_NAME1 = "policyName1";
    public static final String POLICY_NAME2 = "policyName2";
    public static final String POLICY_NAME3 = "policyName3";
    public static final String ACTION1 = FLOW_NAME1 + ".action";
    public static final String ACTION2 = FLOW_NAME2 + ".action";
    public static final String ACTION3 = FLOW_NAME3 + ".action";
    public static final String ACTION_TYPE = "actionType";
    public static final int MAX_ATTEMPTS = 10;
    public static final int DEFAULT_PRIORITY = 150;
    public static final int DELAY = 100;
    public static final int MAX_DELAY = 500;
    public static final int MULTIPLIER = 1;
    public static final boolean RANDOM = false;

    public static Result applyResumePolicies(DgsQueryExecutor dgsQueryExecutor, List<String> names) {
        ApplyResumePoliciesProjectionRoot projection = new ApplyResumePoliciesProjectionRoot()
                .success()
                .info()
                .errors();

        ApplyResumePoliciesGraphQLQuery query =
                ApplyResumePoliciesGraphQLQuery.newRequest().names(names).build();
        GraphQLQueryRequest graphQLQueryRequest =
                new GraphQLQueryRequest(query, projection);

        return dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + query.getOperationName(),
                Result.class);
    }

    public static Result resumePolicyDryRun(DgsQueryExecutor dgsQueryExecutor, String name, String flow, String errorSubstring) {
        ResumePolicyDryRunProjectionRoot projection = new ResumePolicyDryRunProjectionRoot()
                .success()
                .info()
                .errors();

        ResumePolicyInput input = makeResumePolicy(name, errorSubstring, flow, null, null, MAX_ATTEMPTS, DELAY);

        ResumePolicyDryRunGraphQLQuery query =
                ResumePolicyDryRunGraphQLQuery.newRequest().resumePolicyInput(input).build();
        GraphQLQueryRequest graphQLQueryRequest =
                new GraphQLQueryRequest(query, projection);

        return dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + query.getOperationName(),
                Result.class);
    }

    public static List<ResumePolicy> getAllResumePolicies(DgsQueryExecutor dgsQueryExecutor) {
        GetAllResumePoliciesProjectionRoot projection = new GetAllResumePoliciesProjectionRoot()
                .id()
                .name()
                .errorSubstring()
                .flow()
                .action()
                .actionType()
                .maxAttempts()
                .priority()
                .backOff()
                .delay()
                .maxDelay()
                .multiplier()
                .random()
                .parent();

        GetAllResumePoliciesGraphQLQuery query =
                GetAllResumePoliciesGraphQLQuery.newRequest().build();
        GraphQLQueryRequest graphQLQueryRequest =
                new GraphQLQueryRequest(query, projection);

        TypeRef<List<ResumePolicy>> policyListType = new TypeRef<>() {
        };

        return dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + query.getOperationName(),
                policyListType);
    }

    public static ResumePolicy getResumePolicy(DgsQueryExecutor dgsQueryExecutor, String id) {
        GetResumePolicyProjectionRoot projection = new GetResumePolicyProjectionRoot()
                .id()
                .name()
                .errorSubstring()
                .flow()
                .action()
                .actionType()
                .maxAttempts()
                .priority()
                .backOff()
                .delay()
                .maxDelay()
                .multiplier()
                .random()
                .parent();

        GetResumePolicyGraphQLQuery query =
                GetResumePolicyGraphQLQuery.newRequest().id(id).build();
        GraphQLQueryRequest graphQLQueryRequest =
                new GraphQLQueryRequest(query, projection);

        return dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + query.getOperationName(),
                ResumePolicy.class);
    }

    public static List<Result> loadResumePolicyWithDuplicate(DgsQueryExecutor dgsQueryExecutor) {
        ResumePolicyInput input = makeResumePolicy(POLICY_NAME1, ERROR_SUBSTRING, FLOW_NAME1, ACTION1, ACTION_TYPE, MAX_ATTEMPTS, DELAY);
        ResumePolicyInput duplicateCriteria = makeResumePolicy("different", ERROR_SUBSTRING, FLOW_NAME1, ACTION1, ACTION_TYPE, MAX_ATTEMPTS, DELAY);
        ResumePolicyInput duplicateName = makeResumePolicy(POLICY_NAME1, "different", FLOW_NAME1, ACTION1, ACTION_TYPE, MAX_ATTEMPTS, DELAY);
        ResumePolicyInput third = makeResumePolicy(POLICY_NAME2, ERROR_SUBSTRING, FLOW_NAME2, ACTION2, ACTION_TYPE, MAX_ATTEMPTS, DELAY);
        return executeLoadPolicies(dgsQueryExecutor, true, List.of(input, duplicateCriteria, duplicateName, third));
    }

    public static List<Result> loadResumePolicy(
            DgsQueryExecutor dgsQueryExecutor,
            boolean replace,
            String policyNane,
            String flowName,
            String errorSubstring) {
        ResumePolicyInput input = makeResumePolicy(policyNane, errorSubstring, flowName, null, null, MAX_ATTEMPTS, DELAY);
        return executeLoadPolicies(dgsQueryExecutor, replace, List.of(input));
    }

    public static BackOffInput makeBackOff(int delay) {
        return BackOffInput.newBuilder()
                .delay(delay)
                .maxDelay(MAX_DELAY)
                .random(RANDOM)
                .multiplier(MULTIPLIER)
                .build();
    }

    public static ResumePolicyInput makeResumePolicy(
            String name,
            String errorSubstring,
            String flow,
            String action,
            String actionType,
            int maxAttempts,
            int delay) {
        return ResumePolicyInput.newBuilder()
                .name(name)
                .errorSubstring(errorSubstring)
                .flow(flow)
                .action(action)
                .actionType(actionType)
                .maxAttempts(maxAttempts)
                .priority(DEFAULT_PRIORITY)
                .backOff(makeBackOff(delay))
                .build();
    }

    public static boolean isDefaultFlow(ResumePolicy policy) {
        return policy.getFlow().equals(FLOW_NAME1);
    }

    public static boolean matchesDefault(ResumePolicy policy) {
        return policy.getErrorSubstring().equals(ERROR_SUBSTRING) &&
                policy.getFlow().equals(FLOW_NAME1) &&
                policy.getAction().equals(ACTION1) &&
                policy.getActionType().equals(ACTION_TYPE) &&
                policy.getName().equals(POLICY_NAME1) &&
                policy.getMaxAttempts() == MAX_ATTEMPTS &&
                policy.getPriority() == DEFAULT_PRIORITY &&
                policy.getBackOff().getDelay() == DELAY &&
                policy.getBackOff().getMaxDelay() == MAX_DELAY &&
                policy.getBackOff().getMultiplier() == MULTIPLIER &&
                policy.getBackOff().getRandom() == RANDOM;
    }

    public static boolean matchesUpdated(ResumePolicy policy) {
        return policy.getErrorSubstring().equals(ERROR_SUBSTRING) &&
                policy.getFlow().equals(FLOW_NAME3) &&
                policy.getAction().equals(ACTION3) &&
                policy.getActionType().equals(ACTION_TYPE) &&
                policy.getName().equals(POLICY_NAME3) &&
                policy.getMaxAttempts() == MAX_ATTEMPTS &&
                policy.getPriority() == DEFAULT_PRIORITY &&
                policy.getBackOff().getDelay() == (2 * DELAY) &&
                policy.getBackOff().getMaxDelay() == MAX_DELAY &&
                policy.getBackOff().getMultiplier() == MULTIPLIER &&
                policy.getBackOff().getRandom() == RANDOM;
    }

    static private List<Result> executeLoadPolicies(DgsQueryExecutor dgsQueryExecutor, boolean replaceAll, List<ResumePolicyInput> policies) {
        LoadResumePoliciesGraphQLQuery query = LoadResumePoliciesGraphQLQuery.newRequest().replaceAll(replaceAll).policies(policies).build();
        LoadResumePoliciesProjectionRoot projection = new LoadResumePoliciesProjectionRoot()
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

    public static boolean removeResumePolicy(DgsQueryExecutor dgsQueryExecutor, String id) {
        RemoveResumePolicyGraphQLQuery query = RemoveResumePolicyGraphQLQuery.newRequest().id(id).build();

        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(query, null);

        return dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + query.getOperationName(), Boolean.class);
    }

    public static Result updateResumePolicy(DgsQueryExecutor dgsQueryExecutor, String id) {
        ResumePolicyInput input = makeResumePolicy(POLICY_NAME3, ERROR_SUBSTRING, FLOW_NAME3, ACTION3, ACTION_TYPE, MAX_ATTEMPTS, 2 * DELAY);
        input.setId(id);

        UpdateResumePolicyProjectionRoot projection = new UpdateResumePolicyProjectionRoot()
                .success()
                .errors();

        UpdateResumePolicyGraphQLQuery query =
                UpdateResumePolicyGraphQLQuery.newRequest().resumePolicy(input).build();
        GraphQLQueryRequest graphQLQueryRequest =
                new GraphQLQueryRequest(query, projection);

        return dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + query.getOperationName(),
                Result.class);
    }

}
