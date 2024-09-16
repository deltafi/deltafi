/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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
import org.deltafi.core.types.DeltaFile;
import org.deltafi.core.types.DeltaFileFlow;
import org.deltafi.common.types.IngressEventItem;
import org.deltafi.core.audit.CoreAuditLogger;
import org.deltafi.core.configuration.DeltaFiProperties;
import org.deltafi.core.exceptions.IngressException;
import org.deltafi.core.exceptions.IngressMetadataException;
import org.deltafi.core.exceptions.IngressStorageException;
import org.deltafi.core.exceptions.IngressUnavailableException;
import org.deltafi.core.exceptions.MissingFlowException;
import org.deltafi.core.metrics.MetricService;
import org.deltafi.core.types.IngressResult;
import org.deltafi.core.types.RestDataSource;
import org.deltafi.core.util.FlowBuilders;
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
    private  final RestDataSourceService restDataSourceService;
    private final ErrorCountService errorCountService;

    private final IngressService ingressService;

    private final ExecutorService executorService = Executors.newCachedThreadPool();

    private static final OffsetDateTime TIME = OffsetDateTime.MAX;
    private static final RestDataSource REST_DATA_SOURCE = FlowBuilders.buildDataSource("rest-data-source");

    @Captor
    ArgumentCaptor<IngressEventItem> ingressEventCaptor;

    IngressServiceTest(@Mock MetricService metricService, @Mock CoreAuditLogger coreAuditLogger,
                       @Mock DiskSpaceService diskSpaceService, @Mock DeltaFilesService deltaFilesService,
                       @Mock DeltaFiPropertiesService deltaFiPropertiesService,
                       @Mock RestDataSourceService restDataSourceService,
                       @Mock ErrorCountService errorCountService) {
        this.metricService = metricService;
        this.coreAuditLogger = coreAuditLogger;
        this.diskSpaceService = diskSpaceService;
        this.deltaFilesService = deltaFilesService;
        this.deltaFiPropertiesService = deltaFiPropertiesService;
        this.restDataSourceService = restDataSourceService;
        this.errorCountService = errorCountService;

        ingressService = new IngressService(metricService, coreAuditLogger, diskSpaceService, CONTENT_STORAGE_SERVICE,
                deltaFilesService, deltaFiPropertiesService, restDataSourceService, errorCountService, UUID_GENERATOR);
    }

    @Test
    @SneakyThrows
    void ingressBinaryFile() {
        mockExecution(true);

        Map<String, String> headerMetadata = Map.of("k1", "v1", "k2", "v2");
        verifyNormalExecution(ingressService.ingress("flow", "filename", MediaType.APPLICATION_OCTET_STREAM,
                "username", OBJECT_MAPPER.writeValueAsString(headerMetadata),
                new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)), TIME));

        IngressEventItem ingressEventItem = ingressEventCaptor.getValue();
        assertEquals(2, ingressEventItem.getMetadata().size());
    }

    private void mockNormalExecution() {
        mockExecution(true);
    }

    private void mockExecution(boolean flowRunning) {
        DeltaFiProperties deltaFiProperties = new DeltaFiProperties();
        Mockito.when(deltaFiPropertiesService.getDeltaFiProperties()).thenReturn(deltaFiProperties);
        Mockito.when(diskSpaceService.isContentStorageDepleted()).thenReturn(false);
        if (flowRunning) {
            Mockito.when(restDataSourceService.getRunningFlowByName(any())).thenReturn(REST_DATA_SOURCE);
        } else {
            Mockito.when(restDataSourceService.getRunningFlowByName(any())).thenThrow(new MissingFlowException("Flow flow is not running"));
        }
        Mockito.when(errorCountService.generateErrorMessage("flow")).thenReturn(null);
        DeltaFileFlow flow = DeltaFileFlow.builder().name("flow").build();
        DeltaFile deltaFile = DeltaFile.builder().flows(List.of(flow)).build();
        Mockito.when(deltaFilesService.ingress(any(), ingressEventCaptor.capture(), any(), any())).thenReturn(deltaFile);
    }

    private void verifyNormalExecution(List<IngressResult> ingressResults) throws IOException, ObjectStorageException {
        Mockito.verify(coreAuditLogger).logIngress("username", "filename");

        Map<String, String> metricTags = Map.of(DeltaFiConstants.ACTION, "ingress",
                DeltaFiConstants.SOURCE, DeltaFiConstants.INGRESS_ACTION, DeltaFiConstants.DATA_SOURCE, "flow");
        Mockito.verify(metricService).increment(DeltaFiConstants.FILES_IN, metricTags, 1);
        Mockito.verify(metricService).increment(DeltaFiConstants.BYTES_IN, metricTags, "content".length());

        String content;
        try (InputStream contentInputStream = CONTENT_STORAGE_SERVICE.load(ingressResults.getFirst().content())) {
            content = new String(contentInputStream.readAllBytes());
        }
        assertEquals("content", content);
        assertEquals("flow", ingressResults.getFirst().flow());
        assertEquals("filename", ingressResults.getFirst().content().getName());
        assertEquals(UUID_GENERATOR.generate(), ingressResults.getFirst().did());
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
        deltaFiProperties.setIngressEnabled(false);
        Mockito.when(deltaFiPropertiesService.getDeltaFiProperties()).thenReturn(deltaFiProperties);

        assertThrows(IngressUnavailableException.class,
                () -> ingressService.ingress("flow", "filename", MediaType.APPLICATION_OCTET_STREAM,
                        "username", OBJECT_MAPPER.writeValueAsString(Map.of()),
                        new ByteArrayInputStream("binary content".getBytes(StandardCharsets.UTF_8)), TIME));
    }

    @Test
    void ingressStorageDepleted() {
        DeltaFiProperties deltaFiProperties = new DeltaFiProperties();
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
        Mockito.when(deltaFiPropertiesService.getDeltaFiProperties()).thenReturn(deltaFiProperties);
        Mockito.when(diskSpaceService.isContentStorageDepleted()).thenReturn(false);

        assertThrows(IngressMetadataException.class,
                () -> ingressService.ingress("flow", "filename", MediaType.APPLICATION_OCTET_STREAM,
                        "username", "bad header metadata",
                        new ByteArrayInputStream("binary content".getBytes(StandardCharsets.UTF_8)), TIME));

        Map<String, String> metricTags = Map.of(DeltaFiConstants.ACTION, "ingress", DeltaFiConstants.SOURCE,
                DeltaFiConstants.INGRESS_ACTION, DeltaFiConstants.DATA_SOURCE, "flow");
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
                DeltaFiConstants.INGRESS_ACTION, DeltaFiConstants.DATA_SOURCE, "flow");
        Mockito.verify(metricService).increment(DeltaFiConstants.FILES_DROPPED, metricTags, 1);
    }

    @Test
    @SneakyThrows
    void ingressFlowFileV1FlowInHeader() {
        mockNormalExecution();

        Map<String, String> headerMetadata = Map.of("k1", "v1", "flow", "flow");
        Map<String, String> flowFileAttributes = Map.of("k1", "b", "encodedString", "\uD84E\uDCE7");
        try (FlowFileInputStream flowFileInputStream = FlowFileInputStream.create(
                new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)), flowFileAttributes,
                "content".length(), executorService)) {
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
        try (FlowFileInputStream flowFileInputStream = FlowFileInputStream.create(
                new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)), flowFileAttributes,
                "content".length(), executorService)) {
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
        try (FlowFileInputStream flowFileInputStream = FlowFileInputStream.create(
                new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)), flowFileAttributes,
                "content".length(), executorService)) {
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
        try (FlowFileInputStream flowFileInputStream = FlowFileInputStream.create(
                new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)), flowFileAttributes,
                "content".length(), executorService)) {
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
                    try (FlowFileInputStream flowFileInputStream = FlowFileInputStream.create(
                            new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)), flowFileAttributes,
                            "content".length(), executorService)) {
                        ingressService.ingress("flow", null, ContentType.APPLICATION_FLOWFILE_V_1,
                                "username", OBJECT_MAPPER.writeValueAsString(headerMetadata), flowFileInputStream, TIME);
                    }
                });

        Map<String, String> metricTags = Map.of(DeltaFiConstants.ACTION, "ingress", DeltaFiConstants.SOURCE,
                DeltaFiConstants.INGRESS_ACTION, DeltaFiConstants.DATA_SOURCE, "flow");
        Mockito.verify(metricService).increment(DeltaFiConstants.FILES_DROPPED, metricTags, 1);
    }

    @Test
    void ingressBinaryFlowNotRunning() {
        mockExecution(false);

        Map<String, String> headerMetadata = Map.of("k1", "v1", "k2", "v2");
        IngressException ingressException = assertThrows(IngressException.class,
                () -> ingressService.ingress("flow", "filename", MediaType.APPLICATION_OCTET_STREAM,
                        "username", OBJECT_MAPPER.writeValueAsString(headerMetadata),
                        new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)), TIME));

        assertEquals("Flow flow is not running", ingressException.getMessage());

        Map<String, String> metricTags = Map.of(DeltaFiConstants.ACTION, "ingress", DeltaFiConstants.SOURCE,
                DeltaFiConstants.INGRESS_ACTION, DeltaFiConstants.DATA_SOURCE, "flow");
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
                DeltaFiConstants.INGRESS_ACTION, DeltaFiConstants.DATA_SOURCE, "flow");
        Mockito.verify(metricService).increment(DeltaFiConstants.FILES_DROPPED, metricTags, 1);
    }

    @Test
    void transformFlowErrorsExceeded() {
        mockNormalExecution();

        Mockito.when(errorCountService.generateErrorMessage("flow")).thenReturn("errors exceeded");

        Map<String, String> headerMetadata = Map.of("k1", "v1", "k2", "v2");
        IngressException ingressException = assertThrows(IngressException.class,
                () -> ingressService.ingress("flow", "filename", MediaType.APPLICATION_OCTET_STREAM,
                        "username", OBJECT_MAPPER.writeValueAsString(headerMetadata),
                        new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)), TIME));

        assertEquals("errors exceeded", ingressException.getMessage());

        Map<String, String> metricTags = Map.of(DeltaFiConstants.ACTION, "ingress", DeltaFiConstants.SOURCE,
                DeltaFiConstants.INGRESS_ACTION, DeltaFiConstants.DATA_SOURCE, "flow");
        Mockito.verify(metricService).increment(DeltaFiConstants.FILES_DROPPED, metricTags, 1);
    }

    @Test
    void ingressBinaryServiceException() {
        mockNormalExecution();

        Mockito.when(deltaFilesService.ingress(any(), ingressEventCaptor.capture(), any(), any())).thenThrow(new RuntimeException());

        Map<String, String> headerMetadata = Map.of("k1", "v1", "k2", "v2");
        assertThrows(IngressException.class,
                () -> ingressService.ingress("flow", "filename", MediaType.APPLICATION_OCTET_STREAM,
                        "username", OBJECT_MAPPER.writeValueAsString(headerMetadata),
                        new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)), TIME));

        Mockito.verify(deltaFilesService).deleteContentAndMetadata(any(), any());

        Map<String, String> metricTags = Map.of(DeltaFiConstants.ACTION, "ingress", DeltaFiConstants.SOURCE,
                DeltaFiConstants.INGRESS_ACTION, DeltaFiConstants.DATA_SOURCE, "flow");
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
