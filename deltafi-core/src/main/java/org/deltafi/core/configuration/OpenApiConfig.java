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
package org.deltafi.core.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.core.security.NeedsPermission;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Provides stable, deterministic metadata for the springdoc-generated OpenAPI document.
 *
 * <p>The title, version, and a single relative server URL are pinned here so the committed
 * spec ({@code src/main/resources/openapi/openapi.yaml}) only changes when the API itself
 * changes - not on every regeneration. Without the fixed server, springdoc would emit the
 * (random) request host/port, churning the drift check on every run.
 */
@Configuration
public class OpenApiConfig {

    // Authority names inside the @PreAuthorize SpEL are single-quoted, e.g. hasAuthority('FlowView').
    private static final Pattern AUTHORITY = Pattern.compile("'([^']+)'");

    @Bean
    public OpenAPI deltafiOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("DeltaFi REST API")
                        .version("v2")
                        .description("""
                                REST API for the DeltaFi data transformation and enrichment platform.

                                Authorization: endpoints that list a required permission are also accessible to \
                                users holding the Admin permission, which grants every permission system-wide."""))
                .servers(List.of(new Server().url("/")));
    }

    /**
     * Surfaces the authorization required by each endpoint. Permissions are declared on handler methods
     * via {@link NeedsPermission} annotations (shorthand for {@code @PreAuthorize}). We read the required
     * authorities straight from each annotation's {@code @PreAuthorize} SpEL - the source of truth -
     * rather than the annotation's simple name, so compound "X and Y" annotations are represented
     * honestly (the simple name {@code DeltaFileReplayAndAcknowledge} is not a grantable authority).
     *
     * <p>The specific permissions are exposed as a sorted {@code x-required-permissions} vendor extension
     * (machine-readable, ignored by client codegen) plus a human-readable note on the operation
     * description (shown in Swagger UI). When the expression requires <em>all</em> listed permissions
     * (an {@code and}), an {@code x-required-permissions-all: true} flag is added so the list is not
     * misread as any-of. {@code Admin} is filtered out as the universal fallback (documented once on the
     * API description) <em>unless</em> it is the only accepted authority, in which case it is the real
     * requirement and is surfaced as {@code Admin}.
     *
     * <p>Endpoints without a {@link NeedsPermission} annotation (e.g. the unauthenticated plugin
     * registration endpoint) get nothing, which itself signals they are not permission-gated.
     */
    @Bean
    public OperationCustomizer requiredPermissionsCustomizer() {
        return (operation, handlerMethod) -> {
            for (Annotation annotation : handlerMethod.getMethod().getAnnotations()) {
                Class<? extends Annotation> type = annotation.annotationType();
                if (type.getDeclaringClass() != NeedsPermission.class) {
                    continue;
                }
                PreAuthorize preAuthorize = type.getAnnotation(PreAuthorize.class);
                if (preAuthorize == null) {
                    continue;
                }

                String expression = preAuthorize.value();
                boolean requiresAll = expression.contains(" and ");
                List<String> authorities = AUTHORITY.matcher(expression).results()
                        .map(m -> m.group(1))
                        .distinct()
                        .sorted()
                        .toList();

                // Drop the universal Admin fallback unless Admin is the only accepted authority,
                // in which case it is the actual requirement (not the everything-grant fallback).
                List<String> permissions = authorities.stream()
                        .filter(a -> !a.equals(DeltaFiConstants.ADMIN_PERMISSION))
                        .toList();
                if (permissions.isEmpty()) {
                    permissions = authorities; // Admin-only endpoint
                }
                if (permissions.isEmpty()) {
                    return operation;
                }

                operation.addExtension("x-required-permissions", permissions);
                if (requiresAll && permissions.size() > 1) {
                    operation.addExtension("x-required-permissions-all", true);
                }

                String note = (permissions.size() > 1 ? "Requires permissions: " : "Requires permission: ")
                        + String.join(requiresAll ? " and " : " or ", permissions) + ".";
                String description = operation.getDescription();
                operation.setDescription(description == null || description.isBlank()
                        ? note : description + "\n\n" + note);

                return operation; // a handler carries a single NeedsPermission annotation
            }

            return operation;
        };
    }

    /**
     * Removes DeltaFi's internal auth/identity headers ({@code X-User-*}, {@code X-Metrics-Role}) from
     * the documented request parameters. These are populated by the auth proxy, never by clients, and
     * their names are an internal mechanism that can change - so they should not be broadcast in the
     * published contract. The required-permission note (and {@code x-required-permissions}) already
     * conveys the authorization requirement generically, without exposing the header scheme.
     */
    @Bean
    public OperationCustomizer hideInternalAuthHeaders() {
        Set<String> internalHeaders = Set.of(
                DeltaFiConstants.USER_NAME_HEADER,
                DeltaFiConstants.USER_ID_HEADER,
                DeltaFiConstants.PERMISSIONS_HEADER,
                DeltaFiConstants.USER_METRIC_ROLE_HEADER);
        return (operation, handlerMethod) -> {
            if (operation.getParameters() != null) {
                operation.getParameters().removeIf(p ->
                        "header".equals(p.getIn()) && internalHeaders.contains(p.getName()));
            }
            return operation;
        };
    }
}
