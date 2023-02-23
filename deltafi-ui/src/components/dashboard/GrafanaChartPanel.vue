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
  <Panel :header="header" class="chart-panel pb-3">
    <GrafanaChart :panel-id="panelId" :from="from" :to="to" />
  </Panel>
</template>

<script setup>
import Panel from "primevue/panel";
import GrafanaChart from "@/components/GrafanaChart.vue";
import { computed, onMounted, onUnmounted, ref, toRefs } from "vue";

const props = defineProps({
  header: {
    type: String,
    required: true,
  },
  panelId: {
    type: Number,
    required: true,
  },
  refreshMinutes: {
    type: Number,
    required: false,
    default: 5,
  },
  timeframeMinutes: {
    type: Number,
    required: false,
    default: 60,
  }
})

const { header, panelId, refreshMinutes, timeframeMinutes } = toRefs(props);

let autoRefresh = null;
const refreshInterval = computed(() => refreshMinutes.value * 60 * 1000);
const last = computed(() => timeframeMinutes.value * 60 * 1000);
const now = ref(new Date())
const from = computed(() => new Date(now.value - last.value).getTime())
const to = computed(() => now.value.getTime())

onMounted(() => {
  now.value = new Date();
  autoRefresh = setInterval(() => {
    now.value = new Date();
  }, refreshInterval.value);
});

onUnmounted(() => clearInterval(autoRefresh));
</script>
