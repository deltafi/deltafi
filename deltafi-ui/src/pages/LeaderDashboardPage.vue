<!--
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
-->

<template>
  <div class="leader-dashboard-page">
    <PageHeader heading="Fleet Dashboard">
      <Button label="Refresh" icon="pi pi-refresh" @click="refresh" :loading="loading" />
    </PageHeader>

    <!-- View Mode Selector -->
    <div class="view-mode-container mb-3">
      <SelectButton v-model="viewMode" :options="viewModeOptions" optionLabel="label" optionValue="value" :allow-empty="false" class="view-mode-selector" />
      <SelectButton v-model="displayMode" :options="displayModeOptions" optionLabel="icon" optionValue="value" :allow-empty="false" class="display-mode-selector" dataKey="value">
        <template #option="{ option }">
          <i :class="option.icon" v-tooltip.top="option.tooltip" />
        </template>
      </SelectButton>
    </div>
    <div v-if="isTimeBasedMetricView" class="interval-selector-container mb-3">
      <SelectButton v-model="flowMetricsInterval" :options="intervalOptions" optionLabel="label" optionValue="value" :allow-empty="false" class="interval-selector" />
      <i class="pi pi-spin pi-spinner loading-indicator" :class="{ visible: flowMetricsLoading }" />
    </div>

    <!-- Filters -->
    <CollapsiblePanel class="filters-panel mb-3" :collapsed="true">
      <template #header>
        <span class="filters-header">
          <span class="p-panel-title">Filters</span>
          <Button v-tooltip.right="{ value: 'Clear filters', disabled: !hasActiveFilters }" rounded :class="`ml-2 p-column-filter-menu-button p-link p-column-filter-menu-button-open ${hasActiveFilters ? 'p-column-filter-menu-button-active' : ''}`" :disabled="!hasActiveFilters" @click.stop="clearFilters">
            <i class="pi pi-filter" style="font-size: 1rem" />
          </Button>
        </span>
      </template>
      <div class="filters-content">
        <div class="filter-item">
          <label>Search by Name</label>
          <InputText v-model="filterText" placeholder="Filter by member name..." class="w-full" />
        </div>

        <div class="filter-item">
          <label>Filter by Tags</label>
          <MultiSelect v-model="selectedTags" :options="allTags" placeholder="Select tags..." class="w-full" />
        </div>

        <div v-if="isMetricView" class="filter-item">
          <label>Filter by {{ tableKeyHeader }}</label>
          <InputText v-model="metricFilter" :placeholder="`Filter ${tableKeyHeader.toLowerCase()}...`" class="w-full" />
        </div>
      </div>
      <div class="filter-stats">
          Showing {{ sortedMembers.length }} of {{ members.length }} members
        </div>
    </CollapsiblePanel>

    <!-- Summary View -->
    <div v-if="displayMode === 'summary'" class="summary-view mb-3" :class="{ 'metric-summary': isMetricView }">
      <!-- DataFlow Summary -->
      <Panel v-if="viewMode === 'summary'" header="Fleet DataFlow Summary" class="summary-panel">
        <!-- Filtered stats -->
        <div v-if="isFiltered" class="summary-section">
          <div class="summary-section-label">Filtered ({{ filteredMemberCount }} of {{ members.length }} members)</div>
          <div class="summary-grid">
            <div class="summary-stat">
              <div class="summary-value healthy">{{ filteredHealthyCount }}</div>
              <div class="summary-label">Healthy</div>
            </div>
            <div class="summary-stat" v-if="filteredUnhealthyCount > 0">
              <div class="summary-value unhealthy">{{ filteredUnhealthyCount }}</div>
              <div class="summary-label">Unhealthy</div>
            </div>
            <div class="summary-stat">
              <div class="summary-value">{{ filteredTotalInFlight.toLocaleString() }}</div>
              <div class="summary-label">In-Flight</div>
            </div>
            <div class="summary-stat">
              <div class="summary-value" :class="{ error: filteredTotalErrors > 0 }">{{ filteredTotalErrors.toLocaleString() }}</div>
              <div class="summary-label">Errors</div>
            </div>
            <div class="summary-stat">
              <div class="summary-value">{{ filteredTotalWarmQueue.toLocaleString() }}</div>
              <div class="summary-label">Warm Queue</div>
            </div>
            <div class="summary-stat">
              <div class="summary-value" :class="{ 'has-cold-queue': filteredTotalColdQueue > 0 }">{{ filteredTotalColdQueue.toLocaleString() }}</div>
              <div class="summary-label">Cold Queue</div>
            </div>
            <div class="summary-stat">
              <div class="summary-value" :class="{ 'has-paused': filteredTotalPaused > 0 }">{{ filteredTotalPaused.toLocaleString() }}</div>
              <div class="summary-label">Paused</div>
            </div>
          </div>
        </div>
        <!-- Fleet totals -->
        <div class="summary-section" :class="{ 'fleet-totals': isFiltered }">
          <div v-if="isFiltered" class="summary-section-label">Fleet Totals ({{ members.length }} members)</div>
          <div class="summary-grid">
            <div v-if="!isFiltered" class="summary-stat">
              <div class="summary-value">{{ members.length }}</div>
              <div class="summary-label">Members</div>
            </div>
            <div class="summary-stat">
              <div class="summary-value healthy">{{ aggregatedStats?.healthyCount ?? 0 }}</div>
              <div class="summary-label">Healthy</div>
            </div>
            <div class="summary-stat" v-if="(aggregatedStats?.unhealthyCount ?? 0) > 0">
              <div class="summary-value unhealthy">{{ aggregatedStats?.unhealthyCount ?? 0 }}</div>
              <div class="summary-label">Unhealthy</div>
            </div>
            <div class="summary-stat">
              <div class="summary-value">{{ (aggregatedStats?.totalInFlight ?? 0).toLocaleString() }}</div>
              <div class="summary-label">In-Flight</div>
            </div>
            <div class="summary-stat">
              <div class="summary-value" :class="{ error: (aggregatedStats?.totalErrors ?? 0) > 0 }">{{ (aggregatedStats?.totalErrors ?? 0).toLocaleString() }}</div>
              <div class="summary-label">Errors</div>
            </div>
            <div class="summary-stat">
              <div class="summary-value">{{ (aggregatedStats?.totalWarmQueue ?? 0).toLocaleString() }}</div>
              <div class="summary-label">Warm Queue</div>
            </div>
            <div class="summary-stat">
              <div class="summary-value" :class="{ 'has-cold-queue': (aggregatedStats?.totalColdQueue ?? 0) > 0 }">{{ (aggregatedStats?.totalColdQueue ?? 0).toLocaleString() }}</div>
              <div class="summary-label">Cold Queue</div>
            </div>
            <div class="summary-stat">
              <div class="summary-value" :class="{ 'has-paused': (aggregatedStats?.totalPaused ?? 0) > 0 }">{{ (aggregatedStats?.totalPaused ?? 0).toLocaleString() }}</div>
              <div class="summary-label">Paused</div>
            </div>
          </div>
        </div>
      </Panel>

      <!-- System Summary -->
      <Panel v-else-if="viewMode === 'system'" header="Fleet System Summary" class="summary-panel">
        <!-- Filtered stats -->
        <div v-if="isFiltered" class="summary-section">
          <div class="summary-section-label">Filtered ({{ filteredMemberCount }} of {{ members.length }} members)</div>
          <div class="summary-grid">
            <div class="summary-stat">
              <div class="summary-value">{{ avgCpu ?? '—' }}{{ avgCpu != null ? '%' : '' }}</div>
              <div class="summary-label">Avg CPU</div>
            </div>
            <div class="summary-stat">
              <div class="summary-value">{{ maxCpu ?? '—' }}{{ maxCpu != null ? '%' : '' }}</div>
              <div class="summary-label">Max CPU</div>
            </div>
            <div class="summary-stat">
              <div class="summary-value">{{ avgMemory ?? '—' }}{{ avgMemory != null ? '%' : '' }}</div>
              <div class="summary-label">Avg Memory</div>
            </div>
            <div class="summary-stat">
              <div class="summary-value">{{ maxMemory ?? '—' }}{{ maxMemory != null ? '%' : '' }}</div>
              <div class="summary-label">Max Memory</div>
            </div>
            <div class="summary-stat">
              <div class="summary-value">{{ avgDisk ?? '—' }}{{ avgDisk != null ? '%' : '' }}</div>
              <div class="summary-label">Avg Disk</div>
            </div>
            <div class="summary-stat">
              <div class="summary-value">{{ maxDisk ?? '—' }}{{ maxDisk != null ? '%' : '' }}</div>
              <div class="summary-label">Max Disk</div>
            </div>
          </div>
        </div>
        <!-- Fleet totals -->
        <div class="summary-section" :class="{ 'fleet-totals': isFiltered }">
          <div v-if="isFiltered" class="summary-section-label">Fleet Totals ({{ members.length }} members)</div>
          <div class="summary-grid">
            <div class="summary-stat">
              <div class="summary-value">{{ allAvgCpu ?? '—' }}{{ allAvgCpu != null ? '%' : '' }}</div>
              <div class="summary-label">Avg CPU</div>
            </div>
            <div class="summary-stat">
              <div class="summary-value">{{ allMaxCpu ?? '—' }}{{ allMaxCpu != null ? '%' : '' }}</div>
              <div class="summary-label">Max CPU</div>
            </div>
            <div class="summary-stat">
              <div class="summary-value">{{ allAvgMemory ?? '—' }}{{ allAvgMemory != null ? '%' : '' }}</div>
              <div class="summary-label">Avg Memory</div>
            </div>
            <div class="summary-stat">
              <div class="summary-value">{{ allMaxMemory ?? '—' }}{{ allMaxMemory != null ? '%' : '' }}</div>
              <div class="summary-label">Max Memory</div>
            </div>
            <div class="summary-stat">
              <div class="summary-value">{{ allAvgDisk ?? '—' }}{{ allAvgDisk != null ? '%' : '' }}</div>
              <div class="summary-label">Avg Disk</div>
            </div>
            <div class="summary-stat">
              <div class="summary-value">{{ allMaxDisk ?? '—' }}{{ allMaxDisk != null ? '%' : '' }}</div>
              <div class="summary-label">Max Disk</div>
            </div>
          </div>
        </div>
      </Panel>

      <!-- Flow Metrics Summary -->
      <Panel v-else-if="isMetricView" :header="metricSummaryHeader" class="summary-panel metric-panel">
        <DataTable :value="summaryTableData" size="small" class="metrics-table" scrollable scrollHeight="flex" :rowClass="rowClass">
          <Column field="key" :header="tableKeyHeader" style="min-width: 200px">
            <template #body="{ data }">
              <strong v-if="data.isTotal">{{ data.key }}</strong>
              <span v-else>{{ data.key }}</span>
            </template>
          </Column>
          <Column field="value" header="Value" style="min-width: 120px" class="numeric-column">
            <template #body="{ data }">
              <strong v-if="data.isTotal">{{ formatBytes(data.value) }}</strong>
              <span v-else>{{ formatBytes(data.value) }}</span>
            </template>
          </Column>
        </DataTable>
      </Panel>
    </div>

    <!-- Member Cards Grid -->
    <div v-else-if="displayMode === 'cards'" class="members-grid">
      <MemberStatusCard v-for="member in sortedMembers" :key="member.memberName" :member="member" :viewMode="viewMode" :flowMetrics="flowMetrics[member.memberName]" :flowMetricsLoaded="Object.keys(flowMetrics).length > 0" :metricFilter="metricFilter" />
    </div>

    <!-- Table View -->
    <div v-else-if="displayMode === 'table'" class="table-view">
      <DataTable :value="sortedTableData" size="small" class="metrics-table" scrollable scrollHeight="flex" :rowClass="rowClass" v-model:sortField="tableSortField" v-model:sortOrder="tableSortOrder" @sort="onTableSort">
        <Column field="member" header="Member" frozen style="width: 1%; white-space: nowrap" sortable>
          <template #body="{ data }">
            <strong v-if="data.isTotal">{{ data.member }}</strong>
            <span v-else>{{ data.member }}</span>
          </template>
        </Column>
        <Column v-if="!isMetricView" header="" style="width: 1%; white-space: nowrap; text-align: right">
          <template #body="{ data }">
            <div v-if="data.memberData" class="member-icons justify-end">
              <i v-if="!data.memberData.isLeader" class="pi pi-external-link text-muted cursor-pointer" @click="openMemberSite(data.memberData.url)" v-tooltip.top="'Visit ' + data.memberData.url" />
              <i v-else class="pi pi-external-link" style="visibility: hidden" />
              <i class="pi pi-tag text-muted" v-tooltip.top="data.memberData.tags.length > 0 ? 'Tags: ' + data.memberData.tags.join(', ') : 'No tags'" />
              <Tag v-if="data.memberData.connectionState === 'UNREACHABLE'" severity="danger" value="Unreachable" class="status-tag" />
              <Tag v-else-if="data.memberData.connectionState === 'STALE'" severity="warning" value="Stale" class="status-tag" />
              <Tag v-else-if="data.memberData.status" :severity="getHealthSeverity(data.memberData)" :value="data.memberData.status.state" class="status-tag" />
            </div>
          </template>
        </Column>
        <Column v-if="isMetricView" field="key" :header="tableKeyHeader" style="min-width: 200px" sortable>
          <template #body="{ data }">
            <strong v-if="data.isTotal">{{ data.key }}</strong>
            <span v-else>{{ data.key }}</span>
          </template>
        </Column>
        <Column v-for="col in tableColumns" :key="col.field" :field="col.field" :header="col.header" style="min-width: 120px" class="numeric-column" sortable>
          <template #body="{ data }">
            <strong v-if="data.isTotal" :class="getValueClass(col.field, data[col.field])">{{ formatBytesOrValue(data[col.field]) }}</strong>
            <span v-else :class="getValueClass(col.field, data[col.field])">{{ formatBytesOrValue(data[col.field]) }}</span>
          </template>
        </Column>
      </DataTable>
    </div>

    <!-- Empty State -->
    <div v-if="!loading && members.length === 0" class="empty-state">
      <Message severity="info">
        No members configured. This instance is not configured as a leader.
      </Message>
    </div>

    <!-- Last Refresh -->
    <div v-if="lastRefresh" class="last-refresh">
      Last refresh: <Timestamp :timestamp="lastRefresh" :showTimeAgo="true" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import useLeaderDashboard from "@/composables/useLeaderDashboard";
