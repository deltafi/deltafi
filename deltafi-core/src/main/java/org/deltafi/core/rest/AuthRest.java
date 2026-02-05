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
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.deltafi.core.audit.CoreAuditLogger;
import org.deltafi.core.configuration.AuthProperties;
import org.deltafi.core.exceptions.InvalidRequestException;
import org.deltafi.core.types.DeltaFiUserDetails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;

import static org.deltafi.common.constant.DeltaFiConstants.*;

@RestController
@RequestMapping("/api/v2/auth")
@Slf4j
public class AuthRest {

    private static final String X_ORIGINAL_URL = "X-ORIGINAL-URL";
    private static final String ORCHESTRATION_PATH = "/orchestration";
    private static final String VISUALIZATION_PATH = "/visualization";

    private final String domain;
    private final AuthProperties authProperties;
    private final String headlampToken;
    private final CoreAuditLogger auditLogger;

    public AuthRest(@Value("${DELTAFI_UI_DOMAIN}") String domain, AuthProperties authProperties,
            @Value("${HEADLAMP_TOKEN:#{null}}") String headlampToken, CoreAuditLogger auditLogger) {
        this.domain = domain;
        this.authProperties = authProperties;
        this.headlampToken = headlampToken;
        this.auditLogger = auditLogger;
    }

    @GetMapping
    public void get(@AuthenticationPrincipal UserDetails user, HttpServletResponse response,
                    @RequestHeader(value = X_ORIGINAL_URL, required = false) String originalUrl) {
        if (user instanceof DeltaFiUserDetails deltaFiUserDetails) {
            String requestedDomain = extractDomain(originalUrl);
            verifyDomainAccess(requestedDomain, deltaFiUserDetails);
            String requestedPath = extractPath(originalUrl);
            verifyPathAccess(requestedPath, deltaFiUserDetails);
            response.setStatus(HttpServletResponse.SC_OK);
            response.setHeader(USER_NAME_HEADER, user.getUsername());
            response.setHeader(USER_ID_HEADER, deltaFiUserDetails.id());
            response.setHeader(PERMISSIONS_HEADER, deltaFiUserDetails.getPermissions());
            response.setHeader(USER_METRIC_ROLE_HEADER, deltaFiUserDetails.metricsRole());
            if ((headlampToken != null) && requestedPath.startsWith(ORCHESTRATION_PATH)) {
                response.setHeader(AUTHORIZATION_HEADER, "Bearer " + headlampToken);
            }
        } else {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    private void verifyPathAccess(String requestedPath, DeltaFiUserDetails user) {
        if (authProperties.noAuth()) {
            return;
        }

        String requiredPermission = pathPermission(requestedPath);

        if (requiredPermission == null) {
            return;
        }

        if (!user.hasPermission(requiredPermission)) {
            auditLogger.audit("request to '{}' was denied due to missing permission '{}'", requestedPath, requiredPermission);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    private void verifyDomainAccess(String requestedDomain, DeltaFiUserDetails user) {
        if (authProperties.noAuth()) {
            return;
        }

        // calls to the standard domain will use the regular required permissions method for authorization
        if (this.domain.equals(requestedDomain)) {
            return;
        }

        String requiredPermission = domainPermission(requestedDomain);

        if (!user.hasPermission(requiredPermission)) {
            auditLogger.audit("request to the '{}' domain was denied due to missing permission '{}'", requestedDomain, requiredPermission);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    private String domainPermission(String domain) {
        // Make sure this a valid domain and take the first chunk
        if (domain.contains(".") && domain.endsWith(this.domain)) {
            domain = domain.substring(0, domain.indexOf("."));
        }

        return switch(domain) {
            case "graphite", "metrics" -> "MetricsView";
            case "ingress" -> "DeltaFileIngress";
            default -> "Admin";
        };
    }

    private String pathPermission(String path) {
        if (path == null) {
            return null;
        }

        if (path.startsWith(VISUALIZATION_PATH)) {
            return "MetricsView";
        }

        if (path.startsWith(ORCHESTRATION_PATH)) {
            return "Admin";
        }

        return null;
    }

    private String extractDomain(String originalUrl) {
        if (StringUtils.isBlank(originalUrl)) {
            throw new InvalidRequestException("Missing required header " + X_ORIGINAL_URL);
        }

        try {
            String host = URI.create(originalUrl).getHost();
            if (StringUtils.isBlank(host)) {
                throw invalidRequestException();
            }
            return host;
        } catch (IllegalArgumentException e) {
            log.error("Illegal URL in auth check {}", originalUrl, e);
            throw invalidRequestException();
        }
    }

    private String extractPath(String originalUrl) {
        if (StringUtils.isBlank(originalUrl)) {
            throw new InvalidRequestException("Missing required header " + X_ORIGINAL_URL);
        }

        try {
            return URI.create(originalUrl).getPath();
        } catch (IllegalArgumentException e) {
            log.error("Illegal URL in auth check {}", originalUrl, e);
            throw invalidRequestException();
        }
    }

    private InvalidRequestException invalidRequestException() {
        return new InvalidRequestException("Invalid " + X_ORIGINAL_URL + " header provided");
    }
}
