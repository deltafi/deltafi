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
package org.deltafi.core.action.jolt;

import org.assertj.core.api.Assertions;
import org.deltafi.actionkit.action.ResultType;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.deltafi.test.asserters.ActionResultAssertions.assertErrorResult;
import static org.deltafi.test.asserters.ActionResultAssertions.assertTransformResult;

class JoltTransformTest {

    JoltTransform action = new JoltTransform();
    DeltaFiTestRunner runner = DeltaFiTestRunner.setup();

    @Test
    void testJoltTransformByIndex() {
        JoltParameters params = new JoltParameters();
        params.setJoltSpec("[{\"operation\": \"shift\", \"spec\": {\"original\": \"new\"}}]");
        params.setContentIndexes(List.of(0)); // Transform only the first content

        TransformInput input = createInput();
        ResultType result = action.transform(runner.actionContext(), params, input);

        assertTransformResult(result)
                .hasContentCount(1)
                .hasContentMatchingAt(0, this::checkContent);
    }

    @Test
    void testJoltTransformByFilePattern() {
        JoltParameters params = new JoltParameters();
        params.setJoltSpec("[{\"operation\": \"shift\", \"spec\": {\"original\": \"new\"}}]");
        params.setFilePatterns(List.of("example.json")); // Transform content with file name "example.json"

        TransformInput input = createInput();
        ResultType result = action.transform(runner.actionContext(), params, input);

        assertTransformResult(result)
                .hasContentCount(1)
                .hasContentMatchingAt(0, this::checkContent);
    }

    @Test
    void testJoltTransformByMediaType() {
        JoltParameters params = new JoltParameters();
        params.setJoltSpec("[{\"operation\": \"shift\", \"spec\": {\"original\": \"new\"}}]");
        params.setMediaTypes(List.of("application/json")); // Transform content with media type "application/json"

        TransformInput input = createInput();
        ResultType result = action.transform(runner.actionContext(), params, input);

        assertTransformResult(result)
                .hasContentCount(1)
                .hasContentMatchingAt(0, this::checkContent);
    }

    @Test
    void testJoltTransformNoMatch() {
        JoltParameters params = new JoltParameters();
        params.setJoltSpec("[{\"operation\": \"shift\", \"spec\": {\"original\": \"new\"}}]");
        params.setContentIndexes(List.of(1)); // No match, index out of range
        params.setFilePatterns(List.of("nonmatching.json")); // No match, different file name
        params.setMediaTypes(List.of("text/plain")); // No match, different media type

        TransformInput input = createInput();
        ResultType result = action.transform(runner.actionContext(), params, input);

        assertTransformResult(result)
                .hasContentCount(1)
                .hasContentMatchingAt(0, content -> {
                    // Content should remain unchanged
                    Assertions.assertThat(content.getName()).isEqualTo("example.json");
                    String loadedContent = content.loadString();
                    Assertions.assertThat(loadedContent).contains("\"original\":");
                    Assertions.assertThat(loadedContent).doesNotContain("\"new\":");
                    return true;
                });
    }

    @Test
    void testJoltTransformWithInvalidSpec() {
        JoltParameters params = new JoltParameters();
        params.setJoltSpec("INVALID_SPEC");
        params.setMediaTypes(List.of("application/json"));
        params.setFilePatterns(List.of("example.json"));

        TransformInput input = createInput();
        assertErrorResult(action.transform(runner.actionContext(), params, input))
                .hasCause("Error parsing Jolt specification");
    }

    @Test
    void testJoltTransformWithErrorInTransformation() {
        JoltParameters params = new JoltParameters();
        params.setJoltSpec("[{\"operation\": \"shift\", \"spec\": {\"original\": \"new\"}}]");
        params.setMediaTypes(List.of("application/json"));
        params.setFilePatterns(List.of("example.json"));

        TransformInput input = createInputWithErrorInContent();
        assertErrorResult(action.transform(runner.actionContext(), params, input))
                .hasCause("Error transforming content at index 0");
    }

    private boolean checkContent(ActionContent content) {
        Assertions.assertThat(content.getName()).isEqualTo("example.json");
        String loadedContent = content.loadString();
        Assertions.assertThat(loadedContent).contains("\"new\":");
        Assertions.assertThat(loadedContent).doesNotContain("\"original\":");

        return true;
    }

    private TransformInput createInput() {
        List<ActionContent> content = List.of(ActionContent.saveContent(runner.actionContext(), "{\"original\": \"value\"}", "example.json", "application/json"));
        return TransformInput.builder().content(content).build();
    }

    private TransformInput createInputWithErrorInContent() {
        List<ActionContent> content = List.of(ActionContent.saveContent(runner.actionContext(), "{\"original\": value\"}", "example.json", "application/json"));
        return TransformInput.builder().content(content).build();
    }
}
