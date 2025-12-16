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
  <Panel class="member-status-card">
    <template #header>
      <div class="panel-header-content">
        <div class="header-title">
          <span class="member-name">{{ member.memberName }}</span>
          <span v-if="member.version" class="member-version">v{{ member.version }}</span>
        </div>
      </div>
    </template>
    <template #icons>
      <div class="header-icons">
        <i v-if="!member.isLeader" class="pi pi-external-link text-muted header-icon" @click="openSite" v-tooltip.top="'Visit ' + member.url" />
        <i class="pi pi-tag text-muted" v-tooltip.top="member.tags.length > 0 ? 'Tags: ' + member.tags.join(', ') : 'No tags'" />
        <div class="member-status-badge">
          <!-- Connection state indicator -->
          <Tag v-if="member.connectionState === 'UNREACHABLE'" severity="danger" value="Unreachable" />
          <Tag v-else-if="member.connectionState === 'STALE'" severity="warning" :value="`Stale (${formatAge(member.lastUpdated)})`" class="cursor-pointer" @click="showDetails" v-tooltip.top="'View cached status'" />
          <!-- Health badge - clickable -->
          <Tag v-else-if="member.status" :severity="healthSeverity" :value="member.status.state" :icon="healthIcon" class="cursor-pointer" @click="showDetails" />
        </div>
      </div>
    </template>

    <div class="member-card-content">

      <!-- DataFlow Metrics -->
      <div v-if="viewMode === 'summary'" class="metrics-grid mb-3">
        <div class="metric">
          <div class="metric-label">In Flight</div>
          <div class="metric-value">
            {{ member.inFlightCount ?? "—" }}
          </div>
        </div>

        <div class="metric">
          <div class="metric-label">Errors</div>
          <div class="metric-value" :class="{ 'has-errors': (member.errorCount ?? 0) > 0 }">
            {{ member.errorCount ?? "—" }}
          </div>
        </div>

        <div class="metric">
          <div class="metric-label">Warm Queue</div>
          <div class="metric-value">
            {{ member.warmQueuedCount ?? "—" }}
          </div>
        </div>

        <div class="metric">
          <div class="metric-label">Cold Queue</div>
          <div class="metric-value" :class="{ 'has-cold-queue': (member.coldQueuedCount ?? 0) > 0 }">
            {{ member.coldQueuedCount ?? "—" }}
          </div>
        </div>

        <div class="metric">
          <div class="metric-label">Paused</div>
          <div class="metric-value" :class="{ 'has-paused': (member.pausedCount ?? 0) > 0 }">
            {{ member.pausedCount ?? "—" }}
          </div>
        </div>
      </div>

      <!-- System Metrics -->
      <div v-if="viewMode === 'system'" class="metrics-grid mb-3">
        <div class="metric">
          <div class="metric-label">CPU</div>
          <div class="metric-value">{{ member.cpuUsage ?? "—" }}{{ member.cpuUsage ? '%' : '' }}</div>
        </div>

        <div class="metric">
          <div class="metric-label">Memory</div>
          <div class="metric-value">{{ member.memoryUsage ?? "—" }}{{ member.memoryUsage ? '%' : '' }}</div>
        </div>

        <div class="metric">
          <div class="metric-label">Disk</div>
          <div class="metric-value">{{ member.diskUsage ?? "—" }}{{ member.diskUsage ? '%' : '' }}</div>
        </div>
      </div>

      <!-- Ingress Metrics -->
      <div v-if="viewMode === 'ingress'" class="flow-metrics mb-3">
        <div v-if="!flowMetricsLoaded" class="loading-metrics">
          <i class="pi pi-spin pi-spinner" />
          <span>Loading metrics...</span>
        </div>
        <template v-else>
          <div class="metric-total">
            <div class="total-label">Total Ingress</div>
            <div v-if="!flowMetrics" class="total-value unavailable">Unavailable</div>
            <div v-else-if="!hasIngress" class="total-value no-data-value">No data</div>
            <span v-else class="total-value">{{ formatBytes(ingressTotal) }}</span>
          </div>
          <div v-if="filteredIngress.length > 0" class="breakdown-entries">
            <div v-for="[key, value] in filteredIngress" :key="key" class="breakdown-entry">
              <span class="entry-key">{{ key }}</span>
              <span class="entry-value">{{ formatBytes(value) }}</span>
            </div>
          </div>
        </template>
      </div>

      <!-- Egress Metrics -->
      <div v-if="viewMode === 'egress'" class="flow-metrics mb-3">
        <div v-if="!flowMetricsLoaded" class="loading-metrics">
          <i class="pi pi-spin pi-spinner" />
          <span>Loading metrics...</span>
        </div>
        <template v-else>
          <div class="metric-total">
            <div class="total-label">Total Egress</div>
            <div v-if="!flowMetrics" class="total-value unavailable">Unavailable</div>
            <div v-else-if="!hasEgress" class="total-value no-data-value">No data</div>
            <span v-else class="total-value">{{ formatBytes(egressTotal) }}</span>
          </div>
          <div v-if="filteredEgress.length > 0" class="breakdown-entries">
            <div v-for="[key, value] in filteredEgress" :key="key" class="breakdown-entry">
              <span class="entry-key">{{ key }}</span>
              <span class="entry-value">{{ formatBytes(value) }}</span>
            </div>
          </div>
        </template>
      </div>

      <!-- Storage Metrics -->
      <div v-if="viewMode === 'storage'" class="mb-3">
        <div v-if="!flowMetricsLoaded" class="loading-metrics">
          <i class="pi pi-spin pi-spinner" />
          <span>Loading metrics...</span>
        </div>
        <template v-else>
          <div v-if="!flowMetrics" class="no-data unavailable">Unavailable</div>
          <div v-else-if="!hasStorage" class="no-data">No data</div>
          <div v-else class="metrics-grid" :class="{ 'single-column': storageEntries.length === 1 }">
            <div v-for="[key, value] in storageEntries" :key="key" class="metric">
              <div class="metric-label">{{ key }}</div>
              <div class="metric-value">{{ formatBytes(value) }}</div>
            </div>
            <div v-if="storageEntries.length > 1" class="metric total-metric">
              <div class="metric-label">Total</div>
              <div class="metric-value">{{ formatBytes(storageTotal) }}</div>
            </div>
          </div>
        </template>
      </div>

      <!-- Deleted Metrics -->
      <div v-if="viewMode === 'deleted'" class="flow-metrics mb-3">
        <div v-if="!flowMetricsLoaded" class="loading-metrics">
          <i class="pi pi-spin pi-spinner" />
          <span>Loading metrics...</span>
        </div>
        <template v-else>
          <div class="metric-total">
            <div class="total-label">Total Deleted</div>
            <div v-if="!flowMetrics" class="total-value unavailable">Unavailable</div>
            <div v-else-if="!hasDeleted" class="total-value no-data-value">No data</div>
            <span v-else class="total-value">{{ formatBytes(deletedTotal) }}</span>
          </div>
          <div v-if="filteredDeleted.length > 0" class="breakdown-entries">
            <div v-for="[key, value] in filteredDeleted" :key="key" class="breakdown-entry">
              <span class="entry-key">{{ key }}</span>
              <span class="entry-value">{{ formatBytes(value) }}</span>
            </div>
          </div>
        </template>
      </div>

      <!-- Connection error message -->
      <div v-if="member.connectionError" class="connection-error mb-3">
        <i class="pi pi-exclamation-circle error-icon" />
        <div class="error-content">
          <div class="error-text">{{ parsedError.message }}</div>
          <div v-if="parsedError.url" class="error-url" v-tooltip.bottom="parsedError.url">{{ parsedError.urlDisplay }}</div>
        </div>
      </div>

      <!-- Last updated -->
      <div class="last-updated">
        Last updated: <Timestamp :timestamp="member.lastUpdated" :showTimeAgo="true" />
      </div>

      <!-- Status Details Dialog -->
      <CheckDialog ref="checkDialog" :header="member.memberName + ' System Status Details'" :checks="member.status?.checks" :last-updated="member.status?.timestamp" :readonly="true" />
    </div>
  </Panel>

