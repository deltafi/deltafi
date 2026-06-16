<!--
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>

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
  <ul class="summary-stats-bar mb-3" role="list" aria-label="DeltaFile statistics" :aria-busy="!deltaFileStats">
    <li v-for="stat in stats" :key="stat.label" class="stat-item" :aria-label="statAriaLabel(stat)">
      <div class="stat-value-row">
        <Skeleton v-if="!deltaFileStats" width="3.5rem" height="2rem" />
        <template v-else>
          <template v-for="(value, index) in stat.values" :key="index">
            <span v-if="index > 0" class="stat-value-separator" aria-hidden="true" />
            <span class="stat-value">{{ value }}</span>
          </template>
        </template>
      </div>
      <div class="stat-label-row">
        <i :class="[stat.icon, 'stat-icon']" :style="{ color: stat.color }" aria-hidden="true" />
        <span class="stat-label">{{ stat.label }}</span>
        <span v-if="stat.label === 'In-flight DeltaFiles' && deltaFileStats && deltaFileStats.inFlightCount > 0 && deltaFileStats.oldestInFlightCreated" v-tooltip.top="oldestTooltip" class="oldest-clock" :class="{ clickable: deltaFileStats.oldestInFlightDid }" :role="deltaFileStats.oldestInFlightDid ? 'button' : 'img'" :tabindex="deltaFileStats.oldestInFlightDid ? 0 : -1" :aria-label="oldestAriaLabel" :aria-disabled="!deltaFileStats.oldestInFlightDid || undefined" @click="viewOldestDeltaFile" @keydown.enter.prevent="viewOldestDeltaFile" @keydown.space.prevent="viewOldestDeltaFile">
          <i class="fas fa-clock" aria-hidden="true" />
        </span>
      </div>
    </li>
  </ul>
</template>

<script setup>
import Skeleton from "primevue/skeleton";
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

const neutralColor = "var(--text-color)";

const stats = computed(() => {
  const s = deltaFileStats.value;
  return [
    { label: "Total DeltaFiles", icon: "fas fa-database", color: neutralColor, values: s ? [s.totalCount.toLocaleString("en-US")] : [] },
    { label: "In-flight DeltaFiles", icon: "fas fa-paper-plane", color: neutralColor, values: s ? (s.inFlightCount > 0 ? [s.inFlightCount.toLocaleString("en-US"), formattedBytes(s.inFlightBytes)] : ["0"]) : [] },
    { label: "Warm Queue", icon: "fas fa-fire", color: "var(--orange-500)", values: s ? [(s.warmQueuedCount ?? 0).toLocaleString("en-US")] : [] },
    { label: "Cold Queue", icon: "fas fa-snowflake", color: "var(--blue-500)", values: s ? [(s.coldQueuedCount ?? 0).toLocaleString("en-US")] : [] },
    { label: "Rate Limited", icon: "fas fa-tachometer-alt", color: neutralColor, values: s ? [(s.rateLimitedCount ?? 0).toLocaleString("en-US")] : [] },
    { label: "Paused", icon: "fas fa-pause-circle", color: neutralColor, values: s ? [(s.pausedCount ?? 0).toLocaleString("en-US")] : [] }
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

const oldestAriaLabel = computed(() => {
  const oldest = deltaFileStats.value?.oldestInFlightCreated;
  if (!oldest) return "";
  const timeAgo = useTimeAgo(oldest).value;
  const action = deltaFileStats.value?.oldestInFlightDid ? ". Activate to view the oldest in-flight DeltaFile." : "";
  return `Oldest in-flight DeltaFile: ${timeAgo}${action}`;
})

const statAriaLabel = (stat) => {
  if (!deltaFileStats.value) return `${stat.label}: loading`;
  if (!stat.values || stat.values.length === 0) return stat.label;
  return `${stat.label}: ${stat.values.join(", ")}`;
}

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

const reload = async () => {
  const result = await fetchDeltaFileStats();
  if (result) deltaFileStats.value = result;
}

onMounted(reload);

defineExpose({ reload });
</script>

<style>
.summary-stats-bar {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 1.75rem;
  padding: 0.5rem 0 0;
  margin: 0;
  list-style: none;

  .stat-item {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 0.4rem;
    padding: 0.85rem 1.5rem;
    background: var(--surface-ground);
    border-radius: var(--p-content-border-radius, 6px);
    min-width: 140px;
  }

  .stat-value-row {
    display: flex;
    justify-content: center;
    align-items: baseline;
    gap: 0.5rem;
  }

  .stat-value {
    font-size: 1.75rem;
    font-weight: 700;
    line-height: 1.2;
  }

  .stat-value-separator {
    align-self: center;
    width: 1px;
    height: 1.4rem;
    background: var(--text-color-secondary);
    opacity: 0.6;
  }

  .stat-label-row {
    display: flex;
    align-items: baseline;
    gap: 0.4rem;
  }

  .stat-icon {
    font-size: 1rem;
    vertical-align: baseline;
  }

  .stat-label {
    font-size: 0.9rem;
    color: var(--text-color-secondary);
    text-transform: uppercase;
    letter-spacing: 0.025em;
    white-space: nowrap;
  }

  .oldest-clock {
    margin-left: 2px;
    cursor: help;
    color: var(--text-color-secondary);
    font-size: 0.9rem;
    display: inline-flex;
    align-items: center;
  }

  .oldest-clock.clickable {
    cursor: pointer;
  }

  .oldest-clock.clickable:hover {
    color: var(--primary-color);
  }
}

@media (max-width: 1200px) {
  .summary-stats-bar {
    gap: 0.5rem;

    .stat-item {
      padding: 0.85rem 0.75rem;
      min-width: 0;
    }
  }
}
</style>
