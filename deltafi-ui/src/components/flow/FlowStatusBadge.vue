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
<!-- ABOUTME: Displays flow status as a badge - shows INVALID/PLUGIN issues or normal state. -->
<!-- ABOUTME: Used in data source, transform, and data sink tables to show flow health. -->

<template>
  <div class="d-flex justify-content-center">
    <Tag v-if="!rowData.flowStatus.valid" v-tooltip.left="invalidTooltip" :value="'INVALID'" severity="danger" icon="pi pi-exclamation-triangle" :rounded="true" />
    <Tag v-else-if="pluginDisabled && rowData.flowStatus.state === 'RUNNING'" v-tooltip.left="'Plugin is disabled'" :value="'PLUGIN'" severity="warning" icon="pi pi-pause" :rounded="true" />
    <Tag v-else-if="!rowData.pluginReady && rowData.flowStatus.state === 'RUNNING'" v-tooltip.left="rowData.pluginNotReadyReason || 'Plugin is not ready'" :value="'PLUGIN'" severity="warning" icon="pi pi-clock" :rounded="true" />
    <Tag v-else :value="rowData.flowStatus.state" :severity="stateSeverity" :rounded="true" />
  </div>
</template>

<script setup>
import { computed } from "vue";
import Tag from "primevue/tag";

const props = defineProps({
  rowData: {
    type: Object,
    required: true,
  },
});

const invalidTooltip = computed(() => {
  const errors = props.rowData.flowStatus?.errors;
  if (!errors || errors.length === 0) return "";
  return errors.map((e) => e.message).join("\n");
});

const pluginDisabled = computed(() => {
  return props.rowData.pluginNotReadyReason === "Plugin is disabled";
});

const stateSeverity = computed(() => {
  switch (props.rowData.flowStatus.state) {
    case "RUNNING":
      return "success";
    case "PAUSED":
      return "warning";
    case "STOPPED":
      return "secondary";
    default:
      return "secondary";
  }
});
</script>
