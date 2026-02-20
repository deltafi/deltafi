<!--
   ABOUTME: Button that displays search icon with keyboard shortcut hint.
   ABOUTME: Shows ⌘K on Mac and Ctrl-K on other platforms.
-->
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
  <Tag class="command-palette-button" severity="secondary" :value="shortcutLabel" @click="openCommandPalette">
    <template #icon>
      <i class="pi pi-search mr-2" />
    </template>
  </Tag>
</template>

<script setup>
import Tag from "primevue/tag";
import { computed } from "vue";
import useCommandPalette from "@/composables/useCommandPalette";

const { open: openCommandPalette } = useCommandPalette();
const isMac = navigator.platform.toUpperCase().indexOf("MAC") >= 0;

const shortcutLabel = computed(() => {
  return isMac ? "⌘+K" : "Ctrl+K";
});
</script>

<style scoped lang="scss">
.command-palette-button {
  cursor: pointer;
  background-color: #efefef;
  border: 1px solid #c9cdd1;
  color: #495057;
}
</style>