</template>

<script setup lang="ts">
import { ref, computed } from "vue";
import type { MemberStatus, FlowMetrics } from "@/composables/useLeaderDashboard";
import Panel from "primevue/panel";
import Tag from "primevue/tag";
import Timestamp from "@/components/Timestamp.vue";
import CheckDialog from "@/components/CheckDialog.vue";

interface Props {
  member: MemberStatus;
  viewMode?: string;
  flowMetrics?: FlowMetrics;
  flowMetricsLoaded?: boolean;
  metricFilter?: string;
}

const props = withDefaults(defineProps<Props>(), {
  viewMode: "summary",
  flowMetricsLoaded: false,
  metricFilter: "",
});
const detailsVisible = ref(false);
const checkDialog = ref<InstanceType<typeof CheckDialog> | null>(null);

const healthSeverity = computed(() => {
  if (!props.member.status) return "secondary";
  const code = props.member.status.code;
  if (code === 0) return "success";
  if (code === 1) return "warning";
  return "danger";
});

const healthIcon = computed(() => {
  if (!props.member.status) return "pi pi-question";
  const code = props.member.status.code;
  if (code === 0) return "pi pi-check";
  if (code === 1) return "pi pi-exclamation-triangle";
  return "pi pi-times";
});

const showDetails = () => {
  if (checkDialog.value) {
    checkDialog.value.show();
  }
};

