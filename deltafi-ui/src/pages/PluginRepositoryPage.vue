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
    <PageHeader heading="Plugin Repositories">
      <div class="d-flex mb-2">
        <div>
          <DialogTemplate component-name="pluginRepository/PluginRepositoryConfigurationDialog" header="Add New Image Repository" dialog-width="40vw" @reload-plugin-repos="fetchPluginImageRepositories()">
            <Button v-has-permission:PluginImageRepoWrite label="Add Image Repository" icon="pi pi-plus" class="p-button-sm p-button-outlined mx-1" />
          </DialogTemplate>
        </div>
      </div>
    </PageHeader>
    <Panel header="Plugin Repositories" class="plugin-repo-config-panel table-panel">
      <template #icons>
        <span class="p-input-icon-left">
          <i class="pi pi-search" />
          <InputText v-model="filters['global'].value" v-tooltip.left="'Search on pluginGroupIds, imageRepositoryBase, and imagePullSecret'" placeholder="Search" />
        </span>
      </template>
      <DataTable v-model:filters="filters" :value="pluginImageRepositories" data-Key="id" responsive-layout="scroll" striped-rows class="p-datatable-sm p-datatable-gridlines plugin-repo-config-table" :global-filter-fields="['pluginGroupIds', 'imageRepositoryBase', 'imagePullSecret']" :row-hover="true">
        <template #empty> No Plugin Repositories to display </template>
        <Column field="imageRepositoryBase" header="Image Repository Base" :sortable="true" :style="{ width: 'auto' }">
          <template #body="{ data }">
            <DialogTemplate component-name="pluginRepository/PluginRepositoryConfigurationDialog" header="View Plugin Repository" dialog-width="40vw" :row-data-prop="data" view-plugin-image-repo @reload-plugin-repos="fetchPluginImageRepositories()">
              <a class="cursor-pointer" style="color: black">{{ data.imageRepositoryBase }}</a>
            </DialogTemplate>
          </template>
        </Column>
        <Column header="Plugin Group Ids" :sortable="true" :style="{ width: '30%' }">
          <template #body="{ data }">
            <span v-for="plugin of data.pluginGroupIds" :key="plugin" class="badge badge-pill badge-secondary mr-1">{{ plugin }}</span>
          </template>
        </Column>
        <Column field="imagePullSecret" header="Image Pull Secret" :sortable="true" :style="{ width: '20%' }" />
        <Column :style="{ width: '5%' }" class="plugin-repo-config-actions-column" :hidden="!$hasSomePermissions('PluginImageRepoWrite', 'PluginImageRepoDelete')">
          <template #body="{ data }">
            <div class="d-flex justify-content-between">
              <DialogTemplate component-name="pluginRepository/PluginRepositoryConfigurationDialog" header="Update Plugin Repository" dialog-width="40vw" :row-data-prop="data" @reload-plugin-repos="fetchPluginImageRepositories()">
                <Button v-has-permission:PluginImageRepoWrite v-tooltip.left="`Update Plugin Repository`" icon="pi pi-pencil" class="p-button-text p-button-sm p-button-rounded p-button-secondary" />
              </DialogTemplate>
              <PluginImageRepositoryRemoveButton v-has-permission:PluginImageRepoDelete :row-data-prop="data" @reload-plugin-repos="fetchPluginImageRepositories()" />
            </div>
          </template>
        </Column>
      </DataTable>
    </Panel>
  </div>
</template>

<script setup>
import PluginImageRepositoryRemoveButton from "@/components/pluginRepository/PluginRepositoryRemoveButton.vue";
import DialogTemplate from "@/components/DialogTemplate.vue";
import PageHeader from "@/components/PageHeader.vue";
import usePlugins from "@/composables/usePlugins";
import { nextTick, onMounted, ref } from "vue";
import { FilterMatchMode } from "primevue/api";

import Button from "primevue/button";
import Column from "primevue/column";
import DataTable from "primevue/datatable";
import InputText from "primevue/inputtext";
import Panel from "primevue/panel";

const pluginImageRepositories = ref([]);
const { getPluginImageRepositories } = usePlugins();

onMounted(async () => {
  fetchPluginImageRepositories();
});

const fetchPluginImageRepositories = async () => {
  let pluginImageRepositoriesResponse = await getPluginImageRepositories();
  pluginImageRepositories.value = [];
  await nextTick();
  pluginImageRepositories.value = pluginImageRepositoriesResponse.getPluginImageRepositories;
};

const filters = ref({
  global: { value: null, matchMode: FilterMatchMode.CONTAINS },
});
</script>

<style lang="scss">
@import "@/styles/pages/plugin-repository-configuration-page.scss";
</style>
