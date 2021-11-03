<template>
  <div class="SystemMetrics">
    <div class="d-flex justify-content-between flex-wrap flex-md-nowrap align-items-center pt-3 pb-2 mb-3 border-bottom">
      <h1 class="h2">System Metrics</h1>
    </div>
    <DataTable
      :value="nodes"
      v-model:expandedRows="expandedRows"
      dataKey="name"
      responsiveLayout="scroll"
      stripedRows
      class="p-datatable-gridlines p-datatable-sm"
      :loading="loading"
    >
    <template #empty>
      No System Metrics available
    </template>
    <template #loading>
      Loading System Metrics Data. Please wait.
    </template>
      <Column :expander="true"></Column>
      <Column field="name" header="Node Name" sortable></Column>
      <Column header="Pods">
        <template #body="nodes">
          {{ nodes.data.pods.length }}
        </template>
      </Column>
      <Column field="resources.cpu.usage" header="CPU (Limit | Request | Usage)" sortable>
        <template #body="nodes">
          {{ formattedCores(nodes.data.resources.cpu) }}
        </template>
      </Column>
      <Column field="resources.memory.usage" header="Memory (Limit | Request | Usage)" sortable>
        <template #body="nodes">
          {{ formattedBytes(nodes.data.resources.memory) }}
        </template>
      </Column>
      <Column field="resources.disk.usage" header="Disk (Limit | Request | Usage)" sortable>
        <template #body="nodes">
          {{ formattedBytes(nodes.data.resources.disk) }}
        </template>
      </Column>
      <template #expansion="nodes">
        <div class="pods-subtable">
          <DataTable :value="nodes.data.pods" responsiveLayout="scroll">
              <Column field="name" header="Pod Name" sortable></Column>
              <Column field="resources.cpu.usage" header="Pod CPU (Limit | Request | Usage)" sortable>
                <template #body="pods">
                  {{ formattedCores(pods.data.resources.cpu) }}
                </template>
              </Column>
              <Column field="resources.memory.usage" header="Pod Memory (Limit | Request | Usage)" sortable>
                <template #body="pods">
                  {{ formattedBytes(pods.data.resources.memory) }}
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
  name: "System Metrics",
  data() {
    return {
      nodes: [],
      expandedRows: [],
      autoRefresh: null,
      loading: true,
    };
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
  watch: {
    $route(to, from) {
      // Clear the auto refresh when the route changes.
      clearInterval(this.autoRefresh);
    },
  },
};
</script>
