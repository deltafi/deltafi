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
          class="deltafi-input-field ml-3"
          @change="fetchErrors()"
        />
        <Button
          v-model="showAcknowledged"
          :icon="showAcknowledged ? 'fas fa-eye-slash' : 'fas fa-eye'"
          :label="showAcknowledged ? 'Hide Acknowledged' : 'Show Acknowledged'"
          class="p-button p-button-secondary p-button-outlined deltafi-input-field show-acknowledged-toggle ml-3"
          @click="toggleShowAcknowledged()"
        />
        <Button
          :icon="refreshButtonIcon"
          label="Refresh"
          class="p-button p-button-secondary p-button-outlined deltafi-input-field ml-3"
          @click="fetchErrors()"
        />
      </div>
    </div>
    <ConfirmDialog />
    <Panel header="DeltaFiles with Errors" @contextmenu="onPanelRightClick">
      <ContextMenu ref="menu" :model="menuItems" />
      <template #icons>
        <Button class="p-panel-header-icon p-link p-mr-2" @click="toggleMenu">
          <span class="fas fa-bars" />
        </Button>
        <Menu ref="menu" :model="menuItems" :popup="true" />
      </template>
      <DataTable
        id="errorsTable"
        v-model:expandedRows="expandedRows"
        v-model:selection="selectedErrors"
        responsive-layout="scroll"
        selection-mode="multiple"
        data-key="did"
        paginator-template="CurrentPageReport FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown"
        current-page-report-template="Showing {first} to {last} of {totalRecords} DeltaFiles"
        class="p-datatable-gridlines p-datatable-sm"
        striped-rows
        :meta-key-selection="false"
        :value="errors"
        :loading="loading"
        :paginator="totalErrors > 0"
        :rows="10"
        :rows-per-page-options="[10,20,50,100]"
        :lazy="true"
        :total-records="totalErrors"
        :always-show-paginator="true"
        :row-hover="true"
        @page="onPage($event)"
        @sort="onSort($event)"
      >
        <template #empty>
          No DeltaFiles with Errors to display.
        </template>
        <template #loading>
          Loading DeltaFiles with Errors. Please wait.
        </template>
        <Column class="expander-column" :expander="true" />
        <Column field="did" header="DID">
          <template #body="error">
            <a class="monospace" :href="`/deltafile/viewer/${error.data.did}`">{{ error.data.did }}</a>
            <ErrorAcknowledgedBadge
              v-if="error.data.errorAcknowledged"
              :reason="error.data.errorAcknowledgedReason"
              :timestamp="error.data.errorAcknowledged"
              class="ml-2"
            />
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
            <DataTable responsive-layout="scroll" :value="error.data.actions" :row-hover="false" striped-rows class="p-datatable-sm p-datatable-gridlines" :row-class="actionRowClass" @row-click="actionRowClick">
              <Column field="name" header="Action" />
              <Column field="state" header="State" />
              <Column field="created" header="Created" />
              <Column field="modified" header="Modified" />
              <Column field="errorCause" header="Error Cause">
                <template #body="action">
                  <span v-if="(['ERROR', 'RETRIED'].includes(action.data.state)) && (action.data.errorCause !== null)">
                    {{ action.data.errorCause }}
                  </span>
                  <span v-else>N/A</span>
                </template>
              </Column>
            </DataTable>
          </div>
        </template>
      </DataTable>
    </Panel>
    <ErrorViewer v-model:visible="errorViewer.visible" :action="errorViewer.action" />
    <AcknowledgeErrorsDialog v-model:visible="ackErrorsDialog.visible" :dids="ackErrorsDialog.dids" @acknowledged="onAcknowledged" />
  </div>
</template>

<script>
import GraphQLService from "@/service/GraphQLService";
import { UtilFunctions } from "@/utils/UtilFunctions";
import Column from "primevue/column";
import DataTable from "primevue/datatable";
import Dropdown from "primevue/dropdown";
import Button from "primevue/button";
import Panel from "primevue/panel";
import Menu from "primevue/menu";
import ConfirmDialog from "primevue/confirmdialog";
import ContextMenu from "primevue/contextmenu";
import ErrorViewer from "@/components/ErrorViewer.vue";
import AcknowledgeErrorsDialog from "@/components/AcknowledgeErrorsDialog.vue";
import { ErrorsActionTypes } from '@/store/modules/errors/action-types';
import ErrorAcknowledgedBadge from "@/components/ErrorAcknowledgedBadge.vue";
import _ from 'lodash';

const maxRetrySuccessDisplay = 10;
const refreshInterval = 5000; // 5 seconds

