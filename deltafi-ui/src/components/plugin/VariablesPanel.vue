<!--
   DeltaFi - Data transformation and enrichment platform

   Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>

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
  <CollapsiblePanel header="Variables" class="table-panel">
    <DataTable :value="props.variables" responsive-layout="scroll" class="p-datatable-sm p-datatable-gridlines plugin-variables-table" striped-rows :row-hover="true">
      <template #empty> No Variables Included </template>
      <Column field="name" header="Name">
        <template #body="{ data }">
          {{ data.name }}
          <i v-if="data.description" v-tooltip.right="data.description" class="ml-1 text-muted fas fa-info-circle fa-fw" />
        </template>
      </Column>
      <Column field="value" header="Value" class="value-column">
        <template #body="{ data }">
          <PluginVariableEditDialog :variable="data" @saved="$emit('updated')">
            <div class="value-clickable">
              <span v-if="data.value !== null && data.value !== data.defaultValue" class="override-icon">
                <i v-tooltip.left="'Default value has been overridden'" class="fas fa fa-gavel mr-2 text-muted"></i>
              </span>
              <span v-if="data.value == null">
                <span v-if="data.defaultValue == null"><em>null</em></span>
                <span v-else-if="['MAP', 'LIST'].includes(data.dataType)">
                  <div v-for="item in viewList(data.defaultValue)" :key="item" class="list-item">{{ item }}</div>
                </span>
                <span v-else>{{ data.defaultValue }}</span>
              </span>
              <span v-else-if="data.value === ''"><em>empty string</em></span>
              <span v-else-if="['MAP', 'LIST'].includes(data.dataType)">
                <div v-for="item in viewList(data.value)" :key="item" class="list-item">{{ item }}</div>
              </span>
              <span v-else>{{ data.value }}</span>
            </div>
          </PluginVariableEditDialog>
        </template>
      </Column>
    </DataTable>
  </CollapsiblePanel>
</template>

<script setup>
import Column from "primevue/column";
import DataTable from "primevue/datatable";
import CollapsiblePanel from "@/components/CollapsiblePanel";
import PluginVariableEditDialog from "@/components/plugin/VariableEditDialog";
import { defineProps, defineEmits } from "vue";

defineEmits(["updated"]);

const props = defineProps({
  variables: {
    type: Object,
    required: true,
  }
});

const viewList = (value) => {
  return value.split(',').map((i) => i.trim());
}
</script>

<style lang="scss">
.plugin-variables-table {
  td.value-column {
    padding: 0 !important;
  }

  .value-clickable {
    cursor: pointer;
    padding: 0.5rem !important;
    width: 100%;
    display: flex;
  }

  .value-clickable>* {
    flex: 0 0 auto;
  }

  .override-icon {
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .list-item::before {
    content: '•';
    margin-right: 0.25rem;
    font-weight: bold;
  }
}
</style>
