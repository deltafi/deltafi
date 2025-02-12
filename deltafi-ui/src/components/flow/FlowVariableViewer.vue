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
  <div class="flow-variable-viewer">
    <CollapsiblePanel :header="header" class="table-panel mb-3">
      <DataTable :value="variables" responsive-layout="scroll" class="p-datatable-sm p-datatable-gridlines" striped-rows data-key="name">
        <template #empty>
          No Variables Included
        </template>
        <Column field="name" header="Name" />
        <Column field="value" header="Value" />
        <Column field="description" header="Description" />
        <Column field="defaultValue" header="Default Value" />
        <Column field="dataType" header="Data Type" />
      </DataTable>
    </CollapsiblePanel>
  </div>
</template>

<script setup>
import CollapsiblePanel from "@/components/CollapsiblePanel.vue";
import { reactive } from "vue";

import DataTable from "primevue/datatable";
import Column from "primevue/column";

const props = defineProps({
  variables: {
    type: Object,
    required: true,
  },
  header: {
    type: String,
    required: true,
  },
});

const { header, variables } = reactive(props);
</script>
