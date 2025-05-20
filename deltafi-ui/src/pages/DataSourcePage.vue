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
        <div class="btn-toolbar">
          <IconField iconPosition="left">
            <InputIcon class="pi pi-search"> </InputIcon>
            <InputText v-model="filterFlowsText" type="text" placeholder="Search" class="p-inputtext deltafi-input-field mx-1" />
          </IconField>
          <DataSourcePageHeaderButtonGroup :export-data-sources="dataSourceExport" @reload-data-sources="refresh" />
        </div>
      </div>
    </PageHeader>
    <RestDataSourcesPanel ref="restDataSourcesPanel" :filter-flows-text-prop="filterFlowsText" @data-sources-list="exportableRestDataSource" />
    <TimedDataSourcesPanel ref="timedDataSourcesPanel" :filter-flows-text-prop="filterFlowsText" @data-sources-list="exportableTimedDataSource" />
  </div>
</template>

<script setup>
import DataSourcePageHeaderButtonGroup from "@/components/dataSources/DataSourcePageHeaderButtonGroup.vue";
import PageHeader from "@/components/PageHeader.vue";
import RestDataSourcesPanel from "@/components/dataSources/RestDataSourcesPanel.vue";
import TimedDataSourcesPanel from "@/components/dataSources/TimedDataSourcesPanel.vue";
import useTopics from "@/composables/useTopics";
import { computed, inject, onBeforeMount, onMounted, onUnmounted, provide, ref } from "vue";

import _ from "lodash";

import IconField from "primevue/iconfield";
import InputIcon from "primevue/inputicon";
import InputText from "primevue/inputtext";

const { getAllTopics } = useTopics();
const refreshInterval = 5000; // 5 seconds
const isIdle = inject("isIdle");
const restDataSourcesPanel = ref(null);
const timedDataSourcesPanel = ref(null);
const editing = ref(false);
const filterFlowsText = ref("");
provide("isEditing", editing);
let autoRefresh;

const refresh = async () => {
  restDataSourcesPanel.value.refresh();
  timedDataSourcesPanel.value.refresh();
};

onBeforeMount(async () => {
  await getAllTopics();
});

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

const restDataSourceList = ref([]);
const timedDataSourceList = ref([]);

const dataSourceExport = computed(() => {
  const dataSourceObject = {};
  dataSourceObject["restDataSources"] = restDataSourceList.value;
  dataSourceObject["timedDataSources"] = timedDataSourceList.value;
  return dataSourceObject;
});

const exportableRestDataSource = (value) => {
  const formatRestDataSourcesList = JSON.parse(JSON.stringify(value));
  formatRestDataSourcesList.forEach((e, index) => (formatRestDataSourcesList[index] = _.pick(e, ["name", "type", "description", "metadata", "annotationConfig", "topic"])));

  restDataSourceList.value = formatRestDataSourcesList;
};

const exportableTimedDataSource = (value) => {
  const formatTimedDataSourceList = JSON.parse(JSON.stringify(value));
  formatTimedDataSourceList.forEach((e, index) => (formatTimedDataSourceList[index] = _.pick(e, ["name", "type", "description", "metadata", "topic", "cronSchedule", "timedIngressAction.name", "timedIngressAction.type", "timedIngressAction.parameters", "timedIngressAction.apiVersion", "timedIngressAction.join", "annotationConfig.annotations", "annotationConfig.metadataPatterns", "annotationConfig.discardPrefix"])));

  timedDataSourceList.value = formatTimedDataSourceList;
};
</script>
