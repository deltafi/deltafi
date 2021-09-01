package org.deltafi.ingress.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.graphql.dgs.client.GraphQLResponse;
import io.minio.ObjectWriteResponse;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.common.trace.ZipkinService;
import org.deltafi.core.domain.generated.types.KeyValueInput;
import org.deltafi.core.domain.generated.types.ObjectReferenceInput;
import org.deltafi.ingress.exceptions.DeltafiException;
import org.deltafi.ingress.exceptions.DeltafiGraphQLException;
import org.deltafi.ingress.exceptions.DeltafiMetadataException;
import org.deltafi.ingress.exceptions.DeltafiMinioException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class DeltaFileServiceTest {

    public static final String OBJECT_NAME = "in.txt";
    public static final String FLOW = "flow";

    @InjectMocks
    DeltaFileService deltaFileService;

    @Mock
    MinioService minioService;

    @Mock
    GraphQLClientService graphQLClientService;

    @Mock
    ZipkinService zipkinService;

    @Spy
    @SuppressWarnings("unused")
    ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void ingressData() throws DeltafiMinioException, DeltafiException, DeltafiMetadataException {
        mockMinio();
        GraphQLResponse dgsResponse = new GraphQLResponse("{\"data\": {}, \"errors\": []}");
        Mockito.when(graphQLClientService.executeGraphQLQuery(any())).thenReturn(dgsResponse);

        String did = deltaFileService.ingressData(null, OBJECT_NAME, FLOW, null);

        Mockito.verify(minioService).putObject(did, OBJECT_NAME, null, -1L);
        Mockito.verify(graphQLClientService).executeGraphQLQuery(any());
        Mockito.verify(zipkinService).createChildSpan(eq(did), eq(DeltaFileService.INGRESS_ACTION), eq(OBJECT_NAME), eq(FLOW), any());
        Mockito.verify(zipkinService).markSpanComplete(any());
        Assertions.assertNotNull(did);
    }

    @Test
    void ingressData_graphqlErrors() {
        mockMinio();
        GraphQLResponse dgsResponse = new GraphQLResponse("{\"data\": {}, \"errors\": [{\"message\": \"Bad graphql mutation\"}]}");
        Mockito.when(graphQLClientService.executeGraphQLQuery(any())).thenReturn(dgsResponse);

        Assertions.assertThrows(DeltafiGraphQLException.class, () -> deltaFileService.ingressData(null, OBJECT_NAME, FLOW, null));
        Mockito.verify(minioService).removeObject(DeltaFiConstants.MINIO_BUCKET, OBJECT_NAME);
    }

    @Test
    void ingressData_dgsFail() {
        mockMinio();
        Mockito.when(graphQLClientService.executeGraphQLQuery(any())).thenThrow(new DeltafiGraphQLException("failed to send to dgs"));

        Assertions.assertThrows(DeltafiGraphQLException.class, () -> deltaFileService.ingressData(null, OBJECT_NAME, FLOW, null));

        Mockito.verify(minioService).removeObject(DeltaFiConstants.MINIO_BUCKET, OBJECT_NAME);
    }

    @Test
    void ingressData_unexpectedException() {
        mockMinio();
        Mockito.when(graphQLClientService.executeGraphQLQuery(any())).thenThrow(new RuntimeException("failed to send to dgs"));

        Assertions.assertThrows(DeltafiException.class, () -> deltaFileService.ingressData(null, OBJECT_NAME, FLOW, null));

        Mockito.verify(minioService).removeObject(DeltaFiConstants.MINIO_BUCKET, OBJECT_NAME);
    }

    @Test
    void toObjectReferenceInput() {
        ObjectWriteResponse response = new ObjectWriteResponse(null, "storage", null, OBJECT_NAME, null,null);
        Mockito.when(minioService.getObjectSize(response)).thenReturn(10L);
        ObjectReferenceInput objectReferenceInput = deltaFileService.toObjectReferenceInput(response);

        Assertions.assertEquals("storage", objectReferenceInput.getBucket());
        Assertions.assertEquals(OBJECT_NAME, objectReferenceInput.getName());
        Assertions.assertEquals(0, objectReferenceInput.getOffset());
        Assertions.assertEquals(10, objectReferenceInput.getSize());
    }

    @Test
    void fromMetadataString() throws DeltafiMetadataException {

        String metadata = "{\"simple\": \"value\"}";

        List<KeyValueInput> keyValueInputs = deltaFileService.fromMetadataString(metadata);
        Assertions.assertEquals(1, keyValueInputs.size());
        Assertions.assertEquals("simple", keyValueInputs.get(0).getKey());
        Assertions.assertEquals("value", keyValueInputs.get(0).getValue());
    }

    @Test
    void fromMetadataString_subObject() throws DeltafiMetadataException {

        String metadata = "{\"complex\": {\"key\": {\"list\": [1, 2, 3]}}}";

        List<KeyValueInput> keyValueInputs = deltaFileService.fromMetadataString(metadata);
        Assertions.assertEquals(1, keyValueInputs.size());
        Assertions.assertEquals("complex", keyValueInputs.get(0).getKey());
        Assertions.assertEquals("{\"key\":{\"list\":[1,2,3]}}", keyValueInputs.get(0).getValue());
    }

    @Test
    void fromMetadataString_fail() {
        String metadata = "[\"bad\"]";
        Assertions.assertThrows(DeltafiMetadataException.class, () -> deltaFileService.fromMetadataString(metadata));
    }

    void mockMinio() {
        ObjectWriteResponse response = new ObjectWriteResponse(null, "storage", null, OBJECT_NAME, null,null);
        try {
            Mockito.when(minioService.putObject(any(), eq(OBJECT_NAME), any(), eq(-1L))).thenReturn(response);
        } catch (DeltafiMinioException e) {
            Assertions.fail();
        }
    }
}