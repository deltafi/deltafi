<!--
   DeltaFi - Data transformation and enrichment platform

   Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>

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
  <div>
    <span @click="showDialog()">
      <slot />
    </span>
    <Dialog v-model:visible="dialogVisible" :header="filename" :style="{ width: '75vw', height: '90vh' }" :maximizable="true" :modal="true" :dismissable-mask="true" :draggable="false" @show="onResize" @maximize="onResize" @unmaximize="onResize">
      <div ref="dialogContainer" class="dialog-container">
        <div class="dialog-row">
          <div v-if="showListbox" class="dialog-column dialog-column-left" :style="`height: ${dialogContainerHeight}`">
            <Listbox v-model="selectedItem" :list-style="`height: ${dialogContainerHeight}`" :style="`height: ${dialogContainerHeight}`" :options="listboxItems" option-label="name" />
          </div>
          <div class="dialog-column dialog-column-right" :style="`height: ${dialogContainerHeight}`">
            <ContentViewer :max-height="dialogContainerHeight" :content-reference="selectedContent.contentReference" :metadata="selectedMetadata" :filename="filename" />
          </div>
        </div>
      </div>
    </Dialog>
  </div>
</template>

<script setup>
import { computed, ref, defineProps, reactive, nextTick, watch } from "vue";
import Dialog from "primevue/dialog";
import Listbox from 'primevue/listbox';

import ContentViewer from "@/components/ContentViewer.vue";

const props = defineProps({
  content: {
    type: Array,
    required: true,
  },
  action: {
    type: String,
    required: false,
    default: null
  },
});

const { content, action } = reactive(props);

const dialogVisible = ref(false);
const showDialog = () => {
  dialogVisible.value = true;
};

const showListbox = computed(() => content.length > 1);
const listboxItems = computed(() => {
  return content.map((content, index) => {
    return {
      index: index,
      name: `${index} : ${content.name}`
    };
  });
});

const selectedItem = ref(listboxItems.value[0])
const selectedContent = computed(() => content[selectedItem.value.index])
const selectedContentReference = computed(() => selectedContent.value.contentReference);
const selectedMetadata = computed(() => selectedContent.value.metadata);

watch(selectedItem, (newItem, oldValue) => {
  if (newItem === null) selectedItem.value = oldValue
});

const dialogContainer = ref();
const dialogContainerHeight = ref();
const onResize = async () => {
  await nextTick();
  dialogContainerHeight.value = `${dialogContainer.value.clientHeight}px`;
};

const filename = computed(() => {
  return selectedContentReference.value.filename
    || selectedContent.value.filename
    || selectedContent.value.name
    || `${selectedContentReference.value.did}-${action}`;
})
</script>

<style lang="scss">
@import "@/styles/components/content-dialog.scss";
</style>