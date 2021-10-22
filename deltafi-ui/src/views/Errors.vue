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
    <DataTable
      :value="errors"
      stripedRows
      v-model:expandedRows="expandedRows"
      class="p-datatable-gridlines p-datatable-sm"
    >
      <Column :expander="true"></Column>
      <Column field="did" header="DID (UUID)"> </Column>
      <Column field="filename" header="Filename" :sortable="true"></Column>
      <Column field="flow" header="Flow" :sortable="true"></Column>
      <Column field="stage" header="Stage" :sortable="true"></Column>
      <Column field="created" header="Timestamp" :sortable="true"></Column>
      <Column field="errors.length" header="Error Count"></Column>
      <Column :exportable="false" style="min-width: 8rem">
        <template #body="errors">
          <button @click="RetryClickConfirm($event, errors.data.did)" class="btn btn-sm btn-outline-secondary">Retry</button>
        </template>
      </Column>
      <template #expansion="errors">
        <div class="errors-Subtable">
          <DataTable :value="errors.data.errors">
            <Column field="action" header="Action"></Column>
            <Column field="state" header="State"></Column>
            <Column field="created" header="Created"></Column>
            <Column field="cause" header="Cause"></Column>
            <Column field="context" header="Contex">
              <template #body="errors">
                <pre> {{ errors.data.context }} </pre>
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

export default {
  name: "errors",
  data() {
    return {
      errors: [],
      expandedRows: [],
      startTimeDate: newDateObj,
      endTimeDate: currentDateObj,
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
      let data = new FormData();
      data.append("did", p_did);
      fetch("/api/v1/errors/retry", {
        method: "POST",
        referrer: "",
        body: data,
      }).then((res) => {
        console.log("Request complete! response:", res);
      });
    },
    async fetchErrors(startD, endD) {
      const request = new Request(
        "/api/v1/errors?start=" + startD.getTime() + "&end=" + endD.getTime(),
        {
          referrer: "",
        }
      );
      const res = await fetch(request);
      const data = await res.json();
      this.errors = data.errors;
    },
    UpdateErrors(startD, endD) {
      alert(startD + endD);
    },
  },
  created() {
    this.fetchErrors(this.startTimeDate, this.endTimeDate);
  },
};
</script>

<style>
.time-range .form-control:disabled,
.time-range .form-control[readonly] {
  background-color: #ffffff;
}
</style>