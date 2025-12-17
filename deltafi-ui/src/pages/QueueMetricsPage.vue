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
  <div class="queue-metrics-page">
    <PageHeader heading="Queue Metrics" />

    <div class="row">
      <div class="col-12">
        <Panel header="Action Queues" class="table-panel">
          <template #header>
            <div class="d-flex justify-content-between align-items-center w-100">
              <span>Action Queues</span>
              <div class="d-flex align-items-center">
                <span style="font-size: 0.875rem; color: var(--text-color-secondary); margin-right: 12px;">Group by Action Class</span>
                <InputSwitch v-model="rollupByClass" />
              </div>
            </div>
          </template>
          <DataTable
            responsive-layout="scroll"
            :value="mergedQueues"
            striped-rows
            row-hover
            class="p-datatable-sm p-datatable-gridlines"
            :sort-field="rollupByClass ? 'actionClass' : 'flowName'"
            :sort-order="1"
            :loading="showLoading"
          >
            <template #empty>
              No queued actions.
            </template>
            <template #loading>
              Loading queue metrics. Please wait.
            </template>
            <Column v-if="!rollupByClass" header="Flow" field="flowName" :sortable="true" />
            <Column v-if="!rollupByClass" header="Type" field="flowType" :sortable="true" class="type-column" />
            <Column v-if="!rollupByClass" header="Action" field="actionName" :sortable="true" />
            <Column header="Action Class" field="actionClass" :sortable="true" class="action-class-column">
              <template #body="row">
                <span v-tooltip.top="getPluginTooltip(row.data.actionClass)">{{ row.data.actionClass }}</span>
              </template>
            </Column>
            <Column header="Warm" field="warmCount" :sortable="true" class="metric-column">
              <template #body="row">
                <span :class="{ 'text-muted': row.data.warmCount === 0 }">{{ row.data.warmCount }}</span>
              </template>
            </Column>
            <Column header="Cold" field="coldCount" :sortable="true" class="metric-column">
              <template #body="row">
                <span :class="{ 'text-muted': row.data.coldCount === 0 }">
                  {{ row.data.coldCount }}
                </span>
              </template>
            </Column>
            <Column header="Total" field="totalCount" :sortable="true" class="metric-column">
              <template #body="row">
                <strong>{{ row.data.totalCount }}</strong>
              </template>
            </Column>
            <Column header="Oldest" field="oldestQueuedAt" :sortable="true" class="timestamp-column">
              <template #body="row">
                <Timestamp v-if="row.data.oldestQueuedAt" :timestamp="row.data.oldestQueuedAt" show-time-ago />
                <span v-else class="text-muted">-</span>
              </template>
            </Column>
          </DataTable>
        </Panel>
      </div>
    </div>

    <!-- Running Tasks -->
    <div class="row mt-3">
      <div class="col-12">
        <Panel
          :toggleable="true"
          :collapsed="runningTasksCollapsed"
          @update:collapsed="runningTasksCollapsed = $event"
          class="table-panel running-tasks-panel"
        >
          <template #header>
            <div class="clickable-header" @click="runningTasksCollapsed = !runningTasksCollapsed">
              {{ runningTasksHeader }}
            </div>
          </template>
          <DataTable
            responsive-layout="scroll"
            :value="runningTasks?.tasks || []"
            striped-rows
            row-hover
            class="p-datatable-sm p-datatable-gridlines"
            sort-field="runningForMs"
            :sort-order="-1"
          >
            <template #empty>
              No tasks currently running.
            </template>
            <Column header="DID" field="did" class="did-column">
              <template #body="row">
                <router-link :to="{ path: '/deltafile/viewer/' + row.data.did }">
                  {{ row.data.did.substring(0, 8) }}...
                </router-link>
              </template>
            </Column>
            <Column header="Action Class" field="actionClass" :sortable="true" class="action-class-column">
              <template #body="row">
                <span v-tooltip.top="getPluginTooltip(row.data.actionClass)">{{ row.data.actionClass }}</span>
              </template>
            </Column>
            <Column header="Action" field="actionName" :sortable="true" />
            <Column header="Duration" field="runningForMs" :sortable="true" class="duration-column">
              <template #body="row">
                <span :class="{ 'text-warning': row.data.runningForMs > 300000 }">
                  {{ formatDuration(row.data.runningForMs) }}
                </span>
              </template>
            </Column>
            <Column header="Started" field="startTime" :sortable="true" class="timestamp-column">
              <template #body="row">
                <Timestamp :timestamp="row.data.startTime" show-time-ago />
              </template>
            </Column>
            <Column header="Worker" field="appName" :sortable="true" />
          </DataTable>
        </Panel>
      </div>
    </div>

    <!-- Last Updated -->
    <div v-if="queueMetrics?.timestamp" class="row mt-2">
      <div class="col-12 text-muted text-end">
        Last updated: <Timestamp :timestamp="queueMetrics.timestamp" />
      </div>
    </div>
  </div>
</template>

<script setup>
import PageHeader from "@/components/PageHeader.vue";
import Timestamp from "@/components/Timestamp.vue";
import useQueueMetrics, { useActionPlugins, useRunningTasks } from "@/composables/useQueueMetrics";
import { computed, onMounted, onUnmounted, inject, ref } from "vue";

import Column from "primevue/column";
import DataTable from "primevue/datatable";
import InputSwitch from "primevue/inputswitch";
import Panel from "primevue/panel";

