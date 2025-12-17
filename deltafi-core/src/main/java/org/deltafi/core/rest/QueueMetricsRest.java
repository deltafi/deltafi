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
// ABOUTME: REST endpoint for detailed queue metrics with per-flow breakdown.
// ABOUTME: Exposes warm queue (from Valkey) and cold queue (from trigger-maintained table) metrics.
package org.deltafi.core.rest;

import lombok.AllArgsConstructor;
import org.deltafi.common.types.ActionExecution;
import org.deltafi.core.repo.DeltaFileFlowRepoCustom.ColdQueueMetrics;
import org.deltafi.core.security.NeedsPermission;
import org.deltafi.core.services.PluginService;
import org.deltafi.core.services.QueueManagementService;
import org.deltafi.core.services.QueueManagementService.WarmQueueMetrics;
import org.deltafi.core.services.UnifiedFlowService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v2/metrics/queues")
@AllArgsConstructor
public class QueueMetricsRest {

    private static final long LONG_RUNNING_THRESHOLD_MS = 30_000;

    private final QueueManagementService queueManagementService;
    private final PluginService pluginService;
    private final UnifiedFlowService unifiedFlowService;

    /**
     * Get detailed queue metrics with per-flow breakdown.
     * Includes both warm queue (Valkey) and cold queue (PostgreSQL) metrics.
     * Results are cached for 5 seconds to prevent overwhelming the backend.
     */
    @NeedsPermission.MetricsView
    @GetMapping("/detailed")
    public DetailedQueueMetrics getDetailedQueueMetrics() {
        List<WarmQueueMetrics> warmQueues = queueManagementService.getDetailedWarmQueueMetrics();
        List<ColdQueueMetrics> coldQueues = queueManagementService.getCachedColdQueueCounts();
        return new DetailedQueueMetrics(warmQueues, coldQueues, OffsetDateTime.now());
    }

    public record DetailedQueueMetrics(
            List<WarmQueueMetrics> warmQueues,
            List<ColdQueueMetrics> coldQueues,
            OffsetDateTime timestamp) {}

    /**
     * Get a mapping of action class names to their plugin artifact IDs.
     */
    @NeedsPermission.MetricsView
    @GetMapping("/action-plugins")
    public Map<String, String> getActionPluginMapping() {
        return unifiedFlowService.allActionConfigurations().stream()
                .map(action -> action.getType())
                .distinct()
                .collect(Collectors.toMap(
                        actionClass -> actionClass,
                        actionClass -> {
                            String plugin = pluginService.getPluginWithAction(actionClass);
                            return plugin != null ? plugin : "unknown";
                        },
                        (a, b) -> a));
    }

    /**
     * Get currently running tasks (actions actively executing with valid heartbeats).
     * Results are cached for 5 seconds to prevent overwhelming the backend.
     */
    @NeedsPermission.MetricsView
    @GetMapping("/running")
    public RunningTasksResponse getRunningTasks() {
        List<ActionExecution> tasks = queueManagementService.getCachedRunningTasks();
        OffsetDateTime now = OffsetDateTime.now();

        List<RunningTask> taskDetails = tasks.stream()
                .map(task -> new RunningTask(
                        task.clazz(),
                        task.action(),
                        task.did(),
                        task.startTime(),
                        Duration.between(task.startTime(), now).toMillis(),
                        task.appName()))
                .toList();

        return new RunningTasksResponse(taskDetails, LONG_RUNNING_THRESHOLD_MS);
    }

    public record RunningTasksResponse(List<RunningTask> tasks, long heartbeatThresholdMs) {}

    public record RunningTask(
            String actionClass,
            String actionName,
            UUID did,
            OffsetDateTime startTime,
            long runningForMs,
            String appName) {}
}
