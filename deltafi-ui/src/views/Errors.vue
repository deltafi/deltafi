<template>
  <div class="Error's">
    <h1>Welcome to the DeltaFi Error interface.</h1>
    <span></span>
    <a>Standard Vue</a>
    <table class="table table-striped table-bordered">
      <thead>
        <tr>
          <th>DID (UUID)</th>
          <th>Filename</th>
          <th>Flow</th>
          <th>Stage</th>
          <th>Timestamp</th>
          <th>Error Count</th>
          <th>ReTry</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="error in errors" :key="error.did">
          <td>{{ error.did }}</td>
          <td>{{ error.filename }}</td>
          <td>{{ error.flow }}</td>
          <td>{{ error.stage }}</td>
          <td>{{ error.created }}</td>
          <td>{{ error.errors.length }}</td>
          <td><button @click="RetryClickAction(error.did)">Retry</button></td>
        </tr>
      </tbody>
    </table>
    <div>
      <a>Primevue</a>
    </div>
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
        <Column field="did" header="Retry"></Column>
        <template #expansion="errors">
          <div class="errors-Subtable">
            <DataTable :value="errors.data.errors" >
              <Column field="action" header="Action"></Column>
              <Column field="state" header="State"></Column>
              <Column field="created" header="Created"></Column>
              <Column field="cause" header="Cause"></Column>
              <Column field="contex" header="Contex"></Column>
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
      alert('goofy' + p_did);
    }
  },
};
</script>