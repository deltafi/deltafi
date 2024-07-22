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
    <ConfirmPopup :group="dataSourceType + '_' + rowData.name">
      <template #message="slotProps">
        <div class="flex btn-group p-4">
          <i :class="slotProps.message.icon" style="font-size: 1.5rem"></i>
          <p class="pl-2" v-html="slotProps.message.message" />
        </div>
      </template>
    </ConfirmPopup>
    <InputSwitch v-tooltip.top="rowData.flowStatus.state" :model-value="rowData.flowStatus.state" false-value="STOPPED" true-value="RUNNING" class="p-button-sm" @click="confirmationPopup($event)" />
  </span>
  <span v-else class="pt-1">
    <Tag v-tooltip.left="tooltip" class="ml-2" :value="rowData.flowStatus.state" severity="info" icon="pi pi-info-circle" :rounded="true" />
  </span>
</template>

<script setup>
import useDataSource from "@/composables/useDataSource";
import useNotifications from "@/composables/useNotifications";
import useTopics from "@/composables/useTopics";
import { computed, defineProps, toRefs, onBeforeMount, ref } from "vue";

import Tag from "primevue/tag";
import ConfirmPopup from "primevue/confirmpopup";
import InputSwitch from "primevue/inputswitch";
import { useConfirm } from "primevue/useconfirm";
import _ from "lodash";

const { hasActiveSubscribers } = useTopics();
const confirm = useConfirm();
const topicActive = ref(false);
const { startRestDataSourceByName, startTimedDataSourceByName, stopRestDataSourceByName, stopTimedDataSourceByName } = useDataSource();
const notify = useNotifications();
const emit = defineEmits(["change"]);

const props = defineProps({
  rowDataProp: {
    type: Object,
    required: true,
  },
  dataSourceType: {
    type: String,
    required: true,
  },
  configureDataSourceDialog: {
    type: Boolean,
    required: false,
    default: false,
  },
});

onBeforeMount(async () => (topicActive.value = await hasActiveSubscribers(rowData.value.topic)));

const { rowDataProp: rowData, dataSourceType, configureDataSourceDialog } = toRefs(props);

const confirmationPopup = async (event) => {
  const { name, state, topic } = { name: rowData.value.name, state: rowData.value.flowStatus.state, topic: rowData.value.topic };

  if (_.isEqual(state, "RUNNING")) {
    // Stop
    confirm.require({
      target: event.currentTarget,
      group: `${dataSourceType.value}_${name}`,
      message: `Stop the <b>${name}</b> data source?`,
      acceptLabel: "Stop",
      rejectLabel: "Cancel",
      icon: "pi pi-exclamation-triangle",
      accept: () => toggleFlowState(name, state),
      reject: () => {},
    });
  } else {
    // Start
    if (topicActive.value) {
      await toggleFlowState(name, state);
    } else {
      confirm.require({
        target: event.currentTarget,
        group: `${dataSourceType.value}_${name}`,
        message: `Start the <b>${name}</b> data source? Target topic <b>${topic}</b> has no active subscribers.`,
        acceptLabel: "Start",
        rejectLabel: "Cancel",
        icon: "pi pi-exclamation-triangle",
        accept: () => toggleFlowState(name, state),
        reject: () => {},
      });
    }
  }
};

const tooltip = computed(() => {
  let errorsList = _.map(rowData.value.flowStatus.errors, "message");

  if (errorsList.toString().includes(";")) {
    errorsList = errorsList.toString().split(";");
  }

  return ` Errors:\n •${errorsList.join("\n•")}`;
});

const toggleFlowState = async (flowName, newFlowState) => {
  if (!configureDataSourceDialog.value) {
    if (_.isEqual(newFlowState, "STOPPED")) {
      if (_.isEqual(dataSourceType.value, "timedDataSource")) {
        notify.info("Starting Timed Data Source", `Starting <b>${flowName}</b> data source.`, 3000);
        await startTimedDataSourceByName(flowName);
      } else {
        notify.info("Starting Rest Data Source", `Starting <b>${flowName}</b> data source.`, 3000);
        await startRestDataSourceByName(flowName);
      }
    } else {
      if (_.isEqual(dataSourceType.value, "timedDataSource")) {
        notify.info("Stopping Timed Data Source", `Stopping <b>${flowName}</b> data source.`, 3000);
        await stopTimedDataSourceByName(flowName);
      } else {
        notify.info("Stopping Rest Data Source", `Stopping <b>${flowName}</b> data source.`, 3000);
        await stopRestDataSourceByName(flowName);
      }
    }
    emit("change");
  }
  rowData.value.flowStatus.state = _.isEqual(rowData.value.flowStatus.state, "RUNNING") ? "STOPPED" : "RUNNING";
};
</script>
