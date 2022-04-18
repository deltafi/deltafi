package org.deltafi.core.action;

import org.apache.nifi.util.FlowFileUnpackagerV1;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.egress.EgressResult;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.service.HttpService;
import org.deltafi.common.content.ContentReference;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.core.domain.api.converters.KeyValueConverter;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.SourceInfo;
import org.deltafi.core.domain.generated.types.FormattedData;
import org.deltafi.core.parameters.HttpEgressParameters;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.net.ssl.SSLSession;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

@ExtendWith(MockitoExtension.class)
class FlowfileEgressActionTest {
    private static final byte[] CONTENT = "This is the content.".getBytes();
    private static final Map<String, String> METADATA = Map.of(
            "thing1", "foo",
            "thing2", "bar");

    private static final String DID = UUID.randomUUID().toString();
    private static final String ORIG_FILENAME = "origFilename";
    private static final String FLOW = "theFlow";
    private static final String POST_FILENAME = "postFilename";

    private static final String ACTION = "MyEgressAction";
    private static final String EGRESS_FLOW = "outFlow";
    private static final String URL = "https://url.com";

    private static final String CONTENT_NAME = "contentName";
    private static final String CONTENT_TYPE = "application/flowfile-v1";

    private static final Map<String, String> ADDITIONAL_METADATA = Map.of(
            "did", DID,
            "flow", EGRESS_FLOW,
            "ingressFlow", FLOW,
            "originalFilename", ORIG_FILENAME,
            "filename", POST_FILENAME
    );

    private static final ContentReference CONTENT_REFERENCE = new ContentReference(CONTENT_NAME, 0, CONTENT.length, DID, CONTENT_TYPE);

    private static final SourceInfo SOURCE_INFO = new SourceInfo(ORIG_FILENAME, FLOW, List.of());
    private static final FormattedData FORMATTED_DATA = FormattedData.newBuilder()
            .filename(POST_FILENAME)
            .contentReference(CONTENT_REFERENCE)
            .metadata(KeyValueConverter.fromMap(METADATA))
            .build();

    static Integer NUM_TRIES = 3;
    static Integer RETRY_WAIT = 10;
    private static final HttpEgressParameters PARAMS = new HttpEgressParameters(EGRESS_FLOW, URL, NUM_TRIES, RETRY_WAIT);

    @Mock
    private ContentStorageService contentStorageService;

    @Mock
    private HttpService httpService;

    @InjectMocks
    private FlowfileEgressAction action;

    @Test
    public void execute() throws IOException, ObjectStorageException {
        when(contentStorageService.load(eq(CONTENT_REFERENCE))).thenAnswer(invocation -> new ByteArrayInputStream(CONTENT));
        Result result = runTest(200, "good job", METADATA, 1);

        assertThat(result, instanceOf(EgressResult.class));
        assertThat(result.toEvent().getDid(), equalTo(DID));
        assertThat(result.toEvent().getAction(), equalTo(ACTION));
    }

    @SuppressWarnings("unchecked")
    private Result runTest(int statusCode, String responseBody, Map<String, String> expectedMetadata, int numTries) throws IOException {
        ActionContext context = ActionContext.builder().did(DID).name(ACTION).build();
        HttpResponse<InputStream> httpResponse = new HttpResponse<>() {
            @Override
            public int statusCode() {
                return statusCode;
            }

            @Override
            public HttpRequest request() {
                return null;
            }

            @Override
            public Optional<HttpResponse<InputStream>> previousResponse() {
                return Optional.empty();
            }

            @Override
            public HttpHeaders headers() {
                return null;
            }

            @Override
            public InputStream body() {
                return new ByteArrayInputStream(responseBody.getBytes());
            }

            @Override
            public Optional<SSLSession> sslSession() {
                return Optional.empty();
            }

            @Override
            public URI uri() {
                return null;
            }

            @Override
            public HttpClient.Version version() {
                return null;
            }
        };

        when(httpService.post(any(), any(), any(), any())).thenReturn(httpResponse, httpResponse, httpResponse);
        Result result = action.egress(context, PARAMS, SOURCE_INFO, FORMATTED_DATA);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> headersCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<InputStream> bodyCaptor = ArgumentCaptor.forClass(InputStream.class);
        verify(httpService, times(numTries)).post(eq(URL), headersCaptor.capture(), bodyCaptor.capture(), eq(CONTENT_TYPE));

        byte[] body = bodyCaptor.getValue().readAllBytes();
        FlowFileUnpackagerV1 unpackager = new FlowFileUnpackagerV1();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Map<String, String> metadata = unpackager.unpackageFlowFile(new ByteArrayInputStream(body), out);
        byte[] content = out.toByteArray();

        // Expected metadata + ADDITIONAL_METADATA should be in the flowfile attributes
        assertThat(metadata, equalTo(Stream.of(expectedMetadata, ADDITIONAL_METADATA).flatMap(m -> m.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))));

        assertThat(content, equalTo(CONTENT));

        return result;
    }

    @Test
    public void executeMissingData() throws ObjectStorageException {
        when(contentStorageService.load(eq(CONTENT_REFERENCE))).thenThrow(ObjectStorageException.class);

        ActionContext context = ActionContext.builder().did(DID).name(ACTION).build();
        Result result = action.egress(context, PARAMS, SOURCE_INFO, FORMATTED_DATA);

        verify(httpService, never()).post(any(), any(), any(), any());

        assertThat(result, instanceOf(ErrorResult.class));
        assertThat(result.toEvent().getDid(), equalTo(DID));
        assertThat(result.toEvent().getAction(), equalTo(ACTION));
    }

    private static class TestInputStream extends ByteArrayInputStream {
        public TestInputStream(byte[] buf) {
            super(buf);
        }

        @Override
        public void close() throws IOException {
            throw new IOException();
        }
    }

    @Test
    public void closingInputStreamThrowsIoException() throws IOException, ObjectStorageException {
        when(contentStorageService.load(eq(CONTENT_REFERENCE))).thenAnswer(invocation -> new TestInputStream(CONTENT));
        Result result = runTest(200, "boom", METADATA,1);
        assertThat(result, instanceOf(EgressResult.class));
    }

    @Test
    public void badResponse() throws IOException, ObjectStorageException {
        when(contentStorageService.load(eq(CONTENT_REFERENCE))).thenAnswer(invocation -> new ByteArrayInputStream(CONTENT));
        Result result = runTest(500, "uh oh", METADATA, 4);

        assertThat(result, instanceOf(ErrorResult.class));
        assertThat(((ErrorResult) result).getErrorCause(), containsString("500"));
        assertThat(((ErrorResult) result).getErrorCause(), containsString("uh oh"));
    }
}