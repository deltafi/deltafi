<template>
  <div class="errors">
    <div class="d-flex justify-content-between flex-wrap flex-md-nowrap align-items-center pt-3 pb-2 mb-3 border-bottom">
      <h1 class="h2">
        Errors
      </h1>
      <div class="time-range btn-toolbar mb-2 mb-md-0">
        <Dropdown
          v-model="ingressFlowNameSelected"
          placeholder="Select a Flow"
          :options="ingressFlowNames"
          option-label="name"
          show-clear
          :editable="false"
          class="deltafi-input-field"
        />
        <Calendar
          id="startDateTime"
          v-model="startTimeDate"
          selection-mode="single"
          :inline="false"
          :show-time="true"
          :manual-input="false"
          hour-format="12"
          input-class="deltafi-input-field ml-3"
        />
        <span class="mt-2 ml-3">&mdash;</span>
        <Calendar
          id="endDateTime"
          v-model="endTimeDate"
          selection-mode="single"
          :inline="false"
          :show-time="true"
          :manual-input="false"
          hour-format="12"
          input-class="deltafi-input-field ml-3"
        />
        <Button class="p-button-sm p-button-secondary p-button-outlined ml-3" @click="fetchErrors(startTimeDate, endTimeDate, offset, perPage, sortField, sortDirection, ingressFlowNameSelected)">
          Search
        </Button>
      </div>
    </div>
    <ConfirmDialog />
    <Panel header="DeltaFiles with Errors" @contextmenu="onPanelRightClick">
      <ContextMenu ref="menu" :model="items" />
      <template #icons>
        <Button class="p-panel-header-icon p-link p-mr-2" @click="toggle">
          <span class="fas fa-bars" />
        </Button>
        <Menu ref="menu" :model="items" :popup="true" />
      </template>
      <DataTable
        id="errorsTable"
        v-model:expandedRows="expandedRows"
        v-model:selection="selectedErrors"
        selection-mode="multiple"
        data-key="did"
        :meta-key-selection="false"
        :value="errors"
        striped-rows
        class="p-datatable-gridlines p-datatable-sm"
        :loading="loading"
        :paginator="totalErrors > 0"
        :rows="20"
        paginator-template="CurrentPageReport FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown"
        current-page-report-template="Showing {first} to {last} of {totalRecords} DeltaFiles"
        :rows-per-page-options="[10,20,50,100]"
        :lazy="true"
        :total-records="totalErrors"
        :always-show-paginator="true"
        :row-hover="true"
        @page="onPage($event)"
        @sort="onSort($event)"
      >
        <template #empty>
          No DeltaFiles with Errors in the selected time range.
        </template>
        <template #loading>
          Loading DeltaFiles with Errors. Please wait.
        </template>
        <Column class="expander-column" :expander="true" />
        <Column field="did" header="DID">
          <template #body="error">
            <a class="monospace" :href="`/deltafile/viewer/${error.data.did}`">{{ error.data.did }}</a>
          </template>
        </Column>
        <Column field="sourceInfo.filename" header="Filename" :sortable="true" />
        <Column field="sourceInfo.flow" header="Flow" :sortable="true" />
        <Column field="created" header="Created" :sortable="true" />
        <Column field="modified" header="Modified" :sortable="true" />
        <Column field="last_error_cause" header="Last Error">
          <template #body="error">
            {{ latestError(error.data.actions).errorCause }}
          </template>
        </Column>
        <Column field="actions.length" header="Errors">
          <template #body="error">
            {{ countErrors(error.data.actions) }}
          </template>
        </Column>
        <template #expansion="error">
          <div class="errors-Subtable">
            <DataTable :value="error.data.actions" :row-hover="false">
              <Column field="name" header="Action" />
              <Column field="state" header="State" />
              <Column field="created" header="Created" />
              <Column field="modified" header="Modified" />
              <Column field="errorCause" header="Cause">
                <template #body="action">
                  <span v-if="(action.data.state === 'ERROR') && (action.data.errorCause !== null)">{{ action.data.errorCause }}</span>
                  <span v-else>N/A</span>
                </template>
              </Column>
              <Column field="errorContext" header="Context">
                <template #body="action">
                  <div v-if="action.data.errorContext">
                    <Button label="Show Context" icon="pi pi-external-link" class="p-button-sm p-button-raised p-button-secondary" @click="openContextDialog(action.data.errorContext)" />
                  </div>
                  <div v-else>
                    <span>No context provided</span>
                  </div>
                </template>
              </Column>
            </DataTable>
          </div>
        </template>
      </DataTable>
    </Panel>
    <Dialog v-model:visible="showContextDialog" header="Error Context" :style="{width: '75vw'}" :maximizable="true" :modal="true">
      <HighlightedCode :highlight="false" :code="contextDialogData" />
    </Dialog>
    <Toast position="bottom-right" />
  </div>
