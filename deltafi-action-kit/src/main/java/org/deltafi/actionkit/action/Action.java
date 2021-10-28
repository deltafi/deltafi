package org.deltafi.actionkit.action;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.graphql.dgs.client.codegen.GraphQLQueryRequest;
import io.quarkus.arc.Subclass;
import io.quarkus.runtime.StartupEvent;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.actionkit.action.util.ActionParameterSchemaGenerator;
import org.deltafi.actionkit.config.DeltafiConfig;
import org.deltafi.actionkit.exception.DgsPostException;
import org.deltafi.actionkit.service.ActionEventService;
import org.deltafi.actionkit.service.DomainGatewayService;
import org.deltafi.common.metric.MetricLogger;
import org.deltafi.common.trace.DeltafiSpan;
import org.deltafi.common.trace.ZipkinService;
import org.deltafi.core.domain.api.types.ActionInput;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.api.types.JsonMap;
import org.deltafi.core.domain.generated.client.RegisterActionGraphQLQuery;
import org.deltafi.core.domain.generated.client.RegisterActionProjectionRoot;
import org.deltafi.core.domain.generated.types.ActionEventType;
import org.deltafi.core.domain.generated.types.ActionSchemaInput;
import org.deltafi.core.domain.generated.types.SourceInfo;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.annotation.PostConstruct;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public abstract class Action<P extends ActionParameters> {
    private static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper().enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);

    @Inject
    protected DomainGatewayService domainGatewayService;

    @Inject
    protected ZipkinService zipkinService;

    @Inject
    protected ActionEventService actionEventService;

    @Inject
    protected DeltafiConfig config;

    @ConfigProperty(name = "quarkus.application.version", defaultValue = "missing-value")
    String version;

    @SuppressWarnings("unused")
    public void start(@Observes StartupEvent start) {
        // quarkus will prune the actions if this is not included
    }

    public abstract Result execute(DeltaFile deltaFile, P params);

    public abstract Class<P> getParamType();

    @PostConstruct
    void startAction() {
        log.info("Starting action: {}", getFeedString());
        final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleWithFixedDelay(this::startListening, config.actionPollingInitialDelayMs(),
                config.actionPollingPeriodMs(), TimeUnit.MILLISECONDS);

        final ScheduledExecutorService registerScheduler = Executors.newScheduledThreadPool(1);
        registerScheduler.scheduleWithFixedDelay(this::registerParamSchema, config.actionRegistrationInitialDelayMs(),
                config.actionRegistrationPeriodMs(), TimeUnit.MILLISECONDS);
    }

    protected void logFilesProcessedMetric(ActionEventType actionEventType, DeltaFile deltaFile) {
        logMetric(actionEventType, deltaFile, "files_processed", 1);
    }

    protected void logMetric(ActionEventType actionEventType, DeltaFile deltaFile, String name, long value) {
        logMetric(actionEventType, deltaFile, name, value, Map.of());
    }

    protected void logMetric(ActionEventType actionEventType, DeltaFile deltaFile, String name, long value,
                             Map<String, String> extraTags) {
        HashMap<String, String> tags = new HashMap<>();
        tags.put("action", getClass().getSimpleName());
        tags.putAll(extraTags);

        MetricLogger.logMetric(actionEventType.name().toLowerCase(), deltaFile.getDid(),
                deltaFile.getSourceInfo().getFlow(), name, value, tags);
    }

    private void startListening() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                ActionInput actionInput = actionEventService.getAction(getFeedString());

                log.trace("Running action with input {}", actionInput);
                DeltaFile deltaFile = actionInput.getDeltaFile();
                P params = convertToParams(actionInput.getActionParams());

                SourceInfo sourceInfo = deltaFile.getSourceInfo();
                DeltafiSpan span = zipkinService.createChildSpan(deltaFile.getDid(), params.getName(), sourceInfo.getFilename(), sourceInfo.getFlow());

                executeAction(deltaFile, params, span);
            }
        } catch (Throwable e) {
            log.error("Tell Jeremy he really, really needs to fix this exception: " + e.getMessage());
        }
    }

    private void executeAction(DeltaFile deltaFile, P params, DeltafiSpan span) throws JsonProcessingException {
        try {
            Result result = execute(deltaFile, params);
            if (result != null) {
                actionEventService.submitResult(result);
            }
            zipkinService.markSpanComplete(span);
        } catch (DgsPostException ignored) {
            // do nothing -- the error has already been logged
        } catch (Throwable e) {
            StringWriter stackWriter = new StringWriter();
            e.printStackTrace(new PrintWriter(stackWriter));
            String reason = "Action execution exception: " + "\n" + e.getMessage() + "\n" + stackWriter;
            log.error(params.getName() + " submitting error result for " + deltaFile.getDid() + ": " + reason);
            ErrorResult err = new ErrorResult(params.getName(), deltaFile, "Action execution exception", e).logErrorTo(log);
            actionEventService.submitResult(err);
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

    void doRegisterParamSchema() {
        JsonNode schemaJson = ActionParameterSchemaGenerator.generateSchema(getParamType());
        JsonMap definition = OBJECT_MAPPER.convertValue(schemaJson, JsonMap.class);
        ActionSchemaInput paramInput = ActionSchemaInput.newBuilder().actionClass(getClassCanonicalName())
                .paramClass(getParamType().getCanonicalName()).actionKitVersion(version).schema(definition).build();

        RegisterActionProjectionRoot projectionRoot = new RegisterActionProjectionRoot().actionClass();

        RegisterActionGraphQLQuery graphQLQuery = RegisterActionGraphQLQuery.newRequest().actionSchema(paramInput).build();
        GraphQLQueryRequest request = new GraphQLQueryRequest(graphQLQuery, projectionRoot);

        log.trace("Registering schema: {}", schemaJson.toPrettyString());
        domainGatewayService.submit(request);
    }

    private String getClassCanonicalName() {
        return this instanceof Subclass ? this.getClass().getSuperclass().getCanonicalName() : this.getClass().getCanonicalName();
    }

    public P convertToParams(Map<String, Object> params) {
        return OBJECT_MAPPER.convertValue(params, getParamType());
    }
}