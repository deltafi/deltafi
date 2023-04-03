<!--
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>

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
  <div class="system-metrics-page">
    <PageHeader heading="System Metrics" />
    <Panel header="Nodes" class="table-panel">
      <DataTable v-model:expandedRows="expandedRows" :value="nodes" data-key="name" responsive-layout="scroll" striped-rows class="p-datatable-gridlines p-datatable-sm node-table" :loading="showLoading" loading-icon="p-datatable-loading-icon pi-spin" sort-field="name" :sort-order="1">
        <template #empty>No System Metrics available</template>
        <template #loading>Loading System Metrics Data. Please wait.</template>
        <Column class="expander-column" :expander="true" />
        <Column header="Node Name" field="name" :sortable="true" />
        <Column header="App Count" class="apps-column">
          <template #body="node">{{ node.data.apps.length }}</template>
        </Column>
        <Column header="CPU" field="resources.cpu.usage" :sortable="true" class="resource-column">
          <template #body="node">
            <ProgressBar v-tooltip.top="node.data.resources.cpu.usage + 'm / ' + node.data.resources.cpu.limit + 'm'" :value="calculatePercent(node.data.resources.cpu.usage, node.data.resources.cpu.limit)" />
          </template>
        </Column>
        <Column header="Memory" field="resources.memory.usage" :sortable="true" class="resource-column">
          <template #body="node">
            <ProgressBar v-tooltip.top="formattedBytes(node.data.resources.memory.usage) + ' / ' + formattedBytes(node.data.resources.memory.limit)" :value="calculatePercent(node.data.resources.memory.usage, node.data.resources.memory.limit)">{{ formattedBytes(node.data.resources.memory.usage) }} ({{ calculatePercent(node.data.resources.memory.usage, node.data.resources.memory.limit) }}%)</ProgressBar>
          </template>
        </Column>
        <Column header="Disk" field="resources.disk.usage" :sortable="true" class="resource-column">
          <template #body="node">
            <ProgressBar v-tooltip.top="formattedBytes(node.data.resources.disk.usage) + ' / ' + formattedBytes(node.data.resources.disk.limit)" :value="calculatePercent(node.data.resources.disk.usage, node.data.resources.disk.limit)">{{ formattedBytes(node.data.resources.disk.usage) }} ({{ calculatePercent(node.data.resources.disk.usage, node.data.resources.disk.limit) }}%)</ProgressBar>
          </template>
        </Column>
        <template #expansion="node">
          <div class="apps-subtable">
            <DataTable :value="node.data.apps" responsive-layout="scroll" class="app-table" sort-field="name" :sort-order="1">
              <Column header="App Name" field="name" :sortable="true" />
            </DataTable>
          </div>
        </template>
      </DataTable>
    </Panel>
  </div>
</template>

<script setup>
import PageHeader from "@/components/PageHeader.vue";
import ProgressBar from "@/components/deprecatedPrimeVue/ProgressBar";
import useSystemMetrics from "@/composables/useSystemMetrics";
import useUtilFunctions from "@/composables/useUtilFunctions";
import { computed, inject, onMounted, onUnmounted, ref } from "vue";

import Column from "primevue/column";
import DataTable from "primevue/datatable";
import Panel from "primevue/panel";

const { formattedBytes } = useUtilFunctions();
const refreshInterval = 5000; // 5 seconds
const expandedRows = ref([]);
const { data: nodes, loaded, loading, fetch: fetchSystemMetrics } = useSystemMetrics();
const showLoading = computed(() => !loaded.value && loading.value);
const isIdle = inject("isIdle");
let autoRefresh = null;

onMounted(() => {
  fetchSystemMetrics();
  autoRefresh = setInterval(() => {
    if (!isIdle.value && !loading.value) {
      fetchSystemMetrics();
    }
  }, refreshInterval);
});

onUnmounted(() => {
  clearInterval(autoRefresh);
});

const calculatePercent = (numerator, denominator) => {
  return Math.round((numerator / denominator) * 100);
};

</script>

<style lang="scss">
@import "@/styles/pages/system-metrics-page.scss";
</style>