</template>

<script>
import GraphQLService from "@/service/GraphQLService";
import { UtilFunctions } from "@/utils/UtilFunctions";
import Toast from "primevue/toast";
import Column from "primevue/column";
import DataTable from "primevue/datatable";
import Dialog from "primevue/dialog";
import Calendar from "primevue/calendar";
import Dropdown from "primevue/dropdown";
import Button from "primevue/button";
import Panel from "primevue/panel";
import Menu from "primevue/menu";
import ConfirmDialog from "primevue/confirmdialog";
import ContextMenu from "primevue/contextmenu";
import HighlightedCode from "@/components/HighlightedCode.vue";

const maxRetrySuccessDisplay = 10;

var currentDateObj = new Date();
var numberOfMlSeconds = currentDateObj.getTime();
var addMlSeconds = 60 * 60 * 1000;
var newDateObj = new Date(numberOfMlSeconds - addMlSeconds);
currentDateObj = new Date(numberOfMlSeconds + addMlSeconds);

export default {
  name: "ErrorsPage",
  components: {
    Toast,
    Column,
    DataTable,
    Dialog,
    Calendar,
    Dropdown,
    Button,
    Panel,
    Menu,
    ConfirmDialog,
    ContextMenu,
    HighlightedCode
  },
  data() {
    return {
      errors: [],
      ingressFlowNameSelected: null,
      ingressFlowNames: [],
      expandedRows: [],
      startTimeDate: newDateObj,
      endTimeDate: currentDateObj,
      showContextDialog: false,
      loading: true,
      contextDialogData: "",
      totalErrors: 0,
      offset: 0,
      perPage: 20,
      sortField: "modified",
      sortDirection: "DESC",
      selectedErrors: [],
      items: [
        {
          label: "Clear Selected",
          icon: "fas fa-times fa-fw",
          command: () => {
            this.selectedErrors = [];
          },
        },
        {
          label: "Select All Visible",
          icon: "fas fa-check-double fa-fw",
          command: () => {
            this.selectedErrors = this.errors;
          },
        },
        {
          label: "Retry Selected",
          icon: "fas fa-redo fa-fw",
          command: () => {
            this.RetryClickConfirm();
          },
        },
      ],
    };
  },
  created() {
    this.graphQLService = new GraphQLService();
    this.utilFunctions = new UtilFunctions();
    this.fetchIngressFlows();
    this.fetchErrors(
      this.startTimeDate,
      this.endTimeDate,
      this.offset,
      this.perPage,
      this.sortField,
      this.sortDirection,
      this.ingressFlowNameSelected
    );
  },
  methods: {
    toggle(event) {
      this.$refs.menu.toggle(event);
    },
    onPanelRightClick(event) {
      this.$refs.menu.show(event);
    },
    RetryClickConfirm() {
      if (this.selectedErrors.length == 0) {
        this.$toast.add({
          severity: "warn",
          summary: `No DeltaFiles selected`,
          life: 3000,
        });
        return;
      }
      let dids = this.selectedErrors.map(selectedError => { return selectedError.did });
      let noun = this.plurality("DeltaFile", dids)
      this.$confirm.require({
        message: `Are you sure you want to retry ${dids.length} selected ${noun}?`,
        accept: () => {
          this.RetryClickAction(dids);
        },
      });
    },
    RetryClickAction(dids) {
      this.loading = true;
      this.graphQLService.postErrorRetry(dids)
        .then((res) => {
          this.loading = false;
          if (res.data !== undefined && res.data !== null) {
            let successRetry = new Array();
            for(const retryStatus of res.data.retry) {
              if(retryStatus.success) {
                successRetry.push(retryStatus);
              } else {
                this.$toast.add({
                  severity: "error",
                  summary: `Retry request failed for ${retryStatus.did}`,
                  detail: retryStatus.error,
                  life: 10000,
                });
              }
            }
            if(successRetry.length > 0) {
              let successfulDids = successRetry.map(retryStatus => { return retryStatus.did })
              if (successfulDids.length > maxRetrySuccessDisplay) {
                successfulDids = successfulDids.slice(0, maxRetrySuccessDisplay)
                successfulDids.push("...")
              }
              let noun = this.plurality("DeltaFile", successRetry)
              this.$toast.add({
                severity: "success",
                summary: `Retry request sent successfully for ${successRetry.length} ${noun}`,
                detail: successfulDids.join(", "),
                life: 5000,
              });
            }
            this.fetchErrors(
              this.startTimeDate,
              this.endTimeDate,
              this.offset,
              this.perPage,
              this.sortField,
              this.sortDirection,
              this.ingressFlowNameSelected
            );
            this.selectedErrors = [];
          } else {
            throw res.errors[0];
          }
        })
        .catch(error => {
          this.loading = false;
          console.error(error);
          this.$toast.add({
            severity: "error",
            summary: "Retry request failed",
            detail: error,
            life: 10000,
          });
        });
    },
    async fetchIngressFlows() {
      const ingressFlowData = await this.graphQLService.getConfigByType(
        "INGRESS_FLOW"
      );
      this.ingressFlowNames = ingressFlowData.data.deltaFiConfigs;
    },
    async fetchErrors(
      startD,
      endD,
      offset,
      perPage,
      sortField,
      sortDirection,
      ingressFlowName
    ) {
      if (ingressFlowName != null) {
        ingressFlowName = ingressFlowName.name;
      }
      this.loading = true;
      const data = await this.graphQLService.getErrors(
        startD,
        endD,
        offset,
        perPage,
        sortField,
        sortDirection,
        ingressFlowName
      );
      this.errors = data.data.deltaFiles.deltaFiles;
      this.totalErrors = data.data.deltaFiles.totalCount;
      this.loading = false;
    },
    UpdateErrors(startD, endD) {
      alert(startD + endD);
    },
    openContextDialog(contextData) {
      this.contextDialogData = this.utilFunctions.formatContextData(contextData);
      this.showContextDialog = true;
    },
    closeContextDialog() {
      this.showContextDialog = false;
      this.contextDialogData = "";
    },
    filterErrors(actions) {
      return actions.filter((action) => {
        return action.state === "ERROR";
      });
    },
    latestError(actions) {
      return this.filterErrors(actions).sort((a, b) =>
        a.modified < b.modified ? 1 : -1
      )[0];
    },
    countErrors(actions) {
      return this.filterErrors(actions).length;
    },
    onPage(event) {
      this.offset = event.first;
      this.perPage = event.rows;
      this.fetchErrors(
        this.startTimeDate,
        this.endTimeDate,
        this.offset,
        this.perPage,
        this.sortField,
        this.sortDirection,
        this.ingressFlowNameSelected
      );
    },
    onSort(event) {
      this.offset = event.first;
      this.perPage = event.rows;
      this.sortField = event.sortField;
      this.sortDirection = event.sortOrder > 0 ? "DESC" : "ASC";
      this.fetchErrors(
        this.startTimeDate,
        this.endTimeDate,
        this.offset,
        this.perPage,
        this.sortField,
        this.sortDirection,
        this.ingressFlowNameSelected
      );
    },
    plurality(word, array) {
      let s = array.length > 1 ? "s" : "";
      return `${word}${s}`;
    }
  },
  graphQLService: null,
  utilFunctions: null,
};
</script>

<style lang="scss">
@import "@/styles/pages/errors-page.scss";
</style>