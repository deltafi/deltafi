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
  <span>
    <ConfirmDialog :group="dialogGroup" />
    <InputSwitch v-tooltip="tooltip" v-model="isOn" :disabled="updating" @click="onChange($event)" />
  </span>
</template>

<script setup>
import { ref, onMounted, computed } from "vue";
import InputSwitch from "primevue/inputswitch";
import useSystemProperties from "@/composables/useSystemProperties";
import useNotifications from "@/composables/useNotifications";
import ConfirmDialog from "primevue/confirmdialog";
import { useConfirm } from "primevue/useconfirm";

const confirm = useConfirm();
const notify = useNotifications();
const { get, set } = useSystemProperties();

const props = defineProps({
  propertyName: {
    type: String,
    required: true,
  },
  offConfirmation: {
    type: String,
    required: false,
    default: null,
  },
  onConfirmation: {
    type: String,
    required: false,
    default: null,
  },
  offTooltip: {
    type: String,
    required: false,
    default: null,
  },
  onTooltip: {
    type: String,
    required: false,
    default: null,
  },
});

const propertyValue = ref();
const isOn = ref(false);
const updating = ref(false);
const emit = defineEmits(["changed"]);

onMounted(async () => {
  propertyValue.value = await get(props.propertyName);
  isOn.value = propertyValue.value === true;
});

const dialogGroup = computed(() => {
  return props.propertyName + "_system_property_switch";
});

const tooltip = computed(() => {
  return isOn.value ? props.onTooltip : props.offTooltip;
});

const onChange = async (event) => {
  event.preventDefault();
  if (!isOn.value) {
    // Turned ON
    if (props.onConfirmation) {
      return new Promise((resolve) => {
        confirm.require({
          message: props.onConfirmation,
          group: dialogGroup.value,
          header: "Confirmation",
          icon: "pi pi-exclamation-triangle",
          accept: async () => {
            await setProperty("true");
            isOn.value = true;
            resolve();
          },
          reject: () => {
            isOn.value = false;
            resolve();
          },
        });
      });
    } else {
      // No confirmation needed
      await setProperty("true");
      isOn.value = true;
    }
  } else {
    // Turned OFF
    if (props.offConfirmation) {
      return new Promise((resolve) => {
        confirm.require({
          message: props.offConfirmation,
          group: dialogGroup.value,
          header: "Confirmation",
          icon: "pi pi-exclamation-triangle",
          accept: async () => {
            await setProperty("false");
            isOn.value = false;
            resolve();
          },
          reject: () => {
            isOn.value = true;
            resolve();
          },
        });
      });
    } else {
      // No confirmation needed
      await setProperty("false");
      isOn.value = false;
    }
  }
};

const setProperty = async (value) => {
  updating.value = true;
  if (await set(props.propertyName, value.toString())) {
    notify.success("Success", `System property <b>${props.propertyName}</b> set to <b>${value}</b>.`);
  } else {
    notify.error("Error", `Failed to set system property <b>${props.propertyName}</b>.`);
    // Revert the switch state on failure
    isOn.value = !isOn.value;
  }
  updating.value = false;
  emit("changed", { key: props.propertyName, value: value });
};

</script>