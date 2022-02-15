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
              <Dropdown v-model="selectedFlow" :options="ingressFlows" option-label="name" placeholder="Select an Ingress Flow" :class="{ 'p-invalid': flowSelectInvalid }" />
              <InlineMessage v-if="flowSelectInvalid" class="ml-3"> Flow is required </InlineMessage>
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
        <CollapsiblePanel v-if="deltaFiles.length" header="DeltaFiles" class="table-panel">
          <DataTable responsive-layout="scroll" :value="deltaFiles" striped-rows class="p-datatable-sm p-datatable-gridlines deltafiles" :row-class="uploadsRowClass">
            <Column field="did" header="DID" class="did-column">
              <template #body="file">
                <span v-if="file.data.loading">
                  <ProgressBar :value="file.data.percentComplete" />
                </span>
                <span v-else-if="file.data.error"><i class="fas fa-times" /> Error</span>
                <router-link v-else class="monospace" :to="{ path: '/deltafile/viewer/' + file.data.did }">
                  {{ file.data.did }}
                </router-link>
              </template>
            </Column>
            <Column field="filename" header="Filename" class="filename-column" />
            <Column field="flow" header="Flow" class="flow-column" />
          </DataTable>
        </CollapsiblePanel>
      </div>
    </div>
  </div>
</template>

<script>
import CollapsiblePanel from "@/components/CollapsiblePanel";
import FileUpload from "primevue/fileupload";
import Dropdown from "primevue/dropdown";
import Column from "primevue/column";
import DataTable from "primevue/datatable";
import InputText from "primevue/inputtext";
import Button from "primevue/button";
import Panel from "primevue/panel";
import ProgressBar from "primevue/progressbar";
import InlineMessage from "primevue/inlinemessage";
import useIngress from "@/composables/useIngress";
import useFlows from "@/composables/useFlows";
import { ref, reactive, computed } from "vue";

export default {
  name: "DeltaFileUploadPage",
  components: {
    CollapsiblePanel,
    FileUpload,
    Dropdown,
    DataTable,
    Column,
    InputText,
    Button,
    Panel,
    InlineMessage,
    ProgressBar,
  },
  setup() {
    const selectedFlow = ref(null);
    const flowSelectError = ref(false);
    const metadata = reactive([]);
    const fileUploader = ref();
    const deltaFiles = reactive([]);
    const { ingressFlows, fetchIngressFlows } = useFlows();
    const { ingressFile } = useIngress();

    const metadataRecord = computed(() => {
      let record = {};
      if (metadata.length > 0) {
        for (const field of metadata) {
          if (field.key.length > 0) record[field.key] = field.value;
        }
      }
      return record;
    });

    const flowSelectInvalid = computed(() => {
      return flowSelectError.value && selectedFlow.value == null;
    });

    const metadataClearDisabled = computed(() => {
      return metadata.length == 0 && selectedFlow.value == null;
    });

    const addMetadataField = () => {
      metadata.push({ key: "", value: "" });
    };

    const removeMetadataField = (field) => {
      let index = metadata.indexOf(field);
      metadata.splice(index, 1);
    };

    const clearMetadata = () => {
      flowSelectError.value = false;
      metadata.length = 0;
      selectedFlow.value = null;
    };

    const onUpload = (event) => {
      if (selectedFlow.value == null) {
        flowSelectError.value = true;
      } else {
        ingressFiles(event);
      }
    };

    const ingressFiles = (event) => {
      for (let file of event.files) {
        const result = ingressFile(file, selectedFlow.value.name, metadataRecord.value);
        deltaFiles.push(result);
      }
      fileUploader.value.files = [];
    };

    const uploadsRowClass = (data) => {
      return data.error ? "table-danger" : null;
    };

    // Created
    fetchIngressFlows();

    return {
      selectedFlow,
      flowSelectError,
      metadata,
      deltaFiles,
      metadataRecord,
      flowSelectInvalid,
      metadataClearDisabled,
      addMetadataField,
      removeMetadataField,
      clearMetadata,
      onUpload,
      ingressFiles,
      uploadsRowClass,
      ingressFlows,
      fileUploader,
    };
  },
};
</script>

<style lang="scss">
@import "@/styles/pages/deltafile-upload-page.scss";
</style>