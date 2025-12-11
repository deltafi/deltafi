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

import lombok.RequiredArgsConstructor;
import org.deltafi.core.security.NeedsPermission;
import org.deltafi.core.services.LeaderConfigService;
import org.deltafi.core.services.MemberMonitorService;
import org.deltafi.core.types.FlowMetrics;
import org.deltafi.core.types.leader.AggregatedStats;
import org.deltafi.core.types.leader.ConfigDiff;
import org.deltafi.core.types.leader.LeaderDashboardData;
import org.deltafi.core.types.leader.PluginsResponse;
import org.deltafi.core.types.snapshot.Snapshot;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST controller for leader-member monitoring endpoints.
 * Provides access to member status and metrics for the leader dashboard.
 */
@RestController
@RequestMapping("/api/v2/leader")
@RequiredArgsConstructor
public class LeaderRest {
    private final MemberMonitorService memberMonitorService;
    private final LeaderConfigService leaderConfigService;

    @GetMapping("/members")
    @NeedsPermission.StatusView
    public LeaderDashboardData getMembers() {
        return memberMonitorService.getAllMemberStatuses();
    }

    @GetMapping("/stats")
    @NeedsPermission.StatusView
    public AggregatedStats getStats() {
        return memberMonitorService.getAggregatedStats();
    }

    /**
     * Get flow metrics for all members.
     * Returns a map of member name to their flow metrics for the specified interval.
     *
     * @param minutes Time interval in minutes (default 60, max 1440 = 1 day)
     */
    @GetMapping("/metrics/flow")
    @NeedsPermission.StatusView
    public Map<String, FlowMetrics> getAllFlowMetrics(@RequestParam(defaultValue = "60") int minutes) {
        return memberMonitorService.getAllFlowMetrics(minutes);
    }

    /**
     * Get flow metrics for a specific member.
     *
     * @param memberName The member to query
     * @param minutes Time interval in minutes (default 60, max 1440 = 1 day)
     */
    @GetMapping("/metrics/flow/{memberName}")
    @NeedsPermission.StatusView
    public FlowMetrics getMemberFlowMetrics(
            @PathVariable String memberName,
            @RequestParam(defaultValue = "60") int minutes) {
        return memberMonitorService.getMemberFlowMetrics(memberName, minutes);
    }

    // --- Configuration Management Endpoints ---

    /**
     * Get plugins installed on all members.
     *
     * @return Response containing plugins and unreachable members
     */
    @GetMapping("/config/plugins")
    @NeedsPermission.SnapshotRead
    public PluginsResponse getAllMemberPlugins() {
        return leaderConfigService.getAllMemberPlugins();
    }

    /**
     * Get the current snapshot for a member (or leader).
     *
     * @param memberName The member name, or "Leader" for the leader's snapshot
     */
    @GetMapping("/config/snapshot/{memberName}")
    @NeedsPermission.SnapshotRead
    public Snapshot getMemberSnapshot(@PathVariable String memberName) {
        return leaderConfigService.getMemberSnapshot(memberName);
    }

    /**
     * Compute configuration diff between leader and a member.
     *
     * @param memberName The member to compare against
     */
    @GetMapping("/config/diff/{memberName}")
    @NeedsPermission.SnapshotRead
    public ConfigDiff getConfigDiff(@PathVariable String memberName) {
        return leaderConfigService.computeDiff(memberName);
    }
}
