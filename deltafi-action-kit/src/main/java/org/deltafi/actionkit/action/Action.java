package org.deltafi.actionkit.action;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.graphql.dgs.client.codegen.BaseProjectionNode;
import io.quarkus.arc.Subclass;
import io.quarkus.runtime.StartupEvent;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.actionkit.config.DeltafiConfig;
import org.deltafi.actionkit.exception.DgsPostException;
import org.deltafi.actionkit.service.DomainGatewayService;
import org.deltafi.actionkit.service.RedisService;
import org.deltafi.common.trace.DeltafiSpan;
import org.deltafi.common.trace.ZipkinService;
import org.deltafi.dgs.api.types.ActionInput;
import org.deltafi.dgs.api.types.DeltaFile;
import org.deltafi.dgs.generated.types.SourceInfo;

import javax.annotation.PostConstruct;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public abstract class Action<P extends ActionParameters> {

    private static final ObjectMapper mapper = new ObjectMapper()
        .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);

    @Inject
    DomainGatewayService domainGatewayService;

    @Inject
    ZipkinService zipkinService;

    @Inject
    RedisService redisService;

    @Inject
    DeltafiConfig config;

    public void start(@Observes StartupEvent start) {
        // quarkus will prune the actions if this is not included
    }

    public abstract Result execute(DeltaFile deltaFile, P params);

    public abstract Class<P> getParamType();

    @PostConstruct
    void startAction() {
        final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleWithFixedDelay(this::startListening,
                config.action_polling_start_delay_ms,
                config.action_polling_frequency_ms,
                TimeUnit.MILLISECONDS);
    }

    private void startListening() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                ActionInput actionInput = redisService.actionFeed(getFeedString());

                log.trace("Running action with input {}", actionInput);
                DeltaFile deltaFile = actionInput.getDeltaFile();
                P params = convertToParams(actionInput.getActionParams());

                SourceInfo sourceInfo = deltaFile.getSourceInfo();
                DeltafiSpan span = zipkinService.createChildSpan(deltaFile.getDid(), params.getName(), sourceInfo.getFilename(), sourceInfo.getFlow());
                deltaFile = domainGatewayService.federate(deltaFile, getDomainProjections(), getEnrichmentProjections());

                executeAction(deltaFile, params, span);
            }
        } catch (Throwable e) {
            log.error("Tell Jeremy he really, really needs to fix this exception: " + e.getMessage());
        }
    }

    private void executeAction(DeltaFile deltaFile, P params, DeltafiSpan span) {
        try {
            Result result = execute(deltaFile, params);
            if (result != null) {
                if (result.resultType().equals(Result.ResultType.GRAPHQL)) {
                    domainGatewayService.submit(result);
                } else {
                    redisService.submit(result);
                }

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
            domainGatewayService.submit(err);
        }
    }

    private String getFeedString() {
        if (this instanceof Subclass) {
            return this.getClass().getSuperclass().getCanonicalName();
        }

        return this.getClass().getCanonicalName();
    }

    public Map<String, BaseProjectionNode> getDomainProjections() {
        return Collections.emptyMap();
    }

    public Map<String, BaseProjectionNode> getEnrichmentProjections() {
        return Collections.emptyMap();
    }

    public P convertToParams(Map<String, Object> params) {
        return mapper.convertValue(params, getParamType());
    }

}
