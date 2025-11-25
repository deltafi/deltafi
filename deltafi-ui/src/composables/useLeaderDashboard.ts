/*
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

import { ref, computed } from "vue";
import useApi from "./useApi";

export interface MemberStatus {
  memberName: string;
  url: string;
  tags: string[];
  isLeader: boolean;
  status: MonitorResult | null;
  errorCount: number | null;
  inFlightCount: number | null;
  warmQueuedCount: number | null;
  coldQueuedCount: number | null;
  pausedCount: number | null;
  lastUpdated: string;
  connectionState: "CONNECTED" | "UNREACHABLE" | "STALE";
  connectionError: string | null;
  version: string | null;
  cpuUsage: number | null;
  memoryUsage: number | null;
  diskUsage: number | null;
}

export interface MonitorResult {
  code: number;
  color: string;
  state: string;
  checks: CheckResult[];
  timestamp: string;
}

export interface CheckResult {
  description: string;
  code: number;
  message: string;
  timestamp: string;
}

export interface LeaderDashboardData {
  members: MemberStatus[];
  lastRefresh: string;
}

export interface AggregatedStats {
  totalInFlight: number;
  totalErrors: number;
  totalWarmQueue: number;
  totalColdQueue: number;
  totalPaused: number;
  memberCount: number;
  healthyCount: number;
  unhealthyCount: number;
}

export interface FlowMetrics {
  ingressByDataSource: Record<string, number>;
  egressByDataSink: Record<string, number>;
  storageByService: Record<string, number>;
  deletedByPolicy: Record<string, number>;
}

export default function useLeaderDashboard() {
  const { response, get, loading } = useApi();
  const members = ref<MemberStatus[]>([]);
  const lastRefresh = ref<string>("");
  const filterText = ref<string>("");
  const selectedTags = ref<string[]>([]);
  const aggregatedStats = ref<AggregatedStats | null>(null);
  const flowMetrics = ref<Record<string, FlowMetrics>>({});
  const flowMetricsInterval = ref<number>(60);
  const flowMetricsLoading = ref<boolean>(false);

  const fetchMembers = async () => {
    await get("leader/members");
    if (response.value) {
      const data = response.value as LeaderDashboardData;
      members.value = data.members;
      lastRefresh.value = data.lastRefresh;
    }
  };

  const fetchStats = async () => {
    await get("leader/stats");
    if (response.value) {
      aggregatedStats.value = response.value as AggregatedStats;
    }
  };

  const fetchFlowMetrics = async (minutes?: number) => {
    const interval = minutes ?? flowMetricsInterval.value;
    flowMetricsLoading.value = true;
    try {
      await get(`leader/metrics/flow?minutes=${interval}`);
      if (response.value) {
        flowMetrics.value = response.value as Record<string, FlowMetrics>;
      }
    } finally {
      flowMetricsLoading.value = false;
    }
  };

  const fetchAll = async () => {
    await Promise.all([fetchMembers(), fetchStats()]);
  };

  const filteredMembers = computed(() => {
    let filtered = members.value;

    // Filter by text
    if (filterText.value) {
      const text = filterText.value.toLowerCase();
      filtered = filtered.filter((m) => m.memberName.toLowerCase().includes(text));
    }

    // Filter by tags
    if (selectedTags.value.length > 0) {
      filtered = filtered.filter((m) => selectedTags.value.some((tag) => m.tags.includes(tag)));
    }

    return filtered;
  });

  const sortedMembers = computed(() => {
    // Sort by health: unreachable > stale > degraded/unhealthy > healthy, then alphabetically
    return [...filteredMembers.value].sort((a, b) => {
      // Connection state priority
      const stateOrder: Record<string, number> = { UNREACHABLE: 0, STALE: 1, CONNECTED: 2 };
      const stateA = stateOrder[a.connectionState];
      const stateB = stateOrder[b.connectionState];
      if (stateA !== stateB) return stateA - stateB;

      // Health code priority (0=green, 1=yellow, 2=red)
      const codeA = a.status?.code ?? 999;
      const codeB = b.status?.code ?? 999;
      if (codeA !== codeB) return codeB - codeA; // Higher code first (red > yellow > green)

      // Alphabetically
      return a.memberName.localeCompare(b.memberName);
    });
  });

  const allTags = computed(() => {
    const tags = new Set<string>();
    members.value.forEach((m) => m.tags.forEach((tag) => tags.add(tag)));
    return Array.from(tags).sort();
  });

  return {
    members,
    filteredMembers,
    sortedMembers,
    lastRefresh,
    filterText,
    selectedTags,
    allTags,
    loading,
    aggregatedStats,
    flowMetrics,
    flowMetricsInterval,
    flowMetricsLoading,
    fetchMembers,
    fetchStats,
    fetchFlowMetrics,
    fetchAll,
  };
}
