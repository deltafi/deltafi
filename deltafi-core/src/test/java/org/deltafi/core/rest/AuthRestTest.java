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

    private static final DeltaFiUserDetails ADMIN  = DeltaFiUserDetails.builder().username("admin").permissionSet(Set.of("Admin")).build();
    private static final DeltaFiUserDetails UIOnly = DeltaFiUserDetails.builder().username("ui").permissionSet(Set.of("UIAccess")).build();
    private static final DeltaFiUserDetails METRICS_VIEWER = DeltaFiUserDetails.builder().username("metrics").permissionSet(Set.of("UIAccess", "MetricsView")).build();
    private final AuthRest authRest = new AuthRest("local.deltafi", new AuthProperties("cert"), "abcde", new CoreAuditLogger(Mockito.mock(EventService.class)));

    @Test
    void testDomainCheckHasAccessAdminAccess() {
        HttpServletResponse response = new MockHttpServletResponse();
        // admin can go anywhere
        assertThatCode(() -> authRest.get(ADMIN, response, "https://local.deltafi")).doesNotThrowAnyException();
        assertThatCode(() -> authRest.get(ADMIN, response, "https://metrics.local.deltafi")).doesNotThrowAnyException();
        assertThatCode(() -> authRest.get(ADMIN, response, "https://does.not.matter")).doesNotThrowAnyException();

        assertThatCode(() -> authRest.get(ADMIN, response, "https://local.deltafi/orchestration/c/main")).doesNotThrowAnyException();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(response.getHeader(USER_NAME_HEADER)).isEqualTo(ADMIN.getUsername());
        assertThat(response.getHeader(USER_ID_HEADER)).isEqualTo(ADMIN.getId());
        assertThat(response.getHeader(PERMISSIONS_HEADER)).isEqualTo(ADMIN.getPermissions());
        assertThat(response.getHeader(USER_METRIC_ROLE_HEADER)).isEqualTo(ADMIN.getMetricsRole());
        assertThat(response.getHeader(AUTHORIZATION_HEADER)).isEqualTo("Bearer abcde");
    }

    @Test
    void testDomainCheckUiOnlyAccess() {
        // UI only user can get to domain only, not a subdomain
        assertThatCode(() -> authRest.get(UIOnly, new MockHttpServletResponse(), "https://local.deltafi")).doesNotThrowAnyException();
        assertThatThrownBy(() -> authRest.get(UIOnly, new MockHttpServletResponse(), "https://metrics.local.deltafi"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessage("403 FORBIDDEN");
    }

    @Test
    void testPathAccessDeniedWithoutPermission() {
        // UIOnly user (no Admin permission) should be denied access to orchestration paths
        assertThatThrownBy(() -> authRest.get(UIOnly, new MockHttpServletResponse(), "https://local.deltafi/orchestration/c/main"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessage("403 FORBIDDEN");

        // UIOnly user (no MetricsView permission) should be denied access to visualization paths
        assertThatThrownBy(() -> authRest.get(UIOnly, new MockHttpServletResponse(), "https://local.deltafi/visualization/dashboards"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessage("403 FORBIDDEN");
    }

    @Test
    void testMetricsViewerCanAccessVisualizationButNotOrchestration() {
        // MetricsView user can access visualization paths
        assertThatCode(() -> authRest.get(METRICS_VIEWER, new MockHttpServletResponse(), "https://local.deltafi/visualization/dashboards"))
                .doesNotThrowAnyException();

        // MetricsView user (no Admin permission) should be denied access to orchestration paths
        assertThatThrownBy(() -> authRest.get(METRICS_VIEWER, new MockHttpServletResponse(), "https://local.deltafi/orchestration/c/main"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessage("403 FORBIDDEN");
    }

    @Test
    void testAuthorizationHeaderNotSetForNonOrchestrationPaths() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        authRest.get(ADMIN, response, "https://local.deltafi/api/v2/something");
        assertThat(response.getHeader(AUTHORIZATION_HEADER)).isNull();
    }

    @Test
    void testNullHeadlampToken() {
        AuthRest authRestNullToken = new AuthRest("local.deltafi", new AuthProperties("cert"), null, new CoreAuditLogger(Mockito.mock(EventService.class)));
        MockHttpServletResponse response = new MockHttpServletResponse();
        // Should not set a "Bearer null" header when token is null
        authRestNullToken.get(ADMIN, response, "https://local.deltafi/orchestration/c/main");
        assertThat(response.getHeader(AUTHORIZATION_HEADER)).isNull();
    }

    @Test
    void testHostExtractionErrorHandling() {
        assertThatThrownBy(() -> authRest.get(ADMIN, new MockHttpServletResponse(), null))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Missing required header X-ORIGINAL-URL");

        assertThatThrownBy(() -> authRest.get(ADMIN, new MockHttpServletResponse(), ""))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Missing required header X-ORIGINAL-URL");

        assertThatThrownBy(() -> authRest.get(ADMIN, new MockHttpServletResponse(), "htps:www.malformed"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Invalid X-ORIGINAL-URL header provided");
    }
}