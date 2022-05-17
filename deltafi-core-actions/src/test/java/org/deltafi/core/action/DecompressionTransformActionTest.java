/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
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

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.SneakyThrows;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.common.content.ContentReference;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.core.domain.api.types.*;
import org.deltafi.core.parameters.DecompressionTransformParameters;
import org.deltafi.core.parameters.DecompressionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import javax.ws.rs.core.MediaType;
import java.io.FilterInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.eq;


@ExtendWith(MockitoExtension.class)
public class DecompressionTransformActionTest {
    private static final String CONTENT_TYPE = "application/octet-stream";
    private static final String DID = UUID.randomUUID().toString();
    private static final String FLOW = "theFlow";
    private static final String ACTION_VERSION = "0.0";

    ActionContext ACTION_CONTEXT = ActionContext.builder()
            .actionVersion(ACTION_VERSION)
            .did(DID)
            .name("MyDecompressionTransformAction")
            .ingressFlow(FLOW)
            .build();

    @Mock
    private ContentStorageService contentStorageService;

    @InjectMocks
    DecompressionTransformAction action;

    @Test
    @SneakyThrows
    public void decompressTarGz() {
        decompressionTest(
                "things.tar.gz",
                DecompressionType.TAR_GZIP,
                List.of(
                        new ExpectedFile("thing1.txt", "thing1\n"),
                        new ExpectedFile("thing2.txt", "thing2\n")
                )
        );
    }

    @Test
    @SneakyThrows
    public void autoDecompressTarGz() {
        decompressionTest(
                "things.tar.gz",
                DecompressionType.AUTO,
                List.of(
                        new ExpectedFile("thing1.txt", "thing1\n"),
                        new ExpectedFile("thing2.txt", "thing2\n")
                ),
                "tar.gz"
        );
    }

    @Test
    @SneakyThrows
    public void autoDecompressTarZ() {
        decompressionTest(
                "things.tar.Z",
                DecompressionType.AUTO,
                List.of(
                        new ExpectedFile("thing1.txt", "thing1\n"),
                        new ExpectedFile("thing2.txt", "thing2\n")
                ),
                "tar.z"
        );
    }

    @Test
    @SneakyThrows
    public void autoDecompressTarXZ() {
        decompressionTest(
                "things.tar.xz",
                DecompressionType.AUTO,
                List.of(
                        new ExpectedFile("thing1.txt", "thing1\n"),
                        new ExpectedFile("thing2.txt", "thing2\n")
                ),
                "tar.xz"
        );
    }

    @Test
    @SneakyThrows
    public void decompressTarXZ() {
        decompressionTest(
                "things.tar.xz",
                DecompressionType.TAR_XZ,
                List.of(
                        new ExpectedFile("thing1.txt", "thing1\n"),
                        new ExpectedFile("thing2.txt", "thing2\n")
                ),
                "tar.xz"
        );
    }

    @Test
    @SneakyThrows
    public void decompressTarZ() {
        decompressionTest(
                "things.tar.Z",
                DecompressionType.TAR_Z,
                List.of(
                        new ExpectedFile("thing1.txt", "thing1\n"),
                        new ExpectedFile("thing2.txt", "thing2\n")
                ),
                "tar.z"
        );
    }

    @Test
    @SneakyThrows
    public void decompressAR() {
        decompressionTest(
                "things.ar",
                DecompressionType.AR,
                List.of(
                        new ExpectedFile("thing1.txt", "thing1\n"),
                        new ExpectedFile("thing2.txt", "thing2\n")
                )
        );
    }


    @Test
    @SneakyThrows
    public void autoDecompressAR() {
        decompressionTest(
                "things.ar",
                DecompressionType.AUTO,
                List.of(
                        new ExpectedFile("thing1.txt", "thing1\n"),
                        new ExpectedFile("thing2.txt", "thing2\n")
                ),
                "ar"
        );
    }

