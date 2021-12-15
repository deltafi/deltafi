<template>
  <div class="DeltaFileView">
    <div class="d-flex justify-content-between flex-wrap flex-md-nowrap align-items-center pt-3 pb-2 mb-3 border-bottom">
      <h1 class="h2">
        {{ pageHeader }}
      </h1>
      <div class="btn-toolbar">
        <button v-show="!showForm && hasErrors" class="btn btn-sm btn-outline-secondary" @click="retryConfirm($event)">
          Retry
        </button>
        <button v-show="!showForm" class="btn btn-sm btn-outline-secondary ml-2" @click="viewDeltaFileJSON()">
          View Raw JSON
        </button>
      </div>
    </div>
    <Toast position="bottom-right" />
    <div v-if="showForm" class="p-float-label">
      <div class="row">
        <div class="col-4">
          <div class="p-inputgroup">
            <InputText v-model="did" placeholder="DID (UUID)" :class="{'p-invalid': did && !validDID}" @keyup.enter="navigateToDID" />
            <Button class="p-button-primary" :disabled="(!did || !validDID)" @click="navigateToDID">
              View
            </Button>
          </div>
          <small v-if="did && !validDID" class="p-error ml-1">Invalid UUID</small>
        </div>
      </div>
    </div>
    <div v-else>
      <div class="row mb-3">
        <div class="col-12">
          <CollapsiblePanel header="Metadata">
            <div class="row">
              <div v-for="field in metadataFields" :key="field" class="col-12 col-md-6 col-xl-3">
                <dl>
                  <dt>{{ field.key }}</dt>
                  <dd :class="{monospace: field.key === 'DID'}">
                    {{ field.value }}
                  </dd>
                </dl>
              </div>
            </div>
          </CollapsiblePanel>
        </div>
      </div>
      <div class="row mb-3">
        <div class="col-12">
          <CollapsiblePanel header="Actions" class="actions-panel">
            <DataTable :value="actions" responsive-layout="scroll" striped-rows class="p-datatable-sm" :row-class="actionRowClass" @row-click="actionRowClick">
              <Column field="name" header="Action" :sortable="true" />
              <Column field="state" header="State" :sortable="true">
                <template #body="action">
                  {{ action.data.state }}<span v-if="action.data.state === 'ERROR'">: {{ action.data.errorCause }}</span>
                </template>
              </Column>
              <Column field="created" header="Created" :sortable="true" />
              <Column field="modified" header="Modified" :sortable="true" />
              <Column field="elapsed" header="Elapsed" :sortable="true">
                <template #body="action">
                  {{ action.data.elapsed }}
                </template>
              </Column>
            </DataTable>
          </CollapsiblePanel>
        </div>
      </div>
    </div>
    <Dialog v-model:visible="showJSONDialog" header="DeltaFile JSON" :style="{width: '75vw'}" :maximizable="true" :modal="true">
      <pre class="dark">{{ deltaFileData }}</pre>
    </Dialog>
    <Dialog v-model:visible="errorDialog.visible" :header="errorDialog.action" :style="{width: '75vw'}" :maximizable="true" :modal="true">
      <strong>Error Cause</strong>
      <pre class="dark">{{ errorDialog.cause }}</pre>
      <strong>Error Context</strong>
      <pre class="dark">{{ errorDialog.context }}</pre>
    </Dialog>
    <ConfirmPopup />
  </div>
</template>

<script>
import InputText from "primevue/inputtext";
import GraphQLService from "@/service/GraphQLService";
import { UtilFunctions } from "@/utils/UtilFunctions";
import CollapsiblePanel from "@/components/CollapsiblePanel.vue";
import Column from "primevue/column";
import DataTable from "primevue/datatable";
import Toast from "primevue/toast";
import Button from "primevue/button";
import Dialog from "primevue/dialog";
import ConfirmPopup from 'primevue/confirmpopup';
import * as filesize from "filesize";

const uuidRegex = new RegExp(
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-5][0-9a-f]{3}-[089ab][0-9a-f]{3}-[0-9a-f]{12}$/i
);

