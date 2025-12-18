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
<!-- ABOUTME: Dialog wrapper for viewing DeltaFile content via ContentSelector. -->
<!-- ABOUTME: Accepts content location pointer and passes to ContentSelector. -->

<template>
  <div>
    <span @click="showDialog()">
      <slot />
    </span>
    <Dialog v-model:visible="dialogVisible" :header="header" :style="{ width: '75vw', height: '90vh' }" :maximizable="true" :modal="true" :dismissable-mask="true" :draggable="false">
      <ContentSelector
        :did="did"
        :flow-number="flowNumber"
        :action-index="actionIndex"
        :content-index="contentIndex"
        :content="content"
        @content-selected="handleContentSelected"
      />
    </Dialog>
  </div>
</template>

<script setup>
import { ref, reactive } from "vue";
import Dialog from "primevue/dialog";
import ContentSelector from "@/components/ContentSelector.vue";

const props = defineProps({
  did: {
    type: String,
    required: true,
  },
  flowNumber: {
    type: Number,
    required: true,
  },
  actionIndex: {
    type: Number,
    default: undefined,
  },
  contentIndex: {
    type: Number,
    default: undefined,
  },
  content: {
    type: Array,
    required: true,
  },
});

const { content } = reactive(props);

const dialogVisible = ref(false);
const showDialog = () => (dialogVisible.value = true);

const header = ref(content[0]?.name || "Content");
const handleContentSelected = (pointer) => (header.value = pointer.name);
</script>

<style>
.p-dialog-content {
  height: 100%;

  .dialog-container {
    height: 100%;
  }
}
</style>
