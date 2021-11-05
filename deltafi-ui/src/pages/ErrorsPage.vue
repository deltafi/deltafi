<template>
  <div class="Errors">
    <div class="d-flex justify-content-between flex-wrap flex-md-nowrap align-items-center pt-3 pb-2 mb-3 border-bottom">
      <h1 class="h2">
        Errors
      </h1>
      <div class="time-range btn-toolbar mb-2 mb-md-0">
        <Calendar
          id="startDateTime"
          v-model="startTimeDate"
          selection-mode="single"
          :inline="false"
          :show-time="true"
          :manual-input="false"
          hour-format="12"
          input-class="form-control form-control-sm ml-3"
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
          input-class="form-control form-control-sm ml-3"
        />
        <button class="btn btn-sm btn-outline-secondary ml-3" @click="fetchErrors(startTimeDate, endTimeDate)">
          Search
        </button>
      </div>
    </div>
    <ConfirmPopup />
    <Toast position="top-right" />
    <DataTable
      v-model:expandedRows="expandedRows"
      :value="errors"
      striped-rows
      class="p-datatable-gridlines p-datatable-sm"
      :loading="loading"
    >
      <template #empty>
        No errors in the selected time range
      </template>
      <template #loading>
        Loading Errors Data. Please wait.
      </template>
      <Column :expander="true" />
      <Column field="did" header="DID (UUID)" />
      <Column field="sourceInfo.filename" header="Filename" :sortable="true" />
      <Column field="sourceInfo.flow" header="Flow" :sortable="true" />
      <Column field="stage" header="Stage" :sortable="true" />
      <Column field="created" header="Timestamp" :sortable="true" />
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
          <button class="btn btn-sm btn-outline-secondary" @click="RetryClickConfirm($event, error.data.did)">
            Retry
          </button>
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
                  <Button  label="Show Context" icon="pi pi-external-link" class="p-button-sm p-button-raised p-button-secondary" @click="openContextDialog(action.data.errorContext)" />
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
var currentDateObj = new Date();
var numberOfMlSeconds = currentDateObj.getTime();
var addMlSeconds = 60 * 60 * 1000;
var newDateObj = new Date(numberOfMlSeconds - addMlSeconds);
currentDateObj = new Date(numberOfMlSeconds + addMlSeconds);
import GraphQLService from "../service/GraphQLService";

export default {
  name: "ErrorsPage",
  data() {
    return {
      errors: [],
      expandedRows: [],
      startTimeDate: newDateObj,
      endTimeDate: currentDateObj,
      showContextDialog: false,
      loading: true,
      contextDialogData: "",
    };
  },
  created() {
    this.graphQLService = new GraphQLService();
    this.fetchErrors(this.startTimeDate, this.endTimeDate);
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
            summary: "Retry Successful",
            detail: "",
            life: 3000,
          });
          this.fetchErrors(this.startTimeDate, this.endTimeDate);
        } else {
          this.$toast.add({
            severity: "error",
            summary: "Retry Failed",
            detail: res.errors[0].message,
            life: 10000,
          });
        }
      });
    },
    async fetchErrors(startD, endD) {
      this.loading = true;
      const data = await this.graphQLService.getErrors(startD, endD);
      this.errors = data.data.deltaFiles.deltaFiles;
      this.loading = false;
    },
    UpdateErrors(startD, endD) {
      alert(startD + endD);
    },
    openContextDialog(contextData) {
      this.contextDialogData = contextData;
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
  },
  graphQLService: null,
};
</script>
<style>
.time-range .form-control:disabled,
.time-range .form-control[readonly] {
  background-color: #ffffff;
}

pre.dark {
  background-color: #333333;
  color: #dddddd;
  padding: 1em;
}
</style>
