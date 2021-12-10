<template>
  <div class="action-metrics">
    <div class="d-flex justify-content-between flex-wrap flex-md-nowrap align-items-center pt-3 pb-2 mb-3 border-bottom">
      <h1 class="h2">
        Action Metrics
      </h1>
      <div class="btn-toolbar mb-2 mb-md-0">
        <Dropdown v-model="layout" :options="layoutOptions" option-label="name" placeholder="Layout" class="ml-3" />
        <Dropdown v-model="time_range" :options="time_ranges" option-label="name" placeholder="Time Range" class="ml-3" @change="timeRangeChange" />
      </div>
    </div>
    <div v-show="layout.grouped" class="row pr-2 pl-2">
      <div v-if="showProgressBar" class="col-12">
        <ProgressBar mode="indeterminate" style="height: .5em" />
      </div>
      <!-- Left Column -->
      <div :class="layout.class">
        <span v-for="family in leftColumnActionFamilies" :key="family">
          <ActionMetricsTable
            v-if="hasActions(family)"
            :family="family" :actions="actionMetricsByFamily(family)"
            :loading="loadingActionMetrics" class="mb-3"
          />
        </span>
      </div>
      <!-- Right Column -->
      <div :class="layout.class">
        <span v-for="family in rightColumnActionFamilies" :key="family">
          <ActionMetricsTable
            v-if="hasActions(family)"
            :family="family" :actions="actionMetricsByFamily(family)"
            :loading="loadingActionMetrics" class="mb-3"
          />
        </span>
      </div>
    </div>
    <!-- Ungrouped -->
    <div v-show="!layout.grouped" class="row pr-2 pl-2">
      <div :class="layout.class">
        <span>
          <ActionMetricsTable
            family="All" :actions="actionMetricsUngrouped"
            :loading="loadingActionMetrics" class="mb-3"
          />
        </span>
      </div>
    </div>
  </div>
</template>

<script>
import ActionMetricsTable from "@/components/ActionMetricsTable.vue";
import ApiService from "../service/ApiService";
import Dropdown from "primevue/dropdown";
import ProgressBar from "primevue/progressbar";

const refreshInterval = 5000; // 5 seconds

export default {
  name: "ActionMetricsPage",
  components: {
    Dropdown,
    ProgressBar,
    ActionMetricsTable,
  },
  data() {
    return {
      activeIndex: 0,
      actionMetrics: {},
      displayGrouped: true,
      loadingActionMetrics: true,
      autoRefresh: null,
      time_range: { name: "Last 5 minutes", code: "5m" },
      time_ranges: [
        { name: "Last 5 minutes", code: "5m" },
        { name: "Last 15 minutes", code: "15m" },
        { name: "Last 30 minutes", code: "30m" },
        { name: "Last 1 hour", code: "1h" },
        { name: "Last 24 hours", code: "24h" },
      ],
      layout: null,
      layoutOptions: [
        {
          name: "Two Columns - Grouped",
          class: "col-6 pl-2 pr-2",
          grouped: true,
          code: 0,
        },
        {
          name: "One Column - Grouped",
          class: "col-12 pl-2 pr-2",
          grouped: true,
          code: 1,
        },
        {
          name: "One Column - Ungrouped",
          class: "col-12 pl-2 pr-2",
          grouped: false,
          code: 2,
        },
      ],
      leftColumnActionFamilies: ["ingress", "transform", "load", "delete"],
      rightColumnActionFamilies: ["format", "enrich", "validate", "egress"],
    };
  },
  computed: {
    showProgressBar() {
      return (
        this.loadingActionMetrics && Object.keys(this.actionMetrics).length == 0
      );
    },
    actionMetricsUngrouped() {
      return Object.keys(this.actionMetrics).reduce((result, family) => {
        return Object.assign(result, this.actionMetrics[family]);
      }, {});
    },
  },
  watch: {
    $route() {
      // Clear the auto refresh when the route changes.
      clearInterval(this.autoRefresh);
    },
  },
  mounted() {
    this.fetchActionMetrics();
    this.autoRefresh = setInterval(
      function () {
        this.fetchActionMetrics();
      }.bind(this),
      refreshInterval
    );
  },
  created() {
    this.layout = this.layoutOptions[0];
    this.apiService = new ApiService();
  },
  methods: {
    async fetchActionMetrics() {
      let response = await this.apiService.get(
        "/metrics/action",
        new URLSearchParams({ last: this.time_range.code })
      );
      this.actionMetrics = response.actions;
      this.loadingActionMetrics = false;
    },
    hasActions(family) {
      return Object.keys(this.actionMetrics[family] || {}).length > 0;
    },
    actionMetricsByFamily(family) {
      return this.actionMetrics[family] || {};
    },
    timeRangeChange() {
      this.loadingActionMetrics = true;
      this.fetchActionMetrics();
    },
  },
  apiService: null,
};
</script>

<style lang="scss">
@import "../styles/action-metrics-page.scss";
</style>