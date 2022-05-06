/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.Subclass;
import io.quarkus.runtime.StartupEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.metrics.ActionMetricsLogger;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.actionkit.action.util.ActionParameterSchemaGenerator;
import org.deltafi.actionkit.config.ActionKitConfig;
import org.deltafi.actionkit.config.ActionVersionProperty;
import org.deltafi.actionkit.service.ActionEventService;
import org.deltafi.actionkit.service.HostnameService;
import org.deltafi.common.content.ContentReference;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.properties.DeltaFiSystemProperties;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.trace.DeltafiSpan;
import org.deltafi.common.trace.ZipkinService;
import org.deltafi.core.domain.api.types.*;
import org.deltafi.core.domain.generated.types.ActionRegistrationInput;
import org.jetbrains.annotations.NotNull;

import javax.annotation.PostConstruct;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Base class for all DeltaFi Actions.  No action should directly extend this class, but should use
 * specialized classes in the action taxonomy (LoadAction, EgressAction, etc.)
 * @param <P> The parameter class that will be used to configure the action instance.
 */
@Slf4j
@RequiredArgsConstructor
public abstract class Action<P extends ActionParameters> {
    private static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper().enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);

    @Inject
    protected ContentStorageService contentStorageService;

    @Inject
    protected ZipkinService zipkinService;

    @Inject
    protected ActionEventService actionEventService;

    @Inject
    protected ActionKitConfig config;

    @Inject
    protected HostnameService hostnameService;

    @Inject
    @SuppressWarnings("CdiInjectionPointsInspection")
    protected DeltaFiSystemProperties deltaFiSystemProperties;

    @Inject
    @SuppressWarnings("CdiInjectionPointsInspection")
    protected ActionVersionProperty actionVersionProperty;

    private final ActionType actionType;
    private final Class<P> paramType;

    private final ScheduledExecutorService startListeningExecutor = Executors.newSingleThreadScheduledExecutor();

    /**
     * Empty method that prevents Quarkus engine from pruning action classes at startup
     * @param start Event that is generated at startup
     */
    @SuppressWarnings({"unused", "EmptyMethod"})
    public void start(@Observes StartupEvent start) {
        // quarkus will prune the actions if this is not included
    }

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
    protected abstract Result execute(@NotNull DeltaFile deltaFile, @NotNull ActionContext context, @NotNull P params);

    /**
     * Automatically called after construction to initiate polling for inbound actions to be executed
     */
    @PostConstruct
    public void startAction() {
        log.info("Starting action: {}", getFeedString());
        startListeningExecutor.scheduleWithFixedDelay(this::startListening,
                config.actionPollingInitialDelayMs(), config.actionPollingPeriodMs(), TimeUnit.MILLISECONDS);
    }

    private void startListening() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                ActionInput actionInput = actionEventService.getAction(getFeedString());
                ActionContext context = actionInput.getActionContext();
                context.setActionVersion(getVersion());
                context.setHostname(getHostname());
                context.setStartTime(OffsetDateTime.now());

                log.trace("Running action with input {}", actionInput);
                DeltaFile deltaFile = actionInput.getDeltaFile();
                P params = convertToParams(actionInput.getActionParams());

                SourceInfo sourceInfo = deltaFile.getSourceInfo();

                DeltafiSpan span = zipkinService.isEnabled() ? zipkinService.createChildSpan(deltaFile.getDid(), context.getName(), sourceInfo.getFilename(), sourceInfo.getFlow()) : null;

                executeAction(deltaFile, context, params);

                if (Objects.nonNull(span)) { zipkinService.markSpanComplete(span); }
            }
        } catch (Throwable e) {
            log.error("Unexpected exception caught at Action thread execution level: " + e.getMessage());
        }
    }

    private void executeAction(DeltaFile deltaFile, ActionContext context, P params) throws JsonProcessingException {
        try {
            Result result = execute(deltaFile, context, params);
            if (Objects.isNull(result)) {
                throw new RuntimeException("Action " + context.getName() + " returned null Result for did " + context.getDid());
            }

            actionEventService.submitResult(result);
            ActionMetricsLogger.logMetrics(actionType, result);
        } catch (Throwable e) {
            ErrorResult errorResult = new ErrorResult(context, "Action execution exception", e).logErrorTo(log);
            actionEventService.submitResult(errorResult);

            // TODO: Log metrics on error caused by exception???
        }
    }

    private String getFeedString() {
        if (this instanceof Subclass) {
            return this.getClass().getSuperclass().getCanonicalName();
        }

        return this.getClass().getCanonicalName();
    }

    /**
     * @return the version of this action
     */
    public final String getVersion() {
        if (Objects.nonNull(actionVersionProperty)) {
            return actionVersionProperty.getVersion();
        }

        return "UNKNOWN";
    }

    /**
     * @return hostname of the host where this action is running
     */
    public final String getHostname() {
        return hostnameService.getHostname();
    }

    /**
     * @return system name for the current DeltaFi system
     */
    @SuppressWarnings("unused")
    public final String getSystemName() {
        return deltaFiSystemProperties.getSystemName();
    }

    /**
     * Get the canonical class name of the parameter class
     * @return parameter class name
     */
    protected String getParamClass() {
        return paramType.getCanonicalName();
    }

    /**
     * Generate a key/value map for the parameter schema of this action
     * @return Map of parameter class used to configure this action
     */
    protected Map<String, Object> getDefinition() {
        JsonNode schemaJson = ActionParameterSchemaGenerator.generateSchema(paramType);
        Map<String, Object> definition = OBJECT_MAPPER.convertValue(schemaJson, new TypeReference<>(){});
        log.trace("Action schema: {}", schemaJson.toPrettyString());
        return definition;
    }

    /**
     * Each action type base class should implement this method.  The implementation should provide an action
     * schema and add it to the provided ActionRegistrationInput.  This method will be invoked by an action
     * registration service for each type of action that is created.
     *
     * @param a An ActionRegistrationInput object representing the schema for the implemented action type
     */
    public abstract void registerSchema(ActionRegistrationInput a);

    /**
     * Safely get the canonical name of this action class
     * @return the canonical name of the action class as a string
     */
    protected String getClassCanonicalName() {
        return this instanceof Subclass ? this.getClass().getSuperclass().getCanonicalName() : this.getClass().getCanonicalName();
    }

    /**
     * Convert a map of key/values to a parameter object for the Action
     * @param params Key-value map representing the values in the paraameter object
     * @return a parameter object initialized by the params map
     */
    public P convertToParams(Map<String, Object> params) {
        return OBJECT_MAPPER.convertValue(params, paramType);
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
    protected ContentReference saveContent(String did, InputStream content, String mediaType) throws ObjectStorageException {
        return contentStorageService.save(did, content, mediaType);
    }

    /**
     * Delete content for a specific DID.  All content associated with a specific
     * DeltaFile DID will be deleted
     * @param did DeltaFile identifier for content to be deleted
     * @return true on success, false if unable to delete content
     */
    protected boolean deleteContent(String did) {
        return contentStorageService.deleteAll(did);
    }
}
