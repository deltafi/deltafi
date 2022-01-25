package org.deltafi.ingress.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.ingress.exceptions.DeltafiException;
import org.deltafi.ingress.exceptions.DeltafiMetadataException;
import org.deltafi.ingress.service.DeltaFileService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.ws.rs.core.MediaType;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@QuarkusTest
class DeltaFileRestTest {

    @InjectMock
    DeltaFileService deltaFileService;

    @Test
    void testIngress() throws ObjectStorageException, DeltafiException, DeltafiMetadataException {
        RequestSpecification request = RestAssured.given();
        request.body("incoming data".getBytes(StandardCharsets.UTF_8));
        request.contentType(MediaType.APPLICATION_OCTET_STREAM);
        request.headers(Map.of("Flow", "flow", "Filename", "incoming.txt", "Metadata", "{\"key\": \"value\"}"));

        Response response = request.post("/deltafile/ingress");

        Assertions.assertEquals(200, response.getStatusCode());

        Mockito.verify(deltaFileService).ingressData(Mockito.any(), Mockito.eq("incoming.txt"), Mockito.eq("flow"), Mockito.eq("{\"key\": \"value\"}"));
    }

    @Test
    void testIngress_missingFlow() {
        RequestSpecification request = RestAssured.given();
        request.body("incoming data".getBytes(StandardCharsets.UTF_8));
        request.contentType(MediaType.APPLICATION_OCTET_STREAM);
        request.headers(Map.of("Filename", "incoming.txt"));

        Response response = request.post("/deltafile/ingress");

        Assertions.assertEquals(400, response.getStatusCode());

        Mockito.verifyNoInteractions(deltaFileService);
    }

    @Test
    void testIngress_missingFilename() {
        RequestSpecification request = RestAssured.given();
        request.body("incoming data".getBytes(StandardCharsets.UTF_8));
        request.contentType(MediaType.APPLICATION_OCTET_STREAM);
        request.headers(Map.of("Flow", "flow"));

        Response response = request.post("/deltafile/ingress");

        Assertions.assertEquals(400, response.getStatusCode());

        Mockito.verifyNoInteractions(deltaFileService);
    }

    @Test
    void testIngress_queryParams() throws ObjectStorageException, DeltafiException, DeltafiMetadataException {
        RequestSpecification request = RestAssured.given();
        request.body("incoming data".getBytes(StandardCharsets.UTF_8));
        request.contentType(MediaType.APPLICATION_OCTET_STREAM);
        request.headers(Map.of("Flow", "flowFromHeader", "Filename", "fileFromHeader"));

        Response response = request.post("/deltafile/ingress?filename=fileFromParam&flow=flowFromParam");

        Assertions.assertEquals(200, response.getStatusCode());

        Mockito.verify(deltaFileService).ingressData(Mockito.any(), Mockito.eq("fileFromParam"), Mockito.eq("flowFromParam"), Mockito.isNull());
    }

}
