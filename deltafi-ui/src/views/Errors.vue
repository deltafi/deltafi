<template>
  <div class="Error's">
    <h1>Welcome to the DeltaFi Error interface.</h1>
    <span></span>
    <table class="table table-striped table-bordered">
      <thead>
        <tr>
          <th>Parent Flow</th>
          <th>Parent Action</th>
          <th>Parent State</th>
          <th>Parent DID (UUID)</th>
          <th>Error/Child DID (UUID)</th>
          <th>Timestamp</th>
          <th>Error</th>
          <th>ReTry</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="error in errors" :key="error.did">
          <td>{{ error.parent.flow }}</td>
          <td>{{ error.parent.action }}</td>
          <td>{{ error.parent.state }}</td>
          <td>{{ error.parent.did }}</td>
          <td>{{ error.did }}</td>
          <td>{{ error.timestamp }}</td>
          <td>{{ error.cause }}</td>
          <td><button @click="RetryClickAction(error.parent.did)">Retry</button></td>
        </tr>
      </tbody>
    </table>
  </div>
</template>


<script lang="ts">
import errors from "../test/errors.test.mock";

export default {
  name: "errors",
  data() {
    return {
      errors
    };
  },
  methods: {
    RetryClickAction(p_did) {
      alert('goofy' + p_did);
    }
  },
};
</script>