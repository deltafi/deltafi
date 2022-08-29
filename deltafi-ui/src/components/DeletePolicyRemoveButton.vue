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
    <ConfirmPopup group="removePolicy">
      <template #message="slotProps">
        <div class="flex p-4">
          <i :class="slotProps.message.icon" style="font-size: 1.5rem"></i>
          <p class="pl-2">
            {{ slotProps.message.message }}
          </p>
        </div>
      </template>
    </ConfirmPopup>
    <Button v-tooltip.top="`Remove Delete Policy`" icon="pi pi-trash" class="p-button-text p-button-sm p-button-rounded p-button-danger" @click="confirmationPopup($event, rowData.id)" />
  </span>
</template>

<script setup>
import useDeletePolicyQueryBuilder from "@/composables/useDeletePolicyQueryBuilder";
import useNotifications from "@/composables/useNotifications";
import { defineEmits, defineProps, reactive } from "vue";

import ConfirmPopup from "primevue/confirmpopup";
import Button from "primevue/button";
import { useConfirm } from "primevue/useconfirm";

const confirm = useConfirm();
const emit = defineEmits(["reloadDeletePolicies"]);
const { removeDeletePolicy } = useDeletePolicyQueryBuilder();
const notify = useNotifications();

const props = defineProps({
  rowDataProp: {
    type: Object,
    required: true,
  },
});

const { rowDataProp: rowData } = reactive(props);

const confirmationPopup = (event, policyName) => {
  confirm.require({
    target: event.currentTarget,
    message: `Remove ${policyName} Delete Policy?`,
    acceptLabel: "Remove",
    rejectLabel: "Cancel",
    icon: "pi pi-exclamation-triangle",
    accept: () => {
      notify.info("Removing Delete Policy", `Removing policy ${policyName}.`, 3000);
      confirmedRemoveDeletePolicy(policyName);
    },
    reject: () => {},
  });
};

const confirmedRemoveDeletePolicy = async (policyName) => {
  await removeDeletePolicy(policyName);
  emit("reloadDeletePolicies");
};
</script>
