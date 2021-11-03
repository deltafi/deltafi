package org.deltafi.actionkit.action;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.graphql.dgs.client.codegen.BaseProjectionNode;
import com.netflix.graphql.dgs.client.codegen.GraphQLQuery;
import com.netflix.graphql.dgs.client.codegen.GraphQLQueryRequest;
import io.quarkus.arc.Subclass;
import io.quarkus.runtime.StartupEvent;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.metrics.ActionMetricsGenerator;
import org.deltafi.actionkit.action.metrics.ActionMetricsLogger;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.actionkit.action.util.ActionParameterSchemaGenerator;
import org.deltafi.actionkit.config.DeltafiConfig;
import org.deltafi.actionkit.exception.DgsPostException;
import org.deltafi.actionkit.service.ActionEventService;
import org.deltafi.actionkit.service.DomainGatewayService;
import org.deltafi.common.metric.Metric;
import org.deltafi.common.trace.DeltafiSpan;
import org.deltafi.common.trace.ZipkinService;
import org.deltafi.core.domain.api.types.ActionInput;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.api.types.JsonMap;
import org.deltafi.core.domain.generated.client.RegisterGenericSchemaGraphQLQuery;
import org.deltafi.core.domain.generated.client.RegisterGenericSchemaProjectionRoot;
import org.deltafi.core.domain.generated.types.GenericActionSchemaInput;
import org.deltafi.core.domain.generated.types.ActionEventType;
import org.deltafi.core.domain.generated.types.SourceInfo;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.annotation.PostConstruct;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public abstract class Action<P extends ActionParameters> implements ActionMetricsGenerator<P> {
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

    private final Class<P> paramType;
    private final ActionEventType actionEventType;
    private final ActionMetricsLogger<P> actionMetricsLogger;

    public Action(Class<P> paramType, ActionEventType actionEventType) {
        this.paramType = paramType;
        this.actionEventType = actionEventType;

        actionMetricsLogger = new ActionMetricsLogger<>(this);
    }

    @SuppressWarnings("unused")
    public void start(@Observes StartupEvent start) {
        // quarkus will prune the actions if this is not included
    }

    public abstract Result<P> execute(DeltaFile deltaFile, P params);

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
            Result<P> result = execute(deltaFile, params);
            if (result != null) {
                actionEventService.submitResult(result);

                if (!(result instanceof ErrorResult)) {
                    actionMetricsLogger.logMetrics(result);
                }

                // TODO: Log metrics on error result???
            }
            zipkinService.markSpanComplete(span);
        } catch (DgsPostException ignored) {
            // do nothing -- the error has already been logged
        } catch (Throwable e) {
            StringWriter stackWriter = new StringWriter();
            e.printStackTrace(new PrintWriter(stackWriter));
            String reason = "Action execution exception: " + "\n" + e.getMessage() + "\n" + stackWriter;
            log.error(params.getName() + " submitting error result for " + deltaFile.getDid() + ": " + reason);
            ErrorResult<P> errorResult = new ErrorResult<>(deltaFile, params, "Action execution exception", e).logErrorTo(log);
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

    protected String getVersion() {
        return version;
    }

    protected String getParamClass() {
        return paramType.getCanonicalName();
    }

    protected JsonMap getDefinition() {
        JsonNode schemaJson = ActionParameterSchemaGenerator.generateSchema(paramType);
        JsonMap definition = OBJECT_MAPPER.convertValue(schemaJson, JsonMap.class);
        log.trace("Registering schema: {}", schemaJson.toPrettyString());
        return definition;
    }

    public GraphQLQuery getRegistrationQuery() {
        GenericActionSchemaInput paramInput = GenericActionSchemaInput.newBuilder()
            .id(getClassCanonicalName())
            .paramClass(getParamClass())
            .actionKitVersion(getVersion())
            .schema(getDefinition())
            .build();
        return RegisterGenericSchemaGraphQLQuery.newRequest().actionSchema(paramInput).build();
    }

    void doRegisterParamSchema() {
        domainGatewayService.submit(new GraphQLQueryRequest(
                    getRegistrationQuery(), getRegistrationProjection()));
    }

    protected BaseProjectionNode getRegistrationProjection() {
        return new RegisterGenericSchemaProjectionRoot(). id();
    }

    protected String getClassCanonicalName() {
        return this instanceof Subclass ? this.getClass().getSuperclass().getCanonicalName() : this.getClass().getCanonicalName();
    }

    public P convertToParams(Map<String, Object> params) {
        return OBJECT_MAPPER.convertValue(params, paramType);
    }

    @Override
    public ActionEventType getActionEventType() {
        return actionEventType;
    }

    @Override
    public Collection<Metric> generateMetrics(Result<P> result) {
        return List.of(Metric.builder().name("files_processed").value(1).build());
    }
}
