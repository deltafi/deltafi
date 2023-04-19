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
  <div class="auto-resume-page">
    <PageHeader heading="Auto Resume">
      <div class="d-flex mb-2">
        <Button label="Export Rules" icon="fas fa-download fa-fw" class="p-button-sm p-button-secondary p-button-outlined mx-1" @click="exportAutoResume()" />
        <AutoResumeImportFile v-has-permission:ResumePolicyCreate @reload-resume-rules="fetchAutoResumeRules()" />
        <DialogTemplate component-name="autoResume/AutoResumeConfigurationDialog" header="Add New Auto Resume Rule" required-permission="ResumePolicyCreate" dialog-width="75vw" :row-data-prop="{}" @reload-resume-rules="fetchAutoResumeRules()">
          <Button v-has-permission:ResumePolicyCreate label="Add Rule" icon="pi pi-plus" class="p-button-sm p-button-outlined mx-1" />
        </DialogTemplate>
      </div>
    </PageHeader>
    <Panel header="Rules" class="auto-resume-panel table-panel">
      <template #icons>
        <span class="p-input-icon-left">
          <i class="pi pi-search" />
          <InputText v-model="filters['global'].value" placeholder="Search" />
        </span>
      </template>
      <DataTable v-model:filters="filters" :value="uiAutoResumeRules" :loading="loading && !loaded" data-Key="id" responsive-layout="scroll" striped-rows class="p-datatable-sm p-datatable-gridlines" :global-filter-fields="['flow', 'errorSubstring', 'action', 'actionType']" :row-hover="true">
        <template #empty> No Auto Resume rules to display </template>
        <Column field="name" header="Name" :sortable="true" :style="{ width: '15%' }">
          <template #body="{ data }">
            <DialogTemplate component-name="autoResume/AutoResumeConfigurationDialog" header="View Auto Resume Rule" dialog-width="75vw" :row-data-prop="data" view-auto-resume-rule @reload-resume-rules="fetchAutoResumeRules()">
              <a class="cursor-pointer" style="color: black">{{ data.name }}</a>
            </DialogTemplate>
          </template>
        </Column>
        <Column field="errorSubstring" header="Error Substring" :sortable="true" :style="{ width: '40%' }"></Column>
        <Column field="flow" header="Flow" :sortable="true" :style="{ width: '10%' }"></Column>
        <Column field="action" header="Action" :sortable="true" :style="{ width: '20%' }"></Column>
        <Column field="actionType" header="Action Type" :sortable="true" :style="{ width: '15%' }"></Column>
        <Column :style="{ width: '5%' }" :body-style="{ padding: 0 }" :hidden="!$hasSomePermissions('ResumePolicyUpdate', 'ResumePolicyDelete')">
          <template #body="{ data }">
            <div class="d-flex">
              <DialogTemplate component-name="autoResume/AutoResumeConfigurationDialog" header="Edit Auto Resume Rule" required-permission="ResumePolicyUpdate" dialog-width="75vw" :row-data-prop="data" @reload-resume-rules="fetchAutoResumeRules()">
                <Button v-has-permission:ResumePolicyUpdate v-tooltip.top="`Edit Rule`" icon="pi pi-pencil" class="p-button-text p-button-sm p-button-rounded p-button-secondary" />
              </DialogTemplate>
              <AutoResumeRemoveButton v-has-permission:ResumePolicyDelete class="pl-2" :row-data-prop="data" @reload-resume-rules="fetchAutoResumeRules()" />
            </div>
          </template>
        </Column>
      </DataTable>
    </Panel>
  </div>
</template>

<script setup>
import AutoResumeImportFile from "@/components/autoResume/AutoResumeImportFile.vue";
import AutoResumeRemoveButton from "@/components/autoResume/AutoResumeRemoveButton.vue";
import DialogTemplate from "@/components/DialogTemplate.vue";
import PageHeader from "@/components/PageHeader.vue";
import useAutoResumeQueryBuilder from "@/composables/useAutoResumeQueryBuilder";
import { nextTick, onMounted, ref } from "vue";
import { FilterMatchMode } from "primevue/api";

import Button from "primevue/button";
import Column from "primevue/column";
import DataTable from "primevue/datatable";
import InputText from "primevue/inputtext";
import Panel from "primevue/panel";

import _ from "lodash";

const autoResumeRules = ref([]);
const uiAutoResumeRules = ref([]);
const { getAllResumePolicies, loaded, loading } = useAutoResumeQueryBuilder();

onMounted(async () => {
  fetchAutoResumeRules();
});

const fetchAutoResumeRules = async () => {
  let autoResumeRulesResponse = await getAllResumePolicies();
  autoResumeRules.value = [];
  uiAutoResumeRules.value = [];
  await nextTick();
  autoResumeRules.value = autoResumeRulesResponse.data.getAllResumePolicies;
  uiAutoResumeRules.value = _.assign(autoResumeRules.value);
};

const filters = ref({
  global: { value: null, matchMode: FilterMatchMode.CONTAINS },
});

const formatExportAutoResumeData = () => {
  let autoResumeRulesList = JSON.parse(JSON.stringify(autoResumeRules.value));
  // Remove the id key from route
  autoResumeRulesList.forEach((e, index) => (autoResumeRulesList[index] = _.omit(e, ["id"])));

  return autoResumeRulesList;
};

const exportAutoResume = () => {
  let link = document.createElement("a");
  let downloadFileName = "auto_resume_export_" + new Date(Date.now()).toLocaleDateString();
  link.download = downloadFileName.toLowerCase();
  let blob = new Blob([JSON.stringify(formatExportAutoResumeData(), null, 2)], {
    type: "application/json",
  });
  link.href = URL.createObjectURL(blob);
  link.click();
  URL.revokeObjectURL(link.href);
  link.remove();
};
</script>

<style lang="scss">
@import "@/styles/pages/auto-resume-page.scss";
</style>
