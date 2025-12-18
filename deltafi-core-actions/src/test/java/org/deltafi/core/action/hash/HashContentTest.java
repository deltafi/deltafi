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
package org.deltafi.core.action.hash;

import org.assertj.core.api.Assertions;
import org.deltafi.actionkit.action.ResultType;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.test.asserters.TransformResultAssert;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

class HashContentTest {

    HashContent action = new HashContent();
    DeltaFiTestRunner runner = DeltaFiTestRunner.setup();

    @Test
    void testHashSha256Default() throws Exception {
        HashContentParameters params = new HashContentParameters();

        String content = "Hello, World!";
        TransformInput input = createInput(content, "test.txt", "text/plain");
        ResultType result = action.transform(runner.actionContext(), params, input);

        String expectedHash = computeHash(content, "SHA-256");

        TransformResultAssert.assertThat(result)
                .hasContentCount(1)
                .addedMetadata("hash", expectedHash)
                .hasContentMatchingAt(0, c -> {
                    // Content should be unchanged
                    Assertions.assertThat(c.loadString()).isEqualTo(content);
                    return true;
                });
    }

    @Test
    void testHashMd5() throws Exception {
        HashContentParameters params = new HashContentParameters();
        params.setAlgorithm(HashContentParameters.HashAlgorithm.MD5);

        String content = "test";
        TransformInput input = createInput(content, "test.txt", "text/plain");
        ResultType result = action.transform(runner.actionContext(), params, input);

        String expectedHash = computeHash(content, "MD5");

        TransformResultAssert.assertThat(result)
                .addedMetadata("hash", expectedHash);
    }

    @Test
    void testHashSha1() throws Exception {
        HashContentParameters params = new HashContentParameters();
        params.setAlgorithm(HashContentParameters.HashAlgorithm.SHA_1);

        String content = "test";
        TransformInput input = createInput(content, "test.txt", "text/plain");
        ResultType result = action.transform(runner.actionContext(), params, input);

        String expectedHash = computeHash(content, "SHA-1");

        TransformResultAssert.assertThat(result)
                .addedMetadata("hash", expectedHash);
    }

    @Test
    void testHashSha512() throws Exception {
        HashContentParameters params = new HashContentParameters();
        params.setAlgorithm(HashContentParameters.HashAlgorithm.SHA_512);

        String content = "test";
        TransformInput input = createInput(content, "test.txt", "text/plain");
        ResultType result = action.transform(runner.actionContext(), params, input);

        String expectedHash = computeHash(content, "SHA-512");

        TransformResultAssert.assertThat(result)
                .addedMetadata("hash", expectedHash);
    }

    @Test
    void testHashCustomMetadataKey() throws Exception {
        HashContentParameters params = new HashContentParameters();
        params.setMetadataKey("checksum");

        String content = "test";
        TransformInput input = createInput(content, "test.txt", "text/plain");
        ResultType result = action.transform(runner.actionContext(), params, input);

        String expectedHash = computeHash(content, "SHA-256");

        TransformResultAssert.assertThat(result)
                .addedMetadata("checksum", expectedHash);
    }

    @Test
    void testHashMultipleContent() throws Exception {
        HashContentParameters params = new HashContentParameters();

        List<ActionContent> contents = List.of(
                ActionContent.saveContent(runner.actionContext(), "first", "first.txt", "text/plain"),
                ActionContent.saveContent(runner.actionContext(), "second", "second.txt", "text/plain")
        );
        TransformInput input = TransformInput.builder().content(contents).build();
        ResultType result = action.transform(runner.actionContext(), params, input);

        String hash0 = computeHash("first", "SHA-256");
        String hash1 = computeHash("second", "SHA-256");

        TransformResultAssert.assertThat(result)
                .hasContentCount(2)
                .addedMetadata("hash.0", hash0)
                .addedMetadata("hash.1", hash1);
    }

    @Test
    void testHashWithContentSelection() throws Exception {
        HashContentParameters params = new HashContentParameters();
        params.setContentIndexes(List.of(1));

        List<ActionContent> contents = List.of(
                ActionContent.saveContent(runner.actionContext(), "first", "first.txt", "text/plain"),
                ActionContent.saveContent(runner.actionContext(), "second", "second.txt", "text/plain")
        );
        TransformInput input = TransformInput.builder().content(contents).build();
        ResultType result = action.transform(runner.actionContext(), params, input);

        String hash = computeHash("second", "SHA-256");

        // Only one hash should be generated (for the selected content)
        // Since there are 2 total contents but only 1 matched, it uses index suffix
        TransformResultAssert.assertThat(result)
                .hasContentCount(2)
                .addedMetadata("hash.0", hash);
    }

    @Test
    void testHashEmptyContent() throws Exception {
        HashContentParameters params = new HashContentParameters();

        String content = "";
        TransformInput input = createInput(content, "empty.txt", "text/plain");
        ResultType result = action.transform(runner.actionContext(), params, input);

        String expectedHash = computeHash(content, "SHA-256");

        TransformResultAssert.assertThat(result)
                .hasContentCount(1)
                .addedMetadata("hash", expectedHash);
    }

    @Test
    void testHashBinaryContent() throws Exception {
        HashContentParameters params = new HashContentParameters();

        byte[] binaryData = new byte[]{0x00, 0x01, 0x02, (byte) 0xFF, (byte) 0xFE};
        List<ActionContent> contents = List.of(
                ActionContent.saveContent(runner.actionContext(), binaryData, "binary.dat", "application/octet-stream")
        );
        TransformInput input = TransformInput.builder().content(contents).build();
        ResultType result = action.transform(runner.actionContext(), params, input);

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        String expectedHash = HexFormat.of().formatHex(digest.digest(binaryData));

        TransformResultAssert.assertThat(result)
                .addedMetadata("hash", expectedHash);
    }

    @Test
    void testHashContentUnchanged() {
        HashContentParameters params = new HashContentParameters();

        String content = "unchanged content";
        TransformInput input = createInput(content, "test.txt", "text/plain");
        ResultType result = action.transform(runner.actionContext(), params, input);

        TransformResultAssert.assertThat(result)
                .hasContentCount(1)
                .hasContentMatchingAt(0, c -> {
                    Assertions.assertThat(c.getName()).isEqualTo("test.txt");
                    Assertions.assertThat(c.getMediaType()).isEqualTo("text/plain");
                    Assertions.assertThat(c.loadString()).isEqualTo(content);
                    return true;
                });
    }

    private TransformInput createInput(String content, String name, String mediaType) {
        List<ActionContent> contents = List.of(
                ActionContent.saveContent(runner.actionContext(), content, name, mediaType)
        );
        return TransformInput.builder().content(contents).build();
    }

    private String computeHash(String content, String algorithm) throws Exception {
        MessageDigest digest = MessageDigest.getInstance(algorithm);
        byte[] hash = digest.digest(content.getBytes());
        return HexFormat.of().formatHex(hash);
    }
}