import MemberStatusCard from "@/components/leader/MemberStatusCard.vue";
import PageHeader from "@/components/PageHeader.vue";
import InputText from "primevue/inputtext";
import MultiSelect from "primevue/multiselect";
import SelectButton from "primevue/selectbutton";
import Button from "primevue/button";
import Message from "primevue/message";
import Panel from "primevue/panel";
import Tag from "primevue/tag";
import DataTable from "primevue/datatable";
import Column from "primevue/column";
import Timestamp from "@/components/Timestamp.vue";
import CollapsiblePanel from "@/components/CollapsiblePanel.vue";

const {
  members,
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
  fetchAll,
  fetchFlowMetrics,
} = useLeaderDashboard();

// Load persisted preferences from localStorage
const STORAGE_KEY = "leaderDashboard";
const loadPreferences = () => {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored) return JSON.parse(stored);
  } catch { /* ignore */ }
  return {};
};
const savedPrefs = loadPreferences();

const viewMode = ref(savedPrefs.viewMode || "summary");
const metricFilter = ref("");
const displayMode = ref<"cards" | "table" | "summary">(savedPrefs.displayMode || "cards");
const tableSortField = ref<string | null>(null);
const tableSortOrder = ref<number | null>(null);

// Override flowMetricsInterval if saved
if (savedPrefs.flowMetricsInterval) {
  flowMetricsInterval.value = savedPrefs.flowMetricsInterval;
}

