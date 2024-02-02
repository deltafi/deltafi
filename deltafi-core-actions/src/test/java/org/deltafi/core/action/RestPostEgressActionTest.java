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
package org.deltafi.core.action;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.egress.EgressInput;
import org.deltafi.actionkit.action.egress.EgressResult;
import org.deltafi.actionkit.action.egress.EgressResultType;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.common.content.Segment;
import org.deltafi.common.http.HttpPostException;
import org.deltafi.common.http.HttpService;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.Content;
import org.deltafi.core.parameters.RestPostEgressParameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.net.ssl.SSLSession;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RestPostEgressActionTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private static final String DID = UUID.randomUUID().toString();
    private static final String ORIG_FILENAME = "origFilename";
    private static final String DATA_SOURCE = "theFlow";
    private static final String POST_FILENAME = "postFilename";

    private static final String ACTION = "MyRestEgressAction";
    private static final String EGRESS_FLOW = "outFlow";
    private static final String URL = "https://url.com";
    private static final String METADATA_KEY = "theMetadataKey";

    private static final byte[] DATA = "data to be egressed".getBytes();
    private static final String CONTENT_TYPE = "application/json";

    private static final Segment SEGMENT = new Segment(UUID.randomUUID().toString(), 0, DATA.length, DID);
    private static final ActionContext CONTEXT = ActionContext.builder().did(DID).flowName(EGRESS_FLOW).actionName(ACTION).deltaFileName(ORIG_FILENAME).dataSource(DATA_SOURCE).build();
    private static final Content CONTENT = new Content(POST_FILENAME, CONTENT_TYPE, List.of(SEGMENT));
    static final Integer NUM_TRIES = 3;
    static final Integer RETRY_WAIT = 10;
    private static final RestPostEgressParameters PARAMS = new RestPostEgressParameters(URL, METADATA_KEY, NUM_TRIES, RETRY_WAIT);

    @Mock
    private ContentStorageService contentStorageService;

    @Mock
    private HttpService httpService;

    @InjectMocks
    private RestPostEgressAction action;

    @BeforeEach
    public void init() {
        CONTEXT.setContentStorageService(contentStorageService);
    }

    @Test
    void execute() throws IOException, ObjectStorageException {
        when(contentStorageService.load(CONTENT)).thenReturn(new ByteArrayInputStream(DATA));
        EgressResultType result = runTest(200, "good job", 1);

        assertTrue(result instanceof EgressResult);
        assertEquals(DID, result.toEvent().getDid());
        assertEquals(ACTION, result.toEvent().getActionName());
        assertEquals(EGRESS_FLOW, result.toEvent().getFlowName());
    }

    @SuppressWarnings("unchecked")
    private EgressResultType runTest(int statusCode, String responseBody, int numTries) throws IOException {
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
        if (statusCode < 0) {
            when(httpService.post(any(), any(), any(), any())).thenThrow(HttpPostException.class);
        } else {
            when(httpService.post(any(), any(), any(), any())).thenReturn(httpResponse, httpResponse, httpResponse);
        }
        EgressResultType result = action.egress(CONTEXT, PARAMS, egressInput());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> headersCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<InputStream> bodyCaptor = ArgumentCaptor.forClass(InputStream.class);
        verify(httpService, times(numTries)).post(eq(URL), headersCaptor.capture(), bodyCaptor.capture(), eq(CONTENT_TYPE));

        Map<String, String> actual = headersCaptor.getValue();
        assertNotNull(actual.get(METADATA_KEY));
        Map<String, String> metadata = OBJECT_MAPPER.readValue(actual.get(METADATA_KEY), new TypeReference<>() {
        });
        assertEquals(DID, metadata.get("did"));
        assertEquals(DATA_SOURCE, metadata.get("dataSource"));
        assertEquals(EGRESS_FLOW, metadata.get("flow"));
        assertEquals(POST_FILENAME, metadata.get("filename"));
        assertEquals(ORIG_FILENAME, metadata.get("originalFilename"));

        assertArrayEquals(DATA, bodyCaptor.getValue().readAllBytes());

        return result;
    }

    @Test
    void closingInputStreamThrowsIoException() throws IOException, ObjectStorageException {
        when(contentStorageService.load(CONTENT)).thenReturn(new TestInputStream(DATA));
        EgressResultType result = runTest(200, "good job", 1);
        assertTrue(result instanceof EgressResult);
    }

    @Test
    void badResponse() throws IOException, ObjectStorageException {
        when(contentStorageService.load(CONTENT)).thenReturn(new ByteArrayInputStream(DATA));
        EgressResultType result = runTest(500, "uh oh", 4);

        assertTrue(result instanceof ErrorResult);
        assertTrue(((ErrorResult) result).getErrorCause().contains("500"));
        assertTrue(((ErrorResult) result).getErrorCause().contains("uh oh"));
    }

    @Test
    void badPost() throws IOException, ObjectStorageException {
        when(contentStorageService.load(CONTENT)).thenReturn(new ByteArrayInputStream(DATA));
        EgressResultType result = runTest(-1, "uh oh", 4);

        assertTrue(result instanceof ErrorResult);
        assertTrue(((ErrorResult) result).getErrorCause().contains("Service post failure"));
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

    private ActionContent actionContent() {
        return new ActionContent(CONTENT, contentStorageService);
    }

    private EgressInput egressInput() {
        return EgressInput.builder().content(actionContent()).metadata(Map.of()).build();
    }
}
