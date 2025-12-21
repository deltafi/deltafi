<!--
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
-->

<template>
  <div class="system-map-page">
    <ConfirmDialog group="stop-flow">
      <template #message="slotProps">
        <span class="p-confirm-dialog-icon pi pi-exclamation-triangle" />
        <span class="p-confirm-dialog-message" v-html="slotProps.message.message" />
      </template>
    </ConfirmDialog>
    <PageHeader>
      <template #header>
        <div class="d-flex align-items-center">
          <h2 class="mb-0">System Map: Full</h2>
          <template v-if="filterMode && filterNodeName">
            <span class="dataflow-indicator ml-3">
              <i class="pi pi-share-alt mr-1" />
              Dataflow: <strong>{{ filterNodeName }}</strong>
            </span>
            <Button
              label="Show All"
              icon="pi pi-expand"
              class="p-button-outlined p-button-sm ml-2"
              @click="clearFilter"
            />
          </template>
        </div>
      </template>
      <template #default>
        <MetricsControls
          v-model="metricsInterval"
          @refresh="manualRefresh"
        />
      </template>
    </PageHeader>

    <ProgressBar v-if="showLoading" mode="indeterminate" style="height: 0.5em" />

    <div v-if="!showLoading" class="map-container">
      <div class="graph-wrapper">
        <SystemMapGraph
          :nodes="displayNodes"
          :edges="displayEdges"
          :selected-node-id="selectedNode?.id"
          :error-counts="errorsByFlow"
          :queue-counts="queueCountsByFlow"
          :flow-metrics="perFlowMetricsMap"
          @select-node="onSelectNode"
          @error-badge-click="onErrorBadgeClick"
          @flow-state-change="onFlowStateChange"
          @show-dataflow="onShowDataflow"
        />
      </div>

      <!-- Details panel -->
      <div v-if="selectedNode" class="details-panel">
        <div class="panel-header">
          <div class="panel-header-left">
            <div class="panel-title">
              <span class="panel-node-name">{{ selectedNode.name }}</span>
              <span class="panel-node-type">{{ formatTypeLong(selectedNode.type) }}</span>
              <span v-if="selectedNode.state" :class="['panel-state-badge', `state-${selectedNode.state.toLowerCase()}`]">
                {{ selectedNode.state }}
              </span>
              <span v-if="selectedNodeStatusBadge" v-tooltip.top="selectedNodeStatusBadge.tooltip" :class="['panel-status-badge', selectedNodeStatusBadge.severity]">
                <i :class="selectedNodeStatusBadge.icon" />
                {{ selectedNodeStatusBadge.label }}
              </span>
            </div>
            <div v-if="selectedNode.sourcePlugin" class="panel-plugin">
              <i class="fas fa-plug fa-rotate-90" />
              {{ selectedNode.sourcePlugin.artifactId }}
            </div>
          </div>
          <button class="panel-close" @click="selectedNode = null">
            <i class="pi pi-times" />
          </button>
        </div>
        <div class="panel-content">
          <div class="panel-actions">
            <Button
              v-if="filterMode && filterNodeId === selectedNode.id"
              label="Show All"
              icon="pi pi-expand"
              class="p-button-outlined p-button-sm"
              @click="clearFilter"
            />
            <Button
              v-else
              label="Show Dataflow"
              icon="pi pi-share-alt"
              class="p-button-outlined p-button-sm"
              @click="showDataflow"
            />
            <Button
                label="Goto Detail Page"
                icon="pi pi-arrow-right"
                class="p-button-sm"
                @click="goToPipelineView"
            />
          </div>

          <!-- Action chain for flows -->
          <div v-if="selectedNode.type !== 'TOPIC'" class="mt-3">
            <div v-if="loadingDetails" class="loading-indicator">
              <i class="pi pi-spin pi-spinner" />
              <span>Loading flow details...</span>
            </div>

            <div v-else-if="selectedNodeActions.length > 0" class="action-chain">
              <div class="action-chain-header">
                <span class="action-chain-title">Action Chain</span>
                <span class="action-count">{{ selectedNodeActions.length }} action{{ selectedNodeActions.length !== 1 ? 's' : '' }}</span>
              </div>
              <div class="action-list">
                <template v-for="(action, index) in selectedNodeActions" :key="action.name">
                  <!-- Queue bubble before action -->
                  <div
                    v-if="actionQueueCounts[action.name]?.total > 0"
                    v-tooltip.top="formatActionQueueTooltip(action.name)"
                    class="action-queue-bubble"
                  >
                    {{ actionQueueCounts[action.name].total.toLocaleString() }}
                  </div>
                  <div v-if="actionQueueCounts[action.name]?.total > 0" class="action-arrow">
                    <i class="pi pi-arrow-right" />
                  </div>
                  <div v-tooltip.top="{ value: formatActionTooltip(action), escape: false }" class="action-item">
                    <div class="action-details">
                      <div class="action-name">{{ action.name }}</div>
                      <div class="action-type">{{ action.type }}</div>
                      <div v-if="actionMetricsMap[action.name]" class="action-metrics">
                        <span v-if="actionMetricsMap[action.name].executionCount > 0" class="metric">
                          <i class="pi pi-play" />
                          {{ formatNumber(actionMetricsMap[action.name].executionCount) }}
                        </span>
                        <span v-if="actionMetricsMap[action.name].executionTimeMs > 0" class="metric">
                          <i class="pi pi-clock" />
                          {{ formatDuration(actionMetricsMap[action.name].executionTimeMs, actionMetricsMap[action.name].executionCount) }}
                        </span>
                      </div>
                    </div>
                    <div v-if="action.join" class="action-join-badge">
                      <i class="pi pi-link" />
                      Join
                    </div>
                  </div>
                  <div v-if="index < selectedNodeActions.length - 1" class="action-arrow">
                    <i class="pi pi-arrow-right" />
                  </div>
                </template>
              </div>
            </div>

            <p v-else-if="selectedNodeDetails" class="text-muted">
              This flow has no actions configured.
            </p>
          </div>

          <div v-if="selectedNode.type === 'TOPIC'" class="node-connections mt-3">
            <div v-if="getPublishers(selectedNode.id).length > 0" class="connection-group">
              <h6>Publishers</h6>
              <div class="connection-list">
                <span
                  v-for="pub in getPublishers(selectedNode.id)"
                  :key="pub.id"
                  class="connection-chip"
                  @click="onSelectNode(pub)"
                >
                  {{ pub.name }}
                </span>
              </div>
            </div>
            <div v-if="getSubscribers(selectedNode.id).length > 0" class="connection-group">
              <h6>Subscribers</h6>
              <div class="connection-list">
                <span
                  v-for="sub in getSubscribers(selectedNode.id)"
                  :key="sub.id"
                  class="connection-chip"
                  @click="onSelectNode(sub)"
                >
                  {{ sub.name }}
                </span>
              </div>
            </div>
          </div>

          <div v-else class="node-connections mt-3">
            <div v-if="getUpstream(selectedNode.id).length > 0" class="connection-group">
              <h6>Upstream</h6>
              <div class="connection-list">
                <span
                  v-for="node in getUpstream(selectedNode.id)"
                  :key="node.id"
                  class="connection-chip"
                  @click="onSelectNode(node)"
                >
                  {{ node.name }}
                </span>
              </div>
            </div>
            <div v-if="getDownstream(selectedNode.id).length > 0" class="connection-group">
              <h6>Downstream</h6>
              <div class="connection-list">
                <span
                  v-for="node in getDownstream(selectedNode.id)"
                  :key="node.id"
                  class="connection-chip"
                  @click="onSelectNode(node)"
                >
                  {{ node.name }}
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
// ABOUTME: System map page showing full topology of all flows and topics.
// ABOUTME: Uses SVG-based rendering with details panel and navigation options.