// Persist preferences when they change
const savePreferences = () => {
  localStorage.setItem(STORAGE_KEY, JSON.stringify({
    viewMode: viewMode.value,
    displayMode: displayMode.value,
    flowMetricsInterval: flowMetricsInterval.value,
  }));
};

watch([viewMode, displayMode, flowMetricsInterval], savePreferences);

// Filter helpers
const hasActiveFilters = computed(() => {
  return filterText.value.length > 0 || selectedTags.value.length > 0 || metricFilter.value.length > 0;
});

const isFiltered = computed(() => {
  return filterText.value.length > 0 || selectedTags.value.length > 0;
});

const clearFilters = () => {
  filterText.value = "";
  selectedTags.value = [];
  metricFilter.value = "";
};

const viewModeOptions = [
  { label: "DataFlow", value: "summary" },
  { label: "System", value: "system" },
  { label: "Ingress", value: "ingress" },
  { label: "Egress", value: "egress" },
  { label: "Storage", value: "storage" },
  { label: "Deleted", value: "deleted" },
];

const intervalOptions = [
  { label: "5m", value: 5 },
  { label: "30m", value: 30 },
  { label: "1h", value: 60 },
  { label: "6h", value: 360 },
  { label: "12h", value: 720 },
  { label: "1d", value: 1440 },
];

