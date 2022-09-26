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
  <div class="upload-page">
    <PageHeader heading="Upload Files" />
    <div class="mb-3 row">
      <div class="col-12">
        <Panel class="metadata-panel" header="Metadata">
          <template #header>
            <Button label="Add Metadata Field" icon="pi pi-plus" class="mr-2" @click="addMetadataField" />
            <Button label="Clear" icon="pi pi-times" :disabled="metadataClearDisabled" class="mr-2" @click="clearMetadata" />
            <div class="float-right btn-group">
              <Button label="Export Metadata" icon="fas fa-download fa-fw" class="p-button-md p-button-secondary p-button-outlined mx-1" :disabled="metadataClearDisabled" @click="onExportMetadata" />
              <FileUpload ref="metadataFileUploader" auto mode="basic" choose-label="Import Metadata" accept=".json,application/JSON" :file-limit="1" custom-upload class="p-button-md p-button-secondary p-button-outlined mx-1" @uploader="preUploadMetadataValidation" @click="setOverlayPanelPosition" />
              <OverlayPanel ref="errorOverlayPanel" dismissable show-close-icon @hide="clearMetadataUploadErrors">
                <Message severity="error" :sticky="true" class="mb-2 mt-0" :closable="false">
                  <ul>
                    <div v-for="(error, key) in _.uniq(errorsList)" :key="key">
                      <li class="text-wrap text-break">{{ error }}</li>
                    </div>
                  </ul>
                </Message>
              </OverlayPanel>
            </div>
          </template>
          <div class="row">
            <div class="col-5 p-fluid">
              <InputText type="text" value="Ingress Flow" disabled />
            </div>
            <div class="col-5">
              <!-- TODO: GitLab issue "Fix multi-select dropdown data bouncing" (https://gitlab.com/systolic/deltafi/deltafi-ui/-/issues/96). Placeholder hacky fix to stop the bouncing of data within the field. -->
              <Dropdown v-model="selectedFlow" :options="activeIngressFlows" option-label="name" :placeholder="selectedFlow ? selectedFlow.name + ' ' : 'Select an Ingress Flow'" show-clear />
            </div>
          </div>
          <div v-for="field in metadata" :key="field" class="row mt-4 p-fluid">
            <div class="col-5">
              <InputText v-model="field.key" type="text" placeholder="Key" />
            </div>
            <div class="col-5">
              <InputText v-model="field.value" type="text" placeholder="Value" />
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
        <FileUpload ref="fileUploader" :multiple="true" choose-label="Add Files" cancel-label="Clear" :custom-upload="true" @uploader="onUpload">
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
          <DataTable responsive-layout="scroll" sort-field="uploadedTimestamp" :sort-order="-1" :value="deltaFiles" striped-rows class="p-datatable-sm p-datatable-gridlines deltafiles" :row-class="uploadsRowClass">
            <Column field="did" header="DID" class="did-column">
              <template #body="file">
                <span v-if="file.data.loading">
                  <ProgressBar :value="file.data.percentComplete" />
                </span>
                <span v-else-if="file.data.error"> <i class="fas fa-times" /> Error </span>
                <DidLink v-else :did="file.data.did" />
              </template>
            </Column>
            <Column field="filename" header="Filename" class="filename-column" />
            <Column field="flow" header="Ingress Flow" class="flow-column" />
            <Column field="uploadedTimestamp" header="Uploaded At" class="updated-timestamp-column">
              <template #body="file">
                <Timestamp :timestamp="file.data.uploadedTimestamp" />
              </template>
            </Column>
            <Column field="uploadedMetadata" header="Metadata" class="metadata-column">
              <template #body="file">
                <span v-if="!_.isEmpty(file.data.uploadedMetadata)">
                  <MetadataViewer id="viewMetadata" :metadata-references="formatMetadataforViewer(file.data.filename, file.data.uploadedMetadata)">
                    <Button v-tooltip.top.hover="'View Metadata'" icon="fas fa-table" class="content-button p-button-link p-0" />
                  </MetadataViewer>
                  <Button id="replayMetadata" v-tooltip.top.hover="'Reuse Metadata'" icon="fas fa-redo" class="content-button p-button-link button2 p-0" @click="replayMetadata(file.data)" />
                </span>
              </template>
            </Column>
          </DataTable>
        </CollapsiblePanel>
      </div>
    </div>
  </div>
</template>

<script setup>
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
import ProgressBar from "primevue/progressbar";
import ScrollTop from "primevue/scrolltop";
import CollapsiblePanel from "@/components/CollapsiblePanel";
import MetadataViewer from "@/components/MetadataViewer.vue";
import PageHeader from "@/components/PageHeader.vue";
import DidLink from "@/components/DidLink.vue";
import Timestamp from "@/components/Timestamp.vue";
import useMetadataConfiguration from "@/composables/useMetadataConfiguration";
import useFlows from "@/composables/useFlows";
import useIngress from "@/composables/useIngress";
import useNotifications from "@/composables/useNotifications";
import { useStorage, StorageSerializers } from "@vueuse/core";
import { ref, computed, onBeforeMount, watch } from "vue";
import _ from "lodash";

const uploadedTimestamp = ref(new Date());
const deltaFilesMenu = ref();
const selectedFlow = ref(null);
const metadata = ref([]);
const fileUploader = ref();
const deltaFiles = ref([]);
const { fetchActiveIngressFlows, activeIngressFlows } = useFlows();
const { ingressFile } = useIngress();
const notify = useNotifications();
const { validateMetadataFile } = useMetadataConfiguration();
const metadataFileUploader = ref(null);
const errorsList = ref([]);
const validUpload = ref({});
const errorOverlayPanel = ref(null);
const overlayPanelPosition = ref({});

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

onBeforeMount(() => {
  getSelectedFlowSession();
  getMetadataSession();
  getDeltaFileSession();
});

const deltaFilesMenuToggle = (event) => {
  deltaFilesMenu.value.toggle(event);
};

const metadataRecord = computed(() => {
  let record = {};
  if (metadata.value.length > 0) {
    for (const field of metadata.value) {
      if (field.key.length > 0) record[field.key] = field.value;
    }
  }
  return record;
});

const metadataClearDisabled = computed(() => {
  return metadata.value.length == 0 && selectedFlow.value == null;
});

const addMetadataField = () => {
  metadata.value.push({ key: "", value: "" });
};

const removeMetadataField = (field) => {
  let index = metadata.value.indexOf(field);
  metadata.value.splice(index, 1);
};

const clearMetadata = () => {
  selectedFlow.value = null;
  selectedFlowStorage.value = "";
  metadata.value = [];
  metadataStorage.value = "";
};

const onUpload = (event) => {
  ingressFiles(event);
};

const ingressFiles = async (event) => {
  uploadedTimestamp.value = new Date();
  for (let file of event.files) {
    const result = ingressFile(file, metadataRecord.value, _.get(selectedFlow.value, "name", null));
    result["uploadedTimestamp"] = uploadedTimestamp.value;
    result["uploadedMetadata"] = JSON.parse(JSON.stringify(metadata.value));
    deltaFiles.value.unshift(result);
  }
  fileUploader.value.files = [];
};

watch(
  () => deltaFiles.value,
  () => {
    // If all files are done loading, store in session
    if (deltaFiles.value.every((file) => !file.loading && file.did)) {
      storeDeltaFileUploadSession(deltaFiles.value);
    }
  },
  { deep: true }
);

// Store for the sessions user selected Flow.
const selectedFlowStorage = useStorage("selectedFlowStorage-session-storage", {}, sessionStorage, { serializer: StorageSerializers.object });
// Store for the sessions user inputed metadata.
const metadataStorage = useStorage("metadataStorage-session-storage", {}, sessionStorage, { serializer: StorageSerializers.object });
// Store for the sessions user uploaded deltaFiles.
const deltaFilesStorage = useStorage("deltafiles-upload-session-storage", {}, sessionStorage, { serializer: StorageSerializers.object });

const storeMetaDataUploadSession = async () => {
  // Save off inputed metadata into store.
  metadataStorage.value = metadata.value;

  // Save off selected flow into store.
  selectedFlowStorage.value = selectedFlow.value;
};

const storeDeltaFileUploadSession = async (results) => {
  // If there is no data in the deltaFiles storage then just save it off. If data is in there we want to persist it so concat the older data with
  // the new data and save it off.
  if (_.isEmpty(deltaFilesStorage.value)) {
    deltaFilesStorage.value = results;
  } else {
    deltaFilesStorage.value = _.uniqBy(_.concat(results, deltaFilesStorage.value), "did");
  }

  // Save off inputed metadata into store.
  metadataStorage.value = metadata.value;

  // Save off selected flow into store.
  selectedFlowStorage.value = selectedFlow.value;

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

const getSelectedFlowSession = () => {
  if (!_.isEmpty(selectedFlowStorage.value)) {
    selectedFlow.value = selectedFlowStorage.value;
  }
};

const replayMetadata = (value) => {
  metadata.value = JSON.parse(JSON.stringify(value.uploadedMetadata));
  let flowSelected = {};
  flowSelected["name"] = value.flow;
  selectedFlow.value = flowSelected;
};

const clearDeltaFilesSession = () => {
  deltaFilesStorage.value = "";
  deltaFiles.value = [];
};

const uploadsRowClass = (data) => {
  return data.error ? "table-danger" : null;
};

// Created
fetchActiveIngressFlows();

const formatMetadataforViewer = (filename, uploadedMetadata) => {
  let metaDataObject = {};
  metaDataObject[filename] = uploadedMetadata;
  return JSON.parse(JSON.stringify(metaDataObject));
};

const onExportMetadata = () => {
  let formattedMetadata = {};
  if (_.isEmpty(selectedFlow.value) && _.isEmpty(metadataRecord.value)) {
    return;
  }
  if (!_.isEmpty(selectedFlow.value)) {
    formattedMetadata["flow"] = selectedFlow.value.name;
  }
  formattedMetadata["metadata"] = metadataRecord.value;

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

const preUploadMetadataValidation = async (request) => {
  for (let file of request.files) {
    let reader = new FileReader();

    reader.readAsText(file);

    reader.onload = function () {
      let uploadNotValid = validateMetadataFile(reader.result);
      errorsList.value = [];
      if (uploadNotValid) {
        for (let errorMessages of uploadNotValid) {
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
  let parseMetadataUpload = JSON.parse(validUpload.value);
  let flowSelected = {};
  if (!_.isEmpty(_.get(parseMetadataUpload, "flow"))) {
    flowSelected["name"] = _.get(parseMetadataUpload, "flow");
    selectedFlow.value = flowSelected;
  } else {
    selectedFlow.value = null;
  }
  let reformatMetadata = [];
  for (const [key, value] of Object.entries(parseMetadataUpload.metadata)) {
    let formatMetadata = {};
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
</script>

<style lang="scss">
@import "@/styles/pages/deltafile-upload-page.scss";
</style>
