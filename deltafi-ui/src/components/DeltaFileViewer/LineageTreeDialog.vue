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
<!-- ABOUTME: Modal dialog for viewing the full lineage tree of a DeltaFile. -->
<!-- ABOUTME: Wraps LineageTreeGraph with a maximizable dialog container. -->

<template>
  <Dialog
    v-model:visible="dialogVisible"
    :header="dialogHeader"
    :style="{ width: '85vw', height: '90vh' }"
    :maximizable="true"
    :modal="true"
    :dismissable-mask="true"
    :draggable="false"
    class="lineage-tree-dialog"
  >
    <div class="dialog-content">
      <LineageTreeGraph
        v-if="dialogVisible"
        :root-did="did"
        :delta-file-data="deltaFileData"
        :initial-ancestor-depth="3"
        :initial-descendant-depth="3"
      />
    </div>
  </Dialog>
</template>

<script setup>
import { computed, ref } from "vue";
import Dialog from "primevue/dialog";
import LineageTreeGraph from "./LineageTreeGraph.vue";

const props = defineProps({
  did: {
    type: String,
    required: true,
  },
  deltaFileData: {
    type: Object,
    required: true,
  },
});

const dialogVisible = ref(false);

const dialogHeader = computed(() => {
  const name = props.deltaFileData?.name || props.did;
  const truncatedName = name.length > 50 ? name.substring(0, 47) + "..." : name;
  return `Lineage Tree: ${truncatedName}`;
});

const show = () => {
  dialogVisible.value = true;
};

const hide = () => {
  dialogVisible.value = false;
};

defineExpose({
  show,
  hide,
});
</script>

<style scoped>
.dialog-content {
  height: calc(90vh - 80px);
  width: 100%;
}

.lineage-tree-dialog :deep(.p-dialog-content) {
  padding: 0;
  overflow: hidden;
}
</style>