import PageHeader from "@/components/PageHeader.vue";
import ProgressBar from "@/components/deprecatedPrimeVue/ProgressBar.vue";
import SystemMapGraph from "@/components/pipeline/SystemMapGraph.vue";
import { formatTypeLong } from "@/components/pipeline/useGraphStyles";
import useFlowErrors from "@/components/pipeline/useFlowErrors";
import useFlows from "@/composables/useFlows";
import useFlowQueryBuilder from "@/composables/useFlowQueryBuilder";
import useDataSource from "@/composables/useDataSource";
import useActionMetrics from "@/composables/useActionMetrics";
import useNotifications from "@/composables/useNotifications";
import useTopics from "@/composables/useTopics";
import usePerFlowMetrics from "@/composables/usePerFlowMetrics";
import useQueueMetrics, { aggregateQueueCountsByFlow } from "@/composables/useQueueMetrics";
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import { useRouter } from "vue-router";

import Button from "primevue/button";
import ConfirmDialog from "primevue/confirmdialog";
import { useConfirm } from "primevue/useconfirm";
import MetricsControls from "@/components/pipeline/MetricsControls.vue";

const router = useRouter();
const confirm = useConfirm();
const { topics, getAllTopics, loading, loaded } = useTopics();
const { errorsByFlow, fetch: fetchFlowErrors } = useFlowErrors();
const { setFlowState } = useFlows();
const { getTransformFlowByName, getDataSinkByName } = useFlowQueryBuilder();
const { getTimedDataSources } = useDataSource();
const { data: actionMetrics, fetch: fetchActionMetrics } = useActionMetrics();
const { data: perFlowMetrics, fetch: fetchPerFlowMetrics } = usePerFlowMetrics();
const { data: queueMetrics, fetch: fetchQueueMetrics } = useQueueMetrics();
const notify = useNotifications();

