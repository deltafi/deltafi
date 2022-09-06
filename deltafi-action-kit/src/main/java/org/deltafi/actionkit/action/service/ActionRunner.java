/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.properties.ActionsProperties;
import org.deltafi.actionkit.properties.DeltaFiProperties;
import org.deltafi.actionkit.service.HostnameService;
import org.deltafi.common.action.ActionEventQueue;
import org.deltafi.common.metrics.Metric;
import org.deltafi.common.metrics.MetricRepository;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.ActionInput;
import org.deltafi.common.types.ActionType;
import org.deltafi.common.types.DeltaFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Base class for all DeltaFi Actions.  No action should directly extend this class, but should use
 * specialized classes in the action taxonomy (LoadAction, EgressAction, etc.)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ActionRunner {
    @Autowired
    private ActionEventQueue actionEventQueue;

    @Autowired
    private ActionsProperties actionsProperties;

    @Autowired
    private HostnameService hostnameService;

    @Autowired
    private DeltaFiProperties deltaFiProperties;

    @Autowired
    private List<Action<?>> actions;

    @Autowired
    BuildProperties buildProperties;

    @Autowired
    MetricRepository metricService;

    private ExecutorService executor;

    /**
     * Automatically called after construction to initiate polling for inbound actions to be executed
     */
    @PostConstruct
    public void startActions() {
        if (actions.isEmpty()) {
            log.error("No actions found!");
        }
        executor = Executors.newFixedThreadPool(actions.size());
        for (Action<?> action : actions) {
            log.info("Starting action: {}", getFeedString(action));
            executor.submit(() -> listen(action, actionsProperties.getActionPollingInitialDelayMs()));
        }
    }

    private void listen(Action<?> action, long delayMs) {
        try {
            Thread.sleep(delayMs);
            while (!Thread.currentThread().isInterrupted()) {
                log.trace(getFeedString(action) + "listening");
                ActionInput actionInput = actionEventQueue.takeAction(getFeedString(action));
                OffsetDateTime now = OffsetDateTime.now();
                ActionContext context = actionInput.getActionContext();
                context.setActionVersion(buildProperties.getVersion());
                context.setHostname(hostnameService.getHostname());
                context.setStartTime(now);
                context.setSystemName(deltaFiProperties.getSystemName());

                log.trace("Running action {} with input {}", getFeedString(action), actionInput);
                DeltaFile deltaFile = actionInput.getDeltaFile();

                executeAction(action, deltaFile, context, actionInput.getActionParams());
            }
        } catch (Throwable e) {
            log.error("Unexpected exception caught at " + getFeedString(action) + " thread execution level: " + e.getMessage());
            executor.submit(() -> listen(action, actionsProperties.getActionPollingPeriodMs()));
        }
    }

    private void executeAction(Action<?> action, DeltaFile deltaFile, ActionContext context, Map<String, Object> params) {
        try {
            Result result = action.executeAction(deltaFile, context, params);
            if (Objects.isNull(result)) {
                throw new RuntimeException("Action " + context.getName() + " returned null Result for did " + context.getDid());
            }

            actionEventQueue.putResult(result.toEvent());
            postMetrics(result, action.getActionType());
        } catch (Throwable e) {
            ErrorResult errorResult = new ErrorResult(context, "Action execution exception", e).logErrorTo(log);
            postMetrics(errorResult, action.getActionType());
        }
    }

    private void postMetrics(Result result, ActionType actionType) {
        String ingressFlow = result.getContext().getIngressFlow();
        String egressFlow = result.getContext().getEgressFlow();
        String source = result.getContext().getName();
        for (Metric metric: result.getMetrics()) {
            metric.addTag("action", actionType.name().toLowerCase())
                    .addTag("source", source);
            if (ingressFlow != null) metric.addTag("ingressFlow", ingressFlow);
            if (egressFlow != null) metric.addTag("egressFlow", egressFlow);

            metricService.increment(metric);
        }
    }

    private String getFeedString(Action<?> action) {
        return action.getClass().getCanonicalName();
    }
}
