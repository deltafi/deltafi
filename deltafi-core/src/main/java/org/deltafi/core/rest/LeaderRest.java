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
package org.deltafi.core.rest;

import lombok.RequiredArgsConstructor;
import org.deltafi.core.generated.types.DeltaFilesFilter;
import org.deltafi.core.security.NeedsPermission;
import org.deltafi.core.services.AggregatedSearchOptionsService;
import org.deltafi.core.services.FederatedErrorService;
import org.deltafi.core.services.FederatedSearchService;
import org.deltafi.core.services.LeaderConfigService;
import org.deltafi.core.services.MemberMonitorService;
import org.deltafi.core.types.FlowMetrics;
import org.deltafi.core.types.leader.AggregatedStats;
import org.deltafi.core.types.leader.ConfigDiff;
import org.deltafi.core.types.leader.LeaderDashboardData;
import org.deltafi.core.types.leader.AggregatedSearchOptions;
import org.deltafi.core.types.leader.FederatedActionRequest;
import org.deltafi.core.types.leader.FederatedActionResponse;
import org.deltafi.core.types.leader.FederatedErrorFilter;
import org.deltafi.core.types.leader.FederatedErrorResponse;
import org.deltafi.core.types.leader.FederatedErrorSummaryResponse;
import org.deltafi.core.types.leader.FederatedSearchResponse;
import org.deltafi.core.types.leader.PluginsResponse;
import org.deltafi.core.types.snapshot.Snapshot;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * ABOUTME: REST controller for leader-member monitoring and federated operations.
 * ABOUTME: Provides endpoints for member status, config management, and fleet-wide error handling.
 */
@RestController
@RequestMapping(value = "/api/v2/leader", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class LeaderRest {
    private final MemberMonitorService memberMonitorService;
    private final LeaderConfigService leaderConfigService;
    private final FederatedSearchService federatedSearchService;
    private final FederatedErrorService federatedErrorService;
    private final AggregatedSearchOptionsService aggregatedSearchOptionsService;

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

    // --- Federated Search Endpoints ---

    /**
     * Execute a federated search across all members.
     * Returns match counts per member for the given filter criteria.
     *
     * @param filter The search filter to apply
     */
    @PostMapping("/search/federated")
    @NeedsPermission.DeltaFileMetadataView
    public FederatedSearchResponse federatedSearch(@RequestBody DeltaFilesFilter filter) {
        return federatedSearchService.search(filter);
    }

    /**
     * Get aggregated search options from all members.
     * Returns combined flow names, topics, and annotation keys.
     */
    @GetMapping("/search/options")
    @NeedsPermission.DeltaFileMetadataView
    public AggregatedSearchOptions getSearchOptions() {
        return aggregatedSearchOptionsService.getAggregatedOptions();
    }

    // --- Federated Error Endpoints ---

    /**
     * Execute a federated error search across all members.
     * Returns error counts per member for the given filter criteria.
     *
     * @param filter The error filter to apply
     */
    @PostMapping("/errors/search")
    @NeedsPermission.DeltaFileMetadataView
    public FederatedErrorResponse federatedErrorSearch(@RequestBody FederatedErrorFilter filter) {
        return federatedErrorService.searchErrors(filter);
    }

    /**
     * Acknowledge errors matching the filter across fleet members.
     *
     * @param request Contains filter criteria, reason, and optional member list
     */
    @PostMapping("/errors/acknowledge")
    @NeedsPermission.DeltaFileAcknowledge
    public FederatedActionResponse federatedAcknowledge(@RequestBody FederatedActionRequest request) {
        return federatedErrorService.acknowledgeErrors(request);
    }

    /**
     * Resume errors matching the filter across fleet members.
     *
     * @param request Contains filter criteria and optional member list
     */
    @PostMapping("/errors/resume")
    @NeedsPermission.DeltaFileResume
    public FederatedActionResponse federatedResume(@RequestBody FederatedActionRequest request) {
        return federatedErrorService.resumeErrors(request);
    }

    /**
     * Replay errors matching the filter across fleet members.
     *
     * @param request Contains filter criteria and optional member list
     */
    @PostMapping("/errors/replay")
    @NeedsPermission.DeltaFileReplay
    public FederatedActionResponse federatedReplay(@RequestBody FederatedActionRequest request) {
        return federatedErrorService.replayErrors(request);
    }

    /**
     * Replay and acknowledge errors matching the filter across fleet members.
     *
     * @param request Contains filter criteria, reason, and optional member list
     */
    @PostMapping("/errors/replay-and-acknowledge")
    @NeedsPermission.DeltaFileReplayAndAcknowledge
    public FederatedActionResponse federatedReplayAndAcknowledge(@RequestBody FederatedActionRequest request) {
        return federatedErrorService.replayAndAcknowledgeErrors(request);
    }

    /**
     * Get error summary by data source across all fleet members.
     *
     * @param filter The error filter to apply
     */
    @PostMapping("/errors/summary/by-data-source")
    @NeedsPermission.DeltaFileMetadataView
    public FederatedErrorSummaryResponse errorSummaryByDataSource(@RequestBody FederatedErrorFilter filter) {
        return federatedErrorService.errorSummaryByDataSource(filter);
    }

    /**
     * Get error summary by error message across all fleet members.
     *
     * @param filter The error filter to apply
     */
    @PostMapping("/errors/summary/by-message")
    @NeedsPermission.DeltaFileMetadataView
    public FederatedErrorSummaryResponse errorSummaryByMessage(@RequestBody FederatedErrorFilter filter) {
        return federatedErrorService.errorSummaryByMessage(filter);
    }
}
