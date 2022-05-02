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
  <div class="metadata-viewer">
    <span @click="showDialog()">
      <slot />
    </span>
    <Dialog v-model:visible="dialogVisible" position="top" header="Metadata" :style="{ width: '75vw' }" :maximizable="true" :modal="true" :dismissable-mask="true">
      <div class="metadata-viewer">
        <div v-for="(metadataArray, actionName) in props.metadataReferences" :key="actionName">
          <CollapsiblePanel :header="actionName" class="table-panel mb-3">
            <DataTable responsive-layout="scroll" :value="metadataArray" striped-rows sort-field="key" :sort-order="1" class="p-datatable-sm">
              <Column field="key" header="Key" :style="{ width: '25%' }" :sortable="true" />
              <Column field="value" header="Value" :style="{ width: '75%' }" :sortable="true" />
            </DataTable>
          </CollapsiblePanel>
        </div>
      </div>
    </Dialog>
  </div>
</template>

<script setup>
import Dialog from "primevue/dialog";
import DataTable from "primevue/datatable";
import Column from "primevue/column";
import CollapsiblePanel from "@/components/CollapsiblePanel.vue";

import { ref, defineProps, defineExpose } from "vue";

const props = defineProps({
  metadataReferences: {
    type: Object,
    required: true,
  },
});

const dialogVisible = ref(false);

const showDialog = () => {
  dialogVisible.value = true;
};

defineExpose({
  showDialog
});
</script>
