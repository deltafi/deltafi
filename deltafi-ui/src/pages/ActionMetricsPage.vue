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
  <div class="action-metrics-page">
    <PageHeader heading="Action Metrics" />
    <span v-if="hasErrors">
      <Message v-for="error in errors" :key="error" :closable="false" severity="error">{{ error }}</Message>
    </span>
    <div v-else-if="actionMetrics">
      <CollapsiblePanel header="Actions" class="action-metrics-panel table-panel mb-3">
        <template #icons>
          <Dropdown v-model="ingressFlowNameSelected" placeholder="Select a Flow" :options="ingressFlowNames" show-clear :editable="false" class="deltafi-input-field mr-3" @change="flowChange" />
          <Dropdown v-model="timeRange" :options="timeRanges" option-label="name" placeholder="Time Range" class="deltafi-input-field mr-3" @change="timeRangeChange" />
        </template>
        <ActionMetricsTable :actions="actionMetricsUngrouped" :loading="!loaded" class="mb-3" @pause-timer="onPauseTimer" />
      </CollapsiblePanel>
      <CollapsiblePanel header="Queues" class="queue-metrics-panel table-panel mb-3">
        <QueueMetricsTable />
      </CollapsiblePanel>
    </div>
    <ProgressBar v-else mode="indeterminate" style="height: 0.5em" />
  </div>
</template>

<script setup>
import ActionMetricsTable from "@/components/ActionMetricsTable.vue";
import CollapsiblePanel from "@/components/CollapsiblePanel.vue";
import PageHeader from "@/components/PageHeader.vue";
import ProgressBar from "@/components/deprecatedPrimeVue/ProgressBar";
import QueueMetricsTable from "@/components/QueueMetricsTable.vue";
import useActionMetrics from "@/composables/useActionMetrics";
import useFlows from "@/composables/useFlows";
import { computed, inject, onMounted, onUnmounted, ref } from "vue";

import Dropdown from "primevue/dropdown";
import Message from "primevue/message";

const refreshInterval = 5000; // 5 seconds
const { data: actionMetrics, fetch: getActionMetrics, errors, loaded, loading, actionMetricsUngrouped } = useActionMetrics();
const { ingressFlows: ingressFlowNames, fetchIngressFlowNames } = useFlows();
const isIdle = inject("isIdle");

fetchIngressFlowNames();

const timeRanges = [
  { name: "Last 5 minutes", code: "5m" },
  { name: "Last 15 minutes", code: "15m" },
  { name: "Last 30 minutes", code: "30m" },
  { name: "Last 1 hour", code: "1h" },
  { name: "Last 24 hours", code: "24h" },
  { name: "Last 3 days", code: "3d" },
  { name: "Last 5 days", code: "5d" },
  { name: "Last 7 days", code: "7d" },
  { name: "Last 14 days", code: "14d" },
];
const ingressFlowNameSelected = ref(null);
const timeRange = ref(timeRanges[0]);

const hasErrors = computed(() => {
  return errors.value.length > 0;
});

let autoRefresh = null;
onUnmounted(() => {
  clearInterval(autoRefresh);
});

onMounted(async () => {
  await fetchActionMetrics();
  autoRefresh = setInterval(fetchActionMetrics, refreshInterval);
});

const onPauseTimer = (value) => {
  if (value) {
    clearInterval(autoRefresh);
  } else {
    autoRefresh = setInterval(fetchActionMetrics, refreshInterval);
  }
};

const fetchActionMetrics = async () => {
  if (!isIdle.value && !loading.value) {
    let actionMetricsParams = { last: timeRange.value.code };
    if (ingressFlowNameSelected.value) {
      actionMetricsParams["flowName"] = ingressFlowNameSelected.value;
    }
    await getActionMetrics(actionMetricsParams);
  }
};

const timeRangeChange = async () => {
  loaded.value = false;
  await fetchActionMetrics();
};

const flowChange = async () => {
  loaded.value = false;
  await fetchActionMetrics();
};
</script>

<style lang="scss">
@import "@/styles/pages/action-metrics-page.scss";
</style>
