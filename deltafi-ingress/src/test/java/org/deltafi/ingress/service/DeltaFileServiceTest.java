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
package org.deltafi.ingress.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.graphql.dgs.client.GraphQLClient;
import com.netflix.graphql.dgs.client.GraphQLResponse;
import lombok.SneakyThrows;
import org.deltafi.common.content.ContentReference;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.types.KeyValue;
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
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class DeltaFileServiceTest {

    public static final String OBJECT_NAME = "in.txt";
    public static final String FLOW = "flow";
    public static final String FLOW_PLAN = "namespace";
    public static final String FULL_FLOW_NAME = FLOW_PLAN + "." + FLOW;
    public static final String USERNAME = "username";

    @InjectMocks
    DeltaFileService deltaFileService;

    @Mock
    ContentStorageService contentStorageService;

    @Mock
    GraphQLClientService graphQLClientService;

    @Mock
    GraphQLClient graphQLClient;

    @Spy
    @SuppressWarnings("unused")
    ObjectMapper objectMapper = new ObjectMapper();

    @Test @SneakyThrows
    void ingressData() {
        ContentReference contentReference = new ContentReference("fileName", "did", "application/octet-stream");
        Mockito.when(contentStorageService.save(any(), (InputStream) isNull(), eq(MediaType.APPLICATION_JSON))).thenReturn(contentReference);
        Mockito.when(graphQLClientService.graphQLClient(USERNAME)).thenReturn(graphQLClient);

        GraphQLResponse dgsResponse = new GraphQLResponse("{\"data\": {\"ingress\": {\"sourceInfo\": {\"flow\": \"namespace.flow\"}}} , \"errors\": []}");
        Mockito.when(graphQLClient.executeQuery(any())).thenReturn(dgsResponse);

        DeltaFileService.IngressResult created = deltaFileService.ingressData(null, OBJECT_NAME, FULL_FLOW_NAME, Collections.emptyMap(), MediaType.APPLICATION_JSON, USERNAME);

        Mockito.verify(contentStorageService).save(any(), (InputStream) isNull(), eq(MediaType.APPLICATION_JSON));
        Mockito.verify(graphQLClient).executeQuery(any());
        Assertions.assertNotNull(created.getContentReference().getDid());
    }

    @Test @SneakyThrows
    void ingressData_graphqlErrors() {
        ContentReference contentReference = new ContentReference("fileName", "did", "application/octet-stream");
        Mockito.when(contentStorageService.save(any(), (InputStream) isNull(), eq(MediaType.APPLICATION_JSON))).thenReturn(contentReference);
        Mockito.when(graphQLClientService.graphQLClient(USERNAME)).thenReturn(graphQLClient);

        GraphQLResponse dgsResponse = new GraphQLResponse("{\"data\": {}, \"errors\": [{\"message\": \"Bad graphql mutation\"}]}");
        Mockito.when(graphQLClient.executeQuery(any())).thenReturn(dgsResponse);

        Assertions.assertThrows(DeltafiGraphQLException.class, () -> deltaFileService.ingressData(null, OBJECT_NAME, FULL_FLOW_NAME, Collections.emptyMap(), MediaType.APPLICATION_JSON, USERNAME));

        Mockito.verify(contentStorageService).delete(any());
    }

    @Test @SneakyThrows
    void ingressData_dgsFail() {
        ContentReference contentReference = new ContentReference("fileName", "did", "application/octet-stream");
        Mockito.when(contentStorageService.save(any(), (InputStream) isNull(), eq(MediaType.APPLICATION_JSON))).thenReturn(contentReference);
        Mockito.when(graphQLClientService.graphQLClient(USERNAME)).thenReturn(graphQLClient);

        Mockito.when(graphQLClient.executeQuery(any())).thenThrow(new DeltafiGraphQLException("failed to send to dgs"));

        DeltafiGraphQLException e = Assertions.assertThrows(DeltafiGraphQLException.class,
                () -> deltaFileService.ingressData(null, OBJECT_NAME, FULL_FLOW_NAME, Collections.emptyMap(), MediaType.APPLICATION_JSON, USERNAME));
        Assertions.assertEquals("failed to send to dgs", e.getMessage());

        Mockito.verify(contentStorageService).delete(any());
    }

    @Test @SneakyThrows
    void ingressData_unexpectedException() {
        ContentReference contentReference = new ContentReference("fileName", "did", "application/octet-stream");
        Mockito.when(contentStorageService.save(any(), (InputStream) isNull(), eq(MediaType.APPLICATION_JSON))).thenReturn(contentReference);
        Mockito.when(graphQLClientService.graphQLClient(USERNAME)).thenReturn(graphQLClient);

        Mockito.when(graphQLClient.executeQuery(any())).thenThrow(new RuntimeException("failed to send to dgs"));

        Assertions.assertThrows(DeltafiException.class, () -> deltaFileService.ingressData(null, OBJECT_NAME, FULL_FLOW_NAME, Collections.emptyMap(), MediaType.APPLICATION_JSON, USERNAME));

        Mockito.verify(contentStorageService).delete(any());
    }

    @Test @SneakyThrows
    void fromMetadataString() {
        String metadata = "{\"simple\": \"value\"}";

        List<KeyValue> keyValues = deltaFileService.fromMetadataString(metadata);
        Assertions.assertEquals(1, keyValues.size());
        Assertions.assertEquals("simple", keyValues.get(0).getKey());
        Assertions.assertEquals("value", keyValues.get(0).getValue());
    }

    @Test @SneakyThrows
    void fromMetadataString_subObject() {
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
