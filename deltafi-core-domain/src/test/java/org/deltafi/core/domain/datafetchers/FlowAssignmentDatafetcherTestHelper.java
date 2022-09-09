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
import org.deltafi.common.types.KeyValue;
import org.deltafi.common.types.SourceInfo;
import org.deltafi.core.domain.generated.client.*;
import org.deltafi.core.domain.generated.types.FlowAssignmentRuleInput;
import org.deltafi.core.domain.services.FlowAssignmentService;
import org.deltafi.core.domain.types.FlowAssignmentRule;
import org.deltafi.core.domain.types.Result;

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

    static public List<FlowAssignmentRule> getAllFlowAssignmentRules(DgsQueryExecutor dgsQueryExecutor) {
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

        TypeRef<List<FlowAssignmentRule>> ruleListType = new TypeRef<>() {
        };

        return dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + query.getOperationName(),
                ruleListType);
    }

    static public FlowAssignmentRule getFlowAssignment(DgsQueryExecutor dgsQueryExecutor, String name) {
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
                FlowAssignmentRule.class);
    }

    static public Result saveFirstRule(DgsQueryExecutor dgsQueryExecutor) {
        FlowAssignmentRuleInput input = makeRegexRule(RULE_NAME1, FLOW_NAME1, 0, FILENAME_REGEX);
        return executeLoadRules(dgsQueryExecutor, true, List.of(input)).get(0);
    }

    static public Result saveBadRule(DgsQueryExecutor dgsQueryExecutor, boolean replaceAll) {
        return executeLoadRules(dgsQueryExecutor, replaceAll, List.of(makeBadRule())).get(0);
    }

    static public FlowAssignmentRuleInput makeRegexRule(
            String name,
            String flow,
            int priority,
            String regex) {
        if (priority > 0) {
            return FlowAssignmentRuleInput.newBuilder()
                    .name(name)
                    .flow(flow)
                    .priority(priority)
                    .filenameRegex(regex)
                    .build();
        } else {
            return FlowAssignmentRuleInput.newBuilder()
                    .name(name)
                    .flow(flow)
                    .filenameRegex(regex)
                    .build();
        }
    }

    static public List<Result> saveAllRules(DgsQueryExecutor dgs) {
        List<FlowAssignmentRuleInput> rules = List.of(
                makeRegexRule(RULE_NAME1, "b", 500, "x"),
                makeRegexRule(RULE_NAME2, "a", 500, "x"),
                makeRegexRule(RULE_NAME3, "d", 501, "x"),
                makeRegexRule(RULE_NAME4, "c", 499, "x"),
                makeBadRule());
        return executeLoadRules(dgs, true, rules);
    }

    static public FlowAssignmentRuleInput makeBadRule() {
        return FlowAssignmentRuleInput.newBuilder()
                .name("")
                .flow("")
                .build();
    }

    static public Result saveSecondRuleSet(DgsQueryExecutor dgsQueryExecutor) {
        FlowAssignmentRuleInput input = FlowAssignmentRuleInput.newBuilder()
                .name(RULE_NAME2)
                .flow(FLOW_NAME2)
                .priority(FlowAssignmentService.DEFAULT_PRIORITY - 1)
                .requiredMetadata(Collections.singletonList(new KeyValue(META_KEY, META_VALUE)))
                .build();

        return executeLoadRules(dgsQueryExecutor, false, List.of(input)).get(0);
    }

    static private List<Result> executeLoadRules(DgsQueryExecutor dgsQueryExecutor, boolean replaceAll, List<FlowAssignmentRuleInput> rules) {
        LoadFlowAssignmentRulesGraphQLQuery query = LoadFlowAssignmentRulesGraphQLQuery.newRequest().replaceAll(replaceAll).rules(rules).build();
        LoadFlowAssignmentRulesProjectionRoot projection = new LoadFlowAssignmentRulesProjectionRoot()
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
