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
  <div ref="listContainer" class="list-edit">
    <div v-for="(item, i) in list" :key="i" class="list-row mb-2">
      <InputText v-model="list[i]" class="mr-2" />
      <Button icon="pi pi-times" aria-label="Remove item" class="p-button-secondary p-button-outlined remove-button" @click="removeItem(i)" />
    </div>
    <Button ref="addButton" icon="pi pi-plus" label="Add Item" class="p-button-secondary p-button-outlined" @click="addItem" />
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted, watch } from "vue";

import Button from "primevue/button";
import InputText from "primevue/inputtext";

const emit = defineEmits(["update:modelValue"]);
const props = defineProps({
  modelValue: {
    type: [String, Array],
    required: false,
    default: "",
  },
});

const list = ref([]);
const listContainer = ref(null);
const addButton = ref(null);
// Flag to track when a new item is added so we can focus it after the update cycle completes
const shouldFocusLast = ref(false);

// Focuses the last input in the list and scrolls the Add button into view
const focusLastInput = async () => {
  await nextTick();
  const inputs = listContainer.value?.querySelectorAll("input");
  if (inputs && inputs.length > 0) {
    inputs[inputs.length - 1].focus();
  }
  // Scroll the Add button into view so it remains visible after adding items
  const buttonEl = addButton.value?.$el ?? addButton.value;
  if (buttonEl) {
    buttonEl.scrollIntoView({ block: "end" });
  }
};

const loadListFromModelValue = async () => {
  if (props.modelValue !== null) {
    if (Array.isArray(props.modelValue)) {
      list.value = props.modelValue;
    } else {
      list.value = props.modelValue.split(",").map((i) => i.trim());
    }
  }
  if (shouldFocusLast.value) {
    shouldFocusLast.value = false;
    await focusLastInput();
  }
};

onMounted(() => {
  loadListFromModelValue();
});

// Emits the updated list value, filtering out any null or empty items
const emitUpdate = () => {
  const filteredList = list.value.filter((item) => item !== null && item !== "");
  if (filteredList.length === 0) {
    emit("update:modelValue", null);
  } else {
    emit("update:modelValue", filteredList.join(", "));
  }
};

watch(() => list.value, emitUpdate, { deep: true });

watch(() => props.modelValue, loadListFromModelValue, { deep: true });

// Adds a new item and sets flag to focus it after the Vue update cycle completes
const addItem = () => {
  shouldFocusLast.value = true;
  list.value.push(null);
};

const removeItem = (index) => {
  list.value.splice(index, 1);
};
</script>

<style>
.list-edit {
  .list-row {
    width: 100%;
    display: flex;
  }

  .list-row > * {
    flex: 1 1 auto;
  }

  .remove-button {
    flex: 0 0 auto;
  }
}
</style>
