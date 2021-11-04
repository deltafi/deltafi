<template>
  <div class="SystemMetrics">
    <div class="d-flex justify-content-between flex-wrap flex-md-nowrap align-items-center pt-3 pb-2 mb-3 border-bottom">
      <h1 class="h2">
        System Metrics
      </h1>
    </div>
    <DataTable
      v-model:expandedRows="expandedRows"
      :value="nodes"
      data-key="name"
      responsive-layout="scroll"
      striped-rows
      class="p-datatable-gridlines p-datatable-sm"
      :loading="loading"
    >
      <template #empty>
        No System Metrics available
      </template>
      <template #loading>
        Loading System Metrics Data. Please wait.
      </template>
      <Column :expander="true" />
      <Column field="name" header="Node Name" sortable />
      <Column header="Pods">
        <template #body="node">
          {{ node.data.pods.length }}
        </template>
      </Column>
      <Column field="resources.cpu.usage" header="CPU (Limit | Request | Usage)" sortable>
        <template #body="node">
          {{ formattedCores(node.data.resources.cpu) }}
        </template>
      </Column>
      <Column field="resources.memory.usage" header="Memory (Limit | Request | Usage)" sortable>
        <template #body="node">
          {{ formattedBytes(node.data.resources.memory) }}
        </template>
      </Column>
      <Column field="resources.disk.usage" header="Disk (Limit | Request | Usage)" sortable>
        <template #body="node">
          {{ formattedBytes(node.data.resources.disk) }}
        </template>
      </Column>
      <template #expansion="node">
        <div class="pods-subtable">
          <DataTable :value="node.data.pods" responsive-layout="scroll">
            <Column field="name" header="Pod Name" sortable />
            <Column field="resources.cpu.usage" header="Pod CPU (Limit | Request | Usage)" sortable>
              <template #body="pod">
                {{ formattedCores(pod.data.resources.cpu) }}
              </template>
            </Column>
            <Column field="resources.memory.usage" header="Pod Memory (Limit | Request | Usage)" sortable>
              <template #body="pod">
                {{ formattedBytes(pod.data.resources.memory) }}
              </template>
            </Column>
          </DataTable>
        </div>
      </template>
    </DataTable>
  </div>
</template>

<script>
import ApiService from "../service/ApiService";
import * as filesize from "filesize";

const refreshInterval = 5000; // 5 seconds

export default {
  name: "SystemMetricsPage",
  data() {
    return {
      nodes: [],
      expandedRows: [],
      autoRefresh: null,
      loading: true,
    };
  },
  watch: {
    $route() {
      // Clear the auto refresh when the route changes.
      clearInterval(this.autoRefresh);
    },
  },
  created() {
    this.apiService = new ApiService();
  },
  mounted() {
    this.fetchNodes();
    this.autoRefresh = setInterval(
      function () {
        this.fetchNodes();
      }.bind(this),
      refreshInterval
    );
  },
  methods: {
    async fetchNodes() {
      let response = await this.apiService.getNodes();
      this.nodes = response.nodes;
      this.loading = false;
    },
    formattedBytes(resource) {
      return [
        filesize(resource.limit || 0, { base: 10 }),
        filesize(resource.request || 0, { base: 10 }),
        filesize(resource.usage || 0, { base: 10 }),
      ].join(" | ");
    },
    formattedCores(resource) {
      return [resource.limit, resource.request, resource.usage]
        .map((c) => {
          return `${c}m`;
        })
        .join(" | ");
    },
  },
  apiService: null,
};
</script>
