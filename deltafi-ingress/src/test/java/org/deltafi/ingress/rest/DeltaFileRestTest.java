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
package org.deltafi.ingress.rest;

import io.minio.MinioClient;
import lombok.SneakyThrows;
import org.apache.nifi.util.FlowFilePackagerV1;
import org.deltafi.common.content.ContentReference;
import org.deltafi.common.metrics.MetricRepository;
import org.deltafi.ingress.service.DeltaFileService;
import org.deltafi.ingress.service.GraphQLClientService;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;

import javax.ws.rs.core.MediaType;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Map;

import static org.deltafi.common.constant.DeltaFiConstants.USER_HEADER;
import static org.deltafi.ingress.rest.DeltaFileRest.FLOWFILE_V1_MEDIA_TYPE;
import static org.deltafi.ingress.util.Metrics.tagsFor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DeltaFileRestTest {

    @Autowired
    TestRestTemplate restTemplate;

    @MockBean
    DeltaFileService deltaFileService;

    @MockBean
    GraphQLClientService graphQLClientService;

    @MockBean
    MetricRepository metricRepository;

    @MockBean
    MinioClient minioClient;

    static final String CONTENT = "STARLORD was here";
    static final String METADATA = "{\"key\": \"value\"}";
    static final String FILENAME = "incoming.txt";
    static final String FLOW = "theFlow";
    static final String INCOMING_FLOWFILE_METADATA = "{\"fromHeader\": \"this is from header\", \"overwrite\": \"emacs is awesome\"}";
    static final Map<String, String> RESOLVED_FLOWFILE_METADATA = Map.of(
            "fromHeader", "this is from header",
            "overwrite", "vim is awesome",
            "fromFlowfile", "youbetcha");
    static final Map<String, String> FLOWFILE_METADATA_NO_HEADERS = Map.of(
            "filename", FILENAME,
            "flow", FLOW,
            "overwrite", "vim is awesome",
            "fromFlowfile", "youbetcha");
    static final Map<String, String> FLOWFILE_METADATA_NO_HEADERS_NO_FLOW = Map.of(
            "filename", FILENAME,
            "overwrite", "vim is awesome",
            "fromFlowfile", "youbetcha");
    static final String MEDIA_TYPE = MediaType.APPLICATION_OCTET_STREAM;
    static final String USERNAME = "myname";
    ContentReference CONTENT_REFERENCE = new ContentReference(FILENAME, 0, CONTENT.length(), "did", MEDIA_TYPE);
    DeltaFileService.IngressResult INGRESS_RESULT = new DeltaFileService.IngressResult(CONTENT_REFERENCE, FLOW);

    private ResponseEntity<String> ingress(String filename, String flow, String metadata, byte[] body, String contentType) {
        HttpHeaders headers = new HttpHeaders();
        if (filename != null) {
            headers.add("Filename", filename);
        }
        if (flow != null) {
            headers.add("Flow", flow);
        }
        if (metadata != null) {
            headers.add("Metadata", metadata);
        }
        headers.add(HttpHeaders.CONTENT_TYPE, contentType);
        headers.add(USER_HEADER, USERNAME);
        HttpEntity<byte[]> request = new HttpEntity<>(body, headers);

        return restTemplate.postForEntity("/deltafile/ingress", request, String.class);
    }

    @Test
    @SneakyThrows
    void testIngress() {
        Mockito.when(deltaFileService.ingressData(any(), eq(FILENAME), eq(FLOW), eq(METADATA), eq(MEDIA_TYPE), eq(USERNAME))).thenReturn(INGRESS_RESULT);

        ResponseEntity<String> response = ingress(FILENAME, FLOW, METADATA, CONTENT.getBytes(), MediaType.APPLICATION_OCTET_STREAM);
        assertEquals(200, response.getStatusCodeValue());
        assertEquals(INGRESS_RESULT.getContentReference().getDid(), response.getBody());

        ArgumentCaptor<InputStream> is = ArgumentCaptor.forClass(InputStream.class);
        Mockito.verify(deltaFileService).ingressData(is.capture(), eq(FILENAME), eq(FLOW), eq(METADATA), eq(MEDIA_TYPE), eq(USERNAME));
        // TODO: EOF inputStream?
        // assertThat(new String(is.getValue().readAllBytes()), equalTo(CONTENT));

        Map<String, String> tags = tagsFor(FLOW);
        Mockito.verify(metricRepository).increment("files_in", tags, 1);
        Mockito.verify(metricRepository).increment("bytes_in", tags, CONTENT.length());
    }

    @Test
    @SneakyThrows
    void testIngress_missingFlow() {
        Mockito.when(deltaFileService.ingressData(any(), eq(FILENAME), isNull(), eq(METADATA), eq(MEDIA_TYPE), eq(USERNAME))).thenReturn(INGRESS_RESULT);

        ResponseEntity<String> response = ingress(FILENAME, null, METADATA, CONTENT.getBytes(), MediaType.APPLICATION_OCTET_STREAM);

        assertEquals(200, response.getStatusCodeValue());
        assertEquals(INGRESS_RESULT.getContentReference().getDid(), response.getBody());

        ArgumentCaptor<InputStream> is = ArgumentCaptor.forClass(InputStream.class);
        Mockito.verify(deltaFileService).ingressData(is.capture(), eq(FILENAME), isNull(), eq(METADATA), eq(MEDIA_TYPE), eq(USERNAME));
        // TODO: EOF inputStream?
        // assertThat(new String(is.getValue().readAllBytes()), equalTo(CONTENT));

        Map<String, String> tags = tagsFor(FLOW);
        Mockito.verify(metricRepository).increment("files_in", tags, 1);
        Mockito.verify(metricRepository).increment("bytes_in", tags, CONTENT.length());
    }

    @Test
    void testIngress_missingFilename() {
        ResponseEntity<String> response = ingress(null, FLOW, METADATA, CONTENT.getBytes(), MediaType.APPLICATION_OCTET_STREAM);
        assertEquals(400, response.getStatusCodeValue());

        Mockito.verifyNoInteractions(deltaFileService);
    }

    @Test
    @SneakyThrows
    void testIngress_flowfile() {
        Mockito.when(deltaFileService.ingressData(any(), eq(FILENAME), eq(FLOW), eq(RESOLVED_FLOWFILE_METADATA), eq(MEDIA_TYPE), eq(USERNAME))).thenReturn(INGRESS_RESULT);
        ResponseEntity<String> response = ingress(FILENAME, FLOW, INCOMING_FLOWFILE_METADATA, flowfile(Map.of("overwrite", "vim is awesome", "fromFlowfile", "youbetcha")), FLOWFILE_V1_MEDIA_TYPE);

        assertEquals(200, response.getStatusCodeValue());

        ArgumentCaptor<InputStream> is = ArgumentCaptor.forClass(InputStream.class);

        Mockito.verify(deltaFileService).ingressData(is.capture(),
                eq(FILENAME),
                eq(FLOW),
                eq(RESOLVED_FLOWFILE_METADATA),
                eq(MEDIA_TYPE),
                eq(USERNAME));

        // TODO: EOF inputStream?
        // assertThat(new String(is.getValue().readAllBytes()), equalTo(CONTENT));

        Map<String, String> tags = tagsFor(FLOW);
        Mockito.verify(metricRepository).increment("files_in", tags, 1);
        Mockito.verify(metricRepository).increment("bytes_in", tags, CONTENT.length());
    }

    @Test
    @SneakyThrows
    void testIngress_flowfile_noParamsOrHeaders() {
        Mockito.when(deltaFileService.ingressData(any(), eq(FILENAME), eq(FLOW), eq(FLOWFILE_METADATA_NO_HEADERS), eq(MEDIA_TYPE), eq(USERNAME))).thenReturn(INGRESS_RESULT);
        ResponseEntity<String> response = ingress(null, null, null,
                flowfile(Map.of(
                        "flow", FLOW,
                        "filename", FILENAME,
                        "overwrite", "vim is awesome",
                        "fromFlowfile", "youbetcha")),
                FLOWFILE_V1_MEDIA_TYPE);

        assertEquals(200, response.getStatusCodeValue());

        ArgumentCaptor<InputStream> is = ArgumentCaptor.forClass(InputStream.class);

        Mockito.verify(deltaFileService).ingressData(is.capture(),
                eq(FILENAME),
                eq(FLOW),
                eq(FLOWFILE_METADATA_NO_HEADERS),
                eq(MEDIA_TYPE),
                eq(USERNAME));

        // TODO: EOF inputStream?
        // assertThat(new String(is.getValue().readAllBytes()), equalTo(CONTENT));
    }

    @Test @SneakyThrows
    void testIngress_flowfile_missingFilename() {
        ResponseEntity<String> response = ingress(null, null, null,
                flowfile(Map.of(
                        "flow", FLOW,
                        "overwrite", "vim is awesome",
                        "fromFlowfile", "youbetcha")),
                FLOWFILE_V1_MEDIA_TYPE);

        assertEquals(400, response.getStatusCodeValue());
        assertEquals("Filename must be passed in as a header or flowfile attribute", response.getBody());
    }

    @Test
    @SneakyThrows
    void testIngress_flowfile_missingFlow() {
        Mockito.when(deltaFileService.ingressData(any(), eq(FILENAME), isNull(), eq(FLOWFILE_METADATA_NO_HEADERS_NO_FLOW), eq(MEDIA_TYPE), eq(USERNAME))).thenReturn(INGRESS_RESULT);
        ResponseEntity<String> response = ingress(null, null, null,
                flowfile(Map.of(
                        "filename", FILENAME,
                        "overwrite", "vim is awesome",
                        "fromFlowfile", "youbetcha")),
                FLOWFILE_V1_MEDIA_TYPE);

        assertEquals(200, response.getStatusCodeValue());

        ArgumentCaptor<InputStream> is = ArgumentCaptor.forClass(InputStream.class);

        Mockito.verify(deltaFileService).ingressData(is.capture(),
                eq(FILENAME),
                isNull(),
                eq(FLOWFILE_METADATA_NO_HEADERS_NO_FLOW),
                eq(MEDIA_TYPE),
                eq(USERNAME));

        // TODO: EOF inputStream?
        // assertThat(new String(is.getValue().readAllBytes()), equalTo(CONTENT));
    }

    @SneakyThrows
    byte[] flowfile(Map<String, String> metadata) {
        FlowFilePackagerV1 packager = new FlowFilePackagerV1();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        packager.packageFlowFile(new ByteArrayInputStream(CONTENT.getBytes()),
                out, metadata, CONTENT.length());
        return out.toByteArray();
    }
}
