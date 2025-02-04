/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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
import org.deltafi.actionkit.action.ActionKitException;
import org.deltafi.actionkit.action.ResultType;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.exception.ExpectedContentException;
import org.deltafi.actionkit.exception.MissingMetadataException;
import org.deltafi.actionkit.exception.StartupException;
import org.deltafi.actionkit.properties.ActionsProperties;
import org.deltafi.actionkit.registration.PluginRegistrar;
import org.deltafi.actionkit.service.ActionEventQueue;
import org.deltafi.actionkit.service.HostnameService;
import org.deltafi.common.content.ActionContentStorageService;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.ActionEvent;
import org.deltafi.common.types.ActionInput;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private List<Action<?, ?, ?>> actions = Collections.emptyList();

    @Autowired
    BuildProperties buildProperties;

    @Autowired
    PluginRegistrar pluginRegistrar;

    @Autowired
    ContentStorageService contentStorageService;

    private final Map<String, ExecutorService> executors = new HashMap<>();

    /**
     * Automatically called after construction to initiate polling for inbound actions to be executed
     */
    @PostConstruct
    public void startActions() {
        if (actions.isEmpty()) {
            log.warn("No actions found! This may be a flow-only plugin.");
        }

        pluginRegistrar.register();

        for (Action<?, ?, ?> action : actions) {
            String actionName = action.getClassCanonicalName();
            int numThreads = actionsProperties.getActionThreads().getOrDefault(actionName, 1);
            ExecutorService executor = Executors.newFixedThreadPool(numThreads);
            executors.put(actionName, executor);

            for (int i = 0; i < numThreads; i++) {
                log.info("Starting action: {}{}", actionName, numThreads > 1 ? " (thread %s)".formatted(i + 1) : "");
                executor.submit(() -> listen(action, actionsProperties.getActionPollingInitialDelayMs()));
            }
        }

        markRunning();
    }

    private void listen(Action<?, ?, ?> action, long delayMs) {
        try {
            Thread.sleep(delayMs);
            ActionContentStorageService actionContentStorageService = new ActionContentStorageService(contentStorageService);
            while (!Thread.currentThread().isInterrupted()) {
                log.trace("{} listening", action.getClassCanonicalName());
                ActionInput actionInput = actionEventQueue.takeAction(action.getClassCanonicalName());
                actionInput.getActionContext().setActionVersion(buildProperties.getVersion());
                actionInput.getActionContext().setHostname(hostnameService.getHostname());
                actionInput.getActionContext().setStartTime(OffsetDateTime.now());
                actionInput.getActionContext().setContentStorageService(actionContentStorageService);
                executeAction(action, actionInput, actionInput.getReturnAddress());
                actionContentStorageService.clear();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Throwable e) {
            log.error("Unexpected exception caught at {} thread execution level: ", action.getClassCanonicalName(), e);
            ExecutorService actionExecutor = executors.get(action.getClassCanonicalName());
            actionExecutor.submit(() -> listen(action, actionsProperties.getActionPollingPeriodMs()));
        }
        log.warn("Shutting down action thread: {}", action.getClassCanonicalName());
    }

    void executeAction(Action<?, ?, ?> action, ActionInput actionInput, String returnAddress) {
        ActionContext context = actionInput.getActionContext();
        log.trace("Running action {} with input {}", action.getClassCanonicalName(), actionInput);
        ResultType result;
        try (MDC.MDCCloseable ignored = MDC.putCloseable("action", context.getActionName())) {
            result = action.executeAction(actionInput);

            if (result == null) {
                throw new RuntimeException("Action " + context.getActionName() + " returned null Result for did " + context.getDid());
            }
        } catch (ExpectedContentException e) {
            result = new ErrorResult(context, "Action received no content", e).logErrorTo(log);
        } catch (MissingMetadataException e) {
            result = new ErrorResult(context, "Missing metadata with key " + e.getKey(), e).logErrorTo(log);
        } catch (ActionKitException e) {
            result = new ErrorResult(context, e.getMessage(), e).logErrorTo(log);
        } catch (Throwable e) {
            result = new ErrorResult(context, "Action execution exception", e).logErrorTo(log);
        }
        ActionEvent event = result.toEvent();
        orphanContentCheck(context, event);
        action.clearActionExecution();

        try {
            actionEventQueue.putResult(event, returnAddress);
        } catch (Throwable e) {
            log.error("Error sending result to valkey for did {}", context.getDid(), e);
        }
    }

    private void orphanContentCheck(ActionContext context, ActionEvent event) {
        int count = context.getContentStorageService().deleteUnusedContent(event);
        if (count > 0) {
            log.warn("Deleted {} unused content entries for did {} due to a {} event by {}", count, context.getDid(), event.getType(), event.getActionName());
        }
    }

    private void markRunning() {
        try {
            Files.createFile(Path.of("/tmp/running"));
        } catch (FileAlreadyExistsException e) {
            log.warn("Using existing running file");
        } catch (IOException e) {
            throw new StartupException("Failed to write running file: " + e.getMessage());
        }
    }
}