const displayModeOptions = [
  { value: "summary", icon: "pi pi-chart-bar", tooltip: "Summary view" },
  { value: "cards", icon: "pi pi-th-large", tooltip: "Card view" },
  { value: "table", icon: "pi pi-table", tooltip: "Table view" },
];

const isMetricView = computed(() => ["ingress", "egress", "storage", "deleted"].includes(viewMode.value));
const isTimeBasedMetricView = computed(() => ["ingress", "egress", "deleted"].includes(viewMode.value));

const metricSummaryHeader = computed(() => {
  switch (viewMode.value) {
    case "ingress": return "Fleet Ingress Summary";
    case "egress": return "Fleet Egress Summary";
    case "storage": return "Fleet Storage Summary";
    case "deleted": return "Fleet Deleted Summary";
    default: return "Fleet Summary";
  }
});

// Summary view aggregations
const avgCpu = computed(() => {
  const values = sortedMembers.value.map(m => m.cpuUsage).filter(v => v != null) as number[];
  if (values.length === 0) return null;
  return Math.round(values.reduce((a, b) => a + b, 0) / values.length * 10) / 10;
});

const avgMemory = computed(() => {
  const values = sortedMembers.value.map(m => m.memoryUsage).filter(v => v != null) as number[];
  if (values.length === 0) return null;
  return Math.round(values.reduce((a, b) => a + b, 0) / values.length * 10) / 10;
});

