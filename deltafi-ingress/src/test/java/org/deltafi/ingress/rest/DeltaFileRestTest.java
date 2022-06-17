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

@QuarkusTest
class DeltaFileRestTest {

    @InjectMock
    DeltaFileService deltaFileService;

    static final String CONTENT="STARLORD was here";

    @Test @SneakyThrows
    void testIngress() {
        RequestSpecification request = RestAssured.given();
        request.body(CONTENT.getBytes(StandardCharsets.UTF_8));
        request.contentType(MediaType.APPLICATION_OCTET_STREAM);
        request.headers(Map.of("Flow", "flow", "Filename", "incoming.txt", "Metadata", "{\"key\": \"value\"}"));

        Response response = request.post("/deltafile/ingress");

        assertThat(response.getStatusCode(), equalTo(200));

        ArgumentCaptor<InputStream> is = ArgumentCaptor.forClass(InputStream.class);
        Mockito.verify(deltaFileService).ingressData(is.capture(), Mockito.eq("incoming.txt"), Mockito.eq("flow"), Mockito.eq("{\"key\": \"value\"}"), Mockito.eq(MediaType.APPLICATION_OCTET_STREAM));
        assertThat(new String(is.getValue().readAllBytes()), equalTo(CONTENT));
    }

    @Test @SneakyThrows
    void testIngress_missingFlow() {
        RequestSpecification request = RestAssured.given();
        request.body(CONTENT.getBytes(StandardCharsets.UTF_8));
        request.contentType(MediaType.APPLICATION_OCTET_STREAM);
        request.headers(Map.of("Filename", "incoming.txt", "Metadata", "{\"key\": \"value\"}"));

        Response response = request.post("/deltafile/ingress");

        assertThat(response.getStatusCode(), equalTo(200));

        ArgumentCaptor<InputStream> is = ArgumentCaptor.forClass(InputStream.class);
        Mockito.verify(deltaFileService).ingressData(is.capture(), Mockito.eq("incoming.txt"), Mockito.isNull(), Mockito.eq("{\"key\": \"value\"}"), Mockito.eq(MediaType.APPLICATION_OCTET_STREAM));
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
        RequestSpecification request = RestAssured.given();
        request.body(CONTENT.getBytes(StandardCharsets.UTF_8));
        request.contentType(MediaType.APPLICATION_OCTET_STREAM);
        request.headers(Map.of("Flow", "flowFromHeader", "Filename", "fileFromHeader"));

        Response response = request.post("/deltafile/ingress?filename=fileFromParam&flow=flowFromParam");

        assertThat(response.getStatusCode(), equalTo(200));

        Mockito.verify(deltaFileService).ingressData(Mockito.any(), Mockito.eq("fileFromParam"), Mockito.eq("flowFromParam"), (String)Mockito.isNull(), Mockito.eq(MediaType.APPLICATION_OCTET_STREAM));
    }

    @Test @SneakyThrows
    void testIngress_flowfile() {
        RequestSpecification request = RestAssured.given();
        request.body(flowfile(CONTENT, Map.of("overwrite", "vim is awesome", "fromFlowfile", "youbetcha")));
        request.contentType(DeltaFileRest.FLOWFILE_V1_MEDIA_TYPE);
        request.headers(Map.of("metadata", "{\"fromHeader\": \"this is from header\", \"overwrite\": \"emacs is awesome\"}"));

        Response response = request.post("/deltafile/ingress?filename=myFilename&flow=myFlow");

        assertThat(response.getStatusCode(), equalTo(200));

        ArgumentCaptor<InputStream> is = ArgumentCaptor.forClass(InputStream.class);

        Mockito.verify(deltaFileService).ingressData(is.capture(),
                Mockito.eq("myFilename"),
                Mockito.eq("myFlow"),
                Mockito.eq(Map.of(
                        "fromHeader", "this is from header",
                        "overwrite", "vim is awesome",
                        "fromFlowfile", "youbetcha")),
                Mockito.eq(MediaType.APPLICATION_OCTET_STREAM));
        assertThat(new String(is.getValue().readAllBytes()), equalTo(CONTENT));
    }

    @Test @SneakyThrows
    void testIngress_flowfile_noParamsOrHeaders() {
        RequestSpecification request = RestAssured.given();
        request.body(flowfile(CONTENT, Map.of(
                "flow", "myFlow",
                "filename", "myFilename",
                "overwrite", "vim is awesome",
                "fromFlowfile", "youbetcha")));
        request.contentType(DeltaFileRest.FLOWFILE_V1_MEDIA_TYPE);
        request.headers(Map.of(
                "metadata", "{\"fromHeader\": \"this is from header\", \"overwrite\": \"emacs is awesome\"}"));

        Response response = request.post("/deltafile/ingress");

        assertThat(response.getStatusCode(), equalTo(200));

        ArgumentCaptor<InputStream> is = ArgumentCaptor.forClass(InputStream.class);

        Mockito.verify(deltaFileService).ingressData(is.capture(),
                Mockito.eq("myFilename"),
                Mockito.eq("myFlow"),
                Mockito.eq(Map.of(
                        "filename", "myFilename",
                        "flow", "myFlow",
                        "fromHeader", "this is from header",
                        "overwrite", "vim is awesome",
                        "fromFlowfile", "youbetcha")),
                Mockito.eq(MediaType.APPLICATION_OCTET_STREAM));
        assertThat(new String(is.getValue().readAllBytes()), equalTo(CONTENT));
    }

    @Test @SneakyThrows
    void testIngress_flowfile_missingFilename() {
        RequestSpecification request = RestAssured.given();
        request.body(flowfile(CONTENT, Map.of(
                "flow", "myFlow",
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
        RequestSpecification request = RestAssured.given();
        request.body(flowfile(CONTENT, Map.of("overwrite", "vim is awesome", "fromFlowfile", "youbetcha")));
        request.contentType(DeltaFileRest.FLOWFILE_V1_MEDIA_TYPE);
        request.headers(Map.of("metadata", "{\"fromHeader\": \"this is from header\", \"overwrite\": \"emacs is awesome\"}"));

        Response response = request.post("/deltafile/ingress?filename=myFilename");

        assertThat(response.getStatusCode(), equalTo(200));

        ArgumentCaptor<InputStream> is = ArgumentCaptor.forClass(InputStream.class);

        Mockito.verify(deltaFileService).ingressData(is.capture(),
                Mockito.eq("myFilename"),
                Mockito.isNull(),
                Mockito.eq(Map.of(
                        "fromHeader", "this is from header",
                        "overwrite", "vim is awesome",
                        "fromFlowfile", "youbetcha")),
                Mockito.eq(MediaType.APPLICATION_OCTET_STREAM));
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
