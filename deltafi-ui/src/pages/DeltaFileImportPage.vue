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
  <div class="deltafile-import-page">
    <PageHeader heading="Import DeltaFiles" />
    <div class="mb-3 row">
      <div class="col-12">
        <FileUpload ref="fileUploaderRef" :multiple="true" choose-label="Add Files" cancel-label="Clear" accept=".tar,application/x-tar" :custom-upload="true" @uploader="onUpload">
          <template #content="{ files, removeFileCallback }">
            <div class="p-fileupload-files">
              <div v-for="(file, index) of files" :key="file.name + file.type + file.size" class="p-fileupload-row">
                <div class="p-fileupload-filename">
                  {{ file.name }}
                </div>
                <div>{{ formatSize(file.size) }}</div>
                <div>
                  <Button icon="pi pi-times" @click="onRemoveFile(removeFileCallback, index)" />
                </div>
              </div>
            </div>
          </template>
          <template #empty>
            <i class="ml-3">Drag and drop DeltaFile tar archives here to import.</i>
          </template>
        </FileUpload>
      </div>
    </div>

    <div class="mb-3 row">
      <div class="col-12">
        <CollapsiblePanel v-if="imports.length" header="Imported DeltaFiles" class="table-panel">
          <template #icons>
            <Button class="p-panel-header-icon p-link p-mr-2" @click="importsMenuToggle">
              <span class="fas fa-cog" />
            </Button>
            <Menu id="config_menu" ref="importsMenu" :model="importsMenuItems" :popup="true" />
          </template>
          <DataTable responsive-layout="scroll" sort-field="importedTimestamp" :sort-order="-1" :value="imports" striped-rows class="p-datatable-sm p-datatable-gridlines imports-table" :row-class="importsRowClass" data-key="filename">
            <Column field="filename" header="Filename" class="filename-column" />
            <Column field="status" header="Status" class="status-column">
              <template #body="row">
                <span v-if="row.data.loading">
                  <ProgressBar :value="row.data.percentComplete" />
                </span>
                <span v-else-if="row.data.error"> <i class="fas fa-times text-danger" /> Error </span>
                <span v-else> <i class="fas fa-check text-success" /> Success </span>
              </template>
            </Column>
            <Column field="count" header="DeltaFiles Imported" class="count-column">
              <template #body="row">
                <span v-if="row.data.count !== null">{{ row.data.count }}</span>
                <span v-else>-</span>
              </template>
            </Column>
            <Column field="bytes" header="Bytes Imported" class="bytes-column">
              <template #body="row">
                <span v-if="row.data.bytes !== null">{{ formatSize(row.data.bytes) }}</span>
                <span v-else>-</span>
              </template>
            </Column>
            <Column field="importedTimestamp" header="Imported At" class="imported-timestamp-column">
              <template #body="row">
                <Timestamp :timestamp="row.data.importedTimestamp" />
              </template>
            </Column>
          </DataTable>
        </CollapsiblePanel>
      </div>
    </div>
  </div>
</template>

<script setup>
import CollapsiblePanel from "@/components/CollapsiblePanel.vue";
import PageHeader from "@/components/PageHeader.vue";
import ProgressBar from "@/components/deprecatedPrimeVue/ProgressBar.vue";
import Timestamp from "@/components/Timestamp.vue";
import useImportDeltaFile from "@/composables/useImportDeltaFile";
import { ref } from "vue";
import { usePrimeVue } from "primevue/config";

import Button from "primevue/button";
import Column from "primevue/column";
import DataTable from "primevue/datatable";
import FileUpload from "primevue/fileupload";
import Menu from "primevue/menu";

const primevue = usePrimeVue();

const fileUploaderRef = ref();
const imports = ref([]);
const importsMenu = ref();
const { importDeltaFile } = useImportDeltaFile();

const importsMenuItems = ref([
  {
    label: "Options",
    items: [
      {
        label: "Clear Imports",
        icon: "fas fa-times",
        command: () => {
          clearImports();
        },
      },
    ],
  },
]);

const importsMenuToggle = (event) => {
  importsMenu.value.toggle(event);
};

const onUpload = (event) => {
  const importedTimestamp = new Date();
  for (const file of event.files) {
    const result = importDeltaFile(file);
    result["importedTimestamp"] = importedTimestamp;
    imports.value.unshift(result);
  }
  fileUploaderRef.value.files = [];
};

const clearImports = () => {
  imports.value = [];
};

const importsRowClass = (data) => {
  return data.error ? "table-danger" : null;
};

const formatSize = (bytes) => {
  const k = 1024;
  const dm = 3;
  const sizes = primevue.config.locale.fileSizeTypes;

  if (bytes === 0) {
    return `0 ${sizes[0]}`;
  }

  const i = Math.floor(Math.log(bytes) / Math.log(k));
  const formattedSize = parseFloat((bytes / Math.pow(k, i)).toFixed(dm));

  return `${formattedSize} ${sizes[i]}`;
};

const onRemoveFile = (removeFileCallback, index) => {
  removeFileCallback(index);
};
</script>

<style>
.deltafile-import-page {
  .p-fileupload-empty {
    padding: 1.5rem 1rem;
  }

  .p-fileupload-content {
    position: relative;
    padding: 0 !important;
  }

  .p-fileupload-row {
    display: flex;
    align-items: center;
  }

  .p-fileupload-row>div {
    flex: 1 1 auto;
    width: 33%;
  }

  .p-fileupload-row>div:last-child {
    text-align: right;
  }

  .p-fileupload-content .p-progressbar {
    width: 100%;
    position: absolute;
    top: 0;
    left: 0;
  }

  .p-button.p-fileupload-choose {
    position: relative;
    overflow: hidden;
  }

  .p-button.p-fileupload-choose input[type="file"] {
    display: none;
  }

  .p-fileupload-choose.p-fileupload-choose-selected input[type="file"] {
    display: none;
  }

  .p-fileupload-filename {
    word-break: break-all;
  }

  .p-fluid .p-fileupload .p-button {
    width: auto;
  }

  .p-fileupload-buttonbar {
    >* {
      margin-right: 0.5rem;
    }
  }

  .imports-table {
    .filename-column {
      overflow-wrap: anywhere;
    }

    .status-column {
      width: 15%;
    }

    .count-column {
      width: 15%;
    }

    .bytes-column {
      width: 15%;
    }

    .imported-timestamp-column {
      width: 15%;
    }
  }
}
</style>
