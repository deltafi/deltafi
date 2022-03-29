package org.deltafi.ingress.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.graphql.dgs.client.GraphQLClient;
import com.netflix.graphql.dgs.client.GraphQLResponse;
import org.deltafi.common.content.ContentReference;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.trace.ZipkinService;
import org.deltafi.core.domain.api.types.KeyValue;
import org.deltafi.ingress.exceptions.DeltafiException;
import org.deltafi.ingress.exceptions.DeltafiGraphQLException;
import org.deltafi.ingress.exceptions.DeltafiMetadataException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.ws.rs.core.MediaType;
import java.io.InputStream;
import java.util.List;

import static org.deltafi.common.constant.DeltaFiConstants.INGRESS_ACTION;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class DeltaFileServiceTest {

    public static final String OBJECT_NAME = "in.txt";
    public static final String FLOW = "flow";

    @InjectMocks
    DeltaFileService deltaFileService;

    @Mock
    ContentStorageService contentStorageService;

    @Mock
    GraphQLClient graphQLClient;

    @Mock
    ZipkinService zipkinService;

    @Spy
    @SuppressWarnings("unused")
    ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void ingressData() throws ObjectStorageException, DeltafiException, DeltafiMetadataException {
        ContentReference contentReference = new ContentReference("fileName", "did", "application/octet-stream");
        Mockito.when(contentStorageService.save(any(), (InputStream) isNull(), eq(MediaType.APPLICATION_JSON))).thenReturn(contentReference);

        GraphQLResponse dgsResponse = new GraphQLResponse("{\"data\": {}, \"errors\": []}");
        Mockito.when(graphQLClient.executeQuery(any())).thenReturn(dgsResponse);
        Mockito.when(zipkinService.isEnabled()).thenReturn(true);

        String did = deltaFileService.ingressData(null, OBJECT_NAME, FLOW, null, MediaType.APPLICATION_JSON);

        Mockito.verify(contentStorageService).save(eq(did), (InputStream) isNull(), eq(MediaType.APPLICATION_JSON));
        Mockito.verify(graphQLClient).executeQuery(any());
        Mockito.verify(zipkinService).createChildSpan(eq(did), eq(INGRESS_ACTION), eq(OBJECT_NAME), eq(FLOW), any());
        Mockito.verify(zipkinService).markSpanComplete(any());
        Assertions.assertNotNull(did);
    }

    @Test
    void ingressData_graphqlErrors() throws ObjectStorageException {
        ContentReference contentReference = new ContentReference("fileName", "did", "application/octet-stream");
        Mockito.when(contentStorageService.save(any(), (InputStream) isNull(), eq(MediaType.APPLICATION_JSON))).thenReturn(contentReference);

        GraphQLResponse dgsResponse = new GraphQLResponse("{\"data\": {}, \"errors\": [{\"message\": \"Bad graphql mutation\"}]}");
        Mockito.when(graphQLClient.executeQuery(any())).thenReturn(dgsResponse);

        Assertions.assertThrows(DeltafiGraphQLException.class, () -> deltaFileService.ingressData(null, OBJECT_NAME, FLOW, null, MediaType.APPLICATION_JSON));

        Mockito.verify(contentStorageService).delete(any());
    }

    @Test
    void ingressData_dgsFail() throws ObjectStorageException {
        ContentReference contentReference = new ContentReference("fileName", "did", "application/octet-stream");
        Mockito.when(contentStorageService.save(any(), (InputStream) isNull(), eq(MediaType.APPLICATION_JSON))).thenReturn(contentReference);

        Mockito.when(graphQLClient.executeQuery(any())).thenThrow(new DeltafiGraphQLException("failed to send to dgs"));

        DeltafiGraphQLException e = Assertions.assertThrows(DeltafiGraphQLException.class,
                () -> deltaFileService.ingressData(null, OBJECT_NAME, FLOW, null, MediaType.APPLICATION_JSON));
        Assertions.assertEquals("failed to send to dgs", e.getMessage());

        Mockito.verify(contentStorageService).delete(any());
    }

    @Test
    void ingressData_unexpectedException() throws ObjectStorageException {
        ContentReference contentReference = new ContentReference("fileName", "did", "application/octet-stream");
        Mockito.when(contentStorageService.save(any(), (InputStream) isNull(), eq(MediaType.APPLICATION_JSON))).thenReturn(contentReference);

        Mockito.when(graphQLClient.executeQuery(any())).thenThrow(new RuntimeException("failed to send to dgs"));

        Assertions.assertThrows(DeltafiException.class, () -> deltaFileService.ingressData(null, OBJECT_NAME, FLOW, null, MediaType.APPLICATION_JSON));

        Mockito.verify(contentStorageService).delete(any());
    }

    @Test
    void fromMetadataString() throws DeltafiMetadataException {
        String metadata = "{\"simple\": \"value\"}";

        List<KeyValue> keyValues = deltaFileService.fromMetadataString(metadata);
        Assertions.assertEquals(1, keyValues.size());
        Assertions.assertEquals("simple", keyValues.get(0).getKey());
        Assertions.assertEquals("value", keyValues.get(0).getValue());
    }

    @Test
    void fromMetadataString_subObject() throws DeltafiMetadataException {
        String metadata = "{\"complex\": {\"key\": {\"list\": [1, 2, 3]}}}";

        List<KeyValue> keyValues = deltaFileService.fromMetadataString(metadata);
        Assertions.assertEquals(1, keyValues.size());
        Assertions.assertEquals("complex", keyValues.get(0).getKey());
        Assertions.assertEquals("{\"key\":{\"list\":[1,2,3]}}", keyValues.get(0).getValue());
    }

    @Test
    void fromMetadataString_fail() {
        String metadata = "[\"bad\"]";
        Assertions.assertThrows(DeltafiMetadataException.class, () -> deltaFileService.fromMetadataString(metadata));
    }
}