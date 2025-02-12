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
  <div class="deltafile-upload-page">
    <PageHeader heading="Upload Files" />
    <div class="mb-3 row">
      <div class="col-12">
        <Panel class="metadata-panel" header="Metadata">
          <template #header>
            <Button label="Add Metadata Field" icon="pi pi-plus" class="mr-2" @click="addMetadataField" />
            <Button label="Clear" icon="pi pi-times" :disabled="metadataClearDisabled" class="mr-2" @click="clearMetadata" />
            <div class="float-right btn-group">
              <Button v-tooltip.bottom="'Download Metadata To File'" label="Download Metadata" icon="fas fa-download fa-fw" class="p-button-md p-button-secondary p-button-outlined mx-1" :disabled="metadataClearDisabled" @click="onExportMetadata" />
              <FileUpload ref="metadataFileUploader" v-tooltip.bottom="'Upload Metadata From File'" auto mode="basic" choose-label="Upload Metadata" choose-icon="fas fa-upload fa-fw" accept=".json,application/json" :file-limit="1" custom-upload class="metadata-upload p-button-secondary p-button-outlined mx-1" @uploader="preUploadMetadataValidation" @click="setOverlayPanelPosition" />
              <Button v-tooltip.bottom="'Import Metadata From DeltaFile'" class="metadata-upload p-button-secondary p-button-outlined mx-1" icon="pi pi-file-import" label="Import Metadata" @click="showImportDialog" />
              <OverlayPanel ref="errorOverlayPanel" dismissable show-close-icon @hide="clearMetadataUploadErrors">
                <Message severity="error" :sticky="true" class="mb-2 mt-0" :closable="false">
                  <ul>
                    <div v-for="(error, key) in _.uniq(errorsList)" :key="key">
                      <li class="text-wrap text-break">
                        {{ error }}
                      </li>
                    </div>
                  </ul>
                </Message>
              </OverlayPanel>
            </div>
          </template>
          <div class="row">
            <div class="col-5 p-fluid">
              <InputText type="text" value="Data Source" disabled />
            </div>
            <div class="col-5">
              <Dropdown v-model="selectedDataSource" :options="formattedDataSourceNames" placeholder="Select a REST Data Source" show-clear :class="dataSourceDropdownClass" />
            </div>
          </div>
          <div v-for="field in metadata" :key="field" class="row mt-4 p-fluid">
            <div class="col-5">
              <InputText v-model.trim="field.key" type="text" placeholder="Key" />
            </div>
            <div class="col-5">
              <InputText v-model.trim="field.value" type="text" placeholder="Value" />
            </div>
            <div class="col-2">
              <Button icon="pi pi-times" @click="removeMetadataField(field)" />
            </div>
          </div>
          <ScrollTop target="parent" :threshold="100" icon="pi pi-arrow-up" />
        </Panel>
      </div>
    </div>

    <div class="mb-3 row">
      <div class="col-12">
        <FileUpload ref="fileUploaderRef" :multiple="true" choose-label="Add Files" cancel-label="Clear" :custom-upload="true" @uploader="onUpload">
          <template #content="{ files, removeFileCallback }">
            <div class="p-fileupload-files">
              <div v-for="(file, index) of files" :key="file.name + file.type + file.size" class="p-fileupload-row">
                <div class="p-fileupload-filename">
                  {{ file.name }}
                </div>
                <div>{{ formatSize(file.size) }}</div>
                <div>
                  <InputText v-model.trim="file['customContentType']" type=" text" :placeholder="file['type']" />
                </div>
                <div>
                  <Button icon="pi pi-times" @click="onRemoveFile(removeFileCallback, index)" />
                </div>
              </div>
            </div>
          </template>
          <template #empty>
            <i class="ml-3">Drag and drop files to here to upload.</i>
          </template>
        </FileUpload>
      </div>
    </div>

    <div class="mb-3 row">
      <div class="col-12">
        <CollapsiblePanel v-if="deltaFiles.length" header="Uploaded DeltaFiles" class="table-panel">
          <template #icons>
            <Button class="p-panel-header-icon p-link p-mr-2" @click="deltaFilesMenuToggle">
              <span class="fas fa-cog" />
            </Button>
            <Menu id="config_menu" ref="deltaFilesMenu" :model="deltaFilesMenuItems" :popup="true" />
          </template>
          <DataTable responsive-layout="scroll" sort-field="uploadedTimestamp" :sort-order="-1" :value="deltaFiles" striped-rows class="p-datatable-sm p-datatable-gridlines deltafiles" :row-class="uploadsRowClass" data-key="filename">
            <Column field="dids" header="DID(s)" class="did-column">
              <template #body="file">
                <span v-if="file.data.loading">
                  <ProgressBar :value="file.data.percentComplete" />
                </span>
                <span v-else-if="file.data.error"> <i class="fas fa-times" /> Error </span>
                <span v-else>
                  <span v-for="(did, index) in file.data.dids" :key="did">
                    <DidLink :did="did" /><br v-if="index != file.data.dids.length - 1">
                  </span>
                </span>
              </template>
            </Column>
            <Column field="filename" header="Filename" class="filename-column" />
            <Column field="dataSource" header="Data Source" class="data-source-column" />
            <Column field="uploadedTimestamp" header="Uploaded At" class="updated-timestamp-column">
              <template #body="file">
                <Timestamp :timestamp="file.data.uploadedTimestamp" />
              </template>
            </Column>
            <Column field="uploadedMetadata" header="Metadata" class="metadata-column">
              <template #body="file">
                <span v-if="!_.isEmpty(file.data.uploadedMetadata)" class="btn-group">
                  <DialogTemplate component-name="MetadataViewer" header="Metadata" :metadata="formatMetadataForViewer(file.data.filename, file.data.uploadedMetadata)">
                    <Button v-tooltip.top.hover="'View Metadata'" icon="fas fa-table" class="content-button p-button-link p-0" />
                  </DialogTemplate>
                  <Button id="replayMetadata" v-tooltip.top.hover="'Reuse Metadata'" icon="fas fa-redo" class="content-button p-button-link button2 p-0" @click="replayMetadata(file.data)" />
                </span>
              </template>
            </Column>
          </DataTable>
        </CollapsiblePanel>
      </div>
    </div>
  </div>
  <ImportMetadataDialog v-model:visible="showUploadDialog" @meta-data-value="onMetaImport" />
