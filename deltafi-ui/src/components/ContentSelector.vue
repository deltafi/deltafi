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
  <div class="content-selector-container">
    <div v-if="showListbox" class="left-column">
      <Listbox v-model="selectedItem" :options="listboxItems" option-label="name" />
    </div>
    <div class="right-column">
      <ContentViewer :content="selectedContent" />
    </div>
  </div>
</template>

<script setup>
import { computed, defineEmits, defineProps, reactive, ref, watch } from "vue";
import Listbox from "primevue/listbox";
import ContentViewer from "@/components/ContentViewer.vue";

const emit = defineEmits(["contentSelected"]);
const props = defineProps({
  content: {
    type: Array,
    required: true,
  },
});

const { content } = reactive(props);
const showListbox = computed(() => content.length > 1);
const listboxItems = computed(() => {
  return content.map((content, index) => {
    return {
      index: index,
      name: `${index} : ${content.name}`,
    };
  });
});
const selectedItem = ref(listboxItems.value[0]);
const selectedContent = computed(() => content[selectedItem.value.index]);
emit("contentSelected", selectedContent.value);

watch(selectedItem, (newItem, oldValue) => {
  if (newItem === null) selectedItem.value = oldValue;
  emit("contentSelected", selectedContent.value);
});
</script>

<style lang="scss">
@import "@/styles/components/content-selector.scss";
</style>
