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
  <Panel header="Metrics" class="metrics-panel pb-3">
    <template #icons>
      <div class="time-range btn-toolbar mb-2 mb-md-0">
        <Dropdown v-model="timeFrame" :options="timeFrames" option-label="name" style="min-width: 11rem" class="ml-3" />
        <Dropdown v-model="node" :options="optionsDropdown" style="min-width: 11rem" class="ml-3" />
        <Button class="p-button p-button-outlined ml-3" icon="fa fa-sync-alt" label="Refresh" @click="refreshDashboard" />
      </div>
    </template>
    <div class="row">
      <div v-for="(panelId, header) in grafanaPanelIdMap" :key="panelId" :class="`col-6 chart`">
        <GrafanaChartWrapper :key="refreshKey" :header="header" :panel-id="panelId" :key-pairs="keyPairs" :refresh-minutes="5" :timeframe-minutes="timeFrame.minutes" :dashboard-id="uiChartID" />
      </div>
    </div>
  </Panel>
</template>

<script setup>
import GrafanaChartWrapper from "@/components/dashboard/GrafanaChartWrapper.vue";
import Button from "primevue/button";
import Dropdown from "primevue/dropdown";
import Panel from "primevue/panel";
import { ref, onBeforeMount, watch, computed } from "vue";
import { useStorage, StorageSerializers } from "@vueuse/core";
import useSystemMetrics from "@/composables/useSystemMetrics";

const { data: nodes, fetch: fetchSystemMetrics } = useSystemMetrics();
const uiChartID = computed(() => (node.value == "All" ? "ui-charts" : "ui-charts-by-node"));
const refreshDashboard = () => (refreshKey.value += 1);
const refreshKey = ref(0);

const timeFrames = [
  { name: "Last Hour", minutes: 60 },
  { name: "Last 4 Hours", minutes: 60 * 4 },
  { name: "Last 8 Hours", minutes: 60 * 8 },
  { name: "Last 12 Hours", minutes: 60 * 12 },
  { name: "Last 24 Hours", minutes: 60 * 24 },
  { name: "Last 3 Days", minutes: 60 * 24 * 3 },
  { name: "Last 7 Days", minutes: 60 * 24 * 7 },
  { name: "Last 14 Days", minutes: 60 * 24 * 14 },
];

const keyPairs = computed(() => {
  if (node.value !== null) {
    return { "var-hostName": node.value };
  } else {
    return { "var-hostName": "" };
  }
});

const timeFrame = ref(timeFrames[0]);
const node = ref("All");

const stateStorageKey = "dashboard-metrics-panel-session-storage";
const state = useStorage(stateStorageKey, {}, sessionStorage, { serializer: StorageSerializers.object });
const getInitialDates = async () => (timeFrame.value = state.value.timeFrame ? state.value.timeFrame : timeFrames[0]);
const setPersistedParams = async () => (state.value = { timeFrame: timeFrame.value });

const grafanaPanelIdMap = {
  "CPU utilization over time": 40,
  "RAM utilization over time": 30,
  "App CPU utilization over time": 10,
  "App RAM utilization over time": 50,
  "Disk utilization over time": 20,
};

onBeforeMount(async () => {
  await fetchSystemMetrics();
  await getInitialDates();
  await setPersistedParams();
});

const getHostNames = computed(() => {
  return nodes.value.map((node) => node.name).sort();
});

const optionsDropdown = computed(() => {
  const options = getHostNames.value;
  options.unshift("All");
  return options;
});

watch(timeFrame, () => {
  setPersistedParams();
});
</script>

<style>
.metrics-panel {
  .p-panel-content {
    padding-bottom: 0.25rem !important;
    padding-top: 0.25rem !important;
  }

  .p-panel-header {
    height: 44px !important;
  }

  .row {
    padding: 0 0.4rem;

    .chart {
      padding: 0.5rem;
    }
  }
}
</style>
