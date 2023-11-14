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
package org.deltafi.core.services;

import org.assertj.core.api.Assertions;
import org.deltafi.common.types.Action;
import org.deltafi.common.types.ActionState;
import org.deltafi.common.types.ActionType;
import org.deltafi.common.types.Content;
import org.deltafi.common.types.DeltaFile;
import org.deltafi.core.services.pubsub.RuleEvaluator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class RuleEvaluatorTest {

    RuleEvaluator ruleEvaluator = new RuleEvaluator();

    @Test
    void testEvaluation() {
        DeltaFile deltaFile = new DeltaFile();
        deltaFile.setActions(new ArrayList<>());
        Action action = new Action();
        action.setContent(List.of(new Content()));
        action.setMetadata(Map.of("a", "b"));
        action.setType(ActionType.TRANSFORM);
        action.setState(ActionState.COMPLETE);
        deltaFile.getActions().add(action);

//        ruleEvaluator.evaluateCondition(content);

//        ruleEvaluator.evaluateCondition("metadata['b'] = 'c'", deltaFile);
        Assertions.assertThat(ruleEvaluator.evaluateCondition("metadata.containsKey('a')", deltaFile))
                .isTrue();
        Assertions.assertThat(ruleEvaluator.evaluateCondition("metadata['a'] == 'b'", deltaFile))
                .isTrue();

        Assertions.assertThat(ruleEvaluator.evaluateCondition("metadata.containsKey('b')", deltaFile))
                .isFalse();
        Assertions.assertThat(ruleEvaluator.evaluateCondition("metadata['a'] == 'c'", deltaFile))
                .isFalse();


    }
}