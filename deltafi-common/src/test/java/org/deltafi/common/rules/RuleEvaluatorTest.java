/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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
import org.deltafi.common.test.storage.s3.InMemoryObjectStorageService;
import org.deltafi.common.types.*;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class RuleEvaluatorTest {

    ContentStorageService contentStorageService = new ContentStorageService(new InMemoryObjectStorageService());
    RuleEvaluator ruleEvaluator = new RuleEvaluator();

    @Test
    void testEvaluation_metadataChecks() {
        Assertions.assertThat(ruleEvaluator.evaluateCondition("metadata.containsKey('a')", testMetadata(), testContent())).isTrue();
        Assertions.assertThat(ruleEvaluator.evaluateCondition("metadata['a'] == 'b'", testMetadata(), testContent())).isTrue();
        Assertions.assertThat(ruleEvaluator.evaluateCondition("metadata.containsKey('b')", testMetadata(), testContent())).isFalse();
        Assertions.assertThat(ruleEvaluator.evaluateCondition("metadata['a'] == 'c'", testMetadata(), testContent())).isFalse();
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
        RuleEvaluator.ImmutableDeltaFileFlow deltaFile = new RuleEvaluator.ImmutableDeltaFileFlow(originalMetadata, originalContent);
        // cannot rewrite value for key 'a'
        Assertions.assertThatThrownBy(() -> ruleEvaluator.doEvaluateCondition("metadata['a'] = 'c'", deltaFile))
                .isInstanceOf(UnsupportedOperationException.class);
        // cannot add a new metadata key/value
        Assertions.assertThatThrownBy(() -> ruleEvaluator.doEvaluateCondition("metadata['b'] = 'c'", deltaFile))
                .isInstanceOf(UnsupportedOperationException.class);

        //
        ruleEvaluator.doEvaluateCondition("content[0].setName('c')", deltaFile);
        Assertions.assertThat(originalContent.getFirst().getName()).isEqualTo("test.txt");
        Assertions.assertThat(deltaFile.content.getFirst().getName()).isEqualTo("c");
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