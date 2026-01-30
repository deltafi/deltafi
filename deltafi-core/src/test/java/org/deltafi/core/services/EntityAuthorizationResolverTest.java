/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
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

import org.deltafi.core.services.EntityAuthorizationResolver.AuthResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntityAuthorizationResolverTest {

    @Mock
    private HttpClient httpClient;

    @Test
    void isAuthorized_whenDisabled_returnsGranted() {
        EntityAuthorizationResolver resolver = new EntityAuthorizationResolver(httpClient, "http://localhost:8080/api/v1/authorize", false);

        AuthResponse response = resolver.isAuthorized(Map.of("key", "value"));

        assertThat(response.authorized()).isTrue();
        assertThat(response.reason()).isNull();
    }

    @Test
    void isAuthorized_whenEnabledAndAuthorized_returnsGranted() throws Exception {
        EntityAuthorizationResolver resolver = new EntityAuthorizationResolver(httpClient, "http://localhost:8080/api/v1/authorize", true);

        @SuppressWarnings("unchecked")
        HttpResponse<String> httpResponse = mock(HttpResponse.class);
        when(httpResponse.body()).thenReturn("{\"authorized\":true,\"reason\":null}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);

        try (MockedStatic<DeltaFiUserService> mockedStatic = Mockito.mockStatic(DeltaFiUserService.class)) {
            mockedStatic.when(DeltaFiUserService::currentUsername).thenReturn("testUser");

            AuthResponse response = resolver.isAuthorized(Map.of("classification", "UNCLASS"));

            assertThat(response.authorized()).isTrue();
        }
    }

    @Test
    void isAuthorized_whenEnabledAndDenied_returnsDeniedWithReason() throws Exception {
        EntityAuthorizationResolver resolver = new EntityAuthorizationResolver(httpClient, "http://localhost:8080/api/v1/authorize", true);

        @SuppressWarnings("unchecked")
        HttpResponse<String> httpResponse = mock(HttpResponse.class);
        when(httpResponse.body()).thenReturn("{\"authorized\":false,\"reason\":\"insufficient clearance\"}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);

        try (MockedStatic<DeltaFiUserService> mockedStatic = Mockito.mockStatic(DeltaFiUserService.class)) {
            mockedStatic.when(DeltaFiUserService::currentUsername).thenReturn("testUser");

            AuthResponse response = resolver.isAuthorized(Map.of("classification", "SECRET"));

            assertThat(response.authorized()).isFalse();
            assertThat(response.reason()).isEqualTo("insufficient clearance");
        }
    }

    @Test
    void isAuthorized_whenHttpCallFails_returnsDenied() throws Exception {
        EntityAuthorizationResolver resolver = new EntityAuthorizationResolver(httpClient, "http://localhost:8080/api/v1/authorize", true);

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(new IOException("Connection refused"));

        try (MockedStatic<DeltaFiUserService> mockedStatic = Mockito.mockStatic(DeltaFiUserService.class)) {
            mockedStatic.when(DeltaFiUserService::currentUsername).thenReturn("testUser");

            AuthResponse response = resolver.isAuthorized(Map.of("key", "value"));

            assertThat(response.authorized()).isFalse();
        }
    }

    @Test
    void isAuthorized_whenInterrupted_returnsDeniedAndReinterrupts() throws Exception {
        EntityAuthorizationResolver resolver = new EntityAuthorizationResolver(httpClient, "http://localhost:8080/api/v1/authorize", true);

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(new InterruptedException("interrupted"));

        try (MockedStatic<DeltaFiUserService> mockedStatic = Mockito.mockStatic(DeltaFiUserService.class)) {
            mockedStatic.when(DeltaFiUserService::currentUsername).thenReturn("testUser");

            AuthResponse response = resolver.isAuthorized(Map.of("key", "value"));

            assertThat(response.authorized()).isFalse();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();

            // Clear the interrupt flag for test cleanup
            Thread.interrupted();
        }
    }

    @Test
    void isAuthorized_whenInvalidResponse_returnsDenied() throws Exception {
        EntityAuthorizationResolver resolver = new EntityAuthorizationResolver(httpClient, "http://localhost:8080/api/v1/authorize", true);

        @SuppressWarnings("unchecked")
        HttpResponse<String> httpResponse = mock(HttpResponse.class);
        when(httpResponse.body()).thenReturn("not valid json");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);

        try (MockedStatic<DeltaFiUserService> mockedStatic = Mockito.mockStatic(DeltaFiUserService.class)) {
            mockedStatic.when(DeltaFiUserService::currentUsername).thenReturn("testUser");

            AuthResponse response = resolver.isAuthorized(Map.of("key", "value"));

            assertThat(response.authorized()).isFalse();
        }
    }
}
