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
  <i v-tooltip.right="tooltip" :class="classes" />
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from "vue";
import { useTimeAgo } from "@vueuse/core";

const props = defineProps({
  reason: {
    type: String,
    required: false,
    default: "N/A"
  },
  timestamp: {
    type: String,
    required: true,
  },
  icon: {
    type: String,
    required: false,
    default: "fa-solid fa-clock-rotate-left fa-flip-horizontal",
  },
});

let autoRefresh = null;
const refreshInterval = 1000; // 1 second
const timestamp = computed(() => new Date(props.timestamp));
const timeDiffInWords = ref("");
const secondsDiff = ref(0);
const tooltipPrefix = computed(() => secondsDiff.value <= 0 ? "Scheduled for auto resume" : "Next auto resume");

const calculateTooltip = () => {
  const now = new Date();
  secondsDiff.value = Math.round((timestamp.value.getTime() - now) / 1000);
  if (secondsDiff.value <= 0) {
    // Past
    timeDiffInWords.value = Math.abs(secondsDiff.value) < 60 ?
      `${Math.abs(secondsDiff.value)} seconds ago` :
      useTimeAgo(timestamp.value).value
  } else {
    // Future
    timeDiffInWords.value = secondsDiff.value < 60 ?
      `in ${secondsDiff.value} seconds` :
      useTimeAgo(timestamp.value).value
  }
};

onMounted(() => {
  calculateTooltip()
  autoRefresh = setInterval(() => {
    calculateTooltip()
  }, refreshInterval);
});

onUnmounted(() => {
  clearInterval(autoRefresh);
});

const classes = computed(() => {
  return `auto-resume-icon ${props.icon}`;
});

const tooltip = computed(() => {
  return `${tooltipPrefix.value} ${timeDiffInWords.value}\n(at ${props.timestamp})\n\n${props.reason}`;
});
</script>

<style scoped>
.auto-resume-icon {
  color: var(--green);
}
</style>
