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
import ProgressBar from "primevue/progressbar";
import VersionsPanel from "@/components/VersionsPanel.vue";
import PageHeader from "@/components/PageHeader.vue";
import useVersions from "@/composables/useVersions";
import { onMounted, computed, onUnmounted, inject } from "vue";
import _ from 'lodash';

const refreshInterval = 5000; // 5 seconds
const { data: versions, loaded, loading, fetch: fetchVersions } = useVersions();
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
  autoRefresh = setInterval(() => {
    if (!isIdle.value && !loading.value) {
      fetchVersions();
    }
  }, refreshInterval);
});

onUnmounted(() => {
  clearInterval(autoRefresh);
});
</script>
