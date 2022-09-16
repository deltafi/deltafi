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
  <div>
    <PageHeader heading="Ingress Routing">
      <div class="d-flex mb-2">
        <Button label="Export Rules" icon="fas fa-download fa-fw" class="p-button-sm p-button-secondary p-button-outlined mx-1" @click="exportDeletePolicies()" />
        <DialogTemplate component-name="ingressRouting/IngressRoutingImportFile" header="Import Ingress Routes" dialog-width="40vw" @reload-ingress-routes="fetchIngressRoutes()">
          <Button label="Import Rules" icon="fas fa-upload fa-fw" class="p-button-sm p-button-secondary p-button-outlined mx-1" />
        </DialogTemplate>
        <DialogTemplate component-name="ingressRouting/IngressRoutingConfigurationDialog" header="Add New Ingress Route Rule" dialog-width="25vw" :row-data-prop="{}" @reload-ingress-routes="fetchIngressRoutes()">
          <Button label="Add Rule" icon="pi pi-plus" class="p-button-sm p-button-outlined mx-1" />
        </DialogTemplate>
      </div>
    </PageHeader>
    <Panel header="Rules" class="ingress-routing-panel table-panel">
      <template #icons>
        <span class="p-input-icon-left">
          <i class="pi pi-search" />
          <InputText v-model="filters['global'].value" v-tooltip.left="'Keyword Search on Name and Flow'" placeholder="Keyword Search" />
        </span>
      </template>
      <DataTable v-model:filters="filters" :value="uiIngressRoutes" data-Key="id" responsive-layout="scroll" striped-rows class="p-datatable-sm p-datatable-gridlines" :global-filter-fields="['name', 'flow', 'priority', 'matcherType', 'matcherValue']">
        <template #empty> No Ingress Routing rules to display </template>
        <Column field="name" header="Name" :sortable="true" :style="{ width: 'auto' }">
          <template #body="{ data }">
            <div class="justify-content-start">
              <DialogTemplate component-name="ingressRouting/IngressRoutingConfigurationDialog" header="View Ingress Route Rule" dialog-width="25vw" :row-data-prop="data" view-ingress-route-rule @reload-ingress-routes="fetchIngressRoutes()">
                <Button :label="data.name" class="p-button-link text-body" />
              </DialogTemplate>
            </div>
          </template>
        </Column>
        <Column field="flow" header="Flow" :sortable="true" :style="{ width: 'auto' }"></Column>
        <Column field="priority" header="Priority" :sortable="true" :style="{ width: '5rem' }"></Column>
        <Column field="filenameRegex" header="Filename Regex" :sortable="true" :style="{ width: '11rem' }"></Column>
        <Column field="requiredMetadata" header="Required Metadata" :sortable="true" :style="{ width: 'auto' }">
          <template #body="{ data }">
            <div v-for="item in viewList(data.requiredMetadata)" :key="item">{{ item }}</div>
          </template>
        </Column>
        <Column :style="{ width: '5rem' }">
          <template #body="{ data }">
            <div class="d-flex">
              <DialogTemplate component-name="ingressRouting/IngressRoutingConfigurationDialog" header="Edit Ingress Route Rule" dialog-width="25vw" :row-data-prop="data" @reload-ingress-routes="fetchIngressRoutes()">
                <Button v-tooltip.top="`Edit Rule`" icon="pi pi-pencil" class="p-button-text p-button-sm p-button-rounded p-button-secondary" />
              </DialogTemplate>
              <IngressRoutingRemoveButton class="pl-2" :row-data-prop="data" @reload-ingress-routes="fetchIngressRoutes()" />
            </div>
          </template>
        </Column>
      </DataTable>
    </Panel>
  </div>
</template>

<script setup>
import IngressRoutingRemoveButton from "@/components/ingressRouting/IngressRoutingRemoveButton.vue";
import DialogTemplate from "@/components/DialogTemplate.vue";
import PageHeader from "@/components/PageHeader.vue";
import useIngressRoutingQueryBuilder from "@/composables/useIngressRoutingQueryBuilder";
import { nextTick, onMounted, ref } from "vue";
import { FilterMatchMode } from "primevue/api";

import Button from "primevue/button";
import Column from "primevue/column";
import DataTable from "primevue/datatable";
import InputText from "primevue/inputtext";
import Panel from "primevue/panel";

import _ from "lodash";

const ingressRoutes = ref([]);
const uiIngressRoutes = ref([]);
const { getAllFlowAssignmentRules } = useIngressRoutingQueryBuilder();

onMounted(async () => {
  fetchIngressRoutes();
});

const fetchIngressRoutes = async () => {
  let ingressRouteResponse = await getAllFlowAssignmentRules();
  ingressRoutes.value = [];
  uiIngressRoutes.value = [];
  await nextTick();
  ingressRoutes.value = ingressRouteResponse.data.getAllFlowAssignmentRules;
  uiIngressRoutes.value = _.assign(ingressRoutes.value);
};

const filters = ref({
  global: { value: null, matchMode: FilterMatchMode.CONTAINS },
});

const viewList = (value) => {
  if (_.isEmpty(value)) {
    return null;
  }
  // Combine objects of Key Name and Value Name into a key value pair
  let combineKeyValue = value.reduce((r, { key, value }) => ((r[key] = value), r), {});

  // Turn Object into string
  let stringifyCombineKeyValue = Object.entries(combineKeyValue)
    .map(([k, v]) => `${k}: ${v}`)
    .join(", ");

  return stringifyCombineKeyValue.split(",").map((i) => i.trim());
};

const formatExportIngressRoutesData = () => {
  let ingressRoutesList = JSON.parse(JSON.stringify(ingressRoutes.value));
  // Remove the id key from route
  ingressRoutesList.forEach((e, index) => (ingressRoutesList[index] = _.omit(e, ["id"])));

  return ingressRoutesList;
};

const exportDeletePolicies = () => {
  let link = document.createElement("a");
  let downloadFileName = "ingress_route_export_" + new Date(Date.now()).toLocaleDateString();
  link.download = downloadFileName.toLowerCase();
  let blob = new Blob([JSON.stringify(formatExportIngressRoutesData(), null, 2)], {
    type: "application/JSON",
  });
  link.href = URL.createObjectURL(blob);
  link.click();
  URL.revokeObjectURL(link.href);
  link.remove();
};
</script>

<style lang="scss">
@import "@/styles/pages/ingress-routing-configuration-page.scss";
</style>
