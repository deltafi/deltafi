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
import org.deltafi.test.asserters.TransformResultAssert;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;

class Base64EncodeTest {

    Base64Encode action = new Base64Encode();
    DeltaFiTestRunner runner = DeltaFiTestRunner.setup();

    @Test
    void testEncodeBasic() {
        Base64EncodeParameters params = new Base64EncodeParameters();

        String original = "Hello, World!";
        TransformInput input = createInput(original, "test.txt", "text/plain");
        ResultType result = action.transform(runner.actionContext(), params, input);

        TransformResultAssert.assertThat(result)
                .hasContentCount(1)
                .hasContentMatchingAt(0, content -> {
                    Assertions.assertThat(content.getName()).isEqualTo("test.txt.b64");
                    Assertions.assertThat(content.getMediaType()).isEqualTo("text/plain");
                    String encoded = content.loadString();
                    String decoded = new String(Base64.getDecoder().decode(encoded));
                    Assertions.assertThat(decoded).isEqualTo(original);
                    return true;
                });
    }

    @Test
    void testEncodeUrlSafe() {
        Base64EncodeParameters params = new Base64EncodeParameters();
        params.setUrlSafe(true);

        // Binary data that produces + and / in standard Base64
        byte[] data = new byte[]{-5, -1, -2, -3, -4};
        TransformInput input = createInputBytes(data, "binary.dat", "application/octet-stream");
        ResultType result = action.transform(runner.actionContext(), params, input);

        TransformResultAssert.assertThat(result)
                .hasContentCount(1)
                .hasContentMatchingAt(0, content -> {
                    String encoded = content.loadString();
                    // URL-safe encoding should not contain + or /
                    Assertions.assertThat(encoded).doesNotContain("+").doesNotContain("/");
                    // Should be decodable with URL-safe decoder
                    byte[] decoded = Base64.getUrlDecoder().decode(encoded);
                    Assertions.assertThat(decoded).isEqualTo(data);
                    return true;
                });
    }

    @Test
    void testEncodeCustomMediaType() {
        Base64EncodeParameters params = new Base64EncodeParameters();
        params.setOutputMediaType("application/base64");

        TransformInput input = createInput("test", "file.txt", "text/plain");
        ResultType result = action.transform(runner.actionContext(), params, input);

        TransformResultAssert.assertThat(result)
                .hasContentCount(1)
                .hasContentMatchingAt(0, content -> {
                    Assertions.assertThat(content.getMediaType()).isEqualTo("application/base64");
                    return true;
                });
    }

    @Test
    void testEncodeWithContentSelection() {
        Base64EncodeParameters params = new Base64EncodeParameters();
        params.setContentIndexes(List.of(0));

        List<ActionContent> contents = List.of(
                ActionContent.saveContent(runner.actionContext(), "first", "first.txt", "text/plain"),
                ActionContent.saveContent(runner.actionContext(), "second", "second.txt", "text/plain")
        );
        TransformInput input = TransformInput.builder().content(contents).build();
        ResultType result = action.transform(runner.actionContext(), params, input);

        TransformResultAssert.assertThat(result)
                .hasContentCount(2)
                .hasContentMatchingAt(0, content -> {
                    Assertions.assertThat(content.getName()).isEqualTo("first.txt.b64");
                    return true;
                })
                .hasContentMatchingAt(1, content -> {
                    // Second content should be unchanged
                    Assertions.assertThat(content.getName()).isEqualTo("second.txt");
                    Assertions.assertThat(content.loadString()).isEqualTo("second");
                    return true;
                });
    }

    @Test
    void testEncodeRetainExisting() {
        Base64EncodeParameters params = new Base64EncodeParameters();
        params.setRetainExistingContent(true);

        TransformInput input = createInput("test", "file.txt", "text/plain");
        ResultType result = action.transform(runner.actionContext(), params, input);

        TransformResultAssert.assertThat(result)
                .hasContentCount(2)
                .hasContentMatchingAt(0, content -> {
                    // Original should be retained
                    Assertions.assertThat(content.getName()).isEqualTo("file.txt");
                    Assertions.assertThat(content.loadString()).isEqualTo("test");
                    return true;
                })
                .hasContentMatchingAt(1, content -> {
                    // Encoded version
                    Assertions.assertThat(content.getName()).isEqualTo("file.txt.b64");
                    return true;
                });
    }

    private TransformInput createInput(String content, String name, String mediaType) {
        List<ActionContent> contents = List.of(
                ActionContent.saveContent(runner.actionContext(), content, name, mediaType)
        );
        return TransformInput.builder().content(contents).build();
    }

    private TransformInput createInputBytes(byte[] content, String name, String mediaType) {
        List<ActionContent> contents = List.of(
                ActionContent.saveContent(runner.actionContext(), content, name, mediaType)
        );
        return TransformInput.builder().content(contents).build();
    }
}
