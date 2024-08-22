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
    <PageHeader heading="Data Sources">
      <div class="mb-2">
        <DialogTemplate component-name="dataSources/DataSourceConfigurationDialog" header="Add New Data Source" dialog-width="50vw" @reload-data-sources="refresh">
          <Button v-has-permission:FlowUpdate label="Add Data Source" icon="pi pi-plus" class="p-button-sm p-button-outlined mx-1" />
        </DialogTemplate>
      </div>
    </PageHeader>
    <RestDataSourcesPanel ref="restDataSourcesPanel" @data-sources-list="exportableDataSource" />
    <TimedDataSourcesPanel ref="timedDataSourcesPanel" @data-sources-list="exportableDataSource" />
  </div>
</template>

<script setup>
import DialogTemplate from "@/components/DialogTemplate.vue";
import PageHeader from "@/components/PageHeader.vue";
import RestDataSourcesPanel from "@/components/dataSources/RestDataSourcesPanel.vue";
import TimedDataSourcesPanel from "@/components/dataSources/TimedDataSourcesPanel.vue";
import { ref, inject, onMounted, provide, onUnmounted } from "vue";

import Button from "primevue/button";

const refreshInterval = 5000; // 5 seconds
const isIdle = inject("isIdle");
const restDataSourcesPanel = ref(null);
const timedDataSourcesPanel = ref(null);
const editing = ref(false);
provide("isEditing", editing);
let autoRefresh;

const refresh = async () => {
  restDataSourcesPanel.value.refresh();
  timedDataSourcesPanel.value.refresh();
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

const dataSourceList = ref(null);

const exportableDataSource = (value) => {
  dataSourceList.value = value;
};
</script>
