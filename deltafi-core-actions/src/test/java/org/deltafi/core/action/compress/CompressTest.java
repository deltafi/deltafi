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
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.test.asserters.ContentAssert;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.MediaType;

import static org.deltafi.test.asserters.ActionResultAssertions.assertTransformResult;
import static org.deltafi.test.asserters.ActionResultAssertions.assertTransformResults;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class CompressTest {
    private final Compress action = new Compress();
    private final DeltaFiTestRunner runner = DeltaFiTestRunner.setup(action, "CompressTest");

    @Test
    public void compressesSingleGzip() {
        ResultType result = action.transform(runner.actionContext(),
                new CompressParameters(CompressType.GZIP, null), input("fileA"));

        verifySingleResult(result, CompressType.GZIP, "fileA.gz", CompressType.GZIP.getMediaType());
    }

    @Test
    public void compressesSingleXz() {
        ResultType result = action.transform(runner.actionContext(),
                new CompressParameters(CompressType.XZ, null), input("fileA"));

        verifySingleResult(result, CompressType.XZ, "fileA.xz", CompressType.XZ.getMediaType());
    }

    @Test
    public void compressesSingleGzipWithMediaType() {
        ResultType result = action.transform(runner.actionContext(),
                new CompressParameters(CompressType.GZIP, MediaType.APPLICATION_OCTET_STREAM), input("fileA"));

        verifySingleResult(result, CompressType.GZIP, "fileA.gz", MediaType.APPLICATION_OCTET_STREAM);
    }

    private TransformInput input(String... files) {
        return TransformInput.builder().content(runner.saveContentFromResource(files)).build();
    }

    private void verifySingleResult(ResultType result, CompressType compressType, String file, String mediaType) {
        assertTransformResult(result)
                .hasContentMatching(actionContent -> {
                    ContentAssert.assertThat(actionContent)
                            .loadBytesIsEqualTo(runner.readResourceAsBytes(file))
                            .hasMediaType(mediaType);
                    return true;
                })
                .addedMetadata("compressType", compressType.getValue());
    }

    @Test
    public void compressesMultipleGzip() {
        ResultType result = action.transform(runner.actionContext(),
                new CompressParameters(CompressType.GZIP, null), input("fileA", "fileB"));

        verifyMultipleResults(result, CompressType.GZIP, "fileA.gz", "fileB.gz");
    }

    @Test
    public void compressesMultipleXz() {
        ResultType result = action.transform(runner.actionContext(),
                new CompressParameters(CompressType.XZ, null), input("fileA", "fileB"));

        verifyMultipleResults(result, CompressType.XZ, "fileA.xz", "fileB.xz");
    }

    private void verifyMultipleResults(ResultType result, CompressType compressType, String fileA, String fileB) {
        assertTransformResults(result)
                .hasChildrenSize(2)
                .hasChildResultAt(0, child -> {
                    ContentAssert.assertThat(child.getContent().getFirst())
                            .loadBytesIsEqualTo(runner.readResourceAsBytes(fileA))
                            .hasMediaType(compressType.getMediaType());
                    assertEquals(compressType.getValue(), child.getMetadata().get("compressType"));
                    return true;
                })
                .hasChildResultAt(1, child -> {
                    ContentAssert.assertThat(child.getContent().getFirst())
                            .loadBytesIsEqualTo(runner.readResourceAsBytes(fileB))
                            .hasMediaType(compressType.getMediaType());
                    assertEquals(compressType.getValue(), child.getMetadata().get("compressType"));
                    return true;
                });
    }
}
