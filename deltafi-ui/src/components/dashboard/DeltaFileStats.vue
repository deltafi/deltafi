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
  <Panel header="DeltaFile Stats" class="links-panel pb-3">
    <div v-if="!deltaFileStats" class="p-4">
      Loading...
    </div>
    <div v-else class="list-group list-group-flush">
      <div class="row">
        <div v-for="label in statLabels" :key="label" class="col-4 text-center">
          <div class="stat-label">{{ label }}</div>
        </div>
      </div>
      <div class="row">
        <div v-for="value in statValues" :key="value" class="col-4 text-center">
          <div class="stat-data">{{ value }}</div>
        </div>
      </div>
    </div>
  </Panel>
</template>

<script setup>
import Panel from "primevue/panel";
import { ref, onMounted, computed } from "vue";
import useServerSentEvents from "@/composables/useServerSentEvents";
import useUtilFunctions from "@/composables/useUtilFunctions";
import useDeltaFileStats from "@/composables/useDeltaFileStats";

const { formattedBytes } = useUtilFunctions();
const { serverSentEvents } = useServerSentEvents();
const { fetchDeltaFileStats } = useDeltaFileStats();
const deltaFileStats = ref(null)
const statLabels = [
  "Total DeltaFiles",
  "Total DeltaFiles In-flight",
  "Total Bytes In-flight"
]
const statValues = computed(() => {
  if (deltaFileStats.value == null) return {};

  return [
    deltaFileStats.value.totalCount.toLocaleString("en-US"),
    deltaFileStats.value.inFlightCount.toLocaleString("en-US"),
    formattedBytes(deltaFileStats.value.inFlightBytes)
  ]
})

serverSentEvents.addEventListener('deltafiStats', (event) => {
  try {
    deltaFileStats.value = JSON.parse(event.data);
  } catch (error) {
    console.error(`Failed to parse SSE deltaFileStats data: ${event.data}`)
  }
});

onMounted(async () => {
  deltaFileStats.value = await fetchDeltaFileStats();
})
</script>

<style lang="scss">
.stat-data {
  padding-bottom: 0.3rem;
  font-size: x-large;
  font-weight: 500;
}

.stat-label {
  padding-top: 0.4rem;
  font-size: small;
}
</style>