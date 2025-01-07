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
        <Panel header="Queues" class="table-panel">
          <DataTable responsive-layout="scroll" :value="queueMetrics" striped-rows row-hover class="p-datatable-sm p-datatable-gridlines" sort-field="name" :sort-order="1" :loading="showLoading" data-key="name">
            <template #empty>No action queue metrics available.</template>
            <template #loading>Loading action queue metrics data. Please wait.</template>
            <Column header="Queue Name" field="name" :sortable="true" />
            <Column header="Queue Size" field="size" :sortable="true" class="metric-column" />
            <Column header="Last Updated" field="timestamp" :sortable="true" class="timestamp-column">
              <template #body="row">
                <Timestamp :timestamp="row.data.timestamp" />
              </template>
            </Column>
          </DataTable>
        </Panel>
      </div>
    </div>
  </div>
</template>

<script setup>
import PageHeader from "@/components/PageHeader.vue";
import Timestamp from "@/components/Timestamp.vue";
import useQueueMetrics from "@/composables/useQueueMetrics";
import { computed, onMounted, onUnmounted, inject } from "vue";

import Column from "primevue/column";
import DataTable from "primevue/datatable";
import Panel from "primevue/panel";

const refreshInterval = 5000; // 5 seconds
const { data: queueMetrics, loaded, loading, fetch: fetchQueueMetrics } = useQueueMetrics();
const showLoading = computed(() => !loaded.value && loading.value);
const isIdle = inject("isIdle");

let autoRefresh = null;

onMounted(() => {
  fetchQueueMetrics();
  autoRefresh = setInterval(() => {
    if (!isIdle.value && !loading.value) {
      fetchQueueMetrics();
    }
  }, refreshInterval);
});

onUnmounted(() => {
  clearInterval(autoRefresh);
});
</script>

<style lang="scss">
@import "@/styles/pages/queue-metrics-page.scss";
</style>
