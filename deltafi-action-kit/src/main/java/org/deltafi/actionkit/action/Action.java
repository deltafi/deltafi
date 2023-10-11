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
package org.deltafi.actionkit.action;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.actionkit.action.util.ActionParameterSchemaGenerator;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.types.*;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Base class for all DeltaFi Actions.  No action should directly extend this class, but should use
 * specialized classes in the action taxonomy (LoadAction, EgressAction, etc.)
 * @param <I> The input type
 * @param <P> The parameter class that will be used to configure the action instance.
 * @param <R> The result type
 */
@RequiredArgsConstructor
@Slf4j
public abstract class Action<I, P extends ActionParameters, R extends ResultType> {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);

    @Autowired
    @Setter
    protected ContentStorageService contentStorageService;

    @Getter
    private ActionExecution actionExecution = null;

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

    protected final Class<P> paramClass = getGenericParameterType();

    private final ActionType actionType;
    private final String description;

    private ActionDescriptor actionDescriptor;
    private Map<String, Object> definition;

    /**
     * Builds the action-specific input instance used by the execute method.
     * @param actionContext the context for the specific instance of the action being executed
     * @param deltaFileMessage the DeltaFileMessage to build the input from
     * @return the action-specific input instance
     */
    protected abstract I buildInput(@NotNull ActionContext actionContext, @NotNull DeltaFileMessage deltaFileMessage);

    /**
     * Builds an action-specific input instance used by the execute method from a list of action-specific inputs.  This
     * method is used when the action context includes a collect configuration.
     * @param actionInputs the list of action-specific inputs
     * @return the combined action-specific input instance
     */
    protected I collect(@NotNull List<I> actionInputs) {
        throw new UnsupportedOperationException("Collect is not supported for " + getClassCanonicalName());
    }

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

        actionExecution = new ActionExecution(getClassCanonicalName(), actionInput.getActionContext().getName(), actionInput.getActionContext().getDid(), OffsetDateTime.now());

        if (actionInput.getActionContext().getCollect() != null) {
            return execute(actionInput.getActionContext(), collect(actionInput.getDeltaFileMessages().stream()
                            .map(deltaFileMessage -> buildInput(actionInput.getActionContext(), deltaFileMessage)).toList()),
                    convertToParams(actionInput.getActionParams()));
        }

        return execute(actionInput.getActionContext(), buildInput(actionInput.getActionContext(),
                actionInput.getDeltaFileMessages().get(0)), convertToParams(actionInput.getActionParams()));
    }

    public void clearActionExecution() {
        actionExecution = null;
    }

    public ActionDescriptor getActionDescriptor() {
        if (actionDescriptor == null) {
            actionDescriptor = buildActionDescriptor();
        }
        return actionDescriptor;
    }

    protected ActionDescriptor buildActionDescriptor() {
        return ActionDescriptor.builder()
                .name(getClassCanonicalName())
                .description(description)
                .type(actionType)
                .schema(getDefinition())
                .build();
    }

    /**
     * Generate a key/value map for the parameter schema of this action
     * @return Map of parameter class used to configure this action
     */
    public Map<String, Object> getDefinition() {
        if (definition == null) {
            JsonNode schemaJson = ActionParameterSchemaGenerator.generateSchema(paramClass);
            definition = OBJECT_MAPPER.convertValue(schemaJson, new TypeReference<>() {});
            log.trace("Action schema: {}", schemaJson.toPrettyString());
        }
        return definition;
    }

    /**
     * Safely get the canonical name of this action class
     * @return the canonical name of the action class as a string
     */
    public String getClassCanonicalName() {
        return getClass().getCanonicalName();
    }

    /**
     * Convert a map of key/values to a parameter object for the Action
     * @param params Key-value map representing the values in the parameter object
     * @return a parameter object initialized by the params map
     */
    public P convertToParams(@NotNull Map<String, Object> params) {
        return OBJECT_MAPPER.convertValue(params, paramClass);
    }
}
