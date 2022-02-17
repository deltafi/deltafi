<template>
  <div class="queue-metrics">
    <PageHeader heading="Queue Metrics" />
    <div class="row">
      <div class="col-12">
        <CollapsiblePanel header="Queues" class="table-panel">
          <DataTable responsive-layout="scroll" :value="queueMetrics" striped-rows class="p-datatable-sm p-datatable-gridlines" sort-field="name" :sort-order="1" :loading="showLoading">
            <template #empty>No action queue metrics available.</template>
            <template #loading>Loading action queue metrics data. Please wait.</template>
            <Column header="Queue Name" field="name" :sortable="true" />
            <Column header="Queue Size" field="size" :sortable="true" class="metric-column" />
            <Column header="Timestamp" field="timestamp" :sortable="true" class="metric-column" />
          </DataTable>
        </CollapsiblePanel>
      </div>
    </div>
  </div>
</template>

<script setup>
import Column from "primevue/column";
import DataTable from "primevue/datatable";
import CollapsiblePanel from "@/components/CollapsiblePanel.vue";
import PageHeader from "@/components/PageHeader.vue";
import useQueueMetrics from "@/composables/useQueueMetrics";
import { computed, onMounted, onUnmounted } from "vue";

const refreshInterval = 5000; // 5 seconds
const { data: queueMetrics, loaded, loading, fetch: fetchQueueMetrics } = useQueueMetrics();
const showLoading = computed(() => !loaded.value && loading.value);

let autoRefresh = null;

onMounted(() => {
  fetchQueueMetrics();
  autoRefresh = setInterval(fetchQueueMetrics, refreshInterval);
});

onUnmounted(() => {
  clearInterval(autoRefresh);
});
</script>
