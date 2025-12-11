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

<!-- ABOUTME: Shared metrics interval dropdown and refresh button component. -->
<!-- ABOUTME: Emits interval changes and refresh requests to parent components. -->

<template>
  <div class="metrics-controls">
    <label class="mr-2">Metrics:</label>
    <Dropdown
      :model-value="modelValue"
      :options="intervalOptions"
      option-label="label"
      option-value="value"
      class="interval-dropdown"
      @update:model-value="$emit('update:modelValue', $event)"
    />
    <Button
      type="button"
      icon="pi pi-refresh"
      class="p-button-text p-button-sm ml-2"
      @click.stop.prevent="$emit('refresh')"
      v-tooltip.left="'Refresh metrics'"
    />
  </div>
</template>

<script setup lang="ts">
import Dropdown from "primevue/dropdown";
import Button from "primevue/button";

defineProps({
  modelValue: {
    type: Number,
    required: true,
  },
});

defineEmits(["update:modelValue", "refresh"]);

const intervalOptions = [
  { label: "Last 1 min", value: 1 },
  { label: "Last 5 min", value: 5 },
  { label: "Last 15 min", value: 15 },
  { label: "Last 1 hour", value: 60 },
  { label: "Last 6 hours", value: 360 },
  { label: "Last 24 hours", value: 1440 },
];
</script>

<style scoped>
.metrics-controls {
  display: flex;
  align-items: center;
}

.metrics-controls label {
  font-size: 0.875rem;
  color: var(--text-color);
  font-weight: 500;
  margin-top: 3px;
}

.interval-dropdown {
  width: 140px;
}

.interval-dropdown :deep(.p-dropdown-label) {
  font-size: 0.875rem;
  padding: 0.4rem 0.75rem;
}
</style>