const avgDisk = computed(() => {
  const values = sortedMembers.value.map(m => m.diskUsage).filter(v => v != null) as number[];
  if (values.length === 0) return null;
  return Math.round(values.reduce((a, b) => a + b, 0) / values.length * 10) / 10;
});

const maxCpu = computed(() => {
  const values = sortedMembers.value.map(m => m.cpuUsage).filter(v => v != null) as number[];
  if (values.length === 0) return null;
  return Math.max(...values);
});

const maxMemory = computed(() => {
  const values = sortedMembers.value.map(m => m.memoryUsage).filter(v => v != null) as number[];
  if (values.length === 0) return null;
  return Math.max(...values);
});

const maxDisk = computed(() => {
  const values = sortedMembers.value.map(m => m.diskUsage).filter(v => v != null) as number[];
  if (values.length === 0) return null;
  return Math.max(...values);
});

// Unfiltered system stats for comparison
const allAvgCpu = computed(() => {
  const values = members.value.map(m => m.cpuUsage).filter(v => v != null) as number[];
  if (values.length === 0) return null;
  return Math.round(values.reduce((a, b) => a + b, 0) / values.length * 10) / 10;
});
const allMaxCpu = computed(() => {
  const values = members.value.map(m => m.cpuUsage).filter(v => v != null) as number[];
  if (values.length === 0) return null;
  return Math.max(...values);
});
const allAvgMemory = computed(() => {
  const values = members.value.map(m => m.memoryUsage).filter(v => v != null) as number[];
  if (values.length === 0) return null;
  return Math.round(values.reduce((a, b) => a + b, 0) / values.length * 10) / 10;
});
const allMaxMemory = computed(() => {
  const values = members.value.map(m => m.memoryUsage).filter(v => v != null) as number[];
  if (values.length === 0) return null;
  return Math.max(...values);
});
const allAvgDisk = computed(() => {
  const values = members.value.map(m => m.diskUsage).filter(v => v != null) as number[];
  if (values.length === 0) return null;
  return Math.round(values.reduce((a, b) => a + b, 0) / values.length * 10) / 10;
});
const allMaxDisk = computed(() => {
  const values = members.value.map(m => m.diskUsage).filter(v => v != null) as number[];
  if (values.length === 0) return null;
  return Math.max(...values);
});

// DataFlow summary stats computed from filtered members
const filteredMemberCount = computed(() => sortedMembers.value.length);
const filteredHealthyCount = computed(() => sortedMembers.value.filter(m => m.status?.code === 0).length);
const filteredUnhealthyCount = computed(() => sortedMembers.value.filter(m => m.status?.code === 2).length);
const filteredTotalInFlight = computed(() => sortedMembers.value.reduce((sum, m) => sum + (m.inFlightCount ?? 0), 0));
const filteredTotalErrors = computed(() => sortedMembers.value.reduce((sum, m) => sum + (m.errorCount ?? 0), 0));
const filteredTotalWarmQueue = computed(() => sortedMembers.value.reduce((sum, m) => sum + (m.warmQueuedCount ?? 0), 0));
const filteredTotalColdQueue = computed(() => sortedMembers.value.reduce((sum, m) => sum + (m.coldQueuedCount ?? 0), 0));
const filteredTotalPaused = computed(() => sortedMembers.value.reduce((sum, m) => sum + (m.pausedCount ?? 0), 0));

const aggregatedFlowBreakdown = computed((): [string, number][] => {
  const totals: Record<string, number> = {};
  const filter = metricFilter.value.toLowerCase();

  for (const member of sortedMembers.value) {
    const fm = flowMetrics.value[member.memberName];
    if (!fm) continue;

    const dataMap = getFlowMetricMap(fm, viewMode.value);
    for (const [key, value] of Object.entries(dataMap)) {
      if (filter && !key.toLowerCase().includes(filter)) continue;
      totals[key] = (totals[key] || 0) + value;
    }
  }

  return Object.entries(totals).sort((a, b) => b[1] - a[1]);
});

const aggregatedFlowTotal = computed(() => {
  return aggregatedFlowBreakdown.value.reduce((sum, [, value]) => sum + value, 0);
});

// Summary table data for metric views (aggregated by key, no member column)
const summaryTableData = computed(() => {
  const rows: Record<string, unknown>[] = [];
  const totalRow = { key: "Total", value: aggregatedFlowTotal.value, isTotal: true };

  for (const [key, value] of aggregatedFlowBreakdown.value) {
    rows.push({ key, value });
  }

  return rows.length > 0 ? [totalRow, ...rows] : [];
});