// Metrics refresh interval (30 seconds)
const METRICS_REFRESH_INTERVAL = 30000;
let metricsRefreshTimer = null;

const selectedNode = ref(null);
const selectedNodeDetails = ref(null);
const loadingDetails = ref(false);
const refreshing = ref(false);

// Show loading only on initial load, not during refreshes
const showLoading = computed(() => !loaded.value);
const filterMode = ref(false);
const filterNodeId = ref(null);
const filterNodeName = ref(null);

// Metrics interval state
const metricsInterval = ref(15);

// Map action metrics by name for easy lookup
const actionMetricsMap = computed(() => {
  const map = {};
  for (const m of actionMetrics.value) {
    map[m.actionName] = m;
  }
  return map;
});

// Get queue counts per action for the selected flow
const actionQueueCounts = computed(() => {
  if (!selectedNode.value || !queueMetrics.value) return {};
  const flowName = selectedNode.value.name;
  const counts = {};

  for (const q of queueMetrics.value.warmQueues || []) {
    if (q.flowName === flowName) {
      if (!counts[q.actionName]) counts[q.actionName] = { warm: 0, cold: 0, total: 0 };
      counts[q.actionName].warm += q.count;
      counts[q.actionName].total += q.count;
    }
  }
  for (const q of queueMetrics.value.coldQueues || []) {
    if (q.flowName === flowName) {
      if (!counts[q.actionName]) counts[q.actionName] = { warm: 0, cold: 0, total: 0 };
      counts[q.actionName].cold += q.count;
      counts[q.actionName].total += q.count;
    }
  }
  return counts;
});

function formatActionQueueTooltip(actionName) {
  const q = actionQueueCounts.value[actionName];
  if (!q || q.total === 0) return '';
  const parts = [];
  if (q.warm > 0) parts.push(`${q.warm.toLocaleString()} warm`);
  if (q.cold > 0) parts.push(`${q.cold.toLocaleString()} cold`);
  return `Queued: ${parts.join(', ')}`;
}

// Extract actions from selected node details
const selectedNodeActions = computed(() => {
  if (!selectedNodeDetails.value) return [];

  const details = selectedNodeDetails.value;

  // Transform flows have transformActions array
  if (details.transformActions && Array.isArray(details.transformActions)) {
    return details.transformActions;
  }

  // Data sinks have a single egressAction
  if (details.egressAction) {
    return [details.egressAction];
  }

  // Timed data sources have timedIngressAction
  if (details.timedIngressAction) {
    return [details.timedIngressAction];
  }

  return [];
});

// Status badge for selected node (INVALID or PLUGIN states)
const selectedNodeStatusBadge = computed(() => {
  if (!selectedNode.value || selectedNode.value.type === "TOPIC") return null;

  // INVALID state: flow has validation errors
  if (selectedNode.value.valid === false) {
    const errorMessages = selectedNode.value.errors?.map((e) => e.message).join(", ") || "Configuration error";
    return {
      label: "INVALID",
      icon: "fas fa-exclamation-circle",
      severity: "invalid",
      tooltip: errorMessages,
    };
  }

  // PLUGIN states: only show when flow is RUNNING but plugin isn't ready
  if (selectedNode.value.state === "RUNNING" && selectedNode.value.pluginReady === false) {
    const isDisabled = selectedNode.value.pluginNotReadyReason?.includes("disabled");
    return {
      label: "PLUGIN",
      icon: isDisabled ? "fas fa-pause" : "fas fa-clock",
      severity: "plugin",
      tooltip: selectedNode.value.pluginNotReadyReason || "Plugin not ready",
    };
  }

  return null;
});

