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
  <div>
    <PageHeader heading="Data Sinks">
      <div class="btn-toolbar">
        <IconField iconPosition="left">
          <InputIcon class="pi pi-search"> </InputIcon>
          <InputText v-model="filterFlowsText" type="text" placeholder="Search" class="p-inputtext deltafi-input-field mx-1" />
        </IconField>
        <DataSinkPageHeaderButtonGroup :export-data-sinks="dataSinkExport" @reload-data-sinks="refresh" />
      </div>
    </PageHeader>
    <DataSinksPanel ref="dataSinksPanel" :filter-flows-text-prop="filterFlowsText" @data-sinks-list="exportableRestDataSource" />
  </div>
</template>

<script setup>
import DataSinkPageHeaderButtonGroup from "@/components/dataSinks/DataSinkPageHeaderButtonGroup.vue";
import DataSinksPanel from "@/components/dataSinks/DataSinksPanel.vue";
import PageHeader from "@/components/PageHeader.vue";
import { computed, inject, onMounted, onUnmounted, provide, ref } from "vue";

import _ from "lodash";

import IconField from "primevue/iconfield";
import InputIcon from "primevue/inputicon";
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

const dataSinkExport = computed(() => {
  const dataSinkObject = {};
  dataSinkObject["dataSinks"] = dataSinksList.value;
  return dataSinkObject;
});

const exportableRestDataSource = (value) => {
  const formatDataSinksList = JSON.parse(JSON.stringify(value));
  formatDataSinksList.forEach((e, index) => (formatDataSinksList[index] = _.pick(e, ["name", "type", "description", "subscribe", "egressAction.name", "egressAction.type", "egressAction.parameters", "egressAction.apiVersion", "egressAction.join"])));

  dataSinksList.value = formatDataSinksList;
};
</script>
