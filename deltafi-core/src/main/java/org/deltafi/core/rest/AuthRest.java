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

    public static final String X_ORIGINAL_URL = "X-ORIGINAL-URL";
    private final String domain;
    private final AuthProperties authProperties;
    private final CoreAuditLogger auditLogger;

    public AuthRest(@Value("${DELTAFI_UI_DOMAIN}") String domain, AuthProperties authProperties, CoreAuditLogger auditLogger) {
        this.domain = domain;
        this.authProperties = authProperties;
        this.auditLogger = auditLogger;
    }

    @GetMapping
    public void get(@AuthenticationPrincipal UserDetails user, HttpServletResponse response,
                    @RequestHeader(value = X_ORIGINAL_URL, required = false) String originalUrl) {
        if (user instanceof DeltaFiUserDetails deltaFiUserDetails) {
            verifyDomainAccess(originalUrl, deltaFiUserDetails);
            verifyPathAccess(originalUrl, deltaFiUserDetails);
            response.setStatus(HttpServletResponse.SC_OK);
            response.setHeader(USER_NAME_HEADER, user.getUsername());
            response.setHeader(USER_ID_HEADER, deltaFiUserDetails.id());
            response.setHeader(PERMISSIONS_HEADER, deltaFiUserDetails.getPermissions());
            response.setHeader(USER_METRIC_ROLE_HEADER, deltaFiUserDetails.metricsRole());
        } else {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    void verifyPathAccess(String originalUrl, DeltaFiUserDetails user) {
        if (authProperties.noAuth()) {
            return;
        }

        String requestedPath = extractPath(originalUrl);

        String requiredPermission = pathPermission(requestedPath);

        if (requiredPermission == null) {
            return;
        }

        if (!user.hasPermission(requiredPermission)) {
            auditLogger.audit("request to '{}' was denied due to missing permission '{}'", requestedPath, requiredPermission);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    void verifyDomainAccess(String originalUrl, DeltaFiUserDetails user) {
        if (authProperties.noAuth()) {
            return;
        }

        String requestedDomain = extractDomain(originalUrl);

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

    String domainPermission(String domain) {
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

    String pathPermission(String path) {
        if (path == null) {
            return null;
        }

        if (path.startsWith("/visualization")) {
            return "MetricsView";
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
            String path = URI.create(originalUrl).getPath();
            if (StringUtils.isBlank(path)) {
                throw invalidRequestException();
            }
            return path;
        } catch (IllegalArgumentException e) {
            log.error("Illegal URL in auth check {}", originalUrl, e);
            throw invalidRequestException();
        }
    }

    private InvalidRequestException invalidRequestException() {
        return new InvalidRequestException("Invalid " + X_ORIGINAL_URL + " header provided");
    }
}
