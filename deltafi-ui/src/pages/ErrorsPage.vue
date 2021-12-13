<template>
  <div class="Errors">
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
        <span class="mt-1 ml-3">&mdash;</span>
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
    <ConfirmPopup />
    <Toast position="bottom-right" />
    <DataTable
      v-model:expandedRows="expandedRows"
      :value="errors"
      striped-rows
      class="p-datatable-gridlines p-datatable-sm"
      :loading="loading"
      :paginator="true"
      :rows="10"
      paginator-template="CurrentPageReport FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown"
      current-page-report-template="Showing {first} to {last} of {totalRecords} Errors"
      :rows-per-page-options="[10,20,50]"
      :lazy="true"
      :total-records="totalErrors"
      :always-show-paginator="false"
      @page="onPage($event)"
      @sort="onSort($event)"
    >
      <template #empty>
        No errors in the selected time range
      </template>
      <template #loading>
        Loading Errors Data. Please wait.
      </template>
      <Column :expander="true" />
      <Column field="did" header="DID (UUID)">
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
      <Column field="actions.length" header="Error Count">
        <template #body="error">
          {{ countErrors(error.data.actions) }}
        </template>
      </Column>
      <Column :exportable="false" style="min-width: 8rem">
        <template #body="error">
          <Button class="p-button-sm p-button-secondary p-button-outlined" @click="RetryClickConfirm($event, error.data.did)">
            Retry
          </Button>
        </template>
      </Column>
      <template #expansion="error">
        <div class="errors-Subtable">
          <DataTable :value="error.data.actions">
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
                  <Button label="Show Context" icon="fas fa-external-link-alt" class="p-button-sm p-button-raised p-button-secondary" @click="openContextDialog(action.data.errorContext)" />
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
    <Dialog v-model:visible="showContextDialog" header="Error Context" :style="{width: '75vw'}" :maximizable="true" :modal="true">
      <pre class="dark">{{ contextDialogData }}</pre>
    </Dialog>
  </div>
</template>

<script>
import GraphQLService from "../service/GraphQLService";
import Toast from "primevue/toast";
import Column from "primevue/column";
import DataTable from "primevue/datatable";
import Dialog from "primevue/dialog";
import Calendar from "primevue/calendar";
import Dropdown from "primevue/dropdown";
import ConfirmPopup from "primevue/confirmpopup";
import Button from "primevue/button";

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
    ConfirmPopup,
    Button,
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
      perPage: 10,
      sortField: "modified",
      sortDirection: "DESC",
    };
  },
  created() {
    this.graphQLService = new GraphQLService();
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
    RetryClickConfirm(event, p_did) {
      this.$confirm.require({
        target: event.currentTarget,
        message: "Are you sure you want to Retry?",
        accept: () => {
          this.RetryClickAction(p_did);
        },
      });
    },
    RetryClickAction(p_did) {
      this.graphQLService.postErrorRetry(p_did).then((res) => {
        if (res.data !== null) {
          this.$toast.add({
            severity: "success",
            summary: "Retry request sent successfully",
            detail: p_did,
            life: 3000,
          });

          this.fetchErrors(
            this.startTimeDate,
            this.endTimeDate,
            this.offset,
            this.perPage,
            this.sortField,
            this.sortDirection,
            this.ingressFlowNameSelected
          );
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
      this.contextDialogData = this.formmatContextData(contextData);
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
    formmatContextData(contextData) {
      let formattedString = contextData;
      // Javascript does not provide the PCRE recursive parameter (?R) which would allow for the matching against nested JSON using regex: /\{(?:[^{}]|(?R))*\}/g. In order to
      // capture nested JSON we have to have this long regex.
      let jsonIdentifierRegEx = /\{(?:[^{}]|(\{(?:[^{}]|(\{(?:[^{}]|(\{(?:[^{}]|(\{(?:[^{}]|(\{(?:[^{}]|(\{(?:[^{}]|(\{(?:[^{}]|(""))*\}))*\}))*\}))*\}))*\}))*\}))*\}))*\}/g

      formattedString = formattedString.replace(jsonIdentifierRegEx, match => parseMatch(match));
      
      function parseMatch(match) {
          try {
            JSON.parse(match);
          } catch (e) {
            return match;
          }
          return JSON.stringify(JSON.parse(match), null, 2);
      }

      return formattedString;
    },
  },
  graphQLService: null,
};
</script>

<style lang="scss">
@import "../styles/errors-page.scss";
</style>