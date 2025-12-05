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
package org.deltafi.core.action.slice;

// ABOUTME: Tests for the Slice transform action.
// ABOUTME: Verifies byte range extraction with various offset, size, and content selection scenarios.

import org.deltafi.actionkit.action.ResultType;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.test.asserters.ErrorResultAssert;
import org.deltafi.test.asserters.TransformResultAssert;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.junit.jupiter.api.Test;

import java.util.List;

class SliceTest {

    Slice action = new Slice();
    DeltaFiTestRunner runner = DeltaFiTestRunner.setup();

    @Test
    void sliceWithOffsetAndSize() {
        SliceParameters params = new SliceParameters();
        params.setOffset(2);
        params.setSize(3L);

        ResultType result = transform(params, "ABCDEFG");

        TransformResultAssert.assertThat(result)
                .hasContentMatchingAt(0, "test.txt", "text/plain", "CDE");
    }

    @Test
    void sliceWithOffsetOnly() {
        SliceParameters params = new SliceParameters();
        params.setOffset(4);

        ResultType result = transform(params, "ABCDEFG");

        TransformResultAssert.assertThat(result)
                .hasContentMatchingAt(0, "test.txt", "text/plain", "EFG");
    }

    @Test
    void sliceFromBeginning() {
        SliceParameters params = new SliceParameters();
        params.setOffset(0);
        params.setSize(3L);

        ResultType result = transform(params, "ABCDEFG");

        TransformResultAssert.assertThat(result)
                .hasContentMatchingAt(0, "test.txt", "text/plain", "ABC");
    }

    @Test
    void sliceSizeExceedsContent() {
        SliceParameters params = new SliceParameters();
        params.setOffset(5);
        params.setSize(100L);

        ResultType result = transform(params, "ABCDEFG");

        TransformResultAssert.assertThat(result)
                .hasContentMatchingAt(0, "test.txt", "text/plain", "FG");
    }

    @Test
    void sliceOffsetBeyondContentSizeErrors() {
        SliceParameters params = new SliceParameters();
        params.setOffset(100);

        ResultType result = transform(params, "ABCDEFG");

        ErrorResultAssert.assertThat(result)
                .hasCause("Error transforming content at index 0")
                .hasContextContaining("Offset 100 is beyond content size 7");
    }

    @Test
    void sliceOffsetBeyondContentSizeAllowsEmpty() {
        SliceParameters params = new SliceParameters();
        params.setOffset(100);
        params.setAllowEmptyResult(true);

        ResultType result = transform(params, "ABCDEFG");

        TransformResultAssert.assertThat(result)
                .hasContentMatchingAt(0, "test.txt", "text/plain", "");
    }

    @Test
    void sliceWithFilePatternMatch() {
        SliceParameters params = new SliceParameters();
        params.setOffset(0);
        params.setSize(3L);
        params.setFilePatterns(List.of("*.txt"));

        ResultType result = transform(params, "ABCDEFG");

        TransformResultAssert.assertThat(result)
                .hasContentMatchingAt(0, "test.txt", "text/plain", "ABC");
    }

    @Test
    void sliceWithFilePatternNoMatch() {
        SliceParameters params = new SliceParameters();
        params.setOffset(0);
        params.setSize(3L);
        params.setFilePatterns(List.of("*.json"));

        ResultType result = transform(params, "ABCDEFG");

        TransformResultAssert.assertThat(result)
                .hasContentMatchingAt(0, "test.txt", "text/plain", "ABCDEFG");
    }