</template>

<script setup>
import CollapsiblePanel from "@/components/CollapsiblePanel.vue";
import DialogTemplate from "@/components/DialogTemplate.vue";
import DidLink from "@/components/DidLink.vue";
import ImportMetadataDialog from "@/components/ImportMetadataDialog.vue";
import PageHeader from "@/components/PageHeader.vue";
import ProgressBar from "@/components/deprecatedPrimeVue/ProgressBar.vue";
import Timestamp from "@/components/Timestamp.vue";
import useFlows from "@/composables/useFlows";
import useIngress from "@/composables/useIngress";
import useMetadataConfiguration from "@/composables/useMetadataConfiguration";
import useNotifications from "@/composables/useNotifications";
import { computed, onBeforeMount, onMounted, ref, watch } from "vue";
import { StorageSerializers, useStorage } from "@vueuse/core";
import _ from "lodash";
import { usePrimeVue } from "primevue/config";

import Button from "primevue/button";
import Column from "primevue/column";
import DataTable from "primevue/datatable";
import Dropdown from "primevue/dropdown";
import FileUpload from "primevue/fileupload";
import InputText from "primevue/inputtext";
import Menu from "primevue/menu";
import Message from "primevue/message";
import OverlayPanel from "primevue/overlaypanel";
import Panel from "primevue/panel";
import ScrollTop from "primevue/scrolltop";

const primevue = usePrimeVue();

const uploadedTimestamp = ref(new Date());
const showUploadDialog = ref(false);
const deltaFilesMenu = ref();
const selectedDataSource = ref(null);
const metadata = ref([]);
const fileUploaderRef = ref();
const deltaFiles = ref([]);
const { allDataSourceFlowNames, fetchAllDataSourceFlowNames } = useFlows();
const { ingressFile } = useIngress();
const notify = useNotifications();
const { validateMetadataFile } = useMetadataConfiguration();
const metadataFileUploader = ref(null);
const errorsList = ref([]);
const validUpload = ref({});
const errorOverlayPanel = ref(null);
const overlayPanelPosition = ref({});
const uploadClicked = ref(false);
const formattedDataSourceNames = ref([]);

const deltaFilesMenuItems = ref([
  {
    label: "Options",
    items: [
      {
        label: "Clear DeltaFiles",
        icon: "fas fa-times",
        command: () => {
          clearDeltaFilesSession();
        },
      },
    ],
  },
]);

const dataSourceDropdownClass = computed(() => {
  return selectedDataSource.value == null && uploadClicked.value ? "invalid" : null;
});

const showImportDialog = () => {
  showUploadDialog.value = true;
};

const onMetaImport = (importData) => {
  showUploadDialog.value = false;
  selectedDataSource.value = importData.dataSource;
  metadata.value = importData.metadata;
  storeMetaDataUploadSession();
};

onBeforeMount(() => {
  getSelectedDataSourceSession();
  getMetadataSession();
  getDeltaFileSession();
});

