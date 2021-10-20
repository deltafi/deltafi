<template>
  <div class="Errors">
    <h1>Welcome to the DeltaFi Error interface.</h1>
    <span></span>

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
          <button @click="RetryClickAction(errors.data.did)">Retry</button>
        </template>
      </Column>
      <template #expansion="errors">
        <div class="errors-Subtable">
          <DataTable :value="errors.data.errors">
            <Column field="action" header="Action"></Column>
            <Column field="state" header="State"></Column>
            <Column field="created" header="Created"></Column>
            <Column field="cause" header="Cause"></Column>
            <Column field="context" header="Contex"></Column>
          </DataTable>
        </div>
      </template>
    </DataTable>
  </div>
</template>


<script>
// import errors from "../test/errors.test.mock";

export default {
  name: "errors",
  data() {
    return {
      errors: [],
      expandedRows: [],
    };
  },
  methods: {
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
    async fetchErrors() {
      const request = new Request("/api/v1/errors", {
        referrer: "",
      });
      const res = await fetch(request);
      const data = await res.json();
      this.errors = data.errors;
    }
  },
  created() {
    this.fetchErrors();
  },
};
</script>