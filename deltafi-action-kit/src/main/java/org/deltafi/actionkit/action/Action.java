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
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
    ActionVersionProperty actionVersionProperty;

    private final ActionType actionType;
    private final Class<P> paramType;

    private final ScheduledExecutorService startListeningExecutor = Executors.newSingleThreadScheduledExecutor();

    @SuppressWarnings({"unused", "EmptyMethod"})
    public void start(@Observes StartupEvent start) {
        // quarkus will prune the actions if this is not included
    }

    protected abstract Result execute(@NotNull DeltaFile deltaFile, @NotNull ActionContext context, @NotNull P params);

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

                log.trace("Running action with input {}", actionInput);
                DeltaFile deltaFile = actionInput.getDeltaFile();
                P params = convertToParams(actionInput.getActionParams());

                SourceInfo sourceInfo = deltaFile.getSourceInfo();

                DeltafiSpan span = zipkinService.isEnabled() ? zipkinService.createChildSpan(deltaFile.getDid(), context.getName(), sourceInfo.getFilename(), sourceInfo.getFlow()) : null;

                executeAction(deltaFile, context, params);

                if (Objects.nonNull(span)) { zipkinService.markSpanComplete(span); }
            }
        } catch (Throwable e) {
            log.error("Tell Jeremy he really, really needs to fix this exception: " + e.getMessage());
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

    public final String getVersion() {
        if (Objects.nonNull(actionVersionProperty)) {
            return actionVersionProperty.getVersion();
        }

        return "UNKNOWN";
    }

    public final String getHostname() {
        return hostnameService.getHostname();
    }

    @SuppressWarnings("unused")
    public final String getSystemName() {
        return deltaFiSystemProperties.getSystemName();
    }

    protected String getParamClass() {
        return paramType.getCanonicalName();
    }

    protected Map<String, Object> getDefinition() {
        JsonNode schemaJson = ActionParameterSchemaGenerator.generateSchema(paramType);
        Map<String, Object> definition = OBJECT_MAPPER.convertValue(schemaJson, new TypeReference<>(){});
        log.trace("Action schema: {}", schemaJson.toPrettyString());
        return definition;
    }

    public abstract void registerSchema(ActionRegistrationInput a);

    protected String getClassCanonicalName() {
        return this instanceof Subclass ? this.getClass().getSuperclass().getCanonicalName() : this.getClass().getCanonicalName();
    }

    public P convertToParams(Map<String, Object> params) {
        return OBJECT_MAPPER.convertValue(params, paramType);
    }

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

    protected InputStream loadContentAsInputStream(ContentReference contentReference) throws ObjectStorageException {
        return contentStorageService.load(contentReference);
    }

    protected ContentReference saveContent(String did, byte[] content, String mediaType) throws ObjectStorageException {
        return contentStorageService.save(did, content, mediaType);
    }

    protected ContentReference saveContent(String did, InputStream content, String mediaType) throws ObjectStorageException {
        return contentStorageService.save(did, content, mediaType);
    }

    protected boolean deleteContent(String did) {
        return contentStorageService.deleteAll(did);
    }
}
