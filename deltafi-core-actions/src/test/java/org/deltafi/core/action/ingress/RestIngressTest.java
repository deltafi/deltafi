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
package org.deltafi.core.action.ingress;

import org.assertj.core.api.Assertions;
import org.deltafi.actionkit.action.ingress.IngressResult;
import org.deltafi.actionkit.action.ingress.IngressResultItem;
import org.deltafi.actionkit.action.ingress.IngressResultType;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.common.types.IngressStatus;
import org.deltafi.core.action.HttpClientMocker;
import org.deltafi.test.asserters.ContentAssert;
import org.deltafi.test.asserters.IngressResultAssert;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static javax.ws.rs.core.HttpHeaders.CONTENT_DISPOSITION;
import static org.junit.jupiter.api.Assertions.*;

class RestIngressTest {
    private static final String URL_CONTEXT = "https://somewhere/fetch-test";
    private static final String TEST_FILE = "file.txt";

    DeltaFiTestRunner runner = DeltaFiTestRunner.setup();
    HttpClient httpClient = Mockito.mock(HttpClient.class);
    RestIngress restIngress = new RestIngress(httpClient);
    HttpClientMocker httpClientMocker = new HttpClientMocker(httpClient, TEST_FILE);

    @Test
    void ingressesFile() {
        httpClientMocker.mockSuccessResponse(Map.of(CONTENT_DISPOSITION, "attachment; filename=\"" + TEST_FILE + "\""));
        runTest(TEST_FILE, MediaType.MEDIA_TYPE_WILDCARD, null);
    }

    @Test
    void ingressesNoContent() {
        httpClientMocker.mockResponse(204);
        runTest(0, null, null, null);
    }

    @Test
    void ingressesFileWithoutContentDispositionFilename() {
        httpClientMocker.mockSuccessResponse(Map.of(
                HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN,
                HttpHeaders.CONTENT_DISPOSITION, "inline"
        ));
        runTest(RestIngress.DEFAULT_FILENAME, null);
    }

    @Test
    void ingressesFileWithoutContentDisposition() {
        httpClientMocker.mockSuccessResponse(Map.of(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN));
        runTest(RestIngress.DEFAULT_FILENAME, null);
    }

    @Test
    void ingressesFileWithHeaders() {
        httpClientMocker.mockSuccessResponse();

        runTest(TEST_FILE, Map.of(DeltaFiConstants.PERMISSIONS_HEADER, DeltaFiConstants.ADMIN_PERMISSION,
                "another-header", "another-header-value"));
        httpClientMocker.verifyRequest(this::verifyHeaders);
    }

    private void verifyHeaders(HttpRequest httpRequest) {
        Assertions.assertThat(httpRequest.headers().allValues(DeltaFiConstants.PERMISSIONS_HEADER))
                .hasSize(1).contains(DeltaFiConstants.ADMIN_PERMISSION);
        Assertions.assertThat(httpRequest.headers().allValues("another-header"))
                .hasSize(1).contains("another-header-value");
    }

    @Test
    void failsIngressWithBadUrl() {
        HttpResponse<InputStream> response = httpClientMocker.mockResponse(404);
        httpClientMocker.mockResponseBody(response, "Bad response status: 404");

        runUnhealthyTest("Bad response status: 404");
    }

    @Test
    void failsIngressWithInterruptedException() throws IOException, InterruptedException {
        Mockito.when(httpClient.send(Mockito.any(), Mockito.any())).thenThrow(new InterruptedException("Test error"));
        runUnhealthyTest("Test error");
        assertTrue(Thread.currentThread().isInterrupted());
    }

    @Test
    void failsIngressWithIOException() throws IOException, InterruptedException {
        Mockito.when(httpClient.send(Mockito.any(), Mockito.any())).thenThrow(new IOException("Test error"));
        runUnhealthyTest("Test error");
        assertFalse(Thread.currentThread().isInterrupted());
    }

    private void runTest(String expectedFilename, Map<String, String> headers) {
        runTest(expectedFilename, MediaType.TEXT_PLAIN, headers);
    }

    private void runTest(String expectedFilename, String expectedMediaType, Map<String, String> headers) {
        runTest(1, expectedFilename, expectedMediaType, headers);
    }

    private void runTest(int numExpectedResults, String expectedFilename, String expectedMediaType,
                         Map<String, String> headers) {
        IngressResult ingressResult = executeAction(headers);

        if (numExpectedResults == 0) {
            return;
        }
        IngressResultItem firstResult = ingressResult.getIngressResultItems().getFirst();
        assertEquals(expectedFilename, firstResult.getDeltaFileName());

        ContentAssert.assertThat(firstResult.getContent().getFirst())
                .hasName(expectedFilename)
                .hasMediaType(expectedMediaType)
                .loadStringIsEqualTo("This is test content.");
    }

    private void runUnhealthyTest(String expectedMessage) {
        IngressResult ingressResult = executeAction(null);
        IngressResultAssert.assertThat(ingressResult)
                .hasChildrenSize(0)
                .hasStatus(IngressStatus.UNHEALTHY)
                .hasStatusMessage("Unable to get file from REST URL: " + expectedMessage);
    }

    private IngressResult executeAction(Map<String, String> headers) {
        RestIngress.Parameters parameters = new RestIngress.Parameters();
        parameters.setUrl(URL_CONTEXT);
        parameters.setHeaders(headers);

        IngressResultType ingressResultType = restIngress.ingress(runner.actionContext(), parameters);
        return IngressResultAssert.assertThat(ingressResultType).actual();
    }
}
