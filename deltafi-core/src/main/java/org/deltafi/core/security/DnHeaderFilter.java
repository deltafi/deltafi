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

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedCredentialsNotFoundException;
import org.springframework.security.web.authentication.preauth.RequestHeaderAuthenticationFilter;

@Slf4j
public class DnHeaderFilter extends RequestHeaderAuthenticationFilter {

    public static final String SSL_CLIENT_SUBJECT_DN = "ssl-client-subject-dn";

    public DnHeaderFilter(AuthenticationManager authenticationManager) {
        this.setPrincipalRequestHeader(SSL_CLIENT_SUBJECT_DN);
        this.setExceptionIfHeaderMissing(true);
        this.setAuthenticationManager(authenticationManager);
    }

    /**
     * Read and returns the header named by {@code principalRequestHeader} from the
     * request.
     * @throws PreAuthenticatedCredentialsNotFoundException if the header is missing and
     * {@code exceptionIfHeaderMissing} is set to {@code true}.
     */
    @Override
    protected Object getPreAuthenticatedPrincipal(HttpServletRequest request) {
        Object rawPrincipal = super.getPreAuthenticatedPrincipal(request);

        if (rawPrincipal instanceof String principal) {
            try {
                return DnUtil.normalizeDn(principal);
            } catch (IllegalArgumentException e) {
                log.error("Invalid DN received: {}", principal, e);
            }
        }

        throw new BadCredentialsException("Invalid DN provided: " + rawPrincipal);
    }
}