    @Test
    void sliceMultipleContentsSelectByIndex() {
        SliceParameters params = new SliceParameters();
        params.setOffset(0);
        params.setSize(2L);
        params.setContentIndexes(List.of(1));

        ActionContent content1 = runner.saveContent("AAA", "first.txt", "text/plain");
        ActionContent content2 = runner.saveContent("BBB", "second.txt", "text/plain");
        ActionContent content3 = runner.saveContent("CCC", "third.txt", "text/plain");
        TransformInput input = TransformInput.builder().content(List.of(content1, content2, content3)).build();

        ResultType result = action.transform(runner.actionContext(), params, input);

        TransformResultAssert.assertThat(result)
                .hasContentCount(3)
                .hasContentMatchingAt(0, "first.txt", "text/plain", "AAA")
                .hasContentMatchingAt(1, "second.txt", "text/plain", "BB")
                .hasContentMatchingAt(2, "third.txt", "text/plain", "CCC");
    }

    @Test
    void sliceWithRetainExistingContent() {
        SliceParameters params = new SliceParameters();
        params.setOffset(0);
        params.setSize(3L);
        params.setRetainExistingContent(true);

        ResultType result = transform(params, "ABCDEFG");

        TransformResultAssert.assertThat(result)
                .hasContentCount(2)
                .hasContentMatchingAt(0, "test.txt", "text/plain", "ABCDEFG")
                .hasContentMatchingAt(1, "test.txt", "text/plain", "ABC");
    }

    @Test
    void sliceByMediaType() {
        SliceParameters params = new SliceParameters();
        params.setOffset(0);
        params.setSize(2L);
        params.setMediaTypes(List.of("text/plain"));

        ActionContent textContent = runner.saveContent("ABCDEFG", "test.txt", "text/plain");
        ActionContent jsonContent = runner.saveContent("1234567", "test.json", "application/json");
        TransformInput input = TransformInput.builder().content(List.of(textContent, jsonContent)).build();

        ResultType result = action.transform(runner.actionContext(), params, input);

        TransformResultAssert.assertThat(result)
                .hasContentCount(2)
                .hasContentMatchingAt(0, "test.txt", "text/plain", "AB")
                .hasContentMatchingAt(1, "test.json", "application/json", "1234567");
    }

    @Test
    void sliceNegativeOffset() {
        SliceParameters params = new SliceParameters();
        params.setOffset(-3);

        ResultType result = transform(params, "ABCDEFG");

        TransformResultAssert.assertThat(result)
                .hasContentMatchingAt(0, "test.txt", "text/plain", "EFG");
    }

    @Test
    void sliceNegativeOffsetWithSize() {
        SliceParameters params = new SliceParameters();
        params.setOffset(-5);
        params.setSize(2L);

        ResultType result = transform(params, "ABCDEFG");

        TransformResultAssert.assertThat(result)
                .hasContentMatchingAt(0, "test.txt", "text/plain", "CD");
    }

    @Test
    void sliceNegativeOffsetBeyondContentSizeErrors() {
        SliceParameters params = new SliceParameters();
        params.setOffset(-100);

        ResultType result = transform(params, "ABCDEFG");

        ErrorResultAssert.assertThat(result)
                .hasCause("Error transforming content at index 0")
                .hasContextContaining("Negative offset -100 is beyond content size 7");
    }

    @Test
    void sliceNegativeOffsetBeyondContentSizeAllowsClamp() {
        SliceParameters params = new SliceParameters();
        params.setOffset(-100);
        params.setSize(3L);
        params.setAllowEmptyResult(true);

        ResultType result = transform(params, "ABCDEFG");

        TransformResultAssert.assertThat(result)
                .hasContentMatchingAt(0, "test.txt", "text/plain", "ABC");
    }

    @Test
    void sliceNegativeSizeErrors() {
        SliceParameters params = new SliceParameters();
        params.setOffset(0);
        params.setSize(-5L);

        ResultType result = transform(params, "ABCDEFG");

        ErrorResultAssert.assertThat(result)
                .hasCause("Error transforming content at index 0")
                .hasContextContaining("Size cannot be negative: -5");
    }

    private ResultType transform(SliceParameters params, String content) {
        ActionContent actionContent = runner.saveContent(content, "test.txt", "text/plain");
        TransformInput input = TransformInput.builder().content(List.of(actionContent)).build();
        return action.transform(runner.actionContext(), params, input);
    }
}