const openSite = () => {
  window.open(props.member.url, "_blank");
};

const formatAge = (timestamp: string) => {
  const now = Date.now();
  const then = new Date(timestamp).getTime();
  const diffMs = now - then;
  const diffSec = Math.floor(diffMs / 1000);
  const diffMin = Math.floor(diffSec / 60);
  const diffHour = Math.floor(diffMin / 60);
  const diffDay = Math.floor(diffHour / 24);

  if (diffDay > 0) return `${diffDay}d ago`;
  if (diffHour > 0) return `${diffHour}h ago`;
  if (diffMin > 0) return `${diffMin}m ago`;
  return `${diffSec}s ago`;
};

// Parse connection error to extract message and URL
const parsedError = computed(() => {
  const error = props.member.connectionError;
  if (!error) return { message: "", url: null, urlDisplay: null };

  // Match pattern like "HTTP 503: Service unavailable (https://...)"
  const match = error.match(/^(.+?)\s*\(((https?:\/\/[^)]+))\)$/);
  if (match) {
    const url = match[2];
    // Extract just the path for display
    try {
      const urlObj = new URL(url);
      return {
        message: match[1].trim(),
        url: url,
        urlDisplay: urlObj.pathname,
      };
    } catch {
      return { message: match[1].trim(), url: url, urlDisplay: url };
    }
  }

  return { message: error, url: null, urlDisplay: null };
});

// Flow metrics helpers
const hasIngress = computed(() => props.flowMetrics && Object.keys(props.flowMetrics.ingressByDataSource || {}).length > 0);
const hasEgress = computed(() => props.flowMetrics && Object.keys(props.flowMetrics.egressByDataSink || {}).length > 0);
const hasStorage = computed(() => props.flowMetrics && Object.keys(props.flowMetrics.storageByService || {}).length > 0);
const hasDeleted = computed(() => props.flowMetrics && Object.keys(props.flowMetrics.deletedByPolicy || {}).length > 0);

const sumValues = (obj: Record<string, number> | undefined): number => {
  if (!obj) return 0;
  return Object.values(obj).reduce((sum, val) => sum + val, 0);
};

const ingressTotal = computed(() => sumValues(props.flowMetrics?.ingressByDataSource));
const egressTotal = computed(() => sumValues(props.flowMetrics?.egressByDataSink));
const storageTotal = computed(() => sumValues(props.flowMetrics?.storageByService));
const storageEntries = computed(() => Object.entries(props.flowMetrics?.storageByService || {}).sort((a, b) => a[0].localeCompare(b[0])));
const deletedTotal = computed(() => sumValues(props.flowMetrics?.deletedByPolicy));

// Filtered entries based on metricFilter
const filterEntries = (obj: Record<string, number> | undefined): [string, number][] => {
  if (!obj) return [];
  const filter = props.metricFilter.toLowerCase();
  return Object.entries(obj).filter(([key]) => key.toLowerCase().includes(filter));
};

