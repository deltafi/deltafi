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
    <PageHeader heading="Delete Policies">
      <div class="d-flex mb-2">
        <Button label="Export Policies" icon="fas fa-download fa-fw" class="p-button-sm p-button-secondary p-button-outlined mx-1" @click="exportDeletePolicies" />
        <DeletePolicyImportFile @reload-delete-policies="fetchDeletePolicies()" />
        <div>
          <DialogTemplate component-name="deletePolicy/DeletePolicyConfigurationDialog" header="Add New Delete Policy" dialog-width="25vw" @reload-delete-policies="fetchDeletePolicies()">
            <Button label="Add Policy" icon="pi pi-plus" class="p-button-sm p-button-outlined mx-1" />
          </DialogTemplate>
        </div>
      </div>
    </PageHeader>
    <Panel header="Delete Policies" class="delete-policy-panel table-panel">
      <template #icons>
        <span class="p-input-icon-left">
          <i class="pi pi-search" />
          <InputText v-model="filters['global'].value" v-tooltip.left="'Search on Name and Flow'" placeholder="Search" />
        </span>
      </template>
      <DataTable v-model:filters="filters" :value="uiDeletePoliciesList" data-Key="id" responsive-layout="scroll" striped-rows class="p-datatable-sm p-datatable-gridlines" :global-filter-fields="['name', 'flow']" :row-hover="true">
        <template #empty> No delete policies to display </template>
        <Column field="name" header="Name" :sortable="true" :style="{ width: '40%' }">
          <template #body="{ data }">
            <DialogTemplate component-name="deletePolicy/DeletePolicyConfigurationDialog" header="View Delete Policy" dialog-width="25vw" :row-data-prop="data" view-delete-policy @reload-delete-policies="fetchDeletePolicies()">
              <a class="cursor-pointer" style="color: black">{{ data.name }}</a>
            </DialogTemplate>
          </template>
        </Column>
        <Column field="flow" header="Flow" :sortable="true" :style="{ width: '30%' }"></Column>
        <Column field="__typename" header="Type" :sortable="true" :style="{ width: '10%' }">
          <template #body="{ data }">
            <i v-tooltip.right="deletePolicyType.get(data.__typename).tooltip" :class="deletePolicyType.get(data.__typename).class" />
          </template>
        </Column>
        <Column field="locked" header="Locked" :sortable="true" :style="{ width: '10%' }">
          <template #body="{ data }">
            <i v-tooltip.right="deletePolicyLocked.get(data.locked).tooltip" :class="deletePolicyLocked.get(data.locked).class" />
          </template>
        </Column>
        <Column :style="{ width: '10%' }" :body-style="{ padding: 0 }">
          <template #body="{ data }">
            <div class="d-flex">
              <DialogTemplate component-name="deletePolicy/DeletePolicyConfigurationDialog" header="Update Delete Policy" dialog-width="25vw" :row-data-prop="data" edit-delete-policy @reload-delete-policies="fetchDeletePolicies()">
                <Button v-tooltip.top="`Edit Delete Policy`" icon="pi pi-pencil" class="p-button-text p-button-sm p-button-rounded p-button-secondary" />
              </DialogTemplate>
              <DeletePolicyRemoveButton class="pl-2" :row-data-prop="data" @reload-delete-policies="fetchDeletePolicies()"></DeletePolicyRemoveButton>
              <DeletePolicyStateInputSwitch class="pl-2 pt-1" :row-data-prop="data" @reload-delete-policies="fetchDeletePolicies()"></DeletePolicyStateInputSwitch>
            </div>
          </template>
        </Column>
      </DataTable>
    </Panel>
  </div>
</template>

<script setup>
import DeletePolicyImportFile from "@/components/deletePolicy/DeletePolicyImportFile";
import DeletePolicyRemoveButton from "@/components/deletePolicy/DeletePolicyRemoveButton.vue";
import DeletePolicyStateInputSwitch from "@/components/deletePolicy/DeletePolicyStateInputSwitch.vue";
import DialogTemplate from "@/components/DialogTemplate.vue";
import PageHeader from "@/components/PageHeader.vue";
import useDeletePolicyQueryBuilder from "@/composables/useDeletePolicyQueryBuilder";
import { computed, nextTick, onMounted, ref } from "vue";
import { FilterMatchMode } from "primevue/api";

import Button from "primevue/button";
import Column from "primevue/column";
import DataTable from "primevue/datatable";
import InputText from "primevue/inputtext";
import Panel from "primevue/panel";

import _ from "lodash";

const deletePolicies = ref([]);
const { getDeletePolicies } = useDeletePolicyQueryBuilder();

onMounted(async () => {
  fetchDeletePolicies();
});

const fetchDeletePolicies = async () => {
  let deletePoliciesResponse = await getDeletePolicies();
  deletePolicies.value = [];
  await nextTick();
  deletePolicies.value = deletePoliciesResponse.data.getDeletePolicies;
};

const filters = ref({
  global: { value: null, matchMode: FilterMatchMode.CONTAINS },
});

const deletePolicyType = new Map([
  ["TimedDeletePolicy", { tooltip: "Timed Delete Policy", class: "far fa-clock text-muted ml-1" }],
  ["DiskSpaceDeletePolicy", { tooltip: "DiskSpace Delete Policy", class: "fas fa-hdd text-muted ml-1" }],
]);
const deletePolicyLocked = new Map([
  [true, { tooltip: "Locked Policy", class: "fas fa-lock text-muted ml-1" }],
  [false, { tooltip: "Unlocked Policy", class: "fas fa-lock-open text-muted ml-1" }],
]);

const uiDeletePoliciesList = computed(() => {
  return deletePolicies.value.map((obj) => {
    if (_.isEmpty(obj["flow"])) {
      return { ...obj, flow: "All" };
    }
    return obj;
  });
});

const formatExportPolicyData = () => {
  // Separate the two types of policies into their own list
  let TimedDeletePolicyList = deletePolicies.value.filter((e) => e.__typename === "TimedDeletePolicy");
  let DiskSpaceDeletePolicyList = deletePolicies.value.filter((e) => e.__typename === "DiskSpaceDeletePolicy");

  // Remove the __typename key from policies
  TimedDeletePolicyList.forEach((e, index) => (TimedDeletePolicyList[index] = _.omit(e, ["__typename"])));
  DiskSpaceDeletePolicyList.forEach((e, index) => (DiskSpaceDeletePolicyList[index] = _.omit(e, ["__typename"])));

  // Remove the id key from policies
  TimedDeletePolicyList.forEach((e, index) => (TimedDeletePolicyList[index] = _.omit(e, ["id"])));
  DiskSpaceDeletePolicyList.forEach((e, index) => (DiskSpaceDeletePolicyList[index] = _.omit(e, ["id"])));

  let formattedDeletePolicies = {};
  formattedDeletePolicies["timedPolicies"] = TimedDeletePolicyList;
  formattedDeletePolicies["diskSpacePolicies"] = DiskSpaceDeletePolicyList;
  return formattedDeletePolicies;
};

const exportDeletePolicies = () => {
  let link = document.createElement("a");
  let downloadFileName = "delete_policy_export_" + new Date(Date.now()).toLocaleDateString();
  link.download = downloadFileName.toLowerCase();
  let blob = new Blob([JSON.stringify(formatExportPolicyData(), null, 2)], {
    type: "application/json",
  });
  link.href = URL.createObjectURL(blob);
  link.click();
  URL.revokeObjectURL(link.href);
  link.remove();
};
</script>

<style lang="scss">
@import "@/styles/pages/delete-policy-configuration-page.scss";
</style>
