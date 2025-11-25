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
// ABOUTME: Composable for leader configuration management functionality.
// ABOUTME: Provides API calls for plugin comparison, snapshot viewing, and config diffs.

import { ref, computed } from "vue";
import useApi from "./useApi";

export interface PluginInfo {
  groupId: string;
  artifactId: string;
  version: string;
  displayName: string;
  imageName: string;
  imageTag: string;
}

export interface DiffItem {
  path: string;
  type: "ADDED" | "REMOVED" | "MODIFIED";
  leaderValue: unknown;
  memberValue: unknown;
}

export interface DiffSection {
  name: string;
  diffCount: number;
  diffs: DiffItem[];
}

export interface ConfigDiff {
  leaderName: string;
  memberName: string;
  comparedAt: string;
  sections: DiffSection[];
}

export interface Snapshot {
  pluginVariables?: unknown[];
  deletePolicies?: unknown;
  deltaFiProperties?: { key: string; value: string }[];
  links?: unknown[];
  restDataSources?: unknown[];
  timedDataSources?: unknown[];
  onErrorDataSources?: unknown[];
  transformFlows?: unknown[];
  dataSinks?: unknown[];
  plugins?: PluginSnapshot[];
  resumePolicies?: unknown[];
  systemFlowPlans?: unknown;
  users?: unknown[];
  roles?: unknown[];
}

export interface PluginSnapshot {
  imageName: string;
  imagePullSecret: string | null;
  pluginCoordinates: {
    groupId: string;
    artifactId: string;
    version: string;
  };
}

export interface MemberSummary {
  name: string;
  reporting: boolean;
  diffCount: number | null;
  loading: boolean;
}

