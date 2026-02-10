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

<template>
  <div class="transforms-page">
    <PageHeader heading="Transforms">
      <FlowPageHeaderButtonGroup :export-transforms="transformsExport" @reload-transforms="refresh" />
    </PageHeader>
    <ProgressBar v-if="showLoading" mode="indeterminate" style="height: 0.5em" />
    <div v-else>
      <IconField iconPosition="left" class="mb-3">
        <InputIcon class="pi pi-search"></InputIcon>
        <InputText v-model="filterFlowsText" type="text" placeholder="Search" class="p-inputtext deltafi-input-field" />
      </IconField>
      <FlowDataTable flow-type-prop="transform" :flow-data-prop="flowData" :filter-flows-text-prop="filterFlowsText" :flow-data-by-plugin-prop="flowDataByPlugin" @reload-transforms="refresh" />
    </div>
  </div>
</template>

<script setup>
import FlowDataTable from "@/components/flow/FlowDataTable.vue";
import FlowPageHeaderButtonGroup from "@/components/flow/FlowPageHeaderButtonGroup.vue";
import PageHeader from "@/components/PageHeader.vue";
import ProgressBar from "@/components/deprecatedPrimeVue/ProgressBar.vue";
import useFlowQueryBuilder from "@/composables/useFlowQueryBuilder";
import useTopics from "@/composables/useTopics";
import { onMounted, ref, computed } from "vue";
import _ from "lodash";

import IconField from "primevue/iconfield";
import InputIcon from "primevue/inputicon";
import InputText from "primevue/inputtext";

const { getAllFlows, loaded, loading } = useFlowQueryBuilder();
const { clearTopicsCache, getAllTopics, loaded: topicsLoaded, loading: topicsLoading } = useTopics();

const transformsFlowData = ref([]);
const flowData = ref([]);
const flowDataByPlugin = ref({});
const filterFlowsText = ref("");
const showLoading = computed(() => (!loaded.value && loading.value) || (!topicsLoaded.value && topicsLoading.value));

onMounted(async () => {
  await Promise.all([clearTopicsCache(), getAllTopics(), fetchFlows()]);
});

const fetchFlows = async () => {
  const response = await getAllFlows();

  transformsFlowData.value = response.data.getAllFlows.transform;
  flowData.value = formatData(transformsFlowData.value);
  const grouped = _.chain(flowData.value).sortBy("artifactId").groupBy("artifactId").value();
  const { "system-plugin": systemPlugin, ...rest } = grouped;
  flowDataByPlugin.value = systemPlugin ? { ...rest, "system-plugin": systemPlugin } : rest;
};

const formatData = (data) => {
  const formattedFlowData = JSON.parse(JSON.stringify(data));

  formattedFlowData.forEach((flow) => {
    flow["flowType"] = "transform";
    const mvnCoordinates = "";
    flow["mvnCoordinates"] = mvnCoordinates.concat(flow.sourcePlugin.groupId, ":", flow.sourcePlugin.artifactId, ":", flow.sourcePlugin.version);
    const searchableFlowKeys = (({ name, description, mvnCoordinates, publish }) => ({ name, description, mvnCoordinates, publish }))(flow);
    flow["searchField"] = JSON.stringify(Object.values(searchableFlowKeys));
    flow["artifactId"] = flow.sourcePlugin.artifactId;
  });

  return formattedFlowData;
};

const transformsExport = computed(() => {
  const formatTransformsList = JSON.parse(JSON.stringify(flowData.value));
  formatTransformsList.forEach((e, index) => (formatTransformsList[index] = _.pick(e, ["name", "type", "description", "subscribe", "transformActions", "publish.matchingPolicy", "publish.defaultRule", "publish.rules"])));

  const transformsObject = {};
  transformsObject["transforms"] = formatTransformsList;
  return transformsObject;
});
const refresh = async () => {
  await fetchFlows();
};
</script>
<style>
.transforms-page {
  .p-divider.p-component.p-divider-vertical.p-divider-solid.p-divider-center.mx-0.flow-divider-color:before {
    border-left: 1px #f7f8fa !important;
    border-left-style: solid !important;
  }
}
</style>
