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
  <div>
    <h5>{{ header }}</h5>
    <GrafanaChart ref="chartRef" :panel-id="panelId" :from="from" :to="to" :refresh="refresh" :key-pairs="props.keyPairs" :dashboard-id="props.dashboardId" />
  </div>
</template>

<script setup>
import GrafanaChart from "@/components/GrafanaChart.vue";
import { computed, ref, toRefs } from "vue";
const props = defineProps({
  header: {
    type: String,
    required: true,
  },
  panelId: {
    type: Number,
    required: true,
  },
  refresh: {
    type: String,
    required: false,
    default: "1m",
  },
  timeframeMinutes: {
    type: Number,
    required: false,
    default: 60,
  },
  from: {
    type: [Number, String],
    required: false,
    default: null,
  },
  to: {
    type: [Number, String],
    required: false,
    default: null,
  },
  keyPairs: {
    type: Object,
    required: false,
    default: null,
  },
  dashboardId: {
    type: String,
    required: false,
    default: "ui-charts",
  },
});

const { header, panelId, timeframeMinutes } = toRefs(props);
const chartRef = ref(null);

const reload = () => chartRef.value?.reload();
defineExpose({ reload });

// Use Grafana's relative time syntax so the iframe URL stays stable.
// Grafana handles its own data refresh internally via the refresh param.
const from = computed(() => {
  if (props.from) return props.from;
  return `now-${timeframeMinutes.value}m`;
});
const to = computed(() => {
  if (props.to) return props.to;
  return "now";
});
</script>
