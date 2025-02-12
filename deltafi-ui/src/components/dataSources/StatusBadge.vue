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
  <Tag v-tooltip.left="message" class="m-0" :value="tagText" :severity="tagSeverity" :icon="tagIcon" :rounded="true" />
</template>

<script setup>
import { computed, toRefs } from "vue";
import Tag from "primevue/tag";

const props = defineProps({
  status: {
    type: String,
    required: true,
  },
  message: {
    type: String,
    required: false,
    default: null
  },
});
const { status, message } = toRefs(props);

const tagSeverity = computed(() => {
  if (status.value == "HEALTHY") return "success";
  if (status.value == "DEGRADED") return "warning";
  if (status.value == "UNHEALTHY") return "danger";

  return null;
});

const tagIcon = computed(() => {
  if (status.value == "DEGRADED") return "pi pi-exclamation-triangle";
  if (status.value == "UNHEALTHY") return "pi pi-times";
  if (status.value == "HEALTHY") return "pi pi-check";

  return null;
});

const tagText = computed(() => {
  const text = status.value.charAt(0).toUpperCase() + status.value.slice(1).toLowerCase();
  return text;
});
</script>

<style />