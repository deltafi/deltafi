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
  <span v-if="$hasPermission('FlowUpdate')">
    <ConfirmPopup></ConfirmPopup>
    <ConfirmPopup :group="rowData.flowType + '_testmode_' + rowData.name">
      <template #message="slotProps">
        <div class="flex btn-group p-4">
          <i :class="slotProps.message.icon" style="font-size: 1.5rem"></i>
          <p class="pl-2" v-html="slotProps.message.message" />
        </div>
      </template>
    </ConfirmPopup>
    <InputSwitch v-tooltip.top="tooltip" :model-value="rowData.flowStatus.testMode" class="p-button-sm" @click="confirmationPopup($event, rowData.name, rowData.flowStatus.testMode)" />
  </span>
  <span v-else class="pr-2 float-left">
    <Button :label="testModeToolTip" :class="testModeButtonClass" style="width: 5.5rem" disabled />
  </span>
</template>

<script setup>
import useDataSink from "@/composables/useDataSink";
import useNotifications from "@/composables/useNotifications";
import { computed, defineProps, toRefs, ref } from "vue";

import Button from "primevue/button";
import ConfirmPopup from "primevue/confirmpopup";
import InputSwitch from "primevue/inputswitch";
import { useConfirm } from "primevue/useconfirm";
import _ from "lodash";

const confirm = useConfirm();

const { enableDataSinkTestModeByName, disableDataSinkTestModeByName } = useDataSink();
const notify = useNotifications();

const props = defineProps({
  rowDataProp: {
    type: Object,
    required: true,
  },
});

const { rowDataProp: rowData } = toRefs(props);
const tooltip = ref();
tooltip.value = rowData.value.flowStatus.testMode ? "Test Mode Enabled" : "Test Mode Disabled";

const testModeToolTip = computed(() => {
  return _.isEqual(rowData.value.flowStatus.testMode, true) ? "Enabled" : "Disabled";
});

const testModeButtonClass = computed(() => {
  return _.isEqual(rowData.value.flowStatus.testMode, true) ? "p-button-primary" : "p-button-secondary";
});

const confirmationPopup = (event, name, testMode) => {
  if (testMode) {
    confirm.require({
      target: event.currentTarget,
      group: `${rowData.value.flowType}_testmode_${rowData.value.name}`,
      message: `Disable Test Mode for <b>${name}</b> flow?`,
      acceptLabel: "Disable Test Mode",
      rejectLabel: "Cancel",
      icon: "pi pi-exclamation-triangle",
      accept: () => toggleFlowState(name, testMode),
      reject: () => { },
    });
  } else {
    confirm.require({
      target: event.currentTarget,
      group: `${rowData.value.flowType}_testmode_${rowData.value.name}`,
      message: `Enable Test Mode for <b>${name}</b> flow?`,
      acceptLabel: "Enable Test Mode",
      rejectLabel: "Cancel",
      icon: "pi pi-exclamation-triangle",
      accept: () => toggleFlowState(name, testMode),
      reject: () => { },
    });
  }
};

const toggleFlowState = async (flowName, newFlowTestMode) => {
  if (!newFlowTestMode) {
    notify.info("Enabling Test Mode", `Enabling Test Mode for <b>${flowName}</b> flow.`, 3000);
    await enableDataSinkTestModeByName(flowName);
  } else {
    notify.info("Disabling Test Mode", `Disabling Test Mode for <b>${flowName}</b> flow.`, 3000);
    await disableDataSinkTestModeByName(flowName);
  }
  rowData.value.flowStatus.testMode = !rowData.value.flowStatus.testMode;
  tooltip.value = rowData.value.flowStatus.testMode ? "Test Mode Enabled" : "Test Mode Disabled";
};
</script>