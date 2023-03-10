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
package org.deltafi.actionkit.action.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.ResultType;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.exception.ExpectedContentException;
import org.deltafi.actionkit.exception.MissingMetadataException;
import org.deltafi.actionkit.exception.MissingSourceMetadataException;
import org.deltafi.actionkit.exception.StartupException;
import org.deltafi.actionkit.properties.ActionsProperties;
import org.deltafi.actionkit.registration.PluginRegistrar;
import org.deltafi.actionkit.service.HostnameService;
import org.deltafi.common.action.ActionEventQueue;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.ActionInput;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
public class ActionRunner {
    @Autowired
    private ActionEventQueue actionEventQueue;

    @Autowired
    private ActionsProperties actionsProperties;

    @Autowired
    private HostnameService hostnameService;

    @Autowired(required = false)
    private List<Action<?>> actions = Collections.emptyList();

    @Autowired
    BuildProperties buildProperties;

    @Autowired
    PluginRegistrar pluginRegistrar;

    private ExecutorService executor;

    /**
     * Automatically called after construction to initiate polling for inbound actions to be executed
     */
    @PostConstruct
    public void startActions() {
        if (actions.isEmpty()) {
            log.error("No actions found!");
            return;
        }

        pluginRegistrar.register();

        executor = Executors.newFixedThreadPool(actions.size());
        for (Action<?> action : actions) {
            log.info("Starting action: {}", action.getClassCanonicalName());
            executor.submit(() -> listen(action, actionsProperties.getActionPollingInitialDelayMs()));
        }

        markRunning();
    }

    private void listen(Action<?> action, long delayMs) {
        try {
            Thread.sleep(delayMs);
            while (!Thread.currentThread().isInterrupted()) {
                log.trace("{} listening", action.getClassCanonicalName());
                ActionInput actionInput = actionEventQueue.takeAction(action.getClassCanonicalName());
                actionInput.getActionContext().setActionVersion(buildProperties.getVersion());
                actionInput.getActionContext().setHostname(hostnameService.getHostname());
                actionInput.getActionContext().setStartTime(OffsetDateTime.now());
                executeAction(action, actionInput, actionInput.getReturnAddress());
            }
        } catch (Throwable e) {
            log.error("Unexpected exception caught at {} thread execution level: ", action.getClassCanonicalName(), e);
            executor.submit(() -> listen(action, actionsProperties.getActionPollingPeriodMs()));
        }
    }

    private void executeAction(Action<?> action, ActionInput actionInput, String returnAddress) {
        ActionContext context = actionInput.getActionContext();
        log.trace("Running action {} with input {}", action.getClassCanonicalName(), actionInput);
        ResultType result;
        try (MDC.MDCCloseable ignored = MDC.putCloseable("action", context.getName())) {
            result = action.executeAction(actionInput);
            if (Objects.isNull(result)) {
                throw new RuntimeException("Action " + context.getName() + " returned null Result for did " + context.getDid());
            }
        } catch (ExpectedContentException e) {
            result = new ErrorResult(context, "Action received no content", e).logErrorTo(log);
        } catch (MissingSourceMetadataException e) {
            result = new ErrorResult(context, "Missing ingress metadata with key " + e.getKey(), e).logErrorTo(log);
        } catch (MissingMetadataException e) {
            result = new ErrorResult(context, "Missing metadata with key " + e.getKey(), e).logErrorTo(log);
        } catch (Throwable e) {
            result = new ErrorResult(context, "Action execution exception", e).logErrorTo(log);
        }

        try {
            actionEventQueue.putResult(result.toEvent(), returnAddress);
        } catch (Throwable e) {
            log.error("Error sending result to redis for did " + context.getDid(), e);
        }
    }

    private void markRunning() {
        try {
            Files.createFile(Path.of("/running"));
        } catch (IOException e) {
            throw new StartupException("Failed to write running file: " + e.getMessage());
        }
    }
}
