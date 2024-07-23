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
package org.deltafi.core.action.compress;

import org.deltafi.actionkit.action.ResultType;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.test.asserters.ContentAssert;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.MediaType;
import java.util.Arrays;
import java.util.List;

import static org.deltafi.test.asserters.ActionResultAssertions.assertTransformResult;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class DecompressTest {
    private final Decompress action = new Decompress();
    private final DeltaFiTestRunner runner = DeltaFiTestRunner.setup(action, "DecompressTest");

    @Test
    public void errorResultOnNoContent() {
        ResultType result = action.transform(runner.actionContext(), new DecompressParameters(),
                TransformInput.builder().build());

        assertInstanceOf(ErrorResult.class, result);
    }

    @Test
    public void unarchivesAr() {
        runTest(Format.AR, false);
    }

    @Test
    public void unarchivesTar() {
        runTest(Format.TAR, false);
    }

    @Test
    public void unarchivesTarGzip() {
        runTest(Format.TAR_GZIP, false);
    }

    @Test
    public void unarchivesTarXz() {
        runTest(Format.TAR_XZ, false);
    }

    @Test
    public void unarchivesTarZ() {
        runTest(Format.TAR_Z, false);
    }

    @Test
    public void unarchivesZip() {
        runTest(Format.ZIP, false);
    }

    @Test
    public void unarchivesArDetected() {
        runTest(Format.AR, true);
    }

    @Test
    public void unarchivesTarDetected() {
        runTest(Format.TAR, true);
    }

    @Test
    public void unarchivesTarGzipDetected() {
        runTest(Format.TAR_GZIP, true);
    }

    @Test
    public void unarchivesTarXzDetected() {
        runTest(Format.TAR_XZ, true);
    }

    @Test
    public void unarchivesTarZDetected() {
        runTest(Format.TAR_Z, true);
    }

    @Test
    public void unarchivesZipDetected() {
        runTest(Format.ZIP, true);
    }

    private void runTest(Format archiveType, boolean detected) {
        ResultType result = action.transform(runner.actionContext(),
                new DecompressParameters(detected ? null : archiveType), input("compressed." + archiveType.getValue()));

        verifyTransform(result, archiveType);
    }

    private TransformInput input(String... files) {
        return TransformInput.builder().content(runner.saveContentFromResource(files)).build();
    }

    private static final String FILE1 = "thing1.txt";
    private static final String FILE2 = "thing2.txt";

    private void verifyTransform(ResultType result, Format archiveType) {
        assertTransformResult(result)
                .hasContentMatchingAt(0, actionContent -> {
                    ContentAssert.assertThat(actionContent)
                            .hasName(FILE1)
                            .hasMediaType(MediaType.APPLICATION_OCTET_STREAM);
                    return true;
                })
                .hasContentMatchingAt(1, actionContent -> {
                    ContentAssert.assertThat(actionContent)
                            .hasName(FILE2)
                            .hasMediaType(MediaType.APPLICATION_OCTET_STREAM);
                    return true;
                })
                .contentLoadBytesEquals(List.of(runner.readResourceAsBytes(FILE1),
                        runner.readResourceAsBytes(FILE2)))
                .addedMetadata("compressFormat", archiveType.getValue());
    }

    @Test
    public void decompressesSingleGzip() {
        runTest(Format.GZIP, input("fileA.gz"), "fileA");
    }

    @Test
    public void decompressesMultipleGzip() {
        runTest(Format.GZIP, input("fileA.gz", "fileB.gz"), "fileA", "fileB");
    }

    @Test
    public void decompressesSingleXz() {
        runTest(Format.XZ, input("fileA.xz"), "fileA");
    }

    @Test
    public void decompressesSingleZ() {
        runTest(Format.Z, input("fileC.Z"), "fileC");
    }

    @Test
    public void decompressesSingleGzipDetected() {
        runTest(new DecompressParameters(), Format.GZIP, input("fileA.gz"), "fileA");
    }

    @Test
    public void decompressesSingleXzDetected() {
        runTest(new DecompressParameters(), Format.XZ, input("fileA.xz"), "fileA");
    }

   @Test
    public void decompressesSingleZDetected() {
        runTest(new DecompressParameters(), Format.Z, input("fileC.Z"), "fileC");
    }

    private void runTest(Format compressFormat, TransformInput input, String... outputFiles) {
        runTest(new DecompressParameters(compressFormat), compressFormat, input, outputFiles);
    }

    private void runTest(DecompressParameters parameters, Format expectedCompressFormat, TransformInput input, String... outputFiles) {
        ResultType result = action.transform(runner.actionContext(), parameters, input);

        assertTransformResult(result)
                .contentLoadBytesEquals(Arrays.stream(outputFiles).map(runner::readResourceAsBytes).toList())
                .addedMetadata("compressFormat", expectedCompressFormat.getValue());
    }
}
