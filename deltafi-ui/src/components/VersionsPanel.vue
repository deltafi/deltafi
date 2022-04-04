<template>
  <CollapsiblePanel :header="props.header" class="table-panel">
    <DataTable responsive-layout="scroll" :value="props.versions" striped-rows class="p-datatable-sm p-datatable-gridlines" :loading="props.loading">
      <template #empty>No version information available.</template>
      <template #loading>Loading version information. Please wait.</template>
      <Column field="app" header="App" :sortable="true" />
      <Column field="container" header="Container" :sortable="true" />
      <Column field="image.name" header="Image" :sortable="true" />
      <Column field="image.tag" header="Tag" :sortable="true" />
    </DataTable>
  </CollapsiblePanel>
</template>

<script setup>
import Column from "primevue/column";
import DataTable from "primevue/datatable";
import CollapsiblePanel from "@/components/CollapsiblePanel.vue";
import { defineProps } from "vue";

const props = defineProps({
  header: {
    type: String,
    required: true,
  },
  versions: {
    type: Array,
    required: true,
  },
  loading: {
    type: Boolean,
    required: false,
    default: false
  }
});
</script>
