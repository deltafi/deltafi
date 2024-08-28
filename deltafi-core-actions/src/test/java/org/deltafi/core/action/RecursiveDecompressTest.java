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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.assertj.core.api.Assertions;
import org.deltafi.actionkit.action.ResultType;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.core.parameters.DecompressionType;
import org.deltafi.core.parameters.RecursiveDecompressParameters;
import org.deltafi.test.asserters.ContentAssert;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.deltafi.test.asserters.ActionResultAssertions.assertErrorResult;
import static org.deltafi.test.asserters.ActionResultAssertions.assertTransformResult;

class RecursiveDecompressTest {
    private static final String CONTENT_TYPE = "application/octet-stream";
    private static final String MANIFEST_CONTENT_TYPE = "application/json";
    private static final String MANIFEST = "MANIFEST";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static {
        OBJECT_MAPPER.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    RecursiveDecompress action = new RecursiveDecompress();
    DeltaFiTestRunner runner = DeltaFiTestRunner.setup(action, "DecompressionTransformActionTest");

    @Test
    void decompressTarGz() {
        validateUnarchiveResult("decompressTarGz/things.tar.gz", DecompressionType.TAR_GZIP, "tar.gz");
    }

    @Test
    void autoDecompressTarGz() {
        validateUnarchiveResult("autoDecompressTarGz/things.tar.gz", DecompressionType.AUTO, "tar.gz");
    }

    @Test
    void autoDecompressTarZ() {
        validateUnarchiveResult("autoDecompressTarZ/things.tar.Z", DecompressionType.AUTO, "tar.z");
    }

    @Test
    void autoDecompressTarXZ() {
        validateUnarchiveResult("autoDecompressTarXZ/things.tar.xz", DecompressionType.AUTO, "tar.xz");
    }


    @Test
    void decompressTarXZ() {
        validateUnarchiveResult("decompressTarXZ/things.tar.xz", DecompressionType.TAR_XZ, "tar.xz");
    }

    @Test
    void decompressTarZ() {
        validateUnarchiveResult("decompressTarZ/things.tar.Z", DecompressionType.TAR_Z, "tar.z");
    }

    @Test
    void decompressAR() {
        validateUnarchiveResult("decompressAR/things.ar", DecompressionType.AR, "ar");
    }

    @Test
    void autoDecompressAR() {
        validateUnarchiveResult("autoDecompressAR/things.ar", DecompressionType.AUTO, "ar");
    }

    @Test
    void autoDecompressZip() {
        validateUnarchiveResult("autoDecompressZip/things.zip", DecompressionType.AUTO, "zip");
    }

    @Test
    void autoDecompressTar() {
        ResultType result = runAction("autoDecompressTar/things.tar", DecompressionType.AUTO);
        validateUnarchiveTar(result);
    }

    @Test
    void autoDecompressGzip() {
        validateDecompressResult("autoDecompressGzip/thing1.txt.gz", DecompressionType.AUTO, "thing1.txt.gz", "gz");
    }

    @Test
    void decompressZip() {
        validateUnarchiveResult("decompressZip/things.zip", DecompressionType.ZIP, "zip");
    }

    @Test
    void decompressGzip() {
        validateDecompressResult("decompressGzip/thing1.txt.gz", DecompressionType.GZIP, "thing1.txt.gz", "gz");
    }

    @Test
    void autoDecompressXZ() {
        validateDecompressResult("autoDecompressXZ/thing1.txt.xz", DecompressionType.AUTO, "thing1.txt.xz", "xz");
    }

    @Test
    void decompressXZ() {
        validateDecompressResult("decompressXZ/thing1.txt.xz", DecompressionType.XZ, "thing1.txt.xz", "xz");
    }

    @Test
    void decompressZ() {
        validateDecompressResult("decompressZ/thing1.txt.Z", DecompressionType.Z, "thing1.txt.Z", "z");
    }

    @Test
    void autoDecompressZ() {
        validateDecompressResult("autoDecompressZ/thing1.txt.Z", DecompressionType.AUTO, "thing1.txt.Z", "z");
    }

    @Test
    void unarchiveTar() {
        ResultType result = runAction("unarchiveTar/things.tar", DecompressionType.TAR);
        validateUnarchiveTar(result);
    }

    @Test
    void autoUnarchiveTarZ() {
        validateUnarchiveResult("autoUnarchiveTarZ/things.tar.Z", DecompressionType.AUTO, "tar.z");
    }

    @Test
    void autoUnarchiveTarXZ() {
        validateUnarchiveResult("autoUnarchiveTarXZ/things.tar.xz", DecompressionType.AUTO, "tar.xz");
    }

    @Test
    void unarchiveSubdirectoryTar() {
        ResultType resultType = runAction("unarchiveSubdirectoryTar/foobar.tar", DecompressionType.TAR);

        assertTransformResult(resultType)
                .hasContentMatchingAt(0, actionContent -> contentMatches(actionContent, "bar/2/baz", "bar2\n", 0))
                .hasContentMatchingAt(1, actionContent -> contentMatches(actionContent, "bar/3/baz", "bar3\n", 0))
                .hasContentMatchingAt(2, actionContent -> contentMatches(actionContent, "bar/1/baz", "bar1\n", 0))
                .hasContentMatchingAt(3, actionContent -> contentMatches(actionContent, "foo/2/baz", "foo2\n", 0))
                .hasContentMatchingAt(4, actionContent -> contentMatches(actionContent, "foo/3/baz", "foo3\n", 0))
                .hasContentMatchingAt(5, actionContent -> contentMatches(actionContent, "foo/1/baz", "foo1\n", 0));
    }

    @Test
    void recursiveAuto() {
        ResultType resultType = runAction("recursive/multi-layers.tgz", DecompressionType.AUTO, 5, MANIFEST);

        Map<String, String> lineage = new HashMap<>();
        lineage.putAll(Map.of(
                "top-dir/a.txt.gz", "multi-layers.tgz",
                "top-dir/dir2/sub2/b.txt", "multi-layers.tgz",
                "top-dir/dir3-has-two-zips.tar", "multi-layers.tgz",
                "top-dir/dir1/three.zip", "multi-layers.tgz"));
        lineage.putAll(Map.of(
                "top-dir/a.txt", "top-dir/a.txt.gz",
                "top-dir/dir3/z.txt.zip", "top-dir/dir3-has-two-zips.tar",
                "top-dir/dir3/y.txt.zip", "top-dir/dir3-has-two-zips.tar",
                "top-dir/dir1/1", "top-dir/dir1/three.zip",
                "top-dir/dir1/2", "top-dir/dir1/three.zip",
                "top-dir/dir1/3", "top-dir/dir1/three.zip",
                "top-dir/dir3/z.txt", "top-dir/dir3/z.txt.zip",
                "top-dir/dir3/y.txt", "top-dir/dir3/y.txt.zip"));
        String expectedManifest = mapToJson(lineage);

        assertTransformResult(resultType)
                .hasContentCount(8)
                .hasContentMatchingAt(0, actionContent -> contentMatches(actionContent, MANIFEST, expectedManifest, 0))
                .hasContentMatchingAt(1, actionContent -> contentMatches(actionContent, "top-dir/dir2/sub2/b.txt", "bbb\n", 0))
                .hasContentMatchingAt(2, actionContent -> contentMatches(actionContent, "top-dir/dir1/1", "111\n", 0))
                .hasContentMatchingAt(3, actionContent -> contentMatches(actionContent, "top-dir/dir1/2", "222\n", 0))
                .hasContentMatchingAt(4, actionContent -> contentMatches(actionContent, "top-dir/dir1/3", "333\n", 0))
                .hasContentMatchingAt(5, actionContent -> contentMatches(actionContent, "top-dir/a.txt", "aaa\n", 0))
                .hasContentMatchingAt(6, actionContent -> contentMatches(actionContent, "top-dir/dir3/z.txt", "zzz\n", 0))
                .hasContentMatchingAt(7, actionContent -> contentMatches(actionContent, "top-dir/dir3/y.txt", "yyy\n", 0));
    }

    @Test
    void recurseLessLevels() {
        ResultType resultType = runAction("recursive/multi-layers.tgz", DecompressionType.TAR_GZIP, 1, MANIFEST);

        Map<String, String> lineage = new HashMap<>();
        lineage.putAll(Map.of(
                "top-dir/a.txt.gz", "multi-layers.tgz",
                "top-dir/dir2/sub2/b.txt", "multi-layers.tgz",
                "top-dir/dir3-has-two-zips.tar", "multi-layers.tgz",
                "top-dir/dir1/three.zip", "multi-layers.tgz"));
        lineage.putAll(Map.of(
                "top-dir/a.txt", "top-dir/a.txt.gz",
                "top-dir/dir3/z.txt.zip", "top-dir/dir3-has-two-zips.tar",
                "top-dir/dir3/y.txt.zip", "top-dir/dir3-has-two-zips.tar",
                "top-dir/dir1/1", "top-dir/dir1/three.zip",
                "top-dir/dir1/2", "top-dir/dir1/three.zip",
                "top-dir/dir1/3", "top-dir/dir1/three.zip"));
        String expectedManifest = mapToJson(lineage);

        assertTransformResult(resultType)
                .hasContentCount(8)
                .hasContentMatchingAt(0, actionContent -> contentMatches(actionContent, MANIFEST, expectedManifest, 0))
                .hasContentMatchingAt(1, actionContent -> contentMatches(actionContent, "top-dir/dir2/sub2/b.txt", "bbb\n", 0))
                .hasContentMatchingAt(2, actionContent -> contentMatches(actionContent, "top-dir/dir3/z.txt.zip", null, 0))
                .hasContentMatchingAt(3, actionContent -> contentMatches(actionContent, "top-dir/dir3/y.txt.zip", null, 0))
                .hasContentMatchingAt(4, actionContent -> contentMatches(actionContent, "top-dir/dir1/1", "111\n", 0))
                .hasContentMatchingAt(5, actionContent -> contentMatches(actionContent, "top-dir/dir1/2", "222\n", 0))
                .hasContentMatchingAt(6, actionContent -> contentMatches(actionContent, "top-dir/dir1/3", "333\n", 0))
                .hasContentMatchingAt(7, actionContent -> contentMatches(actionContent, "top-dir/a.txt", "aaa\n", 0));
    }

    @Test
    void recursiveAutoWithoutManifest() {
        ResultType resultType = runAction("recursive/multi-layers.tgz", DecompressionType.TAR_GZIP, 5, null);

        assertTransformResult(resultType)
                .hasContentCount(7)
                .hasContentMatchingAt(0, actionContent -> contentMatches(actionContent, "top-dir/dir2/sub2/b.txt", "bbb\n", 0))
                .hasContentMatchingAt(1, actionContent -> contentMatches(actionContent, "top-dir/dir1/1", "111\n", 0))
                .hasContentMatchingAt(2, actionContent -> contentMatches(actionContent, "top-dir/dir1/2", "222\n", 0))
                .hasContentMatchingAt(3, actionContent -> contentMatches(actionContent, "top-dir/dir1/3", "333\n", 0))
                .hasContentMatchingAt(4, actionContent -> contentMatches(actionContent, "top-dir/a.txt", "aaa\n", 0))
                .hasContentMatchingAt(5, actionContent -> contentMatches(actionContent, "top-dir/dir3/z.txt", "zzz\n", 0))
                .hasContentMatchingAt(6, actionContent -> contentMatches(actionContent, "top-dir/dir3/y.txt", "yyy\n", 0));
    }

    @Test
    void unarchiveSubdirectoryZip() {
        ResultType resultType = runAction("unarchiveSubdirectoryZip/foobar.zip", DecompressionType.ZIP);
        validateUnarchiveZipWithSubdirectories(resultType);
    }

    @Test
    void unarchiveSubdirectoryAutoZip() {
        ResultType resultType = runAction("unarchiveSubdirectoryAutoZip/foobar.zip", DecompressionType.AUTO);
        validateUnarchiveZipWithSubdirectories(resultType);
    }

    @Test
    void decompressionTypeMismatch() {
        assertErrorResult(runAction("decompressionTypeMismatch/things.tar.gz", DecompressionType.ZIP))
                .hasCause("Unable to decompress zip");
    }

    @Test
    void truncatedFile() {
        assertErrorResult(runAction("truncatedFile/bad.things.tar.gz", DecompressionType.TAR_GZIP))
                .hasCause("Unable to unarchive tar");
    }

    @Test
    void autoNoCompression() {
        assertErrorResult(runAction("autoNoCompression/thing1.txt", DecompressionType.AUTO))
                .hasCause("No compression or archive formats detected");
    }

    void validateUnarchiveResult(String archiveFile, DecompressionType decompressionType, String metadataValue) {
        ResultType resultType = runAction(archiveFile, decompressionType);
        assertTransformResult(resultType)
                .hasContentMatchingAt(0, actionContent -> contentMatches(actionContent, "thing1.txt", "thing1\n"))
                .hasContentMatchingAt(1, actionContent -> contentMatches(actionContent, "thing2.txt", "thing2\n"));
    }

    void validateDecompressResult(String archiveFile, DecompressionType decompressionType, String contentName, String metadataValue) {
        String newContentName = contentName
                .replaceAll(".txt.gz", ".txt")
                .replaceAll(".txt.xz", ".txt")
                .replaceAll(".txt.Z", ".txt");
        ResultType resultType = runAction(archiveFile, decompressionType);
        assertTransformResult(resultType)
                .hasContentMatchingAt(0, actionContent -> contentMatches(actionContent, newContentName, "thing1\n"));
    }

    private void validateUnarchiveZipWithSubdirectories(ResultType resultType) {
        assertTransformResult(resultType)
                .hasContentMatchingAt(0, actionContent -> contentMatches(actionContent, "bar/1/baz", "bar1\n"))
                .hasContentMatchingAt(1, actionContent -> contentMatches(actionContent, "bar/3/baz", "bar3\n"))
                .hasContentMatchingAt(2, actionContent -> contentMatches(actionContent, "bar/2/baz", "bar2\n"))
                .hasContentMatchingAt(3, actionContent -> contentMatches(actionContent, "foo/1/baz", "foo1\n"))
                .hasContentMatchingAt(4, actionContent -> contentMatches(actionContent, "foo/3/baz", "foo3\n"))
                .hasContentMatchingAt(5, actionContent -> contentMatches(actionContent, "foo/2/baz", "foo2\n"));
    }

    private void validateUnarchiveTar(ResultType result) {
        assertTransformResult(result)
                .hasContentMatchingAt(0, actionContent -> contentMatches(actionContent, "thing1.txt", "thing1\n", 0))
                .hasContentMatchingAt(1, actionContent -> contentMatches(actionContent, "thing2.txt", "thing2\n", 0));
    }

    boolean contentMatches(ActionContent actionContent, String name, String value) {
        ContentAssert.assertThat(actionContent)
                .hasName(name)
                .hasMediaType(CONTENT_TYPE)
                .loadStringIsEqualTo(value);
        return true;
    }

    boolean contentMatches(ActionContent actionContent, String expectedName, String contentValue, long expectedOffset) {
        String expectedContentType = MANIFEST.equals(expectedName) ? MANIFEST_CONTENT_TYPE : CONTENT_TYPE;

        ContentAssert.assertThat(actionContent)
                .hasName(expectedName)
                .hasMediaType(expectedContentType);
        if (contentValue != null) {
            ContentAssert.assertThat(actionContent)
                    .loadStringIsEqualTo(contentValue);
        }

        Assertions.assertThat(actionContent.getContent().getSegments()).hasSize(1);
        Assertions.assertThat(actionContent.getContent().getSegments().get(0).getOffset()).isEqualTo(expectedOffset);
        return true;
    }

    ResultType runAction(String archiveFile, DecompressionType decompressionType) {
        return runAction(archiveFile, decompressionType, 99, null);
    }

    ResultType runAction(String archiveFile, DecompressionType decompressionType, int maxLevels, String manifest) {
        return action.transform(runner.actionContext(), new RecursiveDecompressParameters(decompressionType, manifest, maxLevels), input(archiveFile));
    }

    TransformInput input(String archiveFile) {
        return TransformInput.builder()
                .content(runner.saveContentFromResource(archiveFile))
                .build();
    }

    private String mapToJson(Map<String, String> theMap) {
        String jsonManifest = "";
        try {
            jsonManifest = OBJECT_MAPPER.writeValueAsString(theMap);
        } catch (JsonProcessingException e) {
            jsonManifest = "ERROR";
        }
        return jsonManifest;
    }
}
