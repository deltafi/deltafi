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
  <div class="external-links-page">
    <PageHeader heading="External Links">
      <div class="btn-toolbar mb-2 mb-md-0">
        <IconField iconPosition="left">
          <InputIcon class="pi pi-search"> </InputIcon>
          <InputText v-model="filters['global'].value" v-tooltip.left="'Search on Name and Description'" placeholder="Search" class="deltafi-input-field mx-1" />
        </IconField>
        <DialogTemplate component-name="externalLink/ExternalLinkConfigurationDialog" header="Add New Link" dialog-width="25vw" :row-data-prop="{}" @refresh-page="reloadUIConfigs()">
          <Button label="Add Link" icon="pi pi-plus" class="p-button-sm p-button-outlined mx-2" />
        </DialogTemplate>
      </div>
    </PageHeader>
    <Panel header="External links" class="external-links-panel table-panel">
      <DataTable v-model:filters="filters" :value="externalLinks" :loading="loading" responsive-layout="scroll" class="p-datatable-sm p-datatable-gridlines" striped-rows :global-filter-fields="['name', 'description']" :row-hover="true" data-key="name">
        <template #empty> No External Links found </template>
        <template #loading> Loading External Links. Please wait. </template>
        <Column field="name" header="Name" :sortable="true" :style="{ width: '15rem' }">
          <template #body="{ data }">
            <DialogTemplate component-name="externalLink/ExternalLinkConfigurationDialog" header="View External Link" dialog-width="25vw" :row-data-prop="data" row-link-type="External Link" view-link @refresh-page="reloadUIConfigs()">
              <a class="cursor-pointer" style="color: black">{{ data.name }}</a>
            </DialogTemplate>
          </template>
        </Column>
        <Column field="description" header="Description" :sortable="true" :style="{ width: '25rem' }" />
        <Column field="url" header="URL" :sortable="true" :style="{ width: '35rem' }" />
        <Column :style="{ width: '5rem' }" :body-style="{ padding: 0 }">
          <template #body="{ data }">
            <div class="d-flex">
              <DialogTemplate component-name="externalLink/ExternalLinkConfigurationDialog" header="Edit External Link" dialog-width="25vw" :row-data-prop="data" row-link-type="External Link" edit-link @refresh-page="reloadUIConfigs()">
                <Button v-tooltip.top="`Edit External Link`" icon="pi pi-pencil" class="p-button-text p-button-sm p-button-rounded p-button-secondary" />
              </DialogTemplate>
              <LinkRemoveButton class="pl-2" :row-data-prop="data" row-link-type="External Link" @refresh-page="reloadUIConfigs()" />
            </div>
          </template>
        </Column>
      </DataTable>
    </Panel>
    <Panel header="DeltaFile Links" class="external-links-panel table-panel mt-3">
      <DataTable v-model:filters="filters" :value="deltaFileLinks" :loading="loading" responsive-layout="scroll" class="p-datatable-sm p-datatable-gridlines" striped-rows :global-filter-fields="['name', 'description']" :row-hover="true">
        <template #empty> No DeltaFile Links found. </template>
        <template #loading> Loading DeltaFile Links. Please wait. </template>
        <Column field="name" header="Name" :sortable="true" :style="{ width: '15rem' }">
          <template #body="{ data }">
            <DialogTemplate component-name="externalLink/ExternalLinkConfigurationDialog" header="View DeltaFile Link" dialog-width="25vw" :row-data-prop="data" row-link-type="DeltaFile Link" view-link @refresh-page="reloadUIConfigs()">
              <a class="cursor-pointer" style="color: black">{{ data.name }}</a>
            </DialogTemplate>
          </template>
        </Column>
        <Column field="description" header="Description" :sortable="true" :style="{ width: '25rem' }" />
        <Column field="url" header="URL" :sortable="true" :style="{ width: '35rem' }" />
        <Column :style="{ width: '5rem' }" :body-style="{ padding: 0 }">
          <template #body="{ data }">
            <div class="d-flex">
              <DialogTemplate component-name="externalLink/ExternalLinkConfigurationDialog" header="Edit DeltaFile Link" dialog-width="25vw" :row-data-prop="data" row-link-type="DeltaFile Link" edit-link @refresh-page="reloadUIConfigs()">
                <Button v-tooltip.top="`Edit DeltaFile Link`" icon="pi pi-pencil" class="p-button-text p-button-sm p-button-rounded p-button-secondary" />
              </DialogTemplate>
              <LinkRemoveButton class="pl-2" :row-data-prop="data" row-link-type="DeltaFile Link" @refresh-page="reloadUIConfigs()" />
            </div>
          </template>
        </Column>
      </DataTable>
    </Panel>
  </div>
</template>

<script setup>
import DialogTemplate from "@/components/DialogTemplate.vue";
import LinkRemoveButton from "@/components/externalLink/ExternalLinkRemoveButton.vue";
import PageHeader from "@/components/PageHeader.vue";
import useUiConfig from "@/composables/useUiConfig";
import { computed, onMounted, ref } from "vue";

import Button from "primevue/button";
import Column from "primevue/column";
import DataTable from "primevue/datatable";
import { FilterMatchMode } from "primevue/api";
import IconField from "primevue/iconfield";
import InputIcon from "primevue/inputicon";
import InputText from "primevue/inputtext";

import Panel from "primevue/panel";

const { uiConfig, fetchUiConfig } = useUiConfig();
const loading = ref(false);

onMounted(async () => {
  await fetchUiConfig();
});

const filters = ref({
  global: { value: null, matchMode: FilterMatchMode.CONTAINS },
});

const externalLinks = computed(() => {
  return uiConfig.externalLinks;
});

const deltaFileLinks = computed(() => {
  return uiConfig.deltaFileLinks;
});

const reloadUIConfigs = async () => {
  loading.value = true;
  const oldExternalLinks = JSON.stringify(externalLinks.value);
  const oldDeltaFileLinks = JSON.stringify(deltaFileLinks.value);

  let tries = 0;
  const interval = setInterval(async () => {
    await fetchUiConfig(true);
    const newExternalLinks = JSON.stringify(externalLinks.value);
    const newDeltaFileLinks = JSON.stringify(deltaFileLinks.value);
    if (oldExternalLinks !== newExternalLinks || oldDeltaFileLinks !== newDeltaFileLinks || tries === 10) {
      loading.value = false;
      clearInterval(interval);
    }
    tries += 1;
  }, 500);
};
</script>

<style>
.external-links-page {
  .external-links-panel {
    .p-panel-header {
      padding: 0 1.25rem;

      .p-panel-title {
        padding: 1rem 0;
      }
    }

    .list-item::before {
      content: "•";
      margin-right: 0.25rem;
      font-weight: bold;
    }

    .external-links-search-txt {
      font-size: 1rem !important;
    }
  }
}
</style>
