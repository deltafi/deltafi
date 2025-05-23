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
  <span v-if="$hasPermission('DeletePolicyUpdate')" class="pr-2">
    <ConfirmPopup />
    <ConfirmPopup :group="rowData.id + '_' + rowData.name">
      <template #message="slotProps">
        <div class="flex btn-group p-4">
          <i :class="slotProps.message.icon" style="font-size: 1.5rem" />
          <p class="pl-2">
            {{ slotProps.message.message }}
          </p>
        </div>
      </template>
    </ConfirmPopup>
    <InputSwitch v-tooltip.top="deletePolicyToolTip" :model-value="checked" class="p-button-sm" @click="confirmationPopup($event, rowData.id, rowData.name, checked)" />
  </span>
  <span v-else class="pr-2 float-left">
    <Button :label="deletePolicyToolTip" :class="buttonClass" style="width: 5.5rem" disabled />
  </span>
</template>

<script setup>
import useDeletePolicyQueryBuilder from "@/composables/useDeletePolicyQueryBuilder";
import useNotifications from "@/composables/useNotifications";
import { computed, reactive, ref } from "vue";

import Button from "primevue/button";
import ConfirmPopup from "primevue/confirmpopup";
import InputSwitch from "primevue/inputswitch";
import { useConfirm } from "primevue/useconfirm";
import _ from "lodash";

const confirm = useConfirm();
const emit = defineEmits(["reloadDeletePolicies"]);
const { enablePolicy } = useDeletePolicyQueryBuilder();
const notify = useNotifications();

const props = defineProps({
  rowDataProp: {
    type: Object,
    required: true,
  },
});

const { rowDataProp: rowData } = reactive(props);

const checked = ref(rowData.enabled);

const deletePolicyToolTip = computed(() => {
  return _.isEqual(checked.value, true) ? "Enabled" : "Disabled";
});

const buttonClass = computed(() => {
  return _.isEqual(checked.value, true) ? "p-button-primary" : "p-button-secondary";
});

const confirmationPopup = (event, policyId, policyName, state) => {
  if (_.isEqual(state, true)) {
    confirm.require({
      target: event.currentTarget,
      group: `${policyId}_${policyName}`,
      message: `Disable the ${policyName} Delete Policy?`,
      acceptLabel: "Disable",
      rejectLabel: "Cancel",
      icon: "pi pi-exclamation-triangle",
      accept: () => {
        notify.info("Disabling Delete Policy", `Disabling policy ${policyName}.`, 3000);
        toggleDeletePolicyState(policyId, !state);
      },
      reject: () => { },
    });
  } else {
    toggleDeletePolicyState(policyId, !state);
  }
};

const toggleDeletePolicyState = async (policyId, newDeletePolicyState) => {
  await enablePolicy(policyId, newDeletePolicyState);
  checked.value = newDeletePolicyState;
  emit("reloadDeletePolicies");
};
</script>
