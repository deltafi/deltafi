<template>
  <div class="system-metrics">
    <PageHeader heading="System Metrics" />
    <CollapsiblePanel header="Nodes" class="table-panel">
      <DataTable v-model:expandedRows="expandedRows" :value="nodes" data-key="name" responsive-layout="scroll" striped-rows class="p-datatable-gridlines p-datatable-sm node-table" :loading="!loaded">
        <template #empty> No System Metrics available </template>
        <template #loading> Loading System Metrics Data. Please wait. </template>
        <Column class="expander-column" :expander="true" />
        <Column header="Node Name" field="name" :sortable="true" />
        <Column header="Pods" class="pods-column">
          <template #body="node">
            {{ node.data.pods.length }}
          </template>
        </Column>
        <Column header="CPU" field="resources.cpu.usage" :sortable="true" class="resource-column">
          <template #body="node">
            <ProgressBar v-tooltip.top="node.data.resources.cpu.usage + 'm / ' + node.data.resources.cpu.limit + 'm'" :value="calculatePercent(node.data.resources.cpu.usage, node.data.resources.cpu.limit)" />
          </template>
        </Column>
        <Column header="Memory" field="resources.memory.usage" :sortable="true" class="resource-column">
          <template #body="node">
            <ProgressBar v-tooltip.top="formattedBytes(node.data.resources.memory.usage) + ' / ' + formattedBytes(node.data.resources.memory.limit)" :value="calculatePercent(node.data.resources.memory.usage, node.data.resources.memory.limit)"> {{ formattedBytes(node.data.resources.memory.usage) }} ({{ calculatePercent(node.data.resources.memory.usage, node.data.resources.memory.limit) }}%) </ProgressBar>
          </template>
        </Column>
        <Column header="Disk" field="resources.disk.usage" :sortable="true" class="resource-column">
          <template #body="node">
            <ProgressBar v-tooltip.top="formattedBytes(node.data.resources.disk.usage) + ' / ' + formattedBytes(node.data.resources.disk.limit)" :value="calculatePercent(node.data.resources.disk.usage, node.data.resources.disk.limit)"> {{ formattedBytes(node.data.resources.disk.usage) }} ({{ calculatePercent(node.data.resources.disk.usage, node.data.resources.disk.limit) }}%) </ProgressBar>
          </template>
        </Column>
        <template #expansion="node">
          <div class="pods-subtable">
            <DataTable :value="node.data.pods" responsive-layout="scroll" class="pod-table">
              <Column header="Pod Name" field="name" :sortable="true" />
              <Column header="Namespace" field="namespace" :sortable="true" />
              <Column header="Pod CPU" field="resources.cpu.usage" :sortable="true" class="resource-column">
                <template #body="pod">
                  <ProgressBar v-tooltip.top="buildPodResourceTooltip(pod, node, 'cpu', formattedCPU)" :value="calculatePercent(pod.data.resources.cpu.usage, pod.data.resources.cpu.limit || node.data.resources.cpu.limit)" />
                </template>
              </Column>
              <Column header="Pod Memory" field="resources.memory.usage" :sortable="true" class="resource-column">
                <template #body="pod">
                  <ProgressBar v-tooltip.top="buildPodResourceTooltip(pod, node, 'memory', formattedBytes)" :value="calculatePercent(pod.data.resources.memory.usage, pod.data.resources.memory.limit || node.data.resources.memory.limit)"> {{ formattedBytes(pod.data.resources.memory.usage) }} ({{ calculatePercent(pod.data.resources.memory.usage, pod.data.resources.memory.limit || node.data.resources.memory.limit) }}%) </ProgressBar>
                </template>
              </Column>
              <Column class="resource-column disabled-column" />
            </DataTable>
          </div>
        </template>
      </DataTable>
    </CollapsiblePanel>
  </div>
</template>

<script>
import Column from "primevue/column";
import DataTable from "primevue/datatable";
import ProgressBar from "primevue/progressbar";
import CollapsiblePanel from "@/components/CollapsiblePanel";
import useSystemMetrics from "@/composables/useSystemMetrics";
import useUtilFunctions from "@/composables/useUtilFunctions";
import { ref, onMounted, onUnmounted } from "vue";

export default {
  name: "SystemMetricsPage",
  components: {
    Column,
    DataTable,
    ProgressBar,
    CollapsiblePanel,
  },
  setup() {
    const { formattedBytes } = useUtilFunctions();
    const refreshInterval = 5000; // 5 seconds
    const expandedRows = ref([]);
    const { data: nodes, loaded, fetch: fetchSystemMetrics } = useSystemMetrics();

    let autoRefresh = null;

    onMounted(() => {
      fetchSystemMetrics();
      autoRefresh = setInterval(fetchSystemMetrics, refreshInterval);
    });

    onUnmounted(() => {
      clearInterval(autoRefresh);
    });

    const calculatePercent = (numerator, denominator) => {
      return Math.round((numerator / denominator) * 100);
    };

    const formattedCPU = (cpu) => {
      return `${cpu}m`;
    };

    const buildPodResourceTooltip = (pod, node, resource, formatFuntion) => {
      let limitType = pod.data.resources[resource].limit !== 0 ? "Pod" : "Node";
      let limit = pod.data.resources[resource].limit || node.data.resources[resource].limit;
      return [formatFuntion(pod.data.resources[resource].usage), "/", formatFuntion(limit), "\n", `(${limitType} Limit)`].join(" ");
    };

    return {
      nodes,
      loaded,
      expandedRows,
      calculatePercent,
      formattedBytes,
      formattedCPU,
      buildPodResourceTooltip,
    };
  },
};
</script>

<style lang="scss">
@import "@/styles/pages/system-metrics-page.scss";
</style>