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
  <div>
    <PageHeader heading="Versions" />
    <ProgressBar v-if="showLoading" mode="indeterminate" style="height: 0.5em" />
    <div v-for="group in groups" v-else :key="group">
      <VersionsPanel v-if="verionsByGroup[group]" class="mb-3" :header="group" :versions="verionsByGroup[group]"></VersionsPanel>
    </div>
  </div>
</template>

<script setup>
import ProgressBar from "@/components/deprecatedPrimeVue/ProgressBar";
import VersionsPanel from "@/components/VersionsPanel.vue";
import PageHeader from "@/components/PageHeader.vue";
import useVersions from "@/composables/useVersions";
import useVersion from "@/composables/useVersion";
import { onMounted, computed, onUnmounted, inject } from "vue";
import _ from 'lodash';

const refreshInterval = 5000; // 5 seconds
const { data: versions, loaded, loading, fetch: fetchVersions } = useVersions();
const { fetchVersion } = useVersion();
const isIdle = inject("isIdle");

let autoRefresh;

const showLoading = computed(() => !loaded.value && loading.value);

const groups = ['Core', 'Plugins', 'Other'];
const verionsByGroup = computed(() => {
  return _.groupBy(versions.value, (v) => {
    switch (v.group) {
      case 'deltafi-core':
        return 'Core'
      case 'deltafi-plugins':
        return 'Plugins'
      default:
        return 'Other'
    };
  });
})

onMounted(() => {
  fetchVersions();
  fetchVersion();
  autoRefresh = setInterval(() => {
    if (!isIdle.value && !loading.value) {
      fetchVersions();
      fetchVersion();
    }
  }, refreshInterval);
});

onUnmounted(() => {
  clearInterval(autoRefresh);
});
</script>
