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
  <div>
    <CollapsiblePanel :header="propertySet.displayName" class="property-set mb-3 table-panel">
      <template #header>
        <span class="p-panel-title">
          {{ propertySet.displayName }}
          <i v-if="propertySet.description" v-tooltip.right="propertySet.description" class="ml-2 text-muted fas fa-info-circle fa-fw" />
        </span>
      </template>
      <DataTable responsive-layout="scroll" sort-field="key" :sort-order="1" :value="visibleProperties" edit-mode="cell" class="p-datatable-sm table-striped p-datatable-gridlines" :row-hover="true" @cell-edit-complete="onCellEditComplete">
        <template #empty>No properties in this property set.</template>
        <Column header="Key" field="key" :sortable="true">
          <template #body="property">
            <span :class="{ 'text-muted': !$hasPermission('SystemPropertiesUpdate') }">{{ property.data.key }}</span>
            <i v-if="tooltipText(property.data)" v-tooltip.right="tooltipText(property.data)" class="ml-2 text-muted fas fa-info-circle fa-fw" />
          </template>
        </Column>
        <Column header="Value" field="value" :sortable="true" class="value-column">
          <template #body="property">
            <span :class="{ 'value-clickable': $hasPermission('SystemPropertiesUpdate') }">
              <span :class="{ 'text-muted': !$hasPermission('SystemPropertiesUpdate') }">{{ property.data.value }}</span>
            </span>
          </template>
          <template #editor="{ data, field }">
            <span v-if="!$hasPermission('SystemPropertiesUpdate')" class="text-muted">{{ data.value }}</span>
            <InputText v-else v-model="data[field]" class="p-inputtext-sm" autofocus />
          </template>
        </Column>
        <Column header="Source" field="propertySource" :sortable="true">
          <template #body="property">
            <span :class="{ 'text-muted': !$hasPermission('SystemPropertiesUpdate') }">{{ property.data.propertySource }}</span>
          </template>
        </Column>
      </DataTable>
    </CollapsiblePanel>
  </div>
</template>

<script setup>
import Column from "primevue/column";
import DataTable from "primevue/datatable";
import InputText from "primevue/inputtext";
import CollapsiblePanel from "@/components/CollapsiblePanel";
import useNotifications from "@/composables/useNotifications";
import usePropertySets from "@/composables/usePropertySets";
import { computed, defineProps, defineEmits, inject, reactive } from "vue";

const hasPermission = inject("hasPermission");

const props = defineProps({
  propSet: {
    type: Object,
    required: true,
  },
});

const emit = defineEmits(["updated"]);

const propertySet = reactive(props.propSet);
const notify = useNotifications();
const { data: propertySetData, update } = usePropertySets();
const visibleProperties = computed(() => propertySet.properties.filter((p) => !p.hidden));

const tooltipText = (property) => {
  let parts = [];
  if (property.description) parts.push(property.description);
  if (!hasPermission("SystemPropertiesUpdate")) parts.push("(Read-only)");
  return parts.join(" ");
};

const updateProperty = async (setId, key, value, refreshable) => {
  await update([
    {
      propertySetId: setId,
      key: key,
      value: value,
    },
  ]);
  if (propertySetData.value.updateProperties > 0) {
    emit("updated");
    if (refreshable) {
      notify.success("Property update successful", key);
    } else {
      notify.warn("Property update successful", "System restart required for change to take effect!");
    }
  } else {
    notify.error("Property update failed");
  }
};

const onCellEditComplete = (event) => {
  let { data, newValue, value } = event;
  if (value !== newValue) {
    updateProperty(propertySet.id, data.key, newValue, data.refreshable);
    data.value = newValue;
  }
};
</script>

<style lang="scss">
.property-set {
  td.value-column {
    padding: 0 !important;

    > span {
      padding: 0.5rem !important;
    }

    .value-clickable {
      cursor: pointer;
      width: 100%;
      display: flex;
    }

    .value-clickable > * {
      flex: 0 0 auto;
    }
  }
}
</style>
