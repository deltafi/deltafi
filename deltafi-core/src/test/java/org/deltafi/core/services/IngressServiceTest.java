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
import lombok.SneakyThrows;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.nifi.ContentType;
import org.deltafi.common.nifi.FlowFileInputStream;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.test.storage.s3.InMemoryObjectStorageService;
import org.deltafi.common.test.uuid.TestUUIDGenerator;
import org.deltafi.common.types.DeltaFile;
import org.deltafi.common.types.IngressEventItem;
import org.deltafi.common.types.SourceInfo;
import org.deltafi.core.audit.CoreAuditLogger;
import org.deltafi.core.configuration.DeltaFiProperties;
import org.deltafi.core.configuration.IngressProperties;
import org.deltafi.core.exceptions.IngressException;
import org.deltafi.core.exceptions.IngressMetadataException;
import org.deltafi.core.exceptions.IngressStorageException;
import org.deltafi.core.exceptions.IngressUnavailableException;
import org.deltafi.core.metrics.MetricService;
import org.deltafi.core.types.IngressResult;
import org.deltafi.core.types.NormalizeFlow;
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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class IngressServiceTest {
    private static final TestUUIDGenerator UUID_GENERATOR = new TestUUIDGenerator();

    private static final ContentStorageService CONTENT_STORAGE_SERVICE =
            new ContentStorageService(new InMemoryObjectStorageService());

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final MetricService metricService;
    private final CoreAuditLogger coreAuditLogger;
    private final DiskSpaceService diskSpaceService;
    private final DeltaFilesService deltaFilesService;
    private final DeltaFiPropertiesService deltaFiPropertiesService;
    private final FlowAssignmentService flowAssignmentService;
    private final NormalizeFlowService normalizeFlowService;
    private final TransformFlowService transformFlowService;
    private final ErrorCountService errorCountService;

    private final IngressService ingressService;

    private final ExecutorService executorService = Executors.newCachedThreadPool();

    private static final OffsetDateTime TIME = OffsetDateTime.MAX;

    @Captor
    ArgumentCaptor<IngressEventItem> ingressEventCaptor;

    IngressServiceTest(@Mock MetricService metricService, @Mock CoreAuditLogger coreAuditLogger,
                       @Mock DiskSpaceService diskSpaceService, @Mock DeltaFilesService deltaFilesService,
                       @Mock DeltaFiPropertiesService deltaFiPropertiesService, @Mock FlowAssignmentService flowAssignmentService,
                       @Mock NormalizeFlowService normalizeFlowService, @Mock TransformFlowService transformFlowService,
                       @Mock ErrorCountService errorCountService) {
        this.metricService = metricService;
        this.coreAuditLogger = coreAuditLogger;
        this.diskSpaceService = diskSpaceService;
        this.deltaFilesService = deltaFilesService;
        this.deltaFiPropertiesService = deltaFiPropertiesService;
        this.flowAssignmentService = flowAssignmentService;
        this.normalizeFlowService = normalizeFlowService;
        this.transformFlowService = transformFlowService;
        this.errorCountService = errorCountService;

        ingressService = new IngressService(metricService, coreAuditLogger, diskSpaceService, CONTENT_STORAGE_SERVICE,
                deltaFilesService, deltaFiPropertiesService, flowAssignmentService, normalizeFlowService,
                transformFlowService, errorCountService, UUID_GENERATOR);
    }

    @Test
    @SneakyThrows
    void ingressBinaryFile() {
        mockNormalExecution();

        Map<String, String> headerMetadata = Map.of("k1", "v1", "k2", "v2");
        verifyNormalExecution(ingressService.ingress("flow", "filename", MediaType.APPLICATION_OCTET_STREAM,
                "username", OBJECT_MAPPER.writeValueAsString(headerMetadata),
                new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)), TIME));

        IngressEventItem ingressEventItem = ingressEventCaptor.getValue();
        assertEquals(2, ingressEventItem.getMetadata().size());
    }

    private void mockNormalExecution() {
        mockExecution(true, false);
    }

    private void mockExecution(boolean ingressFlow, boolean transformFlow) {
        DeltaFiProperties deltaFiProperties = new DeltaFiProperties();
        IngressProperties ingressProperties = new IngressProperties();
        ingressProperties.setEnabled(true);
        deltaFiProperties.setIngress(ingressProperties);
        Mockito.when(deltaFiPropertiesService.getDeltaFiProperties()).thenReturn(deltaFiProperties);
        Mockito.when(diskSpaceService.isContentStorageDepleted()).thenReturn(false);
        Mockito.when(normalizeFlowService.hasRunningFlow(any())).thenReturn(ingressFlow);
        Mockito.when(transformFlowService.hasRunningFlow(any())).thenReturn(transformFlow);
        Mockito.when(transformFlowService.maxErrorsPerFlow()).thenReturn(Map.of("flow", 1));
        Mockito.when(normalizeFlowService.maxErrorsPerFlow()).thenReturn(Map.of("flow", 1));
        Mockito.when(normalizeFlowService.getRunningFlowByName("flow")).thenReturn(new NormalizeFlow());
        Mockito.when(errorCountService.generateErrorMessage("flow")).thenReturn(null);
        DeltaFile deltaFile = DeltaFile.builder().sourceInfo(SourceInfo.builder().flow("flow").build()).build();
        Mockito.when(deltaFilesService.ingress(ingressEventCaptor.capture(), any(), any())).thenReturn(deltaFile);

        UUID_GENERATOR.setUuid("TEST-UUID");
    }

    private void verifyNormalExecution(List<IngressResult> ingressResults) throws IOException, ObjectStorageException {
        Mockito.verify(coreAuditLogger).logIngress("username", "filename");

        Map<String, String> metricTags = Map.of(DeltaFiConstants.ACTION, "ingress",
                DeltaFiConstants.SOURCE, DeltaFiConstants.INGRESS_ACTION, DeltaFiConstants.INGRESS_FLOW, "flow");
        Mockito.verify(metricService).increment(DeltaFiConstants.FILES_IN, metricTags, 1);
        Mockito.verify(metricService).increment(DeltaFiConstants.BYTES_IN, metricTags, "content".length());

        String content;
        try (InputStream contentInputStream = CONTENT_STORAGE_SERVICE.load(ingressResults.get(0).content())) {
            content = new String(contentInputStream.readAllBytes());
        }
        assertEquals("content", content);
        assertEquals("flow", ingressResults.get(0).flow());
        assertEquals("filename", ingressResults.get(0).content().getName());
        assertEquals("TEST-UUID", ingressResults.get(0).did());
    }

    @Test
    @SneakyThrows
    void ingressFlowFileV1() {
        mockNormalExecution();

        Map<String, String> headerMetadata = Map.of("k1", "v1", "k2", "v2", "encodedString", "\uD84E\uDCE7");
        List<IngressResult> ingressResults = ingressService.ingress("flow", "filename",
                ContentType.APPLICATION_FLOWFILE_V_1, "username", OBJECT_MAPPER.writeValueAsString(headerMetadata),
                IngressServiceTest.class.getResourceAsStream("/flowfile-v1"), TIME);

        assertEquals(1, ingressResults.size());
        IngressEventItem ingressEventItem = ingressEventCaptor.getValue();
        assertEquals(8, ingressEventItem.getMetadata().size());
        assertEquals("v1", ingressEventItem.getMetadata().get("k1"));
        assertEquals("\uD84E\uDCE7", ingressEventItem.getMetadata().get("encodedString"));
    }

    @Test
    @SneakyThrows
    void ingressFlowFileV2() {
        mockNormalExecution();

        Map<String, String> headerMetadata = Map.of("k1", "v1", "k2", "v2", "encodedString", "\uD84E\uDCE7");
        List<IngressResult> ingressResults = ingressService.ingress("flow", "filename",
                ContentType.APPLICATION_FLOWFILE_V_2, "username", OBJECT_MAPPER.writeValueAsString(headerMetadata),
                IngressServiceTest.class.getResourceAsStream("/flowfile-v2"), TIME);

        assertEquals(5, ingressResults.size());
        IngressEventItem ingressEventItem = ingressEventCaptor.getValue();
        assertEquals(8, ingressEventItem.getMetadata().size());
        assertEquals("v1", ingressEventItem.getMetadata().get("k1"));
        assertEquals("\uD84E\uDCE7", ingressEventItem.getMetadata().get("encodedString"));
    }

    @Test
    @SneakyThrows
    void ingressFlowFileV3() {
        mockNormalExecution();

        Map<String, String> headerMetadata = Map.of("k1", "v1", "k2", "v2", "encodedString", "\uD84E\uDCE7");
        List<IngressResult> ingressResults = ingressService.ingress("flow", "filename",
                ContentType.APPLICATION_FLOWFILE_V_3, "username", OBJECT_MAPPER.writeValueAsString(headerMetadata),
                IngressServiceTest.class.getResourceAsStream("/flowfile-v3"), TIME);

        assertEquals(5, ingressResults.size());
        IngressEventItem ingressEventItem = ingressEventCaptor.getValue();
        assertEquals(8, ingressEventItem.getMetadata().size());
        assertEquals("v1", ingressEventItem.getMetadata().get("k1"));
        assertEquals("\uD84E\uDCE7", ingressEventItem.getMetadata().get("encodedString"));
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
                        "username", OBJECT_MAPPER.writeValueAsString(Map.of()),
                        new ByteArrayInputStream("binary content".getBytes(StandardCharsets.UTF_8)), TIME));
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
                        "username", OBJECT_MAPPER.writeValueAsString(Map.of()),
                        new ByteArrayInputStream("binary content".getBytes(StandardCharsets.UTF_8)), TIME));
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
                        "username", "bad header metadata",
                        new ByteArrayInputStream("binary content".getBytes(StandardCharsets.UTF_8)), TIME));

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
                        "username", OBJECT_MAPPER.writeValueAsString(headerMetadata),
                        new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)), TIME));

        Map<String, String> metricTags = Map.of(DeltaFiConstants.ACTION, "ingress", DeltaFiConstants.SOURCE,
                DeltaFiConstants.INGRESS_ACTION, DeltaFiConstants.INGRESS_FLOW, "flow");
        Mockito.verify(metricService).increment(DeltaFiConstants.FILES_DROPPED, metricTags, 1);
    }

    @Test
    @SneakyThrows
    void ingressFlowFileV1FlowInHeader() {
        mockNormalExecution();

        Map<String, String> headerMetadata = Map.of("k1", "v1", "flow", "flow");
        Map<String, String> flowFileAttributes = Map.of("k1", "b", "encodedString", "\uD84E\uDCE7");
        try (FlowFileInputStream flowFileInputStream = new FlowFileInputStream()) {
            flowFileInputStream.runPipeWriter(new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)),
                    flowFileAttributes, "content".length(), executorService);
            verifyNormalExecution(ingressService.ingress(null, "filename", ContentType.APPLICATION_FLOWFILE_V_1,
                    "username", OBJECT_MAPPER.writeValueAsString(headerMetadata), flowFileInputStream, TIME));
        }

        IngressEventItem ingressEventItem = ingressEventCaptor.getValue();
        assertEquals(3, ingressEventItem.getMetadata().size());
        assertEquals("v1", ingressEventItem.getMetadata().get("k1"));
        assertEquals("\uD84E\uDCE7", ingressEventItem.getMetadata().get("encodedString"));
    }

    @Test
    @SneakyThrows
    void ingressFlowFileV1FlowInAttributes() {
        mockNormalExecution();

        Map<String, String> headerMetadata = Map.of("k1", "v1", "k2", "v2");
        Map<String, String> flowFileAttributes = Map.of("k1", "b", "flow", "flow");
        try (FlowFileInputStream flowFileInputStream = new FlowFileInputStream()) {
            flowFileInputStream.runPipeWriter(new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)),
                    flowFileAttributes, "content".length(), executorService);
            verifyNormalExecution(ingressService.ingress(null, "filename", ContentType.APPLICATION_FLOWFILE_V_1,
                    "username", OBJECT_MAPPER.writeValueAsString(headerMetadata), flowFileInputStream, TIME));
        }

        IngressEventItem ingressEventItem = ingressEventCaptor.getValue();
        assertEquals(3, ingressEventItem.getMetadata().size());
        assertEquals("v1", ingressEventItem.getMetadata().get("k1"));
    }

    @Test
    @SneakyThrows
    void ingressFlowFileV1AutoResolvesFlow() {
        mockNormalExecution();

        Mockito.when(flowAssignmentService.findFlow(any(), any())).thenReturn("flow");

        Map<String, String> headerMetadata = Map.of("k1", "v1", "k2", "v2");
        Map<String, String> flowFileAttributes = Map.of("k1", "b", "encodedString", "\uD84E\uDCE7");
        try (FlowFileInputStream flowFileInputStream = new FlowFileInputStream()) {
            flowFileInputStream.runPipeWriter(new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)),
                    flowFileAttributes, "content".length(), executorService);
            verifyNormalExecution(ingressService.ingress(null, "filename", ContentType.APPLICATION_FLOWFILE_V_1,
                    "username", OBJECT_MAPPER.writeValueAsString(headerMetadata), flowFileInputStream, TIME));
        }

        IngressEventItem ingressEventItem = ingressEventCaptor.getValue();
        assertEquals(3, ingressEventItem.getMetadata().size());
        assertEquals("v1", ingressEventItem.getMetadata().get("k1"));
    }

    @Test
    @SneakyThrows
    void ingressFlowFileV1FilenameInHeader() {
        mockNormalExecution();

        Map<String, String> headerMetadata = Map.of("k1", "v1", "filename", "filename");
        Map<String, String> flowFileAttributes = Map.of("k1", "b", "encodedString", "\uD84E\uDCE7");
        try (FlowFileInputStream flowFileInputStream = new FlowFileInputStream()) {
            flowFileInputStream.runPipeWriter(new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)),
                    flowFileAttributes, "content".length(), executorService);
            verifyNormalExecution(ingressService.ingress("flow", null, ContentType.APPLICATION_FLOWFILE_V_1,
                    "username", OBJECT_MAPPER.writeValueAsString(headerMetadata), flowFileInputStream, TIME));
        }

        IngressEventItem ingressEventItem = ingressEventCaptor.getValue();
        assertEquals(3, ingressEventItem.getMetadata().size());
        assertEquals("v1", ingressEventItem.getMetadata().get("k1"));
        assertEquals("\uD84E\uDCE7", ingressEventItem.getMetadata().get("encodedString"));
    }

    @Test
    @SneakyThrows
    void ingressFlowFileV1FilenameInAttributes() {
        mockNormalExecution();

        Map<String, String> headerMetadata = Map.of("k1", "v1", "k2", "v2");
        Map<String, String> flowFileAttributes = Map.of("k1", "b", "filename", "filename");
        try (FlowFileInputStream flowFileInputStream = new FlowFileInputStream()) {
            flowFileInputStream.runPipeWriter(new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)),
                    flowFileAttributes, "content".length(), executorService);
            verifyNormalExecution(ingressService.ingress("flow", null, ContentType.APPLICATION_FLOWFILE_V_1,
                    "username", OBJECT_MAPPER.writeValueAsString(headerMetadata), flowFileInputStream, TIME));
        }

        IngressEventItem ingressEventItem = ingressEventCaptor.getValue();
        assertEquals(3, ingressEventItem.getMetadata().size());
        assertEquals("v1", ingressEventItem.getMetadata().get("k1"));
    }

    @Test
    void ingressFlowFileV1MissingFilename() {
        mockNormalExecution();

        Map<String, String> headerMetadata = Map.of("k1", "v1", "k2", "v2");
        Map<String, String> flowFileAttributes = Map.of("k1", "b", "encodedString", "\uD84E\uDCE7");
        assertThrows(IngressMetadataException.class,
                () -> {
                    try (FlowFileInputStream flowFileInputStream = new FlowFileInputStream()) {
                        flowFileInputStream.runPipeWriter(new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)),
                                flowFileAttributes, "content".length(), executorService);
                        ingressService.ingress("flow", null, ContentType.APPLICATION_FLOWFILE_V_1,
                                "username", OBJECT_MAPPER.writeValueAsString(headerMetadata), flowFileInputStream, TIME);
                    }
                });

        Map<String, String> metricTags = Map.of(DeltaFiConstants.ACTION, "ingress", DeltaFiConstants.SOURCE,
                DeltaFiConstants.INGRESS_ACTION, DeltaFiConstants.INGRESS_FLOW, "flow");
        Mockito.verify(metricService).increment(DeltaFiConstants.FILES_DROPPED, metricTags, 1);
    }

    @Test
    @SneakyThrows
    void ingressBinaryAutoResolvesFlow() {
        mockNormalExecution();

        Mockito.when(flowAssignmentService.findFlow(any(), any())).thenReturn("flow");

        Map<String, String> headerMetadata = Map.of("k1", "v1", "k2", "v2");
        verifyNormalExecution(ingressService.ingress(null, "filename", MediaType.APPLICATION_OCTET_STREAM,
                "username", OBJECT_MAPPER.writeValueAsString(headerMetadata),
                new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)), TIME));

        IngressEventItem ingressEventItem = ingressEventCaptor.getValue();
        assertEquals(2, ingressEventItem.getMetadata().size());
    }

    @Test
    void ingressBinaryAutoResolveFlowFails() {
        mockNormalExecution();

        Mockito.when(flowAssignmentService.findFlow(any(), any())).thenReturn(null);

        Map<String, String> headerMetadata = Map.of("k1", "v1", "k2", "v2");
        assertThrows(IngressException.class,
                () -> ingressService.ingress(DeltaFiConstants.AUTO_RESOLVE_FLOW_NAME, "filename",
                        MediaType.APPLICATION_OCTET_STREAM,
                        "username", OBJECT_MAPPER.writeValueAsString(headerMetadata),
                        new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)), TIME));

        Map<String, String> metricTags = Map.of(DeltaFiConstants.ACTION, "ingress", DeltaFiConstants.SOURCE,
                DeltaFiConstants.INGRESS_ACTION, DeltaFiConstants.INGRESS_FLOW, DeltaFiConstants.AUTO_RESOLVE_FLOW_NAME);
        Mockito.verify(metricService).increment(DeltaFiConstants.FILES_DROPPED, metricTags, 1);
    }

    @Test
    void ingressBinaryFlowNotRunning() {
        mockExecution(false, false);

        Map<String, String> headerMetadata = Map.of("k1", "v1", "k2", "v2");
        IngressException ingressException = assertThrows(IngressException.class,
                () -> ingressService.ingress("flow", "filename", MediaType.APPLICATION_OCTET_STREAM,
                        "username", OBJECT_MAPPER.writeValueAsString(headerMetadata),
                        new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)), TIME));

        assertEquals("Flow flow is not running", ingressException.getMessage());

        Map<String, String> metricTags = Map.of(DeltaFiConstants.ACTION, "ingress", DeltaFiConstants.SOURCE,
                DeltaFiConstants.INGRESS_ACTION, DeltaFiConstants.INGRESS_FLOW, "flow");
        Mockito.verify(metricService).increment(DeltaFiConstants.FILES_DROPPED, metricTags, 1);
    }

    @Test
    void ingressBinaryFlowErrorsExceeded() {
        mockNormalExecution();

        Mockito.when(errorCountService.generateErrorMessage("flow")).thenReturn("errors exceeded");

        Map<String, String> headerMetadata = Map.of("k1", "v1", "k2", "v2");
        IngressException ingressException = assertThrows(IngressException.class,
                () -> ingressService.ingress("flow", "filename", MediaType.APPLICATION_OCTET_STREAM,
                        "username", OBJECT_MAPPER.writeValueAsString(headerMetadata),
                        new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)), TIME));

        assertEquals("errors exceeded", ingressException.getMessage());

        Map<String, String> metricTags = Map.of(DeltaFiConstants.ACTION, "ingress", DeltaFiConstants.SOURCE,
                DeltaFiConstants.INGRESS_ACTION, DeltaFiConstants.INGRESS_FLOW, "flow");
        Mockito.verify(metricService).increment(DeltaFiConstants.FILES_DROPPED, metricTags, 1);
    }

    @Test
    void transformFlowErrorsExceeded() {
        mockExecution(false, true);

        Mockito.when(errorCountService.generateErrorMessage("flow")).thenReturn("errors exceeded");

        Map<String, String> headerMetadata = Map.of("k1", "v1", "k2", "v2");
        IngressException ingressException = assertThrows(IngressException.class,
                () -> ingressService.ingress("flow", "filename", MediaType.APPLICATION_OCTET_STREAM,
                        "username", OBJECT_MAPPER.writeValueAsString(headerMetadata),
                        new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)), TIME));

        assertEquals("errors exceeded", ingressException.getMessage());

        Map<String, String> metricTags = Map.of(DeltaFiConstants.ACTION, "ingress", DeltaFiConstants.SOURCE,
                DeltaFiConstants.INGRESS_ACTION, DeltaFiConstants.INGRESS_FLOW, "flow");
        Mockito.verify(metricService).increment(DeltaFiConstants.FILES_DROPPED, metricTags, 1);
    }

    @Test
    void ingressBinaryServiceException() {
        mockNormalExecution();

        Mockito.when(deltaFilesService.ingress(ingressEventCaptor.capture(), any(), any())).thenThrow(new RuntimeException());

        Map<String, String> headerMetadata = Map.of("k1", "v1", "k2", "v2");
        assertThrows(IngressException.class,
                () -> ingressService.ingress("flow", "filename", MediaType.APPLICATION_OCTET_STREAM,
                        "username", OBJECT_MAPPER.writeValueAsString(headerMetadata),
                        new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)), TIME));

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
