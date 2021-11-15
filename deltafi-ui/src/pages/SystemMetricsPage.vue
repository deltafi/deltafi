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
      class="p-datatable-gridlines p-datatable-sm node-table"
      :loading="loading"
    >
      <ColumnGroup type="header">
        <Row>
          <Column :rowspan="2" />
          <Column header="Node Name" :sortable="true" field="name" :rowspan="2" />
          <Column header="Pods" :rowspan="2" />
          <Column header="CPU" :rowspan="1" :colspan="2" />
          <Column header="Memory" :rowspan="1" :colspan="2" />
          <Column header="Disk" :sortable="true" field="resources.disk.usage" :rowspan="1" />
        </Row>
        <Row>
          <Column class="pbar-column" header="Used / Limit" :sortable="true" field="resources.cpu.usage" :rowspan="1" />
          <Column class="pbar-column" header="Requested / Limit" :sortable="true" field="resources.cpu.request" :rowspan="1" />
          <Column class="pbar-column" header="Used / Limit" :sortable="true" field="resources.memory.usage" :rowspan="1" />
          <Column class="pbar-column" header="Requested / Limit" :sortable="true" field="resources.memory.request" :rowspan="1" />
          <Column class="pbar-column" header="Used / Requested" :sortable="true" field="resources.disk.usage" :rowspan="1" />
        </Row>
      </ColumnGroup>
      <template #empty>
        No System Metrics available
      </template>
      <template #loading>
        Loading System Metrics Data. Please wait.
      </template>
      <Column class="expander-column" :expander="true" />
      <Column field="name" />
      <Column class="pods-column">
        <template #body="node">
          {{ node.data.pods.length }}
        </template>
      </Column>
      <Column field="resources.cpu.usage">
        <template #body="node">
          <ProgressBar v-tooltip.top="node.data.resources.cpu.usage + 'm / ' + node.data.resources.cpu.limit + 'm'" :value="Math.round((node.data.resources.cpu.usage / node.data.resources.cpu.limit ) * 100)" />
        </template>
      </Column>
      <Column field="resources.cpu.request">
        <template #body="node">
          <ProgressBar v-tooltip.top="node.data.resources.cpu.request + 'm / ' + node.data.resources.cpu.limit + 'm'" :value="Math.round((node.data.resources.cpu.request / node.data.resources.cpu.limit ) * 100)" />
        </template>
      </Column>
      <Column field="resources.memory.usage">
        <template #body="node">
          <ProgressBar v-tooltip.top="formattedBytes(node.data.resources.memory.usage) + ' / ' + formattedBytes(node.data.resources.memory.limit)" :value="Math.round((node.data.resources.memory.usage/ node.data.resources.memory.limit ) * 100)">
            {{ formattedBytes(node.data.resources.memory.usage) }} ({{ Math.round((node.data.resources.memory.usage/ node.data.resources.memory.limit ) * 100) }}%)
          </ProgressBar>
        </template>
      </Column>
      <Column field="resources.memory.request">
        <template #body="node">
          <ProgressBar v-tooltip.top="formattedBytes(node.data.resources.memory.request) + ' / ' + formattedBytes(node.data.resources.memory.limit)" :value="Math.round((node.data.resources.memory.request/ node.data.resources.memory.limit ) * 100)">
            {{ formattedBytes(node.data.resources.memory.request) }} ({{ Math.round((node.data.resources.memory.request / node.data.resources.memory.limit ) * 100) }}%)
          </ProgressBar>
        </template>
      </Column>
      <Column field="resources.disk.usage">
        <template #body="node">
          <ProgressBar v-if="node.data.resources.disk.request !== 0" v-tooltip.top="formattedBytes(node.data.resources.disk.usage) + ' / ' + formattedBytes(node.data.resources.disk.request)" :value="Math.round((node.data.resources.disk.usage/ node.data.resources.disk.request ) * 100)">
            {{ formattedBytes(node.data.resources.disk.usage) }} ({{ Math.round((node.data.resources.disk.usage/ node.data.resources.disk.request ) * 100) }}%)
          </ProgressBar>
          <span v-else> No limit given, {{ formattedBytes(node.data.resources.disk.request) }} Requested </span>
        </template>
      </Column>
      <template #expansion="node">
        <div class="pods-subtable">
          <DataTable :value="node.data.pods" responsive-layout="scroll" class="pod-table">
            <ColumnGroup type="header">
              <Row>
                <Column header="Pod Name" :sortable="true" field="name" :rowspan="2" />
                <Column header="Pod CPU" :colspan="2" :rowspan="1" />
                <Column header="Pod Memory" :colspan="2" :rowspan="1" />
              </Row>
              <Row>
                <Column class="pbar-column" header="Used / Limit" :sortable="true" field="resources.cpu.usage" :rowspan="1" />
                <Column class="pbar-column" header="Requested / Limit" :sortable="true" field="resources.cpu.request" :rowspan="1" />
                <Column class="pbar-column" header="Used / Limit" :sortable="true" field="resources.memory.usage" :rowspan="1" />
                <Column class="pbar-column" header="Requested / Limit" :sortable="true" field="resources.memory.request" :rowspan="1" />
              </Row>
            </ColumnGroup>
            <Column field="name" />
            <Column field="resources.cpu.usage">
              <template #body="pod">
                <ProgressBar v-if="pod.data.resources.cpu.limit !== 0" v-tooltip.top="pod.data.resources.cpu.usage + 'm / ' + pod.data.resources.cpu.limit + 'm'" :value="Math.round((pod.data.resources.cpu.usage / pod.data.resources.cpu.limit ) * 100)" />
                <ProgressBar v-else v-tooltip.top="pod.data.resources.cpu.usage + 'm / ' + node.data.resources.cpu.limit + 'm\n(Node Limit)'" :value="Math.round((pod.data.resources.cpu.usage / node.data.resources.cpu.limit ) * 100)" />
              </template>
            </Column>
            <Column field="resources.cpu.request">
              <template #body="pod">
                <ProgressBar v-if="pod.data.resources.cpu.limit !== 0" v-tooltip.top="pod.data.resources.cpu.request + 'm / ' + pod.data.resources.cpu.limit + 'm'" :value="Math.round((pod.data.resources.cpu.request / pod.data.resources.cpu.limit ) * 100)" />
                <ProgressBar v-else v-tooltip.top="pod.data.resources.cpu.request + 'm / ' + node.data.resources.cpu.limit + 'm\n(Node Limit)'" :value="Math.round((pod.data.resources.cpu.request / node.data.resources.cpu.limit ) * 100)" />
              </template>
            </Column>
            <Column field="resources.memory.usage">
              <template #body="pod">
                <ProgressBar v-if="pod.data.resources.memory.limit !== 0" v-tooltip.top="formattedBytes(pod.data.resources.memory.usage) + ' / ' + formattedBytes(pod.data.resources.memory.limit)" :value="Math.round((pod.data.resources.memory.usage / pod.data.resources.memory.limit ) * 100)">
                  {{ formattedBytes(pod.data.resources.memory.usage) }} ({{ Math.round((pod.data.resources.memory.usage / pod.data.resources.memory.limit ) * 100) }}%)
                </ProgressBar>
                <ProgressBar v-else v-tooltip.top="formattedBytes(pod.data.resources.memory.usage) + ' / ' + formattedBytes(node.data.resources.memory.limit) + '\n(Node Limit)'" :value="Math.round((pod.data.resources.memory.usage / node.data.resources.memory.limit ) * 100)">
                  {{ formattedBytes(pod.data.resources.memory.usage) }} ({{ Math.round((pod.data.resources.memory.usage / node.data.resources.memory.limit ) * 100) }}%)
                </ProgressBar>
              </template>
            </Column>
            <Column field="resources.memory.request">
              <template #body="pod">
                <ProgressBar v-if="pod.data.resources.memory.limit !== 0" v-tooltip.top="formattedBytes(pod.data.resources.memory.request) + ' / ' + formattedBytes(pod.data.resources.memory.limit)" :value="Math.round((pod.data.resources.memory.request / pod.data.resources.memory.limit ) * 100)">
                  {{ formattedBytes(pod.data.resources.memory.request) }} ({{ Math.round((pod.data.resources.memory.request / pod.data.resources.memory.limit ) * 100) }}%)
                </ProgressBar>
                <ProgressBar v-else v-tooltip.top="formattedBytes(pod.data.resources.memory.request) + ' / ' + formattedBytes(node.data.resources.memory.limit) + '\n(Node Limit)'" :value="Math.round((pod.data.resources.memory.request / node.data.resources.memory.limit ) * 100)">
                  {{ formattedBytes(pod.data.resources.memory.request) }} ({{ Math.round((pod.data.resources.memory.request / node.data.resources.memory.limit ) * 100) }}%)
                </ProgressBar>
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
    formattedBytes(memory) {
      return filesize(memory || 0, { base: 10 });
    },
  },
  apiService: null,
};
</script>
<style>
.p-progressbar {
  background: #dadada;
}
.p-progressbar .p-progressbar-value {
  background: #60a0ff;
}
.pbar-column {
  width: 15%;
}
.expander-column,
.pods-column {
    width: 1%;
}
</style>