// Fetch flow metrics when switching to a metric view or when interval changes
watch(viewMode, (newMode) => {
  if (["ingress", "egress", "storage", "deleted"].includes(newMode)) {
    fetchFlowMetrics();
  }
});

watch(flowMetricsInterval, () => {
  if (isMetricView.value) {
    fetchFlowMetrics();
  }
});

// Table view computed properties
const tableKeyHeader = computed(() => {
  switch (viewMode.value) {
    case "ingress": return "Data Source";
    case "egress": return "Data Sink";
    case "storage": return "Service";
    case "deleted": return "Policy";
    case "summary": return "Metric";
    case "system": return "Metric";
    default: return "Key";
  }
});

const tableColumns = computed(() => {
  if (viewMode.value === "summary") {
    return [
      { field: "inFlight", header: "In-Flight" },
      { field: "errors", header: "Errors" },
      { field: "warmQueue", header: "Warm Queue" },
      { field: "coldQueue", header: "Cold Queue" },
      { field: "paused", header: "Paused" },
    ];
  } else if (viewMode.value === "system") {
    return [
      { field: "cpu", header: "CPU" },
      { field: "memory", header: "Memory" },
      { field: "disk", header: "Disk" },
    ];
  } else {
    return [{ field: "value", header: "Value" }];
  }
});

const tableData = computed(() => {
  const rows: Record<string, unknown>[] = [];
  const filter = metricFilter.value.toLowerCase();

  if (viewMode.value === "summary") {
    // DataFlow metrics - one row per member
    let totalInFlight = 0, totalErrors = 0, totalWarm = 0, totalCold = 0, totalPaused = 0;

    for (const member of sortedMembers.value) {
      rows.push({
        member: member.memberName,
        memberData: member,
        inFlight: member.inFlightCount,
        errors: member.errorCount,
        warmQueue: member.warmQueuedCount,
        coldQueue: member.coldQueuedCount,
        paused: member.pausedCount,
      });
      totalInFlight += member.inFlightCount ?? 0;
      totalErrors += member.errorCount ?? 0;
      totalWarm += member.warmQueuedCount ?? 0;
      totalCold += member.coldQueuedCount ?? 0;
      totalPaused += member.pausedCount ?? 0;
    }

    if (rows.length > 1) {
      rows.push({
        member: "Total",
        inFlight: totalInFlight,
        errors: totalErrors,
        warmQueue: totalWarm,
        coldQueue: totalCold,
        paused: totalPaused,
        isTotal: true,
      });
    }
  } else if (viewMode.value === "system") {
    // System metrics - one row per member
    for (const member of sortedMembers.value) {
      rows.push({
        member: member.memberName,
        memberData: member,
        cpu: member.cpuUsage != null ? `${member.cpuUsage}%` : "—",
        memory: member.memoryUsage != null ? `${member.memoryUsage}%` : "—",
        disk: member.diskUsage != null ? `${member.diskUsage}%` : "—",
      });
    }
  } else if (isMetricView.value) {
    // Flow metrics - one row per (member, key) combination
    let grandTotal = 0;

    for (const member of sortedMembers.value) {
      const fm = flowMetrics.value[member.memberName];
      if (!fm) {
        continue;
      }

      const dataMap = getFlowMetricMap(fm, viewMode.value);
      const entries = Object.entries(dataMap).filter(([key]) =>
        !filter || key.toLowerCase().includes(filter)
      );

      if (entries.length === 0) {
        continue;
      }

      let isFirst = true;
      for (const [key, value] of entries.sort((a, b) => a[0].localeCompare(b[0]))) {
        rows.push({ member: member.memberName, memberData: isFirst ? member : null, key, value });
        grandTotal += value;
        isFirst = false;
      }
    }

    // Add grand total
    if (sortedMembers.value.length > 0) {
      rows.push({ member: "Total", key: "", value: grandTotal, isTotal: true });
    }
  }

  return rows;
});

const sortedTableData = computed(() => {
  const data = tableData.value;
  const totalRow = data.find(r => r.isTotal);
  const dataRows = data.filter(r => !r.isTotal);

  if (!tableSortField.value || !tableSortOrder.value) {
    return totalRow ? [totalRow, ...dataRows] : dataRows;
  }

  const field = tableSortField.value;
  const order = tableSortOrder.value;

  const sorted = [...dataRows].sort((a, b) => {
    const aVal = a[field];
    const bVal = b[field];

    if (aVal == null && bVal == null) return 0;
    if (aVal == null) return 1;
    if (bVal == null) return -1;

    let result = 0;
    if (typeof aVal === "string" && typeof bVal === "string") {
      result = aVal.localeCompare(bVal);
    } else if (typeof aVal === "number" && typeof bVal === "number") {
      result = aVal - bVal;
    } else {
      result = String(aVal).localeCompare(String(bVal));
    }

    return result * order;
  });

  return totalRow ? [totalRow, ...sorted] : sorted;
});

