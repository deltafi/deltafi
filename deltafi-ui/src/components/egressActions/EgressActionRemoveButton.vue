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
  <span>
    <ConfirmPopup></ConfirmPopup>
    <ConfirmPopup :group="rowData.name">
      <template #message="slotProps">
        <div class="flex btn-group p-4">
          <i :class="slotProps.message.icon" style="font-size: 1.5rem"></i>
          <p class="pl-2">
            {{ slotProps.message.message }}
          </p>
        </div>
      </template>
    </ConfirmPopup>
    <span v-tooltip.top="'Remove'" class="cursor-pointer" @click="confirmationPopup($event, rowData.name)">
      <i class="ml-2 text-muted fa-solid fa-trash-can" />
    </span>
  </span>
</template>

<script setup>
import useEgressActions from "@/composables/useEgressActions";
import useNotifications from "@/composables/useNotifications";
import { defineEmits, defineProps, reactive } from "vue";

import ConfirmPopup from "primevue/confirmpopup";
import { useConfirm } from "primevue/useconfirm";

const confirm = useConfirm();
const { removeEgressFlowPlan } = useEgressActions();
const emit = defineEmits(["reloadEgressActions"]);
const notify = useNotifications();

const props = defineProps({
  rowDataProp: {
    type: Object,
    required: true,
  },
});

const { rowDataProp: rowData } = reactive(props);

const confirmationPopup = (event, actionName) => {
  confirm.require({
    target: event.currentTarget,
    group: `${actionName}`,
    message: `Remove ${actionName} Egress?`,
    acceptLabel: "Remove",
    rejectLabel: "Cancel",
    icon: "pi pi-exclamation-triangle",
    accept: () => {
      notify.info("Removing Egress", `Removing ${actionName}.`, 3000);
      confirmedRemoveEgressAction(actionName);
    },
    reject: () => { },
  });
};

const confirmedRemoveEgressAction = async (actionName) => {
  await removeEgressFlowPlan(actionName);
  emit("reloadEgressActions");
};
</script>