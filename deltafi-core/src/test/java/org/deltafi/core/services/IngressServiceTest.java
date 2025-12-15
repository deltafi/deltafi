/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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
import org.deltafi.common.nifi.FlowFileVersion;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.test.content.InMemoryContentStorageService;
import org.deltafi.common.test.uuid.TestUUIDGenerator;
import org.deltafi.common.types.FlowType;
import org.deltafi.core.services.analytics.AnalyticEventService;
import org.deltafi.core.types.*;
import org.deltafi.common.types.IngressEventItem;
import org.deltafi.core.configuration.DeltaFiProperties;
import org.deltafi.core.exceptions.IngressException;
import org.deltafi.core.exceptions.IngressMetadataException;
import org.deltafi.core.exceptions.IngressRateLimitException;
import org.deltafi.core.exceptions.IngressStorageException;
import org.deltafi.core.exceptions.IngressUnavailableException;
import org.deltafi.core.exceptions.MissingFlowException;
import org.deltafi.core.metrics.MetricService;
import org.deltafi.core.util.FlowBuilders;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.deltafi.core.generated.types.RateLimit;
import org.deltafi.core.generated.types.RateLimitUnit;

import javax.ws.rs.core.MediaType;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class IngressServiceTest {
    private static final TestUUIDGenerator UUID_GENERATOR = new TestUUIDGenerator();

    private static final ContentStorageService CONTENT_STORAGE_SERVICE =
            new InMemoryContentStorageService();

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final MetricService metricService;
    private final SystemService systemService;
    private final DeltaFilesService deltaFilesService;
    private final DeltaFiPropertiesService deltaFiPropertiesService;
    private  final RestDataSourceService restDataSourceService;
    private final ErrorCountService errorCountService;
    // TODO: add to tests
    private final AnalyticEventService analyticEventService;
    private final RateLimitService rateLimitService;

    private final IngressService ingressService;

    private final ExecutorService executorService = Executors.newCachedThreadPool();

    private static final OffsetDateTime TIME = OffsetDateTime.MAX;
    private static final RestDataSource REST_DATA_SOURCE = FlowBuilders.buildDataSource("rest-data-source");

    @Captor
    ArgumentCaptor<IngressEventItem> ingressEventCaptor;

    IngressServiceTest(@Mock MetricService metricService,
                       @Mock SystemService systemService, @Mock DeltaFilesService deltaFilesService,
                       @Mock DeltaFiPropertiesService deltaFiPropertiesService,
                       @Mock RestDataSourceService restDataSourceService,
                       @Mock ErrorCountService errorCountService,
                       @Mock AnalyticEventService analyticEventService,
                       @Mock RateLimitService rateLimitService) {
        this.metricService = metricService;
        this.systemService = systemService;
        this.deltaFilesService = deltaFilesService;
        this.deltaFiPropertiesService = deltaFiPropertiesService;
        this.restDataSourceService = restDataSourceService;
        this.errorCountService = errorCountService;
        this.analyticEventService = analyticEventService;
        this.rateLimitService = rateLimitService;

        ingressService = new IngressService(metricService, systemService, CONTENT_STORAGE_SERVICE,
                deltaFilesService, deltaFiPropertiesService, restDataSourceService, errorCountService, UUID_GENERATOR,
                analyticEventService, rateLimitService);
    }

    @Test
    @SneakyThrows
    void ingressBinaryFile() {
        mockExecution(true);

        Map<String, String> headerMetadata = Map.of("k1", "v1", "k2", "v2");
        verifyNormalExecution(ingressService.ingress("dataSource", "filename", MediaType.APPLICATION_OCTET_STREAM,
                "username", OBJECT_MAPPER.writeValueAsString(headerMetadata),
                new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)), TIME), "content");

        IngressEventItem ingressEventItem = ingressEventCaptor.getValue();
        assertEquals(2, ingressEventItem.getMetadata().size());
    }

    private void mockNormalExecution() {
        mockExecution(true);
    }

    @SneakyThrows
    private void mockExecution(boolean flowRunning) {
        DeltaFiProperties deltaFiProperties = new DeltaFiProperties();
        Mockito.when(deltaFiPropertiesService.getDeltaFiProperties()).thenReturn(deltaFiProperties);
        Mockito.when(systemService.isContentStorageDepleted()).thenReturn(false);
        if (flowRunning) {
            Mockito.when(restDataSourceService.getActiveFlowByName(any())).thenReturn(REST_DATA_SOURCE);
        } else {
            Mockito.when(restDataSourceService.getActiveFlowByName(any())).thenThrow(MissingFlowException.stopped("dataSource", FlowType.REST_DATA_SOURCE));
        }
        DeltaFileFlow flow = DeltaFileFlow.builder().flowDefinition(FlowDefinition.builder().name("dataSource").type(FlowType.REST_DATA_SOURCE).build()).build();
        DeltaFile deltaFile = DeltaFile.builder().flows(Set.of(flow)).build();
        Mockito.when(deltaFilesService.ingressRest(any(), ingressEventCaptor.capture(), any(), any())).thenReturn(deltaFile);
    }

    private void verifyNormalExecution(List<IngressResult> ingressResults, String expectedContent) throws IOException, ObjectStorageException {
        Mockito.verifyNoMoreInteractions(metricService);
        String content;
        try (InputStream contentInputStream = CONTENT_STORAGE_SERVICE.load(ingressResults.getFirst().content())) {
            content = new String(contentInputStream.readAllBytes());
        }
        assertEquals(expectedContent, content);
        assertEquals("dataSource", ingressResults.getFirst().dataSource());
        assertEquals("filename", ingressResults.getFirst().content().getName());
        assertEquals(UUID_GENERATOR.generate(), ingressResults.getFirst().did());
    }

    @Test
    @SneakyThrows
    void ingressFlowFileV1() {
        mockNormalExecution();

        Map<String, String> headerMetadata = Map.of("k1", "v1", "k2", "v2", "encodedString", "\uD84E\uDCE7");
        List<IngressResult> ingressResults = ingressService.ingress("dataSource", "filename",
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
        List<IngressResult> ingressResults = ingressService.ingress("dataSource", "filename",
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
        List<IngressResult> ingressResults = ingressService.ingress("dataSource", "filename",
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
                () -> ingressService.ingress("dataSource", "filename", MediaType.APPLICATION_OCTET_STREAM,
                        "username", OBJECT_MAPPER.writeValueAsString(Map.of()),
                        new ByteArrayInputStream("binary content".getBytes(StandardCharsets.UTF_8)), TIME));
    }

    @Test
    void ingressStorageDepleted() {
        DeltaFiProperties deltaFiProperties = new DeltaFiProperties();
        Mockito.when(deltaFiPropertiesService.getDeltaFiProperties()).thenReturn(deltaFiProperties);
        Mockito.when(systemService.isContentStorageDepleted()).thenReturn(true);

        assertThrows(IngressStorageException.class,
                () -> ingressService.ingress("dataSource", "filename", MediaType.APPLICATION_OCTET_STREAM,
                        "username", OBJECT_MAPPER.writeValueAsString(Map.of()),
                        new ByteArrayInputStream("binary content".getBytes(StandardCharsets.UTF_8)), TIME));
    }

    @Test
    void ingressBadHeaderMetadata() {
        DeltaFiProperties deltaFiProperties = new DeltaFiProperties();
        Mockito.when(deltaFiPropertiesService.getDeltaFiProperties()).thenReturn(deltaFiProperties);
        Mockito.when(systemService.isContentStorageDepleted()).thenReturn(false);

        assertThrows(IngressMetadataException.class,
                () -> ingressService.ingress("dataSource", "filename", MediaType.APPLICATION_OCTET_STREAM,
                        "username", "bad header metadata",
                        new ByteArrayInputStream("binary content".getBytes(StandardCharsets.UTF_8)), TIME));

        Map<String, String> metricTags = Map.of(DeltaFiConstants.ACTION, "ingress", DeltaFiConstants.SOURCE,
                DeltaFiConstants.INGRESS_ACTION, DeltaFiConstants.DATA_SOURCE, "dataSource");
        Mockito.verify(metricService).increment(DeltaFiConstants.FILES_DROPPED, metricTags, 1);
    }

    @Test
    void ingressBinaryFileMissingFilename() {
        mockNormalExecution();

        Map<String, String> headerMetadata = Map.of("k1", "v1", "k2", "v2");
        assertThrows(IngressMetadataException.class,
                () -> ingressService.ingress("dataSource", null, MediaType.APPLICATION_OCTET_STREAM,
                        "username", OBJECT_MAPPER.writeValueAsString(headerMetadata),
                        new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)), TIME));

        Map<String, String> metricTags = Map.of(DeltaFiConstants.ACTION, "ingress", DeltaFiConstants.SOURCE,
                DeltaFiConstants.INGRESS_ACTION, DeltaFiConstants.DATA_SOURCE, "dataSource");
        Mockito.verify(metricService).increment(DeltaFiConstants.FILES_DROPPED, metricTags, 1);
    }

    @Test
    @SneakyThrows
    void ingressFlowFileV1FlowInHeader() {
        mockNormalExecution();

        Map<String, String> headerMetadata = Map.of("k1", "v1", "dataSource", "dataSource");
        Map<String, String> flowFileAttributes = Map.of("k1", "b", "encodedString", "\uD84E\uDCE7");
        try (FlowFileInputStream flowFileInputStream = FlowFileInputStream.create(FlowFileVersion.V1,
                new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)), flowFileAttributes,
                "content".length(), executorService)) {
            verifyNormalExecution(ingressService.ingress(null, "filename", ContentType.APPLICATION_FLOWFILE_V_1,
                    "username", OBJECT_MAPPER.writeValueAsString(headerMetadata), flowFileInputStream, TIME), "content");
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
        Map<String, String> flowFileAttributes = Map.of("k1", "b", "dataSource", "dataSource");
        try (FlowFileInputStream flowFileInputStream = FlowFileInputStream.create(FlowFileVersion.V1,
                new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)), flowFileAttributes,
                "content".length(), executorService)) {
            verifyNormalExecution(ingressService.ingress(null, "filename", ContentType.APPLICATION_FLOWFILE_V_1,
                    "username", OBJECT_MAPPER.writeValueAsString(headerMetadata), flowFileInputStream, TIME), "content");
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
        try (FlowFileInputStream flowFileInputStream = FlowFileInputStream.create(FlowFileVersion.V1,
                new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)), flowFileAttributes,
                "content".length(), executorService)) {
            verifyNormalExecution(ingressService.ingress("dataSource", null, ContentType.APPLICATION_FLOWFILE_V_1,
                    "username", OBJECT_MAPPER.writeValueAsString(headerMetadata), flowFileInputStream, TIME), "content");
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
        try (FlowFileInputStream flowFileInputStream = FlowFileInputStream.create(FlowFileVersion.V1,
                new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)), flowFileAttributes,
                "content".length(), executorService)) {
            verifyNormalExecution(ingressService.ingress("dataSource", null, ContentType.APPLICATION_FLOWFILE_V_1,
                    "username", OBJECT_MAPPER.writeValueAsString(headerMetadata), flowFileInputStream, TIME), "content");
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
                    try (FlowFileInputStream flowFileInputStream = FlowFileInputStream.create(FlowFileVersion.V1,
                            new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)), flowFileAttributes,
                            "content".length(), executorService)) {
                        ingressService.ingress("dataSource", null, ContentType.APPLICATION_FLOWFILE_V_1,
                                "username", OBJECT_MAPPER.writeValueAsString(headerMetadata), flowFileInputStream, TIME);
                    }
                });

        Map<String, String> metricTags = Map.of(DeltaFiConstants.ACTION, "ingress", DeltaFiConstants.SOURCE,
                DeltaFiConstants.INGRESS_ACTION, DeltaFiConstants.DATA_SOURCE, "dataSource");
        Mockito.verify(metricService).increment(DeltaFiConstants.FILES_DROPPED, metricTags, 1);
    }

    @Test
    void ingressBinaryFlowNotRunning() {
        mockExecution(false);

        Map<String, String> headerMetadata = Map.of("k1", "v1", "k2", "v2");
        IngressMetadataException ingressMetadataException = assertThrows(IngressMetadataException.class,
                () -> ingressService.ingress("dataSource", "filename", MediaType.APPLICATION_OCTET_STREAM,
                        "username", OBJECT_MAPPER.writeValueAsString(headerMetadata),
                        new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)), TIME));

        assertEquals("The rest data source named dataSource is not running", ingressMetadataException.getMessage());

        Map<String, String> metricTags = Map.of(DeltaFiConstants.ACTION, "ingress", DeltaFiConstants.SOURCE,
                DeltaFiConstants.INGRESS_ACTION, DeltaFiConstants.DATA_SOURCE, "dataSource");
        Mockito.verify(metricService).increment(DeltaFiConstants.FILES_DROPPED, metricTags, 1);
    }

    @Test
    @SneakyThrows
    void ingressBinaryFlowErrorsExceeded() {
        mockNormalExecution();

        Mockito.doThrow(new IngressRateLimitException("errors exceeded")).when(errorCountService).checkErrorsExceeded(FlowType.REST_DATA_SOURCE,"dataSource");

        Map<String, String> headerMetadata = Map.of("k1", "v1", "k2", "v2");
        IngressRateLimitException ingressRateLimitException = assertThrows(IngressRateLimitException.class,
                () -> ingressService.ingress("dataSource", "filename", MediaType.APPLICATION_OCTET_STREAM,
                        "username", OBJECT_MAPPER.writeValueAsString(headerMetadata),
                        new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)), TIME));

        assertEquals("errors exceeded", ingressRateLimitException.getMessage());

        Map<String, String> metricTags = Map.of(DeltaFiConstants.ACTION, "ingress", DeltaFiConstants.SOURCE,
                DeltaFiConstants.INGRESS_ACTION, DeltaFiConstants.DATA_SOURCE, "dataSource");
        Mockito.verify(metricService).increment(DeltaFiConstants.FILES_DROPPED, metricTags, 1);
    }

    @Test
    @SneakyThrows
    void transformFlowErrorsExceeded() {
        mockNormalExecution();

        Mockito.doThrow(new IngressRateLimitException("errors exceeded")).when(errorCountService).checkErrorsExceeded(FlowType.REST_DATA_SOURCE,"dataSource");

        Map<String, String> headerMetadata = Map.of("k1", "v1", "k2", "v2");
        IngressRateLimitException ingressRateLimitException = assertThrows(IngressRateLimitException.class,
                () -> ingressService.ingress("dataSource", "filename", MediaType.APPLICATION_OCTET_STREAM,
                        "username", OBJECT_MAPPER.writeValueAsString(headerMetadata),
                        new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)), TIME));

        assertEquals("errors exceeded", ingressRateLimitException.getMessage());

        Map<String, String> metricTags = Map.of(DeltaFiConstants.ACTION, "ingress", DeltaFiConstants.SOURCE,
                DeltaFiConstants.INGRESS_ACTION, DeltaFiConstants.DATA_SOURCE, "dataSource");
        Mockito.verify(metricService).increment(DeltaFiConstants.FILES_DROPPED, metricTags, 1);
    }

    @Test
    void ingressBinaryServiceException() {
        mockNormalExecution();

        Mockito.when(deltaFilesService.ingressRest(any(), ingressEventCaptor.capture(), any(), any())).thenThrow(new RuntimeException());

        Map<String, String> headerMetadata = Map.of("k1", "v1", "k2", "v2");
        assertThrows(IngressException.class,
                () -> ingressService.ingress("dataSource", "filename", MediaType.APPLICATION_OCTET_STREAM,
                        "username", OBJECT_MAPPER.writeValueAsString(headerMetadata),
                        new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)), TIME));

        Mockito.verify(deltaFilesService).deleteContentAndMetadata(any(), any());

        Map<String, String> metricTags = Map.of(DeltaFiConstants.ACTION, "ingress", DeltaFiConstants.SOURCE,
                DeltaFiConstants.INGRESS_ACTION, DeltaFiConstants.DATA_SOURCE, "dataSource");
        Mockito.verify(metricService).increment(DeltaFiConstants.FILES_DROPPED, metricTags, 1);
    }

    @Test
    void parseMetadataStringWithSubObject() throws IngressMetadataException {
        String metadata = "{\"complex\": {\"key\": {\"list\": [1, 2, 3]}}}";

        Map<String, String> map = ingressService.parseMetadata(metadata);
        Assertions.assertEquals(1, map.size());
        Assertions.assertEquals("{\"key\":{\"list\":[1,2,3]}}", map.get("complex"));
    }

    @Test
    @SneakyThrows
    void ingressBinaryFileWithFilesRateLimit() {
        mockNormalExecution();

        RestDataSource restDataSourceWithRateLimit = FlowBuilders.buildDataSource("rest-data-source");
        RateLimit rateLimit = RateLimit.newBuilder()
                .unit(RateLimitUnit.FILES)
                .maxAmount(10L)
                .durationSeconds(60)
                .build();
        restDataSourceWithRateLimit.setRateLimit(rateLimit);

        Mockito.when(restDataSourceService.getActiveFlowByName(any())).thenReturn(restDataSourceWithRateLimit);
        Mockito.when(rateLimitService.tryConsume(eq("dataSource"), eq(1L), eq(10L), any())).thenReturn(true);

        Map<String, String> headerMetadata = Map.of("k1", "v1", "k2", "v2");
        verifyNormalExecution(ingressService.ingress("dataSource", "filename", MediaType.APPLICATION_OCTET_STREAM,
                "username", OBJECT_MAPPER.writeValueAsString(headerMetadata),
                new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)), TIME), "content");

        Mockito.verify(rateLimitService).tryConsume(eq("dataSource"), eq(1L), eq(10L), any());
    }

    @Test
    @SneakyThrows
    void ingressBinaryFileWithBytesRateLimit() {
        mockNormalExecution();

        RestDataSource restDataSourceWithRateLimit = FlowBuilders.buildDataSource("rest-data-source");
        RateLimit rateLimit = RateLimit.newBuilder()
                .unit(RateLimitUnit.BYTES)
                .maxAmount(1000L)
                .durationSeconds(60)
                .build();
        restDataSourceWithRateLimit.setRateLimit(rateLimit);

        Mockito.when(restDataSourceService.getActiveFlowByName(any())).thenReturn(restDataSourceWithRateLimit);
        Mockito.when(rateLimitService.tryConsume(eq("dataSource"), eq(1L), eq(1000L), any())).thenReturn(true);

        Map<String, String> headerMetadata = Map.of("k1", "v1", "k2", "v2");
        verifyNormalExecution(ingressService.ingress("dataSource", "filename", MediaType.APPLICATION_OCTET_STREAM,
                "username", OBJECT_MAPPER.writeValueAsString(headerMetadata),
                new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)), TIME), "content");

        Mockito.verify(rateLimitService).tryConsume(eq("dataSource"), eq(1L), eq(1000L), any());
        Mockito.verify(rateLimitService).consume(eq("dataSource"), eq(6L), eq(1000L), any()); // "content" is 7 bytes, minus 1 already consumed
    }

    @Test
    @SneakyThrows
    void ingressBinaryFileFilesRateLimitExceeded() {
        mockNormalExecution();

        RestDataSource restDataSourceWithRateLimit = FlowBuilders.buildDataSource("rest-data-source");
        RateLimit rateLimit = RateLimit.newBuilder()
                .unit(RateLimitUnit.FILES)
                .maxAmount(5L)
                .durationSeconds(60)
                .build();
        restDataSourceWithRateLimit.setRateLimit(rateLimit);

        Mockito.when(restDataSourceService.getActiveFlowByName(any())).thenReturn(restDataSourceWithRateLimit);
        Mockito.when(rateLimitService.tryConsume(eq("dataSource"), eq(1L), eq(5L), any())).thenReturn(false);

        Map<String, String> headerMetadata = Map.of("k1", "v1", "k2", "v2");
        IngressRateLimitException exception = assertThrows(IngressRateLimitException.class,
                () -> ingressService.ingress("dataSource", "filename", MediaType.APPLICATION_OCTET_STREAM,
                        "username", OBJECT_MAPPER.writeValueAsString(headerMetadata),
                        new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)), TIME));

        assertEquals("Rate limit exceeded - dataSource allows 5 files per 60 seconds", exception.getMessage());

        Mockito.verify(rateLimitService).tryConsume(eq("dataSource"), eq(1L), eq(5L), any());
        Mockito.verifyNoMoreInteractions(rateLimitService);

        Map<String, String> metricTags = Map.of(DeltaFiConstants.ACTION, "ingress", DeltaFiConstants.SOURCE,
                DeltaFiConstants.INGRESS_ACTION, DeltaFiConstants.DATA_SOURCE, "dataSource");
        Mockito.verify(metricService).increment(DeltaFiConstants.FILES_DROPPED, metricTags, 1);
    }

    @Test
    @SneakyThrows
    void ingressBinaryFileBytesRateLimitExceeded() {
        mockNormalExecution();

        RestDataSource restDataSourceWithRateLimit = FlowBuilders.buildDataSource("rest-data-source");
        RateLimit rateLimit = RateLimit.newBuilder()
                .unit(RateLimitUnit.BYTES)
                .maxAmount(100L)
                .durationSeconds(60)
                .build();
        restDataSourceWithRateLimit.setRateLimit(rateLimit);

        Mockito.when(restDataSourceService.getActiveFlowByName(any())).thenReturn(restDataSourceWithRateLimit);
        Mockito.when(rateLimitService.tryConsume(eq("dataSource"), eq(1L), eq(100L), any())).thenReturn(false);

        Map<String, String> headerMetadata = Map.of("k1", "v1", "k2", "v2");
        IngressRateLimitException exception = assertThrows(IngressRateLimitException.class,
                () -> ingressService.ingress("dataSource", "filename", MediaType.APPLICATION_OCTET_STREAM,
                        "username", OBJECT_MAPPER.writeValueAsString(headerMetadata),
                        new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)), TIME));

        assertEquals("Rate limit exceeded - dataSource allows 100 bytes per 60 seconds", exception.getMessage());

        Mockito.verify(rateLimitService).tryConsume(eq("dataSource"), eq(1L), eq(100L), any());
        Mockito.verifyNoMoreInteractions(rateLimitService);

        Map<String, String> metricTags = Map.of(DeltaFiConstants.ACTION, "ingress", DeltaFiConstants.SOURCE,
                DeltaFiConstants.INGRESS_ACTION, DeltaFiConstants.DATA_SOURCE, "dataSource");
        Mockito.verify(metricService).increment(DeltaFiConstants.FILES_DROPPED, metricTags, 1);
    }

    @Test
    @SneakyThrows
    void ingressBinaryFileWithBytesRateLimitOneByteFile() {
        mockNormalExecution();

        RestDataSource restDataSourceWithRateLimit = FlowBuilders.buildDataSource("rest-data-source");
        RateLimit rateLimit = RateLimit.newBuilder()
                .unit(RateLimitUnit.BYTES)
                .maxAmount(1000L)
                .durationSeconds(60)
                .build();
        restDataSourceWithRateLimit.setRateLimit(rateLimit);

        Mockito.when(restDataSourceService.getActiveFlowByName(any())).thenReturn(restDataSourceWithRateLimit);
        Mockito.when(rateLimitService.tryConsume(eq("dataSource"), eq(1L), eq(1000L), any())).thenReturn(true);

        Map<String, String> headerMetadata = Map.of("k1", "v1", "k2", "v2");
        verifyNormalExecution(ingressService.ingress("dataSource", "filename", MediaType.APPLICATION_OCTET_STREAM,
                "username", OBJECT_MAPPER.writeValueAsString(headerMetadata),
                new ByteArrayInputStream("X".getBytes(StandardCharsets.UTF_8)), TIME), "X");

        Mockito.verify(rateLimitService).tryConsume(eq("dataSource"), eq(1L), eq(1000L), any());
        // For 1-byte file, no additional consume() call should be made since remainingBytes = 1 - 1 = 0
        Mockito.verify(rateLimitService, Mockito.never()).consume(anyString(), anyLong(), anyLong(), any());
    }

    @Test
    @SneakyThrows
    void ingressBinaryFileWithoutRateLimit() {
        mockNormalExecution();

        Map<String, String> headerMetadata = Map.of("k1", "v1", "k2", "v2");
        verifyNormalExecution(ingressService.ingress("dataSource", "filename", MediaType.APPLICATION_OCTET_STREAM,
                "username", OBJECT_MAPPER.writeValueAsString(headerMetadata),
                new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)), TIME), "content");

        Mockito.verifyNoInteractions(rateLimitService);
    }
}