const onTableSort = () => {
  // Sort state is managed by v-model, sorting handled by sortedTableData computed
};

const rowClass = (data: Record<string, unknown>) => {
  return data.isTotal ? "total-row" : "";
};

const getFlowMetricMap = (fm: { ingressByDataSource?: Record<string, number>; egressByDataSink?: Record<string, number>; storageByService?: Record<string, number>; deletedByPolicy?: Record<string, number> }, view: string): Record<string, number> => {
  switch (view) {
    case "ingress": return fm.ingressByDataSource || {};
    case "egress": return fm.egressByDataSink || {};
    case "storage": return fm.storageByService || {};
    case "deleted": return fm.deletedByPolicy || {};
    default: return {};
  }
};

const formatBytesOrValue = (value: unknown): string => {
  if (value === undefined || value === null || value === "—") return "—";
  if (typeof value === "string") return value;
  if (typeof value === "number") {
    // Check if it's a flow metric (bytes) or a count
    if (isMetricView.value) {
      return formatBytes(value);
    }
    return value.toLocaleString();
  }
  return String(value);
};

const formatBytes = (bytes: number): string => {
  if (bytes === 0) return "0 B";
  const units = ["B", "KB", "MB", "GB", "TB"];
  let value = bytes;
  let unitIndex = 0;
  while (value >= 1000 && unitIndex < units.length - 1) {
    value /= 1000;
    unitIndex++;
  }
  return `${value.toFixed(unitIndex === 0 ? 0 : 1)} ${units[unitIndex]}`;
};

const getHealthSeverity = (member: { status?: { code?: number } }): "success" | "warn" | "danger" | "info" => {
  if (!member.status) return "info";
  switch (member.status.code) {
    case 0: return "success";
    case 1: return "warn";
    case 2: return "danger";
    default: return "info";
  }
};

const openMemberSite = (url: string) => {
  window.open(url, "_blank");
};

const getValueClass = (field: string, value: unknown): Record<string, boolean> => {
  if (typeof value !== "number") return {};
  return {
    "has-errors": field === "errors" && value > 0,
    "has-cold-queue": field === "coldQueue" && value > 0,
    "has-paused": field === "paused" && value > 0,
  };
};

let refreshInterval: number | null = null;

onMounted(() => {
  fetchAll();

  // Auto-refresh every 5 seconds
  refreshInterval = window.setInterval(() => {
    fetchAll();
  }, 5_000);
});

onUnmounted(() => {
  if (refreshInterval) {
    clearInterval(refreshInterval);
  }
});

const refresh = () => {
  fetchAll();
};
</script>

