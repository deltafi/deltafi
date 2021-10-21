<template>
  <div class="Errors">
    <ConfirmPopup></ConfirmPopup>
    <h1>Welcome to the DeltaFi Error interface.</h1>
    <span></span>
      <div class="p-field p-col-12 p-md-4" align="right">
        <label for="startTimeDate">Start Day/Time </label>
        <Calendar 
          id="startDateTime" 
          v-model="startTimeDate" 
          selectionMode="single" 
          :inline="false" 
          :showTime="true"  
          :manualInput="false" 
          hourFormat="12"
        />
        <label for="startTimeDate" style="padding-left:10px">End Day/Time </label>
        <Calendar 
          id="endDateTime" 
          v-model="endTimeDate" 
          selectionMode="single" 
          :inline="false" 
          :showTime="true"  
          :manualInput="false" 
          hourFormat="12"
        />
        <br><br>
        <button @click="fetchErrors(startTimeDate,endTimeDate)">Search</button>
    </div>
    <DataTable
      :value="errors"
      stripedRows
      v-model:expandedRows="expandedRows"
      class="p-datatable-gridlines"
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
          <button @click="RetryClickConfirm($event,errors.data.did)">Retry</button>
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
  RetryClickConfirm(event,p_did) {
        this.$confirm.require({
            target: event.currentTarget,
            message: 'Are you sure you want to Retry?',
            accept: () => {
                this.RetryClickAction(p_did);
            }
        });
    },
    RetryClickAction(p_did) {
      //alert(p_did);
      let data = new FormData();
      data.append('did',p_did);
      fetch("/api/v1/errors/retry", {
        method: "POST", 
        referrer: "",
        body: data
      }).then(res => {
        console.log("Request complete! response:", res);
      });
    },
    async fetchErrors(startD,endD) {
      const request = new Request("/api/v1/errors?start=" + startD.getTime() + "&end=" + endD.getTime(), {
        referrer: "",
      });
      const res = await fetch(request);
      const data = await res.json();
      this.errors = data.errors;
    },
    UpdateErrors(startD,endD) {
      alert(startD + endD);
    }
  },
  created() {
    this.fetchErrors(this.startTimeDate,this.endTimeDate);
  },
};
</script>