/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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
import org.deltafi.core.services.PluginService;
import org.deltafi.core.util.TimeFormatter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

@ConditionalOnProperty(value = "schedule.maintenance", havingValue = "true", matchIfMissing = true)
@Service
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class PluginRestartScheduler {
    private final CoreEventQueue coreEventQueue;
    private final DeployerService deployerService;
    private final PluginService pluginService;
    private final DeltaFiPropertiesService propertiesService;

    @Scheduled(fixedRate = 15, timeUnit = TimeUnit.SECONDS)
    public void restartPluginsWithStuckAction() {
        Duration actionExecutionTimeout = propertiesService.getDeltaFiProperties().getActionExecutionTimeout();

        if (actionExecutionTimeout == null || actionExecutionTimeout.isZero()) {
            return;
        }

        Set<String> plugins = new HashSet<>();
        Set<String> restartedActionExecutions = new HashSet<>();
        for (ActionExecution actionExecution : coreEventQueue.getLongRunningTasks()) {
            if (actionExecution.exceedsDuration(actionExecutionTimeout)) {
                String plugin = pluginService.getPluginWithAction(actionExecution.clazz());
                if (plugin != null) {
                    log.error("Restarting plugin: {} with stuck DeltaFile: {} in action class: {} ({}) that has been running for {}", plugin, actionExecution.did(), actionExecution.clazz(), actionExecution.action(), TimeFormatter.humanReadableTimeSince(actionExecution.startTime()));
                    restartedActionExecutions.add(actionExecution.key());
                    plugins.add(plugin);
                }
            }
        }

        deployerService.restartPlugins(plugins);
        coreEventQueue.removeLongRunningTask(restartedActionExecutions);
    }
}