const deltaFilesMenuToggle = (event) => {
  deltaFilesMenu.value.toggle(event);
};

const metadataRecord = computed(() => {
  const record = {};
  if (metadata.value.length > 0) {
    for (const field of metadata.value) {
      if (field.key.length > 0) record[field.key] = field.value;
    }
  }
  return record;
});

const metadataClearDisabled = computed(() => {
  return metadata.value.length == 0 && selectedDataSource.value == null;
});

const addMetadataField = () => {
  metadata.value.push({ key: "", value: "" });
};

const removeMetadataField = (field) => {
  const index = metadata.value.indexOf(field);
  metadata.value.splice(index, 1);
};

const clearMetadata = () => {
  selectedDataSource.value = null;
  selectedDataSourceStorage.value = "";
  metadata.value = [];
  metadataStorage.value = "";
};

const onUpload = (event) => {
  if (selectedDataSource.value) {
    ingressFiles(event);
  } else {
    uploadClicked.value = true;
    notify.warn("Please Select Data Source", "A data source is required to upload files.");
  }
};

const ingressFiles = async (event) => {
  uploadedTimestamp.value = new Date();
  for (const file of event.files) {
    const result = ingressFile(file, metadataRecord.value, selectedDataSource.value);
    result["uploadedTimestamp"] = uploadedTimestamp.value;
    result["uploadedMetadata"] = JSON.parse(JSON.stringify(metadata.value));
    deltaFiles.value.unshift(result);
  }
  fileUploaderRef.value.files = [];
};

watch(
  () => deltaFiles.value,
  () => {
    // If all files are done loading, store in session
    if (deltaFiles.value.every((file) => !file.loading && file.dids)) {
      storeDeltaFileUploadSession(deltaFiles.value);
    }
  },
  { deep: true }
);

// Store for the sessions user selected data source.
const selectedDataSourceStorage = useStorage("selectedDataSourceStorage-session-storage", {}, sessionStorage, { serializer: StorageSerializers.object });
// Store for the sessions user inputed metadata.
const metadataStorage = useStorage("metadataStorage-session-storage", {}, sessionStorage, { serializer: StorageSerializers.object });
// Store for the sessions user uploaded deltaFiles.
const deltaFilesStorage = useStorage("deltafiles-upload-session-storage", {}, sessionStorage, { serializer: StorageSerializers.object });

const storeMetaDataUploadSession = async () => {
  // Save off inputed metadata into store.
  metadataStorage.value = metadata.value;

  // Save off selected data source into store.
  selectedDataSourceStorage.value = selectedDataSource.value;
};

const storeDeltaFileUploadSession = async (results) => {
  // If there is no data in the deltaFiles storage then just save it off. If data is in there we want to persist it so concat the older data with
  // the new data and save it off.
  if (_.isEmpty(deltaFilesStorage.value)) {
    deltaFilesStorage.value = results;
  } else {
    deltaFilesStorage.value = _.uniqBy(_.concat(results, deltaFilesStorage.value), "dids");
  }

  // Save off inputed metadata into store.
  metadataStorage.value = metadata.value;

  // Save off selected data source into store.
  selectedDataSourceStorage.value = selectedDataSource.value;

  getDeltaFileSession();
};

const getDeltaFileSession = () => {
  if (!_.isEmpty(deltaFilesStorage.value)) {
    Object.assign(deltaFiles.value, deltaFilesStorage.value);
  }
};

const getMetadataSession = () => {
  if (!_.isEmpty(metadataStorage.value)) {
    metadata.value = metadataStorage.value;
  }
};

const getSelectedDataSourceSession = () => {
  if (!_.isEmpty(selectedDataSourceStorage.value)) {
    selectedDataSource.value = selectedDataSourceStorage.value;
  }
};

const replayMetadata = (value) => {
  metadata.value = JSON.parse(JSON.stringify(value.uploadedMetadata));
  let dataSourceSelected = {};
  dataSourceSelected = value.dataSource;
  selectedDataSource.value = dataSourceSelected;
};

const clearDeltaFilesSession = () => {
  deltaFilesStorage.value = "";
  deltaFiles.value = [];
};

const uploadsRowClass = (data) => {
  return data.error ? "table-danger" : null;
};

// Created
onMounted(async () => {
  await fetchAllDataSourceFlowNames("RUNNING");
  formatDataSourceNames();
  checkActiveFlows();
});

const formatDataSourceNames = () => {
  formattedDataSourceNames.value = allDataSourceFlowNames.value.restDataSource;
};

const checkActiveFlows = () => {
  selectedDataSource.value = allDataSourceFlowNames.value.restDataSource.includes(selectedDataSource.value) ? selectedDataSource.value : null;
};

