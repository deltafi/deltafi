<template>
  <div class="deltafile-viewer">
    <div class="d-flex justify-content-between flex-wrap flex-md-nowrap align-items-center pt-3 pb-2 mb-3 border-bottom">
      <h1 class="h2">
        {{ pageHeader }}
      </h1>
      <div class="btn-toolbar">
        <Menu id="config_menu" ref="menu" :model="menuItems" :popup="true" />
        <Button v-if="!showForm" class="p-button-secondary p-button-outlined" @click="toggle">
          <span class="fas fa-bars" />
        </Button>
      </div>
    </div>
    <ProgressBar v-if="showProgressBar" mode="indeterminate" style="height: .5em" />
    <div v-else-if="showForm" class="p-float-label">
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
                    <span v-if="field.key === 'Stage'">
                      <ErrorAcknowledgedBadge
                        v-if="deltaFileData.errorAcknowledged"
                        :reason="deltaFileData.errorAcknowledgedReason"
                        :timestamp="deltaFileData.errorAcknowledged"
                      />
                    </span>
                  </dd>
                </dl>
              </div>
            </div>
          </CollapsiblePanel>
        </div>
      </div>
      <div class="row mb-3">
        <div class="col-12">
          <CollapsiblePanel header="Actions" class="actions-panel table-panel">
            <DataTable
              responsive-layout="scroll"
              class="p-datatable-sm p-datatable-gridlines"
              striped-rows
              :value="actions"
              :row-class="actionRowClass"
              @row-click="actionRowClick"
            >
              <Column field="name" header="Action" :sortable="true" />
              <Column field="state" header="State" class="state-column" :sortable="true" />
              <Column field="created" header="Created" class="timestamp-column" :sortable="true" />
              <Column field="modified" header="Modified" class="timestamp-column" :sortable="true" />
              <Column field="elapsed" header="Elapsed" class="elapsed-column" :sortable="true">
                <template #body="action">
                  {{ action.data.elapsed }}
                </template>
              </Column>
              <Column header="Content" class="content-column">
                <template #body="action">
                  <span v-if="contentReferences.hasOwnProperty(action.data.name)">
                    <ContentViewer :content-reference="contentReferences[action.data.name]">
                      <Button icon="far fa-window-maximize" label="View" class="content-button p-button-link" />
                    </ContentViewer>
                  </span>
                </template>
              </Column>
              <Column header="Metadata" class="metadata-column">
                <template #body="action">
                  <span v-if="metadataReferences.hasOwnProperty(action.data.name)">
                    <MetadataViewer :metadata-references="actionMetadata(action.data.name)">
                      <Button icon="fas fa-table" label="View" class="content-button p-button-link" />
                    </MetadataViewer>
                  </span>
                </template>
              </Column>
            </DataTable>
          </CollapsiblePanel>
        </div>
      </div>
      <div class="row mb-3">
        <div class="col-6">
          <CollapsiblePanel header="Domains" class="links-panel pl-0">
            <div v-if="!deltaFileData.domains.length" class="d-flex w-100 justify-content-between no-data-panel-content">
              <strong class="p-2">No Domain Data Available</strong>
            </div>
            <div v-else class="list-group list-group-flush">
              <button v-for="field in deltaFileData.domains" :key="field" class="list-group-item list-group-item-action">
                <DataViewer ref="domainDataView" :viewer-data-reference="field.value" :data-viewer-meta-data="dataViewerMetaData('Domain', field)">
                  <div>
                    <div class="d-flex w-100 justify-content-between">
                      <strong class="mb-0">{{ field.name }}</strong>
                      <i class="far fa-window-maximize" />
                    </div>
                    <small class="mb-1 text-muted">{{ field.mediaType }}</small>
                  </div>
                </DataViewer>
              </button>
            </div>
          </CollapsiblePanel>
        </div>
        <div class="col-6">
          <CollapsiblePanel header="Enrichment" class="links-panel pl-0">
            <div v-if="!deltaFileData.enrichment.length" class="d-flex w-100 justify-content-between no-data-panel-content">
              <strong class="p-2">No Enrichment Data Available</strong>
            </div>
            <div v-else class="list-group list-group-flush">
              <button v-for="field in deltaFileData.enrichment" :key="field" class="list-group-item list-group-item-action">
                <DataViewer ref="enrichmentDataView" :viewer-data-reference="field.value" :data-viewer-meta-data="dataViewerMetaData('Enrichment', field)" viewer-type="Enrichment">
                  <div>
                    <div class="d-flex w-100 justify-content-between">
                      <strong class="mb-0">{{ field.name }}</strong>
                      <i class="far fa-window-maximize" />
                    </div>
                    <small class="mb-1 text-muted">{{ field.mediaType }}</small>
                  </div>
                </DataViewer>
              </button>
            </div>
          </CollapsiblePanel>
        </div>
      </div>
    </div>
    <Dialog v-model:visible="objectDialog.visible" :header="objectDialog.header" :style="{width: '75vw'}" :maximizable="true" :modal="true" :dismissable-mask="true">
      <HighlightedCode :code="objectDialog.body" />
    </Dialog>
    <ErrorViewer v-model:visible="errorViewer.visible" :action="errorViewer.action" />
    <ConfirmDialog />
    <AcknowledgeErrorsDialog v-model:visible="ackErrorsDialog.visible" :dids="[did]" @acknowledged="onAcknowledged" />
    <MetadataViewer ref="allMetadataViewer" :metadata-references="metadataReferences" />
  </div>
