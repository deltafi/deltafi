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
package org.deltafi.core.action.replace;

import org.assertj.core.api.Assertions;
import org.deltafi.actionkit.action.ResultType;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.test.asserters.ErrorResultAssert;
import org.deltafi.test.asserters.TransformResultAssert;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.junit.jupiter.api.Test;

import java.util.List;

class ReplaceTextTest {

    ReplaceText action = new ReplaceText();
    DeltaFiTestRunner runner = DeltaFiTestRunner.setup();

    @Test
    void testReplaceLiteralAll() {
        ReplaceTextParameters params = new ReplaceTextParameters();
        params.setSearchValue("foo");
        params.setReplacement("bar");

        TransformInput input = createInput("foo foo foo", "test.txt", "text/plain");
        ResultType result = action.transform(runner.actionContext(), params, input);

        TransformResultAssert.assertThat(result)
                .hasContentCount(1)
                .hasContentMatchingAt(0, content -> {
                    Assertions.assertThat(content.loadString()).isEqualTo("bar bar bar");
                    return true;
                });
    }

    @Test
    void testReplaceLiteralFirst() {
        ReplaceTextParameters params = new ReplaceTextParameters();
        params.setSearchValue("foo");
        params.setReplacement("bar");
        params.setReplaceFirst(true);

        TransformInput input = createInput("foo foo foo", "test.txt", "text/plain");
        ResultType result = action.transform(runner.actionContext(), params, input);

        TransformResultAssert.assertThat(result)
                .hasContentCount(1)
                .hasContentMatchingAt(0, content -> {
                    Assertions.assertThat(content.loadString()).isEqualTo("bar foo foo");
                    return true;
                });
    }

    @Test
    void testReplaceRegexAll() {
        ReplaceTextParameters params = new ReplaceTextParameters();
        params.setSearchValue("\\d+");
        params.setReplacement("NUM");
        params.setRegex(true);

        TransformInput input = createInput("value1 value22 value333", "test.txt", "text/plain");
        ResultType result = action.transform(runner.actionContext(), params, input);

        TransformResultAssert.assertThat(result)
                .hasContentCount(1)
                .hasContentMatchingAt(0, content -> {
                    Assertions.assertThat(content.loadString()).isEqualTo("valueNUM valueNUM valueNUM");
                    return true;
                });
    }

    @Test
    void testReplaceRegexFirst() {
        ReplaceTextParameters params = new ReplaceTextParameters();
        params.setSearchValue("\\d+");
        params.setReplacement("NUM");
        params.setRegex(true);
        params.setReplaceFirst(true);

        TransformInput input = createInput("value1 value22 value333", "test.txt", "text/plain");
        ResultType result = action.transform(runner.actionContext(), params, input);

        TransformResultAssert.assertThat(result)
                .hasContentCount(1)
                .hasContentMatchingAt(0, content -> {
                    Assertions.assertThat(content.loadString()).isEqualTo("valueNUM value22 value333");
                    return true;
                });
    }

    @Test
    void testReplaceRegexCaptureGroups() {
        ReplaceTextParameters params = new ReplaceTextParameters();
        params.setSearchValue("(\\w+)=(\\w+)");
        params.setReplacement("$2:$1");
        params.setRegex(true);

        TransformInput input = createInput("key=value name=test", "test.txt", "text/plain");
        ResultType result = action.transform(runner.actionContext(), params, input);

        TransformResultAssert.assertThat(result)
                .hasContentCount(1)
                .hasContentMatchingAt(0, content -> {
                    Assertions.assertThat(content.loadString()).isEqualTo("value:key test:name");
                    return true;
                });
    }

    @Test
    void testReplaceNoMatch() {
        ReplaceTextParameters params = new ReplaceTextParameters();
        params.setSearchValue("notfound");
        params.setReplacement("replaced");

        String original = "this text has no matches";
        TransformInput input = createInput(original, "test.txt", "text/plain");
        ResultType result = action.transform(runner.actionContext(), params, input);

        TransformResultAssert.assertThat(result)
                .hasContentCount(1)
                .hasContentMatchingAt(0, content -> {
                    Assertions.assertThat(content.loadString()).isEqualTo(original);
                    return true;
                });
    }

    @Test
    void testReplaceInvalidRegex() {
        ReplaceTextParameters params = new ReplaceTextParameters();
        params.setSearchValue("[invalid(regex");
        params.setReplacement("x");
        params.setRegex(true);

        TransformInput input = createInput("test", "test.txt", "text/plain");
        ResultType result = action.transform(runner.actionContext(), params, input);

        ErrorResultAssert.assertThat(result)
                .hasCause("Error transforming content at index 0");
    }

    @Test
    void testReplacePreservesMetadata() {
        ReplaceTextParameters params = new ReplaceTextParameters();
        params.setSearchValue("old");
        params.setReplacement("new");

        TransformInput input = createInput("old content", "file.txt", "text/plain");
        ResultType result = action.transform(runner.actionContext(), params, input);

        TransformResultAssert.assertThat(result)
                .hasContentCount(1)
                .hasContentMatchingAt(0, content -> {
                    Assertions.assertThat(content.getName()).isEqualTo("file.txt");
                    Assertions.assertThat(content.getMediaType()).isEqualTo("text/plain");
                    return true;
                });
    }

    @Test
    void testReplaceWithContentSelection() {
        ReplaceTextParameters params = new ReplaceTextParameters();
        params.setSearchValue("replace");
        params.setReplacement("REPLACED");
        params.setContentIndexes(List.of(0));

        List<ActionContent> contents = List.of(
                ActionContent.saveContent(runner.actionContext(), "replace this", "first.txt", "text/plain"),
                ActionContent.saveContent(runner.actionContext(), "replace this too", "second.txt", "text/plain")
        );
        TransformInput input = TransformInput.builder().content(contents).build();
        ResultType result = action.transform(runner.actionContext(), params, input);

        TransformResultAssert.assertThat(result)
                .hasContentCount(2)
                .hasContentMatchingAt(0, content -> {
                    Assertions.assertThat(content.loadString()).isEqualTo("REPLACED this");
                    return true;
                })
                .hasContentMatchingAt(1, content -> {
                    // Second should be unchanged
                    Assertions.assertThat(content.loadString()).isEqualTo("replace this too");
                    return true;
                });
    }

    @Test
    void testReplaceSpecialCharactersLiteral() {
        ReplaceTextParameters params = new ReplaceTextParameters();
        params.setSearchValue("$100");
        params.setReplacement("$200");
        params.setRegex(false);

        TransformInput input = createInput("Price: $100", "test.txt", "text/plain");
        ResultType result = action.transform(runner.actionContext(), params, input);

        TransformResultAssert.assertThat(result)
                .hasContentCount(1)
                .hasContentMatchingAt(0, content -> {
                    Assertions.assertThat(content.loadString()).isEqualTo("Price: $200");
                    return true;
                });
    }

    private TransformInput createInput(String content, String name, String mediaType) {
        List<ActionContent> contents = List.of(
                ActionContent.saveContent(runner.actionContext(), content, name, mediaType)
        );
        return TransformInput.builder().content(contents).build();
    }
}
