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
  <span v-if="(_.isEqual(checked, 'RUNNING') && $hasPermission('FlowStop')) || (_.isEqual(checked, 'STOPPED') && $hasPermission('FlowStart'))">
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
    <InputSwitch v-tooltip.top="checked" :model-value="checked" false-value="STOPPED" true-value="RUNNING" class="p-button-sm" @click="confirmationPopup($event, rowData.name, checked, rowData.flowType)" />
  </span>
  <span v-else>
    <Button :label="checked" :class="buttonClass" style="width: 5.5rem" disabled />
  </span>
</template>

<script setup>
import useFlowQueryBuilder from "@/composables/useFlowQueryBuilder";
import useNotifications from "@/composables/useNotifications";
import { computed, defineProps, reactive, ref } from "vue";

import ConfirmPopup from "primevue/confirmpopup";
import Button from "primevue/button";
import InputSwitch from "primevue/inputswitch";
import { useConfirm } from "primevue/useconfirm";
import _ from "lodash";

const confirm = useConfirm();
const { startIngressFlowByName, stopIngressFlowByName, startEnrichFlowByName, stopEnrichFlowByName, startEgressFlowByName, stopEgressFlowByName } = useFlowQueryBuilder();
const notify = useNotifications();

const props = defineProps({
  rowDataProp: {
    type: Object,
    required: true,
  },
});

const { rowDataProp: rowData } = reactive(props);

const checked = ref(props.rowDataProp.flowStatus.state);

const buttonClass = computed(() => {
  return _.isEqual(checked.value, "RUNNING") ? "p-button-primary" : "p-button-secondary";
});

const confirmationPopup = (event, name, state, flowType) => {
  if (_.isEqual(state, "RUNNING")) {
    confirm.require({
      target: event.currentTarget,
      group: `${rowData.flowType}_${rowData.name}`,
      message: `Stop the ${name} flow?`,
      acceptLabel: "Stop",
      rejectLabel: "Cancel",
      icon: "pi pi-exclamation-triangle",
      accept: () => {
        notify.info("Stopping Flow", `Stopping ${flowType} flow ${name}.`, 3000);
        toggleFlowState(name, state, flowType);
      },
      reject: () => {},
    });
  } else {
    toggleFlowState(name, state, flowType);
  }
};

const toggleFlowState = async (flowName, newflowState, flowType) => {
  if (_.isEqual(flowType, "ingress")) {
    if (_.isEqual(newflowState, "STOPPED")) {
      await startIngressFlowByName(flowName);
    } else {
      await stopIngressFlowByName(flowName);
    }
  } else if (_.isEqual(flowType, "enrich")) {
    if (_.isEqual(newflowState, "STOPPED")) {
      await startEnrichFlowByName(flowName);
    } else {
      await stopEnrichFlowByName(flowName);
    }
  } else if (_.isEqual(flowType, "egress")) {
    if (_.isEqual(newflowState, "STOPPED")) {
      await startEgressFlowByName(flowName);
    } else {
      await stopEgressFlowByName(flowName);
    }
  }
  checked.value = _.isEqual(checked.value, "RUNNING") ? "STOPPED" : "RUNNING";
};
</script>
