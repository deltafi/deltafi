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
  <span v-if="$hasPermission('FlowUpdate')">
    <ConfirmPopup />
    <ConfirmPopup :group="rowData.name + '_backingServiceActive'">
      <template #message="slotProps">
        <div class="flex btn-group p-4">
          <i :class="slotProps.message.icon" style="font-size: 1.5rem" />
          <p class="pl-2" v-html="slotProps.message.message" />
        </div>
      </template>
    </ConfirmPopup>
    <InputSwitch v-tooltip.top="tooltip" :model-value="rowData.backingServiceActive" class="p-button-sm" @click="confirmationPopup($event, rowData.name, rowData.backingServiceActive)" />
  </span>
  <span v-else class="pr-2 float-left">
    <Button :label="backingServiceActiveToolTip" :class="backingServiceActiveButtonClass" style="width: 5.5rem" disabled />
  </span>
</template>

<script setup>
import useLookupTable from "@/composables/useLookupTable";
import useNotifications from "@/composables/useNotifications";
import { computed, toRefs, ref } from "vue";

import Button from "primevue/button";
import ConfirmPopup from "primevue/confirmpopup";
import InputSwitch from "primevue/inputswitch";
import { useConfirm } from "primevue/useconfirm";
import _ from "lodash";

const confirm = useConfirm();

const { setLookupTableBackingServiceActive } = useLookupTable();
const notify = useNotifications();

const props = defineProps({
  rowDataProp: {
    type: Object,
    required: true,
  },
});

const { rowDataProp: rowData } = toRefs(props);
const tooltip = ref();
tooltip.value = rowData.value.backingServiceActive ? "Backing Service Active Enabled" : "Backing Service Active Disabled";

const backingServiceActiveToolTip = computed(() => {
  return _.isEqual(rowData.value.backingServiceActive, true) ? "Enabled" : "Disabled";
});

const backingServiceActiveButtonClass = computed(() => {
  return _.isEqual(rowData.value.backingServiceActive, true) ? "p-button-primary" : "p-button-secondary";
});

const confirmationPopup = (event, name, backingServiceActive) => {
  if (backingServiceActive) {
    confirm.require({
      target: event.currentTarget,
      group: `${rowData.value.name}_backingServiceActive`,
      message: `Disable Backing Service Active for the <b>${name}</b>?`,
      acceptLabel: "Disable Backing Service Active",
      rejectLabel: "Cancel",
      icon: "pi pi-exclamation-triangle",
      accept: () => toggleBackendService(name, backingServiceActive),
      reject: () => {},
    });
  } else {
    confirm.require({
      target: event.currentTarget,
      group: `${rowData.value.name}_backingServiceActive`,
      message: `Enable Backing Service Active for the <b>${name}</b>?`,
      acceptLabel: "Enable Backing Service Active",
      rejectLabel: "Cancel",
      icon: "pi pi-exclamation-triangle",
      accept: () => toggleBackendService(name, backingServiceActive),
      reject: () => {},
    });
  }
};

const toggleBackendService = async (name, backingServiceActive) => {
  if (!backingServiceActive) {
    notify.info("Enabling Backing Service Active", `Enabling Backing Service Active for the <b>${name}</b>.`, 3000);
    await setLookupTableBackingServiceActive(name, true);
  } else {
    notify.info("Disabling Backing Service Active", `Disabling Backing Service Active for the <b>${name}</b>`, 3000);
    await setLookupTableBackingServiceActive(name, false);
  }
  rowData.value.backingServiceActive = !rowData.value.backingServiceActive;
  tooltip.value = rowData.value.backingServiceActive ? "Backing Service Active Enabled" : "Backing Service Active Disabled";
};
</script>
