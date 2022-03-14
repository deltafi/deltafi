<template>
  <div class="upload-page">
    <PageHeader heading="Upload Files" />
    <div class="mb-3 row">
      <div class="col-12">
        <Panel class="metadata-panel" header="Metadata">
          <template #header>
            <Button label="Add Metadata Field" icon="pi pi-plus" class="mr-2" @click="addMetadataField" />
            <Button label="Clear" icon="pi pi-times" :disabled="metadataClearDisabled" class="mr-2" @click="clearMetadata" />
          </template>
          <div class="row">
            <div class="col-5 p-fluid">
              <InputText type="text" value="Flow" disabled />
            </div>
            <div class="col-5">
              <!-- TODO: GitLab issue "Fix multi-select dropdown data bouncing" (https://gitlab.com/systolic/deltafi/deltafi-ui/-/issues/96). Placeholder hacky fix to stop the bouncing of data within the field. -->
              <Dropdown v-model="selectedFlow" :options="ingressFlows" option-label="name" :placeholder="selectedFlow ? selectedFlow.name + ' ' : 'Select an Ingress Flow'" :class="{ 'p-invalid': flowSelectInvalid }" />
              <InlineMessage v-if="flowSelectInvalid" class="ml-3">Flow is required</InlineMessage>
            </div>
          </div>
          <div v-for="field in metadata" :key="field" class="row mt-4 p-fluid">
            <div class="col-5">
              <InputText v-model="field.key" type="text" placeholder="Key" />
            </div>
            <div class="col-5">
              <InputText v-model="field.value" type="text" placeholder="Value" />
            </div>
            <div class="col-2 text-right">
              <Button icon="pi pi-times" @click="removeMetadataField(field)" />
            </div>
          </div>
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
          <DataTable responsive-layout="scroll" :value="deltaFiles" striped-rows class="p-datatable-sm p-datatable-gridlines deltafiles" :row-class="uploadsRowClass">
            <Column field="did" header="DID" class="did-column">
              <template #body="file">
                <span v-if="file.data.loading">
                  <ProgressBar :value="file.data.percentComplete" />
                </span>
                <span v-else-if="file.data.error"> <i class="fas fa-times" /> Error </span>
                <router-link v-else class="monospace" :to="{ path: '/deltafile/viewer/' + file.data.did }">{{ file.data.did }}</router-link>
              </template>
            </Column>
            <Column field="filename" header="Filename" class="filename-column" />
            <Column field="flow" header="Flow" class="flow-column" />
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
import InlineMessage from "primevue/inlinemessage";
import InputText from "primevue/inputtext";
import Menu from "primevue/menu";
import Panel from "primevue/panel";
import ProgressBar from "primevue/progressbar";
import CollapsiblePanel from "@/components/CollapsiblePanel";
import MetadataViewer from "@/components/MetadataViewer.vue";
import PageHeader from "@/components/PageHeader.vue";
import Timestamp from "@/components/Timestamp.vue";
import useFlows from "@/composables/useFlows";
import useIngress from "@/composables/useIngress";
import { useStorage, StorageSerializers } from "@vueuse/core";
import { ref, computed, onBeforeMount } from "vue";
import _ from "lodash";

const uploadedTimestamp = ref(new Date());
const deltaFilesMenu = ref();
const selectedFlow = ref(null);
const flowSelectError = ref(false);
const metadata = ref([]);
const fileUploader = ref();
const deltaFiles = ref([]);
const { ingressFlows, fetchIngressFlows } = useFlows();
const { ingressFile } = useIngress();

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

const flowSelectInvalid = computed(() => {
  return flowSelectError.value && selectedFlow.value == null;
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
  flowSelectError.value = false;
  selectedFlow.value = null;
  selectedFlowStorage.value = "";
  metadata.value = [];
  metadataStorage.value = "";
};

const onUpload = (event) => {
  if (selectedFlow.value == null) {
    flowSelectError.value = true;
  } else {
    ingressFiles(event);
  }
};

const ingressFiles = async (event) => {
  let results = new Array();
  uploadedTimestamp.value = new Date();
  for (let file of event.files) {
    const result = await ingressFile(file, selectedFlow.value.name, metadataRecord.value);
    result["uploadedTimestamp"] = uploadedTimestamp.value;
    result["uploadedMetadata"] = metadata.value;
    results.push(result);
  }
  storeDeltaFileUploadSession(results);
  fileUploader.value.files = [];
};

// Store for the sessions user selected Flow.
const selectedFlowStorage = useStorage("selectedFlowStorage-session-storage", {}, sessionStorage, { serializer: StorageSerializers.object });
// Store for the sessions user inputed metadata.
const metadataStorage = useStorage("metadataStorage-session-storage", {}, sessionStorage, { serializer: StorageSerializers.string });
// Store for the sessions user uploaded deltaFiles.
const deltaFilesStorage = useStorage("deltafiles-upload-session-storage", {}, sessionStorage, { serializer: StorageSerializers.string });

const storeDeltaFileUploadSession = async (results) => {
  // If there is no data in the deltaFiles storage then just save it off. If data is in there we want to persist it so concat the older data with
  // the new data and save it off.
  if (_.isEmpty(deltaFilesStorage.value)) {
    deltaFilesStorage.value = JSON.stringify(results);
  } else {
    deltaFilesStorage.value = JSON.stringify(_.uniqBy(_.concat(JSON.parse(deltaFilesStorage.value), results), "did"));
  }

  // Save off inputed metadata into store.
  metadataStorage.value = JSON.stringify(metadata.value);

  // Save off selected flow into store.
  selectedFlowStorage.value = selectedFlow.value;

  getDeltaFileSession();
};

const getDeltaFileSession = () => {
  if (!_.isEmpty(deltaFilesStorage.value)) {
    deltaFiles.value = JSON.parse(deltaFilesStorage.value);
  }
};

const getMetadataSession = () => {
  if (!_.isEmpty(metadataStorage.value)) {
    metadata.value = JSON.parse(metadataStorage.value);
  }
};

const getSelectedFlowSession = () => {
  if (!_.isEmpty(selectedFlowStorage.value)) {
    selectedFlow.value = selectedFlowStorage.value;
  }
};

const replayMetadata = (value) => {
  metadata.value = JSON.parse(JSON.stringify(value.uploadedMetadata));
  let flowSelected = `{"name" : "${value.flow}"}`;
  selectedFlow.value = JSON.parse(flowSelected);
};

const clearDeltaFilesSession = () => {
  deltaFilesStorage.value = "";
  deltaFiles.value = [];
};

const uploadsRowClass = (data) => {
  return data.error ? "table-danger" : null;
};

// Created
fetchIngressFlows();

const formatMetadataforViewer = (filename, uploadedMetadata) => {
  let metaDataObject = `{"${filename}" : ${JSON.stringify(uploadedMetadata)}}`;
  return JSON.parse(metaDataObject);
};
</script>

<style lang="scss">
@import "@/styles/pages/deltafile-upload-page.scss";
</style>