    @Test
    @SneakyThrows
    public void autoDecompressZip() {
        decompressionTest(
                "things.zip",
                DecompressionType.AUTO,
                List.of(
                        new ExpectedFile("thing1.txt", "thing1\n"),
                        new ExpectedFile("thing2.txt", "thing2\n")
                ),
                "zip"
        );
    }

    @Test
    @SneakyThrows
    public void autoDecompressTar() {
        decompressionTest(
                "things.tar",
                DecompressionType.AUTO,
                List.of(
                        new ExpectedFile("thing1.txt", "thing1\n"),
                        new ExpectedFile("thing2.txt", "thing2\n")
                ),
                "tar"
        );
    }

    @Test
    @SneakyThrows
    public void autoDecompressGzip() {
        decompressionTest(
                "thing1.txt.gz",
                DecompressionType.AUTO,
                List.of(
                        new ExpectedFile("thing1.txt.gz", "thing1\n")
                ),
                "gz"
        );
    }

    @Test
    @SneakyThrows
    public void decompressZip() {
        decompressionTest(
                "things.zip",
                DecompressionType.ZIP,
                List.of(
                        new ExpectedFile("thing1.txt", "thing1\n"),
                        new ExpectedFile("thing2.txt", "thing2\n")
                )
        );
    }

    @Test
    @SneakyThrows
    public void decompressGzip() {
        decompressionTest(
                "thing1.txt.gz",
                DecompressionType.GZIP,
                List.of(
                        new ExpectedFile("thing1.txt.gz", "thing1\n")
                )
        );
    }

    @Test
    @SneakyThrows
    public void autoDecompressXZ() {
        decompressionTest(
                "thing1.txt.xz",
                DecompressionType.AUTO,
                List.of(
                        new ExpectedFile("thing1.txt.xz", "thing1\n")
                ),
                "xz"
        );
    }

    @Test
    @SneakyThrows
    public void decompressXZ() {
        decompressionTest(
                "thing1.txt.xz",
                DecompressionType.XZ,
                List.of(
                        new ExpectedFile("thing1.txt.xz", "thing1\n")
                ),
                "xz"
        );
    }

    @Test
    @SneakyThrows
    public void decompressZ() {
        decompressionTest(
                "thing1.txt.Z",
                DecompressionType.Z,
                List.of(
                        new ExpectedFile("thing1.txt.Z", "thing1\n")
                )
        );
    }

    @Test
    @SneakyThrows
    public void autoDecompressZ() {
        decompressionTest(
                "thing1.txt.Z",
                DecompressionType.AUTO,
                List.of(
                        new ExpectedFile("thing1.txt.Z", "thing1\n")
                ),
                "z"
        );
    }

    @Test
    @SneakyThrows
    public void unarchiveTar() {
        decompressionTest(
                "things.tar",
                DecompressionType.TAR,
                List.of(
                        new ExpectedFile("thing1.txt", "thing1\n"),
                        new ExpectedFile("thing2.txt", "thing2\n")
                )
        );
    }

    @Test
    @SneakyThrows
    public void autoUnarchiveTarZ() {
        decompressionTest(
                "things.tar.Z",
                DecompressionType.AUTO,
                List.of(
                        new ExpectedFile("thing1.txt", "thing1\n"),
                        new ExpectedFile("thing2.txt", "thing2\n")
                ),
                "tar.z"
        );
    }

    @Test
    @SneakyThrows
    public void autoUnarchiveTarXZ() {
        decompressionTest(
                "things.tar.xz",
                DecompressionType.AUTO,
                List.of(
                        new ExpectedFile("thing1.txt", "thing1\n"),
                        new ExpectedFile("thing2.txt", "thing2\n")
                ),
                "tar.xz"
        );
    }

