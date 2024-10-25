/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.action.compress;

import org.deltafi.actionkit.action.ResultType;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.common.test.time.TestClock;
import org.deltafi.test.asserters.ContentAssert;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.MediaType;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.deltafi.test.asserters.ActionResultAssertions.assertTransformResult;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.fail;

public class CompressTest {
    private final TestClock testClock = new TestClock();
    private final Compress action = new Compress(testClock);
    private final DeltaFiTestRunner runner = DeltaFiTestRunner.setup("CompressTest");

    @Test
    public void errorResultOnNoContent() {
        CompressParameters compressParameters = new CompressParameters();
        compressParameters.setFormat(Format.TAR);

        ResultType result = action.transform(runner.actionContext(), compressParameters,
                TransformInput.builder().build());

        assertInstanceOf(ErrorResult.class, result);
    }

    @Test
    public void archivesAr() {
        runTest(Format.AR);
    }

    @Test
    public void archivesTar() {
        runTest(Format.TAR);
    }

    @Test
    public void archivesTarGzip() {
        runTest(Format.TAR_GZIP);
    }

    @Test
    public void archivesTarXz() {
        runTest(Format.TAR_XZ);
    }

    @Test
    public void archivesZip() {
        runTest(Format.ZIP);
    }

    private void runTest(Format format) {
        TransformInput transformInput = input("thing1.txt", "thing2.txt");

        CompressParameters compressParameters = new CompressParameters();
        compressParameters.setFormat(format);

        ResultType result = action.transform(runner.actionContext(), compressParameters, transformInput);

        verifyFormat(result, format, transformInput, "compressed." + format.getValue(),
                format.getMediaType());
    }

    @Test
    public void archivesTarWithMediaType() {
        TransformInput transformInput = input("thing1.txt", "thing2.txt");

        CompressParameters compressParameters = new CompressParameters();
        compressParameters.setFormat(Format.TAR);
        compressParameters.setMediaType(MediaType.APPLICATION_OCTET_STREAM);

        ResultType result = action.transform(runner.actionContext(), compressParameters, transformInput);

        verifyFormat(result, Format.TAR, transformInput, "compressed.tar", MediaType.APPLICATION_OCTET_STREAM);
    }

    private TransformInput input(String... files) {
        return TransformInput.builder().content(runner.saveContentFromResource(files)).build();
    }

    private void verifyFormat(ResultType result, Format format, TransformInput transformInput, String name,
            String mediaType) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ArchiveWriter archiveWriter = new ArchiveWriter(transformInput.content(), format, testClock);
        try {
            archiveWriter.write(byteArrayOutputStream);
        } catch (IOException e) {
            fail(e.getMessage());
        }

        assertTransformResult(result)
                .hasContentMatching(content -> {
                    ContentAssert.assertThat(content)
                            .hasName(name)
                            .hasMediaType(mediaType);
                    return true;
                })
                .contentLoadBytesEquals(List.of(byteArrayOutputStream.toByteArray()))
                .addedMetadata("compressFormat", format.getValue());
    }

    @Test
    public void compressesSingleGzip() {
        CompressParameters compressParameters = new CompressParameters();
        compressParameters.setFormat(Format.GZIP);

        ResultType result = action.transform(runner.actionContext(), compressParameters, input("fileA"));

        verifySingleResult(result, Format.GZIP, "fileA.gz", Format.GZIP.getMediaType());
    }

    @Test
    public void compressesSingleXz() {
        CompressParameters compressParameters = new CompressParameters();
        compressParameters.setFormat(Format.XZ);

        ResultType result = action.transform(runner.actionContext(), compressParameters, input("fileA"));

        verifySingleResult(result, Format.XZ, "fileA.xz", Format.XZ.getMediaType());
    }

    @Test
    public void compressesSingleGzipWithMediaType() {
        CompressParameters compressParameters = new CompressParameters();
        compressParameters.setFormat(Format.GZIP);
        compressParameters.setMediaType(MediaType.APPLICATION_OCTET_STREAM);

        ResultType result = action.transform(runner.actionContext(), compressParameters, input("fileA"));

        verifySingleResult(result, Format.GZIP, "fileA.gz", MediaType.APPLICATION_OCTET_STREAM);
    }

    private void verifySingleResult(ResultType result, Format format, String file, String mediaType) {
        assertTransformResult(result)
                .hasContentMatching(actionContent -> {
                    ContentAssert.assertThat(actionContent)
                            .loadBytesIsEqualTo(runner.readResourceAsBytes(file))
                            .hasMediaType(mediaType);
                    return true;
                })
                .addedMetadata("compressFormat", format.getValue());
    }

    @Test
    public void compressesMultipleGzip() {
        CompressParameters compressParameters = new CompressParameters();
        compressParameters.setFormat(Format.GZIP);

        ResultType result = action.transform(runner.actionContext(), compressParameters, input("fileA", "fileB"));

        verifyMultipleResults(result, Format.GZIP, "fileA.gz", "fileB.gz");
    }

    @Test
    public void compressesMultipleXz() {
        CompressParameters compressParameters = new CompressParameters();
        compressParameters.setFormat(Format.XZ);

        ResultType result = action.transform(runner.actionContext(), compressParameters, input("fileA", "fileB"));

        verifyMultipleResults(result, Format.XZ, "fileA.xz", "fileB.xz");
    }

    private void verifyMultipleResults(ResultType result, Format format, String fileA, String fileB) {
        assertTransformResult(result)
                .hasContentMatchingAt(0, actionContent -> {
                    ContentAssert.assertThat(actionContent)
                            .hasName(fileA)
                            .loadBytesIsEqualTo(runner.readResourceAsBytes(fileA))
                            .hasMediaType(format.getMediaType());
                    return true;
                })
                .hasContentMatchingAt(1, actionContent -> {
                    ContentAssert.assertThat(actionContent)
                            .hasName(fileB)
                            .loadBytesIsEqualTo(runner.readResourceAsBytes(fileB))
                            .hasMediaType(format.getMediaType());
                    return true;
                })
                .addedMetadata("compressFormat", format.getValue());
   }
}
