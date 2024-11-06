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
    <PageHeader heading="Data Sinks">
      <div class="btn-toolbar mb-2">
        <span class="p-input-icon-left">
          <i class="pi pi-search" />
          <InputText v-model="filterFlowsText" type="text" placeholder="Search" class="p-inputtext-sm deltafi-input-field flow-panel-search-txt mx-1" />
        </span>
        <DialogTemplate component-name="dataSinks/DataSinkConfigurationDialog" header="Add New Data Sink" dialog-width="50vw" @reload-egress-actions="refresh">
          <Button v-has-permission:FlowUpdate label="Add Data Sink" icon="pi pi-plus" class="p-button-sm p-button-outlined mx-1" />
        </DialogTemplate>
      </div>
    </PageHeader>
    <DataSinksPanel ref="dataSinksPanel" :filter-flows-text-prop="filterFlowsText" @egress-actions-list="exportableDataSinks" />
  </div>
</template>

<script setup>
import DialogTemplate from "@/components/DialogTemplate.vue";
import PageHeader from "@/components/PageHeader.vue";
import DataSinksPanel from "@/components/dataSinks/DataSinksPanel.vue";
import { ref, inject, onMounted, provide, onUnmounted } from "vue";

import Button from "primevue/button";
import InputText from "primevue/inputtext";

const refreshInterval = 5000; // 5 seconds
const isIdle = inject("isIdle");
const dataSinksPanel = ref(null);
const editing = ref(false);
provide("isEditing", editing);
const filterFlowsText = ref("");
let autoRefresh;

const refresh = async () => {
  dataSinksPanel.value.refresh();
};

onMounted(() => {
  autoRefresh = setInterval(() => {
    if (!isIdle.value) {
      refresh();
    }
  }, refreshInterval);
});

onUnmounted(() => {
  clearInterval(autoRefresh);
});

const dataSinksList = ref(null);

const exportableDataSinks = (value) => {
  dataSinksList.value = value;
};
</script>
