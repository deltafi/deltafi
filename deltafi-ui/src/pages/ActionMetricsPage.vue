<template>
  <div class="action-metrics">
    <PageHeader heading="Action Metrics">
      <div class="btn-toolbar mb-2 mb-md-0">
        <Dropdown v-model="ingressFlowNameSelected" placeholder="Select a Flow" :options="ingressFlowNames" option-label="name" show-clear :editable="false" class="deltafi-input-field ml-3" @change="flowChange" />
        <Dropdown v-model="timeRange" :options="timeRanges" option-label="name" placeholder="Time Range" class="deltafi-input-field ml-3" @change="timeRangeChange" />
      </div>
    </PageHeader>
    <span v-if="hasErrors">
      <Message v-for="error in errors" :key="error" :closable="false" severity="error">{{ error }}</Message>
    </span>
    <div v-else-if="actionMetrics">
      <div class="row pr-2 pl-2">
        <div class="col-12 pl-2 pr-2">
          <span>
            <ActionMetricsTable :actions="actionMetricsUngrouped" :loading="loadingActionMetrics" class="mb-3" @pause-timer="onPauseTimer" />
          </span>
        </div>
      </div>
    </div>
    <ProgressBar v-else mode="indeterminate" style="height: 0.5em" />
  </div>
</template>

<script setup>
import Dropdown from "primevue/dropdown";
import Message from "primevue/message";
import ProgressBar from "primevue/progressbar";
import ActionMetricsTable from "@/components/ActionMetricsTable.vue";
import PageHeader from "@/components/PageHeader.vue";
import useActionMetrics from "@/composables/useActionMetrics";
import useFlows from "@/composables/useFlows";
import useUtilFunctions from "@/composables/useUtilFunctions";
import { ref, computed, onUnmounted, onMounted } from "vue";

const refreshInterval = 5000; // 5 seconds
const { data: actionMetrics, fetch: getActionMetrics, errors } = useActionMetrics();
const { ingressFlows: ingressFlowNames, fetchIngressFlows } = useFlows();
const { sentenceCaseString } = useUtilFunctions();

fetchIngressFlows();

const loadingActionMetrics = ref(true);
const timeRanges = [
  { name: "Last 5 minutes", code: "5m" },
  { name: "Last 15 minutes", code: "15m" },
  { name: "Last 30 minutes", code: "30m" },
  { name: "Last 1 hour", code: "1h" },
  { name: "Last 24 hours", code: "24h" },
  { name: "Last 3 days", code: "3d" },
  { name: "Last 5 days", code: "5d" },
  { name: "Last 7 days", code: "7d" },
  { name: "Last 14 days", code: "14d" },
];
const ingressFlowNameSelected = ref(null);
const timeRange = ref(timeRanges[0]);

const actionMetricsUngrouped = computed(() => {
  const newObject = {};
  Object.assign(newObject, actionMetrics.value);
  for (const [familyKey, familyValue] of Object.entries(actionMetrics.value)) {
    for (const [actionKey] of Object.entries(familyValue)) {
      newObject[familyKey][actionKey]["family_type"] = sentenceCaseString(familyKey);
    }
  }
  return Object.keys(newObject).reduce((result, family) => {
    return Object.assign(result, newObject[family]);
  }, {});
});

const hasErrors = computed(() => {
  return errors.value.length > 0;
});

let autoRefresh = null;
onUnmounted(() => {
  clearInterval(autoRefresh);
});

onMounted(async () => {
  await fetchActionMetrics();
  autoRefresh = setInterval(fetchActionMetrics, refreshInterval);
})

const onPauseTimer = (value) => {
  if (value) {
    clearInterval(autoRefresh);
  } else {
    autoRefresh = setInterval(fetchActionMetrics, refreshInterval);
  }
}

const fetchActionMetrics = async () => {
  let actionMetricsParams = { last: timeRange.value.code };
  if (ingressFlowNameSelected.value) {
    actionMetricsParams['flowName'] = ingressFlowNameSelected.value.name;
  }
  await getActionMetrics(actionMetricsParams);
  loadingActionMetrics.value = false;
};

const timeRangeChange = async () => {
  loadingActionMetrics.value = true;
  await fetchActionMetrics();
};

const flowChange = async () => {
  loadingActionMetrics.value = true;
  await fetchActionMetrics();
};
</script>

<style lang="scss">
@import "@/styles/pages/action-metrics-page.scss";
</style>