// Build graph from topics
const flowGraph = computed(() => {
  if (!topics.value || topics.value.length === 0) return { nodes: [], edges: [] };

  const nodesMap = {};
  const edges = [];

  for (const topic of topics.value) {
    const topicId = `TOPIC:${topic.name}`;
    if (!nodesMap[topicId]) {
      nodesMap[topicId] = {
        id: topicId,
        name: topic.name,
        type: "TOPIC",
        state: null,
      };
    }

    for (const pub of topic.publishers || []) {
      const pubId = `${pub.type}:${pub.name}`;
      if (!nodesMap[pubId]) {
        nodesMap[pubId] = {
          id: pubId,
          name: pub.name,
          type: pub.type,
          state: pub.state,
          valid: pub.valid,
          errors: pub.errors,
          sourcePlugin: pub.sourcePlugin,
          pluginReady: pub.pluginReady,
          pluginNotReadyReason: pub.pluginNotReadyReason,
        };
      }
      edges.push({ source: pubId, target: topicId });
    }

    for (const sub of topic.subscribers || []) {
      const subId = `${sub.type}:${sub.name}`;
      if (!nodesMap[subId]) {
        nodesMap[subId] = {
          id: subId,
          name: sub.name,
          type: sub.type,
          state: sub.state,
          valid: sub.valid,
          errors: sub.errors,
          sourcePlugin: sub.sourcePlugin,
          pluginReady: sub.pluginReady,
          pluginNotReadyReason: sub.pluginNotReadyReason,
        };
      }
      edges.push({ source: topicId, target: subId });
    }
  }

  return {
    nodes: Object.values(nodesMap),
    edges,
  };
});

// Get all transitively connected nodes (complete dataflow)
function getConnectedNodes(startNodeId, edges) {
  const connected = new Set([startNodeId]);
  let changed = true;

  // Keep expanding until no new nodes are found
  while (changed) {
    changed = false;
    for (const edge of edges) {
      if (connected.has(edge.source) && !connected.has(edge.target)) {
        connected.add(edge.target);
        changed = true;
      }
      if (connected.has(edge.target) && !connected.has(edge.source)) {
        connected.add(edge.source);
        changed = true;
      }
    }
  }

  return connected;
}

// Get nodes to display (all or filtered to complete dataflow)
const displayNodes = computed(() => {
  if (!filterMode.value || !filterNodeId.value) {
    return flowGraph.value.nodes;
  }

  const connectedIds = getConnectedNodes(filterNodeId.value, flowGraph.value.edges);
  return flowGraph.value.nodes.filter((n) => connectedIds.has(n.id));
});

// Get edges to display
const displayEdges = computed(() => {
  const nodeIds = new Set(displayNodes.value.map((n) => n.id));
  return flowGraph.value.edges.filter((e) => nodeIds.has(e.source) && nodeIds.has(e.target));
});

// Get upstream nodes (publishers to a topic, or topics that feed a flow)
function getUpstream(nodeId) {
  const upstreamIds = flowGraph.value.edges
    .filter((e) => e.target === nodeId)
    .map((e) => e.source);
  return flowGraph.value.nodes.filter((n) => upstreamIds.includes(n.id));
}

// Get downstream nodes
function getDownstream(nodeId) {
  const downstreamIds = flowGraph.value.edges
    .filter((e) => e.source === nodeId)
    .map((e) => e.target);
  return flowGraph.value.nodes.filter((n) => downstreamIds.includes(n.id));
}

// Get publishers of a topic
function getPublishers(topicId) {
  return getUpstream(topicId);
}

// Get subscribers of a topic
function getSubscribers(topicId) {
  return getDownstream(topicId);
}

