<template>
  <div class="container">
    <h3 class="p-3 text-center">DeltaFi Nodes:</h3>
    <table class="table table-striped table-bordered">
      <thead>
        <tr>
          <th>Name</th>
          <th>Created</th>
          <th>Timestamp</th>
          <th>Window</th>
          <th>cpu</th>
          <th>Memory</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="node in nodes" :key="node.metadata.name">
          <td>{{ node.metadata.name }}</td>
          <td>{{ node.metadata.creationTimestamp }}</td>
          <td>{{ node.timestamp }}</td>
          <td>{{ node.window }}</td>
          <td>{{ node.usage.cpu }}</td>
          <td>{{ node.usage.memory }}</td>
        </tr>
      </tbody>
    </table>
    <div></div>
    <h3 class="p-3 text-center">DeltaFi Pods:</h3>
    <table class="table table-striped table-bordered">
      <thead>
        <tr>
          <th>Name</th>
          <th>Created</th>
          <th>Timestamp</th>
          <th>Window</th>
          <th>CPU</th>
          <th>Memory</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="pod in pods" :key="pod.metadata.name">
          <td>{{ pod.metadata.name }}</td>
          <td>{{ pod.metadata.creationTimestamp }}</td>
          <td>{{ pod.timestamp }}</td>
          <td>{{ pod.window }}</td>
          <td>{{ pod.containers[0].usage.cpu}}</td>
          <td>{{ pod.containers[0].usage.memory}}</td>

        </tr>
      </tbody>
    </table>
  </div>
</template>

<script lang="ts">
import nodes from "../test/nodes.test.mock";
import pods from "../test/pods.test.mock";

export default {
  name: "DFDataTable",
  data() {
    return {
      nodes,
      pods,
    };
  },
};
</script>

<!-- Add "scoped" attribute to limit CSS to this component only -->
<style scoped>
h3 {
  margin: 40px 0 0;
}
ul {
  list-style-type: none;
  padding: 0;
}
li {
  display: inline-block;
  margin: 0 10px;
}
a {
  color: #42b983;
}
</style>
