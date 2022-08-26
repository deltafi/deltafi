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
  <div class="plugins-page">
    <PageHeader heading="Plugins" />
    <ProgressBar v-if="showLoading" mode="indeterminate" style="height: 0.5em" />
    <div v-else class="plugin-container">
      <div class="plugin-row">
        <div class="plugin-column plugin-column-left">
          <Listbox v-model="selectedPlugin" :options="listItems" option-label="label" empty-message="No plugins found" />
        </div>
        <div class="plugin-column plugin-column-right">
          <div v-if="selectedPlugin !== null && selectedPlugin !== undefined" class="col ml-0 pl-0">
            <PluginInfoPanel :info="selectedPlugin" class="mb-3" />
            <PluginActionsPanel :actions="selectedPlugin.actions" class="mb-3" />
            <PluginVariablesPanel :variables="selectedPlugin.variables" class="mb-3" @updated="loadPlugins" />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import PageHeader from "@/components/PageHeader.vue";
import { onMounted, ref, computed, watch, provide } from "vue";
import { useRoute, useRouter } from "vue-router";
import usePlugins from "@/composables/usePlugins";
import Listbox from "primevue/listbox";
import ProgressBar from "primevue/progressbar";
import useNotifications from "@/composables/useNotifications";
import PluginActionsPanel from "@/components/plugin/ActionsPanel.vue";
import PluginInfoPanel from "@/components/plugin/InfoPanel.vue";
import PluginVariablesPanel from "@/components/plugin/VariablesPanel.vue";
import _ from "lodash";

const selectedPlugin = ref(null);
provide('selectedPlugin', selectedPlugin);

const route = useRoute();
const router = useRouter();
const notify = useNotifications();
const { data: plugins, fetch: fetchPlugins, loading, loaded } = usePlugins();

const showLoading = computed(() => !loaded.value && loading.value);

const listItems = computed(() => {
  let items = [];
  if (plugins.value) {
    items = plugins.value.plugins.map((plugin) => {
      return {
        label: `${plugin.displayName} - ${plugin.pluginCoordinates.version}`,
        id: buildId(plugin.pluginCoordinates),
        ...plugin,
      };
    });
  }
  return _.orderBy(items, ["label"], ["asc"]);
});

const loadPlugins = async () => {
  await fetchPlugins();
  selectedPlugin.value = route.params.pluginCordinates ? listItems.value.find((e) => e.id == route.params.pluginCordinates) : null;
}

onMounted(async () => {
  loadPlugins()
});

watch(route, () => {
  if (route.path === "/config/plugins") {
    selectedPlugin.value = null;
  }
});

watch(selectedPlugin, (newItem) => {
  if (newItem === undefined) notify.error("Plugin Not Found", route.params.pluginCordinates);
  const path = newItem === null || newItem === undefined ? "/config/plugins" : `/config/plugins/${selectedPlugin.value.id}`;
  router.push({ path });
});

const buildId = (pluginCoordinates) => [pluginCoordinates.groupId, pluginCoordinates.artifactId, pluginCoordinates.version].join(":");
</script>

<style lang="scss">
@import "@/styles/pages/plugin-page.scss";
</style>
