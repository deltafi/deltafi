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
  <div :class="styles.arrayList.item + ' d-flex btn-group align-items-start'">
    <!-- <div :class="styles.arrayList.itemLabel + 'pr-1'">{{ label.split(".").pop()  }}</div> -->
    <div class="input-width ml-2">
      <slot></slot>
    </div>
    <div class="col btn-group px-0 align-items-center">
      <Button icon="pi pi-angle-up text-dark" text rounded :disabled="!moveUpEnabled" @click="moveUpClicked" />
      <Button icon="pi pi-angle-down text-dark" text rounded :disabled="!moveDownEnabled" @click="moveDownClicked" />
      <Button icon="pi pi-trash text-dark" text rounded @click="deleteClicked" />
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
<style>
.input-width {
  width: 100% !important;
}
</style>
