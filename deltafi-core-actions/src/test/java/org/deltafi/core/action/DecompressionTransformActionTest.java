package org.deltafi.core.action;

import lombok.SneakyThrows;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.common.content.ContentReference;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.core.domain.api.types.*;
import org.deltafi.core.domain.api.types.Content;
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
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
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
        String testFile = "things.tar.gz";
        DecompressionTransformParameters params = new DecompressionTransformParameters(DecompressionType.TAR_GZIP);
        Content content = content(testFile);

        Mockito.when(contentStorageService.load(content.getContentReference())).thenReturn(contentFor(testFile));
        storeContent();

        Result result = action.transform(ACTION_CONTEXT, params, sourceInfo(testFile), List.of(content), Collections.emptyMap());

        assertThat(result, instanceOf(TransformResult.class));
        TransformResult tr = (TransformResult) result;

        assertThat(tr.getType(), equalTo("binary"));
        assertThat(tr.getMetadata(), hasItem(new KeyValue("decompressionType", params.getDecompressionType().getValue())));
        assertThat(tr.getContent().size(), equalTo(2));
        assertThat(tr.getContent().get(0).getName(), equalTo("thing1.txt"));
        assertThat(tr.getContent().get(0).getContentReference(), equalTo(new ContentReference("thing1\n", 0, 7, DID, MediaType.APPLICATION_OCTET_STREAM)));
        assertThat(tr.getContent().get(1).getName(), equalTo("thing2.txt"));
        assertThat(tr.getContent().get(1).getContentReference(), equalTo(new ContentReference("thing2\n", 0, 7, DID, MediaType.APPLICATION_OCTET_STREAM)));
    }

    @Test
    @SneakyThrows
    public void decompressionTypeMismatch() {
        String testFile = "things.tar.gz";
        DecompressionTransformParameters params = new DecompressionTransformParameters(DecompressionType.ZIP);
        Content content = content(testFile);

        Mockito.when(contentStorageService.load(content.getContentReference())).thenReturn(contentFor(testFile));

        Result result = action.transform(ACTION_CONTEXT, params, sourceInfo(testFile), List.of(content), Collections.emptyMap());
        assertThat(result, instanceOf(ErrorResult.class));
        assertThat( ((ErrorResult)result).getErrorCause(), equalTo("Unable to decompress zip"));
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

    @Test
    @SneakyThrows
    public void decompressZip() {
        String testFile = "things.zip";
        DecompressionTransformParameters params = new DecompressionTransformParameters(DecompressionType.ZIP);
        Content content = content(testFile);

        Mockito.when(contentStorageService.load(content.getContentReference())).thenReturn(contentFor(testFile));
        storeContent();

        Result result = action.transform(ACTION_CONTEXT, params, sourceInfo(testFile), List.of(content), Collections.emptyMap());

        assertThat(result, instanceOf(TransformResult.class));
        TransformResult tr = (TransformResult) result;

        assertThat(tr.getType(), equalTo("binary"));
        assertThat(tr.getMetadata(), hasItem(new KeyValue("decompressionType", params.getDecompressionType().getValue())));
        assertThat(tr.getContent().size(), equalTo(2));
        assertThat(tr.getContent().get(0).getName(), equalTo("thing1.txt"));
        assertThat(tr.getContent().get(0).getContentReference(), equalTo(new ContentReference("thing1\n", 0, 7, DID, MediaType.APPLICATION_OCTET_STREAM)));
        assertThat(tr.getContent().get(1).getName(), equalTo("thing2.txt"));
        assertThat(tr.getContent().get(1).getContentReference(), equalTo(new ContentReference("thing2\n", 0, 7, DID, MediaType.APPLICATION_OCTET_STREAM)));
    }

    @Test
    @SneakyThrows
    public void decompressGzip() {
        String testFile = "thing1.txt.gz";
        DecompressionTransformParameters params = new DecompressionTransformParameters(DecompressionType.GZIP);
        Content content = content(testFile);

        Mockito.when(contentStorageService.load(content.getContentReference())).thenReturn(contentFor(testFile));
        storeContent();

        Result result = action.transform(ACTION_CONTEXT, params, sourceInfo(testFile), List.of(content), Collections.emptyMap());

        assertThat(result, instanceOf(TransformResult.class));
        TransformResult tr = (TransformResult) result;

        assertThat(tr.getType(), equalTo("binary"));
        assertThat(tr.getMetadata(), hasItem(new KeyValue("decompressionType", params.getDecompressionType().getValue())));
        assertThat(tr.getContent().size(), equalTo(1));
        assertThat(tr.getContent().get(0).getName(), equalTo("thing1.txt.gz"));
        assertThat(tr.getContent().get(0).getContentReference(), equalTo(new ContentReference("thing1\n", 0, 7, DID, MediaType.APPLICATION_OCTET_STREAM)));
    }

    @Test
    @SneakyThrows
    public void unarchiveTar() {
        String testFile = "things.tar";
        DecompressionTransformParameters params = new DecompressionTransformParameters(DecompressionType.TAR);
        Content content = content(testFile);

        Mockito.when(contentStorageService.load(content.getContentReference())).thenReturn(contentFor(testFile));
        storeContent();

        Result result = action.transform(ACTION_CONTEXT, params, sourceInfo(testFile), List.of(content), Collections.emptyMap());

        assertThat(result, instanceOf(TransformResult.class));
        TransformResult tr = (TransformResult) result;

        assertThat(tr.getType(), equalTo("binary"));
        assertThat(tr.getMetadata(), hasItem(new KeyValue("decompressionType", params.getDecompressionType().getValue())));
        assertThat(tr.getContent().size(), equalTo(2));
        assertThat(tr.getContent().get(0).getName(), equalTo("thing1.txt"));
        assertThat(tr.getContent().get(0).getContentReference(), equalTo(new ContentReference("thing1\n", 0, 7, DID, MediaType.APPLICATION_OCTET_STREAM)));
        assertThat(tr.getContent().get(1).getName(), equalTo("thing2.txt"));
        assertThat(tr.getContent().get(1).getContentReference(), equalTo(new ContentReference("thing2\n", 0, 7, DID, MediaType.APPLICATION_OCTET_STREAM)));
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

    @SneakyThrows
    InputStream contentFor(String filename) {
        return this.getClass().getResourceAsStream("/" + filename);
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