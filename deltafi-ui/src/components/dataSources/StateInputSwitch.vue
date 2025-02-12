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
    <ConfirmDialog :group="dataSourceType + '_' + rowData.name">
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
import useTopics from "@/composables/useTopics";
import useFlows from "@/composables/useFlows";
import { computed, toRefs, onBeforeMount, ref } from "vue";

import Tag from "primevue/tag";
import ConfirmDialog from "primevue/confirmdialog";
import { useConfirm } from "primevue/useconfirm";
import _ from "lodash";

const { hasActiveSubscribers } = useTopics();
const confirm = useConfirm();
const topicActive = ref(false);
const { setFlowState } = useFlows();
const notify = useNotifications();
const emit = defineEmits(["change", "confirm-start", "confirm-stop"]);
const states = ['RUNNING', 'STOPPED', 'PAUSED'];
const flowTypeMap = {
  restDataSource: "REST_DATA_SOURCE",
  timedDataSource: "TIMED_DATA_SOURCE",
}

const props = defineProps({
  rowDataProp: {
    type: Object,
    required: true,
  },
  dataSourceType: {
    type: String,
    required: true,
  },
});

onBeforeMount(async () => (topicActive.value = await hasActiveSubscribers(rowData.value.topic)));

const { rowDataProp: rowData, dataSourceType } = toRefs(props);

const start = async () => {
  const { name, topic } = { name: rowData.value.name, topic: rowData.value.topic };
  if (topicActive.value) {
    notify.info("Starting Data Source", `Starting <b>${name}</b> data source.`, 3000);
    await setFlowState(flowTypeMap[dataSourceType.value], name, 'RUNNING');
    emit("change");
  } else {
    emit("confirm-start");
    confirm.require({
      group: `${dataSourceType.value}_${name}`,
      message: `<p>Are you sure you want to start the <b>${name}</b> data source?</p><p>The target topic, <b>${topic}</b>, has no active subscribers.</p>`,
      acceptLabel: "Start",
      rejectLabel: "Cancel",
      icon: "pi pi-exclamation-triangle",
      header: "Start Confirmation",
      rejectProps: {
        label: "Cancel",
        severity: "secondary",
        outlined: true,
      },
      acceptProps: {},
      accept: async () => {
        notify.info("Starting Data Source", `Starting <b>${name}</b> data source.`, 3000);
        await setFlowState(flowTypeMap[dataSourceType.value], name, 'RUNNING');
        emit("change");
        emit("confirm-stop");
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
}

const pause = async () => {
  const { name } = { name: rowData.value.name };
  notify.info("Pausing Data Source", `Pausing <b>${name}</b> data source.`, 3000);
  await setFlowState(flowTypeMap[dataSourceType.value], name, 'PAUSED');
  emit("change");
}

const stop = async () => {
  const { name } = { name: rowData.value.name };
  emit("confirm-start");
  confirm.require({
    group: `${dataSourceType.value}_${name}`,
    message: `Are you sure you want to stop the <b>${name}</b> data source?`,
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
      notify.info("Stopping Data Source", `Stopping <b>${name}</b> data source.`, 3000);
      await setFlowState(flowTypeMap[dataSourceType.value], name, 'STOPPED');
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

const tooltip = computed(() => {
  let errorsList = _.map(rowData.value.flowStatus.errors, "message");

  if (errorsList.toString().includes(";")) {
    errorsList = errorsList.toString().split(";");
  }

  return ` Errors:\n •${errorsList.join("\n•")}`;
});
</script>
