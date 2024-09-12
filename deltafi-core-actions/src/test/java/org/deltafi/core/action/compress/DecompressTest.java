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
package org.deltafi.core.action.compress;

import org.deltafi.actionkit.action.ResultType;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.test.asserters.ContentAssert;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.List;

import static org.deltafi.test.asserters.ActionResultAssertions.assertTransformResult;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class DecompressTest {
    private static final String FILE1 = "thing1.txt";
    private static final String FILE2 = "thing2.txt";
    private final Decompress action = new Decompress();
    private final DeltaFiTestRunner runner = DeltaFiTestRunner.setup("DecompressTest");

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

    @Test
    public void retainAndUnarchivesZipDetected() {
        runTest(Format.ZIP, true, true);
    }

    private void runTest(Format archiveType, boolean detected) {
        runTest(archiveType, detected, false);
    }

    private void runTest(Format archiveType, boolean detected, boolean retainExistingContent) {
        String inputName = "compressed." + archiveType.getValue();
        ResultType result = action.transform(runner.actionContext(),
                new DecompressParameters(detected ? null : archiveType, retainExistingContent),
                input(inputName));

        verifyTransform(result, archiveType, retainExistingContent, inputName);
    }

    private TransformInput input(String... files) {
        return TransformInput.builder().content(runner.saveContentFromResource(files)).build();
    }

    private void verifyTransform(ResultType result, Format archiveType, boolean retainExistingContent, String originalInputName) {
        int startIdx = retainExistingContent ? 1 : 0;

        assertTransformResult(result)
                .hasContentMatchingAt(startIdx, actionContent -> {
                    ContentAssert.assertThat(actionContent)
                            .hasName(FILE1)
                            .hasMediaType(MediaType.APPLICATION_OCTET_STREAM);
                    return true;
                })
                .hasContentMatchingAt(startIdx + 1, actionContent -> {
                    ContentAssert.assertThat(actionContent)
                            .hasName(FILE2)
                            .hasMediaType(MediaType.APPLICATION_OCTET_STREAM);
                    return true;
                })
                .addedMetadata("compressFormat", archiveType.getValue());

        if (retainExistingContent) {
            assertTransformResult(result)
                    .hasContentMatchingAt(0, actionContent -> {
                        ContentAssert.assertThat(actionContent)
                                .hasName(originalInputName);
                        return true;
                    })
                    .contentLoadBytesEquals(List.of(
                            runner.readResourceAsBytes(originalInputName),
                            runner.readResourceAsBytes(FILE1),
                            runner.readResourceAsBytes(FILE2)));
        } else {
            assertTransformResult(result)
                    .contentLoadBytesEquals(List.of(
                            runner.readResourceAsBytes(FILE1),
                            runner.readResourceAsBytes(FILE2)));
        }
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
    public void retainAndDecompressesMultipleGzip() {
        runTest(Format.GZIP, true, input("fileA.gz", "fileB.gz"), "fileA", "fileB");
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
        runTest(compressFormat, false, input, outputFiles);
    }

    private void runTest(Format compressFormat, boolean retainExistingContent, TransformInput input, String... outputFiles) {
        runTest(new DecompressParameters(compressFormat, retainExistingContent), compressFormat, input, outputFiles);
    }

    private void runTest(DecompressParameters parameters, Format expectedCompressFormat, TransformInput input, String... outputFiles) {
        ResultType result = action.transform(runner.actionContext(), parameters, input);
        List<String> names = new ArrayList<>();
        if (parameters.retainExistingContent) {
            names.addAll(input.getContent().stream().map(ActionContent::getName).toList());
        }
        names.addAll(List.of(outputFiles));

        assertTransformResult(result)
                .contentLoadBytesEquals(names.stream().map(runner::readResourceAsBytes).toList())
                .addedMetadata("compressFormat", expectedCompressFormat.getValue());
    }
}
