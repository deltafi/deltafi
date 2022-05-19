<!--
   DeltaFi - Data transformation and enrichment platform

   Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>

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
      <Dropdown v-model="pluginNameSelected" placeholder="Select a Plugin" :options="pluginNames" option-label="name" show-clear :editable="false" class="deltafi-input-field ml-3 mr-2" @change="pluginNameChange" />
      <span class="p-input-icon-left">
        <i class="pi pi-search" />
        <InputText v-model="filterFlowsText" type="text" placeholder="Search" class="p-inputtext-sm deltafi-input-field flow-panel-search-txt" />
      </span>
    </div>
  </PageHeader>
  <div class="row pb-2">
    <div class="col pb-3">
      <h3>Ingress</h3>
      <template v-if="checkFlowsVisible(flowData['ingress'])">
        <Message severity="info" :closable="false">No Ingress flows found</Message>
      </template>
      <template v-for="(ingressFlowValue, ingressFlowKey) in flowData['ingress']" :key="ingressFlowKey">
        <FlowPanel :flow-data-prop="ingressFlowValue" />
      </template>
    </div>
    <template v-if="!_.isEmpty(flowData['enrich'])">
      <Divider layout="vertical" class="mx-0 flow-divider-color" />
      <div class="col pb-3">
        <h3>Enrich</h3>
        <template v-if="checkFlowsVisible(flowData['enrich'])">
          <Message severity="info" :closable="false">No Enrich flows found</Message>
        </template>
        <template v-for="(enrichFlowValue, enrichFlowKey) in flowData['enrich']" :key="enrichFlowKey">
          <FlowPanel :flow-data-prop="enrichFlowValue" />
        </template>
      </div>
    </template>
    <Divider layout="vertical" class="mx-0 flow-divider-color" />
    <div class="col pb-3">
      <h3>Egress</h3>
      <template v-if="checkFlowsVisible(flowData['egress'])">
        <Message severity="info" :closable="false">No Egress flows found</Message>
      </template>
      <template v-for="(egressFlowValue, egressFlowKey) in flowData['egress']" :key="egressFlowKey">
        <FlowPanel :flow-data-prop="egressFlowValue" />
      </template>
    </div>
  </div>
</template>

<script setup>
import FlowPanel from "@/components/FlowPanel.vue";
import PageHeader from "@/components/PageHeader.vue";
import useFlowQueryBuilder from "@/composables/useFlowQueryBuilder";
import { nextTick, onBeforeMount, ref, watch } from "vue";

import Divider from "primevue/divider";
import Dropdown from "primevue/dropdown";
import InputText from "primevue/inputtext";
import Message from "primevue/message";
import _ from "lodash";

const { getAllFlows } = useFlowQueryBuilder();

const allFlowData = ref("");
const flowData = ref("");
const filterFlowsText = ref("");
const pluginNames = ref([]);
const pluginNameSelected = ref(null);

onBeforeMount(async () => {
  fetchFlows();
});

watch(
  () => filterFlowsText.value,
  () => {
    applyFilters();
  }
);

const applyFilters = async () => {
  flowData.value = [];
  await nextTick();
  flowData.value = formatData(allFlowData.value);
};

const fetchFlows = async () => {
  let response = await getAllFlows();

  allFlowData.value = response.data.getAllFlows;
  pluginNames.value = pluginNamesList(allFlowData.value);
  applyFilters();
};

const checkFlowsVisible = (flowsList) => {
  if (_.isEmpty(flowsList)) {
    return false;
  }
  return flowsList.every((el) => el.visible === false);
};

const foundTextInObject = (flow) => {
  if (!_.isEmpty(filterFlowsText.value) && !_.isEmpty(pluginNameSelected.value)) {
    return flow["searchField"].toLowerCase().includes(pluginNameSelected.value.name.toLowerCase()) && flow["searchField"].toLowerCase().includes(filterFlowsText.value.toLowerCase());
  } else if (!_.isEmpty(pluginNameSelected.value)) {
    return flow["searchField"].toLowerCase().includes(pluginNameSelected.value.name.toLowerCase());
  } else {
    return flow["searchField"].toLowerCase().includes(filterFlowsText.value.toLowerCase());
  }
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

const pluginNameChange = () => {
  applyFilters();
};

const formatData = (allFlowData) => {
  let formattedFlowData = JSON.parse(JSON.stringify(allFlowData));
  const flowTypes = ["ingress", "enrich", "egress"];

  for (const flowType of flowTypes) {
    formattedFlowData[flowType.toString()].forEach((flow) => {
      flow["flowType"] = flowType;
      flow["visible"] = false;
      let mvnCoordinates = "";
      flow["mvnCoordinates"] = mvnCoordinates.concat(flow.sourcePlugin.groupId, ":", flow.sourcePlugin.artifactId, ":", flow.sourcePlugin.version);
      let searchableFlowKeys = (({ name, description, mvnCoordinates }) => ({ name, description, mvnCoordinates }))(flow);
      flow["searchField"] = Object.values(searchableFlowKeys).toString();
      if (!_.isEmpty(filterFlowsText.value) || !_.isEmpty(pluginNameSelected.value)) {
        if (foundTextInObject(flow)) {
          flow["visible"] = true;
        }
      } else {
        flow["visible"] = true;
      }
    });
  }
  return formattedFlowData;
};
</script>

<style lang="scss">
@import "@/styles/pages/flow-plans-page.scss";
</style>