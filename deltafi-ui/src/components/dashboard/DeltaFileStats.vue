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
  <Panel header="DeltaFile Stats" class="links-panel pb-3">
    <div v-if="!deltaFileStats" class="p-4">
      Loading...
    </div>
    <div v-else class="list-group list-group-flush">
      <div class="stats-row">
        <div v-for="label in statLabels" :key="label" class="stat-cell text-center">
          <div class="stat-label">
            {{ label }}
            <span v-if="label === 'In-flight' && deltaFileStats.inFlightCount > 0 && deltaFileStats.oldestInFlightCreated" v-tooltip.top="oldestTooltip" class="oldest-clock" :class="{ clickable: deltaFileStats.oldestInFlightDid }" @click="viewOldestDeltaFile">
              <i class="pi pi-clock" />
            </span>
          </div>
        </div>
      </div>
      <div class="stats-row">
        <div v-for="(value, index) in statValues" :key="index" class="stat-cell text-center">
          <div class="stat-data">
            {{ value }}
          </div>
        </div>
      </div>
    </div>
  </Panel>
</template>

<script setup>
import Panel from "primevue/panel";
import { ref, onMounted, computed } from "vue";
import { useRouter } from "vue-router";
import { useTimeAgo } from '@vueuse/core';
import useServerSentEvents from "@/composables/useServerSentEvents";
import useUtilFunctions from "@/composables/useUtilFunctions";
import useDeltaFileStats from "@/composables/useDeltaFileStats";

const router = useRouter();

const { formattedBytes, formatTimestamp } = useUtilFunctions();
const { serverSentEvents } = useServerSentEvents();
const { fetchDeltaFileStats } = useDeltaFileStats();
const deltaFileStats = ref(null)
const statLabels = [
  "Total DeltaFiles",
  "In-flight",
  "In-flight Bytes",
  "Warm Queue",
  "Cold Queue",
  "Paused"
]
const statValues = computed(() => {
  if (deltaFileStats.value == null) return {};

  return [
    deltaFileStats.value.totalCount.toLocaleString("en-US"),
    deltaFileStats.value.inFlightCount.toLocaleString("en-US"),
    formattedBytes(deltaFileStats.value.inFlightBytes),
    (deltaFileStats.value.warmQueuedCount ?? 0).toLocaleString("en-US"),
    (deltaFileStats.value.coldQueuedCount ?? 0).toLocaleString("en-US"),
    (deltaFileStats.value.pausedCount ?? 0).toLocaleString("en-US")
  ]
})

const oldestTooltip = computed(() => {
  const oldest = deltaFileStats.value?.oldestInFlightCreated;
  if (!oldest) return "";
  const timeAgo = useTimeAgo(oldest).value;
  const timestamp = formatTimestamp(oldest, "MM/DD/YYYY, HH:mm:ss");
  const clickHint = deltaFileStats.value?.oldestInFlightDid ? " — Click to view" : "";
  return `Oldest: ${timeAgo} (${timestamp})${clickHint}`;
})

const viewOldestDeltaFile = () => {
  const did = deltaFileStats.value?.oldestInFlightDid;
  if (did) {
    router.push(`/deltafile/viewer/${did}`);
  }
}

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

<style>
.stats-row {
  display: flex;
  flex-wrap: wrap;
}

.stat-cell {
  flex: 1 1 16%;
  min-width: 100px;
}

.stat-data {
  padding-bottom: 0.3rem;
  font-size: x-large;
  font-weight: 500;
}

.stat-label {
  padding-top: 0.4rem;
  font-size: small;
}

.oldest-clock {
  margin-left: 4px;
  cursor: help;
  color: var(--text-color-secondary);
  display: inline-flex;
  align-items: center;
  vertical-align: text-bottom;
}

.oldest-clock.clickable {
  cursor: pointer;
}

.oldest-clock.clickable:hover {
  color: var(--primary-color);
}
</style>