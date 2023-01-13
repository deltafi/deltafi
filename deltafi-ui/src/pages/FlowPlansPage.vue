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
  <PageHeader heading="Flows">
    <div class="btn-toolbar mb-2 mb-md-0">
      <DialogTemplate component-name="flow/FlowConfiguration" header="Flow Configuration">
        <Button v-tooltip.top.hover="'View Flow Configuration'" label="Flow Configuration" class="p-button-sm p-button-secondary p-button-outlined ml-3" />
      </DialogTemplate>
      <Dropdown v-model="pluginNameSelected" placeholder="Select a Plugin" :options="pluginNames" option-label="name" show-clear :editable="false" class="deltafi-input-field ml-3 mr-3" />
      <span class="p-input-icon-left">
        <i class="pi pi-search" />
        <InputText v-model="filterFlowsText" type="text" placeholder="Search" class="p-inputtext-sm deltafi-input-field flow-panel-search-txt" />
      </span>
    </div>
  </PageHeader>
  <FlowDataTable flow-type-prop="ingress" :flow-data-prop="flowData" :plugin-name-selected-prop="pluginNameSelected" :filter-flows-text-prop="filterFlowsText" @update-flows="fetchFlows()"></FlowDataTable>
  <FlowDataTable flow-type-prop="enrich" :flow-data-prop="flowData" :plugin-name-selected-prop="pluginNameSelected" :filter-flows-text-prop="filterFlowsText" @update-flows="fetchFlows()"></FlowDataTable>
  <FlowDataTable flow-type-prop="egress" :flow-data-prop="flowData" :plugin-name-selected-prop="pluginNameSelected" :filter-flows-text-prop="filterFlowsText" @update-flows="fetchFlows()"></FlowDataTable>
</template>

<script setup>
import DialogTemplate from "@/components/DialogTemplate.vue";
import FlowDataTable from "@/components/flow/FlowDataTable.vue";
import PageHeader from "@/components/PageHeader.vue";
import useFlowQueryBuilder from "@/composables/useFlowQueryBuilder";
import { onBeforeMount, ref } from "vue";

import Button from "primevue/button";
import Dropdown from "primevue/dropdown";
import InputText from "primevue/inputtext";
import _ from "lodash";

const { getAllFlows } = useFlowQueryBuilder();

const allFlowData = ref("");
const flowData = ref({});
const filterFlowsText = ref("");
const pluginNames = ref([]);
const pluginNameSelected = ref(null);

onBeforeMount(async () => {
  await fetchFlows();
});

const fetchFlows = async () => {
  let response = await getAllFlows();
  allFlowData.value = response.data.getAllFlows;
  pluginNames.value = pluginNamesList(allFlowData.value);
  flowData.value = formatData(allFlowData.value);
};

const pluginNamesList = (allFlowData) => {
  let mvnCoordinatesArray = [];
  Object.values(allFlowData).forEach((flowTypes) => {
    Object.values(flowTypes).forEach((flow) => {
      let mvnCoordinates = "";
      mvnCoordinates = mvnCoordinates.concat(flow.sourcePlugin.groupId, ":", flow.sourcePlugin.artifactId, ":", flow.sourcePlugin.version);
      mvnCoordinatesArray.push({ name: mvnCoordinates });
    });
  });
  return _.uniqBy(mvnCoordinatesArray, "name");
};
const formatData = (allFlowData) => {
  let formattedFlowData = JSON.parse(JSON.stringify(allFlowData));
  const flowTypes = ["ingress", "enrich", "egress"];

  for (const flowType of flowTypes) {
    formattedFlowData[flowType.toString()].forEach((flow) => {
      flow["flowType"] = flowType;
      let mvnCoordinates = "";
      flow["mvnCoordinates"] = mvnCoordinates.concat(flow.sourcePlugin.groupId, ":", flow.sourcePlugin.artifactId, ":", flow.sourcePlugin.version);
      let searchableFlowKeys = (({ name, description, mvnCoordinates }) => ({ name, description, mvnCoordinates }))(flow);
      flow["searchField"] = Object.values(searchableFlowKeys).toString();
    });
  }
  return formattedFlowData;
};
</script>

<style lang="scss">
@import "@/styles/pages/flow-plans-page.scss";
</style>
