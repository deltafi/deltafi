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
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.actionkit.action.util.ActionParameterSchemaGenerator;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.types.*;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.Nonnull;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

/**
 * Base class for all DeltaFi Actions.  No action should directly extend this class, but should use
 * specialized classes in the action taxonomy (LoadAction, EgressAction, etc.)
 * @param <P> The parameter class that will be used to configure the action instance.
 */
@RequiredArgsConstructor
@Slf4j
public abstract class Action<P extends ActionParameters> {
    private static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper().enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);

    @Autowired
    @Setter
    protected ContentStorageService contentStorageService;

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
     * This is the action entry point where all specific action functionality is implemented.  This abstract method
     * must be implemented by each Action subclass.
     * @param deltaFileMessages Attributes of the DeltaFiles against which the action is executed
     * @param context The context for the specific instance of the action being executed.  This includes the name of the
     *                action, the flow to which it is attached, the version of the action, and the hostname
     * @param params Any configuration parameters that belong to the specific instance of the action.
     * @return An action result object.  If there is an error, an ErrorResult object should be returned.
     * @see ErrorResult
     */
    protected abstract ResultType execute(@Nonnull List<DeltaFileMessage> deltaFileMessages, @Nonnull ActionContext context, @Nonnull P params);

    public ResultType executeAction(ActionInput actionInput) {
        if (actionInput.getDeltaFileMessages() == null || actionInput.getDeltaFileMessages().isEmpty()) {
            throw new ActionKitException("Received actionInput with no deltaFileMessages for did " + actionInput.getActionContext().getDid());
        }
        return execute(actionInput.getDeltaFileMessages(), actionInput.getActionContext(),
                convertToParams(actionInput.getActionParams()));
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
     * @param params Key-value map representing the values in the paraameter object
     * @return a parameter object initialized by the params map
     */
    public P convertToParams(Map<String, Object> params) {
        return OBJECT_MAPPER.convertValue(params, paramClass);
    }
}
