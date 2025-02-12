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
        <Dropdown v-model="timeFrame" :options="timeFrames" option-label="name" style="min-width: 11rem;" />
      </div>
    </template>
    <div class="row">
      <div v-for="(panelId, header) in grafanaPanelIdMap" :key="panelId" :class="`col-6 chart`">
        <GrafanaChartWrapper :key="refreshKey" :header="header" :panel-id="panelId" :timeframe-minutes="timeFrame.minutes" />
      </div>
    </div>
  </Panel>
</template>

<script setup>
import GrafanaChartWrapper from "@/components/dashboard/GrafanaChartWrapper.vue";
import Dropdown from "primevue/dropdown";
import Panel from "primevue/panel";
import { ref, onBeforeMount, watch } from "vue";
import { useStorage, StorageSerializers } from "@vueuse/core";

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
const timeFrame = ref(timeFrames[0])

const stateStorageKey = "dashboard-metrics-panel-session-storage";
const state = useStorage(stateStorageKey, {}, sessionStorage, { serializer: StorageSerializers.object });
const getInitialDates = async () => timeFrame.value = state.value.timeFrame ? state.value.timeFrame : timeFrames[0];
const setPersistedParams = async () => state.value = { timeFrame: timeFrame.value };

const grafanaPanelIdMap = {
  "Bytes Ingressed by Data Source": 1,
  "Content Storage": 3,
  "Bytes Egressed by Data Sink": 2,
  "Content Removed": 4,
}

onBeforeMount(async () => {
  await getInitialDates();
  await setPersistedParams();
});

watch(timeFrame, () => { setPersistedParams() });
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
