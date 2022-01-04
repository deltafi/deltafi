<template>
  <div class="queue-metrics">
    <div class="d-flex justify-content-between flex-wrap flex-md-nowrap align-items-center pt-3 pb-2 mb-3 border-bottom">
      <h1 class="h2">
        Queue Metrics
      </h1>
    </div>
    <div class="row">
      <div v-if="showProgressBar" class="col-12">
        <ProgressBar mode="indeterminate" style="height: .5em" />
      </div>
      <div v-else class="col-12">
        <CollapsiblePanel header="Queues" class="table-panel">
          <DataTable :value="queueMetrics" striped-rows class="p-datatable-sm p-datatable-gridlines" sort-field="name" :sort-order="1" :loading="loadingQueueMetrics">
            <template #empty>
              No action queue metrics available.
            </template>
            <template #loading>
              Loading action queue metrics data. Please wait.
            </template>
            <Column header="Queue Name" field="name" :sortable="true" />
            <Column header="Queue Size" field="size" :sortable="true" class="metric-column" />
            <Column header="Timestamp" field="timestamp" :sortable="true" class="metric-column" />
          </DataTable>
        </CollapsiblePanel>
      </div>
    </div>
  </div>
</template>

<script>
import ApiService from "@/service/ApiService";
import Column from "primevue/column";
import DataTable from "primevue/datatable";
import ProgressBar from "primevue/progressbar";
import CollapsiblePanel from "@/components/CollapsiblePanel";

const refreshInterval = 5000; // 5 seconds

export default {
  name: "QueueMetricsPage",
  components: {
    Column,
    DataTable,
    ProgressBar,
    CollapsiblePanel
  },
  data() {
    return {
      queueMetrics: [],
      loadingQueueMetrics: true,
      autoRefresh: null,
    };
  },
  computed: {
    showProgressBar() {
      return (
        this.loadingQueueMetrics && Object.keys(this.queueMetrics).length == 0
      );
    },
  },
  watch: {
    $route() {
      // Clear the auto refresh when the route changes.
      clearInterval(this.autoRefresh);
    },
  },
  mounted() {
    this.fetchQueueMetrics();
    this.autoRefresh = setInterval(
      function () {
        this.fetchQueueMetrics();
      }.bind(this),
      refreshInterval
    );
  },
  created() {
    this.apiService = new ApiService();
  },
  methods: {
    async fetchQueueMetrics() {
      let response = await this.apiService.get("/metrics/queues");
      this.queueMetrics = response.queues;
      this.loadingQueueMetrics = false;
    },
  },
  apiService: null,
};
</script>
