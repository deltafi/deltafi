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

import org.assertj.core.api.Assertions;
import org.deltafi.actionkit.action.ResultType;
import org.deltafi.actionkit.action.load.LoadInput;
import org.deltafi.actionkit.action.parameters.ReinjectParameters;
import org.deltafi.common.types.ReinjectEvent;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.junit.jupiter.api.Test;

import static org.deltafi.test.asserters.ActionResultAssertions.assertReinjectResult;

public class SplitterLoadActionTest {

    SplitterLoadAction action = new SplitterLoadAction();
    DeltaFiTestRunner runner = DeltaFiTestRunner.setup(action, "SplitterLoadActionTest");

    @Test
    void testSplitOneResult() {
        LoadInput loadInput = LoadInput.builder().content(runner.saveContentFromResource("content1")).build();
        ReinjectParameters reinjectParameters =  new ReinjectParameters("testFlow");

        ResultType result = action.load(runner.actionContext(), reinjectParameters, loadInput);

        assertReinjectResult(result)
                .hasReinjectEventSize(1)
                .hasReinjectEventMatching(reinjectEvent -> reinjectMatcher(reinjectEvent, "content1", "first"));
    }

    @Test
    void testSplitThree() {
        LoadInput loadInput = LoadInput.builder().content(runner.saveContentFromResource("content1", "content2", "content3")).build();
        ReinjectParameters reinjectParameters =  new ReinjectParameters("testFlow");

        ResultType result = action.load(runner.actionContext(), reinjectParameters, loadInput);

        assertReinjectResult(result)
                .hasReinjectEventSize(3)
                .hasReinjectEventMatchingAt(0, reinjectEvent -> reinjectMatcher(reinjectEvent, "content1", "first"))
                .hasReinjectEventMatchingAt(1, reinjectEvent -> reinjectMatcher(reinjectEvent, "content2", "second"))
                .hasReinjectEventMatchingAt(2, reinjectEvent -> reinjectMatcher(reinjectEvent, "content3", "third"));
    }

    boolean reinjectMatcher(ReinjectEvent event, String filename, String expectedContent) {
        Assertions.assertThat(event.getFilename()).isEqualTo(filename);
        Assertions.assertThat(event.getFlow()).isEqualTo("testFlow");
        Assertions.assertThat(event.getAnnotations()).isEmpty();
        Assertions.assertThat(event.getMetadata()).isEmpty();
        Assertions.assertThat(event.getDeleteMetadataKeys()).isEmpty();
        Assertions.assertThat(event.getContent()).hasSize(1);
        Assertions.assertThat(runner.readContent(event.getContent().get(0))).isEqualTo(expectedContent);
        return true;
    }
}
