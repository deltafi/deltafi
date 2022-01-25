package org.deltafi.core.action;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.egress.EgressResult;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.service.HttpService;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.content.ContentReference;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.api.types.SourceInfo;
import org.deltafi.core.domain.generated.types.FormattedData;
import org.deltafi.core.parameters.RestPostEgressParameters;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RestPostEgressActionTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private static final String DID = UUID.randomUUID().toString();
    private static final String ORIG_FILENAME = "origFilename";
    private static final String FLOW = "theFlow";
    private static final String POST_FILENAME = "postFilename";

    private static final String ACTION = "MyRestEgressAction";
    private static final String EGRESS_FLOW = "outFlow";
    private static final String URL = "https://url.com";
    private static final String METADATA_KEY = "theMetadataKey";

    private static final String CONTENT_NAME = "contentName";
    private static final byte[] DATA = "data to be egressed".getBytes();
    private static final String CONTENT_TYPE = "application/json";

    private static final ContentReference CONTENT_REFERENCE = new ContentReference(CONTENT_NAME, 0, DATA.length, DID, CONTENT_TYPE);

    private static final DeltaFile DELTA_FILE = DeltaFile.newBuilder()
            .did(DID)
            .sourceInfo(new SourceInfo(ORIG_FILENAME, FLOW, List.of()))
            .formattedData(Collections.singletonList(
                    FormattedData.newBuilder()
                            .filename(POST_FILENAME)
                            .contentReference(CONTENT_REFERENCE)
                            .build()
            ))
            .build();

    private static final RestPostEgressParameters PARAMS = new RestPostEgressParameters(EGRESS_FLOW, URL, METADATA_KEY);

    @Mock
    private ContentStorageService contentStorageService;

    @Mock
    private HttpService httpService;

    @InjectMocks
    private RestPostEgressAction action;

    @Test
    public void execute() throws IOException, ObjectStorageException {
        when(contentStorageService.load(eq(CONTENT_REFERENCE))).thenReturn(new ByteArrayInputStream(DATA));
        runTestExpectedToSucceed();
    }

    private void runTestExpectedToSucceed() throws IOException {
        ActionContext actionContext = ActionContext.builder().did(DID).name(ACTION).build();
        Result result = action.execute(DELTA_FILE, actionContext, PARAMS);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> headersCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<InputStream> bodyCaptor = ArgumentCaptor.forClass(InputStream.class);
        ArgumentCaptor<String> mediaTypeCaptor = ArgumentCaptor.forClass(String.class);
        verify(httpService).post(eq(URL), headersCaptor.capture(), bodyCaptor.capture(), mediaTypeCaptor.capture());

        Map<String, String> actual = headersCaptor.getValue();
        assertNotNull(actual.get(METADATA_KEY));
        Map<String, String> metadata = OBJECT_MAPPER.readValue(actual.get(METADATA_KEY), new TypeReference<>() {});
        assertEquals(DID, metadata.get("did"));
        assertEquals(FLOW, metadata.get("ingressFlow"));
        assertEquals(EGRESS_FLOW, metadata.get("flow"));
        assertEquals(POST_FILENAME, metadata.get("filename"));
        assertEquals(ORIG_FILENAME, metadata.get("originalFilename"));

        assertArrayEquals(DATA, bodyCaptor.getValue().readAllBytes());
        assertEquals(CONTENT_TYPE, mediaTypeCaptor.getValue());

        assertTrue(result instanceof EgressResult);
        assertEquals(DID, result.toEvent().getDid());
        assertEquals(ACTION, result.toEvent().getAction());
    }

    @Test
    public void executeMissingData() throws ObjectStorageException {
        when(contentStorageService.load(eq(CONTENT_REFERENCE))).thenThrow(ObjectStorageException.class);

        ActionContext actionContext = ActionContext.builder().did(DID).name(ACTION).build();
        Result result = action.execute(DELTA_FILE, actionContext, PARAMS);

        verify(httpService, never()).post(any(), any(), any(), any());

        assertTrue(result instanceof ErrorResult);
        assertEquals(DID, result.toEvent().getDid());
        assertEquals(ACTION, result.toEvent().getAction());
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
        when(contentStorageService.load(eq(CONTENT_REFERENCE))).thenReturn(new TestInputStream(DATA));
        runTestExpectedToSucceed();
    }
}