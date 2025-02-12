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
package org.deltafi.core.schedulers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.types.ActionExecution;
import org.deltafi.core.plugin.deployer.DeployerService;
import org.deltafi.core.services.CoreEventQueue;
import org.deltafi.core.services.DeltaFiPropertiesService;
import org.deltafi.core.services.EventService;
import org.deltafi.core.services.PluginService;
import org.deltafi.core.types.Event;
import org.deltafi.core.util.TimeFormatter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@ConditionalOnProperty(value = "schedule.maintenance", havingValue = "true", matchIfMissing = true)
@Service
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class PluginRestartScheduler {
    private static final String EVENT_SOURCE = "core";

    private final CoreEventQueue coreEventQueue;
    private final DeployerService deployerService;
    private final EventService eventService;
    private final PluginService pluginService;
    private final DeltaFiPropertiesService propertiesService;

    @Scheduled(fixedRate = 15, timeUnit = TimeUnit.SECONDS)
    public void restartPluginsWithStuckAction() {
        Duration actionExecutionTimeout = propertiesService.getDeltaFiProperties().getActionExecutionTimeout();

        if (actionExecutionTimeout == null || actionExecutionTimeout.isZero()) {
            return;
        }

        Set<String> plugins = new HashSet<>();
        Set<String> apps = new HashSet<>();
        Set<String> restartedActionExecutions = new HashSet<>();
        for (ActionExecution actionExecution : coreEventQueue.getLongRunningTasks()) {
            if (actionExecution.exceedsDuration(actionExecutionTimeout)) {
                String plugin = pluginService.getPluginWithAction(actionExecution.clazz());
                String podOrContainer = actionExecution.appName();
                if (plugin != null || actionExecution.appName() != null) {
                    createEvent(plugin, actionExecution);
                    restartedActionExecutions.add(actionExecution.key());
                    // only keep the more specific resource to restart
                    if (podOrContainer != null) {
                        apps.add(podOrContainer);
                    } else {
                        plugins.add(plugin);
                    }
                }
            }
        }

        deployerService.restartPlugins(plugins);
        deployerService.restartApps(apps);
        coreEventQueue.removeLongRunningTask(restartedActionExecutions);
    }

    private void createEvent(String plugin, ActionExecution actionExecution) {
        String summary = "Restarting ";
        if (plugin != null) {
            summary += "plugin: " + plugin;
        }

        if (actionExecution.appName() != null) {
            summary += plugin != null ? " (" + actionExecution.appName() + ")" : actionExecution.appName();
        }

        String content = summary +  " with stuck DeltaFile '%s' in action class '%s' in action '%s' that has been running for %s"
                .formatted(actionExecution.did(), actionExecution.clazz(), actionExecution.action(), TimeFormatter.humanReadableTimeSince(actionExecution.startTime()));

        eventService.createEvent(Event.builder()
                .timestamp(OffsetDateTime.now())
                .summary(summary)
                .content(content)
                .severity(Event.Severity.ERROR)
                .notification(true)
                .source(EVENT_SOURCE).build());
        log.error(content);
    }
}
