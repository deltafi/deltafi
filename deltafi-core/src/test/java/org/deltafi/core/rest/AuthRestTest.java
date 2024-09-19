/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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

import org.deltafi.core.audit.CoreAuditLogger;
import org.deltafi.core.configuration.AuthProperties;
import org.deltafi.core.exceptions.InvalidRequestException;
import org.deltafi.core.types.DeltaFiUserDetails;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthRestTest {

    private static final DeltaFiUserDetails ADMIN  = DeltaFiUserDetails.builder().username("admin").permissionSet(Set.of("Admin")).build();
    private static final DeltaFiUserDetails UIOnly = DeltaFiUserDetails.builder().username("ui").permissionSet(Set.of("UIAccess")).build();
    private final AuthRest authRest = new AuthRest("local.deltafi", new AuthProperties("cert"), new CoreAuditLogger());

    @Test
    void testDomainCheckHasAccessAdminAccess() {
        // admin can go anywhere
        assertThatCode(() -> authRest.verifyDomainAccess("https://local.deltafi", ADMIN)).doesNotThrowAnyException();
        assertThatCode(() -> authRest.verifyDomainAccess("https://metrics.local.deltafi", ADMIN)).doesNotThrowAnyException();
        assertThatCode(() -> authRest.verifyDomainAccess("https://does.not.matter", ADMIN)).doesNotThrowAnyException();
    }

    @Test
    void testDomainCheckUiOnlyAccess() {
        // UI only user can get to domain only, not a subdomain
        assertThatCode(() -> authRest.verifyDomainAccess("https://local.deltafi", UIOnly)).doesNotThrowAnyException();
        assertThatThrownBy(() -> authRest.verifyDomainAccess("https://metrics.local.deltafi", UIOnly))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessage("403 FORBIDDEN");
    }

    @Test
    void testHostExtractionErrorHandling() {
        assertThatThrownBy(() -> authRest.verifyDomainAccess(null, ADMIN))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Missing required header X-ORIGINAL-URL");

        assertThatThrownBy(() -> authRest.verifyDomainAccess("", ADMIN))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Missing required header X-ORIGINAL-URL");

        assertThatThrownBy(() -> authRest.verifyDomainAccess("htps:www.malformed", ADMIN))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Invalid X-ORIGINAL-URL header provided");
    }
}