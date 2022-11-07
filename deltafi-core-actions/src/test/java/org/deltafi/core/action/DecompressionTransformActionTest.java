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
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.actionkit.action.transform.TransformResultType;
import org.deltafi.common.content.ContentReference;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.content.Segment;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.Content;
import org.deltafi.common.types.KeyValue;
import org.deltafi.common.types.SourceInfo;
import org.deltafi.core.parameters.DecompressionTransformParameters;
import org.deltafi.core.parameters.DecompressionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import javax.ws.rs.core.MediaType;
import java.io.FilterInputStream;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class DecompressionTransformActionTest {
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

    @Captor
    ArgumentCaptor<Map<Content, byte[]>> contentMapCaptor;

    @Test
    @SneakyThrows
    void decompressTarGz() {
        unarchiveTest(
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
   void autoDecompressTarGz() {
        unarchiveTest(
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
   void autoDecompressTarZ() {
        unarchiveTest(
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
   void autoDecompressTarXZ() {
        unarchiveTest(
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
   void decompressTarXZ() {
        unarchiveTest(
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
   void decompressTarZ() {
        unarchiveTest(
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
   void decompressAR() {
        unarchiveTest(
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
   void autoDecompressAR() {
        unarchiveTest(
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
   void autoDecompressZip() {
        unarchiveTest(
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
   void autoDecompressTar() {
        unarchiveTest(
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
   void autoDecompressGzip() {
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
   void decompressZip() {
        unarchiveTest(
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
   void decompressGzip() {
        decompressionTest(
                "thing1.txt.gz",
                DecompressionType.GZIP,
                List.of(
                        new ExpectedFile("thing1.txt.gz", "thing1\n")
                ),
                "gz"
        );
    }

    @Test
    @SneakyThrows
   void autoDecompressXZ() {
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
   void decompressXZ() {
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
   void decompressZ() {
        decompressionTest(
                "thing1.txt.Z",
                DecompressionType.Z,
                List.of(
                        new ExpectedFile("thing1.txt.Z", "thing1\n")
                ),
                "z"
        );
    }

    @Test
    @SneakyThrows
   void autoDecompressZ() {
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
   void unarchiveTar() {
        unarchiveTest(
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
   void autoUnarchiveTarZ() {
        unarchiveTest(
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
   void autoUnarchiveTarXZ() {
        unarchiveTest(
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
   void unarchiveSubdirectoryTar() {
        unarchiveTest(
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
   void unarchiveSubdirectoryZip() {
        unarchiveTest(
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
   void unarchiveSubdirectoryAutoZip() {
        unarchiveTest(
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
   void decompressionTypeMismatch() {
        decompressionErrorTest(
                "things.tar.gz",
                DecompressionType.ZIP,
                "Unable to decompress zip"
        );
    }

    @Test @SneakyThrows
   void truncatedFile() {
        decompressionErrorTest(
                "bad.things.tar.gz",
                DecompressionType.TAR_GZIP,
                "Unable to unarchive tar"
        );
    }

    @Test @SneakyThrows
   void autoNoCompression() {
        decompressionErrorTest(
                "thing1.txt",
                DecompressionType.AUTO,
                "No compression or archive formats detected"
        );
    }

    @Test
    @SneakyThrows
   void loadFailure() {
        String testFile = "things.tar.gz";
        DecompressionTransformParameters params = new DecompressionTransformParameters(DecompressionType.GZIP);
        Content content = content(testFile);

        Mockito.when(contentStorageService.load(content.getContentReference())).thenThrow(new ObjectStorageException("Boom", new Exception()));

        TransformResultType result = action.transform(ACTION_CONTEXT, params, sourceInfo(testFile), List.of(content), Collections.emptyMap());
        assertThat(result, instanceOf(ErrorResult.class));
        assertThat( ((ErrorResult)result).getErrorCause(), equalTo("Failed to load compressed binary from storage"));
    }

    @Test
    @SneakyThrows
   void storeFailure() {
        String testFile = "things.tar.gz";
        DecompressionTransformParameters params = new DecompressionTransformParameters(DecompressionType.TAR_GZIP);
        Content content = content(testFile);

        Mockito.when(contentStorageService.load(content.getContentReference())).thenReturn(contentFor(testFile));
        Mockito.when(contentStorageService.saveMany(eq(DID), Mockito.anyMap())).thenThrow(new ObjectStorageException("Boom", new Exception()));

        TransformResultType result = action.transform(ACTION_CONTEXT, params, sourceInfo(testFile), List.of(content), Collections.emptyMap());
        assertThat(result, instanceOf(ErrorResult.class));
        assertThat( ((ErrorResult)result).getErrorCause(), equalTo("Unable to store content"));
    }

    @Data @AllArgsConstructor
    static class ExpectedFile {
        String filename;
        String contents;

        Content toContentWithOutReference() {
            Content content = new Content();
            content.setName(filename);
            content.setMetadata(List.of());
            return content;
        }
    }

    @SneakyThrows
    public void unarchiveTest(String testFile,
                              DecompressionType decompressionType,
                              List<ExpectedFile> expectedFiles) {
        unarchiveTest(testFile, decompressionType, expectedFiles, decompressionType.getValue());
    }

    @SneakyThrows
   void unarchiveTest(String testFile,
                      DecompressionType decompressionType,
                      List<ExpectedFile> expectedFiles,
                      String expectedDecompressionType) {
        DecompressionTransformParameters params = new DecompressionTransformParameters(decompressionType);
        Content content = content(testFile);

        Mockito.when(contentStorageService.load(content.getContentReference())).thenReturn(contentFor(testFile));

        TransformResultType result = action.transform(ACTION_CONTEXT, params, sourceInfo(testFile), List.of(content), Collections.emptyMap());

        assertThat(result, instanceOf(TransformResult.class));
        TransformResult tr = (TransformResult) result;

        Mockito.verify(contentStorageService).saveMany(Mockito.eq(ACTION_CONTEXT.getDid()), contentMapCaptor.capture());

        assertThat(tr.getMetadata(), hasItem(new KeyValue("decompressionType", expectedDecompressionType)));

        Map<Content, byte[]> contentMap = contentMapCaptor.getValue();
        Set<Content> contentList = contentMap.keySet();
        assertThat(contentMap.size(), equalTo(expectedFiles.size()));

        // Ignore the metadata, just match the files
        List<Content> normalizedContent = contentList.stream()
                .map( c -> new Content(c.getName(), Collections.emptyList(), c.getContentReference()))
                .collect(Collectors.toList());

        assertThat(normalizedContent, containsInAnyOrder(expectedFiles.
                stream().map(ExpectedFile::toContentWithOutReference).toArray()));
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

        TransformResultType result = action.transform(ACTION_CONTEXT, params, sourceInfo(testFile), List.of(content), Collections.emptyMap());

        assertThat(result, instanceOf(TransformResult.class));
        TransformResult tr = (TransformResult) result;

        assertThat(tr.getMetadata(), hasItem(new KeyValue("decompressionType", expectedDecompressionType)));

        assertThat(tr.getContent().size(), equalTo(expectedFiles.size()));

        // Ignore the metadata, just match the files
        List<Content> normalizedContent = tr.getContent()
                .stream().map( c -> new Content(c.getName(), Collections.emptyList(), c.getContentReference())).collect(Collectors.toList());

        assertThat(normalizedContent, containsInAnyOrder(expectedFiles.
                stream().map(ef -> new Content(
                        ef.getFilename(),
                        Collections.emptyList(),
                        new ContentReference(MediaType.APPLICATION_OCTET_STREAM, new Segment(ef.getContents(), 0, ef.getContents().length(), DID)))).toArray()));
    }

    @SneakyThrows
   void decompressionErrorTest(String testFile, DecompressionType decompressionType, String errorCause) {
        DecompressionTransformParameters params = new DecompressionTransformParameters(decompressionType);
        Content content = content(testFile);

        Mockito.when(contentStorageService.load(content.getContentReference())).thenReturn(contentFor(testFile));

        TransformResultType result = action.transform(ACTION_CONTEXT, params, sourceInfo(testFile), List.of(content), Collections.emptyMap());
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
        ContentReference contentReference = new ContentReference(CONTENT_TYPE, new Segment(DID, 0, contentFor(filename).available(), DID));
        return new Content(filename, Collections.emptyList(), contentReference);
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
                    Segment segment = new Segment(content, 0, contentLength, did);
                    return new ContentReference(contentType, segment);
                });
    }
}