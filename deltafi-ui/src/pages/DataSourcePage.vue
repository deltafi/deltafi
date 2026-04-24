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

<!-- ABOUTME: Data Sources page with plugin-based grouping. -->
<!-- ABOUTME: Displays REST, Timed, and OnError data sources grouped by plugin. -->

<template>
  <div class="data-sources-page">
    <PageHeader heading="Data Sources">
      <DataSourcePageHeaderButtonGroup :export-data-sources="dataSourceExport" @reload-data-sources="refresh" />
    </PageHeader>
    <ProgressBar v-if="showLoading" mode="indeterminate" style="height: 0.5em" />
    <div v-else>
      <div class="d-flex justify-content-between">
        <IconField iconPosition="left" class="mb-3">
          <InputIcon class="pi pi-search"></InputIcon>
          <InputText v-model="filterFlowsText" type="text" placeholder="Search" aria-label="Search data sources" class="p-inputtext deltafi-input-field" />
        </IconField>
        <div class="kill-switch" >
          <span class="mr-2 text-muted">Ingress Enabled</span>
          <SystemPropertySwitch class="mr-1" property-name="ingressEnabled"
            off-confirmation="Are you sure? This will stop all data from flowing into the system."
            off-tooltip="Enable ingress" on-tooltip="Disable ingress" />
        </div>
      </div>
      <DataSourceDataTable
        :flow-data-prop="flowData"
        :flow-data-by-plugin-prop="flowDataByPlugin"
        :filter-flows-text-prop="filterFlowsText"
        @reload-data-sources="refresh"
        @update-rate-limit="updateRateLimit"
      />
    </div>
  </div>
</template>

<script setup>
import DataSourceDataTable from "@/components/dataSources/DataSourceDataTable.vue";
import DataSourcePageHeaderButtonGroup from "@/components/dataSources/DataSourcePageHeaderButtonGroup.vue";
import PageHeader from "@/components/PageHeader.vue";
import ProgressBar from "primevue/progressbar";
import SystemPropertySwitch from "@/components/SystemPropertySwitch.vue";
import useDataSource from "@/composables/useDataSource";
import useTopics from "@/composables/useTopics";
import { computed, inject, onMounted, onUnmounted, provide, ref } from "vue";
import ToggleButton from 'primevue/togglebutton';

import _ from "lodash";

import IconField from "primevue/iconfield";
import InputIcon from "primevue/inputicon";
import InputText from "primevue/inputtext";

const { getAllDataSources, loaded, loading } = useDataSource();
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
  await Promise.all([
    clearTopicsCache(), 
    getAllTopics(),
    fetchFlows(),
  ]);

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
  const response = await getAllDataSources();
  const data = response.data.getAllFlows;

  // Combine all data source types with type indicator
  const allDataSources = [
    ...data.restDataSource.map(ds => ({ ...ds, dataSourceType: "REST_DATA_SOURCE", type: "REST_DATA_SOURCE" })),
    ...data.timedDataSource.map(ds => ({ ...ds, dataSourceType: "TIMED_DATA_SOURCE", type: "TIMED_DATA_SOURCE" })),
    ...data.onErrorDataSource.map(ds => ({ ...ds, dataSourceType: "ON_ERROR_DATA_SOURCE", type: "ON_ERROR_DATA_SOURCE" })),
  ];

  // Format and normalize data
  allDataSources.forEach(ds => {
    ds.mvnCoordinates = `${ds.sourcePlugin.groupId}:${ds.sourcePlugin.artifactId}:${ds.sourcePlugin.version}`;
    ds.artifactId = ds.sourcePlugin.artifactId;
    ds.searchField = JSON.stringify({ name: ds.name, description: ds.description, mvnCoordinates: ds.mvnCoordinates, topic: ds.topic });
    ds.maxErrors = ds.maxErrors !== -1 ? ds.maxErrors : null;
  });

  flowData.value = allDataSources;
  const grouped = _.chain(flowData.value).sortBy("artifactId").groupBy("artifactId").value();
  const { 'system-plugin': systemPlugin, ...rest } = grouped;
  flowDataByPlugin.value = systemPlugin ? { ...rest, 'system-plugin': systemPlugin } : rest;
};

const dataSourceExport = computed(() => {
  const restDataSources = flowData.value
    .filter(ds => ds.dataSourceType === "REST_DATA_SOURCE")
    .map(ds => _.pick(ds, ["name", "type", "description", "metadata", "annotationConfig", "topic"]));

  const timedDataSources = flowData.value
    .filter(ds => ds.dataSourceType === "TIMED_DATA_SOURCE")
    .map(ds => _.pick(ds, ["name", "type", "description", "metadata", "topic", "cronSchedule", "timedIngressAction.name", "timedIngressAction.type", "timedIngressAction.parameters", "timedIngressAction.apiVersion", "timedIngressAction.join", "annotationConfig.annotations", "annotationConfig.metadataPatterns", "annotationConfig.discardPrefix"]));

  const onErrorDataSources = flowData.value
    .filter(ds => ds.dataSourceType === "ON_ERROR_DATA_SOURCE")
    .map(ds => _.pick(ds, ["name", "type", "description", "metadata", "annotationConfig", "topic", "errorMessageRegex", "sourceMetadataPrefix", "includeSourceMetadataRegex"]));

  return {
    restDataSources,
    timedDataSources,
    onErrorDataSources,
  };
});

const updateRateLimit = ({ name, rateLimit }) => {
  const dataSource = flowData.value.find((ds) => ds.name === name);
  if (dataSource) {
    dataSource.rateLimit = rateLimit;
  }
};

const refresh = async () => {
  if (editing.value) return;
  await fetchFlows();
};
</script>
