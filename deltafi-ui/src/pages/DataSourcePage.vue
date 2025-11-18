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
    <PageHeader heading="Data Sources">
      <div class="d-flex">
        <SystemPropertySwitch class="mt-1 mr-1" property-name="ingressEnabled"
          off-confirmation="Are you sure? This will stop all data from flowing into the system."
          off-tooltip="Enable ingress" on-tooltip="Disable ingress" />
        <div class="btn-toolbar">
          <IconField iconPosition="left">
            <InputIcon class="pi pi-search"> </InputIcon>
            <InputText v-model="filterFlowsText" type="text" placeholder="Search" class="p-inputtext deltafi-input-field mx-1" />
          </IconField>
          <DataSourcePageHeaderButtonGroup :export-data-sources="dataSourceExport" @reload-data-sources="refresh" />
        </div>
      </div>
    </PageHeader>
    <ProgressBar v-if="showLoading" mode="indeterminate" style="height: 0.5em" />
    <div v-else>
      <RestDataSourcesPanel ref="restDataSourcesPanel" :filter-flows-text-prop="filterFlowsText" @data-sources-list="exportableRestDataSource" />
      <TimedDataSourcesPanel ref="timedDataSourcesPanel" :filter-flows-text-prop="filterFlowsText" @data-sources-list="exportableTimedDataSource" />
      <OnErrorDataSourcesPanel ref="onErrorDataSourcesPanel" :filter-flows-text-prop="filterFlowsText" @data-sources-list="exportableOnErrorDataSource" />
    </div>
  </div>
</template>

<script setup>
import DataSourcePageHeaderButtonGroup from "@/components/dataSources/DataSourcePageHeaderButtonGroup.vue";
import ProgressBar from "@/components/deprecatedPrimeVue/ProgressBar.vue";
import PageHeader from "@/components/PageHeader.vue";
import RestDataSourcesPanel from "@/components/dataSources/RestDataSourcesPanel.vue";
import OnErrorDataSourcesPanel from "@/components/dataSources/OnErrorDataSourcePanel.vue";
import TimedDataSourcesPanel from "@/components/dataSources/TimedDataSourcesPanel.vue";
import useTopics from "@/composables/useTopics";
import SystemPropertySwitch from "@/components/SystemPropertySwitch.vue";
import { computed, inject, onMounted, onUnmounted, provide, ref } from "vue";

import _ from "lodash";

import IconField from "primevue/iconfield";
import InputIcon from "primevue/inputicon";
import InputText from "primevue/inputtext";

const { getAllTopics, loaded, loading } = useTopics();
const refreshInterval = 5000; // 5 seconds
const isIdle = inject("isIdle");
const restDataSourcesPanel = ref(null);
const onErrorDataSourcesPanel = ref(null);
const timedDataSourcesPanel = ref(null);
const editing = ref(false);
const filterFlowsText = ref("");
provide("isEditing", editing);
let autoRefresh;

const refresh = async () => {
  restDataSourcesPanel.value.refresh();
  onErrorDataSourcesPanel.value.refresh();
  timedDataSourcesPanel.value.refresh();
};

const showLoading = computed(() => !loaded.value && loading.value);

onMounted(async () => {
  await getAllTopics();

  autoRefresh = setInterval(() => {
    if (!isIdle.value) {
      refresh();
    }
  }, refreshInterval);
});


onUnmounted(() => {
  clearInterval(autoRefresh);
});

const restDataSourceList = ref([]);
const onErrorDataSourceList = ref([]);
const timedDataSourceList = ref([]);

const dataSourceExport = computed(() => {
  const dataSourceObject = {};
  dataSourceObject["restDataSources"] = restDataSourceList.value;
  dataSourceObject["onErrorDataSources"] = onErrorDataSourceList.value;
  dataSourceObject["timedDataSources"] = timedDataSourceList.value;
  return dataSourceObject;
});

const exportableRestDataSource = (value) => {
  const formatRestDataSourcesList = JSON.parse(JSON.stringify(value));
  formatRestDataSourcesList.forEach((e, index) => (formatRestDataSourcesList[index] = _.pick(e, ["name", "type", "description", "metadata", "annotationConfig", "topic"])));

  restDataSourceList.value = formatRestDataSourcesList;
};

const exportableOnErrorDataSource = (value) => {
  const formatOnErrorDataSourcesList = JSON.parse(JSON.stringify(value));
  formatOnErrorDataSourcesList.forEach((e, index) => (formatOnErrorDataSourcesList[index] = _.pick(e, ["name", "type", "description", "metadata", "annotationConfig", "topic", "errorMessageRegex", "sourceMetadataPrefix", "includeSourceMetadataRegex"])));

  onErrorDataSourceList.value = formatOnErrorDataSourcesList;
};

const exportableTimedDataSource = (value) => {
  const formatTimedDataSourceList = JSON.parse(JSON.stringify(value));
  formatTimedDataSourceList.forEach((e, index) => (formatTimedDataSourceList[index] = _.pick(e, ["name", "type", "description", "metadata", "topic", "cronSchedule", "timedIngressAction.name", "timedIngressAction.type", "timedIngressAction.parameters", "timedIngressAction.apiVersion", "timedIngressAction.join", "annotationConfig.annotations", "annotationConfig.metadataPatterns", "annotationConfig.discardPrefix"])));

  timedDataSourceList.value = formatTimedDataSourceList;
};
</script>
