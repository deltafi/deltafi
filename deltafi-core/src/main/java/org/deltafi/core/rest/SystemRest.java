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
package org.deltafi.core.rest;

import lombok.AllArgsConstructor;
import org.deltafi.core.security.NeedsPermission;
import org.deltafi.core.services.*;
import org.deltafi.core.services.SystemService.Status;
import org.deltafi.core.services.SystemService.Versions;
import org.deltafi.core.types.*;
import org.deltafi.core.types.snapshot.Snapshot;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v2")
@AllArgsConstructor
public class SystemRest {

    private final SystemService systemService;
    private final DeltaFilesService deltaFilesService;
    private final BuildProperties buildProperties;
    private final GraphiteQueryService graphiteQueryService;
    private final SystemSnapshotService systemSnapshotService;

    @GetMapping("/status")
    @NeedsPermission.StatusView
    public Status systemStatus() {
        return systemService.systemStatus();
    }

    @GetMapping("/status/report")
    @NeedsPermission.StatusView
    public MemberReport statusReport() {
        Status status = systemService.systemStatus();
        long errorCount = deltaFilesService.countUnacknowledgedErrors();
        var stats = deltaFilesService.deltaFileStats();
        String version = buildProperties.getVersion();

        // Collect system metrics from same source as system metrics page
        List<NodeMetrics> nodes = systemService.nodeAppsAndMetrics();
        Double cpuUsage = getCpuUsage(nodes);
        Double memoryUsage = getMemoryUsage(nodes);
        Double diskUsage = getDiskUsage(nodes);

        return new MemberReport(
                status.status(),
                errorCount,
                stats.getInFlightCount(),
                stats.getWarmQueuedCount(),
                stats.getColdQueuedCount(),
                stats.getPausedCount(),
                cpuUsage,
                memoryUsage,
                diskUsage,
                version
        );
    }

    private Double getCpuUsage(List<NodeMetrics> nodes) {
        long totalUsage = 0;
        long totalLimit = 0;
        for (NodeMetrics node : nodes) {
            Map<String, Long> cpu = node.resources().get("cpu");
            if (cpu != null) {
                Long usage = cpu.get("usage");
                Long limit = cpu.get("limit");
                if (usage != null) totalUsage += usage;
                if (limit != null) totalLimit += limit;
            }
        }
        if (totalLimit > 0) {
            return Math.round((double) totalUsage / totalLimit * 1000.0) / 10.0;
        }
        return null;
    }

    private Double getMemoryUsage(List<NodeMetrics> nodes) {
        long totalUsage = 0;
        long totalLimit = 0;
        for (NodeMetrics node : nodes) {
            Map<String, Long> memory = node.resources().get("memory");
            if (memory != null) {
                Long usage = memory.get("usage");
                Long limit = memory.get("limit");
                if (usage != null) totalUsage += usage;
                if (limit != null) totalLimit += limit;
            }
        }
        if (totalLimit > 0) {
            return Math.round((double) totalUsage / totalLimit * 1000.0) / 10.0;
        }
        return null;
    }

    private Double getDiskUsage(List<NodeMetrics> nodes) {
        long totalUsage = 0;
        long totalLimit = 0;
        for (NodeMetrics node : nodes) {
            Map<String, Long> disk = node.resources().get("disk");
            if (disk != null) {
                Long usage = disk.get("usage");
                Long limit = disk.get("limit");
                if (usage != null) totalUsage += usage;
                if (limit != null) totalLimit += limit;
            }
        }
        if (totalLimit > 0) {
            return Math.round((double) totalUsage / totalLimit * 1000.0) / 10.0;
        }
        return null;
    }

    @PutMapping("/status/{statusCheckId}/pauseDuration")
    @NeedsPermission.StatusPause
    public void pauseStatusCheck(@PathVariable("statusCheckId") String statusCheckId,
            @RequestBody Duration pauseDuration) {
        systemService.pauseStatusCheck(statusCheckId, pauseDuration);
    }

    @DeleteMapping("/status/{statusCheckId}/pauseDuration")
    @NeedsPermission.StatusPause
    public ResponseEntity<Void> resumeStatusCheck(@PathVariable("statusCheckId") String statusCheckId) {
        systemService.resumeStatusCheck(statusCheckId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/versions")
    @NeedsPermission.VersionsView
    public Versions getRunningVersions() {
        return systemService.getRunningVersions();
    }

    /**
     * Query flow metrics for the specified time interval.
     * Returns aggregated ingress, egress, storage, and deleted bytes.
     *
     * @param minutes Time interval in minutes (default 60, max 1440 = 1 day)
     */
    @GetMapping("/metrics/flow")
    @NeedsPermission.StatusView
    public FlowMetrics flowMetrics(@RequestParam(defaultValue = "60") int minutes) {
        // Clamp to valid range: 1 minute to 1 day
        int clampedMinutes = Math.max(1, Math.min(minutes, 1440));
        return graphiteQueryService.queryFlowMetrics(clampedMinutes);
    }

    /**
     * Get the current system configuration as a snapshot without persisting.
     * Used by leader instances to fetch member configurations for comparison.
     */
    @GetMapping("/system/snapshot/current")
    @NeedsPermission.SnapshotRead
    public Snapshot currentSnapshot() {
        return systemSnapshotService.assembleCurrentSnapshot();
    }
}
