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
  <div class="transforms-page">
    <PageHeader heading="Transforms">
      <div class="btn-toolbar align-items-center">
        <Dropdown v-model="pluginNameSelected" placeholder="Select a Plugin" :options="pluginNames" option-label="name" show-clear :editable="false" class="deltafi-input-field mx-1 transform-dropdown" />
        <IconField iconPosition="left">
          <InputIcon class="pi pi-search"> </InputIcon>
          <InputText v-model="filterFlowsText" type="text" placeholder="Search" class="p-inputtext deltafi-input-field mx-1" />
        </IconField>
        <FlowPageHeaderButtonGroup :export-transforms="transformsExport" @reload-transforms="refresh" />
      </div>
    </PageHeader>
    <ProgressBar v-if="showLoading" mode="indeterminate" style="height: 0.5em" />
    <div v-else>
      <FlowDataTable flow-type-prop="transform" :flow-data-prop="flowData" :plugin-name-selected-prop="pluginNameSelected" :filter-flows-text-prop="filterFlowsText" :flow-data-by-plugin-prop="flowDataByPlugin" @reload-transforms="refresh" />
    </div>
  </div>
</template>

<script setup>
import FlowDataTable from "@/components/flow/FlowDataTable.vue";
import FlowPageHeaderButtonGroup from "@/components/flow/FlowPageHeaderButtonGroup.vue";
import PageHeader from "@/components/PageHeader.vue";
import ProgressBar from "@/components/deprecatedPrimeVue/ProgressBar.vue";
import useFlowQueryBuilder from "@/composables/useFlowQueryBuilder";
import { onBeforeMount, ref, computed } from "vue";
import _ from "lodash";

import Dropdown from "primevue/dropdown";
import IconField from "primevue/iconfield";
import InputIcon from "primevue/inputicon";
import InputText from "primevue/inputtext";

const { getAllFlows, loaded, loading } = useFlowQueryBuilder();

const transformsFlowData = ref([]);
const flowData = ref([]);
const flowDataByPlugin = ref({});
const filterFlowsText = ref("");
const pluginNames = ref([]);
const pluginNameSelected = ref(null);
const showLoading = computed(() => !loaded.value && loading.value);

onBeforeMount(async () => {
  await fetchFlows();
});

const fetchFlows = async () => {
  const response = await getAllFlows();

  transformsFlowData.value = response.data.getAllFlows.transform;
  pluginNames.value = pluginNamesList(transformsFlowData.value);
  flowData.value = formatData(transformsFlowData.value);
  flowDataByPlugin.value = _.chain(flowData.value).sortBy("artifactId").groupBy("artifactId").value();
};

const pluginNamesList = (data) => {
  const mvnCoordinatesArray = [];

  data.forEach((flow) => {
    let mvnCoordinates = "";
    mvnCoordinates = mvnCoordinates.concat(flow.sourcePlugin.groupId, ":", flow.sourcePlugin.artifactId, ":", flow.sourcePlugin.version);
    mvnCoordinatesArray.push({ name: mvnCoordinates });
  });

  return _.uniqBy(mvnCoordinatesArray, "name").sort((a, b) => a.name.localeCompare(b.name));
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
  .transform-dropdown {
    width: 16rem;
  }

  .p-divider.p-component.p-divider-vertical.p-divider-solid.p-divider-center.mx-0.flow-divider-color:before {
    border-left: 1px #f7f8fa !important;
    border-left-style: solid !important;
  }
}
</style>
