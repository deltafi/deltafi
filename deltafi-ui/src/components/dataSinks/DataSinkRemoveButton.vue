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
    <ConfirmDialog :group="rowData.name" />
  </span>
</template>

<script setup>
import useEgressActions from "@/composables/useDataSink";
import useNotifications from "@/composables/useNotifications";
import { reactive } from "vue";

import ConfirmDialog from "primevue/confirmdialog";
import { useConfirm } from "primevue/useconfirm";

const confirm = useConfirm();
const { removeDataSinkPlan } = useEgressActions();
const emit = defineEmits(["reloadDataSinks"]);
const notify = useNotifications();

const props = defineProps({
  rowDataProp: {
    type: Object,
    required: true,
  },
});

const { rowDataProp: rowData } = reactive(props);

const confirmationPopup = () => {
  confirm.require({
    position: "center",
    group: rowData.name,
    message: `Remove ${rowData.name} Data Sink?`,
    acceptLabel: "Remove",
    rejectLabel: "Cancel",
    icon: "pi pi-exclamation-triangle",
    accept: () => {
      notify.info("Removing Data Sink", `Removing Data Sink ${rowData.name}.`, 3000);
      confirmedRemoveDataSink();
    },
    reject: () => { },
  });
};

const confirmedRemoveDataSink = async () => {
  let response = await removeDataSinkPlan(rowData.name, rowData.type);

  response = response.data.removeDataSinkPlan;

  if (response) {
    notify.success("Removed Data Sink", `Removed Data Sink ${rowData.name}.`, 3000);
  } else {
    notify.error("Error Removing Data Sink", `Error removing Data Sink ${rowData.name}.`, 3000);
  }
  emit("reloadDataSinks");
};

const showDialog = () => {
  confirmationPopup();
};

defineExpose({
  showDialog,
});
</script>
