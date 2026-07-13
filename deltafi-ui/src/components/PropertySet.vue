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
  <div>
    <CollapsiblePanel :header="propertySet.displayName" class="mb-3 table-panel">
      <template #header>
        <span class="p-panel-title">
          {{ propertySet.displayName }}
          <i v-if="propertySet.description" v-tooltip.right="propertySet.description" class="ml-2 text-muted fas fa-info-circle fa-fw" />
        </span>
      </template>
      <DataTable aria-label="System properties" sort-field="key" :sort-order="1" :value="visibleProperties"
          edit-mode="cell" class="p-datatable-sm p-datatable-gridlines property-set-table" :table-style="{ 'table-layout': 'fixed', width: '100%' }" striped-rows
          :row-hover="true" data-key="key">
        <template #empty> No properties in this property set. </template>
        <Column header="Key" field="key" :sortable="true" :style="{ width: '50%' }">
          <template #body="property">
            <span :class="{ 'text-muted': !$hasPermission('SystemPropertiesUpdate') || property.data.editable === false }">{{ property.data.key }}</span>
            <i v-if="tooltipText(property.data)" v-tooltip.right="tooltipText(property.data)" class="ml-2 text-muted fas fa-info-circle fa-fw" />
          </template>
        </Column>
        <Column field="value" header="Value" class="value-column" :style="{ width: '50%' }">
          <template #body="{ data }">
            <template v-if="!$hasPermission('SystemPropertiesUpdate') || data.editable === false">
              <div class="value-disabled">
                <span v-if="data.value !== data.defaultValue">
                  <i v-tooltip.left="'Default value has been overridden'" class="fas fa fa-gavel mr-2 text-muted" />
                </span>
                <span v-if="data.value == null || data.value === ''"></span>
                <template v-else-if="COLLECTION_TYPES.includes(data.dataType)">
                  <Chip v-for="item in JSON.parse(data.value)" :key="item" v-tooltip.bottom="item.length > 20 ? item : undefined" :label="item" class="mr-1 mb-1 truncate-chip" />
                </template>
                <span v-else class="value-text">{{ data.value }}</span>
              </div>
            </template>
            <template v-else>
              <div v-if="data.dataType === 'BOOLEAN'" class="d-flex align-items-center p-2">
                <span v-if="data.value !== data.defaultValue">
                  <i v-tooltip.right="'Default value has been overridden'" class="fas fa fa-gavel mr-2 text-muted" />
                </span>
                <SystemPropertySwitch :property-name="data.key" @changed="onSaved($event)" />
              </div>
              <PropertyEditDialog v-else :property="data" @saved="onSaved($event)">
                <div class="value-clickable">
                  <span v-if="data.value !== data.defaultValue">
                    <i v-tooltip.left="'Default value has been overridden'" class="fas fa fa-gavel mr-2 text-muted" />
                  </span>
                  <span v-if="data.value == null || data.value === ''"></span>
                  <template v-else-if="data.dataType === 'MAP'">
                    <Chip v-for="item in splitEscaped(data.value)" :key="item" v-tooltip.bottom="item.length > 20 ? item : undefined" :label="item" class="mr-1 mb-1 truncate-chip" />
                  </template>
                  <template v-else-if="COLLECTION_TYPES.includes(data.dataType)">
                    <Chip v-for="item in JSON.parse(data.value)" :key="item" v-tooltip.bottom="item.length > 20 ? item : undefined" :label="item" class="mr-1 mb-1 truncate-chip" />
                  </template>
                  <span v-else class="value-text">{{ data.value }}</span>
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
import Chip from "primevue/chip";
import Column from "primevue/column";
import DataTable from "primevue/datatable";
import CollapsiblePanel from "@/components/CollapsiblePanel.vue";
import SystemPropertySwitch from "@/components/SystemPropertySwitch.vue";
import PropertyEditDialog from "@/components/properties/PropertyEditDialog.vue";
import { splitEscaped, COLLECTION_TYPES } from "@/utils/collectionValues";
import { computed, inject, reactive } from "vue";
import _ from "lodash";

const hasPermission = inject("hasPermission");

const props = defineProps({
  propSet: {
    type: Object,
    required: true,
  },
  filterText: {
    type: String,
    default: "",
  },
});

const emit = defineEmits(["updated"]);

const propertySet = reactive(props.propSet);
const visibleProperties = computed(() => {
  let filtered = propertySet.properties.filter((p) => !p.hidden);
  if (props.filterText) {
    const search = props.filterText.toLowerCase();
    filtered = filtered.filter((p) => p.key?.toLowerCase().includes(search) || p.value?.toLowerCase().includes(search));
  }
  return filtered;
});

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
  td {
    overflow-wrap: anywhere;
    word-break: break-word;
  }

  td.value-column {
    padding: 0 !important;
  }

  .value-disabled {
    cursor: default;
    padding: 0.5rem !important;
    width: 100%;
    display: flex;
    flex-wrap: wrap;
    align-items: center;
  }

  .value-clickable {
    cursor: pointer;
    padding: 0.5rem !important;
    width: 100%;
    display: flex;
    flex-wrap: wrap;
    align-items: center;
  }

  .value-clickable>* {
    flex: 0 0 auto;
  }

  .value-text {
    flex: 1 1 auto;
    min-width: 0;
    overflow-wrap: anywhere;
    word-break: break-word;
    white-space: normal;
  }

  .truncate-chip {
    max-width: 200px;
    overflow: hidden;
    background: var(--surface-hover);
    border: 1px solid var(--surface-border);

    .p-chip-label {
      min-width: 0;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }
}
</style>
