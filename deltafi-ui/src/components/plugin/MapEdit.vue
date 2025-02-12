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
  <div class="map-edit">
    <div v-for="(pair, i) in pairs" :key="i" class="map-row mb-2">
      <InputText v-model="pair.key" class="mr-2" />
      <InputText v-model="pair.value" class="mr-2" />
      <Button icon="pi pi-times" class="p-button-secondary p-button-outlined remove-button" @click="removeItem(i)" />
    </div>
    <Button icon="pi pi-plus" label="Add Item" class="p-button-secondary p-button-outlined" @click="addItem" />
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from "vue";

import Button from "primevue/button";
import InputText from "primevue/inputtext";

const emit = defineEmits(["update:modelValue"]);
const props = defineProps({
  modelValue: {
    type: String,
    required: false,
    default: "",
  },
});
const pairs = ref([]);

const loadPairsFromModelValue = () => {
  if (props.modelValue === null || props.modelValue === "") {
    pairs.value = [];
  } else {
    pairs.value = props.modelValue.split(',').map((i) => i.split(':')).map((j) => { return { key: j[0].trim(), value: j[1].trim() } });
  }
};

onMounted(() => {
  loadPairsFromModelValue();
});

const emitUpdate = () => {
  if (pairs.value.length == 0) {
    emit("update:modelValue", null);
  } else {
    emit("update:modelValue", pairs.value.map((p) => `${p.key}: ${p.value}`).join(", "));
  }
};

watch(() => pairs.value, emitUpdate, { deep: true });

watch(() => props.modelValue, loadPairsFromModelValue, { deep: true });

const addItem = () => {
  pairs.value.push({ key: "", value: "" });
};

const removeItem = (index) => {
  pairs.value.splice(index, 1);
};
</script>

<style>
.map-edit {
  .map-row {
    width: 100%;
    display: flex;
  }

  .map-row>* {
    flex: 1 1 auto;
  }

  .remove-button {
    flex: 0 0 auto;
  }
}
</style>
