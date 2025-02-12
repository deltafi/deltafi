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
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.ActionKitException;
import org.deltafi.actionkit.action.ResultType;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.exception.ExpectedContentException;
import org.deltafi.actionkit.exception.MissingMetadataException;
import org.deltafi.actionkit.exception.StartupException;
import org.deltafi.actionkit.properties.ActionsProperties;
import org.deltafi.actionkit.service.ActionEventQueue;
import org.deltafi.actionkit.service.HostnameService;
import org.deltafi.common.content.ActionContentStorageService;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.ActionEvent;
import org.deltafi.common.types.ActionInput;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.ApplicationContext;

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
    @Getter
    private List<Action<?, ?, ?>> singletonActions = Collections.emptyList();

    @Autowired
    BuildProperties buildProperties;

    @Autowired
    ContentStorageService contentStorageService;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ConfigurableListableBeanFactory beanFactory;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Getter
    private final List<Action<?, ?, ?>> allActions = new ArrayList<>();

    @Value("${APP_NAME:null}")
    private String appName;

    /**
     * Automatically called after construction to initiate polling for inbound actions to be executed
     */
    @PostConstruct
    private void initialize() {
        registerAdditionalActions();
        startActions();
    }

    private void registerAdditionalActions() {
        for (Action<?, ?, ?> action : singletonActions) {
            action.setAppName(appName);
            String actionName = action.getClass().getCanonicalName();
            int numThreads = actionsProperties.getActionThreads().getOrDefault(actionName, 1);

            List<Action<?, ?, ?>> instances = new ArrayList<>();
            instances.add(action);

            for (int i = 1; i < numThreads; i++) {
                String beanName = actionName + "#" + i;
                if (!beanFactory.containsBeanDefinition(beanName)) {
                    Action<?, ?, ?> newInstance = applicationContext.getAutowireCapableBeanFactory().createBean(action.getClass());
                    newInstance.setThreadNum(i);
                    newInstance.setAppName(appName);
                    beanFactory.registerSingleton(beanName, newInstance);
                    instances.add(newInstance);
                }
            }
            allActions.addAll(instances);
        }
    }

    private void startActions() {
        for (Action<?, ?, ?> action : allActions) {
            log.info("Starting action: {} (thread {})", action.getClassCanonicalName(), action.getThreadNum());
            executor.submit(() -> listen(action, actionsProperties.getActionPollingInitialDelayMs()));
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
            executor.submit(() -> listen(action, actionsProperties.getActionPollingPeriodMs()));
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
