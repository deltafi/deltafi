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
  <span v-if="states.includes(rowData.flowStatus.state) && $hasPermission('FlowUpdate')">
    <ConfirmDialog :group="'egressAction__' + rowData.name">
      <template #message="slotProps">
        <span class="p-confirm-dialog-icon pi pi-exclamation-triangle" />
        <span class="p-confirm-dialog-message" v-html="slotProps.message.message" />
      </template>
    </ConfirmDialog>
    <FlowControlButtons v-model="rowData.flowStatus.state" class="control-buttons" @start="start()" @pause="pause()" @stop="stop()" />
  </span>
  <span v-else class="pt-1">
    <Tag v-tooltip.left="tooltip" class="ml-2" :value="rowData.flowStatus.state" severity="info" icon="pi pi-info-circle" :rounded="true" />
  </span>
</template>

<script setup>
import FlowControlButtons from "@/components/FlowControlButtons.vue";
import useNotifications from "@/composables/useNotifications";
import useFlows from "@/composables/useFlows";
import { computed, toRefs } from "vue";

import Tag from "primevue/tag";
import ConfirmDialog from "primevue/confirmdialog";
import { useConfirm } from "primevue/useconfirm";
import _ from "lodash";

const confirm = useConfirm();
const { setFlowState } = useFlows();

const notify = useNotifications();
const emit = defineEmits(["change", "confirm-start", "confirm-stop"]);
const states = ['RUNNING', 'STOPPED', 'PAUSED'];

const props = defineProps({
  rowDataProp: {
    type: Object,
    required: true,
  },
});

const { rowDataProp: rowData } = toRefs(props);

const tooltip = computed(() => {
  let errorsList = _.map(rowData.value.flowStatus.errors, "message");

  if (errorsList.toString().includes(";")) {
    errorsList = errorsList.toString().split(";");
  }

  return ` Errors:\n •${errorsList.join("\n•")}`;
});

const start = async () => {
  const { name } = { name: rowData.value.name };
  notify.info("Starting Data Sink", `Starting <b>${name}</b> data sink.`, 3000);
  await setFlowState('DATA_SINK', name, 'RUNNING');
  emit("change");
}

const pause = async () => {
  const { name } = { name: rowData.value.name };
  notify.info("Pausing Data Sink", `Pausing <b>${name}</b> data sink.`, 3000);
  await setFlowState('DATA_SINK', name, 'PAUSED');
  emit("change");
}

const stop = async () => {
  const { name } = { name: rowData.value.name };
  emit("confirm-start");
  confirm.require({
    group: `egressAction__${name}`,
    message: `Are you sure you want to stop the <b>${name}</b> data sink?`,
    acceptLabel: "Stop",
    rejectLabel: "Cancel",
    icon: "pi pi-exclamation-triangle",
    header: "Stop Confirmation",
    rejectProps: {
      label: "Cancel",
      severity: "secondary",
      outlined: true,
    },
    acceptProps: {},
    accept: async () => {
      notify.info("Stopping Data Sink", `Stopping <b>${name}</b> data sink.`, 3000);
      await setFlowState('DATA_SINK', name, 'STOPPED');
      emit("confirm-stop");
      emit("change");
    },
    reject: () => {
      emit("confirm-stop");
      emit("change");

    },
    onHide: () => {
      emit("confirm-stop");
      emit("change");
    }
  });
}
</script>