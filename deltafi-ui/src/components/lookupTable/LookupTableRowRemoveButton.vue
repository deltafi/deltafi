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
    <ConfirmPopup />
    <ConfirmPopup :group="allData.name + '_' + index">
      <template #message="slotProps">
        <div class="flex btn-group p-4">
          <i :class="slotProps.message.icon" style="font-size: 1.5rem" />
          <p class="pl-2">
            {{ slotProps.message.message }}
          </p>
        </div>
      </template>
    </ConfirmPopup>
    <Button v-tooltip.top="`Delete`" icon="pi pi-trash" class="p-button-text p-button-sm p-button-rounded p-button-danger" @click="confirmationPopup($event)" />
  </span>
</template>

<script setup>
import useLookupTable from "@/composables/useLookupTable";
import useNotifications from "@/composables/useNotifications";
import { reactive } from "vue";

import _ from "lodash";

import ConfirmPopup from "primevue/confirmpopup";
import Button from "primevue/button";
import { useConfirm } from "primevue/useconfirm";

const confirm = useConfirm();
const emit = defineEmits(["reloadLookupTables"]);
const notify = useNotifications();

const props = defineProps({
  allDataProp: {
    type: Object,
    required: true,
  },
  rowDataProp: {
    type: Object,
    required: true,
  },
  index: {
    type: Number,
    required: true,
  },
});

const { rowDataProp: rowData, index, allDataProp: allData } = reactive(props);

const { removeLookupTableRows } = useLookupTable();

const confirmationPopup = (event) => {
  confirm.require({
    target: event.currentTarget,
    group: `${allData.name}_${index}`,
    message: `Remove Row?`,
    acceptLabel: "Remove",
    rejectLabel: "Cancel",
    icon: "pi pi-exclamation-triangle",
    accept: () => {
      notify.info("Removing Row", `Removing Row.`, 3000);
      confirmedRemoveLookupTableRow();
    },
    reject: () => {},
  });
};

const confirmedRemoveLookupTableRow = async () => {
  await deleteRow();
};

const convertRowToPairs = (row) => {
  return _.map(_.toPairs(row), ([column, value]) => ({ column, value }));
};

const deleteRow = async () => {
  const row = convertRowToPairs(rowData);
  const response = await removeLookupTableRows(allData.name, [row]);
  if (!_.isEmpty(response.data.removeLookupTableRows.errors)) {
    notify.error("Error deleting rows", response.data.removeLookupTableRows.errors);
  } else {
    notify.success("Rows deleted successfully");
    emit("reloadLookupTables");
  }
};
</script>
