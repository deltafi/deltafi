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
package org.deltafi.core.action.filter;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.filter.FilterResult;
import org.deltafi.actionkit.action.transform.TransformAction;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.actionkit.action.transform.TransformResultType;
import org.deltafi.common.types.ActionContext;
import org.jetbrains.annotations.NotNull;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class Filter extends TransformAction<FilterParameters> {
    public Filter() {
        super ("Filters by default or when optional criteria is met in content or metadata.");
    }

    @Override
    public TransformResultType transform(@NotNull ActionContext context, @NotNull FilterParameters params, @NotNull TransformInput input) {
        if ((params.getFilterExpressions() == null) || params.getFilterExpressions().isEmpty()) {
            return new FilterResult(context, "Filtered by fiat");
        }

        SpelExpressionParser parser = new SpelExpressionParser();
        StandardEvaluationContext spelContext = new StandardEvaluationContext();
        spelContext.setRootObject(input);

        int matchCount = (int) params.getFilterExpressions().stream()
                .map(parser::parseExpression)
                .map(expression -> expression.getValue(spelContext, Boolean.class))
                .filter(result -> result != null && result)
                .count();

        boolean filtered = false;
        String reason = "";

        switch (params.getFilterBehavior()) {
            case ANY -> {
                filtered = matchCount > 0;
                reason = "Filtered because at least one of the criteria matched";
            }
            case ALL -> {
                filtered = matchCount == params.getFilterExpressions().size();
                reason = "Filtered because all of the criteria matched";
            }
            case NONE -> {
                filtered = matchCount == 0;
                reason = "Filtered because none of the criteria matched";
            }
        }

        if (filtered) {
            return new FilterResult(context, reason);
        }

        TransformResult transformResult = new TransformResult(context);
        transformResult.setContent(input.getContent());
        return transformResult;
    }
}
