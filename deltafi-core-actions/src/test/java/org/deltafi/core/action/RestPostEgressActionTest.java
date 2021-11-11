package org.deltafi.core.action;

import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.egress.EgressResult;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.service.HttpService;
import org.deltafi.actionkit.service.InMemoryObjectStorageService;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.core.parameters.RestPostEgressParameters;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.generated.types.FormattedData;
import org.deltafi.core.domain.generated.types.ObjectReference;
import org.deltafi.core.domain.generated.types.SourceInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class RestPostEgressActionTest {

    InMemoryObjectStorageService inMemoryObjectStorageService = new InMemoryObjectStorageService();

    HttpService httpService = Mockito.mock(HttpService.class);
    ArgumentCaptor<InputStream> isCaptor = ArgumentCaptor.forClass(InputStream.class);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, String>> mapCaptor = ArgumentCaptor.forClass(Map.class);

    RestPostEgressAction action = new RestPostEgressAction(inMemoryObjectStorageService, httpService);

    static final String URL = "https://url.com";
    static final String PREFIX = "thePrefix";
    static final String ACTION = "MyRestEgressAction";

    static final String data = "data to be egressed";
    static final String CONTENT_BUCKET = "contentBucket";
    static final String CONTENT_NAME = "contentName";

    static final String DID = UUID.randomUUID().toString();

    static final String FLOW = "theFlow";
    static final String EGRESS_FLOW = "outFlow";
    static final String ORIG_FILENAME = "origFilename";
    static final String POST_FILENAME = "postFilename";

    ObjectReference objectReference = ObjectReference.newBuilder()
            .bucket(CONTENT_BUCKET)
            .name(CONTENT_NAME)
            .size(data.length())
            .offset(0).build();

    DeltaFile deltaFile = DeltaFile.newBuilder()
            .did(DID)
            .sourceInfo(SourceInfo.newBuilder()
                    .filename(ORIG_FILENAME)
                    .flow(FLOW)
                    .build())
            .formattedData(Collections.singletonList(
                    FormattedData.newBuilder()
                            .filename(POST_FILENAME)
                            .objectReference(objectReference)
                            .build()
            ))
            .build();
    RestPostEgressParameters params = new RestPostEgressParameters(ACTION, Collections.emptyMap(), EGRESS_FLOW, URL, PREFIX);

    @BeforeEach
    void setup() throws ObjectStorageException {
        inMemoryObjectStorageService.clear();
        inMemoryObjectStorageService.putObject(objectReference.getBucket(), objectReference.getName(), data.getBytes());
    }

    @Test
    void execute() throws IOException {
        Result result = action.execute(deltaFile, params);

        verify(httpService).post(eq(URL), mapCaptor.capture(), isCaptor.capture());
        Map<String, String> actual = mapCaptor.getValue();
        assertEquals(DID, actual.get(PREFIX + "did"));
        assertEquals(FLOW, actual.get(PREFIX + "ingressFlow"));
        assertEquals(EGRESS_FLOW, actual.get(PREFIX + "flow"));
        assertEquals(POST_FILENAME, actual.get(PREFIX + "filename"));
        assertEquals(ORIG_FILENAME, actual.get(PREFIX + "originalFilename"));

        assertEquals(data, new String(isCaptor.getValue().readAllBytes()));

        assertTrue(result instanceof EgressResult);
        assertEquals(DID, result.toEvent().getDid());
        assertEquals(ACTION, result.toEvent().getAction());
    }

    @Test
    void executeMissingData() {
        inMemoryObjectStorageService.clear();
        Result result = action.execute(deltaFile, params);

        verify(httpService, never()).post(any(), any(), any());

        assertTrue(result instanceof ErrorResult);
        assertEquals(DID, result.toEvent().getDid());
        assertEquals(ACTION, result.toEvent().getAction());
    }
}