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
  <div class="list-edit">
    <div v-for="(item, i) in list" :key="i" class="list-row mb-2">
      <InputText v-model="list[i]" class="mr-2"></InputText>
      <Button icon="pi pi-times" class="p-button-secondary p-button-outlined remove-button" @click="removeItem(i)" />
    </div>
    <Button icon="pi pi-plus" label="Add Item" class="p-button-secondary p-button-outlined" @click="addItem" />
  </div>
</template>

<script setup>
import { ref, defineProps, defineEmits, onMounted, watch } from "vue";

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
const list = ref([]);

const loadListFromModelValue = () => {
  list.value = props.modelValue !== null ? props.modelValue.split(",").map((i) => i.trim()) : [];
};

onMounted(() => {
  loadListFromModelValue();
});

const emitUpdate = () => {
  if (list.value.length == 0) {
    emit("update:modelValue", null);
  } else {
    emit("update:modelValue", list.value.join(", "));
  }
};

watch(() => list.value, emitUpdate, { deep: true });

watch(() => props.modelValue, loadListFromModelValue, { deep: true });

const addItem = () => {
  list.value.push(null);
};

const removeItem = (index) => {
  list.value.splice(index, 1);
};
</script>

<style lang="scss">
.list-edit {
  .list-row {
    width: 100%;
    display: flex;
  }

  .list-row>* {
    flex: 1 1 auto;
  }

  .remove-button {
    flex: 0 0 auto;
  }
}
</style>
