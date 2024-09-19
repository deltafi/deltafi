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
package org.deltafi.core.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedCredentialsNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DnHeaderFilterTest {

    DnHeaderFilter dnHeaderFilter = new DnHeaderFilter(null);

    @Test
    void getPreAuthenticatedPrincipal() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(DnHeaderFilter.SSL_CLIENT_SUBJECT_DN, "CN=Alice,  OU = Foo,  C=US");

        Object preAuthObject = dnHeaderFilter.getPreAuthenticatedPrincipal(request);

        // verify that this is the normalized DN
        assertThat(preAuthObject).isEqualTo("CN=Alice, OU=Foo, C=US");
    }

    @Test
    void getPreAuthenticatedPrincipalIsCaseInsensitive() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("SSl-client-subject-DN", "CN=Alice,  OU = Foo,  C=US");

        Object preAuthObject = dnHeaderFilter.getPreAuthenticatedPrincipal(request);

        assertThat(preAuthObject).isEqualTo("CN=Alice, OU=Foo, C=US");
    }

    @Test
    void getPreAuthenticatedPrincipalMissingHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertThatThrownBy(() -> dnHeaderFilter.getPreAuthenticatedPrincipal(request))
                .isInstanceOf(PreAuthenticatedCredentialsNotFoundException.class)
                .hasMessage("ssl-client-subject-dn header not found in request.");
    }

    @Test
    void getPreAuthenticatedPrincipalBadCredentials() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(DnHeaderFilter.SSL_CLIENT_SUBJECT_DN, "malformed DN");
        assertThatThrownBy(() -> dnHeaderFilter.getPreAuthenticatedPrincipal(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid DN provided: malformed DN");
    }
}