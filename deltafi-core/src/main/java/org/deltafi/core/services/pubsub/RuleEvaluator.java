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
package org.deltafi.core.services.pubsub;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.types.DeltaFile;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class RuleEvaluator {

    private final Map<String, Expression> expressionCache = new ConcurrentHashMap<>();

    /**
     * Evaluate the condition against the given DeltaFile. A null condition
     * will result in true. An invalid condition results in false.
     * @param condition SPeL expression to evaluate
     * @param deltaFile used in the evaluation
     * @return the result of evaluating the condition
     */
    public boolean evaluateCondition(String condition, DeltaFile deltaFile) {
        if (condition == null) {
            return true;
        }

        try {
            return doEvaluateCondition(condition, new ImmutableDeltaFile(deltaFile));
        } catch (Exception e) {
            log.error("Failed to evaluate condition", e);
            return false;
        }
    }

    boolean doEvaluateCondition(String condition, ImmutableDeltaFile deltaFile) {
        EvaluationContext spelContext = SimpleEvaluationContext.forReadOnlyDataBinding()
                .withRootObject(deltaFile)
                .withInstanceMethods()
                .build();

        Expression expression = expressionCache.computeIfAbsent(condition, this::newExpression);
        Boolean output = expression.getValue(spelContext, Boolean.class);

        return Boolean.TRUE.equals(output);
    }

    private Expression newExpression(String condition) {
        SpelExpressionParser parser = new SpelExpressionParser();
        return parser.parseExpression(condition);
    }

}
