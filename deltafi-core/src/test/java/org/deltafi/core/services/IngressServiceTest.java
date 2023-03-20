/**
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
package org.deltafi.core.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException;
import lombok.SneakyThrows;
import org.deltafi.common.content.ContentReference;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.content.Segment;
import org.deltafi.common.types.DeltaFile;
import org.deltafi.common.types.SourceInfo;
import org.deltafi.core.MockDeltaFiPropertiesService;
import org.deltafi.core.configuration.DeltaFiProperties;
import org.deltafi.core.exceptions.IngressException;
import org.deltafi.core.exceptions.IngressMetadataException;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class IngressServiceTest {

    public static final String OBJECT_NAME = "in.txt";
    public static final String FLOW = "flow";
    public static final String FLOW_PLAN = "namespace";
    public static final String FULL_FLOW_NAME = FLOW_PLAN + "." + FLOW;

    @InjectMocks
    IngressService ingressService;

    @Mock
    ContentStorageService contentStorageService;

    @Mock
    DeltaFilesService deltaFilesService;

    @Mock
    IngressFlowService ingressFlowService;

    @Mock
    FlowAssignmentService flowAssignmentService;

    @Mock
    @SuppressWarnings("unused")
    ErrorCountService errorCountService;

    @Spy
    DeltaFiPropertiesService deltaFiPropertiesService = new MockDeltaFiPropertiesService();

    @Spy
    @SuppressWarnings("unused")
    ObjectMapper objectMapper = new ObjectMapper();

    @Test @SneakyThrows
    void ingressData() {
        ContentReference contentReference = new ContentReference("application/octet-stream", new Segment("fileName", "did"));
        Mockito.when(contentStorageService.save(any(), (InputStream) isNull(), eq(MediaType.APPLICATION_JSON))).thenReturn(contentReference);

        DeltaFile deltaFile = DeltaFile.newBuilder().sourceInfo(SourceInfo.builder().flow("namespace.flow").build()).build();
        Mockito.when(deltaFilesService.ingress(any())).thenReturn(deltaFile);

        IngressService.IngressResult created = ingressService.ingressData(null, OBJECT_NAME, FULL_FLOW_NAME, Collections.emptyMap(), MediaType.APPLICATION_JSON);

        Mockito.verify(contentStorageService).save(any(), (InputStream) isNull(), eq(MediaType.APPLICATION_JSON));
        Mockito.verify(deltaFilesService).ingress(any());
        Assertions.assertNotNull(created.getContentReference().getSegments().get(0).getDid());
    }

    @Test @SneakyThrows
    void isEnabled() {
        Assertions.assertTrue(ingressService.isEnabled());
        deltaFiProperties().getIngress().setEnabled(false);
        Assertions.assertFalse(ingressService.isEnabled());
    }

    @Test
    void isStorageAvailable() {
    }

    @Test @SneakyThrows
    void ingressDataThrowsOnMissingFlow() {
        Mockito.when(ingressFlowService.getRunningFlowByName(any())).thenThrow(new DgsEntityNotFoundException("Not here"));

        assertThrows(
                IngressException.class,
                () -> ingressService.ingressData(null, OBJECT_NAME, "blarg", Collections.emptyMap(), MediaType.APPLICATION_JSON)
        );

        Mockito.verifyNoInteractions(contentStorageService);
    }

    @Test @SneakyThrows
    void ingressDataThrowsOnMissingFilename() {
        assertThrows(
                IngressException.class,
                () -> ingressService.ingressData(null, null, FULL_FLOW_NAME, Collections.emptyMap(), MediaType.APPLICATION_JSON)
        );

        Mockito.verifyNoInteractions(contentStorageService);
    }

    @Test @SneakyThrows
    void ingressDataThrowsOnFailedFlowLookup() {

        Mockito.when(flowAssignmentService.findFlow(any())).thenReturn(null);

        assertThrows(
                IngressException.class,
                () -> ingressService.ingressData(null, OBJECT_NAME, null, Collections.emptyMap(), MediaType.APPLICATION_JSON)
        );

        Mockito.verifyNoInteractions(contentStorageService);
    }

    @Test @SneakyThrows
    void ingressData_unexpectedException() {
        ContentReference contentReference = new ContentReference("application/octet-stream", new Segment("fileName", "did"));
        Mockito.when(contentStorageService.save(any(), (InputStream) isNull(), eq(MediaType.APPLICATION_JSON))).thenReturn(contentReference);

        Mockito.when(deltaFilesService.ingress(any())).thenThrow(new RuntimeException("failed to send to dgs"));

        Assertions.assertThrows(RuntimeException.class, () -> ingressService.ingressData(null, OBJECT_NAME, FULL_FLOW_NAME, Collections.emptyMap(), MediaType.APPLICATION_JSON));

        Mockito.verify(contentStorageService).delete(any());
    }

    @Test @SneakyThrows
    void fromMetadataString() {
        String metadata = "{\"simple\": \"value\"}";

        Map<String, String> map = ingressService.fromMetadataString(metadata);
        Assertions.assertEquals(1, map.size());
        Assertions.assertEquals("value", map.get("simple"));
    }

    @Test @SneakyThrows
    void fromMetadataString_subObject() {
        String metadata = "{\"complex\": {\"key\": {\"list\": [1, 2, 3]}}}";

        Map<String, String> map = ingressService.fromMetadataString(metadata);
        Assertions.assertEquals(1, map.size());
        Assertions.assertEquals("{\"key\":{\"list\":[1,2,3]}}", map.get("complex"));
    }

    @Test
    void fromMetadataString_fail() {
        String metadata = "[\"bad\"]";
        Assertions.assertThrows(IngressMetadataException.class, () -> ingressService.fromMetadataString(metadata));
    }

    private DeltaFiProperties deltaFiProperties() {
        return deltaFiPropertiesService.getDeltaFiProperties();
    }
}
