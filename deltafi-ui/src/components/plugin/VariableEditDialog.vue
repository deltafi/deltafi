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
    <span @click="show()">
      <slot />
    </span>
    <Dialog v-model:visible="dialogVisible" :maximizable="false" :modal="true" :dismissable-mask="false" :draggable="false" :style="{ width: '30vw' }" @show="onShow">
      <template #header>
        <div class="p-dialog-title">
          Editing {{ localVariable.name }}
          <div class="subtitle">
            {{ localVariable.description }}
          </div>
        </div>
      </template>
      <Message v-for="error of errors" :key="error.message" severity="error">
        {{ error.message }}
      </Message>
      <ListEdit v-if="localVariable.dataType === 'LIST'" v-model="localVariable.value" />
      <MapEdit v-if="localVariable.dataType === 'MAP'" v-model="localVariable.value" />
      <InputText v-if="localVariable.dataType === 'STRING'" v-model="localVariable.value" style="width: 100%" />
      <InputNumber v-if="localVariable.dataType === 'NUMBER'" v-model="localVariable.value" style="width: 100%" />
      <span v-if="localVariable.dataType === 'BOOLEAN'">
        <Checkbox v-model="localVariable.value" :binary="true" />
        {{ localVariable.value }}
      </span>
      <template #footer>
        <div class="d-flex">
          <span class="mr-auto">
            <Button v-if="overridden" v-tooltip.bottom="revertTooltip" label="Revert" icon="pi pi-replay" class="p-button-secondary p-button-outlined" :disabled="saving" @click="revert" />
          </span>
          <Button label="Cancel" icon="pi pi-times" class="p-button-text" @click="cancel" />
          <Button :label="saveBtnLabel" :icon="saveBtnIcon" :disabled="saving" @click="onSaveClick" />
        </div>
      </template>
    </Dialog>
  </div>
</template>

<script setup>
import { computed, inject, toRefs, ref } from "vue";

import Button from "primevue/button";
import Dialog from "primevue/dialog";
import InputText from "primevue/inputtext";
import InputNumber from "primevue/inputnumber";
import Checkbox from "primevue/checkbox";
import Message from "primevue/message";

import ListEdit from "@/components/plugin/ListEdit.vue";
import MapEdit from "@/components/plugin/MapEdit.vue";

import usePlugins from "@/composables/usePlugins";
import useNotifications from "@/composables/useNotifications";

const hasPermission = inject("hasPermission");

const props = defineProps({
  pluginCoordinatesProp: {
    type: Object,
    required: true,
  },
  variableProp: {
    type: Object,
    required: true,
  },
});

const { pluginCoordinatesProp: pluginCoordinates, variableProp: variable } = toRefs(props);
const errors = ref([]);
const notify = useNotifications();
const { setPluginVariableValues } = usePlugins();
const emit = defineEmits(["saved"]);
const localVariable = ref({});
const dialogVisible = ref(false);
const saving = ref(false);

const saveBtnLabel = computed(() => {
  return saving.value ? "Saving" : "Save";
});

const saveBtnIcon = computed(() => {
  return saving.value ? "pi pi-spin pi-spinner" : "pi pi-check";
});

const stringValue = computed(() => {
  return localVariable.value.value === null ? null : localVariable.value.value.toString();
});

const overridden = computed(() => {
  return variable.value.value !== null && variable.value.value !== variable.value.defaultValue;
});

const revertTooltip = computed(() => {
  return `Revert to default value: ${variable.value.defaultValue}`;
});

const show = () => {
  if (hasPermission("PluginVariableUpdate")) {
    errors.value = [];
    dialogVisible.value = true;
  } else {
    notify.warn("Access Denied", "You are not authorized to perform this action");
  }
};

const hide = () => {
  localVariable.value = {};
  dialogVisible.value = false;
};

const onSaveClick = () => {
  if (!saving.value) save();
};

const save = async (setNull = false) => {
  saving.value = true;
  await setPluginVariableValues({
    pluginCoordinates: pluginCoordinates.value,
    variables: {
      key: localVariable.value.name,
      value: setNull ? null : stringValue.value,
    },
  });
  emit("saved");
  notify.success("Plugin variable updated successfully", localVariable.value.name);
  saving.value = false;
  hide();
};

const cancel = () => {
  hide();
};

const revert = () => {
  save(true);
};

const onShow = () => {
  localVariable.value = JSON.parse(JSON.stringify(variable.value));

  if (localVariable.value.value === null && localVariable.value.defaultValue !== null) localVariable.value.value = localVariable.value.defaultValue;

  if (localVariable.value.dataType === "BOOLEAN" && localVariable.value.value !== null) localVariable.value.value = Boolean(localVariable.value.value);

  if (localVariable.value.dataType === "NUMBER" && localVariable.value.value !== null) localVariable.value.value = Number(localVariable.value.value);
};
</script>

<style>
.subtitle {
  font-size: 0.75rem;
  font-weight: 300;
  color: var(--gray-700);
}
</style>
