/**
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

import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.core.parameters.DecompressionType;
import org.deltafi.test.action.IOContent;
import org.deltafi.test.action.transform.TransformActionTest;
import org.deltafi.test.action.transform.TransformActionTestCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

@ExtendWith(MockitoExtension.class)
public class DecompressionTransformActionTest extends TransformActionTest {
    private static final String CONTENT_TYPE = "application/octet-stream";

    @InjectMocks
    DecompressionTransformAction action;

    private List<IOContent> getTarResult() {
        return List.of(
                IOContent.builder().name("thing1.txt").contentType(CONTENT_TYPE).metadata(Map.of("lastModified", convertUTCDateToLocal("Wed Mar 09 22:33:51 UTC 2022"))).build(),
                IOContent.builder().name("thing2.txt").contentType(CONTENT_TYPE).metadata(Map.of("lastModified", convertUTCDateToLocal("Wed Mar 09 22:33:49 UTC 2022"))).build());
    }

    private List<IOContent> getZipResult() {
        return List.of(
                IOContent.builder().name("thing1.txt").contentType(CONTENT_TYPE).metadata(Map.of("lastModified", convertUTCDateToLocal("Wed Mar 09 22:37:40 UTC 2022"))).build(),
                IOContent.builder().name("thing2.txt").contentType(CONTENT_TYPE).metadata(Map.of("lastModified", convertUTCDateToLocal("Wed Mar 09 22:37:45 UTC 2022"))).build()
        );
    }

    private List<IOContent> getArResult() {
        return List.of(
                IOContent.builder().name("thing1.txt").contentType(CONTENT_TYPE).metadata(Map.of("lastModified", convertUTCDateToLocal("Fri Apr 01 20:10:09 UTC 2022"))).build(),
                IOContent.builder().name("thing2.txt").contentType(CONTENT_TYPE).metadata(Map.of("lastModified", convertUTCDateToLocal("Wed Mar 09 22:33:49 UTC 2022"))).build()
        );
    }

    @Test
    public void decompressTarGz() {
        execute(TransformActionTestCase.builder()
                .action(action)
                .parameters(Map.of("decompressionType", DecompressionType.TAR_GZIP))
                .testName("decompressTarGz")
                .inputs(Collections.singletonList(IOContent.builder().name("things.tar.gz").contentType(CONTENT_TYPE).build()))
                .resultMetadata(Map.of("decompressionType", "tar.gz"))
                .expectTransformResult(getTarResult())
                .build());
    }

    @Test
    public void autoDecompressTarGz() {
        execute(TransformActionTestCase.builder()
                .action(action)
                .testName("autoDecompressTarGz")
                .parameters(Map.of("decompressionType", DecompressionType.AUTO))
                .inputs(Collections.singletonList(IOContent.builder().name("things.tar.gz").contentType(CONTENT_TYPE).build()))
                .resultMetadata(Map.of("decompressionType", "tar.gz"))
                .expectTransformResult(getTarResult())
                .build());
    }

    @Test
    public void autoDecompressTarZ() {
        execute(TransformActionTestCase.builder()
                .action(action)
                .testName("autoDecompressTarZ")
                .parameters(Map.of("decompressionType", DecompressionType.AUTO))
                .inputs(Collections.singletonList(IOContent.builder().name("things.tar.Z").contentType(CONTENT_TYPE).build()))
                .resultMetadata(Map.of("decompressionType", "tar.z"))
                .expectTransformResult(getTarResult())
                .build());
    }

    @Test
    public void autoDecompressTarXZ() {
        execute(TransformActionTestCase.builder()
                .action(action)
                .testName("autoDecompressTarXZ")
                .parameters(Map.of("decompressionType", DecompressionType.AUTO))
                .inputs(Collections.singletonList(IOContent.builder().name("things.tar.xz").contentType(CONTENT_TYPE).build()))
                .resultMetadata(Map.of("decompressionType", "tar.xz"))
                .expectTransformResult(getTarResult())
                .build());
    }


    @Test
    public void decompressTarXZ() {
        execute(TransformActionTestCase.builder()
                .action(action)
                .testName("decompressTarXZ")
                .parameters(Map.of("decompressionType", DecompressionType.TAR_XZ))
                .inputs(Collections.singletonList(IOContent.builder().name("things.tar.xz").contentType(CONTENT_TYPE).build()))
                .resultMetadata(Map.of("decompressionType", "tar.xz"))
                .expectTransformResult(getTarResult())
                .build());
    }

    @Test
    public void decompressTarZ() {
        execute(TransformActionTestCase.builder()
                .action(action)
                .testName("decompressTarZ")
                .parameters(Map.of("decompressionType", DecompressionType.TAR_Z))
                .inputs(Collections.singletonList(IOContent.builder().name("things.tar.Z").contentType(CONTENT_TYPE).build()))
                .resultMetadata(Map.of("decompressionType", "tar.z"))
                .expectTransformResult(getTarResult())
                .build());
    }

    @Test
    public void decompressAR() {
        execute(TransformActionTestCase.builder()
                .action(action)
                .testName("decompressAR")
                .parameters(Map.of("decompressionType", DecompressionType.AR))
                .inputs(Collections.singletonList(IOContent.builder().name("things.ar").contentType(CONTENT_TYPE).build()))
                .resultMetadata(Map.of("decompressionType", "ar"))
                .expectTransformResult(getArResult())
                .build());
    }

    @Test
    public void autoDecompressAR() {
        execute(TransformActionTestCase.builder()
            .action(action)
            .testName("autoDecompressAR")
            .parameters(Map.of("decompressionType", DecompressionType.AUTO))
            .inputs(Collections.singletonList(IOContent.builder().name("things.ar").contentType(CONTENT_TYPE).build()))
            .resultMetadata(Map.of("decompressionType", "ar"))
            .expectTransformResult(getArResult())
            .build());
    }

    @Test
    public void autoDecompressZip() {
        execute(TransformActionTestCase.builder()
                .action(action)
                .testName("autoDecompressZip")
                .parameters(Map.of("decompressionType", DecompressionType.AUTO))
                .inputs(Collections.singletonList(IOContent.builder().name("things.zip").contentType(CONTENT_TYPE).build()))
                .resultMetadata(Map.of("decompressionType", "zip"))
                .expectTransformResult(getZipResult())
                .build());
    }

    @Test
    public void autoDecompressTar() {
        execute(TransformActionTestCase.builder()
                .action(action)
                .testName("autoDecompressTar")
                .parameters(Map.of("decompressionType", DecompressionType.AUTO))
                .inputs(Collections.singletonList(IOContent.builder().name("things.tar").contentType(CONTENT_TYPE).build()))
                .resultMetadata(Map.of("decompressionType", "tar"))
                .expectTransformResult(getTarResult())
                .build());
    }

    @Test
    public void autoDecompressGzip() {
        execute(TransformActionTestCase.builder()
                .action(action)
                .testName("autoDecompressGzip")
                .parameters(Map.of("decompressionType", DecompressionType.AUTO))
                .inputs(Collections.singletonList(IOContent.builder().name("thing1.txt.gz").contentType(CONTENT_TYPE).build()))
                .resultMetadata(Map.of("decompressionType", "gz"))
                .expectTransformResult(Collections.singletonList(
                        IOContent.builder().name("output.thing1.txt.gz").contentType(CONTENT_TYPE).build()
                ))
                .build());
    }

    @Test
    void decompressZip() {
        execute(TransformActionTestCase.builder()
                .action(action)
                .testName("decompressZip")
                .parameters(Map.of("decompressionType", DecompressionType.ZIP))
                .inputs(Collections.singletonList(IOContent.builder().name("things.zip").contentType(CONTENT_TYPE).build()))
                .resultMetadata(Map.of("decompressionType", "zip"))
                .expectTransformResult(getZipResult())
                .build());
    }

    @Test
    void decompressGzip() {
        execute(TransformActionTestCase.builder()
                .action(action)
                .testName("decompressGzip")
                .parameters(Map.of("decompressionType", DecompressionType.GZIP))
                .inputs(Collections.singletonList(IOContent.builder().name("thing1.txt.gz").contentType(CONTENT_TYPE).build()))
                .resultMetadata(Map.of("decompressionType", "gz"))
                .expectTransformResult(Collections.singletonList(
                        IOContent.builder().name("output.thing1.txt.gz").contentType(CONTENT_TYPE).build()
                ))
                .build());
    }

    @Test
    void autoDecompressXZ() {
        execute(TransformActionTestCase.builder()
                .action(action)
                .testName("autoDecompressXZ")
                .parameters(Map.of("decompressionType", DecompressionType.AUTO))
                .inputs(Collections.singletonList(IOContent.builder().name("thing1.txt.xz").contentType(CONTENT_TYPE).build()))
                .resultMetadata(Map.of("decompressionType", "xz"))
                .expectTransformResult(Collections.singletonList(
                        IOContent.builder().name("output.thing1.txt.xz").contentType(CONTENT_TYPE).build()
                ))
                .build());
    }

    @Test
    void decompressXZ() {
        execute(TransformActionTestCase.builder()
                .action(action)
                .testName("decompressXZ")
                .parameters(Map.of("decompressionType", DecompressionType.XZ))
                .inputs(Collections.singletonList(IOContent.builder().name("thing1.txt.xz").contentType(CONTENT_TYPE).build()))
                .resultMetadata(Map.of("decompressionType", "xz"))
                .expectTransformResult(Collections.singletonList(
                        IOContent.builder().name("output.thing1.txt.xz").contentType(CONTENT_TYPE).build()
                ))
                .build());
    }

    @Test
    void decompressZ() {
        execute(TransformActionTestCase.builder()
                .action(action)
                .testName("decompressZ")
                .parameters(Map.of("decompressionType", DecompressionType.Z))
                .inputs(Collections.singletonList(IOContent.builder().name("thing1.txt.Z").contentType(CONTENT_TYPE).build()))
                .resultMetadata(Map.of("decompressionType", "z"))
                .expectTransformResult(Collections.singletonList(
                        IOContent.builder().name("output.thing1.txt.Z").contentType(CONTENT_TYPE).build()
                ))
                .build());
    }

    @Test
    void autoDecompressZ() {
        execute(TransformActionTestCase.builder()
                .action(action)
                .testName("autoDecompressZ")
                .parameters(Map.of("decompressionType", DecompressionType.Z))
                .inputs(Collections.singletonList(IOContent.builder().name("thing1.txt.Z").contentType(CONTENT_TYPE).build()))
                .resultMetadata(Map.of("decompressionType", "z"))
                .expectTransformResult(Collections.singletonList(
                        IOContent.builder().name("output.thing1.txt.Z").contentType(CONTENT_TYPE).build()
                ))
                .build());
    }

    @Test
    void unarchiveTar() {
        execute(TransformActionTestCase.builder()
                .action(action)
                .testName("unarchiveTar")
                .parameters(Map.of("decompressionType", DecompressionType.TAR))
                .inputs(Collections.singletonList(IOContent.builder().name("things.tar").contentType(CONTENT_TYPE).build()))
                .resultMetadata(Map.of("decompressionType", "tar"))
                .expectTransformResult(getTarResult())
                .build());
    }

    @Test
    void autoUnarchiveTarZ() {
        execute(TransformActionTestCase.builder()
                .action(action)
                .testName("autoUnarchiveTarZ")
                .parameters(Map.of("decompressionType", DecompressionType.AUTO))
                .inputs(Collections.singletonList(IOContent.builder().name("things.tar.Z").contentType(CONTENT_TYPE).build()))
                .resultMetadata(Map.of("decompressionType", "tar.z"))
                .expectTransformResult(getTarResult())
                .build());
    }

    @Test
    void autoUnarchiveTarXZ() {
        execute(TransformActionTestCase.builder()
                .action(action)
                .testName("autoUnarchiveTarXZ")
                .parameters(Map.of("decompressionType", DecompressionType.AUTO))
                .inputs(Collections.singletonList(IOContent.builder().name("things.tar.xz").contentType(CONTENT_TYPE).build()))
                .resultMetadata(Map.of("decompressionType", "tar.xz"))
                .expectTransformResult(getTarResult())
                .build());
    }

    @Test
    void unarchiveSubdirectoryTar() {
        execute(TransformActionTestCase.builder()
                .action(action)
                .testName("unarchiveSubdirectoryTar")
                .parameters(Map.of("decompressionType", DecompressionType.TAR))
                .inputs(Collections.singletonList(IOContent.builder().name("foobar.tar").contentType(CONTENT_TYPE).build()))
                .resultMetadata(Map.of("decompressionType", "tar"))
                .expectTransformResult(Arrays.asList(
                        IOContent.builder().name("foo/1/baz").contentType(CONTENT_TYPE).metadata(Map.of("lastModified", convertUTCDateToLocal("Fri Apr 01 16:21:41 UTC 2022"))).build(),
                        IOContent.builder().name("foo/2/baz").contentType(CONTENT_TYPE).metadata(Map.of("lastModified", convertUTCDateToLocal("Fri Apr 01 16:21:41 UTC 2022"))).build(),
                        IOContent.builder().name("foo/3/baz").contentType(CONTENT_TYPE).metadata(Map.of("lastModified", convertUTCDateToLocal("Fri Apr 01 16:21:41 UTC 2022"))).build(),
                        IOContent.builder().name("bar/1/baz").contentType(CONTENT_TYPE).metadata(Map.of("lastModified", convertUTCDateToLocal("Fri Apr 01 16:21:41 UTC 2022"))).build(),
                        IOContent.builder().name("bar/2/baz").contentType(CONTENT_TYPE).metadata(Map.of("lastModified", convertUTCDateToLocal("Fri Apr 01 16:21:41 UTC 2022"))).build(),
                        IOContent.builder().name("bar/3/baz").contentType(CONTENT_TYPE).metadata(Map.of("lastModified", convertUTCDateToLocal("Fri Apr 01 16:21:41 UTC 2022"))).build()
                ))
                .build());
    }

    @Test
    void unarchiveSubdirectoryZip() {
        execute(TransformActionTestCase.builder()
                .action(action)
                .testName("unarchiveSubdirectoryZip")
                .parameters(Map.of("decompressionType", DecompressionType.ZIP))
                .inputs(Collections.singletonList(IOContent.builder().name("foobar.zip").contentType(CONTENT_TYPE).build()))
                .resultMetadata(Map.of("decompressionType", "zip"))
                .expectTransformResult(Arrays.asList(
                        IOContent.builder().name("foo/1/baz").contentType(CONTENT_TYPE).metadata(Map.of("lastModified", convertUTCDateToLocal("Fri Apr 01 16:21:41 UTC 2022"))).build(),
                        IOContent.builder().name("foo/2/baz").contentType(CONTENT_TYPE).metadata(Map.of("lastModified", convertUTCDateToLocal("Fri Apr 01 16:21:41 UTC 2022"))).build(),
                        IOContent.builder().name("foo/3/baz").contentType(CONTENT_TYPE).metadata(Map.of("lastModified", convertUTCDateToLocal("Fri Apr 01 16:21:41 UTC 2022"))).build(),
                        IOContent.builder().name("bar/1/baz").contentType(CONTENT_TYPE).metadata(Map.of("lastModified", convertUTCDateToLocal("Fri Apr 01 16:21:41 UTC 2022"))).build(),
                        IOContent.builder().name("bar/2/baz").contentType(CONTENT_TYPE).metadata(Map.of("lastModified", convertUTCDateToLocal("Fri Apr 01 16:21:41 UTC 2022"))).build(),
                        IOContent.builder().name("bar/3/baz").contentType(CONTENT_TYPE).metadata(Map.of("lastModified", convertUTCDateToLocal("Fri Apr 01 16:21:41 UTC 2022"))).build()
                ))
                .build());
    }

    @Test
    void unarchiveSubdirectoryAutoZip() {
        execute(TransformActionTestCase.builder()
                .action(action)
                .testName("unarchiveSubdirectoryAutoZip")
                .parameters(Map.of("decompressionType", DecompressionType.AUTO))
                .inputs(Collections.singletonList(IOContent.builder().name("foobar.zip").contentType(CONTENT_TYPE).build()))
                .resultMetadata(Map.of("decompressionType", "zip"))
                .expectTransformResult(Arrays.asList(
                        IOContent.builder().name("foo/1/baz").contentType(CONTENT_TYPE).metadata(Map.of("lastModified", convertUTCDateToLocal("Fri Apr 01 16:21:41 UTC 2022"))).build(),
                        IOContent.builder().name("foo/2/baz").contentType(CONTENT_TYPE).metadata(Map.of("lastModified", convertUTCDateToLocal("Fri Apr 01 16:21:41 UTC 2022"))).build(),
                        IOContent.builder().name("foo/3/baz").contentType(CONTENT_TYPE).metadata(Map.of("lastModified", convertUTCDateToLocal("Fri Apr 01 16:21:41 UTC 2022"))).build(),
                        IOContent.builder().name("bar/1/baz").contentType(CONTENT_TYPE).metadata(Map.of("lastModified", convertUTCDateToLocal("Fri Apr 01 16:21:41 UTC 2022"))).build(),
                        IOContent.builder().name("bar/2/baz").contentType(CONTENT_TYPE).metadata(Map.of("lastModified", convertUTCDateToLocal("Fri Apr 01 16:21:41 UTC 2022"))).build(),
                        IOContent.builder().name("bar/3/baz").contentType(CONTENT_TYPE).metadata(Map.of("lastModified", convertUTCDateToLocal("Fri Apr 01 16:21:41 UTC 2022"))).build()
                ))
                .build());
    }

    @Test
    void decompressionTypeMismatch() {
        execute(TransformActionTestCase.builder()
                .action(action)
                .testName("decompressionTypeMismatch")
                .parameters(Map.of("decompressionType", DecompressionType.ZIP))
                .inputs(Collections.singletonList(IOContent.builder().name("things.tar.gz").contentType(CONTENT_TYPE).build()))
                .expectError("Unable to decompress zip")
                .build());
    }

    @Test
    void truncatedFile() {
        execute(TransformActionTestCase.builder()
                .action(action)
                .testName("truncatedFile")
                .parameters(Map.of("decompressionType", DecompressionType.TAR_GZIP))
                .inputs(Collections.singletonList(IOContent.builder().name("bad.things.tar.gz").contentType(CONTENT_TYPE).build()))
                .expectError("Unable to unarchive tar")
                .build());
    }

    @Test
    void autoNoCompression() {
        execute(TransformActionTestCase.builder()
                .action(action)
                .testName("autoNoCompression")
                .parameters(Map.of("decompressionType", DecompressionType.AUTO))
                .inputs(Collections.singletonList(IOContent.builder().name("thing1.txt").contentType(CONTENT_TYPE).build()))
                .expectError("No compression or archive formats detected")
                .build());
    }

    @Test
    void loadFailure() {
        execute(TransformActionTestCase.builder()
                .action(action)
                .testName("loadFailure")
                .parameters(Map.of("decompressionType", DecompressionType.AUTO))
                .inputs(Collections.singletonList(IOContent.builder().name("things.tar.gz").contentType(CONTENT_TYPE).build()))
                .throwStorageReadException(new ObjectStorageException("Boom", new Exception()))
                .expectError("Failed to load compressed binary from storage")
                .build());
    }

    @Test
    void storeFailure() {
        execute(TransformActionTestCase.builder()
                .action(action)
                .testName("storeFailure")
                .parameters(Map.of("decompressionType", DecompressionType.TAR_GZIP))
                .inputs(Collections.singletonList(IOContent.builder().name("things.tar.gz").contentType(CONTENT_TYPE).build()))
                .throwStorageWriteException(new ObjectStorageException("Boom", new Exception()))
                .expectError("Unable to store content")
                .build());
    }
}
