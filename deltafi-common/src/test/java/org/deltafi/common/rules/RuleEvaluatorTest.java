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
        DeltaFileFlow flow = testDeltaFileFlow();
        Assertions.assertThat(ruleEvaluator.evaluateCondition("metadata.containsKey('a')", flow)).isTrue();
        Assertions.assertThat(ruleEvaluator.evaluateCondition("metadata['a'] == 'b'", flow)).isTrue();
        Assertions.assertThat(ruleEvaluator.evaluateCondition("metadata.containsKey('b')", flow)).isFalse();
        Assertions.assertThat(ruleEvaluator.evaluateCondition("metadata['a'] == 'c'", flow)).isFalse();
    }

    @Test
    void testEvaluation_contentChecks() {
        DeltaFileFlow flow = testDeltaFileFlow();
        Assertions.assertThat(ruleEvaluator.evaluateCondition("hasMediaType('plain/text')", flow)).isTrue();
        Assertions.assertThat(ruleEvaluator.evaluateCondition("hasMediaType('application/json')", flow)).isFalse();
        Assertions.assertThat(ruleEvaluator.evaluateCondition("content.?[getSize() > 2].isEmpty()", flow)).isFalse();
        Assertions.assertThat(ruleEvaluator.evaluateCondition("content.?[getSize() > 10].isEmpty()", flow)).isTrue();
        Assertions.assertThat(ruleEvaluator.evaluateCondition("content.?[getName() == 'test.txt'].isEmpty()", flow)).isFalse();
        Assertions.assertThat(ruleEvaluator.evaluateCondition("content.?[getName() == 'test2.txt'].isEmpty()", flow)).isTrue();
    }

    @Test
    void testImmutableDeltaFile() {
        DeltaFileFlow originalDeltaFileFlow = testDeltaFileFlow();
        RuleEvaluator.ImmutableDeltaFileFlow deltaFile = new RuleEvaluator.ImmutableDeltaFileFlow(originalDeltaFileFlow);
        // cannot rewrite value for key 'a'
        Assertions.assertThatThrownBy(() -> ruleEvaluator.doEvaluateCondition("metadata['a'] = 'c'", deltaFile))
                .isInstanceOf(UnsupportedOperationException.class);
        // cannot add a new metadata key/value
        Assertions.assertThatThrownBy(() -> ruleEvaluator.doEvaluateCondition("metadata['b'] = 'c'", deltaFile))
                .isInstanceOf(UnsupportedOperationException.class);

        //
        ruleEvaluator.doEvaluateCondition("content[0].setName('c')", deltaFile);
        Assertions.assertThat(originalDeltaFileFlow.lastContent().getFirst().getName()).isEqualTo("test.txt");
        Assertions.assertThat(deltaFile.content().getFirst().getName()).isEqualTo("c");
    }

    @SneakyThrows
    private DeltaFileFlow testDeltaFileFlow() {
        Content content = contentStorageService.save(UUID.randomUUID(), "test input".getBytes(StandardCharsets.UTF_8), "test.txt", "plain/text");
        Action action = new Action();
        action.setContent(List.of(content));
        action.setMetadata(Map.of("a", "b"));
        action.setType(ActionType.TRANSFORM);
        action.setState(ActionState.COMPLETE);
        DeltaFileFlow flow = new DeltaFileFlow();
        flow.getActions().add(action);
        return flow;
    }
}