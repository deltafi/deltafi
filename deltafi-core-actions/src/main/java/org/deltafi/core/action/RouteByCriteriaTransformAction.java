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
import org.deltafi.actionkit.action.ReinjectResult;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.filter.FilterResult;
import org.deltafi.actionkit.action.transform.TransformAction;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.actionkit.action.transform.TransformResultType;
import org.deltafi.common.types.ActionContext;
import org.deltafi.core.parameters.RouteByCriteriaParameters;
import org.jetbrains.annotations.NotNull;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class RouteByCriteriaTransformAction extends TransformAction<RouteByCriteriaParameters> {
    public RouteByCriteriaTransformAction() {
        super("""
                The RouteByCriteriaTransformAction allows you to route DeltaFiles to different flows based on specific criteria defined using Spring Expression Language (SpEL). Each SpEL expression is associated with a flow name, and if the expression evaluates to true, the content will be routed to that flow.
                Examples:
                - To route to 'flow1' if metadata key 'x' is 'y': "metadata['x'] == 'y'"
                - To route to 'flow2' if 'x' is not present: "!metadata.containsKey('x')"
                - To route to 'flow3' if any content is JSON: "content.stream().anyMatch(c -> c.getMediaType().equals('application/json'))"
                """);
    }

    @Override
    public TransformResultType transform(@NotNull ActionContext context, @NotNull RouteByCriteriaParameters params, @NotNull TransformInput input) {
        SpelExpressionParser parser = new SpelExpressionParser();
        StandardEvaluationContext spelContext = new StandardEvaluationContext();
        spelContext.setRootObject(input);

        for (Map.Entry<String, String> entry : params.getRoutingExpressions().entrySet()) {
            Expression expression = parser.parseExpression(entry.getKey());
            Boolean result = expression.getValue(spelContext, Boolean.class);
            if (result != null && result) {
                ReinjectResult reinjectResult = new ReinjectResult(context);
                reinjectResult.addChild("routed-from-" + context.getFlow(), entry.getValue(), input.getContent(), input.getMetadata());
                return reinjectResult;
            }
        }

        switch (params.getNoMatchBehavior()) {
            case ERROR -> {
                return new ErrorResult(context, "No routing expressions matched");
            }
            case FILTER -> {
                return new FilterResult(context, "No routing expressions matched");
            }
            case PASSTHROUGH -> {
                return new TransformResult(context, input.getContent());
            }
            default -> {
                return new ErrorResult(context, "Unknown no-match behavior: " + params.getNoMatchBehavior());
            }
        }
    }
}
