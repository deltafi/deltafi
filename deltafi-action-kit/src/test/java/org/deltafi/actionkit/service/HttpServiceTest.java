package org.deltafi.actionkit.service;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.socket.PortFactory;

import javax.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;

@QuarkusTest
class HttpServiceTest {

    @Inject
    HttpService httpService;

    private static ClientAndServer mockServer;

    @BeforeAll
    public static void startMockServer() {
        mockServer = ClientAndServer.startClientAndServer(PortFactory.findFreePort());
    }

    @AfterAll
    public static void stopMockServer() {
        mockServer.stop();
    }

    @Test
    void test_httpRequest() throws IOException {
        Integer port = mockServer.getPort();
        String url = String.format("http://localhost:%d/any",port);
        System.out.println("Using url " + url);
        mockServer.when(HttpRequest.request().withPath("/any").withSecure(false))
                .respond(HttpResponse.response().withBody("gotIt").withStatusCode(200));

        InputStream targetStream = new ByteArrayInputStream("post body".getBytes());
        java.net.http.HttpResponse<InputStream> test = httpService.post(url, Collections.emptyMap(), targetStream, "application/text");
        String response = new String(test.body().readAllBytes());
        Assertions.assertEquals("gotIt", response);
    }

    @Test
    void test_httpsRequest() throws IOException {
        Integer port = mockServer.getPort();
        String url = String.format("https://localhost:%d/any",port);
        System.out.println("Using url " + url);
        mockServer.when(HttpRequest.request().withPath("/any").withSecure(true))
                .respond(HttpResponse.response().withBody("gotIt").withStatusCode(200));

        InputStream targetStream = new ByteArrayInputStream("post body".getBytes());
        java.net.http.HttpResponse<InputStream> test = httpService.post(url, Collections.emptyMap(), targetStream, "application/text");
        String response = new String(test.body().readAllBytes());
        Assertions.assertEquals("gotIt", response);
    }

}