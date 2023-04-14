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
package org.deltafi.core.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.nifi.ContentType;
import org.deltafi.common.nifi.FlowFileUtil;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.test.storage.s3.InMemoryObjectStorageService;
import org.deltafi.common.test.time.TestClock;
import org.deltafi.common.test.uuid.TestUUIDGenerator;
import org.deltafi.common.types.DeltaFile;
import org.deltafi.common.types.IngressEvent;
import org.deltafi.common.types.SourceInfo;
import org.deltafi.core.audit.CoreAuditLogger;
import org.deltafi.core.configuration.DeltaFiProperties;
import org.deltafi.core.configuration.IngressProperties;
import org.deltafi.core.exceptions.IngressException;
import org.deltafi.core.exceptions.IngressMetadataException;
import org.deltafi.core.exceptions.IngressStorageException;
import org.deltafi.core.exceptions.IngressUnavailableException;
import org.deltafi.core.metrics.MetricService;
import org.deltafi.core.types.IngressFlow;
import org.deltafi.core.types.IngressResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.ws.rs.core.MediaType;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class IngressServiceTest {
    private static final TestUUIDGenerator UUID_GENERATOR = new TestUUIDGenerator();
    private static final TestClock CLOCK = new TestClock();

    private static final ContentStorageService CONTENT_STORAGE_SERVICE =
            new ContentStorageService(new InMemoryObjectStorageService());

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final MetricService metricService;
    private final CoreAuditLogger coreAuditLogger;
    private final DiskSpaceService diskSpaceService;
    private final DeltaFilesService deltaFilesService;
    private final DeltaFiPropertiesService deltaFiPropertiesService;
    private final FlowAssignmentService flowAssignmentService;
    private final IngressFlowService ingressFlowService;
    private final TransformFlowService transformFlowService;
    private final ErrorCountService errorCountService;

    private final IngressService ingressService;

    @Captor
    ArgumentCaptor<IngressEvent> ingressEventCaptor;

    IngressServiceTest(@Mock MetricService metricService, @Mock CoreAuditLogger coreAuditLogger,
                       @Mock DiskSpaceService diskSpaceService, @Mock DeltaFilesService deltaFilesService,
                       @Mock DeltaFiPropertiesService deltaFiPropertiesService, @Mock FlowAssignmentService flowAssignmentService,
                       @Mock IngressFlowService ingressFlowService, @Mock TransformFlowService transformFlowService,
                       @Mock ErrorCountService errorCountService) {
        this.metricService = metricService;
        this.coreAuditLogger = coreAuditLogger;
        this.diskSpaceService = diskSpaceService;
        this.deltaFilesService = deltaFilesService;
        this.deltaFiPropertiesService = deltaFiPropertiesService;
        this.flowAssignmentService = flowAssignmentService;
        this.ingressFlowService = ingressFlowService;
        this.transformFlowService = transformFlowService;
        this.errorCountService = errorCountService;

        ingressService = new IngressService(metricService, coreAuditLogger, diskSpaceService, CONTENT_STORAGE_SERVICE,
                deltaFilesService, deltaFiPropertiesService, flowAssignmentService, ingressFlowService,
                transformFlowService, errorCountService, UUID_GENERATOR, CLOCK);
    }

    @Test
    void ingressBinaryFile() throws IngressUnavailableException, ObjectStorageException, IngressStorageException,
            IngressMetadataException, IngressException, IOException {
        mockNormalExecution();

        Map<String, String> headerMetadata = Map.of("k1", "v1", "k2", "v2");
        verifyNormalExecution(ingressService.ingress("flow", "filename", MediaType.APPLICATION_OCTET_STREAM,
                "username", OBJECT_MAPPER.writeValueAsString(headerMetadata), new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8))
        ));

        IngressEvent ingressEvent = ingressEventCaptor.getValue();
        assertEquals(2, ingressEvent.getSourceInfo().getMetadata().size());
    }

    private void mockNormalExecution() {
        mockExecution(true);
    }

    private void mockExecution(boolean flowFound) {
        DeltaFiProperties deltaFiProperties = new DeltaFiProperties();
        IngressProperties ingressProperties = new IngressProperties();
        ingressProperties.setEnabled(true);
        deltaFiProperties.setIngress(ingressProperties);
        Mockito.when(deltaFiPropertiesService.getDeltaFiProperties()).thenReturn(deltaFiProperties);
        Mockito.when(diskSpaceService.isContentStorageDepleted()).thenReturn(false);
        Mockito.when(ingressFlowService.hasRunningFlow(any())).thenReturn(flowFound);
        Mockito.when(transformFlowService.hasRunningFlow(any())).thenReturn(flowFound);
        Mockito.when(ingressFlowService.maxErrorsPerFlow()).thenReturn(Map.of("flow", 1));
        Mockito.when(ingressFlowService.getRunningFlowByName("flow")).thenReturn(new IngressFlow());
        Mockito.when(errorCountService.generateErrorMessage("flow", 1)).thenReturn(null);
        DeltaFile deltaFile = DeltaFile.newBuilder().sourceInfo(SourceInfo.builder().flow("flow").build()).build();
        Mockito.when(deltaFilesService.ingress(ingressEventCaptor.capture())).thenReturn(deltaFile);

        UUID_GENERATOR.setUuid("TEST-UUID");
        CLOCK.setInstant(Instant.ofEpochMilli(12345));
    }

    private void verifyNormalExecution(IngressResult ingressResult) throws IOException, ObjectStorageException {
        Mockito.verify(coreAuditLogger).logIngress("username", "filename");

        Map<String, String> metricTags = Map.of(DeltaFiConstants.ACTION, "ingress",
                DeltaFiConstants.SOURCE, DeltaFiConstants.INGRESS_ACTION, DeltaFiConstants.INGRESS_FLOW, "flow");
        Mockito.verify(metricService).increment(DeltaFiConstants.FILES_IN, metricTags, 1);
        Mockito.verify(metricService).increment(DeltaFiConstants.BYTES_IN, metricTags, "content".length());

        String content;
        try (InputStream contentInputStream = CONTENT_STORAGE_SERVICE.load(ingressResult.contentReference())) {
            content = new String(contentInputStream.readAllBytes());
        }
        assertEquals("content", content);
        assertEquals("flow", ingressResult.flow());
        assertEquals("filename", ingressResult.filename());
        assertEquals("TEST-UUID", ingressResult.did());
    }

    @Test
    void ingressFlowFileV1() throws IngressUnavailableException, ObjectStorageException, IngressStorageException,
            IngressMetadataException, IngressException, IOException {
        mockNormalExecution();

        Map<String, String> headerMetadata = Map.of("k1", "v1", "k2", "v2");
        Map<String, String> flowFileAttributes = Map.of("k1", "b", "encodedString", "\uD84E\uDCE7");
        verifyNormalExecution(ingressService.ingress("flow", "filename", ContentType.APPLICATION_FLOWFILE_V_1,
                "username", OBJECT_MAPPER.writeValueAsString(headerMetadata), new ByteArrayInputStream(FlowFileUtil.packageFlowFileV1(
                        flowFileAttributes, new ByteArrayInputStream(
                                "content".getBytes(StandardCharsets.UTF_8)), "content".length()))
        ));

        IngressEvent ingressEvent = ingressEventCaptor.getValue();
        assertEquals(3, ingressEvent.getSourceInfo().getMetadata().size());
        assertEquals("v1", ingressEvent.getSourceInfo().getMetadata().get("k1"));
        assertEquals("\uD84E\uDCE7", ingressEvent.getSourceInfo().getMetadata().get("encodedString"));
    }

    @Test
    void ingressFlowFileV2() throws IngressUnavailableException, ObjectStorageException, IngressStorageException,
            IngressMetadataException, IngressException, IOException {
        mockNormalExecution();

        Map<String, String> headerMetadata = Map.of("k1", "v1", "k2", "v2");
        Map<String, String> flowFileAttributes = Map.of("k1", "b", "encodedString", "\uD84E\uDCE7");
        verifyNormalExecution(ingressService.ingress("flow", "filename", ContentType.APPLICATION_FLOWFILE_V_2,
                "username", OBJECT_MAPPER.writeValueAsString(headerMetadata), new ByteArrayInputStream(FlowFileUtil.packageFlowFileV2(
                        flowFileAttributes, new ByteArrayInputStream(
                                "content".getBytes(StandardCharsets.UTF_8)), "content".length()))
        ));

        IngressEvent ingressEvent = ingressEventCaptor.getValue();
        assertEquals(3, ingressEvent.getSourceInfo().getMetadata().size());
        assertEquals("v1", ingressEvent.getSourceInfo().getMetadata().get("k1"));
        assertEquals("\uD84E\uDCE7", ingressEvent.getSourceInfo().getMetadata().get("encodedString"));
    }

    @Test
    void ingressFlowFileV3() throws IngressUnavailableException, ObjectStorageException, IngressStorageException,
            IngressMetadataException, IngressException, IOException {
        mockNormalExecution();

        Map<String, String> headerMetadata = Map.of("k1", "v1", "k2", "v2");
        Map<String, String> flowFileAttributes = Map.of("k1", "b", "encodedString", "\uD84E\uDCE7");
        verifyNormalExecution(ingressService.ingress("flow", "filename", ContentType.APPLICATION_FLOWFILE_V_3,
                "username", OBJECT_MAPPER.writeValueAsString(headerMetadata), new ByteArrayInputStream(FlowFileUtil.packageFlowFileV3(
                        flowFileAttributes, new ByteArrayInputStream(
                                "content".getBytes(StandardCharsets.UTF_8)), "content".length()))
        ));

        IngressEvent ingressEvent = ingressEventCaptor.getValue();
        assertEquals(3, ingressEvent.getSourceInfo().getMetadata().size());
        assertEquals("v1", ingressEvent.getSourceInfo().getMetadata().get("k1"));
        assertEquals("\uD84E\uDCE7", ingressEvent.getSourceInfo().getMetadata().get("encodedString"));
    }

    @Test
    void ingressDisabled() {
        DeltaFiProperties deltaFiProperties = new DeltaFiProperties();
        IngressProperties ingressProperties = new IngressProperties();
        ingressProperties.setEnabled(false);
        deltaFiProperties.setIngress(ingressProperties);
        Mockito.when(deltaFiPropertiesService.getDeltaFiProperties()).thenReturn(deltaFiProperties);

        assertThrows(IngressUnavailableException.class,
                () -> ingressService.ingress("flow", "filename", MediaType.APPLICATION_OCTET_STREAM,
                        "username", OBJECT_MAPPER.writeValueAsString(Map.of()), new ByteArrayInputStream("binary content".getBytes(StandardCharsets.UTF_8))
                )
        );
    }

    @Test
    void ingressStorageDepleted() {
        DeltaFiProperties deltaFiProperties = new DeltaFiProperties();
        IngressProperties ingressProperties = new IngressProperties();
        ingressProperties.setEnabled(true);
        deltaFiProperties.setIngress(ingressProperties);
        Mockito.when(deltaFiPropertiesService.getDeltaFiProperties()).thenReturn(deltaFiProperties);
        Mockito.when(diskSpaceService.isContentStorageDepleted()).thenReturn(true);

        assertThrows(IngressStorageException.class,
                () -> ingressService.ingress("flow", "filename", MediaType.APPLICATION_OCTET_STREAM,
                        "username", OBJECT_MAPPER.writeValueAsString(Map.of()), new ByteArrayInputStream("binary content".getBytes(StandardCharsets.UTF_8))
                )
        );
    }

    @Test
    void ingressBadHeaderMetadata() {
        DeltaFiProperties deltaFiProperties = new DeltaFiProperties();
        IngressProperties ingressProperties = new IngressProperties();
        ingressProperties.setEnabled(true);
        deltaFiProperties.setIngress(ingressProperties);
        Mockito.when(deltaFiPropertiesService.getDeltaFiProperties()).thenReturn(deltaFiProperties);
        Mockito.when(diskSpaceService.isContentStorageDepleted()).thenReturn(false);

        assertThrows(IngressMetadataException.class,
                () -> ingressService.ingress("flow", "filename", MediaType.APPLICATION_OCTET_STREAM,
                        "username", "bad header metadata", new ByteArrayInputStream("binary content".getBytes(StandardCharsets.UTF_8))
                )
        );

        Map<String, String> metricTags = Map.of(DeltaFiConstants.ACTION, "ingress", DeltaFiConstants.SOURCE,
                DeltaFiConstants.INGRESS_ACTION, DeltaFiConstants.INGRESS_FLOW, "flow");
        Mockito.verify(metricService).increment(DeltaFiConstants.FILES_DROPPED, metricTags, 1);
    }

    @Test
    void ingressBinaryFileMissingFilename() {
        mockNormalExecution();

        Map<String, String> headerMetadata = Map.of("k1", "v1", "k2", "v2");
        assertThrows(IngressMetadataException.class,
                () -> ingressService.ingress("flow", null, MediaType.APPLICATION_OCTET_STREAM,
                        "username", OBJECT_MAPPER.writeValueAsString(headerMetadata), new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8))
                )
        );

        Map<String, String> metricTags = Map.of(DeltaFiConstants.ACTION, "ingress", DeltaFiConstants.SOURCE,
                DeltaFiConstants.INGRESS_ACTION, DeltaFiConstants.INGRESS_FLOW, "flow");
        Mockito.verify(metricService).increment(DeltaFiConstants.FILES_DROPPED, metricTags, 1);
    }

    @Test
    void ingressFlowFileV1FlowInHeader() throws IngressUnavailableException, ObjectStorageException,
            IngressStorageException, IngressMetadataException, IngressException, IOException {
        mockNormalExecution();

        Map<String, String> headerMetadata = Map.of("k1", "v1", "flow", "flow");
        Map<String, String> flowFileAttributes = Map.of("k1", "b", "encodedString", "\uD84E\uDCE7");
        verifyNormalExecution(ingressService.ingress(null, "filename", ContentType.APPLICATION_FLOWFILE_V_1,
                "username", OBJECT_MAPPER.writeValueAsString(headerMetadata), new ByteArrayInputStream(FlowFileUtil.packageFlowFileV1(
                        flowFileAttributes, new ByteArrayInputStream(
                                "content".getBytes(StandardCharsets.UTF_8)), "content".length()))
        ));

        IngressEvent ingressEvent = ingressEventCaptor.getValue();
        assertEquals(3, ingressEvent.getSourceInfo().getMetadata().size());
        assertEquals("v1", ingressEvent.getSourceInfo().getMetadata().get("k1"));
        assertEquals("\uD84E\uDCE7", ingressEvent.getSourceInfo().getMetadata().get("encodedString"));
    }

    @Test
    void ingressFlowFileV1FlowInAttributes() throws IngressUnavailableException, ObjectStorageException,
            IngressStorageException, IngressMetadataException, IngressException, IOException {
        mockNormalExecution();

        Map<String, String> headerMetadata = Map.of("k1", "v1", "k2", "v2");
        Map<String, String> flowFileAttributes = Map.of("k1", "b", "flow", "flow");
        verifyNormalExecution(ingressService.ingress(null, "filename", ContentType.APPLICATION_FLOWFILE_V_1,
                "username", OBJECT_MAPPER.writeValueAsString(headerMetadata), new ByteArrayInputStream(FlowFileUtil.packageFlowFileV1(
                        flowFileAttributes, new ByteArrayInputStream(
                                "content".getBytes(StandardCharsets.UTF_8)), "content".length()))
        ));

        IngressEvent ingressEvent = ingressEventCaptor.getValue();
        assertEquals(3, ingressEvent.getSourceInfo().getMetadata().size());
        assertEquals("v1", ingressEvent.getSourceInfo().getMetadata().get("k1"));
    }

    @Test
    void ingressFlowFileV1AutoResolvesFlow() throws IOException, IngressUnavailableException, ObjectStorageException,
            IngressStorageException, IngressMetadataException, IngressException {
        mockNormalExecution();

        Mockito.when(flowAssignmentService.findFlow(any())).thenReturn("flow");

        Map<String, String> headerMetadata = Map.of("k1", "v1", "k2", "v2");
        Map<String, String> flowFileAttributes = Map.of("k1", "b", "encodedString", "\uD84E\uDCE7");
        verifyNormalExecution(ingressService.ingress(null, "filename", ContentType.APPLICATION_FLOWFILE_V_1,
                "username", OBJECT_MAPPER.writeValueAsString(headerMetadata), new ByteArrayInputStream(FlowFileUtil.packageFlowFileV1(
                        flowFileAttributes, new ByteArrayInputStream(
                                "content".getBytes(StandardCharsets.UTF_8)), "content".length()))
        ));

        IngressEvent ingressEvent = ingressEventCaptor.getValue();
        assertEquals(3, ingressEvent.getSourceInfo().getMetadata().size());
        assertEquals("v1", ingressEvent.getSourceInfo().getMetadata().get("k1"));
    }

    @Test
    void ingressFlowFileV1FilenameInHeader() throws IngressUnavailableException, ObjectStorageException,
            IngressStorageException, IngressMetadataException, IngressException, IOException {
        mockNormalExecution();

        Map<String, String> headerMetadata = Map.of("k1", "v1", "filename", "filename");
        Map<String, String> flowFileAttributes = Map.of("k1", "b", "encodedString", "\uD84E\uDCE7");
        verifyNormalExecution(ingressService.ingress("flow", null, ContentType.APPLICATION_FLOWFILE_V_1,
                "username", OBJECT_MAPPER.writeValueAsString(headerMetadata), new ByteArrayInputStream(FlowFileUtil.packageFlowFileV1(
                        flowFileAttributes, new ByteArrayInputStream(
                                "content".getBytes(StandardCharsets.UTF_8)), "content".length()))
        ));

        IngressEvent ingressEvent = ingressEventCaptor.getValue();
        assertEquals(3, ingressEvent.getSourceInfo().getMetadata().size());
        assertEquals("v1", ingressEvent.getSourceInfo().getMetadata().get("k1"));
        assertEquals("\uD84E\uDCE7", ingressEvent.getSourceInfo().getMetadata().get("encodedString"));
    }

    @Test
    void ingressFlowFileV1FilenameInAttributes() throws IngressUnavailableException, ObjectStorageException,
            IngressStorageException, IngressMetadataException, IngressException, IOException {
        mockNormalExecution();

        Map<String, String> headerMetadata = Map.of("k1", "v1", "k2", "v2");
        Map<String, String> flowFileAttributes = Map.of("k1", "b", "filename", "filename");
        verifyNormalExecution(ingressService.ingress("flow", null, ContentType.APPLICATION_FLOWFILE_V_1,
                "username", OBJECT_MAPPER.writeValueAsString(headerMetadata), new ByteArrayInputStream(FlowFileUtil.packageFlowFileV1(
                        flowFileAttributes, new ByteArrayInputStream(
                                "content".getBytes(StandardCharsets.UTF_8)), "content".length()))
        ));

        IngressEvent ingressEvent = ingressEventCaptor.getValue();
        assertEquals(3, ingressEvent.getSourceInfo().getMetadata().size());
        assertEquals("v1", ingressEvent.getSourceInfo().getMetadata().get("k1"));
    }

    @Test
    void ingressFlowFileV1MissingFilename() {
        mockNormalExecution();

        Map<String, String> headerMetadata = Map.of("k1", "v1", "k2", "v2");
        Map<String, String> flowFileAttributes = Map.of("k1", "b", "encodedString", "\uD84E\uDCE7");
        assertThrows(IngressMetadataException.class,
                () -> ingressService.ingress("flow", null, ContentType.APPLICATION_FLOWFILE_V_1,
                        "username", OBJECT_MAPPER.writeValueAsString(headerMetadata), new ByteArrayInputStream(
                                FlowFileUtil.packageFlowFileV1(
                                        flowFileAttributes, new ByteArrayInputStream(
                                                "content".getBytes(StandardCharsets.UTF_8)), "content".length()))
                )
        );

        Map<String, String> metricTags = Map.of(DeltaFiConstants.ACTION, "ingress", DeltaFiConstants.SOURCE,
                DeltaFiConstants.INGRESS_ACTION, DeltaFiConstants.INGRESS_FLOW, "flow");
        Mockito.verify(metricService).increment(DeltaFiConstants.FILES_DROPPED, metricTags, 1);
    }

    @Test
    void ingressBinaryAutoResolvesFlow() throws IOException, IngressUnavailableException, ObjectStorageException,
            IngressStorageException, IngressMetadataException, IngressException {
        mockNormalExecution();

        Mockito.when(flowAssignmentService.findFlow(any())).thenReturn("flow");

        Map<String, String> headerMetadata = Map.of("k1", "v1", "k2", "v2");
        verifyNormalExecution(ingressService.ingress(null, "filename", MediaType.APPLICATION_OCTET_STREAM,
                "username", OBJECT_MAPPER.writeValueAsString(headerMetadata), new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8))
        ));

        IngressEvent ingressEvent = ingressEventCaptor.getValue();
        assertEquals(2, ingressEvent.getSourceInfo().getMetadata().size());
    }

    @Test
    void ingressBinaryAutoResolveFlowFails() {
        mockNormalExecution();

        Mockito.when(flowAssignmentService.findFlow(any())).thenReturn(null);

        Map<String, String> headerMetadata = Map.of("k1", "v1", "k2", "v2");
        assertThrows(IngressException.class,
                () -> ingressService.ingress(DeltaFiConstants.AUTO_RESOLVE_FLOW_NAME, "filename",
                        MediaType.APPLICATION_OCTET_STREAM,
                        "username", OBJECT_MAPPER.writeValueAsString(headerMetadata), new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8))
                )
        );

        Map<String, String> metricTags = Map.of(DeltaFiConstants.ACTION, "ingress", DeltaFiConstants.SOURCE,
                DeltaFiConstants.INGRESS_ACTION, DeltaFiConstants.INGRESS_FLOW, DeltaFiConstants.AUTO_RESOLVE_FLOW_NAME);
        Mockito.verify(metricService).increment(DeltaFiConstants.FILES_DROPPED, metricTags, 1);
    }

    @Test
    void ingressBinaryFlowNotRunning() {
        mockExecution(false);

        Map<String, String> headerMetadata = Map.of("k1", "v1", "k2", "v2");
        IngressException ingressException = assertThrows(IngressException.class,
                () -> ingressService.ingress("flow", "filename", MediaType.APPLICATION_OCTET_STREAM,
                        "username", OBJECT_MAPPER.writeValueAsString(headerMetadata), new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8))
                )
        );

        assertEquals("Flow flow is not running", ingressException.getMessage());

        Map<String, String> metricTags = Map.of(DeltaFiConstants.ACTION, "ingress", DeltaFiConstants.SOURCE,
                DeltaFiConstants.INGRESS_ACTION, DeltaFiConstants.INGRESS_FLOW, "flow");
        Mockito.verify(metricService).increment(DeltaFiConstants.FILES_DROPPED, metricTags, 1);
    }

    @Test
    void ingressBinaryFlowErrorsExceeded() {
        mockNormalExecution();

        Mockito.when(errorCountService.generateErrorMessage("flow", 1)).thenReturn("errors exceeded");

        Map<String, String> headerMetadata = Map.of("k1", "v1", "k2", "v2");
        IngressException ingressException = assertThrows(IngressException.class,
                () -> ingressService.ingress("flow", "filename", MediaType.APPLICATION_OCTET_STREAM,
                        "username", OBJECT_MAPPER.writeValueAsString(headerMetadata), new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8))
                )
        );

        assertEquals("errors exceeded", ingressException.getMessage());

        Map<String, String> metricTags = Map.of(DeltaFiConstants.ACTION, "ingress", DeltaFiConstants.SOURCE,
                DeltaFiConstants.INGRESS_ACTION, DeltaFiConstants.INGRESS_FLOW, "flow");
        Mockito.verify(metricService).increment(DeltaFiConstants.FILES_DROPPED, metricTags, 1);
    }

    @Test
    void ingressBinaryServiceException() {
        mockNormalExecution();

        Mockito.when(deltaFilesService.ingress(ingressEventCaptor.capture())).thenThrow(new RuntimeException());

        Map<String, String> headerMetadata = Map.of("k1", "v1", "k2", "v2");
        assertThrows(IngressException.class,
                () -> ingressService.ingress("flow", "filename", MediaType.APPLICATION_OCTET_STREAM,
                        "username", OBJECT_MAPPER.writeValueAsString(headerMetadata), new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8))
                )
        );

        Mockito.verify(deltaFilesService).deleteContentAndMetadata(any(), any());

        Map<String, String> metricTags = Map.of(DeltaFiConstants.ACTION, "ingress", DeltaFiConstants.SOURCE,
                DeltaFiConstants.INGRESS_ACTION, DeltaFiConstants.INGRESS_FLOW, "flow");
        Mockito.verify(metricService).increment(DeltaFiConstants.FILES_DROPPED, metricTags, 1);
    }

    @Test
    void parseMetadataStringWithSubObject() throws IngressMetadataException {
        String metadata = "{\"complex\": {\"key\": {\"list\": [1, 2, 3]}}}";

        Map<String, String> map = ingressService.parseMetadata(metadata);
        Assertions.assertEquals(1, map.size());
        Assertions.assertEquals("{\"key\":{\"list\":[1,2,3]}}", map.get("complex"));
    }
}
