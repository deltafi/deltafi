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
package org.deltafi.core.action.ingress;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import org.deltafi.actionkit.action.ingress.IngressResult;
import org.deltafi.actionkit.action.ingress.IngressResultType;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.common.content.ActionContentStorageService;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.test.storage.s3.InMemoryObjectStorageService;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.IngressStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mockito;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static org.junit.jupiter.api.Assertions.*;

public class RestIngressTest {
    private static final String URL_CONTEXT = "/endpoint?param1=x&param2=y";
    private static final String TEST_FILE = "file.txt";

    private static final ActionContentStorageService CONTENT_STORAGE_SERVICE =
            new ActionContentStorageService(new InMemoryObjectStorageService());

    @RegisterExtension
    static WireMockExtension wireMockHttp = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().dynamicPort())
            .build();

    @BeforeEach
    public void beforeEach() {
        wireMockHttp.resetMappings();
    }

    @Test
    public void ingressesFile() {
        wireMockHttp.stubFor(WireMock.get(URL_CONTEXT)
                .willReturn(WireMock.ok()
                        .withHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + TEST_FILE + "\"")
                        .withBodyFile(TEST_FILE)));

        runTest(TEST_FILE, MediaType.MEDIA_TYPE_WILDCARD, null);
    }

    @Test
    public void ingressesNoContent() {
        wireMockHttp.stubFor(WireMock.get(URL_CONTEXT).willReturn(WireMock.noContent()));

        runTest(0, null, null, null);
    }

    @Test
    public void ingressesFileWithoutContentDispositionFilename() {
        wireMockHttp.stubFor(WireMock.get(URL_CONTEXT)
                .willReturn(WireMock.ok()
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN)
                        .withHeader(HttpHeaders.CONTENT_DISPOSITION, "inline")
                        .withBodyFile(TEST_FILE)));

        runTest(RestIngress.DEFAULT_FILENAME, null);
    }

    @Test
    public void ingressesFileWithoutContentDisposition() {
        wireMockHttp.stubFor(WireMock.get(URL_CONTEXT)
                .willReturn(WireMock.ok()
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN)
                        .withBodyFile(TEST_FILE)));

        runTest(RestIngress.DEFAULT_FILENAME, null);
    }

    @Test
    public void ingressesFileWithHeaders() {
        wireMockHttp.stubFor(WireMock.get(URL_CONTEXT)
                .withHeader(DeltaFiConstants.PERMISSIONS_HEADER, equalTo(DeltaFiConstants.ADMIN_PERMISSION))
                .withHeader("another-header", equalTo("another-header-value"))
                .willReturn(WireMock.ok()
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN)
                        .withHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + TEST_FILE + "\"")
                        .withBodyFile(TEST_FILE)));

        runTest(TEST_FILE, Map.of(DeltaFiConstants.PERMISSIONS_HEADER, DeltaFiConstants.ADMIN_PERMISSION,
                "another-header", "another-header-value"));
    }

    private void runTest(String expectedFilename, Map<String, String> headers) {
        runTest(expectedFilename, MediaType.TEXT_PLAIN, headers);
    }

    private void runTest(String expectedFilename, String expectedMediaType, Map<String, String> headers) {
        runTest(1, expectedFilename, expectedMediaType, headers);
    }

    private void runTest(int numExpectedResults, String expectedFilename, String expectedMediaType,
            Map<String, String> headers) {
        WireMockRuntimeInfo wmRuntimeInfo = wireMockHttp.getRuntimeInfo();

        RestIngress restIngress = new RestIngress(HttpClient.newHttpClient());
        RestIngress.Parameters parameters = new RestIngress.Parameters();
        parameters.setUrl(wmRuntimeInfo.getHttpBaseUrl() + URL_CONTEXT);
        if (headers != null) {
            parameters.setHeaders(headers);
        }
        IngressResultType ingressResultType = restIngress.ingress(
                ActionContext.builder().contentStorageService(CONTENT_STORAGE_SERVICE).build(), parameters);

        assertInstanceOf(IngressResult.class, ingressResultType);
        IngressResult ingressResult = (IngressResult) ingressResultType;
        assertEquals(numExpectedResults, ingressResult.getIngressResultItems().size());
        if (numExpectedResults == 0) {
            return;
        }
        assertEquals(expectedFilename, ingressResult.getIngressResultItems().getFirst().getDeltaFileName());
        try (InputStream inputStream = CONTENT_STORAGE_SERVICE.load(
                ingressResult.getIngressResultItems().getFirst().getContent().getFirst().getContent())) {
            assertEquals(new String(inputStream.readAllBytes()), "This is the content.");
        } catch (ObjectStorageException | IOException e) {
            fail("Unable to load content", e);
        }
        assertEquals(expectedMediaType,
                ingressResult.getIngressResultItems().getFirst().getContent().getFirst().getMediaType());
        CONTENT_STORAGE_SERVICE.delete(
                ingressResult.getIngressResultItems().getFirst().getContent().getFirst().getContent());
    }

    @Test
    public void failsIngressWithBadUrl() {
        runUnhealthyTest(HttpClient.newHttpClient(), "Bad response status: 404");
    }

    @Test
    public void failsIngressWithInterruptedException() throws IOException, InterruptedException {
        HttpClient httpClient = Mockito.mock(HttpClient.class);
        Mockito.when(httpClient.send(Mockito.any(), Mockito.any())).thenThrow(new InterruptedException("Test error"));
        runUnhealthyTest(httpClient, "Test error");
        assertTrue(Thread.currentThread().isInterrupted());
    }

    @Test
    public void failsIngressWithIOException() throws IOException, InterruptedException {
        HttpClient httpClient = Mockito.mock(HttpClient.class);
        Mockito.when(httpClient.send(Mockito.any(), Mockito.any())).thenThrow(new IOException("Test error"));
        runUnhealthyTest(httpClient, "Test error");
    }

    private void runUnhealthyTest(HttpClient httpClient, String expectedMessage) {
        WireMockRuntimeInfo wmRuntimeInfo = wireMockHttp.getRuntimeInfo();

        RestIngress restIngress = new RestIngress(httpClient);
        RestIngress.Parameters parameters = new RestIngress.Parameters();
        parameters.setUrl(wmRuntimeInfo.getHttpBaseUrl() + URL_CONTEXT);
        IngressResultType ingressResultType = restIngress.ingress(
                ActionContext.builder().contentStorageService(CONTENT_STORAGE_SERVICE).build(), parameters);

        assertInstanceOf(IngressResult.class, ingressResultType);
        IngressResult ingressResult = (IngressResult) ingressResultType;
        assertEquals(IngressStatus.UNHEALTHY, ingressResult.getStatus());
        assertEquals("Unable to get file from REST URL: " + expectedMessage, ingressResult.getStatusMessage());
        assertEquals(0, ingressResult.getIngressResultItems().size());
    }
}
