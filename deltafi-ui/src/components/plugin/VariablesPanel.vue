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
  <CollapsiblePanel v-if="filteredVariables.length" :header="header" class="table-panel">
    <DataTable aria-label="Plugin variables" :value="filteredVariables" class="p-datatable-sm p-datatable-gridlines plugin-variables-table" striped-rows :row-hover="true" sort-field="name" :sort-order="1" data-key="name">
      <template #empty>
        No variables included
      </template>
      <Column field="name" header="Name" :style="{ width: '50%' }" sortable>
        <template #body="{ data }">
          {{ data.name }}
          <i v-if="data.description" v-tooltip.right="data.description" class="ml-2 text-muted fas fa-info-circle fa-fw" />
        </template>
      </Column>
      <Column field="value" header="Value" class="value-column" :style="{ width: '50%' }">
        <template #body="{ data }">
          <template v-if="(!$hasPermission('PluginVariableUpdate'))">
            <div class="value-readonly">
              <span v-if="data.value !== null && data.value !== data.defaultValue" class="override-icon">
                <i v-tooltip.left="'Default value has been overridden'" class="fas fa fa-gavel mr-2 text-muted" />
              </span>
              <template v-if="data.value == null">
                <span v-if="data.defaultValue == null"><em>null</em></span>
                <template v-else-if="COLLECTION_TYPES.includes(data.dataType)">
                  <Chip v-for="item in viewList(data.defaultValue)" :key="item" v-tooltip.bottom="item.length > 20 ? item : undefined" :label="item" class="mr-1 mb-1 truncate-chip" />
                </template>
                <span v-else>{{ data.defaultValue }}</span>
              </template>
              <span v-else-if="data.value === ''"><em>empty string</em></span>
              <template v-else-if="COLLECTION_TYPES.includes(data.dataType)">
                <Chip v-for="item in viewList(data.value)" :key="item" v-tooltip.bottom="item.length > 20 ? item : undefined" :label="item" class="mr-1 mb-1 truncate-chip" />
              </template>
              <span v-else>{{ data.value }}</span>
            </div>
          </template>
          <template v-else>
            <PropertyEditDialog :plugin-coordinates-prop="pluginCoordinates" :property="data" @saved="$emit('updated')">
              <div class="value-clickable">
                <span v-if="data.value !== null && data.value !== data.defaultValue" class="override-icon">
                  <i v-tooltip.left="'Default value has been overridden'" class="fas fa fa-gavel mr-2 text-muted" />
                </span>
                <template v-if="data.value == null">
                  <span v-if="data.defaultValue == null"><em>null</em></span>
                  <template v-else-if="COLLECTION_TYPES.includes(data.dataType)">
                    <Chip v-for="item in viewList(data.defaultValue)" :key="item" v-tooltip.bottom="item.length > 20 ? item : undefined" :label="item" class="mr-1 mb-1 truncate-chip" />
                  </template>
                  <span v-else>{{ data.defaultValue }}</span>
                </template>
                <span v-else-if="data.value === ''"><em>empty string</em></span>
                <template v-else-if="COLLECTION_TYPES.includes(data.dataType)">
                  <Chip v-for="item in viewList(data.value)" :key="item" v-tooltip.bottom="item.length > 20 ? item : undefined" :label="item" class="mr-1 mb-1 truncate-chip" />
                </template>
                <span v-else>{{ data.value }}</span>
              </div>
            </PropertyEditDialog>
          </template>
        </template>
      </Column>
    </DataTable>
  </CollapsiblePanel>
</template>

<script setup>
import Chip from "primevue/chip";
import Column from "primevue/column";
import DataTable from "primevue/datatable";
import CollapsiblePanel from "@/components/CollapsiblePanel.vue";
import PropertyEditDialog from "@/components/properties/PropertyEditDialog.vue";
import { splitEscaped, COLLECTION_TYPES } from "@/utils/collectionValues";
import { computed, toRefs } from "vue";

defineEmits(["updated"]);

const props = defineProps({
  headerProp: {
    type: String,
    required: false,
    default: "Variables",
  },
  pluginCoordinatesProp: {
    type: Object,
    required: true,
  },
  variablesProp: {
    type: Object,
    required: true,
  },
  filterTextProp: {
    type: String,
    default: "",
  },
});

const { headerProp: header, pluginCoordinatesProp: pluginCoordinates, variablesProp: variables, filterTextProp: filterText } = toRefs(props);

const filteredVariables = computed(() => {
  if (!filterText.value) return variables.value;
  const search = filterText.value.toLowerCase();
  return variables.value.filter((v) => v.name?.toLowerCase().includes(search) || v.value?.toLowerCase().includes(search));
});

const viewList = splitEscaped;
</script>

<style>
.plugin-variables-table {
  td.value-column {
    padding: 0 !important;
  }

  .value-readonly {
    padding: 0.5rem !important;
    width: 100%;
    display: flex;
    flex-wrap: wrap;
  }

  .value-clickable {
    cursor: pointer;
    padding: 0.5rem !important;
    width: 100%;
    display: flex;
    flex-wrap: wrap;
  }

  .value-clickable>* {
    flex: 0 0 auto;
  }

  .override-icon {
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .truncate-chip {
    max-width: 200px;
    overflow: hidden;

    .p-chip-label {
      min-width: 0;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }
}
</style>
