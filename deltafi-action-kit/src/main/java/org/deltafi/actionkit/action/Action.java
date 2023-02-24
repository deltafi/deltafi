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
import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.actionkit.action.util.ActionParameterSchemaGenerator;
import org.deltafi.common.content.ContentReference;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.types.*;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
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
                return (Class<P>) ((ParameterizedType) type).getActualTypeArguments()[0];
            } catch (Throwable t) {
                // Must be a non-generic class in the inheritance tree
            }
            clazz = clazz.getSuperclass();
            type = clazz.getGenericSuperclass();
        }
        throw new RuntimeException("Cannot instantiate" + getClass());
    }

    private final Class<P> paramClass = getGenericParameterType();

    private final ActionType actionType;
    private final String description;

    private ActionDescriptor actionDescriptor;
    private Map<String, Object> definition;

    /**
     * This is the action entry point where all specific action functionality is implemented.  This abstract method
     * must be implemented by each Action subclass.
     * @param deltaFile An instance of DeltaFile against which the action is executed
     * @param context The context for the specific instance of the action being executed.  This includes the name of the
     *                action, the flow to which it is attached, the version of the action, and the hostname
     * @param params Any configuration parameters that belong to the specific instance of the action.
     * @return An action result object.  If there is an error, an ErrorResult object should be returned.
     * @see ErrorResult
     */
    protected abstract ResultType execute(@Nonnull DeltaFile deltaFile, @Nonnull ActionContext context, @Nonnull P params);

    public ResultType executeAction(ActionInput actionInput) {
        return execute(actionInput.getDeltaFile(), actionInput.getActionContext(),
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
     * Load a content reference from the content storage service as a byte array
     * @param contentReference Reference to content to be loaded
     * @return a byte array for the loaded content
     * @throws ObjectStorageException when the load from the content storage service fails
     */
    @SuppressWarnings("unused")
    protected byte[] loadContent(ContentReference contentReference) throws ObjectStorageException {
        byte[] content = null;
        try (InputStream contentInputStream = loadContentAsInputStream(contentReference)) {
            content = contentInputStream.readAllBytes();
        } catch (IOException e) {
            log.warn("Unable to close content input stream", e);
        }
        return content;
    }

    /**
     * Load a content reference from the content storage service as an InputStream
     * @param contentReference Reference to content to be loaded
     * @return an InputStream for the loaded content
     * @throws ObjectStorageException when the load from the content storage service fails
     */
    protected InputStream loadContentAsInputStream(ContentReference contentReference) throws ObjectStorageException {
        return contentStorageService.load(contentReference);
    }

    /**
     * Save content associated with a DeltaFile to content storage
     * @param did The DID for the DeltaFile associated with the saved content
     * @param content Byte array of content to store.  The entire byte array will be stored in content storage
     * @param mediaType Media type for the content being stored
     * @return a content reference for the new stored content
     * @throws ObjectStorageException when the content storage service fails to store content
     */
    @SuppressWarnings("unused")
    protected ContentReference saveContent(String did, byte[] content, String mediaType) throws ObjectStorageException {
        return contentStorageService.save(did, content, mediaType);
    }

    /**
     * Save content associated with a DeltaFile to content storage
     * @param did The DID for the DeltaFile associated with the saved content
     * @param content InputStream of content to store.  The entire stream will be read into content storage, and the
     *                stream may be closed by underlying processors after execution
     * @param mediaType Media type for the content being stored
     * @return a content reference for the new stored content
     * @throws ObjectStorageException when the content storage service fails to store content
     */
    protected ContentReference saveContent(String did, InputStream content, @SuppressWarnings("SameParameterValue") String mediaType) throws ObjectStorageException {
        return contentStorageService.save(did, content, mediaType);
    }

    /**
     * Save content associated with a DeltaFile to content storage
     * @param did The DID for the DeltaFile associated with the saved content
     * @param contentToBytes map of content objects to the bytes that need to be stored for the content
     * @return an updated list of content that includes the new content references
     * @throws ObjectStorageException when the content storage service fails to store content
     */
    protected List<Content> saveContent(String did, Map<Content, byte[]> contentToBytes) throws ObjectStorageException {
        return contentStorageService.saveMany(did, contentToBytes);
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
