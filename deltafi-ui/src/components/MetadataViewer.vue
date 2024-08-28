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
  <div class="metadata-viewer">
    <div v-for="(metadataArray, actionName) in props.metadata" :key="actionName">
      <CollapsiblePanel v-if="metadataArray.length > 0" :header="actionName" class="table-panel">
        <DataTable responsive-layout="scroll" :value="metadataArray" striped-rows sort-field="key" data-key="key" :sort-order="1" class="p-datatable-sm" scroll-height="500px">
          <Column field="key" header="Key" :style="{ width: '25%' }" :sortable="true" />
          <Column field="value" header="Value" :style="{ width: '75%' }" :sortable="true" />
        </DataTable>
      </CollapsiblePanel>
      <CollapsiblePanel v-if="!_.isEmpty(props.deletedMetadata)" header="Deleted Metadata" class="table-panel mt-3">
        <DataTable responsive-layout="scroll" :value="deletedMetadata" striped-rows sort-field="key" data-key="name" :sort-order="1" class="p-datatable-sm" scroll-height="500px">
          <template #empty>No Deleted Metadata found.</template>
          <Column field="name" header="Action" :style="{ width: '25%' }" :sortable="true" />
          <Column field="deleteMetadataKeys" header="Deleted Metadata Keys" :style="{ width: '75%' }" :sortable="true">
            <template #body="{ data }">
              {{ data.deleteMetadataKeys.join(", ") }}
            </template>
          </Column>
        </DataTable>
      </CollapsiblePanel>
    </div>
    <teleport v-if="isMounted" to="#dialogTemplate">
      <div class="p-dialog-footer">
        <Button v-tooltip.bottom="'Download Metadata To File'" label="Download Metadata" icon="fas fa-download fa-fw" class="p-button-md p-button-secondary p-button-outlined mx-1" @click="onExportMetadata" />
      </div>
    </teleport>
  </div>
</template>

<script setup>
import CollapsiblePanel from "@/components/CollapsiblePanel.vue";
import { ref, defineProps, defineExpose } from "vue";
import { useMounted } from "@vueuse/core";

import Button from "primevue/button";
import Column from "primevue/column";
import DataTable from "primevue/datatable";
import _ from "lodash";

const isMounted = ref(useMounted());

const props = defineProps({
  metadata: {
    type: Object,
    required: true,
  },
  deletedMetadata: {
    type: Object || null,
    required: false,
    default: null,
  },
  flowName: {
    type: String || null,
    required: false,
    default: null,
  },
});

const onExportMetadata = () => {
  let formattedMetadata = {};
  if (!_.isEmpty(props.flowName)) {
    formattedMetadata["flow"] = props.flowName;
  }

  // Combine objects of Key Name and Value Name into a key value pair
  let combineKeyValue = Object.values(props.metadata)[0].reduce((r, { key, value }) => ((r[key] = value), r), {});
  formattedMetadata["metadata"] = combineKeyValue;

  exportMetadataFile(formattedMetadata);
};

const exportMetadataFile = (formattedMetadata) => {
  let link = document.createElement("a");
  let downloadFileName = "metadata_export_" + new Date(Date.now()).toLocaleDateString();
  link.download = downloadFileName.toLowerCase();
  let blob = new Blob([JSON.stringify(formattedMetadata, null, 2)], {
    type: "application/json",
  });
  link.href = URL.createObjectURL(blob);
  link.click();
  URL.revokeObjectURL(link.href);
  link.remove();
};

const dialogVisible = ref(false);

const showDialog = () => {
  dialogVisible.value = true;
};

defineExpose({
  showDialog,
});
</script>
