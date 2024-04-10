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

import org.assertj.core.api.Assertions;
import org.deltafi.actionkit.action.ResultType;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.core.parameters.DecompressionTransformParameters;
import org.deltafi.core.parameters.DecompressionType;
import org.deltafi.test.asserters.ContentAssert;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.junit.jupiter.api.Test;

import static org.deltafi.test.asserters.ActionResultAssertions.*;

class DecompressTest {
    private static final String CONTENT_TYPE = "application/octet-stream";

    Decompress action = new Decompress();
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
                .addedMetadata("decompressionType", "tar")
                .hasContentMatchingAt(0, actionContent -> contentMatches(actionContent, "bar/2/baz", "bar2\n", 2560))
                .hasContentMatchingAt(1, actionContent -> contentMatches(actionContent, "bar/3/baz", "bar3\n", 3584))
                .hasContentMatchingAt(2, actionContent -> contentMatches(actionContent, "bar/1/baz", "bar1\n", 4608))
                .hasContentMatchingAt(3, actionContent -> contentMatches(actionContent, "foo/2/baz", "foo2\n", 7680))
                .hasContentMatchingAt(4, actionContent -> contentMatches(actionContent, "foo/3/baz", "foo3\n", 8704))
                .hasContentMatchingAt(5, actionContent -> contentMatches(actionContent, "foo/1/baz", "foo1\n", 9728));
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
                .addedMetadata("decompressionType", metadataValue)
                .hasContentMatchingAt(0, actionContent -> contentMatches(actionContent, "thing1.txt", "thing1\n"))
                .hasContentMatchingAt(1, actionContent -> contentMatches(actionContent, "thing2.txt", "thing2\n"));
    }

    void validateDecompressResult(String archiveFile, DecompressionType decompressionType, String contentName, String metadataValue) {
        ResultType resultType = runAction(archiveFile, decompressionType);
        assertTransformResult(resultType)
                .addedMetadata("decompressionType", metadataValue)
                .hasContentMatchingAt(0, actionContent -> contentMatches(actionContent, contentName, "thing1\n"));
    }

    private void validateUnarchiveZipWithSubdirectories(ResultType resultType) {
        assertTransformResult(resultType)
                .addedMetadata("decompressionType", "zip")
                .hasContentMatchingAt(0, actionContent -> contentMatches(actionContent, "bar/1/baz", "bar1\n"))
                .hasContentMatchingAt(1, actionContent -> contentMatches(actionContent, "bar/3/baz", "bar3\n"))
                .hasContentMatchingAt(2, actionContent -> contentMatches(actionContent, "bar/2/baz", "bar2\n"))
                .hasContentMatchingAt(3, actionContent -> contentMatches(actionContent, "foo/1/baz", "foo1\n"))
                .hasContentMatchingAt(4, actionContent -> contentMatches(actionContent, "foo/3/baz", "foo3\n"))
                .hasContentMatchingAt(5, actionContent -> contentMatches(actionContent, "foo/2/baz", "foo2\n"));
    }

    private void validateUnarchiveTar(ResultType result) {
        assertTransformResult(result)
                .addedMetadata("decompressionType", "tar")
                .hasContentMatchingAt(0, actionContent -> contentMatches(actionContent, "thing1.txt", "thing1\n", 512))
                .hasContentMatchingAt(1, actionContent -> contentMatches(actionContent, "thing2.txt", "thing2\n", 1536));
    }

    boolean contentMatches(ActionContent actionContent, String name, String value) {
        ContentAssert.assertThat(actionContent)
                .hasName(name)
                .hasMediaType(CONTENT_TYPE)
                .loadStringIsEqualTo(value);
        return true;
    }

    boolean contentMatches(ActionContent actionContent, String expectedName, String contentValue, long expectedOffset) {
        ContentAssert.assertThat(actionContent)
                .hasName(expectedName)
                .hasMediaType(CONTENT_TYPE)
                .loadStringIsEqualTo(contentValue);

        Assertions.assertThat(actionContent.getContent().getSegments()).hasSize(1);
        Assertions.assertThat(actionContent.getContent().getSegments().get(0).getOffset()).isEqualTo(expectedOffset);
        return true;
    }

    ResultType runAction(String archiveFile, DecompressionType decompressionType) {
        return action.transform(runner.actionContext(), new DecompressionTransformParameters(decompressionType), input(archiveFile));
    }

    TransformInput input(String archiveFile) {
        return TransformInput.builder()
                .content(runner.saveContentFromResource(archiveFile))
                .build();
    }
}
