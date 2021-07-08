package org.deltafi.actionkit.service;

import com.netflix.graphql.dgs.client.codegen.BaseProjectionNode;
import io.quarkus.runtime.Startup;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.common.trace.DeltafiSpan;
import org.deltafi.common.trace.ZipkinService;
import org.deltafi.actionkit.config.DeltafiConfig;
import org.deltafi.dgs.generated.types.SourceInfo;
import org.deltafi.actionkit.exception.DgsPostException;
import org.deltafi.actionkit.types.DeltaFile;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Singleton
@Slf4j
@Startup
public class ActionService {

    @Inject
    DeltafiConfig deltafiConfig;

    @Inject
    DomainGatewayService domainGatewayService;

    @Inject
    ZipkinService zipkinService;

    @Inject
    RedisService redisService;

    final ArrayList<ActionRunner> actions = new ArrayList<>();

    @PostConstruct
    void init() {
        deltafiConfig.actions.forEach(action -> {
            try {
                Action a = (Action) loadClass(action.type).getDeclaredConstructor().newInstance();
                a.init(action);
                actions.add(new ActionRunner(a, domainGatewayService, deltafiConfig, zipkinService, redisService));
            } catch (InstantiationException e) {
                log.error("Unable to instantiate: " + action.type);
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                log.error("Unable to access: " + action.type);
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                log.error("Class not found: " + action.type);
                e.printStackTrace();
            } catch (InvocationTargetException | NoSuchMethodException e) {
                log.error("Class constructor not found: " + action.type);
                e.printStackTrace();
            }
            log.info("Added " + action.name);
        });
    }

    public static Class<?> loadClass(String className) throws ClassNotFoundException {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            return Thread.currentThread().getContextClassLoader().loadClass(className);
        }
    }

    static class ActionRunner {
        private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        final Action action;
        final DomainGatewayService domainGatewayService;
        final ZipkinService zipkinService;
        final RedisService redisService;
        final ScheduledFuture<?> pollHandle;
        final Map<String, BaseProjectionNode> domainProjections;
        final Map<String, BaseProjectionNode> enrichmentProjections;

        final Runnable poll = new Runnable() {
            @Override
            public void run() {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        DeltaFile deltaFile = redisService.actionFeed(action.name());
                        SourceInfo sourceInfo = deltaFile.getSourceInfo();
                        DeltafiSpan span = zipkinService.createChildSpan(deltaFile.getDid(), action.name(), sourceInfo.getFilename(), sourceInfo.getFlow());
                        deltaFile = domainGatewayService.federate(deltaFile, domainProjections, enrichmentProjections);

                        try {
                            Result result = action.execute(deltaFile);
                            if (result != null) {
                                domainGatewayService.submit(result);
                            }
                            zipkinService.markSpanComplete(span);
                        } catch (DgsPostException ignored) {
                            // do nothing -- the error has already been logged
                        } catch (Throwable e) {
                            StringWriter stackWriter = new StringWriter();
                            e.printStackTrace(new PrintWriter(stackWriter));
                            String reason = "Action execution exception: " + "\n" + e.getMessage() + "\n" + stackWriter;
                            log.error(action.name() + " submitting error result for " + deltaFile.getDid() + ": " + reason);
                            ErrorResult err = new ErrorResult(action, deltaFile.getDid(), reason);
                            domainGatewayService.submit(err);
                        }
                    }
                } catch (Throwable e) {
                    log.error("Tell Jeremy he really, really needs to fix this exception: " + e.getMessage());
                }
            }
        };

        ActionRunner(Action action, DomainGatewayService domainGatewayService, DeltafiConfig config, ZipkinService zipkinService, RedisService redisService) {
            this.action = action;
            this.domainGatewayService = domainGatewayService;
            this.zipkinService = zipkinService;
            this.redisService = redisService;
            this.domainProjections = action.getDomainProjections();
            this.enrichmentProjections = action.getEnrichmentProjections();
            pollHandle = scheduler.scheduleWithFixedDelay(poll,
                    config.action_polling_start_delay_ms,
                    config.action_polling_frequency_ms,
                    TimeUnit.MILLISECONDS);
        }
    }
}
