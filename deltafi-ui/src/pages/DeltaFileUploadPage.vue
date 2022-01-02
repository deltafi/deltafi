<template>
  <div class="upload">
    <div class="d-flex justify-content-between flex-wrap flex-md-nowrap align-items-center pt-3 pb-2 mb-3 border-bottom">
      <h1 class="h2">
        Upload Files
      </h1>
    </div>

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
              <Dropdown v-model="selectedFlow" :options="flows" option-label="name" placeholder="Select an Ingress Flow" :class="{'p-invalid': flowSelectInvalid}" />
              <InlineMessage v-if="flowSelectInvalid" class="ml-3">
                Flow is required
              </InlineMessage>
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
        <FileUpload ref="fileUpload" :multiple="true" choose-label="Add Files" cancel-label="Clear" :custom-upload="true" @uploader="onUpload" @select="onSelect">
          <template #empty>
            <i class="ml-3">Drag and drop files to here to upload.</i>
          </template>
        </FileUpload>
      </div>
    </div>

    <div class="mb-3 row">
      <div class="col-12">
        <CollapsiblePanel v-if="deltaFiles.length" header="DeltaFiles">
          <DataTable :value="deltaFiles" striped-rows class="p-datatable-sm" :row-class="uploadsRowClass">
            <Column field="did" header="DID">
              <template #body="file">
                <span v-if="file.data.loading"><i class="fas fa-spin fa-circle-notch" /> Loading...</span>
                <span v-else-if="file.data.error"><i class="fas fa-times" /> Error</span>
                <router-link v-else class="monospace" :to="{path: '/deltafile/viewer/' + file.data.did}">
                  {{ file.data.did }}
                </router-link>
              </template>
            </Column>
            <Column field="filename" header="Filename" />
            <Column field="flow" header="Flow" />
          </DataTable>
        </CollapsiblePanel>
      </div>
    </div>
    <Toast position="bottom-right" />
  </div>
</template>

<script>
import CollapsiblePanel from "@/components/CollapsiblePanel";
import FileUpload from "primevue/fileupload";
import Dropdown from "primevue/dropdown";
import Column from "primevue/column";
import DataTable from "primevue/datatable";
import Toast from "primevue/toast";
import InputText from "primevue/inputtext";
import Button from "primevue/button";
import Panel from "primevue/panel";
import axios from "axios";
import InlineMessage from "primevue/inlinemessage";
import GraphQLService from "@/service/GraphQLService";

export default {
  name: "DeltaFileUploadPage",
  components: {
    CollapsiblePanel,
    FileUpload,
    Dropdown,
    DataTable,
    Column,
    Toast,
    InputText,
    Button,
    Panel,
    InlineMessage,
  },
  data() {
    return {
      flows: [],
      selectedFlow: null,
      flowSelectError: false,
      metadata: [],
      deltaFiles: [],
    };
  },
  computed: {
    metadataJson() {
      let metadata = {};
      if (this.metadata.length > 0) {
        for (const field of this.metadata) {
          if (field.key.length > 0) metadata[field.key] = field.value;
        }
      }
      return JSON.stringify(metadata);
    },
    flowSelectInvalid() {
      return this.flowSelectError && this.selectedFlow == null;
    },
    metadataClearDisabled() {
      return this.metadata.length == 0 && this.selectedFlow == null;
    },
  },
  created() {
    this.graphQLService = new GraphQLService();
    this.fetchIngressFlows();
  },
  methods: {
    async fetchIngressFlows() {
      const ingressFlowData = await this.graphQLService.getConfigByType(
        "INGRESS_FLOW"
      );
      this.flows = ingressFlowData.data.deltaFiConfigs;
    },
    addMetadataField() {
      this.metadata.push({ key: "", value: "" });
    },
    removeMetadataField(field) {
      let index = this.metadata.indexOf(field);
      this.metadata.splice(index, 1);
    },
    clearMetadata() {
      this.flowSelectError = false;
      this.metadata = [];
      this.selectedFlow = null;
    },
    onUpload(event) {
      if (this.selectedFlow == null) {
        this.flowSelectError = true;
      } else {
        this.ingressFiles(event);
      }
    },
    ingressFiles(event) {
      for (let file of event.files) {
        file.flow = this.selectedFlow.name;
        file.metadata = this.metadataJson;
        file.index = this.deltaFiles.length;
        this.deltaFiles[file.index] = {
          loading: true,
          error: false,
          filename: file.name,
          flow: file.flow,
        };

        axios
          .request({
            method: "post",
            url: "/deltafile/ingress",
            data: file,
            headers: {
              Flow: file.flow,
              Filename: file.name,
              Metadata: file.metadata,
            },
            onUploadProgress: (progressEvent) => {
              file.percentCompleted = Math.round(
                (progressEvent.loaded * 100) / progressEvent.total
              );
              this.updateProgress();
              if (file.percentCompleted == 100) this.removeFile(file);
            },
          })
          .then((res) => {
            this.deltaFiles[file.index].did = res.data;
            this.deltaFiles[file.index].loading = false;
            this.$toast.add({
              severity: "success",
              summary: "Ingress successful",
              detail: file.name,
              life: 3000,
            });
          })
          .catch((error) => {
            this.deltaFiles[file.index].loading = false;
            this.deltaFiles[file.index].error = true;
            console.error(error);
            this.$toast.add({
              severity: "error",
              summary: `Failed to ingress ${file.name}`,
              detail: error,
              life: 5000,
            });
          });
      }
    },
    updateProgress() {
      let uploader = this.$refs.fileUpload;
      let total = uploader.files.reduce(
        (total, file) => total + (file.percentCompleted || 0),
        0
      );
      uploader.progress = total / uploader.files.length;
    },
    removeFile(file) {
      let index = this.$refs.fileUpload.files.indexOf(file);
      this.$refs.fileUpload.files.splice(index, 1);
    },
    uploadsRowClass(data) {
      return data.error ? 'table-danger': null;
    },
    onSelect() {
      this.$refs.fileUpload.progress = 0;
    }
  },
  graphQLService: null,
};
</script>

<style lang="scss">
@import "@/styles/pages/deltafile-upload-page.scss";
</style>