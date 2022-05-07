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
    <div>
      <PageHeader heading="Plugins" />
    </div>
    <div class="plugin-container">
      <div class="plugin-row">
        <div class="plugin-column plugin-column-left">
          <Listbox v-model="selectedPlugin" :options="listItems" option-label="label" />
        </div>
        <div class="plugin-column plugin-column-right">
          <div v-if="selectedPlugin !== null" class="col ml-0 pl-0">
            <PluginInfoPanel :info="selectedPlugin" class="mb-3" />
            <PluginActionTable :actions="selectedPlugin.actions" class="mb-3" />
            <PluginVariablesTable :variables="selectedPlugin.variables" :plugin-coordinates="selectedPlugin.pluginCoordinates" class="mb-3" />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import PageHeader from "@/components/PageHeader.vue";
import { onMounted, ref, computed, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import usePlugins from "@/composables/usePlugins";
import Listbox from "primevue/listbox";
import PluginActionTable from "@/components/PluginActionTable.vue";
import PluginInfoPanel from "@/components/PluginInfoPanel.vue";
import PluginVariablesTable from "@/components/PluginVariablesTable.vue";
import _ from "lodash";

const selectedPlugin = ref(null);
const route = useRoute();
const router = useRouter();
const { data: plugins, fetch: fetchPlugins } = usePlugins();

const listItems = computed(() => {
  let items = [];
  if (plugins.value) {
    items = plugins.value.plugins.map((plugin) => {
      return {
        label: `${plugin.displayName} - ${plugin.pluginCoordinates.version}`,
        id: buildId(plugin.pluginCoordinates),
        ...plugin
      }
    })
  }
  return _.orderBy(items, ['label'], ['asc']);
});

onMounted(async () => {
  await fetchPlugins();
  selectedPlugin.value = route.params.pluginCordinates ? listItems.value.find(e => e.id == route.params.pluginCordinates) : null
});

watch(route, () => {
  if (route.path === '/config/plugins') selectedPlugin.value = null;
});

watch(selectedPlugin, (newItem) => {
  console.log(newItem)
  const path = (newItem === null || newItem === undefined) ? '/config/plugins' : `/config/plugins/${selectedPlugin.value.id}`;
  router.push({ path });
});

const buildId = (pluginCoordinates) => [pluginCoordinates.groupId, pluginCoordinates.artifactId, pluginCoordinates.version].join(':');
</script>

<style lang="scss">
@import "@/styles/pages/plugin-page.scss";
</style>
