/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.common.rules;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.types.Content;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class RuleEvaluator {
    private static final ImmutableDeltaFileFlow EXAMPLE_DELTA_FILE = new ImmutableDeltaFileFlow(Map.of("a", "b"), List.of(new Content("", "")));

    private final Map<String, Expression> expressionCache = new ConcurrentHashMap<>();

    /**
     * Validate the condition loads and can be evaluated against a sample DeltaFile
     * @param condition to validate
     * @throws IllegalArgumentException if the condition results in an exception
     */
    public void validateCondition(String condition) throws IllegalArgumentException {
        try {
            doEvaluateCondition(condition, EXAMPLE_DELTA_FILE);
        } catch (Exception e) {
            String exceptionMessage = e.getMessage();
            String message = exceptionMessage != null ? "invalid condition `" + condition + "`: " + exceptionMessage :
                    "invalid condition `" + condition + "`";
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Evaluate the condition against the given DeltaFile. A null condition
     * will result in true. An invalid condition results in false.
     * @param condition SPeL expression to evaluate
     * @param metadata used in the evaluation
     * @param content used in the evaluation
     * @return the result of evaluating the condition
     */
    public boolean evaluateCondition(String condition, Map<String, String> metadata, List<Content> content) {
        try {
            return doEvaluateCondition(condition, new ImmutableDeltaFileFlow(metadata, content));
        } catch (Exception e) {
            log.error("Failed to evaluate condition", e);
            return false;
        }
    }

    boolean doEvaluateCondition(String condition, ImmutableDeltaFileFlow deltaFileFlow) {
        if (condition == null) {
            return true;
        }

        EvaluationContext spelContext = SimpleEvaluationContext.forReadOnlyDataBinding()
                .withRootObject(deltaFileFlow)
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

    /**
     * Holds a copy of the DeltaFileFlow metadata and content list to prevent
     * SpEL expressions from modifying the original DeltaFile
     */
    public record ImmutableDeltaFileFlow(Map<String, String> metadata, List<Content> content) {
        public ImmutableDeltaFileFlow(Map<String, String> metadata, List<Content> content) {
            this.metadata = Collections.unmodifiableMap(metadata);
            this.content = content.stream().map(Content::copy).toList();
        }

        /**
         * Helper that can be used to check if DeltaFile contains content with the given mediaType
         *
         * @param mediaType to find in the list of content
         * @return true if there is a content with the given mediaType
         */
        public boolean hasMediaType(String mediaType) {
            return this.content.stream().anyMatch(content -> Objects.equals(mediaType, content.getMediaType()));
        }
    }

}