export default {
  components: {
    InputText,
    CollapsiblePanel,
    Column,
    DataTable,
    Toast,
    Button,
    Dialog,
    ConfirmPopup,
  },
  data() {
    return {
      pageHeader: "DeltaFile Viewer",
      showForm: true,
      did: null,
      deltaFileData: {},
      metadata: [],
      showJSONDialog: false,
      errorDialog: {
        visible: false,
        action: null,
        cause: null,
        context: null,
      }
    };
  },
  computed: {
    metadataFields() {
      return this.metadata.map((field) => {
        return { key: field[0], value: field[1] };
      });
    },
    validDID() {
      return uuidRegex.test(this.did);
    },
    hasErrors() {
      return this.deltaFileData.stage === 'ERROR';
    },
    actions() {
      return this.deltaFileData.actions.map(action => {
        var timeElapsed = new Date(action.modified) - new Date(action.created)
        let seconds = (timeElapsed / 1000).toFixed(1);
        let minutes = (timeElapsed / (1000 * 60)).toFixed(1);
        let hours = (timeElapsed / (1000 * 60 * 60)).toFixed(1);
        let days = (timeElapsed / (1000 * 60 * 60 * 24)).toFixed(1);
        if (seconds < 1 ) {
          timeElapsed = timeElapsed + "ms";
        } else if (seconds < 60) {
          timeElapsed = seconds + "s";
        } else if (minutes < 60) {
          timeElapsed = minutes + "m";
        } else if (hours < 24) {
          timeElapsed = hours + "h";
        } else {
          timeElapsed = days + "d";
        }

        action.elapsed = timeElapsed;
        return action;
      });
    }
  },
  watch: {
    $route(to) {
      this.clearData();
      if (to.params.did) {
        this.did = to.params.did;
        this.loadDeltaFileData();
      } else {
        this.showForm = true;
      }
    },
  },
  created() {
    this.graphQLService = new GraphQLService();
    this.utilFunctions = new UtilFunctions();
  },
  mounted() {
    if (this.$route.params.did) {
      this.did = this.$route.params.did;
      this.loadDeltaFileData();
    }
  },
  methods: {
    clearData() {
      this.did = "";
      this.deltaFileData = {};
      this.metadata = [];
      this.pageHeader = "DeltaFile Viewer";
    },
    navigateToDID() {
      if (this.did === null) return;
      else if (this.did === this.$route.params.did) this.loadDeltaFileData();
      else this.$router.push({ path: `/deltafile/viewer/${this.did}` });
    },
    async loadDeltaFileData() {
      this.graphQLService.getDeltaFile(this.did).then((res) => {
        if (res.data.deltaFile) {
          this.deltaFileData = res.data.deltaFile;
          let domain = this.deltaFileData.domains.length > 0 ?
              this.deltaFileData.domains[0].key :
              "N/A"
          let originalFileSize = this.deltaFileData.protocolStack.length > 0 ?
              filesize(this.deltaFileData.protocolStack[0].objectReference.size, {base:10}) :
              "N/A";
          this.pageHeader = `DeltaFile Viewer: ${this.deltaFileData.sourceInfo.filename}`;
          this.metadata = [];
          this.metadata.push(["DID", this.deltaFileData.did]);
          this.metadata.push([
            "Original Filename",
            this.deltaFileData.sourceInfo.filename,
          ]);
          this.metadata.push(["Original File Size", originalFileSize]);
          this.metadata.push(["Flow", this.deltaFileData.sourceInfo.flow]);
          this.metadata.push(["Domain", domain]);
          this.metadata.push(["Stage", this.deltaFileData.stage]);
          this.metadata.push(["Created", this.deltaFileData.created]);
          this.metadata.push(["Modified", this.deltaFileData.modified]);
          this.showForm = false;
        } else {
          console.debug(res.errors);
          this.$toast.add({
            severity: "error",
            summary: "DeltaFile Not Found",
            detail: "Please check the DID and try again",
            life: 5000,
          });
          this.showForm = true;
        }
      });
    },
    viewDeltaFileJSON() {
      this.showJSONDialog = true;
    },
    actionRowClick(event) {
      let action = event.data;
      if (action.state !== "ERROR") return;

      this.errorDialog = {
        visible: true,
        action: action.name,
        cause: action.errorCause,
        context: this.utilFunctions.formatContextData(action.errorContext),
      }
    },
    actionRowClass(data) {
      return data.state === 'ERROR' ? 'table-danger action-error': null;
    },
    retryConfirm(event) {
      this.$confirm.require({
        target: event.currentTarget,
        message: "Are you sure you want to retry this DeltaFile?",
        accept: () => {
          this.retry();
        },
      });
    },
    retry() {
      this.graphQLService.postErrorRetry(this.did).then((res) => {
        if (res.data) {
          this.$toast.add({
            severity: "success",
            summary: "Retry request sent successfully",
            detail: this.did,
            life: 3000,
          });
          this.loadDeltaFileData();
        } else {
          this.$toast.add({
            severity: "error",
            summary: "Retry request failed",
            detail: res.errors[0].message,
            life: 10000,
          });
        }
      });
    },
  },
  graphQLService: null,
  utilFunctions: null,
};
</script>

<style lang="scss">
@import "@/styles/pages/deltafile-viewer-page.scss";
</style>