const filteredIngress = computed(() => filterEntries(props.flowMetrics?.ingressByDataSource));
const filteredEgress = computed(() => filterEntries(props.flowMetrics?.egressByDataSink));
const filteredStorage = computed(() => filterEntries(props.flowMetrics?.storageByService));
const filteredDeleted = computed(() => filterEntries(props.flowMetrics?.deletedByPolicy));

const formatBytes = (bytes: number | undefined): string => {
  if (bytes === undefined || bytes === null) return "—";
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
</script>

<style scoped>
.member-status-card {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.header-title {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.member-name {
  font-weight: 600;
}

.member-version {
  font-size: 0.875rem;
  color: var(--text-color-secondary);
  font-weight: normal;
}

.cursor-pointer {
  cursor: pointer;
}

.member-status-badge {
  display: flex;
  gap: 0.5rem;
  align-items: center;
}

.member-card-content {
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.connection-error {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
  padding: 0.75rem;
  background: var(--red-50);
  border: 1px solid var(--red-200);
  border-radius: 4px;
  color: var(--red-700);
}

.error-icon {
  font-size: 1rem;
  margin-top: 0.125rem;
  flex-shrink: 0;
}

.error-content {
  min-width: 0;
}

.error-text {
  font-size: 0.875rem;
  font-weight: 500;
  line-height: 1.3;
}

.error-url {
  font-size: 0.75rem;
  color: var(--red-400);
  margin-top: 0.25rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: help;
}

.member-tags {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
}

.tags-label {
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--text-color-secondary);
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 1rem;

  &.single-column {
    grid-template-columns: 1fr;
  }
}

.total-metric {
  grid-column: 1 / -1;
  background: var(--surface-100) !important;
}

.metric {
  text-align: center;
  padding: 1rem;
  background: var(--surface-50);
  border: 1px solid var(--surface-200);
  border-radius: 4px;
}

.metric-label {
  font-size: 0.875rem;
  color: var(--text-color-secondary);
  margin-bottom: 0.5rem;
}

.metric-value {
  font-size: 1.5rem;
  font-weight: bold;
  color: var(--text-color);
}

.metric-value.has-errors {
  color: var(--red-500);
}

.metric-value.has-cold-queue {
  color: var(--orange-500);
}

.metric-value.has-paused {
  color: var(--yellow-500);
}

.flow-metrics {
  font-size: 0.875rem;
  min-height: 120px;
}

.metric-breakdown {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.metric-total {
  text-align: center;
  padding: 0.5rem;
  margin-bottom: 0.5rem;
}

.total-label {
  font-size: 0.75rem;
  text-transform: uppercase;
  color: var(--text-color-secondary);
  margin-bottom: 0.25rem;
}

.total-value {
  font-size: 1.5rem;
  font-weight: bold;
  color: var(--text-color);

  &.unavailable {
    color: var(--orange-500);
    font-size: 1rem;
  }

  &.no-data-value {
    color: var(--text-color-secondary);
    font-size: 1rem;
  }
}

.breakdown-entries {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  max-height: 80px;
  overflow-y: auto;
  background: var(--surface-50);
  border: 1px solid var(--surface-200);
  border-radius: 4px;
  padding: 0.5rem;
}

.breakdown-entry {
  display: flex;
  justify-content: space-between;
  padding: 0.125rem 0;
  font-size: 0.8rem;
}

.entry-key {
  color: var(--text-color-secondary);
}

.entry-value {
  font-weight: 600;
  color: var(--text-color);
}

.no-data {
  text-align: center;
  color: var(--text-color-secondary);
  padding: 2rem 1rem;

  &.unavailable {
    color: var(--orange-500);
  }
}

.loading-metrics {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  padding: 2rem 1rem;
  color: var(--text-color-secondary);

  i {
    font-size: 1.5rem;
  }

  span {
    font-size: 0.875rem;
  }
}

.last-updated {
  font-size: 0.875rem;
  color: var(--text-color-secondary);
  text-align: center;
}

.panel-header-content {
  display: flex;
  align-items: center;
  gap: 0.6rem;
}

.header-icons {
  display: flex;
  align-items: center;
  gap: 0.6rem;
}

.header-icon {
  cursor: pointer;
  font-size: 0.875rem;
}

.header-icon:hover {
  color: var(--primary-color-text);
}
</style>