</template>

<script>
import InputText from "primevue/inputtext";
import GraphQLService from "@/service/GraphQLService";
import ApiService from "@/service/ApiService";
import { UtilFunctions } from "@/utils/UtilFunctions";
import CollapsiblePanel from "@/components/CollapsiblePanel.vue";
import HighlightedCode from "@/components/HighlightedCode.vue";
import Column from "primevue/column";
import DataTable from "primevue/datatable";
import Button from "primevue/button";
import Dialog from "primevue/dialog";
import ConfirmDialog from 'primevue/confirmdialog';
import * as filesize from "filesize";
import Menu from "primevue/menu";
import { mapState } from "vuex";
import ProgressBar from 'primevue/progressbar';
import ContentViewer from '@/components/ContentViewer.vue';
import MetadataViewer from '@/components/MetadataViewer.vue';
import DataViewer from '@/components/DataViewer.vue';
import ErrorViewer from "@/components/ErrorViewer.vue";
import AcknowledgeErrorsDialog from "@/components/AcknowledgeErrorsDialog.vue";
import { ErrorsActionTypes } from '@/store/modules/errors/action-types';
import ErrorAcknowledgedBadge from "@/components/ErrorAcknowledgedBadge.vue";

const uuidRegex = new RegExp(
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-5][0-9a-f]{3}-[089ab][0-9a-f]{3}-[0-9a-f]{12}$/i
);

