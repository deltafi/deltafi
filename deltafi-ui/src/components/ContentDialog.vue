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
  <div>
    <span @click="showDialog()">
      <slot />
    </span>
    <Dialog v-model:visible="dialogVisible" :header="header" :style="{ width: '75vw', height: '90vh' }" :maximizable="true" :modal="true" :dismissable-mask="true" :draggable="false">
      <ContentSelector :content="content" @content-selected="handleContentSelected" />
    </Dialog>
  </div>
</template>

<script setup>
import { ref, defineProps, reactive } from "vue";
import Dialog from "primevue/dialog";
import ContentSelector from "@/components/ContentSelector.vue";

const props = defineProps({
  content: {
    type: Array,
    required: true,
  },
  action: {
    type: String,
    required: false,
    default: null,
  },
});

const { content, action } = reactive(props);

const dialogVisible = ref(false);
const showDialog = () => (dialogVisible.value = true);

const header = ref(content[0].name || `${content[0].did}-${action}`);
const handleContentSelected = (content) => (header.value = content.name);
</script>

<style lang="scss">
@import "@/styles/components/content-dialog.scss";
</style>
