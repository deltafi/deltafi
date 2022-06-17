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
import org.deltafi.core.domain.api.types.KeyValue;
import org.deltafi.core.domain.api.types.SourceInfo;
import org.deltafi.core.domain.generated.client.*;
import org.deltafi.core.domain.generated.types.FlowAssignmentRuleInput;
import org.deltafi.core.domain.generated.types.Result;
import org.deltafi.core.domain.services.FlowAssignmentService;

import java.util.Collections;
import java.util.List;

public class FlowAssignmentDatafetcherTestHelper {

    public static final String RULE_NAME1 = "ruleName1";
    public static final String RULE_NAME2 = "ruleName2";
    public static final String RULE_NAME3 = "ruleName3";
    public static final String RULE_NAME4 = "ruleName4";
    public static final String FLOW_NAME1 = "flowName1";
    public static final String FLOW_NAME2 = "flowName2";
    public static final String FILENAME_REGEX = "filenameRegex";
    public static final String META_KEY = "key";
    public static final String META_VALUE = "value";

    static public List<org.deltafi.core.domain.api.types.FlowAssignmentRule> getAllFlowAssignmentRules(DgsQueryExecutor dgsQueryExecutor) {
        GetAllFlowAssignmentRulesProjectionRoot projection = new GetAllFlowAssignmentRulesProjectionRoot()
                .name()
                .flow()
                .priority()
                .filenameRegex()
                .requiredMetadata()
                .key()
                .value()
                .parent();

        GetAllFlowAssignmentRulesGraphQLQuery query =
                GetAllFlowAssignmentRulesGraphQLQuery.newRequest().build();
        GraphQLQueryRequest graphQLQueryRequest =
                new GraphQLQueryRequest(query, projection);

        TypeRef<List<org.deltafi.core.domain.api.types.FlowAssignmentRule>> ruleListType = new TypeRef<>() {
        };

        return dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + query.getOperationName(),
                ruleListType);
    }

    static public org.deltafi.core.domain.api.types.FlowAssignmentRule getFlowAssignment(DgsQueryExecutor dgsQueryExecutor, String name) {
        GetFlowAssignmentRuleProjectionRoot projection = new GetFlowAssignmentRuleProjectionRoot()
                .name()
                .flow()
                .priority()
                .filenameRegex()
                .requiredMetadata()
                .key()
                .value()
                .parent();

        GetFlowAssignmentRuleGraphQLQuery query =
                GetFlowAssignmentRuleGraphQLQuery.newRequest().name(name).build();
        GraphQLQueryRequest graphQLQueryRequest =
                new GraphQLQueryRequest(query, projection);

        return dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + query.getOperationName(),
                org.deltafi.core.domain.api.types.FlowAssignmentRule.class);
    }

    static public Result saveFirstRule(DgsQueryExecutor dgsQueryExecutor) {
        return saveRegexRule(dgsQueryExecutor, RULE_NAME1, FLOW_NAME1, 0, FILENAME_REGEX);
    }

    static public Result saveRegexRule(
            DgsQueryExecutor dgsQueryExecutor,
            String name,
            String flow,
            int priority,
            String regex) {
        if (priority > 0) {
            return executeSaveRule(dgsQueryExecutor, FlowAssignmentRuleInput.newBuilder()
                    .name(name)
                    .flow(flow)
                    .priority(priority)
                    .filenameRegex(regex)
                    .build());
        } else {
            return executeSaveRule(dgsQueryExecutor, FlowAssignmentRuleInput.newBuilder()
                    .name(name)
                    .flow(flow)
                    .filenameRegex(regex)
                    .build());
        }

    }

    static public boolean saveAllRules(DgsQueryExecutor dgs) {
        boolean result = saveRegexRule(dgs, RULE_NAME1, "b", 500, "x").getSuccess();
        result |= saveRegexRule(dgs, RULE_NAME2, "a", 500, "x").getSuccess();
        result |= saveRegexRule(dgs, RULE_NAME3, "d", 501, "x").getSuccess();
        result |= saveRegexRule(dgs, RULE_NAME4, "c", 499, "x").getSuccess();
        return result;
    }

    static public Result saveBadRule(DgsQueryExecutor dgsQueryExecutor) {
        FlowAssignmentRuleInput input = FlowAssignmentRuleInput.newBuilder()
                .name("")
                .flow("")
                .build();
        return executeSaveRule(dgsQueryExecutor, input);
    }

    static public Result saveSecondRuleSet(DgsQueryExecutor dgsQueryExecutor) {
        FlowAssignmentRuleInput input = FlowAssignmentRuleInput.newBuilder()
                .name(RULE_NAME2)
                .flow(FLOW_NAME2)
                .priority(FlowAssignmentService.DEFAULT_PRIORITY - 1)
                .requiredMetadata(Collections.singletonList(new KeyValue(META_KEY, META_VALUE)))
                .build();

        return executeSaveRule(dgsQueryExecutor, input);
    }

    static private Result executeSaveRule(DgsQueryExecutor dgsQueryExecutor, FlowAssignmentRuleInput input) {
        SaveFlowAssignmentRuleGraphQLQuery query = SaveFlowAssignmentRuleGraphQLQuery.newRequest().flowAssignmentRule(input).build();
        SaveFlowAssignmentRuleProjectionRoot projection = new SaveFlowAssignmentRuleProjectionRoot()
                .success()
                .errors();

        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(query, projection);

        Result result = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + query.getOperationName(), Result.class);

        return result;
    }

    static public String resolveFlowAssignment(DgsQueryExecutor dgsQueryExecutor, SourceInfo sourceInfo) {
        ResolveFlowFromFlowAssignmentRulesGraphQLQuery query = ResolveFlowFromFlowAssignmentRulesGraphQLQuery
                .newRequest().sourceInfo(sourceInfo).build();

        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(query, null);

        return dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + query.getOperationName(), String.class);
    }

    static public boolean removeFlowAssignment(DgsQueryExecutor dgsQueryExecutor, String name) {
        RemoveFlowAssignmentRuleGraphQLQuery query = RemoveFlowAssignmentRuleGraphQLQuery.newRequest().name(name).build();

        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(query, null);

        return dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + query.getOperationName(), Boolean.class);
    }

}