const formatMetadataForViewer = (filename, uploadedMetadata) => {
  const metaDataObject = {};
  metaDataObject[filename] = uploadedMetadata;
  return JSON.parse(JSON.stringify(metaDataObject));
};

const onExportMetadata = () => {
  const formattedMetadata = {};
  if (_.isEmpty(selectedDataSource.value) && _.isEmpty(metadataRecord.value)) {
    return;
  }
  if (!_.isEmpty(selectedDataSource.value)) {
    formattedMetadata["dataSource"] = selectedDataSource.value;
  }
  formattedMetadata["metadata"] = metadataRecord.value;

  exportMetadataFile(formattedMetadata);
};

const exportMetadataFile = (formattedMetadata) => {
  const link = document.createElement("a");
  const downloadFileName = "metadata_export_" + new Date(Date.now()).toLocaleDateString();
  link.download = downloadFileName.toLowerCase();
  const blob = new Blob([JSON.stringify(formattedMetadata, null, 2)], {
    type: "application/json",
  });
  link.href = URL.createObjectURL(blob);
  link.click();
  URL.revokeObjectURL(link.href);
  link.remove();
};

const preUploadMetadataValidation = async (request) => {
  for (const file of request.files) {
    const reader = new FileReader();

    reader.readAsText(file);

    reader.onload = function () {
      const uploadNotValid = validateMetadataFile(reader.result);
      errorsList.value = [];
      if (uploadNotValid) {
        for (const errorMessages of uploadNotValid) {
          errorsList.value.push(errorMessages.message);
        }
        deleteMetadataFile();
        errorOverlayPanel.value.toggle(overlayPanelPosition.value, overlayPanelPosition.value.target);
      } else {
        validUpload.value = reader.result;
        uploadMetadataFile(file);
      }
    };
  }
};

const uploadMetadataFile = async (file) => {
  const parseMetadataUpload = JSON.parse(validUpload.value);
  let dataSourceSelected = {};
  if (!_.isEmpty(_.get(parseMetadataUpload, "dataSource"))) {
    dataSourceSelected = _.get(parseMetadataUpload, "dataSource");
    if (allDataSourceFlowNames.value.restDataSource.includes(dataSourceSelected)) {
      selectedDataSource.value = dataSourceSelected;
    } else {
      notify.warn("Ignoring Invalid Data Source", `The uploaded metadata included an invalid Data Source: ${dataSourceSelected}`);
    }
  }
  const reformatMetadata = [];
  for (const [key, value] of Object.entries(parseMetadataUpload.metadata)) {
    const formatMetadata = {};
    formatMetadata["key"] = key;
    formatMetadata["value"] = value;
    reformatMetadata.push(formatMetadata);
  }
  metadata.value = reformatMetadata;
  notify.success(`Uploaded ${file.name}`, `Successfully imported metadata`, 4000);
  storeMetaDataUploadSession();
  deleteMetadataFile();
};

const deleteMetadataFile = () => {
  metadataFileUploader.value.files = [];
  metadataFileUploader.value.uploadedFileCount = 0;
};

const clearMetadataUploadErrors = () => {
  errorsList.value = [];
};

const setOverlayPanelPosition = (event) => {
  overlayPanelPosition.value = event;
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
.deltafile-upload-page {
  .metadata-panel {
    .p-panel-header {
      display: block;
    }

    .p-inputtext:disabled {
      background: #eeeeee !important;
    }

    .p-panel-content {
      max-height: 45vh;
      overflow: auto;
    }
  }

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
    width: 25%;
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

  .p-fileupload-choose.metadata-upload:not(.p-disabled):hover {
    background: rgba(108, 117, 125, 0.04) !important;
    color: #6c757d !important;
    border-color: #6c757d !important;
  }

  .p-fileupload-choose.metadata-upload:not(.p-disabled):active {
    background: transparent !important;
    color: #6c757d !important;
    border-color: #6c757d !important;
  }

  .deltafiles {
    td.did-column {
      width: 16rem;
    }

    .filename-column {
      overflow-wrap: anywhere;
    }

    .data-source-column {
      width: 15%;
    }

    .updated-timestamp-column {
      width: 15%;
    }

    .metadata-column {
      width: 12%;

      .content-button {
        cursor: pointer !important;
        padding: 0.1rem 0.4rem;
        margin: 0;
        color: #333333;
      }

      .content-button:hover {
        color: #666666 !important;

        .p-button-label {
          text-decoration: none !important;
        }
      }

      .content-button:focus {
        outline: none !important;
        box-shadow: none !important;
      }
    }
  }
}

#viewMetadata,
#replayMetadata {
  display: inline-block;
  /* additional code */
}

.p-dropdown.invalid {
  border-color: #f87171;
}
</style>
