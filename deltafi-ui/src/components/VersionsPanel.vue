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
  <CollapsiblePanel :header="props.header" class="table-panel">
    <DataTable responsive-layout="scroll" :value="props.versions" striped-rows class="p-datatable-sm p-datatable-gridlines" :loading="props.loading" data-key="app">
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
    default: false,
  },
});
</script>
