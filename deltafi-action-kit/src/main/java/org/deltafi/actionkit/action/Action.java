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
package org.deltafi.actionkit.action;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.actionkit.action.parameters.DataSizeModule;
import org.deltafi.common.types.*;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Base class for all DeltaFi Actions.  No action should directly extend this class, but should use
 * specialized classes in the action taxonomy (LoadAction, EgressAction, etc.)
 * @param <I> The input type
 * @param <P> The parameter class that will be used to configure the action instance.
 * @param <R> The result type
 */
@RequiredArgsConstructor
@Getter
public abstract class Action<I, P extends ActionParameters, R extends ResultType> {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .registerModule(new DataSizeModule())
            .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);

    private final ActionType actionType;
    private final String description;

    private final Class<P> paramClass = getGenericParameterType();
    // name of the pod or container running this action
    @Setter
    public String appName;

    private ActionExecution actionExecution = null;

    @Setter
    private int threadNum = 0;

    /**
     * Deep introspection to get the ActionParameters type class.  This keeps subclasses
     * from having to pass this type info as a constructor parameter.
     */
    @SuppressWarnings("unchecked")
    private Class<P> getGenericParameterType() {
        Class<?> clazz = getClass();
        Type type = clazz.getGenericSuperclass();
        while (type != null) {
            try {
                Class<P> typeClz = (Class<P>) ((ParameterizedType) type).getActualTypeArguments()[0];
                if (ActionParameters.class.isAssignableFrom(typeClz)) {
                    return typeClz;
                }
            } catch (Throwable t) {
                // Must be a non-generic class in the inheritance tree
            }
            clazz = clazz.getSuperclass();
            type = clazz.getGenericSuperclass();
        }
        throw new RuntimeException("Cannot instantiate" + getClass());
    }

    /**
     * Builds the action-specific input instance used by the execute method.
     * @param actionContext the context for the specific instance of the action being executed
     * @param deltaFileMessage the DeltaFileMessage to build the input from
     * @return the action-specific input instance
     */
    protected abstract I buildInput(@NotNull ActionContext actionContext, @NotNull DeltaFileMessage deltaFileMessage);

    /**
     * This is the action entry point where all specific action functionality is implemented.
     * @param context The context for the specific instance of the action being executed.  This includes the name
     * of the action, the flow to which it is attached, the version of the action, and the hostname.
     * @param input The action-specific input to the action
     * @param params Any configuration parameters that belong to the specific instance of the action.
     * @return An action result object.  If there is an error, an ErrorResult object should be returned.
     * @see ErrorResult
     */
    protected abstract R execute(@NotNull ActionContext context, @NotNull I input, @NotNull P params);

    public R executeAction(@NotNull ActionInput actionInput) {
        if (actionInput.getDeltaFileMessages() == null || actionInput.getDeltaFileMessages().isEmpty()) {
            throw new ActionKitException("Received actionInput with no deltaFileMessages for did " +
                    actionInput.getActionContext().getDid());
        }

        actionExecution = new ActionExecution(getClassCanonicalName(), actionInput.getActionContext().getActionName(),
                threadNum, actionInput.getActionContext().getDid(), OffsetDateTime.now(), appName);

        if (actionInput.getActionContext().getJoin() != null) {
            return executeJoinAction(actionInput);
        }

        return execute(actionInput.getActionContext(), buildInput(actionInput.getActionContext(),
                actionInput.getDeltaFileMessages().getFirst()), convertToParams(actionInput.getActionParams()));
    }

    protected P convertToParams(@NotNull Map<String, Object> params) {
        return OBJECT_MAPPER.convertValue(params, paramClass);
    }

    public R executeJoinAction(@NotNull ActionInput actionInput) {
        throw new UnsupportedOperationException("Join is not supported for " + getClassCanonicalName());
    }

    public void clearActionExecution() {
        actionExecution = null;
    }

    /**
     * Safely get the canonical name of this action class
     * @return the canonical name of the action class as a string
     */
    public String getClassCanonicalName() {
        return getClass().getCanonicalName();
    }
}
