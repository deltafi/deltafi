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
    <DataTable :value="props.variables" responsive-layout="scroll" edit-mode="cell" class="p-datatable-sm p-datatable-gridlines" striped-rows @cell-edit-complete="onCellEditComplete">
      <template #empty> No Variables Included </template>
      <Column field="name" header="Name"></Column>
      <Column field="value" header="Value">
        <template #editor="{ data, field }">
          <InputText v-model="data[field]" autofocus />
        </template>
      </Column>
      <Column field="description" header="Description"></Column>
      <Column field="defaultValue" header="Default Value"></Column>
      <Column field="dataType" header="Data Type"></Column>
    </DataTable>
  </CollapsiblePanel>
</template>

<script setup>
import Column from "primevue/column";
import DataTable from "primevue/datatable";
import InputText from "primevue/inputtext";
import CollapsiblePanel from "@/components/CollapsiblePanel";
import usePlugins from "@/composables/usePlugins";
import useNotifications from "@/composables/useNotifications";
import { defineProps, defineEmits } from "vue";

const { data: plugins, update: updatePlugins } = usePlugins();
const notify = useNotifications();
const emit = defineEmits(["updated"]);

const props = defineProps({
  variables: {
    type: Object,
    required: true,
  },
  pluginCoordinates: {
    type: Object,
    required: true,
  },
});

const updatePlugin = async (variable, value) => {
  await updatePlugins({
    pluginCoordinates: props.pluginCoordinates,
    variables: {
      key: variable,
      value: value,
    },
  });
  if (plugins.value.setPluginVariableValues) {
    emit("updated");
    notify.success("Plugin Variable update successful", variable);
  } else {
    notify.error("Plugin Variable update failed");
  }
};

const onCellEditComplete = (event) => {
  let { data, newValue, value } = event;
  if (value !== newValue) {
    updatePlugin(data.name, newValue);
    data.value = newValue;
  }
};
</script>
