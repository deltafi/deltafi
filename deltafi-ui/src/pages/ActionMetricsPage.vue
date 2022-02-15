<template>
  <div class="action-metrics">
    <PageHeader heading="Action Metrics">
      <div class="btn-toolbar mb-2 mb-md-0">
        <Dropdown v-model="layout" :options="layoutOptions" option-label="name" placeholder="Layout" class="deltafi-input-field ml-3" />
        <Dropdown v-model="timeRange" :options="timeRanges" option-label="name" placeholder="Time Range" class="deltafi-input-field ml-3" @change="timeRangeChange" />
      </div>
    </PageHeader>
    <div v-show="layout.grouped" class="row pr-2 pl-2">
      <div v-if="!loaded" class="col-12">
        <ProgressBar mode="indeterminate" style="height: 0.5em" />
      </div>
      <!-- Left Column -->
      <div :class="layout.class">
        <span v-for="family in leftColumnActionFamilies" :key="family">
          <ActionMetricsTable v-if="hasActions(family)" :family="family" :actions="actionMetricsByFamily(family)" :loading="loadingActionMetrics" class="mb-3" />
        </span>
      </div>
      <!-- Right Column -->
      <div :class="layout.class">
        <span v-for="family in rightColumnActionFamilies" :key="family">
          <ActionMetricsTable v-if="hasActions(family)" :family="family" :actions="actionMetricsByFamily(family)" :loading="loadingActionMetrics" class="mb-3" />
        </span>
      </div>
    </div>
    <!-- Ungrouped -->
    <div v-show="!layout.grouped" class="row pr-2 pl-2">
      <div :class="layout.class">
        <span>
          <ActionMetricsTable family="All" :actions="actionMetricsUngrouped" :loading="loadingActionMetrics" class="mb-3" />
        </span>
      </div>
    </div>
  </div>
</template>

<script>
import ActionMetricsTable from "@/components/ActionMetricsTable.vue";
import Dropdown from "primevue/dropdown";
import ProgressBar from "primevue/progressbar";
import useActionMetrics from "@/composables/useActionMetrics";
import { ref, computed, onUnmounted, onMounted } from "vue";

const refreshInterval = 5000; // 5 seconds

export default {
  name: "ActionMetricsPage",
  components: {
    Dropdown,
    ProgressBar,
    ActionMetricsTable,
  },
  setup() {
    const { data: actionMetrics, loaded, fetch: getActionMetrics } = useActionMetrics();
    const loadingActionMetrics = ref(true);
    const timeRanges = [
      { name: "Last 5 minutes", code: "5m" },
      { name: "Last 15 minutes", code: "15m" },
      { name: "Last 30 minutes", code: "30m" },
      { name: "Last 1 hour", code: "1h" },
      { name: "Last 24 hours", code: "24h" },
    ];
    const timeRange = ref(timeRanges[0]);
    const layoutOptions = [
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
    ];
    const layout = ref(layoutOptions[0]);
    const leftColumnActionFamilies = ["ingress", "transform", "load", "delete"];
    const rightColumnActionFamilies = ["format", "enrich", "validate", "egress"];
    const actionMetricsUngrouped = computed(() => {
      return Object.keys(actionMetrics.value).reduce((result, family) => {
        return Object.assign(result, actionMetrics.value[family]);
      }, {});
    });
    let autoRefresh = null;
    onUnmounted(() => {
      clearInterval(autoRefresh);
    });
    onMounted(async () => {
      await fetchActionMetrics();
      autoRefresh = setInterval(fetchActionMetrics, refreshInterval);
    });

    const fetchActionMetrics = async () => {
      await getActionMetrics({ last: timeRange.value.code });
      loadingActionMetrics.value = false;
    };
    const hasActions = (family) => {
      return Object.keys(actionMetrics.value[family] || {}).length > 0;
    };
    const actionMetricsByFamily = (family) => {
      return actionMetrics.value[family] || {};
    };
    const timeRangeChange = async () => {
      loadingActionMetrics.value = true;
      await fetchActionMetrics();
    };

    return {
      loadingActionMetrics,
      timeRange,
      timeRanges,
      layout,
      layoutOptions,
      leftColumnActionFamilies,
      rightColumnActionFamilies,
      actionMetricsUngrouped,
      hasActions,
      actionMetricsByFamily,
      timeRangeChange,
      loaded,
    };
  },
};
</script>

<style lang="scss">
@import "@/styles/pages/action-metrics-page.scss";
</style>