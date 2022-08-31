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

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import lombok.SneakyThrows;
import org.apache.nifi.util.FlowFilePackagerV1;
import org.deltafi.common.content.ContentReference;
import org.deltafi.ingress.service.DeltaFileService;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import javax.ws.rs.core.MediaType;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.*;

@QuarkusTest
class DeltaFileRestTest {

    @InjectMock
    DeltaFileService deltaFileService;

    static final String CONTENT = "STARLORD was here";
    static final String METADATA = "{\"key\": \"value\"}";
    static final String FLOWFILE_METADATA = "{\"fromHeader\": \"this is from header\", \"overwrite\": \"emacs is awesome\"}";
    static final String FILENAME = "incoming.txt";
    static final String FLOW = "flow";
    static final String MEDIA_TYPE = MediaType.APPLICATION_OCTET_STREAM;
    static final String USERNAME = "system";
    ContentReference CONTENT_REFERENCE = new ContentReference(FILENAME, "did", MEDIA_TYPE);
    DeltaFileService.IngressResult INGRESS_RESULT = new DeltaFileService.IngressResult(CONTENT_REFERENCE, FLOW);

    @Test @SneakyThrows
    void testIngress() {
        Mockito.when(deltaFileService.ingressData(any(),eq(FILENAME), eq(FLOW), eq(METADATA), eq(MEDIA_TYPE), eq(USERNAME))).thenReturn(INGRESS_RESULT);
        RequestSpecification request = RestAssured.given();
        request.body(CONTENT.getBytes(StandardCharsets.UTF_8));
        request.contentType(MediaType.APPLICATION_OCTET_STREAM);
        request.headers(Map.of("Flow", FLOW, "Filename", FILENAME, "Metadata", METADATA));

        Response response = request.post("/deltafile/ingress");

        assertThat(response.getStatusCode(), equalTo(200));

        ArgumentCaptor<InputStream> is = ArgumentCaptor.forClass(InputStream.class);
        Mockito.verify(deltaFileService).ingressData(is.capture(), Mockito.eq(FILENAME), Mockito.eq(FLOW), Mockito.eq(METADATA), Mockito.eq(MEDIA_TYPE), eq(USERNAME));
        assertThat(new String(is.getValue().readAllBytes()), equalTo(CONTENT));
    }

    @Test @SneakyThrows
    void testIngress_missingFlow() {
        Mockito.when(deltaFileService.ingressData(any(),eq(FILENAME), isNull(), eq(METADATA), eq(MediaType.APPLICATION_OCTET_STREAM), eq(USERNAME))).thenReturn(INGRESS_RESULT);
        RequestSpecification request = RestAssured.given();
        request.body(CONTENT.getBytes(StandardCharsets.UTF_8));
        request.contentType(MediaType.APPLICATION_OCTET_STREAM);
        request.headers(Map.of("Filename", FILENAME, "Metadata", METADATA));


        Response response = request.post("/deltafile/ingress");

        assertThat(response.getStatusCode(), equalTo(200));

        ArgumentCaptor<InputStream> is = ArgumentCaptor.forClass(InputStream.class);
        Mockito.verify(deltaFileService).ingressData(is.capture(), eq(FILENAME), isNull(), eq(METADATA), eq(MEDIA_TYPE), eq(USERNAME));
    }

    @Test
    void testIngress_missingFilename() {
        RequestSpecification request = RestAssured.given();
        request.body(CONTENT.getBytes(StandardCharsets.UTF_8));
        request.contentType(MediaType.APPLICATION_OCTET_STREAM);
        request.headers(Map.of("Flow", "flow"));

        Response response = request.post("/deltafile/ingress");

        assertThat(response.getStatusCode(), equalTo(400));

        Mockito.verifyNoInteractions(deltaFileService);
    }

    @Test @SneakyThrows
    void testIngress_queryParams() {
        Mockito.when(deltaFileService.ingressData(any(),eq(FILENAME), eq(FLOW), (String) isNull(), eq(MEDIA_TYPE), eq(USERNAME))).thenReturn(INGRESS_RESULT);
        RequestSpecification request = RestAssured.given();
        request.body(CONTENT.getBytes(StandardCharsets.UTF_8));
        request.contentType(MediaType.APPLICATION_OCTET_STREAM);
        request.headers(Map.of("Flow", "flowFromHeader", "Filename", "fileFromHeader"));

        Response response = request.post("/deltafile/ingress?filename=" + FILENAME + "&flow=" + FLOW);

        assertThat(response.getStatusCode(), equalTo(200));

        Mockito.verify(deltaFileService).ingressData(any(), eq(FILENAME), eq(FLOW), (String)isNull(), eq(MEDIA_TYPE), eq(USERNAME));
    }