async function onSelectNode(node) {
  selectedNode.value = node;
  selectedNodeDetails.value = null;

  // Fetch flow details for non-topic nodes
  if (node.type !== "TOPIC") {
    loadingDetails.value = true;
    try {
      const details = await fetchFlowDetails(node.type, node.name);
      selectedNodeDetails.value = details;

      // Fetch metrics for actions in this flow
      if (details) {
        const actionNames = extractActionNames(details);
        if (actionNames.length > 0) {
          await fetchActionMetrics(actionNames, metricsInterval.value);
        }
      }
    } catch (e) {
      console.error("Failed to fetch flow details:", e);
    } finally {
      loadingDetails.value = false;
    }
  }
}

function extractActionNames(flowDetails) {
  const names = [];
  if (flowDetails.transformActions) {
    for (const action of flowDetails.transformActions) {
      if (action.name) names.push(action.name);
    }
  }
  if (flowDetails.egressAction?.name) {
    names.push(flowDetails.egressAction.name);
  }
  if (flowDetails.timedIngressAction?.name) {
    names.push(flowDetails.timedIngressAction.name);
  }
  return names;
}

async function fetchFlowDetails(flowType, flowName) {
  if (flowType === "TRANSFORM") {
    const result = await getTransformFlowByName(flowName);
    return result?.data?.getTransformFlow || null;
  } else if (flowType === "DATA_SINK") {
    const result = await getDataSinkByName(flowName);
    return result?.data?.getDataSink || null;
  } else if (flowType === "TIMED_DATA_SOURCE") {
    const result = await getTimedDataSources();
    const sources = result?.data?.getAllFlows?.timedDataSource || [];
    return sources.find((s) => s.name === flowName) || null;
  }
  return null;
}

// Format large numbers with K/M suffixes
function formatNumber(num) {
  if (num >= 1000000) {
    return (num / 1000000).toFixed(1) + "M";
  }
  if (num >= 1000) {
    return (num / 1000).toFixed(1) + "K";
  }
  return num.toString();
}

// Format average duration from total ms and count
function formatDuration(totalMs, count) {
  if (count === 0) return "0ms";
  const avgMs = totalMs / count;
  if (avgMs >= 1000) {
    return (avgMs / 1000).toFixed(1) + "s";
  }
  return Math.round(avgMs) + "ms";
}

// Format action tooltip with description and parameters
function formatActionTooltip(action) {
  const lines = [];

  lines.push(`<strong>${action.name}</strong>`);
  lines.push(`<em>${action.type}</em>`);

  if (action.description) {
    lines.push("");
    lines.push(action.description);
  }

  if (action.parameters && Object.keys(action.parameters).length > 0) {
    lines.push("");
    lines.push("<strong>Parameters:</strong>");
    for (const [key, value] of Object.entries(action.parameters)) {
      const displayValue = typeof value === "object" ? JSON.stringify(value) : value;
      lines.push(`&nbsp;&nbsp;${key}: ${displayValue}`);
    }
  }

  if (action.join) {
    lines.push("");
    lines.push("<strong>Join:</strong>");
    if (action.join.metadataKey) lines.push(`&nbsp;&nbsp;Key: ${action.join.metadataKey}`);
    if (action.join.maxAge) lines.push(`&nbsp;&nbsp;Max Age: ${action.join.maxAge}`);
    if (action.join.minNum) lines.push(`&nbsp;&nbsp;Min: ${action.join.minNum}`);
    if (action.join.maxNum) lines.push(`&nbsp;&nbsp;Max: ${action.join.maxNum}`);
  }

  return lines.join("<br/>");
}

function onErrorBadgeClick(node) {
  const flowType = node.type === "DATA_SINK" ? "dataSink" : node.type === "TRANSFORM" ? "transform" : "dataSource";
  const url = router.resolve(`/errors?flowName=${encodeURIComponent(node.name)}&flowType=${flowType}`);
  window.open(url.href, "_blank");
}

function goToPipelineView() {
  if (selectedNode.value) {
    router.push(`/pipeline/${selectedNode.value.type}/${selectedNode.value.name}`);
  }
}

function showDataflow() {
  if (selectedNode.value) {
    filterMode.value = true;
    filterNodeId.value = selectedNode.value.id;
    filterNodeName.value = selectedNode.value.name;
  }
}

function onShowDataflow(node) {
  selectedNode.value = node;
  filterMode.value = true;
  filterNodeId.value = node.id;
  filterNodeName.value = node.name;
}

function clearFilter() {
  filterMode.value = false;
  filterNodeId.value = null;
  filterNodeName.value = null;
}

