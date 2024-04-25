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
package org.deltafi.core.action.archive;

import org.deltafi.actionkit.action.ResultType;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.test.asserters.ContentAssert;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.MediaType;
import java.util.List;

import static org.deltafi.test.asserters.ActionResultAssertions.assertTransformResult;

public class UnarchiveTest {
    private final Unarchive action = new Unarchive();
    private final DeltaFiTestRunner runner = DeltaFiTestRunner.setup(action, "UnarchiveTest");

    @Test
    public void unarchivesAr() {
        runTest(ArchiveType.AR, false);
    }

    @Test
    public void unarchivesTar() {
        runTest(ArchiveType.TAR, false);
    }

    @Test
    public void unarchivesTarGzip() {
        runTest(ArchiveType.TAR_GZIP, false);
    }

    @Test
    public void unarchivesTarXz() {
        runTest(ArchiveType.TAR_XZ, false);
    }

    @Test
    public void unarchivesTarZ() {
        runTest(ArchiveType.TAR_Z, false);
    }

    @Test
    public void unarchivesZip() {
        runTest(ArchiveType.ZIP, false);
    }

    @Test
    public void unarchivesArDetected() {
        runTest(ArchiveType.AR, true);
    }

    @Test
    public void unarchivesTarDetected() {
        runTest(ArchiveType.TAR, true);
    }

    @Test
    public void unarchivesTarGzipDetected() {
        runTest(ArchiveType.TAR_GZIP, true);
    }

    @Test
    public void unarchivesTarXzDetected() {
        runTest(ArchiveType.TAR_XZ, true);
    }

    @Test
    public void unarchivesTarZDetected() {
        runTest(ArchiveType.TAR_Z, true);
    }

    @Test
    public void unarchivesZipDetected() {
        runTest(ArchiveType.ZIP, true);
    }

    private void runTest(ArchiveType archiveType, boolean detected) {
        ResultType result = action.transform(runner.actionContext(),
                new UnarchiveParameters(detected ? null : archiveType), input("archive." + archiveType.getValue()));

        verifyTransform(result, archiveType);
    }

    private TransformInput input(String... files) {
        return TransformInput.builder().content(runner.saveContentFromResource(files)).build();
    }

    private static final String FILE1 = "thing1.txt";
    private static final String FILE2 = "thing2.txt";

    private void verifyTransform(ResultType result, ArchiveType archiveType) {
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
                .addedMetadata("archiveType", archiveType.getValue());
    }
}
