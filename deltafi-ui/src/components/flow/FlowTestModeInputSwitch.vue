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
  <span>
    <ConfirmPopup></ConfirmPopup>
    <ConfirmPopup :group="rowData.flowType + '_' + rowData.name">
      <template #message="slotProps">
        <div class="flex p-4">
          <i :class="slotProps.message.icon" style="font-size: 1.5rem"></i>
          <p class="pl-2">
            {{ slotProps.message.message }}
          </p>
        </div>
      </template>
    </ConfirmPopup>
    <InputSwitch v-tooltip.top="checkedTooltip" :model-value="checked" class="p-button-sm" @click="confirmationPopup($event, rowData.name, checked, rowData.flowType)" />
  </span>
</template>

<script setup>
import useFlowQueryBuilder from "@/composables/useFlowQueryBuilder";
import useNotifications from "@/composables/useNotifications";
import { defineProps, reactive, ref } from "vue";

import ConfirmPopup from "primevue/confirmpopup";
import InputSwitch from "primevue/inputswitch";
import { useConfirm } from "primevue/useconfirm";
import _ from "lodash";

const confirm = useConfirm();
const { enableTestIngressFlowByName, disableTestIngressFlowByName, enableTestEgressFlowByName, disableTestEgressFlowByName } = useFlowQueryBuilder();
const notify = useNotifications();

const props = defineProps({
  rowDataProp: {
    type: Object,
    required: true,
  },
});

const { rowDataProp: rowData } = reactive(props);
const checked = ref(props.rowDataProp.flowStatus.testMode);
const checkedTooltip = ref();
checkedTooltip.value = props.rowDataProp.flowStatus.testMode ? "Test Mode Enabled" : "Test Mode Disabled";

const confirmationPopup = (event, name, testMode, flowType) => {
  if (testMode) {
    confirm.require({
      target: event.currentTarget,
      group: `${rowData.flowType}_${rowData.name}`,
      message: `Disable Test Mode for ${name} flow?`,
      acceptLabel: "Disable Test Mode",
      rejectLabel: "Cancel",
      icon: "pi pi-exclamation-triangle",
      accept: () => {
        notify.info("Disabling Test Mode", `Disabling Test Mode for ${flowType} flow ${name}.`, 3000);
        toggleFlowState(name, testMode, flowType);
      },
      reject: () => {},
    });
  } else {
    confirm.require({
      target: event.currentTarget,
      group: `${rowData.flowType}_${rowData.name}`,
      message: `Enable Test Mode for ${name} flow?`,
      acceptLabel: "Enable Test Mode",
      rejectLabel: "Cancel",
      icon: "pi pi-exclamation-triangle",
      accept: () => {
        notify.info("Enable Test Mode", `Enable Test Mode for ${flowType} flow ${name}.`, 3000);
        toggleFlowState(name, testMode, flowType);
      },
      reject: () => {},
    });
  }
};

const toggleFlowState = async (flowName, newflowTestMode, flowType) => {
  if (_.isEqual(flowType, "ingress")) {
    if (!newflowTestMode) {
      await enableTestIngressFlowByName(flowName);
    } else {
      await disableTestIngressFlowByName(flowName);
    }
  } else if (_.isEqual(flowType, "egress")) {
    if (!newflowTestMode) {
      await enableTestEgressFlowByName(flowName);
    } else {
      await disableTestEgressFlowByName(flowName);
    }
  }
  checked.value = checked.value === true ? false : true;
  checkedTooltip.value = checked.value ? "Test Mode Enabled" : "Test Mode Disabled";
};
</script>
