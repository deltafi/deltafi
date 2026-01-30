/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
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

import lombok.SneakyThrows;
import org.assertj.core.api.Assertions;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.test.content.InMemoryContentStorageService;
import org.deltafi.common.types.Content;
import org.junit.jupiter.api.Test;
import org.springframework.expression.spel.SpelEvaluationException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class RuleEvaluatorTest {

    ContentStorageService contentStorageService = new InMemoryContentStorageService();
    RuleEvaluator ruleEvaluator = new RuleEvaluator();

    @Test
    void testEvaluation_metadataChecks() {
        Assertions.assertThat(ruleEvaluator.evaluateCondition("metadata.containsKey('a')", testMetadata(), testContent())).isTrue();
        Assertions.assertThat(ruleEvaluator.evaluateCondition("metadata['a'] == 'b'", testMetadata(), testContent())).isTrue();
        Assertions.assertThat(ruleEvaluator.evaluateCondition("metadata.containsKey('b')", testMetadata(), testContent())).isFalse();
        Assertions.assertThat(ruleEvaluator.evaluateCondition("metadata['a'] == 'c'", testMetadata(), testContent())).isFalse();
    }

    @Test
    void testEvaluation_numericComparison() {
        Map<String, String> metadata = Map.of("count", "5", "price", "19.99");
        List<Content> content = testContent();

        // Integer equality - metadata string "5" equals numeric literal 5
        Assertions.assertThat(ruleEvaluator.evaluateCondition("metadata['count'] == 5", metadata, content)).isTrue();
        Assertions.assertThat(ruleEvaluator.evaluateCondition("metadata['count'] != 5", metadata, content)).isFalse();
        Assertions.assertThat(ruleEvaluator.evaluateCondition("metadata['count'] == 10", metadata, content)).isFalse();
        Assertions.assertThat(ruleEvaluator.evaluateCondition("metadata['count'] != 10", metadata, content)).isTrue();

        // Float equality
        Assertions.assertThat(ruleEvaluator.evaluateCondition("metadata['price'] == 19.99", metadata, content)).isTrue();
        Assertions.assertThat(ruleEvaluator.evaluateCondition("metadata['price'] != 19.99", metadata, content)).isFalse();
    }

    @Test
    void testEvaluation_numericComparisonMethods() {
        Map<String, String> metadata = Map.of("count", "10", "price", "19.99");
        List<Content> content = testContent();

        // Greater than
        Assertions.assertThat(ruleEvaluator.evaluateCondition("metadata['count'].gt(5)", metadata, content)).isTrue();
        Assertions.assertThat(ruleEvaluator.evaluateCondition("metadata['count'].gt(10)", metadata, content)).isFalse();
        Assertions.assertThat(ruleEvaluator.evaluateCondition("metadata['count'].gt(15)", metadata, content)).isFalse();

        // Greater than or equal
        Assertions.assertThat(ruleEvaluator.evaluateCondition("metadata['count'].gte(10)", metadata, content)).isTrue();
        Assertions.assertThat(ruleEvaluator.evaluateCondition("metadata['count'].gte(5)", metadata, content)).isTrue();
        Assertions.assertThat(ruleEvaluator.evaluateCondition("metadata['count'].gte(15)", metadata, content)).isFalse();

        // Less than
        Assertions.assertThat(ruleEvaluator.evaluateCondition("metadata['count'].lt(15)", metadata, content)).isTrue();
        Assertions.assertThat(ruleEvaluator.evaluateCondition("metadata['count'].lt(10)", metadata, content)).isFalse();
        Assertions.assertThat(ruleEvaluator.evaluateCondition("metadata['count'].lt(5)", metadata, content)).isFalse();

        // Less than or equal
        Assertions.assertThat(ruleEvaluator.evaluateCondition("metadata['count'].lte(10)", metadata, content)).isTrue();
        Assertions.assertThat(ruleEvaluator.evaluateCondition("metadata['count'].lte(15)", metadata, content)).isTrue();
        Assertions.assertThat(ruleEvaluator.evaluateCondition("metadata['count'].lte(5)", metadata, content)).isFalse();

        // Float comparisons
        Assertions.assertThat(ruleEvaluator.evaluateCondition("metadata['price'].gt(10.0)", metadata, content)).isTrue();
        Assertions.assertThat(ruleEvaluator.evaluateCondition("metadata['price'].lt(20.0)", metadata, content)).isTrue();
        Assertions.assertThat(ruleEvaluator.evaluateCondition("metadata['price'].gte(19.99)", metadata, content)).isTrue();
        Assertions.assertThat(ruleEvaluator.evaluateCondition("metadata['price'].lte(19.99)", metadata, content)).isTrue();
    }

    @Test
    void testEvaluation_contentChecks() {
        Assertions.assertThat(ruleEvaluator.evaluateCondition("hasMediaType('plain/text')", testMetadata(), testContent())).isTrue();
        Assertions.assertThat(ruleEvaluator.evaluateCondition("hasMediaType('application/json')", testMetadata(), testContent())).isFalse();
        Assertions.assertThat(ruleEvaluator.evaluateCondition("content.?[getSize() > 2].isEmpty()", testMetadata(), testContent())).isFalse();
        Assertions.assertThat(ruleEvaluator.evaluateCondition("content.?[getSize() > 10].isEmpty()", testMetadata(), testContent())).isTrue();
        Assertions.assertThat(ruleEvaluator.evaluateCondition("content.?[getName() == 'test.txt'].isEmpty()", testMetadata(), testContent())).isFalse();
        Assertions.assertThat(ruleEvaluator.evaluateCondition("content.?[getName() == 'test2.txt'].isEmpty()", testMetadata(), testContent())).isTrue();
    }

    @Test
    void testImmutableDeltaFile() {
        Map<String, String> originalMetadata = testMetadata();
        List<Content> originalContent = testContent();
        RuleEvaluator.ImmutableDeltaFileFlow deltaFile = RuleEvaluator.ImmutableDeltaFileFlow.from(originalMetadata, originalContent);
        // cannot rewrite value for key 'a'
        Assertions.assertThatThrownBy(() -> ruleEvaluator.doEvaluateCondition("metadata['a'] = 'c'", deltaFile))
                .isInstanceOf(SpelEvaluationException.class);
        // cannot add a new metadata key/value
        Assertions.assertThatThrownBy(() -> ruleEvaluator.doEvaluateCondition("metadata['b'] = 'c'", deltaFile))
                .isInstanceOf(SpelEvaluationException.class);

        ruleEvaluator.doEvaluateCondition("content[0].setName('c')", deltaFile);
        Assertions.assertThat(originalContent.getFirst().getName()).isEqualTo("test.txt");
        Assertions.assertThat(deltaFile.content().getFirst().getName()).isEqualTo("c");
    }

    private Map<String, String> testMetadata() {
        return Map.of("a", "b");
    }

    @SneakyThrows
    private List<Content> testContent() {
        Content content = contentStorageService.save(UUID.randomUUID(), "test input".getBytes(StandardCharsets.UTF_8), "test.txt", "plain/text");
        return List.of(content);
    }
}