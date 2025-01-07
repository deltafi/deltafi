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
  <div class="plugins-page">
    <PageHeader heading="Plugins">
      <div class="d-flex mb-2">
        <DialogTemplate component-name="plugin/PluginConfigurationDialog" header="Install Plugin" required-permission="PluginInstall" dialog-width="40vw" @reload-plugins="loadPlugins()">
          <Button v-has-permission:PluginInstall label="Install Plugin" icon="pi pi-plus" class="p-button-sm p-button-outlined mx-1" />
        </DialogTemplate>
      </div>
    </PageHeader>
    <ProgressBar v-if="showLoading" mode="indeterminate" style="height: 0.5em" />
    <Panel v-else header="Plugins" class="table-panel">
      <Splitter style="height: 77vh" layout="vertical" :gutter-size="10" @resizeend="customSpitterSize">
        <SplitterPanel id="splitterPanelId" :size="startingPanelOneSize" class="flex align-items-center justify-content-center" :style="`overflow-y: auto; ${panelOneSize}`">
          <DataTable id="dataTableId" ref="dataTableIdRef" v-model:selection="selectedPlugin" :value="pluginsList" selection-mode="single" responsive-layout="scroll" striped-rows class="p-datatable-sm p-datatable-gridlines plugin-table" sort-field="displayName" :sort-order="1" :row-hover="true" :meta-key-selection="false" data-key="displayName">
            <template #empty>No Plugins found.</template>
            <template #loading>Loading Plugins. Please wait.</template>
            <Column header="Name" field="displayName" :sortable="true" />
            <Column header="Description" field="description" />
            <Column header="Version" field="pluginCoordinates.version" />
            <Column header="Action Kit Version" field="actionKitVersion" />
            <Column :style="{ width: '5%' }" class="plugin-actions-column" :hidden="!$hasSomePermissions('PluginInstall', 'PluginUninstall')">
              <template #body="{ data }">
                <div v-if="!data.readOnly" class="d-flex justify-content-between">
                  <DialogTemplate component-name="plugin/PluginConfigurationDialog" header="Update Plugin" required-permission="PluginInstall" dialog-width="40vw" :row-data-prop="data" @reload-plugins="loadPlugins()">
                    <Button v-has-permission:PluginInstall v-tooltip.top="`Update Plugin`" icon="pi pi-pencil" class="p-button-text p-button-sm p-button-rounded p-button-secondary" />
                  </DialogTemplate>
                  <PluginRemoveButton v-has-permission:PluginUninstall :row-data-prop="data" @reload-plugins="loadPlugins()" @plugin-removal-errors="pluginRemovalErrors" />
                </div>
              </template>
            </Column>
          </DataTable>
        </SplitterPanel>
        <SplitterPanel :size="startingPanelTwoSize" class="flex align-items-center justify-content-center" :style="`overflow-y: auto; ${panelTwoSize}`">
          <div v-if="selectedPlugin !== null && selectedPlugin !== undefined" class="pt-4 pb-4 col">
            <div>
              <h4>{{ selectedPlugin.displayName }}</h4>
            </div>
            <PluginActionsPanel :actions="selectedPlugin.actions" class="mb-3" />
            <PluginVariablesPanel :key="selectedPlugin.displayName" :plugin-coordinates-prop="selectedPlugin.pluginCoordinates" :variables-prop="selectedPlugin.variables" class="mb-3" @updated="loadPlugins()" />
          </div>
        </SplitterPanel>
      </Splitter>
    </Panel>
  </div>
  <Dialog v-model:visible="errorOverlayDialog" :style="{ width: '750px' }" header="Errors" :modal="true" @hide="hideErrorsDialog()">
    <Message severity="error" :sticky="true" class="mb-2 mt-0" :closable="false">
      <ul>
        <div v-for="(error, key) in _.uniq(errorsList)" :key="key">
          <li class="text-wrap text-break">{{ error }}</li>
        </div>
      </ul>
    </Message>
  </Dialog>
</template>

