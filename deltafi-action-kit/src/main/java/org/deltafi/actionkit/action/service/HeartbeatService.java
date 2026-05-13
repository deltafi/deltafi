/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
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
// ABOUTME: Scheduled service that sends heartbeats and tracks long-running actions.
// ABOUTME: Periodically reports action liveness and execution state to Valkey.
package org.deltafi.actionkit.action.service;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.service.ActionEventQueue;
import org.deltafi.common.types.ActionExecution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Scheduled service that sends action heartbeats and tracks long-running actions in Valkey
 */
@Slf4j
public class HeartbeatService implements AutoCloseable {

    private static final long HEARTBEAT_INTERVAL_MS = 10_000;
    private static final long LONG_RUNNING_INTERVAL_MS = 5_000;

    private final ActionEventQueue actionEventQueue;
    private final ActionRunner actionRunner;
    private final ScheduledExecutorService scheduler;

    private List<ActionExecution> runningActions = Collections.emptyList();

    public HeartbeatService(ActionEventQueue actionEventQueue, ActionRunner actionRunner) {
        this.actionEventQueue = actionEventQueue;
        this.actionRunner = actionRunner;
        this.scheduler = Executors.newScheduledThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "action-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Begin the periodic heartbeat and long-running-task passes. Idempotent within a single instance. */
    public void start() {
        scheduler.scheduleAtFixedRate(this::setHeartbeat,
                HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(this::recordRunningTasks,
                LONG_RUNNING_INTERVAL_MS, LONG_RUNNING_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }

    void setHeartbeat() {
        try {
            for (Action<?, ?, ?> action : actionRunner.getAllActions()) {
                actionEventQueue.setHeartbeat(action.getClassCanonicalName());
            }
        } catch (Exception e) {
            log.warn("Failed to set heartbeats", e);
        }
    }

    void recordRunningTasks() {
        try {
            List<ActionExecution> oldList = new ArrayList<>(runningActions);
            runningActions = new ArrayList<>();

            actionRunner.getAllActions().stream()
                    .map(Action::getActionExecution)
                    .filter(Objects::nonNull)
                    .forEach(actionExecution -> {
                        runningActions.add(actionExecution);
                        oldList.remove(actionExecution);
                        actionEventQueue.recordLongRunningTask(actionExecution);
                    });

            for (ActionExecution actionExecution : oldList) {
                actionEventQueue.removeLongRunningTask(actionExecution);
            }
        } catch (Exception e) {
            log.warn("Pushing long-running-task to valkey failed", e);
        }
    }
}
