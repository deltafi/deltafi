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

import org.apache.nifi.util.FlowFileUnpackagerV1;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.egress.EgressResult;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.service.HttpService;
import org.deltafi.common.content.ContentReference;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.converters.KeyValueConverter;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.FormattedData;
import org.deltafi.common.types.SourceInfo;
import org.deltafi.core.parameters.HttpEgressParameters;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

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
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.deltafi.core.action.FlowfileEgressAction.FLOWFILE_V1_CONTENT_TYPE;
import static org.mockito.ArgumentMatchers.any;
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
    private static final String CONTENT_TYPE = FLOWFILE_V1_CONTENT_TYPE;

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
        Result result = runTest(200, METADATA, 1);

        assertThat(result, instanceOf(EgressResult.class));
        assertThat(result.toEvent().getDid(), equalTo(DID));
        assertThat(result.toEvent().getAction(), equalTo(ACTION));
    }

    @SuppressWarnings("unchecked")
    private Result runTest(int statusCode, Map<String, String> expectedMetadata, int numTries) throws IOException {
        ActionContext context = ActionContext.builder().did(DID).name(ACTION).build();

        final List<byte[]> posts = new ArrayList<>();
        when(httpService.post(any(), any(), any(), any())).thenAnswer(
                (Answer<HttpResponse<InputStream>>) invocation -> {
                    InputStream is = invocation.getArgument(2);
                    posts.add(is.readAllBytes());
                    is.close();
                    HttpResponse<InputStream> httpResponse = new HttpResponse<>() {
                        @Override public int statusCode() {
                            return statusCode;
                        }
                        @Override public HttpRequest request() {
                            return null;
                        }
                        @Override public Optional<HttpResponse<InputStream>> previousResponse() { return Optional.empty(); }
                        @Override public HttpHeaders headers() {
                            return null;
                        }
                        @Override public InputStream body() { return new ByteArrayInputStream("Hello there.".getBytes()); }
                        @Override public Optional<SSLSession> sslSession() {
                            return Optional.empty();
                        }
                        @Override public URI uri() {
                            return null;
                        }
                        @Override public HttpClient.Version version() {
                            return null;
                        }
                    };
                    return httpResponse;
                }
        );
        Result result = action.egress(context, PARAMS, SOURCE_INFO, FORMATTED_DATA);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> headersCaptor = ArgumentCaptor.forClass(Map.class);
        verify(httpService, times(numTries)).post(eq(URL), headersCaptor.capture(), any(), eq(CONTENT_TYPE));

        FlowFileUnpackagerV1 unpackager = new FlowFileUnpackagerV1();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Map<String, String> metadata = unpackager.unpackageFlowFile(new ByteArrayInputStream(posts.get(0)), out);
        // Expected metadata + ADDITIONAL_METADATA should be in the flowfile attributes
        assertThat(metadata, equalTo(Stream.of(expectedMetadata, ADDITIONAL_METADATA).flatMap(m -> m.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))));
        byte[] content = out.toByteArray();
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
            throw new IOException("This is a contrived exception.");
        }
    }

    @Test
    public void closingInputStreamThrowsIoException() throws IOException, ObjectStorageException {
        when(contentStorageService.load(eq(CONTENT_REFERENCE))).thenAnswer(invocation -> new TestInputStream(CONTENT));
        Result result = runTest(200, METADATA,1);
        assertThat(result, instanceOf(EgressResult.class));
    }

    @Test
    public void badResponse() throws IOException, ObjectStorageException {
        when(contentStorageService.load(eq(CONTENT_REFERENCE))).thenAnswer(invocation -> new ByteArrayInputStream(CONTENT));
        Result result = runTest(505, METADATA, 4);

        assertThat(result, instanceOf(ErrorResult.class));
        assertThat(((ErrorResult) result).getErrorCause(), containsString("505"));
        assertThat(((ErrorResult) result).getErrorCause(), containsString("Hello there"));
    }
}