export default {
  name: "ErrorsPage",
  components: {
    Column,
    DataTable,
    Dropdown,
    Button,
    Panel,
    Menu,
    ConfirmDialog,
    ContextMenu,
    ErrorViewer,
    AcknowledgeErrorsDialog,
    ErrorAcknowledgedBadge,
  },
  data() {
    return {
      errors: [],
      lastServerContact: null,
      showAcknowledged: false,
      ingressFlowNameSelected: null,
      ingressFlowNames: [],
      expandedRows: [],
      showContextDialog: false,
      loading: true,
      contextDialogData: "",
      totalErrors: 0,
      offset: 0,
      perPage: 10,
      sortField: "modified",
      sortDirection: "DESC",
      selectedErrors: [],
      ackErrorsDialog: {
        dids: [],
        visible: false
      },
      errorViewer: {
        visible: false,
        action: {}
      },
      menuItems: [
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
          separator:true
        },
        {
          label: "Acknowledge Selected",
          icon: "fas fa-check-circle fa-fw",
          command: () => {
            this.acknowledgeClickConfirm();
          },
          disabled: () => {
            return this.selectedErrors.length == 0;
          }
        },
        {
          label: "Retry Selected",
          icon: "fas fa-redo fa-fw",
          command: () => {
            this.RetryClickConfirm();
          },
          disabled: () => {
            return this.selectedErrors.length == 0;
          }
        },
      ],
    };
  },
  computed: {
    refreshButtonIcon() {
      let classes = ["fa", "fa-sync-alt"];
      if (this.loading) classes.push("fa-spin");
      return classes.join(' ');
    }
  },
  watch: {
    $route() {
      // Clear the auto refresh when the route changes.
      clearInterval(this.autoRefresh);
    },
  },
  created() {
    this.graphQLService = new GraphQLService();
    this.utilFunctions = new UtilFunctions();
    this.fetchIngressFlows();
    this.fetchErrors();
  },
  mounted() {
    this.autoRefresh = setInterval(
      function () {
        this.pollNewErrors();
      }.bind(this),
      refreshInterval
    );
  },
  methods: {
    toggleMenu(event) {
      this.$refs.menu.toggle(event);
    },
    toggleShowAcknowledged() {
      this.showAcknowledged = !this.showAcknowledged
      this.selectedErrors = [];
      this.fetchErrors();
    },
    onPanelRightClick(event) {
      this.$refs.menu.show(event);
    },
    acknowledgeClickConfirm() {
      this.ackErrorsDialog.dids = this.selectedErrors.map(selectedError => { return selectedError.did });
      this.ackErrorsDialog.visible = true
    },
    onAcknowledged(dids, reason) {
      this.selectedErrors = []
      this.ackErrorsDialog.dids = [];
      this.ackErrorsDialog.visible = false
      let pluralized = this.utilFunctions.pluralize(dids.length, "Error")
      this.$toast.add({
        severity: "success",
        summary: `Successfully acknowledged ${pluralized}`,
        detail: reason,
        life: 5000,
      });
      this.$store.dispatch(ErrorsActionTypes.FETCH_ERROR_COUNT);
      this.fetchErrors();
    },
    RetryClickConfirm() {
      let dids = this.selectedErrors.map(selectedError => { return selectedError.did });
      let pluralized = this.utilFunctions.pluralize(dids.length, "DeltaFile")
      this.$confirm.require({
        message: `Are you sure you want to retry ${pluralized}?`,
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
              let pluralized = this.utilFunctions.pluralize(successRetry.length, "DeltaFile")
              this.$toast.add({
                severity: "success",
                summary: `Retry request sent successfully for ${pluralized}`,
                detail: successfulDids.join(", "),
                life: 5000,
              });
            }
            this.removeSelected();
            this.selectedErrors = [];
            this.$store.dispatch(ErrorsActionTypes.FETCH_ERROR_COUNT);
          } else {
            throw res.errors[0];
          }
        })
        .catch(error => {
          this.loading = false;
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
      this.ingressFlowNames = _.sortBy(ingressFlowData.data.deltaFiConfigs, ['name']);
    },
    async fetchErrors() {
      let ingressFlowName = (this.ingressFlowNameSelected != null) ? this.ingressFlowNameSelected.name : null;
      let showAcknowledged = this.showAcknowledged ? null : false;
      this.loading = true;
      this.lastServerContact = new Date();
      this.$toast.removeAllGroups();
      const response = await this.graphQLService.getErrors(
        showAcknowledged,
        this.offset,
        this.perPage,
        this.sortField,
        this.sortDirection,
        ingressFlowName
      );
      this.errors = response.data.deltaFiles.deltaFiles;
      this.totalErrors = response.data.deltaFiles.totalCount;
      this.loading = false;
    },
    removeSelected() {
      this.errors = this.errors.filter(error => {
        return !this.selectedErrors.includes(error);
      })
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
    actionRowClass(action) {
      if (action.state === 'ERROR') return 'table-danger action-error';
      if (action.state === 'RETRIED') return 'table-warning action-error';
    },
    actionRowClick(event) {
      let action = event.data;
      if (['ERROR', 'RETRIED'].includes(action.state)) {
        this.errorViewer.action = action;
        this.errorViewer.visible = true;
      }
    },
    onPage(event) {
      this.offset = event.first;
      this.perPage = event.rows;
      this.fetchErrors();
    },
    onSort(event) {
      this.offset = event.first;
      this.perPage = event.rows;
      this.sortField = event.sortField;
      this.sortDirection = event.sortOrder > 0 ? "DESC" : "ASC";
      this.fetchErrors();
    },
    async pollNewErrors() {
      let response = await this.graphQLService.getErrorCount(this.lastServerContact)
      let count = response.data.deltaFiles.totalCount || 0
      if (count > 0) {
        this.lastServerContact = new Date();
        let pluralized = this.utilFunctions.pluralize(count, "new error")
        this.$toast.add({
          severity: "info",
          summary: `Viewing Stale Data`,
          detail: `${pluralized} occurred since last refresh.`,
        });
      }
    }
  },
  graphQLService: null,
  utilFunctions: null,
};
</script>

<style lang="scss">
@import "@/styles/pages/errors-page.scss";
</style>