export default {
  components: {
    InputText,
    CollapsiblePanel,
    Column,
    DataTable,
    Button,
    Dialog,
    ConfirmDialog,
    Menu,
    ProgressBar,
    HighlightedCode,
    ContentViewer,
    MetadataViewer,
    ErrorViewer,
    AcknowledgeErrorsDialog,
    ErrorAcknowledgedBadge,
    DataViewer
  },
  data() {
    return {
      pageHeader: "DeltaFile Viewer",
      showForm: true,
      showProgressBar: false,
      did: null,
      deltaFileData: {},
      metadata: [],
      ackErrorsDialog: {
        visible: false
      },
      objectDialog: {
        visible: false,
        header: null,
        body: null,
      },
      errorViewer: {
        visible: false,
        action: {},
      },
      menuItems: [
        {
          label: 'Acknowledge',
          icon: 'fas fa-check-circle fa-fw',
          visible: () => this.isError(),
          command: () => {
            this.ackErrorsDialog.visible = true;
          }
        },
        {
          label: 'Retry',
          icon: 'fas fa-redo fa-fw',
          visible: () => this.isError(),
          command: () => {
            this.retryConfirm();
          }
        },
        {
          label: 'View Raw JSON',
          icon: 'fas fa-file-code fa-fw',
          command: () => {
            this.viewDeltaFileJSON();
          }
        },
        {
          label: 'Zipkin Trace',
          icon: 'fas fa-external-link-alt fa-fw',
          command: () => {
            this.openZipkinURL();
          }
        },
        {
          label: 'View All Metadata',
          icon: 'fas fa-database fa-fw',
          visible: () => this.hasMetadata(),
          command: () => {
            this.showMetadataDialog();
          }
        }
      ],
    };
  },
  computed: {
    acknowledgedTooltip() {
      return `${this.deltaFileData.errorAcknowledgedReason}\n\nAcknowledged: ${this.deltaFileData.errorAcknowledged}`
    },
    contentReferences() {
      if (Object.keys(this.deltaFileData).length === 0) return {};

      let layers = this.deltaFileData.protocolStack.concat(this.deltaFileData.formattedData);
      return layers.reduce((content, layer) => {
        let actions = [layer.action, layer.formatAction, layer.egressActions]
          .flat()
          .filter((n) => n);
        for (const action of actions) {
          let filename =
            action === "IngressAction"
              ? this.deltaFileData.sourceInfo.filename
              : layer.filename || `${this.deltaFileData.did}-${layer.action}`;
          content[action] = {
            ...layer.contentReference,
            filename: filename
          }
        }
        return content;
      }, {});
    },
    metadataReferences() {
      if (Object.keys(this.deltaFileData).length === 0) return {};

      let layers = this.deltaFileData.protocolStack.concat(this.deltaFileData.formattedData);
      return layers.reduce((content, layer) => {
        let actions = [layer.action, layer.formatAction]
          .flat()
          .filter((n) => n);
        for (const action of actions) {
          let metadata =
            action === "IngressAction"
              ? this.deltaFileData.sourceInfo.metadata
              : layer.metadata || `${this.deltaFileData.did}-${layer.action}`;
            if( metadata.length > 0 ) {
              content[action] = metadata;
            }
        }
        return content;
      }, {});
    },
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
        const timeElapsed = (new Date(action.modified) - new Date(action.created));
        action.created = new Date(action.created).toISOString();
        action.modified = new Date(action.modified).toISOString();
        return {
          ...action,
          elapsed: this.utilFunctions.duration(timeElapsed)
        }
      });
    },
    formattedDeltaFileData() {
      return JSON.stringify(this.deltaFileData, null, 2);
    },
    ...mapState({
      uiConfig: state => state.uiConfig.uiConfig,
    })
  },
  watch: {
    $route(to) {
      this.clearData();
      if (to.params.did) {
        this.showProgressBar = true;
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
    this.apiService = new ApiService();
  },
  mounted() {
    if (this.$route.params.did) {
      this.did = this.$route.params.did;
      this.showProgressBar = true;
      this.loadDeltaFileData();
    }
  },
  methods: {
    actionMetadata(actionName) {
      return Object.fromEntries(Object.entries(this.metadataReferences).filter(([key]) => key.includes(actionName)));
    },
    onAcknowledged(_, reason) {
      this.ackErrorsDialog.visible = false
      this.$toast.add({
        severity: "success",
        summary: "Successfully Acknowledged Error",
        detail: reason,
        life: 5000,
      });
      this.$store.dispatch(ErrorsActionTypes.FETCH_ERROR_COUNT);
      this.loadDeltaFileData();
    },
    toggle(event) {
      this.$refs.menu.toggle(event);
    },
    formattedBytes(bytes) {
      return filesize(bytes, {base:10})
    },
    openZipkinURL() {
      const zipkinURL = `https://zipkin.${this.uiConfig.domain}/zipkin/traces/${this.did.replaceAll("-", "")}`;
      window.open(zipkinURL, '_blank');
    },
    showMetadataDialog() {
      this.$refs.allMetadataViewer.showDialog();
    },
    isError() {
      return (this.deltaFileData.stage === 'ERROR' ? true : false);
    },
    hasMetadata() {
      return Object.keys(this.metadataReferences).length > 0;
    },
    clearData() {
      this.did = "";
      this.deltaFileData = {};
      this.metadata = [];
      this.pageHeader = "DeltaFile Viewer";
    },
    navigateToDID() {
      if (this.did === null) return;
      else if (this.did === this.$route.params.did) {
        this.showProgressBar = true;
        this.loadDeltaFileData();
      }
      else this.$router.push({ path: `/deltafile/viewer/${this.did}` });
    },
    async loadDeltaFileData() {
      this.showProgressBar = true;
      this.graphQLService.getDeltaFile(this.did).then((res) => {
        if (res.data.deltaFile) {
          this.deltaFileData = res.data.deltaFile;
          let originalFileSize = this.deltaFileData.protocolStack.length > 0 ?
              filesize(this.deltaFileData.protocolStack.find(p => {
                return p.action === 'IngressAction';
              }).contentReference.size, {base:10}) :
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
          this.metadata.push(["Stage", this.deltaFileData.stage]);
          this.metadata.push(["Created", this.deltaFileData.created]);
          this.metadata.push(["Modified", this.deltaFileData.modified]);
          this.showForm = false;
          this.showProgressBar = false;
        } else {
          for (const error of res.errors) {
            console.debug(error.message);
            if (error.extensions.errorType === 'NOT_FOUND') error.message = "DeltaFile not found"
            this.$toast.add({
              severity: "error",
              summary: "Error Loading DeltaFile",
              detail: error.message,
              life: 5000,
            });
          }
          this.showProgressBar = false;
          this.showForm = true;
        }
      });
    },
    viewDeltaFileJSON() {
      this.showObjectDialog("DeltaFile JSON", JSON.stringify(this.deltaFileData, null, 2));
    },
    showObjectDialog(header, body) {
      if (header !== undefined && body !== undefined) {
        this.objectDialog.header = header
        this.objectDialog.body = (typeof body === 'string') ? body : JSON.stringify(body, null, 2)
        this.objectDialog.visible = true;
      }
    },
    actionRowClick(event) {
      let action = event.data;
      if (!['ERROR', 'RETRIED'].includes(action.state)) return

      this.errorViewer = {
        visible: true,
        action: action,
      }
    },
    actionRowClass(action) {
      if (action.state === 'ERROR') return 'table-danger action-error';
      if (action.state === 'RETRIED') return 'table-warning action-error';
    },
    retryConfirm() {
      this.$confirm.require({
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
    dataViewerMetaData(viewerType, field) {
      var dataViewerMetaData = {
        did: this.deltaFileData.did,
        filename: this.deltaFileData.sourceInfo.filename,
        name: field.name,
        mediaType: field.mediaType,
        viewerType: viewerType
      }
      return dataViewerMetaData;
    },
  },
  graphQLService: null,
  ApiService: null,
  utilFunctions: null
};
</script>

<style lang="scss">
@import "@/styles/pages/deltafile-viewer-page.scss";
</style>