    @Test @SneakyThrows
    void testIngress_flowfile() {
        Mockito.when(deltaFileService.ingressData(any(),eq(FILENAME), eq(FLOW), (Map<String, String>) any(), eq(MEDIA_TYPE), eq(USERNAME))).thenReturn(INGRESS_RESULT);
        RequestSpecification request = RestAssured.given();
        request.body(flowfile(CONTENT, Map.of("overwrite", "vim is awesome", "fromFlowfile", "youbetcha")));
        request.contentType(DeltaFileRest.FLOWFILE_V1_MEDIA_TYPE);
        request.headers(Map.of("metadata", "{\"fromHeader\": \"this is from header\", \"overwrite\": \"emacs is awesome\"}"));

        Response response = request.post("/deltafile/ingress?filename=" + FILENAME + "&flow=" + FLOW);

        assertThat(response.getStatusCode(), equalTo(200));

        ArgumentCaptor<InputStream> is = ArgumentCaptor.forClass(InputStream.class);

        Mockito.verify(deltaFileService).ingressData(is.capture(),
                Mockito.eq(FILENAME),
                Mockito.eq(FLOW),
                Mockito.eq(Map.of(
                        "fromHeader", "this is from header",
                        "overwrite", "vim is awesome",
                        "fromFlowfile", "youbetcha")),
                Mockito.eq(MediaType.APPLICATION_OCTET_STREAM),
                eq(USERNAME));
        assertThat(new String(is.getValue().readAllBytes()), equalTo(CONTENT));
    }

    @Test @SneakyThrows
    void testIngress_flowfile_noParamsOrHeaders() {
        Mockito.when(deltaFileService.ingressData(any(),eq(FILENAME), eq(FLOW), (Map<String, String>) any(), eq(MEDIA_TYPE), eq(USERNAME))).thenReturn(INGRESS_RESULT);
        RequestSpecification request = RestAssured.given();
        request.body(flowfile(CONTENT, Map.of(
                "flow", FLOW,
                "filename", FILENAME,
                "overwrite", "vim is awesome",
                "fromFlowfile", "youbetcha")));
        request.contentType(DeltaFileRest.FLOWFILE_V1_MEDIA_TYPE);
        request.headers(Map.of(
                "metadata", "{\"fromHeader\": \"this is from header\", \"overwrite\": \"emacs is awesome\"}"));

        Response response = request.post("/deltafile/ingress");

        assertThat(response.getStatusCode(), equalTo(200));

        ArgumentCaptor<InputStream> is = ArgumentCaptor.forClass(InputStream.class);

        Mockito.verify(deltaFileService).ingressData(is.capture(),
                eq(FILENAME),
                eq(FLOW),
                eq(Map.of(
                        "filename", FILENAME,
                        "flow", FLOW,
                        "fromHeader", "this is from header",
                        "overwrite", "vim is awesome",
                        "fromFlowfile", "youbetcha")),
                eq(MEDIA_TYPE),
                eq(USERNAME));
        assertThat(new String(is.getValue().readAllBytes()), equalTo(CONTENT));
    }

    @Test @SneakyThrows
    void testIngress_flowfile_missingFilename() {
        Mockito.when(deltaFileService.ingressData(any(),eq(FILENAME), eq(FLOW), eq(METADATA), eq(MEDIA_TYPE), eq(USERNAME))).thenReturn(INGRESS_RESULT);
        RequestSpecification request = RestAssured.given();
        request.body(flowfile(CONTENT, Map.of(
                "flow", FLOW,
                "overwrite", "vim is awesome",
                "fromFlowfile", "youbetcha")));
        request.contentType(DeltaFileRest.FLOWFILE_V1_MEDIA_TYPE);
        request.headers(Map.of(
                "metadata", "{\"fromHeader\": \"this is from header\", \"overwrite\": \"emacs is awesome\"}"));

        Response response = request.post("/deltafile/ingress");

        assertThat(response.getStatusCode(), equalTo(400));
        assertThat(response.getBody().prettyPrint(), equalTo("filename must be passed in as a query parameter, header, or flowfile attribute"));
    }

    @Test @SneakyThrows
    void testIngress_flowfile_missingFlow() {
        Mockito.when(deltaFileService.ingressData(any(),eq(FILENAME), isNull(), (Map<String, String>) any(), eq(MEDIA_TYPE), eq(USERNAME))).thenReturn(INGRESS_RESULT);
        RequestSpecification request = RestAssured.given();
        request.body(flowfile(CONTENT, Map.of("overwrite", "vim is awesome", "fromFlowfile", "youbetcha")));
        request.contentType(DeltaFileRest.FLOWFILE_V1_MEDIA_TYPE);
        request.headers(Map.of("metadata", FLOWFILE_METADATA));

        Response response = request.post("/deltafile/ingress?filename=" + FILENAME);

        assertThat(response.getStatusCode(), equalTo(200));

        ArgumentCaptor<InputStream> is = ArgumentCaptor.forClass(InputStream.class);

        Mockito.verify(deltaFileService).ingressData(is.capture(),
                eq(FILENAME),
                isNull(),
                eq(Map.of(
                        "fromHeader", "this is from header",
                        "overwrite", "vim is awesome",
                        "fromFlowfile", "youbetcha")),
                eq(MEDIA_TYPE), eq(USERNAME));
        assertThat(new String(is.getValue().readAllBytes()), equalTo(CONTENT));
    }

    @SneakyThrows
    byte[] flowfile(@NotNull String content, Map<String, String> metadata) {
        FlowFilePackagerV1 packager = new FlowFilePackagerV1();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        packager.packageFlowFile(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)),
                out, metadata, content.length());
        return out.toByteArray();
    }
}
