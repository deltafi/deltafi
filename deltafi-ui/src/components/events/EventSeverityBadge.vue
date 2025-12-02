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
  <Tag :value="badgeText" :severity="tagSeverity" :icon="tagIcon" :rounded="true" v-tooltip.top="tooltip"/>
</template>

<script setup>
import { computed, toRefs } from "vue";
import Tag from "primevue/tag";

const props = defineProps({
  severity: {
    type: String,
    required: true,
  },
  tooltip: {
    type: String,
    required: false,
  },
});
const { severity } = toRefs(props);

const tagSeverity = computed(() => {
  if (severity.value == "warn") return "warning";
  if (severity.value == "error") return "danger";
  if (severity.value == "inactive") return "secondary";

  return severity.value;
});

const tagIcon = computed(() => {
  if (severity.value == "warn") return "pi pi-exclamation-triangle";
  if (severity.value == "error") return "pi pi-times";
  if (severity.value == "info") return "pi pi-info-circle";
  if (severity.value == "success") return "pi pi-check";
  if (severity.value == "inactive") return "pi pi-pause-circle";
  return null;
});

const badgeText = computed(() => {
  const text = severity.value.charAt(0).toUpperCase() + severity.value.slice(1);
  return text === 'Warn' ? 'Warning' : text;
});
</script>

<style />