async function onFlowStateChange(node, newState) {
  const flowType = node.type;
  const flowName = node.name;

  if (newState === "STOPPED") {
    confirm.require({
      group: "stop-flow",
      message: `Are you sure you want to stop <b>${flowName}</b>?`,
      header: "Stop Confirmation",
      icon: "pi pi-exclamation-triangle",
      rejectProps: {
        label: "Cancel",
        severity: "secondary",
        outlined: true,
      },
      acceptProps: {
        label: "Stop",
      },
      accept: async () => {
        notify.info("Stopping Flow", `Stopping <b>${flowName}</b>...`, 3000);
        await setFlowState(flowType, flowName, newState);
        await getAllTopics();
      },
    });
  } else {
    const stateLabel = newState === "RUNNING" ? "Starting" : "Pausing";
    notify.info(`${stateLabel} Flow`, `${stateLabel} <b>${flowName}</b>...`, 3000);
    await setFlowState(flowType, flowName, newState);
    await getAllTopics();
  }
}

async function manualRefresh() {
  await Promise.all([refreshFlowMetrics(), fetchFlowErrors(), fetchQueueMetrics()]);
}

// Map per-flow metrics by flowName for easy lookup
const perFlowMetricsMap = computed(() => {
  const map = {};
  for (const m of perFlowMetrics.value) {
    map[m.flowName] = m;
  }
  return map;
});

// Aggregate queue metrics by flow name
const queueCountsByFlow = computed(() => aggregateQueueCountsByFlow(queueMetrics.value));

// Refresh metrics for all visible flows
async function refreshFlowMetrics() {
  const flowNodes = displayNodes.value.filter((n) => n.type !== "TOPIC");
  if (flowNodes.length === 0) return;

  const flowKeys = flowNodes.map((n) => ({ flowName: n.name, flowType: n.type }));
  await fetchPerFlowMetrics(flowKeys, metricsInterval.value);
}

// Start auto-refresh timer
function startMetricsRefresh() {
  stopMetricsRefresh();
  metricsRefreshTimer = setInterval(async () => {
    await Promise.all([refreshFlowMetrics(), fetchFlowErrors(), fetchQueueMetrics()]);
  }, METRICS_REFRESH_INTERVAL);
}

// Stop auto-refresh timer
function stopMetricsRefresh() {
  if (metricsRefreshTimer) {
    clearInterval(metricsRefreshTimer);
    metricsRefreshTimer = null;
  }
}

// Refresh metrics when displayed nodes change
watch(
  displayNodes,
  async (nodes) => {
    if (nodes.length > 0) {
      await refreshFlowMetrics();
    }
  },
  { immediate: false }
);

// Watch for interval changes to refresh metrics
watch(metricsInterval, async () => {
  await refreshFlowMetrics();
  // Also refresh action metrics if a node is selected
  if (selectedNodeDetails.value) {
    const actionNames = extractActionNames(selectedNodeDetails.value);
    if (actionNames.length > 0) {
      await fetchActionMetrics(actionNames, metricsInterval.value);
    }
  }
});

// Update selectedNode when flowGraph changes (e.g., after state change)
watch(flowGraph, (newGraph) => {
  if (selectedNode.value) {
    const updatedNode = newGraph.nodes.find((n) => n.id === selectedNode.value.id);
    if (updatedNode) {
      selectedNode.value = updatedNode;
    }
  }
});


onMounted(async () => {
  await Promise.all([getAllTopics(), fetchFlowErrors(), fetchQueueMetrics()]);
  startMetricsRefresh();
});

onUnmounted(() => {
  stopMetricsRefresh();
});
</script>

