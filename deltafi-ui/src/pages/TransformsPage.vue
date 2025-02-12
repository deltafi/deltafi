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
      <div class="btn-toolbar mb-2 mb-md-0">
        <Dropdown v-model="pluginNameSelected" placeholder="Select a Plugin" :options="pluginNames" option-label="name" show-clear :editable="false" class="deltafi-input-field mx-1" />
        <span class="p-input-icon-left">
          <i class="pi pi-search" />
          <InputText v-model="filterFlowsText" type="text" placeholder="Search" class="p-inputtext-sm deltafi-input-field flow-panel-search-txt mx-1" />
        </span>
        <PermissionedRouterLink :disabled="!$hasPermission('FlowUpdate')" :to="{ path: 'transform-builder' }">
          <Button v-has-permission:FlowCreate label="Add Transform" icon="pi pi-plus" class="p-button-sm p-button-outlined mx-1" />
        </PermissionedRouterLink>
      </div>
    </PageHeader>
    <ProgressBar v-if="showLoading" mode="indeterminate" style="height: 0.5em" />
    <div v-else>
      <FlowDataTable flow-type-prop="transform" :flow-data-prop="flowData" :plugin-name-selected-prop="pluginNameSelected" :filter-flows-text-prop="filterFlowsText" :flow-data-by-plugin-prop="flowDataByPlugin" @update-flows="fetchFlows()" />
    </div>
  </div>
</template>

<script setup>
import PermissionedRouterLink from "@/components/PermissionedRouterLink.vue";
import ProgressBar from "@/components/deprecatedPrimeVue/ProgressBar.vue";
import FlowDataTable from "@/components/flow/FlowDataTable.vue";
import PageHeader from "@/components/PageHeader.vue";
import useFlowQueryBuilder from "@/composables/useFlowQueryBuilder";
import { onBeforeMount, ref, computed } from "vue";
import _ from "lodash";

import Button from "primevue/button";
import Dropdown from "primevue/dropdown";
import InputText from "primevue/inputtext";

const { getAllFlows, loaded, loading } = useFlowQueryBuilder();

const allFlowData = ref("");
const flowData = ref({});
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
  allFlowData.value = response.data.getAllFlows;
  pluginNames.value = pluginNamesList(allFlowData.value);
  flowData.value = formatData(allFlowData.value);
  flowDataByPlugin.value = _.chain(flowData.value.transform).sortBy("artifactId").groupBy("artifactId").value();
};

const pluginNamesList = (allFlowData) => {
  const mvnCoordinatesArray = [];
  Object.values(allFlowData).forEach((flowTypes) => {
    Object.values(flowTypes).forEach((flow) => {
      let mvnCoordinates = "";
      mvnCoordinates = mvnCoordinates.concat(flow.sourcePlugin.groupId, ":", flow.sourcePlugin.artifactId, ":", flow.sourcePlugin.version);
      mvnCoordinatesArray.push({ name: mvnCoordinates });
    });
  });

  return _.uniqBy(mvnCoordinatesArray, "name").sort((a, b) => a.name.localeCompare(b.name));
};

const formatData = (allFlowData) => {
  const formattedFlowData = JSON.parse(JSON.stringify(allFlowData));
  const flowTypes = ["transform"];

  for (const flowType of flowTypes) {
    formattedFlowData[flowType.toString()].forEach((flow) => {
      flow["flowType"] = flowType;
      const mvnCoordinates = "";
      flow["mvnCoordinates"] = mvnCoordinates.concat(flow.sourcePlugin.groupId, ":", flow.sourcePlugin.artifactId, ":", flow.sourcePlugin.version);
      const searchableFlowKeys = (({ name, description, mvnCoordinates, publish }) => ({ name, description, mvnCoordinates, publish }))(flow);
      flow["searchField"] = JSON.stringify(Object.values(searchableFlowKeys));
      flow["artifactId"] = flow.sourcePlugin.artifactId;
    });
  }
  return formattedFlowData;
};
</script>

<style>
.transforms-page {
  .flow-panel-search-txt {
    font-size: 1rem !important;
  }

  .p-divider.p-component.p-divider-vertical.p-divider-solid.p-divider-center.mx-0.flow-divider-color:before {
    border-left: 1px #f7f8fa !important;
    border-left-style: solid !important;
  }
}
</style>
