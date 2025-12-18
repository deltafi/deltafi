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
package org.deltafi.core.action.base64;

import org.assertj.core.api.Assertions;
import org.deltafi.actionkit.action.ResultType;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.test.asserters.ErrorResultAssert;
import org.deltafi.test.asserters.TransformResultAssert;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;

class Base64DecodeTest {

    Base64Decode action = new Base64Decode();
    DeltaFiTestRunner runner = DeltaFiTestRunner.setup();

    @Test
    void testDecodeBasic() {
        Base64DecodeParameters params = new Base64DecodeParameters();

        String original = "Hello, World!";
        String encoded = Base64.getEncoder().encodeToString(original.getBytes());
        TransformInput input = createInput(encoded, "test.txt.b64", "text/plain");
        ResultType result = action.transform(runner.actionContext(), params, input);

        TransformResultAssert.assertThat(result)
                .hasContentCount(1)
                .hasContentMatchingAt(0, content -> {
                    Assertions.assertThat(content.getName()).isEqualTo("test.txt");
                    Assertions.assertThat(content.getMediaType()).isEqualTo("application/octet-stream");
                    Assertions.assertThat(content.loadString()).isEqualTo(original);
                    return true;
                });
    }

    @Test
    void testDecodeUrlSafe() {
        Base64DecodeParameters params = new Base64DecodeParameters();
        params.setUrlSafe(true);

        byte[] data = new byte[]{-5, -1, -2, -3, -4};
        String encoded = Base64.getUrlEncoder().encodeToString(data);
        TransformInput input = createInput(encoded, "binary.dat.b64", "text/plain");
        ResultType result = action.transform(runner.actionContext(), params, input);

        TransformResultAssert.assertThat(result)
                .hasContentCount(1)
                .hasContentMatchingAt(0, content -> {
                    Assertions.assertThat(content.getName()).isEqualTo("binary.dat");
                    Assertions.assertThat(content.loadBytes()).isEqualTo(data);
                    return true;
                });
    }

    @Test
    void testDecodeCustomMediaType() {
        Base64DecodeParameters params = new Base64DecodeParameters();
        params.setOutputMediaType("application/json");

        String json = "{\"key\": \"value\"}";
        String encoded = Base64.getEncoder().encodeToString(json.getBytes());
        TransformInput input = createInput(encoded, "data.json.b64", "text/plain");
        ResultType result = action.transform(runner.actionContext(), params, input);

        TransformResultAssert.assertThat(result)
                .hasContentCount(1)
                .hasContentMatchingAt(0, content -> {
                    Assertions.assertThat(content.getMediaType()).isEqualTo("application/json");
                    Assertions.assertThat(content.loadString()).isEqualTo(json);
                    return true;
                });
    }

    @Test
    void testDecodeWithoutB64Suffix() {
        Base64DecodeParameters params = new Base64DecodeParameters();

        String original = "test";
        String encoded = Base64.getEncoder().encodeToString(original.getBytes());
        TransformInput input = createInput(encoded, "noext", "text/plain");
        ResultType result = action.transform(runner.actionContext(), params, input);

        TransformResultAssert.assertThat(result)
                .hasContentCount(1)
                .hasContentMatchingAt(0, content -> {
                    // Name should remain unchanged since no .b64 suffix
                    Assertions.assertThat(content.getName()).isEqualTo("noext");
                    Assertions.assertThat(content.loadString()).isEqualTo(original);
                    return true;
                });
    }

    @Test
    void testDecodeWithWhitespaceAndNewlines() {
        Base64DecodeParameters params = new Base64DecodeParameters();

        String original = "Hello, World!";
        String encoded = Base64.getEncoder().encodeToString(original.getBytes());
        // Add whitespace and newlines that commonly appear in Base64 files
        String encodedWithWhitespace = "  " + encoded + "\n\n";
        TransformInput input = createInput(encodedWithWhitespace, "test.b64", "text/plain");
        ResultType result = action.transform(runner.actionContext(), params, input);

        TransformResultAssert.assertThat(result)
                .hasContentCount(1)
                .hasContentMatchingAt(0, content -> {
                    Assertions.assertThat(content.loadString()).isEqualTo(original);
                    return true;
                });
    }

    @Test
    void testDecodeInvalidBase64UrlSafe() {
        Base64DecodeParameters params = new Base64DecodeParameters();
        params.setUrlSafe(true);

        TransformInput input = createInput("not valid base64!!!", "bad.b64", "text/plain");
        ResultType result = action.transform(runner.actionContext(), params, input);

        // URL-safe decoder is strict and will error on invalid input
        ErrorResultAssert.assertThat(result)
                .hasCause("Error transforming content at index 0");
    }

    @Test
    void testDecodeWithContentSelection() {
        Base64DecodeParameters params = new Base64DecodeParameters();
        params.setContentIndexes(List.of(1));

        String encoded = Base64.getEncoder().encodeToString("decoded".getBytes());
        List<ActionContent> contents = List.of(
                ActionContent.saveContent(runner.actionContext(), "plain text", "first.txt", "text/plain"),
                ActionContent.saveContent(runner.actionContext(), encoded, "second.b64", "text/plain")
        );
        TransformInput input = TransformInput.builder().content(contents).build();
        ResultType result = action.transform(runner.actionContext(), params, input);

        TransformResultAssert.assertThat(result)
                .hasContentCount(2)
                .hasContentMatchingAt(0, content -> {
                    // First should be unchanged
                    Assertions.assertThat(content.loadString()).isEqualTo("plain text");
                    return true;
                })
                .hasContentMatchingAt(1, content -> {
                    Assertions.assertThat(content.loadString()).isEqualTo("decoded");
                    return true;
                });
    }

    @Test
    void testDecodeRetainExisting() {
        Base64DecodeParameters params = new Base64DecodeParameters();
        params.setRetainExistingContent(true);

        String original = "test";
        String encoded = Base64.getEncoder().encodeToString(original.getBytes());
        TransformInput input = createInput(encoded, "file.b64", "text/plain");
        ResultType result = action.transform(runner.actionContext(), params, input);

        TransformResultAssert.assertThat(result)
                .hasContentCount(2)
                .hasContentMatchingAt(0, content -> {
                    // Original should be retained
                    Assertions.assertThat(content.getName()).isEqualTo("file.b64");
                    return true;
                })
                .hasContentMatchingAt(1, content -> {
                    // Decoded version
                    Assertions.assertThat(content.getName()).isEqualTo("file");
                    Assertions.assertThat(content.loadString()).isEqualTo(original);
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
