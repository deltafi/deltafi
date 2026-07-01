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
    <span role="button" tabindex="0" class="edit-trigger" @click="show()" @keydown.enter="show()">
      <slot />
    </span>
    <Dialog v-model:visible="dialogVisible" :maximizable="false" :modal="true" :dismissable-mask="false" :closable="false" :draggable="false" :style="{ width: '30vw', minWidth: '400px' }" :aria-label="'Editing ' + localProperty.key" @show="onShow" @keydown.esc="hide">
      <template #header>
        <div class="p-dialog-title">
          Editing {{ localProperty.key }}
          <div class="subtitle">
            {{ localProperty.description }}
          </div>
        </div>
      </template>
      <div ref="inputContainer">
        <ListEdit v-if="localProperty.dataType === 'LIST' || localProperty.dataType === 'SET'" v-model="localProperty.value" />
        <MapEdit v-if="localProperty.dataType === 'MAP'" v-model="localProperty.value" />
        <span v-if="localProperty.dataType === 'STRING'">
          <Password v-if="localProperty.masked" v-model="localProperty.value" :feedback="false" toggle-mask input-style="width: 100%" style="width: 100%" placeholder="Enter a new value" />
          <small v-if="localProperty.masked" class="d-block text-muted mt-1">This value is sensitive. Leave blank to keep the current value.</small>
          <TextArea v-else-if="textArea" v-model="localProperty.value" style="width: 100%" rows="5" />
          <InputText v-else v-model="localProperty.value" style="width: 100%" @paste="onPaste" />
        </span>
        <InputNumber v-if="localProperty.dataType === 'NUMBER'" v-model="localProperty.value" style="width: 100%" />
        <span v-if="localProperty.dataType === 'BOOLEAN'">
          <Checkbox v-model="localProperty.value" :binary="true" aria-label="Property value" />
          {{ localProperty.value }}
        </span>
      </div>
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
import { splitEscaped, escapeCommas } from "@/utils/collectionValues";
import { computed, toRefs, ref, watch, nextTick } from "vue";

import _ from "lodash";

import Button from "primevue/button";
import Checkbox from "primevue/checkbox";
import Dialog from "primevue/dialog";
import InputNumber from "primevue/inputnumber";
import InputText from "primevue/inputtext";
import Password from "primevue/password";
import TextArea from "primevue/textarea";

const { setPluginVariableValues, errors: pluginErrors } = usePlugins();
const { update, reset, errors: propertyErrors } = usePropertySets();

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
const inputContainer = ref(null);

const textArea = computed(() => localProperty.value.value && localProperty.value.value.length > textAreaThreshold);
const saveBtnLabel = computed(() => (saving.value ? "Saving" : "Save"));
const saveBtnIcon = computed(() => (saving.value ? "pi pi-spin pi-spinner" : "pi pi-check"));
const overridden = computed(() => property.value.value !== null && property.value.value !== property.value.defaultValue);
const revertTooltip = computed(() => `Revert to default value: ${property.value.defaultValue}`);

watch(
  () => localProperty.value.value,
  () => {
    // Only auto-focus for simple types, not LIST/MAP which manage their own focus
    if (localProperty.value.dataType !== "LIST" && localProperty.value.dataType !== "SET" && localProperty.value.dataType !== "MAP") {
      setFocus();
    }
  }
);

const setFocus = async () => {
  await nextTick();
  if (!inputContainer.value) return;
  const input = inputContainer.value.querySelectorAll('input, textarea')[0];
  if (input) input.focus();
};

const onPaste = (event) => {
  const pastedText = event.clipboardData.getData('text');
  if (pastedText.includes('\n')) {
    event.preventDefault();
    const input = event.target;
    const start = input.selectionStart;
    const end = input.selectionEnd;
    const currentValue = localProperty.value.value || '';
    localProperty.value.value = currentValue.slice(0, start) + pastedText + currentValue.slice(end);
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

const normalizeSetValue = () => {
  if (localProperty.value.dataType === "SET" && localProperty.value.value) {
    const items = splitEscaped(localProperty.value.value);
    const unique = [...new Set(items)].sort();
    localProperty.value.value = unique.map(escapeCommas).join(", ");
  }
};

const save = async (setNull = false) => {
  // For masked (sensitive) variables an empty field means "keep the current value".
  // Never resend the masked placeholder or an empty value, which would overwrite the stored secret.
  if (!setNull && localProperty.value.masked && (localProperty.value.value === null || localProperty.value.value === "")) {
    hide();
    return;
  }

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
    updateSuccessful = response?.setPluginVariableValues;
  } else {
    response = await update([
      {
        key: localProperty.value.key,
        value: setNull ? null : updateValue.value,
      },
    ]);
    updateSuccessful = response?.updateProperties;
  }

  if (!updateSuccessful) {
    const errors = pluginCoordinates.value ? pluginErrors.value : propertyErrors.value;
    if (errors?.length) {
      const errorMsg = errors.map(e => e.message || e).join(', ');
      notify.error(`${pluginCoordinates.value ? "Plugin variable" : "System Property"} update failed: ${errorMsg}`, `${pluginCoordinates.value ? localProperty.value.name : localProperty.value.key}`);
      saving.value = false;
    } else {
      // No errors but updateSuccessful is false means no changes were made
      normalizeSetValue();
      emit("saved", localProperty.value);
      saving.value = false;
      hide();
    }
  } else {
    normalizeSetValue();
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

  // Masked variables arrive with a placeholder value; start blank so the user enters a fresh secret
  // and we never echo the mask back to the backend.
  if (localProperty.value.masked) {
    localProperty.value.value = "";
    setFocus();
    return;
  }

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
.edit-trigger {
  display: block;
}

.subtitle {
  font-size: 0.75rem;
  font-weight: 300;
  color: var(--text-color-secondary);
}
</style>