const refreshInterval = 5000; // 5 seconds
const { data: queueMetrics, loaded, loading, fetch: fetchQueueMetrics } = useQueueMetrics();
const { data: actionPlugins, fetch: fetchActionPlugins } = useActionPlugins();
const { data: runningTasks, fetch: fetchRunningTasks } = useRunningTasks();
const showLoading = computed(() => !loaded.value && loading.value);
const isIdle = inject("isIdle");
const rollupByClass = ref(false);
const runningTasksCollapsed = ref(false);

const runningTasksHeader = computed(() => {
  const count = runningTasks.value?.tasks?.length || 0;
  return `Running Tasks (${count})`;
});

const getPluginTooltip = (actionClass) => {
  if (!actionPlugins.value) return null;
  const plugin = actionPlugins.value[actionClass];
  return plugin ? `Plugin: ${plugin}` : null;
};

const formatDuration = (ms) => {
  const seconds = Math.floor(ms / 1000);
  const minutes = Math.floor(seconds / 60);
  const hours = Math.floor(minutes / 60);

  if (hours > 0) {
    return `${hours}h ${minutes % 60}m`;
  } else if (minutes > 0) {
    return `${minutes}m ${seconds % 60}s`;
  } else {
    return `${seconds}s`;
  }
};

const mergedQueues = computed(() => {
  if (!queueMetrics.value) return [];

  const warmQueues = queueMetrics.value.warmQueues || [];
  const coldQueues = queueMetrics.value.coldQueues || [];

  const queueMap = new Map();

  if (rollupByClass.value) {
    // Aggregate by actionClass only
    for (const warm of warmQueues) {
      const key = warm.actionClass;
      const existing = queueMap.get(key);
      if (existing) {
        existing.warmCount += warm.count;
        existing.totalCount += warm.count;
        if (warm.oldestQueuedAt && (!existing.oldestQueuedAt || warm.oldestQueuedAt < existing.oldestQueuedAt)) {
          existing.oldestQueuedAt = warm.oldestQueuedAt;
        }
      } else {
        queueMap.set(key, {
          actionClass: warm.actionClass,
          warmCount: warm.count,
          coldCount: 0,
          totalCount: warm.count,
          oldestQueuedAt: warm.oldestQueuedAt,
        });
      }
    }

    for (const cold of coldQueues) {
      const key = cold.actionClass;
      const existing = queueMap.get(key);
      if (existing) {
        existing.coldCount += cold.count;
        existing.totalCount += cold.count;
        if (cold.oldestQueuedAt && (!existing.oldestQueuedAt || cold.oldestQueuedAt < existing.oldestQueuedAt)) {
          existing.oldestQueuedAt = cold.oldestQueuedAt;
        }
      } else {
        queueMap.set(key, {
          actionClass: cold.actionClass,
          warmCount: 0,
          coldCount: cold.count,
          totalCount: cold.count,
          oldestQueuedAt: cold.oldestQueuedAt,
        });
      }
    }
  } else {
    // Create a map keyed by (flowName, actionName, actionClass)
    for (const warm of warmQueues) {
      const key = `${warm.flowName}|${warm.actionName}|${warm.actionClass}`;
      queueMap.set(key, {
        flowName: warm.flowName,
        flowType: warm.flowType,
        actionName: warm.actionName,
        actionClass: warm.actionClass,
        warmCount: warm.count,
        coldCount: 0,
        totalCount: warm.count,
        oldestQueuedAt: warm.oldestQueuedAt,
      });
    }

    for (const cold of coldQueues) {
      const key = `${cold.flowName}|${cold.actionName}|${cold.actionClass}`;
      const existing = queueMap.get(key);
      if (existing) {
        existing.coldCount = cold.count;
        existing.totalCount = existing.warmCount + cold.count;
        if (cold.oldestQueuedAt && (!existing.oldestQueuedAt || cold.oldestQueuedAt < existing.oldestQueuedAt)) {
          existing.oldestQueuedAt = cold.oldestQueuedAt;
        }
      } else {
        queueMap.set(key, {
          flowName: cold.flowName,
          flowType: cold.flowType,
          actionName: cold.actionName,
          actionClass: cold.actionClass,
          warmCount: 0,
          coldCount: cold.count,
          totalCount: cold.count,
          oldestQueuedAt: cold.oldestQueuedAt,
        });
      }
    }
  }

  return Array.from(queueMap.values());
});

let autoRefresh = null;

onMounted(() => {
  fetchQueueMetrics();
  fetchActionPlugins();
  fetchRunningTasks();
  autoRefresh = setInterval(() => {
    if (!isIdle.value && !loading.value) {
      fetchQueueMetrics();
      fetchRunningTasks();
    }
  }, refreshInterval);
});

onUnmounted(() => {
  clearInterval(autoRefresh);
});
</script>

<style>
.queue-metrics-page {
  .action-class-column {
    max-width: 300px;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .type-column {
    width: 120px;
  }

  .metric-column {
    text-align: right;
    width: 80px;
  }

  .timestamp-column {
    width: 180px;
  }

  .did-column {
    width: 100px;
  }

  .duration-column {
    width: 100px;
    text-align: right;
  }

  .running-tasks-panel .clickable-header {
    cursor: pointer;
    flex-grow: 1;
    padding: 0.5rem 0;
  }

  .toggle-label {
    font-size: 0.875rem;
    color: var(--text-color-secondary);
    white-space: nowrap;
    margin-right: 0.75rem;
  }

  .p-panel-header {
    height: 44px !important;
    padding: 0 1.25rem !important;
    font-weight: 600;
  }
}
</style>
