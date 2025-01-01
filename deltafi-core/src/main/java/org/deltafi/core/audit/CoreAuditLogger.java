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
package org.deltafi.core.audit;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInvocation;
import org.deltafi.core.services.DeltaFiUserService;
import org.slf4j.MDC;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authorization.event.AuthorizationDeniedEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Slf4j(topic = "AUDIT")
public class CoreAuditLogger {

    private final Map<String, String> permissionMap = new ConcurrentHashMap<>();

    public void audit(String message, Object ... objects) {
        String username = DeltaFiUserService.currentUsername();
        try (MDC.MDCCloseable ignored = MDC.putCloseable("user", username)) {
            log.info(message, objects);
        }
    }

    @EventListener
    public void accessDeniedLogger(AuthorizationDeniedEvent<MethodInvocation> authorizationDeniedEvent) {
        if (isInitialLogin(authorizationDeniedEvent)) {
            return;
        }
        String username = extractUsername(authorizationDeniedEvent);
        String methodCalled = extractMethodName(authorizationDeniedEvent);
        String missingPermissions = extractMissingPermissions(authorizationDeniedEvent);

        try (@SuppressWarnings("unused") MDC.MDCCloseable mdc = MDC.putCloseable("user", username)) {
            log.info("request '{}' was denied due to missing permission '{}'", methodCalled, missingPermissions);
        }
    }

    private boolean isInitialLogin(AuthorizationDeniedEvent<MethodInvocation> authorizationDeniedEvent) {
        return authorizationDeniedEvent.getSource() instanceof HttpServletRequest;
    }

    private String extractUsername(AuthorizationDeniedEvent<MethodInvocation> authorizationDeniedEvent) {
        Authentication authentication = authorizationDeniedEvent.getAuthentication().get();
        Object rawUser = authentication.getPrincipal();
        if (rawUser instanceof UserDetails user) {
            return user.getUsername();
        }
        return rawUser.toString();
    }

    private String extractMethodName(AuthorizationDeniedEvent<MethodInvocation> authorizationDeniedEvent) {
        Object rawMethodInvocation = authorizationDeniedEvent.getSource();
        if (rawMethodInvocation instanceof MethodInvocation methodInvocation) {
            return methodInvocation.getMethod().getName();
        }
        return "unknownMethod";
    }

    private String extractMissingPermissions(AuthorizationDeniedEvent<MethodInvocation> authorizationDeniedEvent) {
        String permissions = "unknownPermissions";
        Object rawMethodInvocation = authorizationDeniedEvent.getSource();
        if (rawMethodInvocation instanceof MethodInvocation methodInvocation) {
            Method method = methodInvocation.getMethod();
            permissions = permissionMap.get(method.getName());
            if (permissions == null) {
                permissions = extractMissingPermissions(method);
                permissionMap.put(method.getName(), permissions);
            }
        }

        return permissions;
    }

    private String extractMissingPermissions(Method method) {
        PreAuthorize preAuthorize = AnnotationUtils.findAnnotation(method, PreAuthorize.class);
        if (preAuthorize != null) {
            String permissions = preAuthorize.value();
            permissions = permissions.substring(permissions.indexOf('(') + 1, permissions.indexOf(')'));
            permissions = permissions.replace("'", "");
            // do not include admin in the message, just take the first permission
            return permissions.split(",")[0];
        }

        return null;
    }

    public static <T> String listToString(List<T> list) {
        return listToString(list, T::toString);
    }

    public static <T> String listToString(List<T> list, Function<T, String> toString) {
        return list.stream().map(toString).collect(Collectors.joining(", "));
    }
}
