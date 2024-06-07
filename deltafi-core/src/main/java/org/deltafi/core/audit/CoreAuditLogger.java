/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
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

import com.netflix.graphql.dgs.context.DgsContext;
import com.netflix.graphql.dgs.internal.DgsWebMvcRequestData;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInvocation;
import org.deltafi.core.security.DeltaFiUserDetailsService;
import org.jetbrains.annotations.NotNull;
import org.slf4j.MDC;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authorization.event.AuthorizationDeniedEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.deltafi.common.constant.DeltaFiConstants.USER_NAME_HEADER;

@Component
@Slf4j(topic = "AUDIT")
public class CoreAuditLogger extends SimplePerformantInstrumentation {

    private static final String UNKNOWN_USER = "system";
    private static final String IGNORABLE_PATH = "registerActions";

    private final Map<String, String> permissionMap = new ConcurrentHashMap<>();

    @Override
    @NotNull
    public DataFetcher<?> instrumentDataFetcher(DataFetcher<?> dataFetcher, InstrumentationFieldFetchParameters parameters, InstrumentationState state) {
        String path = parameters.getExecutionStepInfo().getPath().getSegmentName();
        boolean isMutation = isMutation(parameters);

        return environment -> {
            if (isMutation && !IGNORABLE_PATH.equals(path)) {

                DgsWebMvcRequestData webContext = DgsContext.getCustomContext(environment);
                List<String> id = null != webContext && null != webContext.getHeaders() ? webContext.getHeaders().getOrEmpty(USER_NAME_HEADER) : List.of();

                String userName = !id.isEmpty() ? id.getFirst() : UNKNOWN_USER;

                try (@SuppressWarnings("unused") MDC.MDCCloseable mdc = MDC.putCloseable("user", userName)) {
                    log.info("called mutation {}", path);
                }
            }

            return dataFetcher.get(environment);
        };
    }

    private boolean isMutation(InstrumentationFieldFetchParameters parameters) {
        GraphQLOutputType type = parameters.getExecutionStepInfo().getParent().getType();
        GraphQLObjectType parent;
        if (type instanceof GraphQLNonNull) {
            parent = (GraphQLObjectType) ((GraphQLNonNull) type).getWrappedType();
        } else {
            parent = (GraphQLObjectType) type;
        }

        return "Mutation".equals(parent.getName());
    }

    public void logIngress(String userName, String fileName) {
        try (MDC.MDCCloseable ignored = MDC.putCloseable("user", userName)) {
            log.info("ingress {}", fileName);
        }
    }

    public void logSurvey(String userName, String flow, String subflow, String direction, Long bytes, Long count) {
        try (MDC.MDCCloseable ignored = MDC.putCloseable("user", userName)) {
            log.info("survey flow={} subflow={} direction={} bytes={} count={}", flow, subflow, direction, bytes, count);
        }
    }

    public void logDelete(String policy, List<UUID> dids, boolean metadata) {
        try (MDC.MDCCloseable ignored = MDC.putCloseable("user", UNKNOWN_USER)) {
            for (UUID did : dids) {
                log.info("policy {} deleted {} content{}", policy, did, (metadata ? " and metadata" : ""));
            }
        }
    }

    public void audit(String message, Object ... objects) {
        String username = DeltaFiUserDetailsService.currentUsername();
        try (MDC.MDCCloseable ignored = MDC.putCloseable("user", username)) {
            log.info(message, objects);
        }
    }

    @EventListener
    public void accessDeniedLogger(AuthorizationDeniedEvent<MethodInvocation> authorizationDeniedEvent) {
        String username = extractUsername(authorizationDeniedEvent);
        String methodCalled = extractMethodName(authorizationDeniedEvent);
        String missingPermissions = extractMissingPermissions(authorizationDeniedEvent);

        try (@SuppressWarnings("unused") MDC.MDCCloseable mdc = MDC.putCloseable("user", username)) {
            log.info("request '{}' was denied due to missing permission '{}'", methodCalled, missingPermissions);
        }
    }


    private String extractUsername(AuthorizationDeniedEvent<MethodInvocation> authorizationDeniedEvent) {
        Authentication authentication = authorizationDeniedEvent.getAuthentication().get();
        Object rawUser = authentication.getPrincipal();
        if (rawUser instanceof User user) {
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
}
