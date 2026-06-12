<!--
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>

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
  <CollapsiblePanel header="Actions" class="table-panel">
    <DataTable aria-label="Plugin actions" :value="props.actions" striped-rows class="p-datatable-sm p-datatable-gridlines action-rows" sort-field="name" :sort-order="1" data-key="name" :row-hover="true" @row-click="openDetails($event.data)">
      <template #empty>
        No actions included
      </template>
      <Column field="name" header="Name" sortable />
      <Column field="actionOptions.description" header="Description" />
      <Column class="details-col">
        <template #body>
          <i class="fas fa-chevron-right text-muted" />
        </template>
      </Column>
    </DataTable>
  </CollapsiblePanel>
  <ActionDocsDrawer v-model:visible="detailsVisible" :action="selectedAction" />
</template>

<script setup>
import { ref } from "vue";
import Column from "primevue/column";
import DataTable from "primevue/datatable";
import CollapsiblePanel from "@/components/CollapsiblePanel.vue";
import ActionDocsDrawer from "@/components/actionDocs/ActionDocsDrawer.vue";

const props = defineProps({
  actions: {
    type: Object,
    required: true,
  },
});

const detailsVisible = ref(false);
const selectedAction = ref(null);

const openDetails = (action) => {
  selectedAction.value = action;
  detailsVisible.value = true;
};
</script>

<style scoped>
.action-rows :deep(tbody tr) {
  cursor: pointer;
}
.details-col {
  width: 2rem;
  text-align: right;
}
</style>
