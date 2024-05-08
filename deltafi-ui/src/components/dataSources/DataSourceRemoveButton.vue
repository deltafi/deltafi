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
    <Button v-tooltip.top="`Remove`" icon="pi pi-trash" class="p-button-text p-button-sm p-button-rounded p-button-secondary mx-n2" @click="confirmationPopup($event, rowData.name)" />
  </span>
</template>

<script setup>
import useDataSource from "@/composables/useDataSource";
import useNotifications from "@/composables/useNotifications";
import { defineEmits, defineProps, reactive } from "vue";

import ConfirmPopup from "primevue/confirmpopup";
import Button from "primevue/button";
import { useConfirm } from "primevue/useconfirm";

const confirm = useConfirm();
const { removeDataSourcePlan } = useDataSource();
const emit = defineEmits(["reloadDataSources"]);
const notify = useNotifications();

const props = defineProps({
  rowDataProp: {
    type: Object,
    required: true,
  },
});

const { rowDataProp: rowData } = reactive(props);

const confirmationPopup = (event, dataSource) => {
  confirm.require({
    target: event.currentTarget,
    group: `${dataSource}`,
    message: `Remove ${dataSource} Data Source?`,
    acceptLabel: "Remove",
    rejectLabel: "Cancel",
    icon: "pi pi-exclamation-triangle",
    accept: () => {
      notify.info("Removing Data Source", `Removing data source ${dataSource}.`, 3000);
      confirmedRemoveDataSource(dataSource);
    },
    reject: () => {},
  });
};

const confirmedRemoveDataSource = async (dataSource) => {
  await removeDataSourcePlan(dataSource);
  emit("reloadDataSources");
};
</script>