    @Test
    @SneakyThrows
    public void unarchiveSubdirectoryTar() {
        decompressionTest(
                "foobar.tar",
                DecompressionType.TAR,
                List.of(
                        new ExpectedFile("foo/1/baz", "foo1\n"),
                        new ExpectedFile("foo/2/baz", "foo2\n"),
                        new ExpectedFile("foo/3/baz", "foo3\n"),
                        new ExpectedFile("bar/1/baz", "bar1\n"),
                        new ExpectedFile("bar/2/baz", "bar2\n"),
                        new ExpectedFile("bar/3/baz", "bar3\n")
                )
        );
    }

    @Test
    @SneakyThrows
    public void unarchiveSubdirectoryZip() {
        decompressionTest(
                "foobar.zip",
                DecompressionType.ZIP,
                List.of(
                        new ExpectedFile("foo/1/baz", "foo1\n"),
                        new ExpectedFile("foo/2/baz", "foo2\n"),
                        new ExpectedFile("foo/3/baz", "foo3\n"),
                        new ExpectedFile("bar/1/baz", "bar1\n"),
                        new ExpectedFile("bar/2/baz", "bar2\n"),
                        new ExpectedFile("bar/3/baz", "bar3\n")
                )
        );
    }

    @Test
    @SneakyThrows
    public void unarchiveSubdirectoryAutoZip() {
        decompressionTest(
                "foobar.zip",
                DecompressionType.AUTO,
                List.of(
                        new ExpectedFile("foo/1/baz", "foo1\n"),
                        new ExpectedFile("foo/2/baz", "foo2\n"),
                        new ExpectedFile("foo/3/baz", "foo3\n"),
                        new ExpectedFile("bar/1/baz", "bar1\n"),
                        new ExpectedFile("bar/2/baz", "bar2\n"),
                        new ExpectedFile("bar/3/baz", "bar3\n")
                ),
                "zip"
        );
    }

    @Test @SneakyThrows
    public void decompressionTypeMismatch() {
        decompressionErrorTest(
                "things.tar.gz",
                DecompressionType.ZIP,
                "Unable to decompress zip"
        );
    }

    @Test @SneakyThrows
    public void truncatedFile() {
        decompressionErrorTest(
                "bad.things.tar.gz",
                DecompressionType.TAR_GZIP,
                "Unable to unarchive tar"
        );
    }

    @Test @SneakyThrows
    public void autoNoCompression() {
        decompressionErrorTest(
                "thing1.txt",
                DecompressionType.AUTO,
                "No compression or archive formats detected"
        );
    }

    @Test
    @SneakyThrows
    public void loadFailure() {
        String testFile = "things.tar.gz";
        DecompressionTransformParameters params = new DecompressionTransformParameters(DecompressionType.GZIP);
        Content content = content(testFile);

        Mockito.when(contentStorageService.load(content.getContentReference())).thenThrow(new ObjectStorageException("Boom", new Exception()));

        Result result = action.transform(ACTION_CONTEXT, params, sourceInfo(testFile), List.of(content), Collections.emptyMap());
        assertThat(result, instanceOf(ErrorResult.class));
        assertThat( ((ErrorResult)result).getErrorCause(), equalTo("Failed to load compressed binary from storage"));
    }

    @Test
    @SneakyThrows
    public void storeFailure() {
        String testFile = "things.tar.gz";
        DecompressionTransformParameters params = new DecompressionTransformParameters(DecompressionType.TAR_GZIP);
        Content content = content(testFile);

        Mockito.when(contentStorageService.load(content.getContentReference())).thenReturn(contentFor(testFile));
        Mockito.when(contentStorageService.save(eq(DID), (InputStream) Mockito.any(), eq(CONTENT_TYPE))).thenThrow(new ObjectStorageException("Boom", new Exception()));

        Result result = action.transform(ACTION_CONTEXT, params, sourceInfo(testFile), List.of(content), Collections.emptyMap());
        assertThat(result, instanceOf(ErrorResult.class));
        assertThat( ((ErrorResult)result).getErrorCause(), equalTo("Unable to store content"));
    }


