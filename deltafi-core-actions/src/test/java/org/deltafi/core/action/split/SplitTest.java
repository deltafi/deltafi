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
package org.deltafi.core.action.split;

import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.actionkit.action.transform.TransformResultType;
import org.deltafi.actionkit.action.transform.TransformResults;
import org.deltafi.test.asserters.TransformResultsAssert;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SplitTest {
    @Test
    public void splits() {
        Split split = new Split();
        DeltaFiTestRunner runner = DeltaFiTestRunner.setup("SplitTest");

        Map<String, String> metadata = Map.of("key1", "value1", "key2", "value2");
        List<ActionContent> actionContent = runner.saveContentFromResource("content1", "content2", "content3");
        actionContent.forEach(a -> a.addTag(a.getName() + "-tag"));

        TransformResultType transformResultType = split.transform(runner.actionContext(),
                new ActionParameters(), TransformInput.builder()
                        .content(actionContent)
                        .metadata(metadata).build());

        TransformResultsAssert.assertThat(transformResultType)
                .hasChildrenSize(3, "Split has 3 children")
                .hasChildResultAt(0, "content1", null, "first", metadata, "Child at index 0 matches")
                .hasChildResultAt(1, "content2", null, "second", metadata, "Child at index 1 matches")
                .hasChildResultAt(2, "content3", null, "third", metadata, "Child at index 2 matches");

        for (int i = 0; i < 3; i++) {
            assertEquals(Set.of("content" + (i + 1) + "-tag"), ((TransformResults) transformResultType).getChildResults().get(i).getContent().getFirst().getTags());
        }
    }
}
