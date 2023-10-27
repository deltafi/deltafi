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
  <span v-if="(_.isEqual(rowData.flowStatus.state, 'RUNNING') && $hasPermission('FlowStop')) || (_.isEqual(rowData.flowStatus.state, 'STOPPED') && $hasPermission('FlowStart'))">
    <ConfirmPopup></ConfirmPopup>
    <ConfirmPopup :group="ingressActionType + '_' + rowData.name">
      <template #message="slotProps">
        <div class="flex btn-group p-4">
          <i :class="slotProps.message.icon" style="font-size: 1.5rem"></i>
          <p class="pl-2">
            {{ slotProps.message.message }}
          </p>
        </div>
      </template>
    </ConfirmPopup>
    <InputSwitch v-tooltip.top="rowData.flowStatus.state" :model-value="rowData.flowStatus.state" false-value="STOPPED" true-value="RUNNING" class="p-button-sm" @click="confirmationPopup($event, rowData.name, rowData.flowStatus.state)" />
  </span>
  <span v-else>
    <Tag class="ml-2" :value="rowData.flowStatus.state" severity="info" icon="pi pi-info-circle" :rounded="true" />
  </span>
</template>

<script setup>
import useIngressActions from "@/composables/useIngressActions";
import useNotifications from "@/composables/useNotifications";
import { defineProps, toRefs } from "vue";

import Tag from "primevue/tag";
import ConfirmPopup from "primevue/confirmpopup";
import InputSwitch from "primevue/inputswitch";
import { useConfirm } from "primevue/useconfirm";
import _ from "lodash";

const confirm = useConfirm();
const { startTimedIngressFlowByName, stopTimedIngressFlowByName } = useIngressActions();
const notify = useNotifications();
const emit = defineEmits(["change"]);

const props = defineProps({
  rowDataProp: {
    type: Object,
    required: true,
  },
  ingressActionType: {
    type: String,
    required: true
  }
});

const { rowDataProp: rowData, ingressActionType } = toRefs(props);

const confirmationPopup = async (event, name, state) => {
  if (_.isEqual(state, "RUNNING")) {
    confirm.require({
      target: event.currentTarget,
      group: `${ingressActionType.value}_${name}`,
      message: `Stop the '${name}' ingress action?`,
      acceptLabel: "Stop",
      rejectLabel: "Cancel",
      icon: "pi pi-exclamation-triangle",
      accept: async () => {
        notify.info("Stopping Ingress Action", `Stopping ${name} ingress action.`, 3000);
        await toggleFlowState(name, state);
        emit("change")
      },
      reject: () => { },
    });
  } else {
    await toggleFlowState(name, state);
    emit("change")
  }
};

const toggleFlowState = async (flowName, newflowState) => {
  if (_.isEqual(ingressActionType.value, "timedIngress")) {
    if (_.isEqual(newflowState, "STOPPED")) {
      await startTimedIngressFlowByName(flowName);
    } else {
      await stopTimedIngressFlowByName(flowName);
    }
  }
  rowData.value.flowStatus.state = _.isEqual(rowData.value.flowStatus.state, "RUNNING") ? "STOPPED" : "RUNNING";
};
</script>