<style lang="scss">
.leader-dashboard-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 120px);
  min-height: 400px;

  .p-panel {
    .p-panel-header {
      padding: .70rem 1rem;
    }
    .p-panel-content {
      padding: 1rem;
    }
  }

  .stats-panel {
    .stats-grid {
      display: flex;
      justify-content: space-around;
      flex-wrap: wrap;
      gap: 1rem;
    }

    .stat-item {
      text-align: center;
      min-width: 80px;

      &.warning {
        background-color: rgba(255, 193, 7, 0.1);
        border-radius: 6px;
        padding: 0.5rem;
      }
    }

    .stat-value {
      font-size: 1.5rem;
      font-weight: 600;
      color: var(--text-color);

      &.healthy {
        color: var(--green-500);
      }

      &.unhealthy {
        color: var(--red-500);
      }

      &.error {
        color: var(--red-500);
      }

      &.has-cold-queue {
        color: var(--orange-500);
      }

      &.has-paused {
        color: var(--yellow-500);
      }
    }

    .stat-label {
      font-size: 0.75rem;
      color: var(--text-color-secondary);
      text-transform: uppercase;
      margin-top: 0.25rem;
    }
  }

  .filters-panel {
    margin-bottom: 1.5rem;
    .p-panel-header {
      padding: 0 1.25rem;

      .p-panel-title {
        padding: 1rem 0;
      }

      .p-panel-header-icon {
        margin-top: 0.25rem;
        margin-right: 0;
      }
    }
  }

  .filters-content {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
    gap: 1rem;
    align-items: end;
  }

  .filter-item {
    display: flex;
    flex-direction: column;
    gap: 0.5rem;
  }

  .filter-item label {
    font-weight: 600;
    font-size: 0.875rem;
  }

  .filter-stats {
    color: var(--text-color-secondary);
    font-size: 0.875rem;
    white-space: nowrap;
    padding: 0;
    margin-top: 1rem;
    text-align: center;
  }

  .members-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(350px, 1fr));
    grid-auto-rows: 1fr;
    gap: 1.5rem;
    margin-bottom: 1.5rem;
  }

  .empty-state {
    text-align: center;
    padding: 2rem;
  }

  .last-refresh {
    text-align: center;
    color: var(--text-color-secondary);
    font-size: 0.875rem;
  }

  .view-mode-container {
    display: flex;
    justify-content: center;
    align-items: center;
    gap: 1rem;
    flex-wrap: wrap;
  }

  .interval-selector-container {
    display: flex;
    justify-content: center;
    align-items: center;
    gap: 0.5rem;
  }

  .interval-selector {
    .p-button {
      padding: 0.25rem 0.5rem;
      font-size: 0.75rem;
    }

    .p-button.p-highlight {
      background: var(--primary-color);
      border-color: var(--primary-color);
      color: var(--primary-color-text);
    }
  }

  .loading-indicator {
    width: 1rem;
    visibility: hidden;

    &.visible {
      visibility: visible;
    }
  }

  .filters-header {
    display: flex;
    align-items: center;
    gap: 0.5rem;
  }

  .table-view {
    margin-bottom: 1.5rem;
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;

    .p-datatable {
      flex: 1;
      display: flex;
      flex-direction: column;
      min-height: 0;
    }

    .p-datatable-wrapper {
      flex: 1;
      min-height: 0;
    }
  }

  .metrics-table {
    font-size: 0.875rem;

    .total-row {
      background-color: var(--surface-100) !important;
      font-weight: 600;

      td {
        position: sticky;
        top: 0;
        z-index: 1;
        background-color: var(--surface-100);
      }
    }

    .numeric-column {
      text-align: right;
    }

    th.numeric-column .p-column-header-content {
      justify-content: flex-end;
    }

    td.numeric-column {
      text-align: right;
    }

    .member-icons {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      flex-shrink: 0;

      &.justify-end {
        justify-content: flex-end;
      }

      .status-tag {
        font-size: 0.7rem;
        padding: 0.15rem 0.4rem;
      }

      i {
        font-size: 0.875rem;
      }
    }

    .has-errors {
      color: var(--red-500);
    }

    .has-cold-queue {
      color: var(--orange-500);
    }

    .has-paused {
      color: var(--yellow-500);
    }
  }

  .display-mode-selector {
    margin-left: 1rem;

    .p-button {
      padding: 0.5rem;
    }

    .p-button.p-highlight {
      background: var(--primary-color);
      border-color: var(--primary-color);
      color: var(--primary-color-text);
    }
  }

  .summary-view {
    &.metric-summary {
      flex: 1;
      min-height: 0;
      display: flex;
      flex-direction: column;
    }

    .summary-panel {
      &.metric-panel {
        flex: 1;
        display: flex;
        flex-direction: column;
        min-height: 0;

        .p-panel-content {
          flex: 1;
          display: flex;
          flex-direction: column;
          min-height: 0;
        }
      }

      .p-panel-content {
        padding: 1.5rem;
      }
    }

    .summary-grid {
      display: flex;
      justify-content: center;
      flex-wrap: wrap;
      gap: 2rem;
    }

    .summary-stat {
      text-align: center;
      min-width: 100px;
    }

    .summary-value {
      font-size: 2rem;
      font-weight: 600;
      color: var(--text-color);

      &.healthy { color: var(--green-500); }
      &.unhealthy { color: var(--red-500); }
      &.error { color: var(--red-500); }
      &.has-cold-queue { color: var(--orange-500); }
      &.has-paused { color: var(--yellow-500); }
      &.large { font-size: 3rem; }
    }

    .summary-label {
      font-size: 0.875rem;
      color: var(--text-color-secondary);
      text-transform: uppercase;
      margin-top: 0.25rem;
    }

    .summary-section {
      &:not(:last-child) {
        margin-bottom: 1.5rem;
        padding-bottom: 1.5rem;
        border-bottom: 1px solid var(--surface-border);
      }

      &.fleet-totals {
        opacity: 0.7;
      }
    }

    .summary-section-label {
      text-align: center;
      font-size: 0.875rem;
      font-weight: 600;
      color: var(--text-color-secondary);
      margin-bottom: 1rem;
    }
  }

  .view-mode-selector {
    .p-button.p-highlight {
      background: var(--primary-color);
      border-color: var(--primary-color);
      color: var(--primary-color-text);
    }
  }
}
</style>
