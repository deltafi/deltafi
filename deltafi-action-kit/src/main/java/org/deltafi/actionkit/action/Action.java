package org.deltafi.actionkit.action;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.graphql.dgs.client.codegen.BaseProjectionNode;
import com.netflix.graphql.dgs.client.codegen.GraphQLQuery;
import com.netflix.graphql.dgs.client.codegen.GraphQLQueryRequest;
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
import org.deltafi.actionkit.exception.DgsPostException;
import org.deltafi.actionkit.service.ActionEventService;
import org.deltafi.actionkit.service.DomainGatewayService;
import org.deltafi.actionkit.service.HostnameService;
import org.deltafi.common.properties.DeltaFiSystemProperties;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.trace.DeltafiSpan;
import org.deltafi.common.trace.ZipkinService;
import org.deltafi.common.content.ContentReference;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.core.domain.api.types.*;

import javax.annotation.PostConstruct;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@RequiredArgsConstructor
public abstract class Action<P extends ActionParameters> {
    private static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper().enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);

    @Inject
    protected DomainGatewayService domainGatewayService;

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
    protected DeltaFiSystemProperties deltaFiSystemProperties;

    @Inject
    ActionVersionProperty actionVersionProperty;

    private final Class<P> paramType;
    private GraphQLQuery registrationQuery = null;
    private BaseProjectionNode registrationProjection = null;

    @SuppressWarnings("unused")
    public void start(@Observes StartupEvent start) {
        // quarkus will prune the actions if this is not included
    }

    public abstract Result execute(DeltaFile deltaFile, ActionContext actionContext, P params);

    @PostConstruct
    public void startAction() {
        log.info("Starting action: {}", getFeedString());

        final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleWithFixedDelay(this::startListening, config.actionPollingInitialDelayMs(),
                config.actionPollingPeriodMs(), TimeUnit.MILLISECONDS);

        final ScheduledExecutorService registerScheduler = Executors.newScheduledThreadPool(1);
        registerScheduler.scheduleWithFixedDelay(this::registerParamSchema, config.actionRegistrationInitialDelayMs(),
                config.actionRegistrationPeriodMs(), TimeUnit.MILLISECONDS);
    }

    private void startListening() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                ActionInput actionInput = actionEventService.getAction(getFeedString());
                ActionContext actionContext = actionInput.getActionContext();
                actionContext.setActionVersion(getVersion());
                actionContext.setHostname(getHostname());

                log.trace("Running action with input {}", actionInput);
                DeltaFile deltaFile = actionInput.getDeltaFile();
                P params = convertToParams(actionInput.getActionParams());

                SourceInfo sourceInfo = deltaFile.getSourceInfo();
                DeltafiSpan span = zipkinService.createChildSpan(deltaFile.getDid(), actionContext.getName(), sourceInfo.getFilename(), sourceInfo.getFlow());

                executeAction(deltaFile, actionContext, params, span);
            }
        } catch (Throwable e) {
            log.error("Tell Jeremy he really, really needs to fix this exception: " + e.getMessage());
        }
    }

    private void executeAction(DeltaFile deltaFile, ActionContext actionContext, P params, DeltafiSpan span) throws JsonProcessingException {
        try {
            Result result = execute(deltaFile, actionContext, params);
            if (result != null) {
                actionEventService.submitResult(result);
                ActionMetricsLogger.logMetrics(result);
            }
            zipkinService.markSpanComplete(span);
        } catch (DgsPostException ignored) {
            // do nothing -- the error has already been logged
        } catch (Throwable e) {
            StringWriter stackWriter = new StringWriter();
            e.printStackTrace(new PrintWriter(stackWriter));
            String reason = "Action execution exception: " + "\n" + e.getMessage() + "\n" + stackWriter;
            log.error(actionContext.getName() + " submitting error result for " + deltaFile.getDid() + ": " + reason);
            ErrorResult errorResult = new ErrorResult(actionContext, "Action execution exception", e).logErrorTo(log);
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

    void registerParamSchema() {
        try {
            doRegisterParamSchema();
        } catch (Exception exception) {
            log.error("Could not send action parameter schema", exception);
        }
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
        log.trace("Registering schema: {}", schemaJson.toPrettyString());
        return definition;
    }

    private GraphQLQuery doGetRegistrationQuery() {
        if (Objects.isNull(registrationQuery)) {
            registrationQuery = getRegistrationQuery();
        }

        return registrationQuery;
    }

    public abstract GraphQLQuery getRegistrationQuery();

    void doRegisterParamSchema() {
        domainGatewayService.submit(new GraphQLQueryRequest(
                doGetRegistrationQuery(), doGetRegistrationProjection()));
    }

    private BaseProjectionNode doGetRegistrationProjection() {
        if (Objects.isNull(registrationProjection)) {
            registrationProjection = getRegistrationProjection();
        }

        return registrationProjection;
    }

    protected abstract BaseProjectionNode getRegistrationProjection();

    protected String getClassCanonicalName() {
        return this instanceof Subclass ? this.getClass().getSuperclass().getCanonicalName() : this.getClass().getCanonicalName();
    }

    public P convertToParams(Map<String, Object> params) {
        return OBJECT_MAPPER.convertValue(params, paramType);
    }

    @SuppressWarnings("unused")
    protected byte[] loadContent(DeltaFile deltaFile, String protocolLayerType) throws ObjectStorageException {
        byte[] content = null;
        try (InputStream contentInputStream = loadContentAsInputStream(deltaFile, protocolLayerType)) {
            content = contentInputStream.readAllBytes();
        } catch (IOException e) {
            log.warn("Unable to close content input stream", e);
        }
        return content;
    }

    protected InputStream loadContentAsInputStream(DeltaFile deltaFile, String protocolLayerType) throws ObjectStorageException {
        return contentStorageService.load(getContentReference(deltaFile, protocolLayerType));
    }

    protected ContentReference saveContent(String did, byte[] content) throws ObjectStorageException {
        return contentStorageService.save(did, content);
    }

    private ContentReference getContentReference(DeltaFile deltaFile, String protocolLayerType) {
        Optional<ProtocolLayer> protocolLayerOptional = deltaFile.getProtocolLayer(protocolLayerType);
        if (protocolLayerOptional.isEmpty()) {
            throw new RuntimeException("Missing protocol layer for " + protocolLayerType);
        }
        return protocolLayerOptional.get().getContentReference();
    }
}