export default function useLeaderConfig() {
  const { response, get, loading } = useApi();

  // State
  const memberPlugins = ref<Record<string, PluginInfo[]>>({});
  const membersNotReporting = ref<string[]>([]);
  const leaderSnapshot = ref<Snapshot | null>(null);
  const memberSnapshot = ref<Snapshot | null>(null);
  const selectedMemberName = ref<string>("");
  const currentSnapshot = ref<Snapshot | null>(null);
  const currentSnapshotMember = ref<string>("Leader");
  const currentDiff = ref<ConfigDiff | null>(null);
  const diffMember = ref<string>("");
  const pluginsLoading = ref(false);
  const snapshotLoading = ref(false);
  const diffLoading = ref(false);
  const memberSummaries = ref<MemberSummary[]>([]);
  const membersLoading = ref(false);

  // Fetch plugins from all members
  const fetchAllPlugins = async () => {
    pluginsLoading.value = true;
    try {
      await get("leader/config/plugins");
      if (response.value) {
        const data = response.value as { plugins: Record<string, PluginInfo[]>; membersNotReporting: string[] };
        memberPlugins.value = data.plugins;
        membersNotReporting.value = data.membersNotReporting || [];
      }
    } finally {
      pluginsLoading.value = false;
    }
  };

  // Fetch snapshot for a specific member
  const fetchSnapshot = async (memberName: string) => {
    snapshotLoading.value = true;
    currentSnapshotMember.value = memberName;
    try {
      await get(`leader/config/snapshot/${encodeURIComponent(memberName)}`);
      if (response.value) {
        currentSnapshot.value = response.value as Snapshot;
      } else {
        currentSnapshot.value = null;
      }
    } finally {
      snapshotLoading.value = false;
    }
  };

  // Fetch diff between leader and a member
  const fetchDiff = async (memberName: string) => {
    diffLoading.value = true;
    diffMember.value = memberName;
    try {
      await get(`leader/config/diff/${encodeURIComponent(memberName)}`);
      if (response.value) {
        currentDiff.value = response.value as ConfigDiff;
      } else {
        currentDiff.value = null;
      }
    } finally {
      diffLoading.value = false;
    }
  };

  // Fetch both leader and member snapshots for comparison
  const fetchComparisonSnapshots = async (memberName: string) => {
    snapshotLoading.value = true;
    selectedMemberName.value = memberName;
    try {
      // Fetch both in parallel using direct fetch to avoid shared response ref race condition
      const [leaderRes, memberRes] = await Promise.all([
        fetch("/api/v2/leader/config/snapshot/Leader").then((r) => (r.ok ? r.json() : null)),
        fetch(`/api/v2/leader/config/snapshot/${encodeURIComponent(memberName)}`).then((r) => (r.ok ? r.json() : null)),
      ]);
      leaderSnapshot.value = leaderRes as Snapshot | null;
      memberSnapshot.value = memberRes as Snapshot | null;
    } finally {
      snapshotLoading.value = false;
    }
  };

  // Fetch leader snapshot only
  const fetchLeaderSnapshot = async () => {
    snapshotLoading.value = true;
    try {
      await get("leader/config/snapshot/Leader");
      if (response.value) {
        leaderSnapshot.value = response.value as Snapshot;
      }
    } finally {
      snapshotLoading.value = false;
    }
  };

  // Compute diff count between two snapshots
  const computeDiffCount = (leader: Snapshot, member: Snapshot): number => {
    let count = 0;

    // Compare plugins
    const leaderPlugins = new Map((leader.plugins || []).map((p) => [`${p.pluginCoordinates.groupId}:${p.pluginCoordinates.artifactId}`, p.pluginCoordinates.version]));
    const memberPluginsMap = new Map((member.plugins || []).map((p) => [`${p.pluginCoordinates.groupId}:${p.pluginCoordinates.artifactId}`, p.pluginCoordinates.version]));
    for (const [key, version] of leaderPlugins) {
      if (!memberPluginsMap.has(key) || memberPluginsMap.get(key) !== version) count++;
    }
    for (const key of memberPluginsMap.keys()) {
      if (!leaderPlugins.has(key)) count++;
    }

    // Compare flows (systemFlowPlans is an object with sub-arrays, not a direct flow array)
    const flowTypes = ["restDataSources", "timedDataSources", "onErrorDataSources", "transformFlows", "dataSinks"] as const;
    for (const flowType of flowTypes) {
      const leaderFlows = new Map(((leader[flowType] as { name: string; running?: boolean; testMode?: boolean }[]) || []).map((f) => [f.name, f]));
      const memberFlows = new Map(((member[flowType] as { name: string; running?: boolean; testMode?: boolean }[]) || []).map((f) => [f.name, f]));
      for (const [name, flow] of leaderFlows) {
        const memberFlow = memberFlows.get(name);
        if (!memberFlow || flow.running !== memberFlow.running || flow.testMode !== memberFlow.testMode) count++;
      }
      for (const name of memberFlows.keys()) {
        if (!leaderFlows.has(name)) count++;
      }
    }

    // Compare properties
    const leaderProps = new Map((leader.deltaFiProperties || []).map((p) => [p.key, p.value]));
    const memberProps = new Map((member.deltaFiProperties || []).map((p) => [p.key, p.value]));
    for (const [key, value] of leaderProps) {
      if (memberProps.get(key) !== value) count++;
    }
    for (const key of memberProps.keys()) {
      if (!leaderProps.has(key)) count++;
    }

    return count;
  };

  // Fetch summaries for all members
  const fetchAllMemberSummaries = async () => {
    membersLoading.value = true;

    // First ensure we have the plugins data to know all members
    if (Object.keys(memberPlugins.value).length === 0) {
      await fetchAllPlugins();
    }

    // Get leader snapshot
    if (!leaderSnapshot.value) {
      await fetchLeaderSnapshot();
    }

    const allMembers = memberNames.value.filter((n) => n !== "Leader");

    // Initialize summaries with loading state
    memberSummaries.value = allMembers.map((name) => ({
      name,
      reporting: !membersNotReporting.value.includes(name),
      diffCount: null,
      loading: !membersNotReporting.value.includes(name),
    }));

    // Fetch snapshots for reporting members in parallel
    // Note: Using direct fetch instead of useApi to avoid shared response ref race condition
    const reportingMembers = allMembers.filter((n) => !membersNotReporting.value.includes(n));

    await Promise.all(
      reportingMembers.map(async (memberName) => {
        const summary = memberSummaries.value.find((s) => s.name === memberName);
        try {
          const res = await fetch(`/api/v2/leader/config/snapshot/${encodeURIComponent(memberName)}`);
          if (!res.ok) {
            if (summary) summary.loading = false;
            return;
          }
          const snapshot = await res.json() as Snapshot | null;
          if (summary && snapshot && leaderSnapshot.value) {
            summary.diffCount = computeDiffCount(leaderSnapshot.value, snapshot);
          }
        } catch {
          // Ignore fetch errors
        } finally {
          if (summary) summary.loading = false;
        }
      })
    );

    membersLoading.value = false;
  };

  // Computed: unique plugins across all members
  const uniquePlugins = computed(() => {
    const pluginMap = new Map<string, { plugin: PluginInfo; members: Map<string, string> }>();

    for (const [memberName, plugins] of Object.entries(memberPlugins.value)) {
      for (const plugin of plugins) {
        const key = `${plugin.groupId}:${plugin.artifactId}`;
        if (!pluginMap.has(key)) {
          pluginMap.set(key, { plugin, members: new Map() });
        }
        pluginMap.get(key)!.members.set(memberName, plugin.version);
      }
    }

    return Array.from(pluginMap.entries()).map(([key, data]) => ({
      coordinates: key,
      displayName: data.plugin.displayName || data.plugin.artifactId,
      groupId: data.plugin.groupId,
      artifactId: data.plugin.artifactId,
      memberVersions: Object.fromEntries(data.members),
    }));
  });

  // Computed: member names from plugins (including those not reporting)
  const memberNames = computed(() => {
    const names = new Set([...Object.keys(memberPlugins.value), ...membersNotReporting.value]);
    return Array.from(names).sort((a, b) => {
      // Leader first, then alphabetically
      if (a === "Leader") return -1;
      if (b === "Leader") return 1;
      return a.localeCompare(b);
    });
  });

  // Computed: total diff count
  const totalDiffCount = computed(() => {
    if (!currentDiff.value) return 0;
    return currentDiff.value.sections.reduce((sum, s) => sum + s.diffCount, 0);
  });

  return {
    // State
    memberPlugins,
    membersNotReporting,
    leaderSnapshot,
    memberSnapshot,
    selectedMemberName,
    currentSnapshot,
    currentSnapshotMember,
    currentDiff,
    diffMember,
    memberSummaries,
    loading,
    pluginsLoading,
    snapshotLoading,
    diffLoading,
    membersLoading,

    // Computed
    uniquePlugins,
    memberNames,
    totalDiffCount,

    // Actions
    fetchAllPlugins,
    fetchSnapshot,
    fetchDiff,
    fetchComparisonSnapshots,
    fetchLeaderSnapshot,
    fetchAllMemberSummaries,
  };
}
