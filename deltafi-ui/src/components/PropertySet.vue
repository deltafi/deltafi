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
  <div>
    <CollapsiblePanel :header="propertySet.displayName" class="mb-3 table-panel">
      <template #header>
        <span class="p-panel-title">
          {{ propertySet.displayName }}
          <i v-if="propertySet.description" v-tooltip.right="propertySet.description" class="ml-2 text-muted fas fa-info-circle fa-fw" />
        </span>
      </template>
      <DataTable responsive-layout="scroll" sort-field="key" :sort-order="1" :value="visibleProperties" edit-mode="cell" class="p-datatable-sm table-striped p-datatable-gridlines property-set-table" :row-hover="true" data-key="key">
        <template #empty> No properties in this property set. </template>
        <Column header="Key" field="key" :sortable="true" :style="{ width: '50%' }">
          <template #body="property">
            <span :class="{ 'text-muted': !$hasPermission('SystemPropertiesUpdate') }">{{ property.data.key }}</span>
            <i v-if="tooltipText(property.data)" v-tooltip.right="tooltipText(property.data)" class="ml-2 text-muted fas fa-info-circle fa-fw" />
          </template>
        </Column>
        <Column header="Value" field="value" :sortable="true" class="value-column" :style="{ width: '50%' }">
          <template #body="{ data }">
            <template v-if="!$hasPermission('SystemPropertiesUpdate')">
              <div>
                <span v-if="data.value !== data.defaultValue" class="override-icon">
                  <i v-tooltip.left="'Default value has been overridden'" class="fas fa fa-gavel mr-2 text-muted" />
                </span>
                <span v-if="data.value == null"><em>null</em></span>
                <span v-else-if="data.value === ''"><em>empty string</em></span>
                <span v-else>{{ data.value }}</span>
              </div>
            </template>
            <template v-else>
              <PropertyEditDialog :property="data" @saved="onSaved($event)">
                <div class="value-clickable">
                  <span v-if="data.value !== data.defaultValue" class="override-icon">
                    <i v-tooltip.left="'Default value has been overridden'" class="fas fa fa-gavel mr-2 text-muted" />
                  </span>
                  <span v-if="data.value == null"><em>null</em></span>
                  <span v-else-if="data.value === ''"><em>empty string</em></span>
                  <span v-else>{{ data.value }}</span>
                </div>
              </PropertyEditDialog>
            </template>
          </template>
        </Column>
      </DataTable>
    </CollapsiblePanel>
  </div>
</template>

<script setup>
import Column from "primevue/column";
import DataTable from "primevue/datatable";
import CollapsiblePanel from "@/components/CollapsiblePanel.vue";
import PropertyEditDialog from "@/components/properties/PropertyEditDialog.vue";
import { computed, inject, reactive } from "vue";
import _ from "lodash";

const hasPermission = inject("hasPermission");

const props = defineProps({
  propSet: {
    type: Object,
    required: true,
  },
});

const emit = defineEmits(["updated"]);

const propertySet = reactive(props.propSet);
const visibleProperties = computed(() => propertySet.properties.filter((p) => !p.hidden));

const tooltipText = (property) => {
  const parts = [];
  if (property.description) parts.push(property.description);
  if (!hasPermission("SystemPropertiesUpdate")) parts.push("(Read-only)");
  return parts.join(" ");
};

const onSaved = (property) => {
  _.find(propertySet.properties, { key: property.key }).value = property.value;
  emit("updated");
};
</script>

<style>
.property-set-table {
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
}
</style>
