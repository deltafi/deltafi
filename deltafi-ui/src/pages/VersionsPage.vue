<template>
  <div>
    <PageHeader heading="Versions" />
    <CollapsiblePanel header="Image Versions" class="table-panel">
      <DataTable responsive-layout="scroll" :value="versions" striped-rows class="p-datatable-sm p-datatable-gridlines" :loading="showLoading">
        <template #empty>No version information available.</template>
        <template #loading>Loading version information. Please wait.</template>
        <Column field="app" header="App" :sortable="true" />
        <Column field="container" header="Container" :sortable="true" />
        <Column field="image.name" header="Image" :sortable="true" />
        <Column field="image.tag" header="Tag" :sortable="true" />
      </DataTable>
    </CollapsiblePanel>
  </div>
</template>

<script setup>
import Column from "primevue/column";
import DataTable from "primevue/datatable";
import CollapsiblePanel from "@/components/CollapsiblePanel.vue";
import PageHeader from "@/components/PageHeader.vue";
import useVersions from "@/composables/useVersions";
import { onMounted, computed, onUnmounted, inject } from "vue";

const refreshInterval = 5000; // 5 seconds
const { data: versions, loaded, loading, fetch: fetchVersions } = useVersions();
const isIdle = inject("isIdle");

let autoRefresh;

const showLoading = computed(() => !loaded.value && loading.value);

onMounted(() => {
  fetchVersions();
  autoRefresh = setInterval(() => {
    if (!isIdle.value) {
      fetchVersions();
    }
  }, refreshInterval);
});

onUnmounted(() => {
  clearInterval(autoRefresh);
});
</script>
