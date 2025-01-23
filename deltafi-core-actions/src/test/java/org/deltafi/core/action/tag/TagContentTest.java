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
package org.deltafi.core.action.tag;

import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.actionkit.action.transform.TransformResultType;
import org.deltafi.common.types.ActionContext;
import org.deltafi.test.asserters.TransformResultAssert;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TagContentTest {

    TagContent action = new TagContent();
    DeltaFiTestRunner runner = DeltaFiTestRunner.setup("TagContentTest");
    ActionContext context = runner.actionContext();

    @Test
    void addsTagsToAllContent() {
        TagContentParameters params = new TagContentParameters();
        params.setTagsToAdd(List.of("Processed", "Verified"));

        TransformInput input = TransformInput.builder()
                .content(List.of(
                        runner.saveEmptyContent("file1.txt", "text/plain"),
                        runner.saveEmptyContent("file2.json", "application/json")))
                .build();

        TransformResultType result = action.transform(context, params, input);

        TransformResultAssert.assertThat(result);
        TransformResult transformResult = (TransformResult) result;

        // Verify all content is passed through
        assertEquals(input.content().size(), transformResult.getContent().size());

        // Verify tags are added
        transformResult.getContent().forEach(content -> {
            assertTrue(content.getTags().contains("Processed"));
            assertTrue(content.getTags().contains("Verified"));
        });
    }

    @Test
    void filtersContentByMediaTypeAndPassesAllThrough() {
        TagContentParameters params = new TagContentParameters();
        params.setMediaTypes(List.of("application/json"));
        params.setTagsToAdd(List.of("Tagged"));

        TransformInput input = TransformInput.builder()
                .content(List.of(
                        runner.saveEmptyContent("file1.txt", "text/plain"),
                        runner.saveEmptyContent("file2.json", "application/json")))
                .build();

        TransformResultType result = action.transform(context, params, input);

        TransformResultAssert.assertThat(result);
        TransformResult transformResult = (TransformResult) result;

        // Verify all content is passed through
        assertEquals(input.content().size(), transformResult.getContent().size());

        // Verify only matching content is tagged
        assertFalse(transformResult.getContent().get(0).getTags().contains("Tagged"));
        assertTrue(transformResult.getContent().get(1).getTags().contains("Tagged"));
    }

    @Test
    void filtersContentByFilenamePatternAndPassesAllThrough() {
        TagContentParameters params = new TagContentParameters();
        params.setFilePatterns(List.of("*.json"));
        params.setTagsToAdd(List.of("PatternMatched"));

        TransformInput input = TransformInput.builder()
                .content(List.of(
                        runner.saveEmptyContent("file1.txt", "text/plain"),
                        runner.saveEmptyContent("file2.json", "application/json")))
                .build();

        TransformResultType result = action.transform(context, params, input);

        TransformResultAssert.assertThat(result);
        TransformResult transformResult = (TransformResult) result;

        // Verify all content is passed through
        assertEquals(input.content().size(), transformResult.getContent().size());

        // Verify only matching content is tagged
        assertFalse(transformResult.getContent().get(0).getTags().contains("PatternMatched"));
        assertTrue(transformResult.getContent().get(1).getTags().contains("PatternMatched"));
    }

    @Test
    void addsTagsToSpecifiedIndicesAndPassesAllThrough() {
        TagContentParameters params = new TagContentParameters();
        params.setContentIndexes(List.of(1));
        params.setTagsToAdd(List.of("IndexedTag"));

        TransformInput input = TransformInput.builder()
                .content(List.of(
                        runner.saveEmptyContent("file1.txt", "text/plain"),
                        runner.saveEmptyContent("file2.json", "application/json")))
                .build();

        TransformResultType result = action.transform(context, params, input);

        TransformResultAssert.assertThat(result);
        TransformResult transformResult = (TransformResult) result;

        // Verify all content is passed through
        assertEquals(input.content().size(), transformResult.getContent().size());

        // Verify only content at specified indices is tagged
        assertFalse(transformResult.getContent().get(0).getTags().contains("IndexedTag"));
        assertTrue(transformResult.getContent().get(1).getTags().contains("IndexedTag"));
    }
}
