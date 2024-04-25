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
import org.deltafi.common.test.time.TestClock;
import org.deltafi.test.asserters.ContentAssert;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.MediaType;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.deltafi.test.asserters.ActionResultAssertions.assertTransformResult;
import static org.junit.jupiter.api.Assertions.fail;

public class ArchiveTest {
    private final TestClock testClock = new TestClock();
    private final Archive action = new Archive(testClock);
    private final DeltaFiTestRunner runner = DeltaFiTestRunner.setup(action, "ArchiveTest");

    @Test
    public void archivesAr() {
        runTest(ArchiveType.AR);
    }

    @Test
    public void archivesTar() {
        runTest(ArchiveType.TAR);
    }

    @Test
    public void archivesTarGzip() {
        runTest(ArchiveType.TAR_GZIP);
    }

    @Test
    public void archivesTarXz() {
        runTest(ArchiveType.TAR_XZ);
    }

    @Test
    public void archivesZip() {
        runTest(ArchiveType.ZIP);
    }

    private void runTest(ArchiveType archiveType) {
        TransformInput transformInput = input("thing1.txt", "thing2.txt");

        ResultType result = action.transform(runner.actionContext(),
                new ArchiveParameters(archiveType, null, true), transformInput);

        verifyFormat(result, archiveType, transformInput, "archive." + archiveType.getValue(),
                archiveType.getMediaType());
    }

    @Test
    public void archivesTarWithMediaTypeAndNoSuffix() {
        TransformInput transformInput = input("thing1.txt", "thing2.txt");

        ResultType result = action.transform(runner.actionContext(),
                new ArchiveParameters(ArchiveType.TAR, MediaType.APPLICATION_OCTET_STREAM, false), transformInput);

        verifyFormat(result, ArchiveType.TAR, transformInput, "archive", MediaType.APPLICATION_OCTET_STREAM);
    }

    private TransformInput input(String... files) {
        return TransformInput.builder().content(runner.saveContentFromResource(files)).build();
    }

    private void verifyFormat(ResultType result, ArchiveType archiveType, TransformInput transformInput, String name,
            String mediaType) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ArchiveWriter archiveWriter = new ArchiveWriter(transformInput.content(), archiveType, testClock);
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
                .addedMetadata("archiveType", archiveType.getValue());
    }
}
