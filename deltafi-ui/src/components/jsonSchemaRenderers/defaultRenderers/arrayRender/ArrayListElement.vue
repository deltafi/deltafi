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
  <div class="array-list-element">
    <div class="array-list-element-content">
      <slot></slot>
    </div>
    <div class="array-list-element-actions">
      <Button v-if="!isObjectType" icon="pi pi-angle-up" aria-label="Move item up" class="p-button-text p-button-sm p-button-rounded" :disabled="!moveUpEnabled" @click="moveUpClicked" />
      <Button v-if="!isObjectType" icon="pi pi-angle-down" aria-label="Move item down" class="p-button-text p-button-sm p-button-rounded" :disabled="!moveDownEnabled" @click="moveDownClicked" />
      <Button icon="pi pi-times" aria-label="Delete item" class="p-button-text p-button-sm p-button-rounded p-button-danger" @click="deleteClicked" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";

import Button from "primevue/button";

const props = defineProps({
  initiallyExpanded: {
    required: false,
    type: Boolean,
    default: false,
  },
  label: {
    required: false,
    type: String,
    default: "",
  },
  moveUpEnabled: {
    required: false,
    type: Boolean,
    default: true,
  },
  moveDownEnabled: {
    required: false,
    type: Boolean,
    default: true,
  },
  moveUp: {
    required: false,
    type: Function,
    default: undefined,
  },
  moveDown: {
    required: false,
    type: Function,
    default: undefined,
  },
  delete: {
    required: false,
    type: Function,
    default: undefined,
  },
  styles: {
    required: true,
    type: Object,
  },
  isObjectType: {
    required: false,
    type: Boolean,
    default: false,
  },
});

const expanded = ref(props.initiallyExpanded);

const expandClicked = () => {
  expanded.value = !expanded.value;
};

const moveUpClicked = (event: any) => {
  event.stopPropagation();
  props.moveUp?.();
};

const moveDownClicked = (event: any) => {
  event.stopPropagation();
  props.moveDown?.();
};

const deleteClicked = (event: any) => {
  event.stopPropagation();
  props.delete?.();
};

defineExpose({ expandClicked, moveUpClicked, moveDownClicked, deleteClicked });
</script>
<style scoped>
.array-list-element {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
  width: 100%;
}

.array-list-element-content {
  flex: 1;
  min-width: 0;
}

.array-list-element-content :deep(.p-inputtext) {
  width: 100%;
}

.array-list-element-content :deep(dl) {
  margin: 0;
}


.array-list-element-content :deep(dd) {
  margin: 0;
}


.array-list-element-actions {
  display: flex;
  align-items: center;
  gap: 0;
  flex-shrink: 0;
}
</style>
