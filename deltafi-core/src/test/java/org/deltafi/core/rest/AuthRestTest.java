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
package org.deltafi.core.rest;

import jakarta.servlet.http.HttpServletResponse;
import org.deltafi.core.audit.CoreAuditLogger;
import org.deltafi.core.configuration.AuthProperties;
import org.deltafi.core.exceptions.InvalidRequestException;
import org.deltafi.core.services.EventService;
import org.deltafi.core.types.DeltaFiUserDetails;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.deltafi.common.constant.DeltaFiConstants.*;

class AuthRestTest {

    private static final DeltaFiUserDetails ADMIN = DeltaFiUserDetails.builder().username("admin").permissionSet(Set.of("Admin")).build();
    private static final DeltaFiUserDetails UI_ONLY = DeltaFiUserDetails.builder().username("ui").permissionSet(Set.of("UIAccess")).build();
    private static final DeltaFiUserDetails METRICS_VIEWER = DeltaFiUserDetails.builder().username("metrics").permissionSet(Set.of("UIAccess", "MetricsView")).build();
    private final AuthRest authRest = new AuthRest(new AuthProperties("cert"), "abcde", new CoreAuditLogger(Mockito.mock(EventService.class)));

    @Test
    void testAdminCanAccessAllPaths() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatCode(() -> authRest.get(ADMIN, response, "https://local.deltafi/")).doesNotThrowAnyException();
        assertThatCode(() -> authRest.get(ADMIN, response, "https://local.deltafi/orchestration/c/main")).doesNotThrowAnyException();
        assertThatCode(() -> authRest.get(ADMIN, response, "https://local.deltafi/visualization/dashboards")).doesNotThrowAnyException();
        assertThatCode(() -> authRest.get(ADMIN, response, "https://local.deltafi/shell/")).doesNotThrowAnyException();

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(response.getHeader(USER_NAME_HEADER)).isEqualTo(ADMIN.getUsername());
        assertThat(response.getHeader(USER_ID_HEADER)).isEqualTo(ADMIN.getId());
        assertThat(response.getHeader(PERMISSIONS_HEADER)).isEqualTo(ADMIN.getPermissions());
        assertThat(response.getHeader(USER_METRIC_ROLE_HEADER)).isEqualTo(ADMIN.getMetricsRole());
        assertThat(response.getHeader(AUTHORIZATION_HEADER)).isEqualTo("Bearer abcde");
    }

    @Test
    void testPathAccessDeniedWithoutPermission() {
        assertThatThrownBy(() -> authRest.verifyPathAccess("/orchestration/c/main", UI_ONLY))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessage("403 FORBIDDEN");

        assertThatThrownBy(() -> authRest.verifyPathAccess("/visualization/dashboards", UI_ONLY))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessage("403 FORBIDDEN");

        assertThatThrownBy(() -> authRest.verifyPathAccess("/shell/", UI_ONLY))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessage("403 FORBIDDEN");
    }

    @Test
    void testMetricsViewerCanAccessVisualizationButNotOrchestration() {
        assertThatCode(() -> authRest.verifyPathAccess("/visualization/dashboards", METRICS_VIEWER))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> authRest.verifyPathAccess("/orchestration/c/main", METRICS_VIEWER))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessage("403 FORBIDDEN");
    }

    @Test
    void testRegularPathsAllowedForAllUsers() {
        assertThatCode(() -> authRest.verifyPathAccess("/api/v2/deltafiles", UI_ONLY)).doesNotThrowAnyException();
        assertThatCode(() -> authRest.verifyPathAccess("/", UI_ONLY)).doesNotThrowAnyException();
    }

    @Test
    void testAuthorizationHeaderNotSetForNonOrchestrationPaths() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        authRest.get(ADMIN, response, "https://local.deltafi/api/v2/something");
        assertThat(response.getHeader(AUTHORIZATION_HEADER)).isNull();
    }

    @Test
    void testNullHeadlampToken() {
        AuthRest authRestNullToken = new AuthRest(new AuthProperties("cert"), null, new CoreAuditLogger(Mockito.mock(EventService.class)));
        MockHttpServletResponse response = new MockHttpServletResponse();
        authRestNullToken.get(ADMIN, response, "https://local.deltafi/orchestration/c/main");
        assertThat(response.getHeader(AUTHORIZATION_HEADER)).isNull();
    }

    @Test
    void testExtractPathErrorHandling() {
        assertThatThrownBy(() -> authRest.extractPath(null))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Missing required header X-ORIGINAL-URL");

        assertThatThrownBy(() -> authRest.extractPath(""))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Missing required header X-ORIGINAL-URL");
    }

    @Test
    void testExtractPathMalformedUrlReturnsNull() {
        // Malformed URLs return null path, which passes auth (backend will reject)
        assertThat(authRest.extractPath("htps:www.malformed")).isNull();
    }

    @Test
    void testExtractPath() {
        assertThat(authRest.extractPath("https://local.deltafi/orchestration/c/main")).isEqualTo("/orchestration/c/main");
        assertThat(authRest.extractPath("https://local.deltafi/visualization/dashboards")).isEqualTo("/visualization/dashboards");
    }
}
