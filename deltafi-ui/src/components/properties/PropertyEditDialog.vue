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
    <Dialog v-model:visible="dialogVisible" :maximizable="false" :modal="true" :dismissable-mask="false" :closable="false" :draggable="false" :style="{ width: '30vw' }" @show="onShow" @keydown.esc="hide">
      <template #header>
        <div class="p-dialog-title">
          Editing {{ localProperty.key }}
          <div class="subtitle">
            {{ localProperty.description }}
          </div>
        </div>
      </template>
      <ListEdit v-if="localProperty.dataType === 'LIST'" v-model="localProperty.value" />
      <MapEdit v-if="localProperty.dataType === 'MAP'" v-model="localProperty.value" />
      <span v-if="localProperty.dataType === 'STRING'">
        <TextArea ref="textAreaEl" v-if="textArea" v-model="localProperty.value" style="width: 100%" rows="5" />
        <InputText ref="inputTextEl" v-else v-model="localProperty.value" style="width: 100%" />
      </span>
      <InputNumber v-if="localProperty.dataType === 'NUMBER'" v-model="localProperty.value" style="width: 100%" />
      <span v-if="localProperty.dataType === 'BOOLEAN'">
        <Checkbox v-model="localProperty.value" :binary="true" />
        {{ localProperty.value }}
      </span>
      <template #footer>
        <div class="d-flex">
          <span class="mr-auto">
            <Button v-if="overridden" v-tooltip.bottom="revertTooltip" label="Revert" icon="pi pi-replay" class="p-button-secondary p-button-outlined" :disabled="saving" @click="revert" />
          </span>
          <Button label="Cancel" icon="pi pi-times" class="p-button-text ml-2" @click="cancel" />
          <Button :label="saveBtnLabel" :icon="saveBtnIcon" :disabled="saving" class="ml-2" @click="onSaveClick" />
        </div>
      </template>
    </Dialog>
  </div>
</template>

<script setup>
import ListEdit from "@/components/plugin/ListEdit.vue";
import MapEdit from "@/components/plugin/MapEdit.vue";
import useNotifications from "@/composables/useNotifications";
import usePlugins from "@/composables/usePlugins";
import usePropertySets from "@/composables/usePropertySets";
import { computed, inject, toRefs, ref, watch, nextTick } from "vue";

import _ from "lodash";

import Button from "primevue/button";
import Checkbox from "primevue/checkbox";
import Dialog from "primevue/dialog";
import InputNumber from "primevue/inputnumber";
import InputText from "primevue/inputtext";
import TextArea from "primevue/textarea";

const { setPluginVariableValues } = usePlugins();
const { update, reset } = usePropertySets();

const props = defineProps({
  pluginCoordinatesProp: {
    type: Object,
    required: false,
    default: null,
  },
  property: {
    type: Object,
    required: true,
  },
});

const { pluginCoordinatesProp: pluginCoordinates, property } = toRefs(props);
const notify = useNotifications();
const emit = defineEmits(["saved"]);
const localProperty = ref({});
const dialogVisible = ref(false);
const saving = ref(false);
const textAreaThreshold = 48;
const textAreaEl = ref(null);
const inputTextEl = ref(null);

const textArea = computed(() => localProperty.value.value && localProperty.value.value.length > textAreaThreshold);
const saveBtnLabel = computed(() => (saving.value ? "Saving" : "Save"));
const saveBtnIcon = computed(() => (saving.value ? "pi pi-spin pi-spinner" : "pi pi-check"));
const overridden = computed(() => property.value.value !== null && property.value.value !== property.value.defaultValue);
const revertTooltip = computed(() => `Revert to default value: ${property.value.defaultValue}`);

watch(
  () => localProperty.value.value,
  () => {
    setFocus();
  }
);

const setFocus = async () => {
  await nextTick();
  if (textArea.value) {
    if (textAreaEl.value) {
      textAreaEl.value.$el.focus();
    }
  } else {
    if (inputTextEl.value) {
      inputTextEl.value.$el.focus();
    }
  }
};

const show = () => {
  dialogVisible.value = true;
};

const hide = () => {
  localProperty.value = {};
  dialogVisible.value = false;
};

const onSaveClick = () => {
  if (!saving.value) save();
};

const updateValue = computed(() => {
  return localProperty.value.value === null ? null : localProperty.value.value.toString();
});

const save = async (setNull = false) => {
  saving.value = true;
  let response = null;
  let updateSuccessful = false;

  if (pluginCoordinates.value) {
    response = await setPluginVariableValues({
      pluginCoordinates: pluginCoordinates.value,
      variables: {
        key: localProperty.value.name,
        value: setNull ? null : updateValue.value,
      },
    });
    updateSuccessful = response.setPluginVariableValues;
  } else {
    response = await update([
      {
        key: localProperty.value.key,
        value: setNull ? null : updateValue.value,
      },
    ]);
    updateSuccessful = response.updateProperties;
  }

  if (!updateSuccessful) {
    localProperty.value.value = localProperty.value.defaultValue;
    notify.error(`${pluginCoordinates.value ? "Plugin variable" : "System Property"} updated failed. Reverting to default value.`, `${pluginCoordinates.value ? localProperty.value.name : localProperty.value.key}`);
    saving.value = false;
  } else {
    emit("saved", localProperty.value);
    notify.success(`${pluginCoordinates.value ? "Plugin variable" : "System Property"} updated successfully.`, `${pluginCoordinates.value ? localProperty.value.name : localProperty.value.key}`);
    saving.value = false;
    hide();
  }
};

const cancel = () => {
  hide();
};

const revert = async () => {
  saving.value = true;
  if (pluginCoordinates.value) {
    save(true);
  } else {
    await reset(localProperty.value.key);
    localProperty.value.value = localProperty.value.defaultValue;
    emit("saved", localProperty.value);
    notify.success("System Property updated successfully.", localProperty.value.key);
    saving.value = false;
    hide();
  }
};

const onShow = () => {
  localProperty.value = JSON.parse(JSON.stringify(property.value));

  if (localProperty.value.value === null && localProperty.value.defaultValue !== null) localProperty.value.value = localProperty.value.defaultValue;

  if (localProperty.value.dataType === "BOOLEAN" && localProperty.value.value !== null) {
    if (_.isString(localProperty.value.value)) {
      localProperty.value.value = String(localProperty.value.value).toLowerCase() === "true";
    }
    localProperty.value.value = Boolean(localProperty.value.value);
  }

  if (localProperty.value.dataType === "NUMBER" && localProperty.value.value !== null) localProperty.value.value = Number(localProperty.value.value);
  setFocus();
};
</script>

<style>
.subtitle {
  font-size: 0.75rem;
  font-weight: 300;
  color: var(--gray-700);
}
</style>
