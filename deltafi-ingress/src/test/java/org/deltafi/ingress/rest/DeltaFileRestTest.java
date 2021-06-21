package org.deltafi.ingress.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.deltafi.ingress.service.DeltaFileService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.inject.Inject;

// TODO: dont try to hit MinIO on startup of this test
@QuarkusTest
@Disabled
class DeltaFileRestTest {

    @InjectMock
    DeltaFileService deltaFileService;

    @Inject
    ObjectMapper objectMapper;

    @Test
    void testMinioEventListener() {
        RequestSpecification request = RestAssured.given();
        request.body(notificationString());
        request.contentType("application/json");
        Response response = request.post("/deltafile");

        Assertions.assertEquals(204, response.getStatusCode());
        Mockito.verify(deltaFileService).processNotificationRecords(Mockito.any());
    }

    private static String notificationString() {
        return "{\n" +
                "  \"Records\": [\n" +
                "    {\n" +
                "      \"eventSource\": \"web\",\n" +
                "      \"s3\": {\n" +
                "        \"bucket\": {\n" +
                "          \"name\": \"incoming\"\n" +
                "        },\n" +
                "        \"object\": {\n" +
                "          \"key\": \"filename\",\n" +
                "          \"size\": 10,\n" +
                "          \"userMetadata\": {\n" +
                "            \"X-Amz-Meta-Attributes\": \"{\\\"flow\\\": \\\"test-flow\\\", \\\"fileType\\\": \\\"stix\\\", \\\"extra\\\": \\\"info\\\", \\\"size\\\": 20}\"\n" +
                "          }\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  ]\n" +
                "}";
    }
}
