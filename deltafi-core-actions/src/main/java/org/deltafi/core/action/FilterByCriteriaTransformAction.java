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
package org.deltafi.core.action;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.filter.FilterResult;
import org.deltafi.actionkit.action.transform.TransformAction;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.actionkit.action.transform.TransformResultType;
import org.deltafi.common.types.ActionContext;
import org.deltafi.core.parameters.FilterByCriteriaParameters;
import org.jetbrains.annotations.NotNull;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class FilterByCriteriaTransformAction extends TransformAction<FilterByCriteriaParameters> {
    public FilterByCriteriaTransformAction() {
        super("""
                The FilterByCriteriaTransformAction allows you to filter or pass DeltaFiles based on specific criteria defined using Spring Expression Language (SpEL). The action takes a list of SpEL expressions that are evaluated against the metadata and content. Depending on the configured filter behavior, the action filters if 'ANY', 'ALL', or 'NONE' of the expressions match.
                Examples:
                - To filter if metadata key 'x' is set to 'y': "#metadata['x'] == 'y'"
                - To filter if 'x' is not 'y' or if 'x' is not present: "#metadata['x'] != 'y' || !#metadata.containsKey('x')"
                - To filter if no content is JSON: "!#content.stream().anyMatch(c -> c.getMediaType.equals('application/json'))"
                """);
    }

    @Override
    public TransformResultType transform(@NotNull ActionContext context, @NotNull FilterByCriteriaParameters params, @NotNull TransformInput input) {
        SpelExpressionParser parser = new SpelExpressionParser();
        StandardEvaluationContext spelContext = new StandardEvaluationContext();
        spelContext.setRootObject(input);

        int matchCount = (int) params.getFilterExpressions().stream()
                .map(parser::parseExpression)
                .map(expression -> expression.getValue(spelContext, Boolean.class))
                .filter(result -> result != null && result)
                .count();

        String reason;
        boolean shouldFilter;
        switch (params.getFilterBehavior()) {
            case ANY -> {
                shouldFilter = matchCount > 0;
                reason = "Filtered because at least one of the criteria matched";
            }
            case ALL -> {
                shouldFilter = matchCount == params.getFilterExpressions().size();
                reason = "Filtered because all of the criteria matched";
            }
            case NONE -> {
                shouldFilter = matchCount == 0;
                reason = "Filtered because none of the criteria matched";
            }
            default -> {
                return new ErrorResult(context, "Unknown filter behavior: " + params.getFilterBehavior());
            }
        }

        if (shouldFilter) {
            return new FilterResult(context, reason);
        } else {
            TransformResult transformResult = new TransformResult(context);
            transformResult.setContent(input.getContent());
            return transformResult;
        }
    }
}
