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
import org.deltafi.actionkit.action.ActionKitException;
import org.deltafi.actionkit.action.ResultType;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.exception.ExpectedContentException;
import org.deltafi.actionkit.exception.MissingMetadataException;
import org.deltafi.actionkit.exception.StartupException;
import org.deltafi.actionkit.properties.ActionsProperties;
import org.deltafi.actionkit.registration.PluginRegistrar;
import org.deltafi.actionkit.service.HostnameService;
import org.deltafi.common.action.ActionEventQueue;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.ActionInput;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.*;
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

        for (Action<?> action : actions) {
            String actionName = action.getClassCanonicalName();
            int numThreads = actionsProperties.getActionThreads().getOrDefault(actionName, 1);
            ExecutorService executor = Executors.newFixedThreadPool(numThreads);
            executors.put(actionName, executor);

            log.info("Starting action: {}", actionName);
            for (int i = 0; i < numThreads; i++) {
                executor.submit(() -> listen(action, actionsProperties.getActionPollingInitialDelayMs()));
            }
        }

        markRunning();
    }

    private void listen(Action<?> action, long delayMs) {
        try {
            Thread.sleep(delayMs);
            while (!Thread.currentThread().isInterrupted()) {
                log.trace("{} listening", action.getClassCanonicalName());
                ActionInput actionInput = actionEventQueue.takeAction(action.getClassCanonicalName());
                // To maintain compatibility with legacy actions, flow and name were combined in the name field of the
                // ActionContext sent to the action in the ActionInput. Put them in their own fields.
                String[] actionNameParts = actionInput.getActionContext().getName().split("\\.");
                actionInput.getActionContext().setFlow(actionNameParts[0]);
                actionInput.getActionContext().setName(actionNameParts[1]);
                actionInput.getActionContext().setActionVersion(buildProperties.getVersion());
                actionInput.getActionContext().setHostname(hostnameService.getHostname());
                actionInput.getActionContext().setStartTime(OffsetDateTime.now());
                actionInput.getActionContext().setContentStorageService(contentStorageService);
                executeAction(action, actionInput, actionInput.getReturnAddress());
            }
        } catch (Throwable e) {
            log.error("Unexpected exception caught at {} thread execution level: ", action.getClassCanonicalName(), e);
            ExecutorService actionExecutor = executors.get(action.getClassCanonicalName());
            actionExecutor.submit(() -> listen(action, actionsProperties.getActionPollingPeriodMs()));
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
        } catch (MissingMetadataException e) {
            result = new ErrorResult(context, "Missing metadata with key " + e.getKey(), e).logErrorTo(log);
        } catch (ActionKitException e) {
            result = new ErrorResult(context, e.getMessage(), e).logErrorTo(log);
        }
        catch (Throwable e) {
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
            Files.createFile(Path.of("/tmp/running"));
        } catch (FileAlreadyExistsException e) {
            log.warn("Using existing running file");
        } catch (IOException e) {
            throw new StartupException("Failed to write running file: " + e.getMessage());
        }
    }
}