    @SneakyThrows
    void storeContent() {
        Mockito.when(contentStorageService.save(eq(DID), (InputStream) Mockito.any(), eq(CONTENT_TYPE))).thenAnswer(
                (Answer<ContentReference>) invocation -> {
                    Object[] args = invocation.getArguments();
                    String did = (String) args[0];
                    InputStream is = (InputStream)args[1];
                    String content = new String(is.readAllBytes());
                    long contentLength = content.length();
                    String contentType = (String) args[2];
                    return new ContentReference(content, 0, contentLength, did, contentType);
                });
    }

    @Data @AllArgsConstructor
    static class ExpectedFile {
        String filename;
        String contents;
    }

    @SneakyThrows
    public void decompressionTest(String testFile,
                                  DecompressionType decompressionType,
                                  List<ExpectedFile> expectedFiles) {
        decompressionTest(testFile, decompressionType, expectedFiles, decompressionType.getValue());
    }

    @SneakyThrows
    public void decompressionTest(String testFile,
                                  DecompressionType decompressionType,
                                  List<ExpectedFile> expectedFiles,
                                  String expectedDecompressionType) {
        DecompressionTransformParameters params = new DecompressionTransformParameters(decompressionType);
        Content content = content(testFile);

        Mockito.when(contentStorageService.load(content.getContentReference())).thenReturn(contentFor(testFile));
        storeContent();

        Result result = action.transform(ACTION_CONTEXT, params, sourceInfo(testFile), List.of(content), Collections.emptyMap());

        assertThat(result, instanceOf(TransformResult.class));
        TransformResult tr = (TransformResult) result;

        assertThat(tr.getType(), equalTo("binary"));

        assertThat(tr.getMetadata(), hasItem(new KeyValue("decompressionType", expectedDecompressionType)));

        assertThat(tr.getContent().size(), equalTo(expectedFiles.size()));

        // Ignore the metadata, just match the files
        List<Content> normalizedContent = tr.getContent()
                .stream().map( c -> new Content(c.getName(), Collections.emptyList(), c.getContentReference())).collect(Collectors.toList());

        assertThat(normalizedContent, containsInAnyOrder(expectedFiles.
                stream().map(ef -> new Content(
                        ef.getFilename(),
                        Collections.emptyList(),
                        new ContentReference(ef.getContents(), 0, ef.getContents().length(), DID, MediaType.APPLICATION_OCTET_STREAM))).toArray()));
    }

    @SneakyThrows
    public void decompressionErrorTest(String testFile, DecompressionType decompressionType, String errorCause) {
        DecompressionTransformParameters params = new DecompressionTransformParameters(decompressionType);
        Content content = content(testFile);

        Mockito.when(contentStorageService.load(content.getContentReference())).thenReturn(contentFor(testFile));

        Result result = action.transform(ACTION_CONTEXT, params, sourceInfo(testFile), List.of(content), Collections.emptyMap());
        assertThat(result, instanceOf(ErrorResult.class));
        assertThat( ((ErrorResult)result).getErrorCause(), equalTo(errorCause));
    }

    static class UnmarkableInputStream extends FilterInputStream {

        public UnmarkableInputStream(InputStream in) {
            super(in);
        }

        @Override
        public boolean markSupported() {
            return false;
        }
    }

    @SneakyThrows
    InputStream contentFor(String filename) {
        return new UnmarkableInputStream(this.getClass().getResourceAsStream("/" + filename));
    }

    SourceInfo sourceInfo(String filename) {
        return new SourceInfo(filename, FLOW, List.of());
    }

    @SneakyThrows
    Content content(String filename) {
        ContentReference contentReference = new ContentReference(DID, 0, contentFor(filename).available(), DID, CONTENT_TYPE);
        return new Content(filename, Collections.emptyList(), contentReference);
    }
}