<style scoped>
.system-map-page {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.dataflow-indicator {
  color: var(--text-color-secondary);
  font-size: 0.9rem;
  font-weight: 400;
}

.dataflow-indicator i {
  color: var(--primary-color);
}

.dataflow-indicator strong {
  color: var(--text-color);
}

.map-container {
  flex: 1;
  min-height: 400px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.graph-wrapper {
  flex: 1;
  position: relative;
  min-height: 200px;
}

/* Details panel */
.details-panel {
  flex: 0 0 40%;
  background: var(--surface-card);
  border-top: 1px solid var(--surface-border);
  box-shadow: 0 -4px 12px rgba(0, 0, 0, 0.1);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 0.75rem 1rem;
  border-bottom: 1px solid var(--surface-border);
  background: var(--surface-ground);
}

.panel-header-left {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.panel-title {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.panel-node-name {
  font-weight: 600;
  font-size: 1.1rem;
}

.panel-node-type {
  color: var(--text-color-secondary);
  font-size: 0.85rem;
}

.panel-state-badge {
  padding: 0.15rem 0.5rem;
  border-radius: 4px;
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: uppercase;
}

.panel-state-badge.state-running {
  background: var(--green-100);
  color: var(--green-700);
}

.panel-state-badge.state-paused {
  background: var(--yellow-100);
  color: var(--yellow-700);
}

.panel-state-badge.state-stopped {
  background: var(--red-100);
  color: var(--red-700);
}

.panel-status-badge {
  padding: 0.15rem 0.5rem;
  border-radius: 4px;
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: uppercase;
  display: flex;
  align-items: center;
  gap: 0.25rem;
}

.panel-status-badge.invalid {
  background: var(--red-100);
  color: var(--red-700);
}

.panel-status-badge.plugin {
  background: var(--yellow-100);
  color: var(--yellow-700);
}

.panel-plugin {
  color: var(--text-color-secondary);
  font-size: 0.8rem;
  display: flex;
  align-items: center;
  gap: 0.35rem;
}

.panel-close {
  background: none;
  border: none;
  cursor: pointer;
  padding: 0.5rem;
  color: var(--text-color-secondary);
  border-radius: 4px;
  transition: all 0.15s ease;
}

.panel-close:hover {
  background: var(--surface-hover);
  color: var(--text-color);
}

.panel-content {
  flex: 1;
  overflow-y: auto;
  padding: 1rem;
}

.panel-actions {
  display: flex;
  gap: 0.5rem;
}

.node-connections {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.connection-group h6 {
  margin: 0 0 0.5rem 0;
  color: var(--text-color-secondary);
  font-size: 0.85rem;
  font-weight: 500;
}

.connection-list {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.connection-chip {
  background: var(--surface-ground);
  border: 1px solid var(--surface-border);
  padding: 0.25rem 0.75rem;
  border-radius: 16px;
  font-size: 0.85rem;
  cursor: pointer;
  transition: all 0.15s ease;
}

.connection-chip:hover {
  background: var(--primary-100);
  border-color: var(--primary-color);
  color: var(--primary-color);
}


/* Loading indicator */
.loading-indicator {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  color: var(--text-color-secondary);
}

.loading-indicator .pi-spinner {
  font-size: 1.2rem;
}

/* Action chain styles */
.action-chain {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.action-chain-header {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.action-chain-title {
  font-weight: 600;
  font-size: 1rem;
}

.action-count {
  color: var(--text-color-secondary);
  font-size: 0.9rem;
}

.action-list {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
}

.action-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  background: var(--surface-ground);
  border: 1px solid var(--surface-border);
  border-radius: 6px;
  padding: 0.75rem 1rem;
  min-width: 220px;
}

.action-arrow {
  color: var(--text-color-secondary);
  display: flex;
  align-items: center;
  padding: 0 0.5rem;
}

.action-arrow .pi {
  font-size: 1.1rem;
}

.action-queue-bubble {
  background: var(--surface-0);
  border: 1px solid var(--surface-400);
  border-radius: 12px;
  padding: 0.25rem 0.6rem;
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--text-color);
  white-space: nowrap;
  cursor: default;
}

.action-details {
  flex: 1;
  min-width: 0;
}

.action-name {
  font-weight: 500;
  font-size: 1rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.action-type {
  color: var(--text-color-secondary);
  font-size: 0.85rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.action-join-badge {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  background: var(--blue-100);
  color: var(--blue-700);
  padding: 0.25rem 0.5rem;
  border-radius: 4px;
  font-size: 0.8rem;
  font-weight: 500;
  flex-shrink: 0;
}

.action-join-badge .pi {
  font-size: 0.75rem;
}

/* Action metrics display */
.action-metrics {
  display: flex;
  gap: 0.75rem;
  margin-top: 0.35rem;
  font-size: 0.8rem;
  color: var(--text-color-secondary);
}

.action-metrics .metric {
  display: flex;
  align-items: center;
  gap: 0.25rem;
}

.action-metrics .metric .pi {
  font-size: 0.75rem;
}
</style>