<script setup>
import DialogTemplate from "@/components/DialogTemplate.vue";
import PageHeader from "@/components/PageHeader.vue";
import PluginActionsPanel from "@/components/plugin/ActionsPanel.vue";
import PluginRemoveButton from "@/components/plugin/PluginRemoveButton.vue";
import PluginVariablesPanel from "@/components/plugin/VariablesPanel.vue";
import ProgressBar from "@/components/deprecatedPrimeVue/ProgressBar";
import useNotifications from "@/composables/useNotifications";
import usePlugins from "@/composables/usePlugins";
import { computed, nextTick, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import _ from "lodash";

import Button from "primevue/button";
import Column from "primevue/column";
import DataTable from "primevue/datatable";
import Dialog from "primevue/dialog";
import Message from "primevue/message";
import Panel from "primevue/panel";
import Splitter from "primevue/splitter";
import SplitterPanel from "primevue/splitterpanel";

const selectedPlugin = ref(null);
const dataTableIdRef = ref();
const rowIndex = ref(0);
const startingPanelOneSize = ref(99);
const startingPanelTwoSize = ref(1);
const panelOneSize = ref(null);
const panelTwoSize = ref(null);
const userResized = ref(false);
const firstMounted = ref(true);

const errorsList = ref([]);
const errorOverlayDialog = ref(false);

const route = useRoute();
const router = useRouter();
const notify = useNotifications();
const { data: plugins, fetch: fetchPlugins, loading, loaded } = usePlugins();

const showLoading = computed(() => !loaded.value && loading.value);

const pluginsList = computed(() => {
  if (plugins.value) {
    plugins.value.plugins.forEach((plugin) => {
      plugin["combinedPluginCoordinates"] = plugin.pluginCoordinates.groupId.concat(":", plugin.pluginCoordinates.artifactId, ":", plugin.pluginCoordinates.version);
      plugin["readOnly"] = isReadOnly(plugin);
    });
    return plugins.value.plugins;
  } else {
    return [];
  }
});

const isReadOnly = (plugin) => {
  return plugin.pluginCoordinates.groupId === "org.deltafi" && ["deltafi-core-actions", "system-plugin"].includes(plugin.pluginCoordinates.artifactId);
}

const loadPlugins = async () => {
  await fetchPlugins();
  selectedPlugin.value = null;
  await nextTick();
  selectedPlugin.value = route.params.pluginCordinates ? pluginsList.value.find((e) => e.combinedPluginCoordinates == route.params.pluginCordinates) : null;
};

onMounted(async () => {
  loadPlugins();
});

watch(route, () => {
  if (route.path === "/config/plugins") {
    selectedPlugin.value = null;
  }
});

watch(selectedPlugin, async (newItem) => {
  if (newItem === undefined) {
    notify.error("Plugin Not Found", route.params.pluginCordinates);
  }
  let path = null;
  if (newItem === null || newItem === undefined) {
    panelOneSize.value = !userResized.value ? splitterSize(99) : panelOneSize.value;
    panelTwoSize.value = !userResized.value ? splitterSize(1) : panelTwoSize.value;

    path = "/config/plugins";
  } else {
    panelOneSize.value = !userResized.value ? splitterSize(50) : panelOneSize.value;
    panelTwoSize.value = !userResized.value ? splitterSize(50) : panelTwoSize.value;

    path = `/config/plugins/${newItem.combinedPluginCoordinates}`;

    scrollToRow(newItem);
  }
  router.push({ path });
});

const customSpitterSize = async (event) => {
  userResized.value = true;
  await nextTick();
  panelOneSize.value = splitterSize(event.sizes[0]);
  panelTwoSize.value = splitterSize(event.sizes[1]);
};

const scrollToRow = async (pluginSelected) => {
  if (firstMounted.value) {
    if (route.params.pluginCordinates) {
      rowIndex.value = dataTableIdRef.value.processedData.findIndex((obj) => obj.combinedPluginCoordinates == route.params.pluginCordinates);
    }
    firstMounted.value = false;
  }
  if (pluginSelected) {
    rowIndex.value = dataTableIdRef.value.processedData.findIndex((obj) => obj.combinedPluginCoordinates == pluginSelected.combinedPluginCoordinates);
  }
  const dataTableElement = document.getElementById("splitterPanelId");
  await nextTick();
  dataTableElement.getElementsByClassName("p-selectable-row")[rowIndex.value].scrollIntoView();
  document.documentElement.scrollTop = 0;
};

const splitterSize = (slitSize) => {
  return `flex-basis: calc(${slitSize}% - 10px);`;
};

const pluginRemovalErrors = (removalErrors) => {
  clearUploadErrors();
  for (let errorMessages of removalErrors) {
    errorsList.value.push(errorMessages);
  }
  errorOverlayDialog.value = true;
};

const hideErrorsDialog = () => {
  errorOverlayDialog.value = false;
  clearUploadErrors();
};

const clearUploadErrors = () => {
  errorsList.value = [];
};
</script>

<style lang="scss">
@import "@/styles/pages/plugin-page.scss";
</style>
