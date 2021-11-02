<template>
  <div class="Errors">
    <div class="d-flex justify-content-between flex-wrap flex-md-nowrap align-items-center pt-3 pb-2 mb-3 border-bottom">
      <h1 class="h2">Errors</h1>
      <div class="time-range btn-toolbar mb-2 mb-md-0">
        <Calendar
          id="startDateTime"
          v-model="startTimeDate"
          selectionMode="single"
          :inline="false"
          :showTime="true"
          :manualInput="false"
          hourFormat="12"
          inputClass="form-control form-control-sm ml-3"
        />
        <span class="mt-1 ml-3">&mdash;</span>
        <Calendar
          id="endDateTime"
          v-model="endTimeDate"
          selectionMode="single"
          :inline="false"
          :showTime="true"
          :manualInput="false"
          hourFormat="12"
          inputClass="form-control form-control-sm ml-3"
        />
        <button @click="fetchErrors(startTimeDate, endTimeDate)" class="btn btn-sm btn-outline-secondary ml-3">Search</button>
      </div>
    </div>
    <ConfirmPopup></ConfirmPopup>
    <Toast position="top-right"></Toast>
    <DataTable
      :value="errors"
      stripedRows
      v-model:expandedRows="expandedRows"
      class="p-datatable-gridlines p-datatable-sm"
    >
      <template #empty>
        No Errors found
      </template>
      <Column :expander="true"></Column>
      <Column field="did" header="DID (UUID)"> </Column>
      <Column field="sourceInfo.filename" header="Filename" :sortable="true"></Column>
      <Column field="sourceInfo.flow" header="Flow" :sortable="true"></Column>
      <Column field="stage" header="Stage" :sortable="true"></Column>
      <Column field="created" header="Timestamp" :sortable="true"></Column>
      <Column field="modified" header="Modified" :sortable="true"></Column>
      <Column field="last_error_cause" header="Last Error">
        <template #body="errors">
          {{findLatestError(errors.data.actions)}}
        </template>
      </Column>
      <Column field="actions.length" header="Error Count"></Column>
      <Column :exportable="false" style="min-width: 8rem">
        <template #body="errors">
          <button @click="RetryClickConfirm($event, errors.data.did)" class="btn btn-sm btn-outline-secondary">Retry</button>
        </template>
      </Column>
      <template #expansion="errors">
        <div class="errors-Subtable">
          <DataTable :value="errors.data.actions">
            <Column field="name" header="Action"></Column>
            <Column field="state" header="State"></Column>
            <Column field="created" header="Created"></Column>
            <Column field="modified" header="Modified"></Column>
            <Column field="errorCause" header="Cause"></Column>
            <Column field="errorContext" header="Context">
              <template #body="errors">
                <Button label="Show Context" icon="pi pi-external-link" class="p-button-sm p-button-raised p-button-secondary" @click="openContextDialog"  v-if="errors.data.errorContext" />
                <span v-else>No context provided</span>
                <Dialog header="Error Context" v-model:visible="showContextDialog" :style="{width: '75vw'}" :maximizable="true" :modal="true">
                  <pre class="dark">{{ errors.data.errorContext }}</pre>
                </Dialog>
              </template>
            </Column>
          </DataTable>
        </div>
      </template>
    </DataTable>
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
  name: "errors",
  data() {
    return {
      errors: [],
      expandedRows: [],
      startTimeDate: newDateObj,
      endTimeDate: currentDateObj,
      showContextDialog: false,
    };
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
      const data = await this.graphQLService.getErrors(startD, endD);
      this.errors = data.data.deltaFiles.deltaFiles;
    },
    UpdateErrors(startD, endD) {
      alert(startD + endD);
    },
    openContextDialog() {
      this.showContextDialog = true;
    },
    closeContextDialog() {
      this.showContextDialog = false;
    },
    findLatestError(errors) {
      return errors.sort((a, b) => (a.modified < b.modified ? 1 : -1))[0]
        .errorCause;
    },
  },
  graphQLService: null,
  created() {
    this.graphQLService = new GraphQLService();
    this.fetchErrors(this.startTimeDate, this.endTimeDate);
  },
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
