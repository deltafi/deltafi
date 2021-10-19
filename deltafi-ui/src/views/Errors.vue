<template>
  <div class="Error's">
    <h1>Welcome to the DeltaFi Error interface.</h1>
    <span></span>

      <DataTable
        :value="errors" 
        v-model:expandedRows="expandedRows"
        class="p-datatable-gridlines"
      >

        <Column field="did" header="DID (UUID)"> </Column>
        <Column field="filename" header="Filename"></Column>
        <Column field="flow" header="Flow"></Column>
        <Column field="stage" header="Stage"></Column>
        <Column field="created" header="Timestamp"></Column>
        <Column :expander="true"  field="errors.length" header="Error Count"></Column>
        <Column :exportable="false" style="min-width:8rem">
          <template #body="errors"> 
            <button @click="RetryClickAction(errors.data.did)">Retry</button>
          </template>
        </Column>
        <template #expansion="errors">
          <div class="errors-Subtable">
            <DataTable :value="errors.data.errors" >
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
import errors from "../test/errors.test.mock";


export default {
  name: "errors",
  data() {
    return {
      errors,
      expandedRows: [],
      
    };
  },
  methods: {
    RetryClickAction( p_did ) {
      alert(p_did);
    }
  },
};
</script>