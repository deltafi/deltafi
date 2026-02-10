<!--
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>

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

<!-- ABOUTME: Data Sinks page with plugin-based grouping. -->
<!-- ABOUTME: Displays data sinks grouped by plugin with filtering support. -->

<template>
  <div class="data-sinks-page">
    <PageHeader heading="Data Sinks">
      <DataSinkPageHeaderButtonGroup :export-data-sinks="dataSinkExport" @reload-data-sinks="refresh" />
    </PageHeader>
    <ProgressBar v-if="showLoading" mode="indeterminate" style="height: 0.5em" />
    <div v-else>
      <div class="d-flex justify-content-between">
        <IconField iconPosition="left" class="mb-3">
          <InputIcon class="pi pi-search"></InputIcon>
          <InputText v-model="filterFlowsText" type="text" placeholder="Search" class="p-inputtext deltafi-input-field" />
        </IconField>
        <div class="kill-switch">
          <span class="mr-2 text-muted">Egress Enabled</span>
          <SystemPropertySwitch class="mr-1" property-name="egressEnabled" off-confirmation="Are you sure? This will stop all data from flowing out of the system." off-tooltip="Enable egress" on-tooltip="Disable egress" />
        </div>
      </div>
      <DataSinkDataTable :flow-data-prop="flowData" :flow-data-by-plugin-prop="flowDataByPlugin" :filter-flows-text-prop="filterFlowsText" @reload-data-sinks="refresh" />
    </div>
  </div>
</template>

<script setup>
import DataSinkDataTable from "@/components/dataSinks/DataSinkDataTable.vue";
import DataSinkPageHeaderButtonGroup from "@/components/dataSinks/DataSinkPageHeaderButtonGroup.vue";
import PageHeader from "@/components/PageHeader.vue";
import ProgressBar from "@/components/deprecatedPrimeVue/ProgressBar.vue";
import SystemPropertySwitch from "@/components/SystemPropertySwitch.vue";
import useDataSink from "@/composables/useDataSink";
import useTopics from "@/composables/useTopics";
import { computed, inject, onMounted, onUnmounted, provide, ref } from "vue";

import _ from "lodash";

import IconField from "primevue/iconfield";
import InputIcon from "primevue/inputicon";
import InputText from "primevue/inputtext";

const { getAllDataSinks, loaded, loading } = useDataSink();
const { clearTopicsCache, getAllTopics, loaded: topicsLoaded, loading: topicsLoading } = useTopics();

const refreshInterval = 5000; // 5 seconds
const isIdle = inject("isIdle");
const editing = ref(false);
provide("isEditing", editing);

const flowData = ref([]);
const flowDataByPlugin = ref({});
const filterFlowsText = ref("");

let autoRefresh;

const showLoading = computed(() => (!loaded.value && loading.value) || (!topicsLoaded.value && topicsLoading.value));

onMounted(async () => {
  await Promise.all([clearTopicsCache(), getAllTopics(), fetchFlows()]);

  autoRefresh = setInterval(() => {
    if (!isIdle.value && !editing.value) {
      refresh();
    }
  }, refreshInterval);
});

onUnmounted(() => {
  clearInterval(autoRefresh);
});

const fetchFlows = async () => {
  const response = await getAllDataSinks();
  const dataSinks = response.data.getAllFlows.dataSink;

  // Format data
  dataSinks.forEach((ds) => {
    ds.type = "DATA_SINK";
    ds.mvnCoordinates = `${ds.sourcePlugin.groupId}:${ds.sourcePlugin.artifactId}:${ds.sourcePlugin.version}`;
    ds.artifactId = ds.sourcePlugin.artifactId;
    ds.searchField = JSON.stringify({ name: ds.name, description: ds.description, mvnCoordinates: ds.mvnCoordinates });
  });

  flowData.value = dataSinks;
  const grouped = _.chain(flowData.value).sortBy("artifactId").groupBy("artifactId").value();
  const { "system-plugin": systemPlugin, ...rest } = grouped;
  flowDataByPlugin.value = systemPlugin ? { ...rest, "system-plugin": systemPlugin } : rest;
};

const dataSinkExport = computed(() => {
  const formatDataSinksList = flowData.value.map((ds) => _.pick(ds, ["name", "type", "description", "subscribe", "egressAction.name", "egressAction.type", "egressAction.parameters", "egressAction.apiVersion", "egressAction.join"]));
  return { dataSinks: formatDataSinksList };
});

const refresh = async () => {
  if (editing.value) return;
  await fetchFlows();
};
</script>
