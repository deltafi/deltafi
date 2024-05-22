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
  <div>
    <PageHeader heading="Egress">
      <div class="btn-toolbar mb-2">
        <span class="p-input-icon-left">
          <i class="pi pi-search" />
          <InputText v-model="filterFlowsText" type="text" placeholder="Search" class="p-inputtext-sm deltafi-input-field flow-panel-search-txt" />
        </span>
        <DialogTemplate component-name="egressActions/EgressActionConfigurationDialog" header="Add New Egress" dialog-width="50vw" @reload-egress-action="refresh">
          <Button v-has-permission:FlowUpdate label="Add Egress" icon="pi pi-plus" class="p-button-sm p-button-outlined mx-1" />
        </DialogTemplate>
      </div>
    </PageHeader>
    <EgressActionsPanel ref="egressActionsPanel" :filter-flows-text-prop="filterFlowsText" @egress-actions-list="exportableEgressActions" />
  </div>
</template>

<script setup>
import DialogTemplate from "@/components/DialogTemplate.vue";
import PageHeader from "@/components/PageHeader.vue";
import EgressActionsPanel from "@/components/egressActions/EgressActionsPanel.vue";
import { ref, inject, onMounted, provide, onUnmounted } from "vue";

import Button from "primevue/button";
import InputText from "primevue/inputtext";

const refreshInterval = 5000; // 5 seconds
const isIdle = inject("isIdle");
const egressActionsPanel = ref(null);
const editing = ref(false);
provide("isEditing", editing);
const filterFlowsText = ref("");
let autoRefresh;

const refresh = async () => {
  egressActionsPanel.value.refresh();
};

onMounted(async () => {
  autoRefresh = setInterval(() => {
    if (!isIdle.value) {
      refresh();
    }
  }, refreshInterval);
});

onUnmounted(() => {
  clearInterval(autoRefresh);
});

const egressActionsList = ref(null);

const exportableEgressActions = (value) => {
  egressActionsList.value = value;
};
</script>
