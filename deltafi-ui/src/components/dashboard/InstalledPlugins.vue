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
  <Panel header="Installed Plugins" class="links-panel pb-3">
    <ProgressBar v-if="loading" mode="indeterminate" style="height: 0.5em" />
    <div v-else-if="pluginsList.length == 0" class="d-flex no-data-panel-content">
      <span class="p-2">No installed plugins</span>
    </div>
    <div v-else class="list-group list-group-flush">
      <PermissionedRouterLink v-for="plugin in pluginsList" :key="plugin" :disabled="!$hasPermission('PluginsView')" :to="{ path: '/config/plugins/' + plugin.mvnCoordinates }" class="list-group-item list-group-item-action">
        <div class="d-flex w-100 justify-content-between">
          <strong class="mb-0">{{ plugin.displayName }}</strong>
          <i class="text-muted fas fa-plug fa-rotate-90 fa-fw" />
        </div>
        <small class="mb-1 text-muted">{{ plugin.pluginCoordinates.version }}</small>
      </PermissionedRouterLink>
    </div>
  </Panel>
</template>

<script setup>
import PermissionedRouterLink from "@/components/PermissionedRouterLink";
import usePlugins from "@/composables/usePlugins";
import Panel from "primevue/panel";
import ProgressBar from "primevue/progressbar";
import { computed, onMounted } from "vue";

const { data: plugins, fetch: fetchPlugins, loading } = usePlugins();

onMounted(async () => {
  await fetchPlugins();
});

const pluginsList = computed(() => {
  let items = [];
  if (plugins.value) {
    items = plugins.value.plugins.map((plugin) => {
      return {
        mvnCoordinates: buildId(plugin.pluginCoordinates),
        ...plugin,
      };
    });
  }
  return items;
});

const buildId = (pluginCoordinates) => [pluginCoordinates.groupId, pluginCoordinates.artifactId, pluginCoordinates.version].join(":");
</script>

<style lang="scss">
.no-data-panel-content {
  padding: 0.5rem 1.25rem;
}
</style>
