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
  <Panel header="External Links" class="links-panel pb-3">
    <div v-if="externalLinks.length == 0" class="d-flex no-data-panel-content">
      <span class="p-2">No external links configured</span>
    </div>
    <div v-else class="list-group list-group-flush">
      <a v-for="link in externalLinks" :key="link" :href="link.url" target="_blank" class="list-group-item list-group-item-action">
        <div class="d-flex w-100 justify-content-between">
          <strong class="mb-0">{{ link.name }}</strong>
          <i class="fas fa-external-link-alt" />
        </div>
        <small class="mb-1 text-muted">{{ link.description }}</small>
      </a>
    </div>
  </Panel>
</template>

<script setup>
import Panel from "primevue/panel";
import { computed, inject } from "vue";

const uiConfig = inject("uiConfig");

const externalLinks = computed(() => {
  return uiConfig.externalLinks;
});
</script>

<style lang="scss">
@import "@/styles/pages/dashboard-page.scss";
</style>
