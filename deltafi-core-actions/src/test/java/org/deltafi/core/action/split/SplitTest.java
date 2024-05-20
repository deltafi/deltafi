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
package org.deltafi.core.action.split;

import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.actionkit.action.transform.TransformResultType;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.deltafi.test.asserters.ActionResultAssertions.assertTransformResults;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class SplitTest {
    @Test
    public void splits() {
        Split split = new Split();
        DeltaFiTestRunner runner = DeltaFiTestRunner.setup(split, "SplitTest");

        Map<String, String> metadata = Map.of("key1", "value1", "key2", "value2");

        TransformResultType transformResultType = split.transform(runner.actionContext(),
                new ActionParameters(), TransformInput.builder()
                        .content(runner.saveContentFromResource("content1", "content2", "content3"))
                        .metadata(metadata).build());

        assertTransformResults(transformResultType)
                .hasChildrenSize(3)
                .hasChildResultAt(0, transformResult -> {
                    assertEquals("content1", transformResult.getContent().getFirst().getName());
                    assertNull(transformResult.getContent().getFirst().getMediaType());
                    assertEquals("first", transformResult.getContent().getFirst().loadString());
                    assertEquals(metadata, transformResult.getMetadata());
                    return true;
                })
                .hasChildResultAt(1, transformResult -> {
                    assertEquals("content2", transformResult.getContent().getFirst().getName());
                    assertNull(transformResult.getContent().getFirst().getMediaType());
                    assertEquals("second", transformResult.getContent().getFirst().loadString());
                    assertEquals(metadata, transformResult.getMetadata());
                    return true;
                })
                .hasChildResultAt(2, transformResult -> {
                    assertEquals("content3", transformResult.getContent().getFirst().getName());
                    assertNull(transformResult.getContent().getFirst().getMediaType());
                    assertEquals("third", transformResult.getContent().getFirst().loadString());
                    assertEquals(metadata, transformResult.getMetadata());
                    return true;
                });
    }
}
