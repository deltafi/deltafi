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
import org.deltafi.test.content.DeltaFiTestRunner;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.deltafi.test.asserters.ActionResultAssertions.assertTransformResult;

public class DecompressTest {
    private final Decompress action = new Decompress();
    private final DeltaFiTestRunner runner = DeltaFiTestRunner.setup(action, "DecompressTest");

    @Test
    void decompressesSingleGzip() {
        ResultType result = action.transform(runner.actionContext(),
                new DecompressParameters(CompressType.GZIP), input("fileA.gz"));

        assertTransformResult(result)
                .contentLoadBytesEquals(List.of(runner.readResourceAsBytes("fileA")))
                .addedMetadata("compressType", CompressType.GZIP.getValue());
    }

    @Test
    void decompressesMultipleGzip() {
        ResultType result = action.transform(runner.actionContext(),
                new DecompressParameters(CompressType.GZIP), input("fileA.gz", "fileB.gz"));

        assertTransformResult(result)
                .contentLoadBytesEquals(List.of(runner.readResourceAsBytes("fileA"),
                        runner.readResourceAsBytes("fileB")))
                .addedMetadata("compressType", CompressType.GZIP.getValue());
    }

    @Test
    void decompressesSingleXz() {
        ResultType result = action.transform(runner.actionContext(),
                new DecompressParameters(CompressType.XZ), input("fileA.xz"));

        assertTransformResult(result)
                .contentLoadBytesEquals(List.of(runner.readResourceAsBytes("fileA")))
                .addedMetadata("compressType", CompressType.XZ.getValue());
    }

    @Test
    void decompressesSingleZ() {
        ResultType result = action.transform(runner.actionContext(),
                new DecompressParameters(CompressType.Z), input("fileC.Z"));

        assertTransformResult(result)
                .contentLoadBytesEquals(List.of(runner.readResourceAsBytes("fileC")))
                .addedMetadata("compressType", CompressType.Z.getValue());
    }

    @Test
    void decompressesSingleGzipDetected() {
        ResultType result = action.transform(runner.actionContext(),
                new DecompressParameters(), input("fileA.gz"));

        assertTransformResult(result)
                .contentLoadBytesEquals(List.of(runner.readResourceAsBytes("fileA")))
                .addedMetadata("compressType", CompressType.GZIP.getValue());
    }

    @Test
    void decompressesSingleXzDetected() {
        ResultType result = action.transform(runner.actionContext(),
                new DecompressParameters(), input("fileA.xz"));

        assertTransformResult(result)
                .contentLoadBytesEquals(List.of(runner.readResourceAsBytes("fileA")))
                .addedMetadata("compressType", CompressType.XZ.getValue());
    }

    @Test
    void decompressesSingleZDetected() {
        ResultType result = action.transform(runner.actionContext(),
                new DecompressParameters(), input("fileC.Z"));

        assertTransformResult(result)
                .contentLoadBytesEquals(List.of(runner.readResourceAsBytes("fileC")))
                .addedMetadata("compressType", CompressType.Z.getValue());
    }

    private TransformInput input(String... files) {
        return TransformInput.builder().content(runner.saveContentFromResource(files)).build();
    }
}
