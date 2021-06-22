package org.deltafi.service;

import io.quarkus.runtime.Startup;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.action.Action;
import org.deltafi.action.Result;
import org.deltafi.action.error.ErrorResult;
import org.deltafi.common.trace.DeltafiSpan;
import org.deltafi.common.trace.ZipkinService;
import org.deltafi.config.DeltafiConfig;
import org.deltafi.dgs.generated.types.SourceInfo;
import org.deltafi.exception.DgsPostException;
import org.deltafi.types.DeltaFile;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
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

    final ArrayList<ActionRunner> actions = new ArrayList<>();

    @PostConstruct
    void init() {
        deltafiConfig.actions.forEach(action -> {
            try {
                Action a = (Action) loadClass(action.type).getDeclaredConstructor().newInstance();
                a.init(action);
                actions.add(new ActionRunner(a, domainGatewayService, deltafiConfig, zipkinService));
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
        // This limit might be manipulated once we build a thread pool here. Until then, set to null to use the system default.
        final Integer limit = null;
        final DomainGatewayService domainGatewayService;
        final ScheduledFuture<?> pollHandle;
        final ZipkinService zipkinService;

        final Runnable poll = new Runnable() {
            @Override
            public void run() {
                try {
                    List<DeltaFile> deltaFiles = domainGatewayService.getDeltaFilesFor(action, limit);
                    while (!deltaFiles.isEmpty()) {
                        for (DeltaFile deltaFile : deltaFiles) {
                            try {
                                SourceInfo sourceInfo = deltaFile.getSourceInfo();
                                DeltafiSpan span = zipkinService.createChildSpan(deltaFile.getDid(), action.name(), sourceInfo.getFilename(), sourceInfo.getFlow());
                                Result result = action.execute(deltaFile);
                                if (result != null) {
                                    domainGatewayService.submit(result);
                                }
                                zipkinService.finishAndSendSpan(span);

                            } catch (DgsPostException ignored) {
                                // do nothing -- the error has already been logged
                                // proceed with the next deltafile
                            } catch (Throwable e) {
//                                String reason = "Action execution exception: " + "\n" + ExceptionUtils.getStackTrace(e);
                                String reason = "Action execution exception: " + e;
                                log.error(action.name() + " submitting error result for " + deltaFile.getDid() + ": " + reason);
                                ErrorResult err = new ErrorResult(action, deltaFile.getDid(), reason);
                                domainGatewayService.submit(err);
                            }
                        }
                        deltaFiles = domainGatewayService.getDeltaFilesFor(action, limit);
                    }
                } catch (Throwable e) {
                    log.error("Tell Jeremy he really, really needs to fix this exception: " + e.getMessage());
                }
            }
        };

        ActionRunner(@SuppressWarnings("CdiInjectionPointsInspection") Action action, DomainGatewayService domainGatewayService, DeltafiConfig config, ZipkinService zipkinService) {

            this.action = action;
            this.domainGatewayService = domainGatewayService;
            this.zipkinService = zipkinService;
            pollHandle = scheduler.scheduleWithFixedDelay(poll,
                    config.action_polling_start_delay_ms,
                    config.action_polling_frequency_ms,
                    TimeUnit.MILLISECONDS);
        }
    }

}