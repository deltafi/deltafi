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
    <DataTable aria-label="Plugin variables" :value="filteredVariables" class="p-datatable-sm p-datatable-gridlines plugin-variables-table" :table-style="{ 'table-layout': 'fixed', width: '100%' }" striped-rows :row-hover="true" sort-field="name" :sort-order="1" data-key="name">
      <template #empty>
        No variables included
      </template>
      <Column field="name" header="Name" :style="{ width: '50%' }" sortable>
        <template #body="{ data }">
          <span>{{ data.name }}<span v-if="data.required" class="required-indicator" aria-hidden="true">*</span></span>
          <i v-if="data.masked" v-tooltip.right="'Sensitive value — stored masked'" class="ml-2 text-muted fas fa-lock fa-fw" />
          <i v-if="data.description" v-tooltip.right="data.description" class="ml-2 text-muted fas fa-info-circle fa-fw" />
        </template>
      </Column>
      <Column field="value" header="Value" class="value-column" :style="{ width: '50%' }">
        <template #body="{ data }">
          <template v-if="!$hasPermission('PluginVariableUpdate')">
            <div class="value-disabled">
              <span v-if="data.value !== null && data.value !== data.defaultValue">
                <i v-tooltip.left="'Default value has been overridden'" class="fas fa fa-gavel mr-2 text-muted" />
              </span>
              <template v-if="data.value == null">
                <span v-if="data.defaultValue == null"></span>
                <template v-else-if="COLLECTION_TYPES.includes(data.dataType)">
                  <Chip v-for="item in splitEscaped(data.defaultValue)" :key="item" v-tooltip.bottom="item.length > 20 ? item : undefined" :label="item" class="mr-1 mb-1 truncate-chip" />
                </template>
                <span v-else class="value-text">{{ data.defaultValue }}</span>
              </template>
              <div v-else-if="data.dataType === 'BOOLEAN'" class="d-flex align-items-center p-2">
                <ToggleSwitch v-tooltip="data.description" v-model="toggleSwitchModels[data.name]" :disabled />
              </div>
              <template v-else-if="COLLECTION_TYPES.includes(data.dataType)">
                <Chip v-for="item in splitEscaped(data.value)" :key="item" v-tooltip.bottom="item.length > 20 ? item : undefined" :label="item" class="mr-1 mb-1 truncate-chip" />
              </template>
              <span v-else class="value-text">{{ data.value }}</span>
            </div>
          </template>
          <template v-else>
            <div v-if="data.value === null">
              <div v-if="data.dataType === 'BOOLEAN'" class="d-flex align-items-center p-2">
                <ToggleSwitch v-tooltip="data.description" v-model="toggleSwitchModels[data.name]" @value-change="(value) => save(data, value)" />
              </div>
              <PropertyEditDialog v-else :plugin-coordinates-prop="pluginCoordinates" :property="data" @saved="$emit('updated')">
                <div class="value-clickable">
                  <span v-if="data.defaultValue === null"></span>
                  <template v-else-if="COLLECTION_TYPES.includes(data.dataType)">
                    <Chip v-for="item in splitEscaped(data.defaultValue)" :key="item" v-tooltip.bottom="item.length > 20 ? item : undefined" :label="item" class="mr-1 mb-1 truncate-chip" />
                  </template>
                  <span v-else class="value-text">{{ data.defaultValue }}</span>
                </div>
              </PropertyEditDialog>
            </div>
            <div v-else>
              <div v-if="data.dataType === 'BOOLEAN'" class="d-flex align-items-center p-2">
                <span v-if="data.value !== data.defaultValue">
                  <i v-tooltip.left="'Default value has been overridden'" class="fas fa fa-gavel mr-2 text-muted" />
                </span>
                <ToggleSwitch v-tooltip="data.description" v-model="toggleSwitchModels[data.name]" @value-change="(value) => save(data, value)" />
              </div>
              <PropertyEditDialog v-else :plugin-coordinates-prop="pluginCoordinates" :property="data" @saved="$emit('updated')">
                <div class="value-clickable">
                  <div v-if="data.value !== data.defaultValue">
                    <i v-tooltip.left="'Default value has been overridden'" class="fas fa fa-gavel mr-2 text-muted" />
                  </div>
                  <template v-if="COLLECTION_TYPES.includes(data.dataType)">
                    <Chip v-for="item in splitEscaped(data.value)" :key="item" v-tooltip.bottom="item.length > 20 ? item : undefined" :label="item" class="mr-1 mb-1 truncate-chip" />
                  </template>
                  <span v-else class="value-text">{{ data.value }}</span>
                </div>
              </PropertyEditDialog>
            </div>
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
import ToggleSwitch from "primevue/toggleswitch";
import CollapsiblePanel from "@/components/CollapsiblePanel.vue";
import PropertyEditDialog from "@/components/properties/PropertyEditDialog.vue";
import { splitEscaped, COLLECTION_TYPES } from "@/utils/collectionValues";
import { computed, onMounted, ref, toRefs } from "vue";

import useNotifications from "@/composables/useNotifications";
import usePlugins from "@/composables/usePlugins";

const { setPluginVariableValues, errors: pluginErrors } = usePlugins();
const notify = useNotifications();

const emit = defineEmits(["updated"]);

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

const toggleSwitchModels = {};

onMounted(async () => {
  for (const variableIndex in variables.value) {
    const variableJson = variables.value[variableIndex];
    if (variableJson.dataType === 'BOOLEAN') {
      toggleSwitchModels[variableJson.name] = ref(JSON.parse(variableJson.value !== null ? variableJson.value : variableJson.defaultValue));
    }
  }
});

const filteredVariables = computed(() => {
  if (!filterText.value) {
    return variables.value;
  }
  const search = filterText.value.toLowerCase();
  return variables.value.filter((v) => v.name?.toLowerCase().includes(search) || v.value?.toLowerCase().includes(search));
});

const save = async (variable, value) => {
  const response = await setPluginVariableValues({
    pluginCoordinates: pluginCoordinates.value,
    variables: {
      key: variable.name,
      value: value.toString() === variable.defaultValue ? null : `${value}`,
    },
  });
  const updateSuccessful = response?.setPluginVariableValues;
  if (!updateSuccessful) {
    if (pluginErrors.value?.length) {
      const errorMsg = pluginErrors.value.map(e => e.message || e).join(', ');
      notify.error(`Plugin variable update failed: ${errorMsg}`, `${variable.name}`);
    } else {
      // No errors but updateSuccessful is false means no changes were made
      emit("updated");
    }
  } else {
    emit("updated");
    notify.success(`Plugin variable updated successfully.`, `${variable.name}`);
  }
};
</script>

<style>
.plugin-variables-table {
  .required-indicator {
    color: var(--red-500);
    margin-left: 0.125rem;
  }

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
