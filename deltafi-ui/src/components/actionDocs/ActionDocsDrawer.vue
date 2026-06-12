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
  <Drawer v-model:visible="visible" position="right" :header="header" class="action-docs-drawer" :style="{ width: '760px', maxWidth: '94vw' }">
    <ActionDocsContent :markdown="action?.docsMarkdown || ''" />
  </Drawer>
</template>

<script setup>
import { computed } from "vue";
import Drawer from "primevue/drawer";
import ActionDocsContent from "@/components/actionDocs/ActionDocsContent.vue";

const visible = defineModel("visible", { type: Boolean, default: false });
const props = defineProps({
  action: {
    type: Object,
    default: null,
  },
});

const header = computed(() => {
  const name = props.action?.name ?? "Action";
  const i = name.lastIndexOf(".");
  return i >= 0 ? name.slice(i + 1) : name;
});
</script>

<style scoped>
.action-docs-drawer :deep(.p-drawer-content) {
  padding: 0 1.25rem 1.5rem;
}
</style>
