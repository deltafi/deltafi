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
  <CollapsiblePanel header="Actions" class="table-panel">
    <DataTable :value="props.actions" responsive-layout="scroll" striped-rows class="p-datatable-sm p-datatable-gridlines" sort-field="name" :sort-order="1">
      <template #empty>No actions included</template>
      <Column field="name" header="Name" sortable></Column>
      <Column field="description" header="Description"></Column>
      <Column field="requiresDomains" header="Requires Domains">
        <template #body="{ data }">
          <span v-if="data.requiresDomains !== null">{{ data.requiresDomains.join(", ") }}</span>
        </template>
      </Column>
      <Column field="requiresEnrichments" header="Requires Enrichments">
        <template #body="{ data }">
          <span v-if="data.requiresEnrichments !== null">{{ data.requiresEnrichments.join(", ") }}</span>
        </template>
      </Column>
    </DataTable>
  </CollapsiblePanel>
</template>

<script setup>
import Column from "primevue/column";
import DataTable from "primevue/datatable";
import CollapsiblePanel from "@/components/CollapsiblePanel";
import { defineProps } from "vue";

const props = defineProps({
  actions: {
    type: Object,
    required: true,
  },
});
</script>
