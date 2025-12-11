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
  <div class="pipeline-visualization-page">
    <ConfirmDialog group="stop-flow">
      <template #message="slotProps">
        <span class="p-confirm-dialog-icon pi pi-exclamation-triangle" />
        <span class="p-confirm-dialog-message" v-html="slotProps.message.message" />
      </template>
    </ConfirmDialog>
    <PageHeader>
      <template #header>
        <div class="d-flex align-items-center">
          <h2 class="mb-0">System Map: Detail</h2>
          <template v-if="focusedFlowName">
            <span class="ml-3 text-muted">{{ focusedFlowName }}</span>
            <button class="change-flow-button ml-2" @click="clearFocusedFlow" v-tooltip.bottom="'Change flow'">
              <i class="pi pi-pencil" />
            </button>
          </template>
        </div>
      </template>
      <template #default>
        <MetricsControls
          v-if="focusedFlowName"
          v-model="metricsInterval"
          @refresh="manualRefresh"
        />
      </template>
    </PageHeader>

    <ProgressBar v-if="showLoading" mode="indeterminate" style="height: 0.5em" />

    <!-- Flow not found error -->
    <div v-if="!showLoading && flowNotFound" class="p-4 text-center">
      <i class="pi pi-exclamation-triangle text-warning" style="font-size: 3rem" />
      <h4 class="mt-3">Flow Not Found</h4>
      <p class="text-muted">
        The flow <strong>{{ focusedFlowName }}</strong> (type: {{ focusedFlowType }}) was not found.
      </p>
      <p class="text-muted">It may have been deleted or the URL may be incorrect.</p>
      <Button label="Select a Flow" icon="pi pi-list" class="mt-2" @click="clearFocusedFlow" />
    </div>

    <div v-if="!showLoading && !focusedFlowName && !flowNotFound" class="p-4 text-center">
      <h4>Select an entity to visualize</h4>
      <p class="text-muted">Choose a data source, transform, data sink, or topic from the lists below.</p>

      <div class="flow-selection-grid mt-4">
        <div class="flow-selection-column">
          <h5>Data Sources</h5>
          <div ref="scrollContainer1" class="listbox-scroll-container">
            <Listbox
              v-model="selectedFlow"
              :options="dataSources"
              option-label="label"
              option-group-label="label"
              option-group-children="items"
              class="w-full"
              list-style="max-height: calc(100vh - 300px)"
              @change="onFlowSelected"
            />
          </div>
        </div>
        <div class="flow-selection-column">
          <h5>Transforms</h5>
          <div ref="scrollContainer2" class="listbox-scroll-container">
            <Listbox
              v-model="selectedFlow"
              :options="transforms"
              option-label="name"
              class="w-full"
              list-style="max-height: calc(100vh - 300px)"
              @change="onFlowSelected"
            />
          </div>
        </div>
        <div class="flow-selection-column">
          <h5>Data Sinks</h5>
          <div ref="scrollContainer3" class="listbox-scroll-container">
            <Listbox
              v-model="selectedFlow"
              :options="dataSinks"
              option-label="name"
              class="w-full"
              list-style="max-height: calc(100vh - 300px)"
              @change="onFlowSelected"
            />
          </div>
        </div>
        <div class="flow-selection-column">
          <h5>Topics</h5>
          <div ref="scrollContainer4" class="listbox-scroll-container">
            <Listbox
              v-model="selectedFlow"
              :options="topicsList"
              option-label="name"
              class="w-full"
              list-style="max-height: calc(100vh - 300px)"
              @change="onFlowSelected"
            />
          </div>
        </div>
      </div>
    </div>

    <div v-if="!showLoading && focusedFlowName && !flowNotFound" class="pipeline-container" :class="{ 'panel-open': selectedNode }">
      <PipelineGraph
        ref="pipelineGraphRef"
        :nodes="visibleNodes"
        :edges="visibleEdges"
        :focused-node-id="focusedNodeId"
        :flow-metrics="perFlowMetricsMap"
        :error-counts="errorsByFlow"
        @select-node="onSelectNode"
        @focus-node="onFocusNode"
        @expand-upstream="expandUpstream"
        @expand-downstream="expandDownstream"
        @flow-state-change="onFlowStateChange"
        @error-badge-click="onErrorBadgeClick"
      />

      <!-- Bottom panel for selected node details -->
      <div v-if="selectedNode" ref="detailsPanelRef" class="details-panel">
        <div class="panel-header">
          <div class="panel-header-left">
            <div class="panel-title">
              <span class="panel-node-name">{{ selectedNode.name }}</span>
              <span class="panel-node-type">{{ formatNodeType(selectedNode.type) }}</span>
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
          <p v-if="selectedNode.type === 'TOPIC'" class="text-muted">
            Topics connect publishers to subscribers. Click on connected flows to see their details.
          </p>

          <div v-else-if="loadingDetails" class="loading-indicator">
            <i class="pi pi-spin pi-spinner" />
            <span>Loading flow details...</span>
          </div>

          <div v-else-if="selectedNodeDetails" class="flow-details">
            <p v-if="selectedNodeDetails.description" class="flow-description">
              {{ selectedNodeDetails.description }}
            </p>

            <div v-if="selectedNodeActions.length > 0" class="action-chain">
            <div class="action-chain-header">
              <span class="action-chain-title">Action Chain</span>
              <span class="action-count">{{ selectedNodeActions.length }} action{{ selectedNodeActions.length !== 1 ? 's' : '' }}</span>
            </div>
            <div class="action-list">
              <template v-for="(action, index) in selectedNodeActions" :key="action.name">
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

            <p v-if="selectedNodeActions.length === 0" class="text-muted">
              This flow has no actions configured.
            </p>
          </div>

          <p v-else class="text-muted">
            No action details available for this flow type.
          </p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
// ABOUTME: Pipeline visualization page with progressive expansion.
// ABOUTME: Shows a focused flow with expandable upstream and downstream connections.

import PageHeader from "@/components/PageHeader.vue";
import ProgressBar from "@/components/deprecatedPrimeVue/ProgressBar.vue";
import PipelineGraph from "@/components/pipeline/PipelineGraph.vue";
import { formatTypeLong } from "@/components/pipeline/useGraphStyles";
import useFlowErrors from "@/components/pipeline/useFlowErrors";
import useTopics from "@/composables/useTopics";
import useFlows from "@/composables/useFlows";
import useFlowQueryBuilder from "@/composables/useFlowQueryBuilder";
import useDataSource from "@/composables/useDataSource";
import useActionMetrics from "@/composables/useActionMetrics";
import usePerFlowMetrics from "@/composables/usePerFlowMetrics";
import useNotifications from "@/composables/useNotifications";
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";

import Button from "primevue/button";
import ConfirmDialog from "primevue/confirmdialog";
import Listbox from "primevue/listbox";
import { useConfirm } from "primevue/useconfirm";
import MetricsControls from "@/components/pipeline/MetricsControls.vue";

import _ from "lodash";

const route = useRoute();
const router = useRouter();
const confirm = useConfirm();
const { topics, getAllTopics, loading, loaded } = useTopics();
const { setFlowState } = useFlows();
const { getTransformFlowByName, getDataSinkByName } = useFlowQueryBuilder();
const { getTimedDataSources } = useDataSource();
const { data: actionMetrics, fetch: fetchActionMetrics } = useActionMetrics();
const { data: perFlowMetrics, fetch: fetchPerFlowMetrics } = usePerFlowMetrics();
const { errorsByFlow, fetch: fetchFlowErrors } = useFlowErrors();
const notify = useNotifications();

const focusedFlowName = ref(null);
const focusedFlowType = ref(null);
const expandedUpstream = ref(new Set());
const expandedDownstream = ref(new Set());
const selectedFlow = ref(null);
const selectedNode = ref(null);

// Scroll container refs for shadow indicators
const scrollContainer1 = ref(null);
const scrollContainer2 = ref(null);
const scrollContainer3 = ref(null);
const scrollContainer4 = ref(null);
const selectedNodeDetails = ref(null);
const loadingDetails = ref(false);
const refreshing = ref(false);
const pipelineGraphRef = ref(null);
const detailsPanelRef = ref(null);

// Metrics interval state
const metricsInterval = ref(60);

// Auto-refresh timer
let metricsRefreshTimer = null;
const METRICS_REFRESH_INTERVAL = 30000; // 30 seconds

// Map action metrics by name for easy lookup
const actionMetricsMap = computed(() => {
  const map = {};
  for (const m of actionMetrics.value) {
    map[m.actionName] = m;
  }
  return map;
});

// Map per-flow metrics by flowName for easy lookup
const perFlowMetricsMap = computed(() => {
  const map = {};
  for (const m of perFlowMetrics.value) {
    map[m.flowName] = m;
  }
  return map;
});

// Show loading only on initial load, not during refreshes
const showLoading = computed(() => !loaded.value);

// Check if the focused flow exists in the graph
const flowNotFound = computed(() => {
  if (!focusedFlowName.value || !focusedFlowType.value) return false;
  if (!loaded.value) return false;
  const nodeId = `${focusedFlowType.value}:${focusedFlowName.value}`;
  return !flowGraph.value.nodes[nodeId];
});

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

// Format node type for display
function formatNodeType(type) {
  return formatTypeLong(type);
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
    if (action.join.minNum) lines.push(`&nbsp;&nbsp;Min: ${action.join.minNum}`);
    if (action.join.maxNum) lines.push(`&nbsp;&nbsp;Max: ${action.join.maxNum}`);
    if (action.join.maxAge) lines.push(`&nbsp;&nbsp;Max Age: ${action.join.maxAge}`);
  }

  return lines.join("<br>");
}

// Computed: focused node ID
const focusedNodeId = computed(() => {
  if (!focusedFlowName.value || !focusedFlowType.value) return null;
  return `${focusedFlowType.value}:${focusedFlowName.value}`;
});

// Build a graph structure from topics
const flowGraph = computed(() => {
  if (!topics.value || topics.value.length === 0) return { nodes: {}, edges: [] };

  const nodes = {};
  const edges = [];

  for (const topic of topics.value) {
    // Add topic node
    const topicId = `TOPIC:${topic.name}`;
    if (!nodes[topicId]) {
      nodes[topicId] = {
        id: topicId,
        name: topic.name,
        type: "TOPIC",
        state: null,
      };
    }

    // Add publisher nodes and edges
    for (const pub of topic.publishers || []) {
      const pubId = `${pub.type}:${pub.name}`;
      if (!nodes[pubId]) {
        nodes[pubId] = {
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
      edges.push({
        source: pubId,
        target: topicId,
        condition: pub.condition,
      });
    }

    // Add subscriber nodes and edges
    for (const sub of topic.subscribers || []) {
      const subId = `${sub.type}:${sub.name}`;
      if (!nodes[subId]) {
        nodes[subId] = {
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
      edges.push({
        source: topicId,
        target: subId,
        condition: sub.condition,
      });
    }
  }

  return { nodes, edges };
});

// Extract flows for selection lists
const dataSources = computed(() => {
  const sources = Object.values(flowGraph.value.nodes).filter((n) =>
    ["REST_DATA_SOURCE", "TIMED_DATA_SOURCE", "ON_ERROR_DATA_SOURCE"].includes(n.type)
  );

  const grouped = _.groupBy(sources, "type");
  const sortByName = (items) => _.sortBy(items, (s) => s.name.toLowerCase());
  return [
    { label: "REST", items: sortByName((grouped["REST_DATA_SOURCE"] || []).map((s) => ({ ...s, label: s.name }))) },
    { label: "Timed", items: sortByName((grouped["TIMED_DATA_SOURCE"] || []).map((s) => ({ ...s, label: s.name }))) },
    { label: "On Error", items: sortByName((grouped["ON_ERROR_DATA_SOURCE"] || []).map((s) => ({ ...s, label: s.name }))) },
  ].filter((g) => g.items.length > 0);
});

const transforms = computed(() => {
  return _.sortBy(
    Object.values(flowGraph.value.nodes).filter((n) => n.type === "TRANSFORM"),
    (n) => n.name.toLowerCase()
  );
});

const dataSinks = computed(() => {
  return _.sortBy(
    Object.values(flowGraph.value.nodes).filter((n) => n.type === "DATA_SINK"),
    (n) => n.name.toLowerCase()
  );
});

const topicsList = computed(() => {
  return _.sortBy(
    Object.values(flowGraph.value.nodes).filter((n) => n.type === "TOPIC"),
    (n) => n.name.toLowerCase()
  );
});

// Calculate visible nodes based on focused node and expansion
const visibleNodes = computed(() => {
  if (!focusedNodeId.value) return [];

  const graph = flowGraph.value;
  const visible = new Set();
  const queue = [focusedNodeId.value];
  visible.add(focusedNodeId.value);

  // Always include immediate connections (1 hop)
  const immediateUpstream = getUpstreamNodes(focusedNodeId.value, graph);
  const immediateDownstream = getDownstreamNodes(focusedNodeId.value, graph);

  for (const nodeId of immediateUpstream) {
    visible.add(nodeId);
  }
  for (const nodeId of immediateDownstream) {
    visible.add(nodeId);
  }

  // Add nodes expanded upstream (show their upstream neighbors)
  for (const expandedId of expandedUpstream.value) {
    visible.add(expandedId);
    for (const nodeId of getUpstreamNodes(expandedId, graph)) {
      visible.add(nodeId);
    }
  }

  // Add nodes expanded downstream (show their downstream neighbors)
  for (const expandedId of expandedDownstream.value) {
    visible.add(expandedId);
    for (const nodeId of getDownstreamNodes(expandedId, graph)) {
      visible.add(nodeId);
    }
  }

  // Convert to array with position info
  return Array.from(visible).map((id) => {
    const node = graph.nodes[id];
    const depth = calculateDepthFromFocus(id, focusedNodeId.value, graph);
    const canExpandUpstream = hasMoreUpstream(id, visible, graph);
    const canExpandDownstream = hasMoreDownstream(id, visible, graph);

    return {
      ...node,
      depth,
      canExpandUpstream,
      canExpandDownstream,
    };
  });
});

// Calculate visible edges
const visibleEdges = computed(() => {
  const visibleNodeIds = new Set(visibleNodes.value.map((n) => n.id));
  return flowGraph.value.edges.filter((e) => visibleNodeIds.has(e.source) && visibleNodeIds.has(e.target));
});

// Helper: get upstream nodes (what connects TO this node)
function getUpstreamNodes(nodeId, graph) {
  const upstream = [];
  for (const edge of graph.edges) {
    if (edge.target === nodeId && graph.nodes[edge.source]) {
      upstream.push(edge.source);
    }
  }
  return upstream;
}

// Helper: get downstream nodes (what this node connects TO)
function getDownstreamNodes(nodeId, graph) {
  const downstream = [];
  for (const edge of graph.edges) {
    if (edge.source === nodeId && graph.nodes[edge.target]) {
      downstream.push(edge.target);
    }
  }
  return downstream;
}

// Helper: calculate depth from focused node (negative = upstream, positive = downstream)
function calculateDepthFromFocus(nodeId, focusId, graph) {
  if (nodeId === focusId) return 0;

  // First, try downstream BFS (positive depth)
  const downstreamDepth = bfsDepth(focusId, nodeId, graph, true);
  if (downstreamDepth !== null) return downstreamDepth;

  // Then, try upstream BFS (negative depth)
  const upstreamDepth = bfsDepth(focusId, nodeId, graph, false);
  if (upstreamDepth !== null) return -upstreamDepth;

  return 0; // Shouldn't happen for visible nodes
}

// BFS to find depth following edges in one direction only
function bfsDepth(startId, targetId, graph, downstream) {
  const visited = new Set();
  const queue = [{ id: startId, depth: 0 }];
  visited.add(startId);

  while (queue.length > 0) {
    const { id, depth } = queue.shift();

    const neighbors = downstream ? getDownstreamNodes(id, graph) : getUpstreamNodes(id, graph);
    for (const neighbor of neighbors) {
      if (neighbor === targetId) return depth + 1;
      if (!visited.has(neighbor)) {
        visited.add(neighbor);
        queue.push({ id: neighbor, depth: depth + 1 });
      }
    }
  }

  return null; // Not found in this direction
}

// Helper: check if node has more upstream connections not in visible set
function hasMoreUpstream(nodeId, visibleSet, graph) {
  const upstream = getUpstreamNodes(nodeId, graph);
  return upstream.some((id) => !visibleSet.has(id));
}

// Helper: check if node has more downstream connections not in visible set
function hasMoreDownstream(nodeId, visibleSet, graph) {
  const downstream = getDownstreamNodes(nodeId, graph);
  return downstream.some((id) => !visibleSet.has(id));
}

// Event handlers
function onFlowSelected(event) {
  if (event.value) {
    router.push(`/pipeline/${event.value.type}/${event.value.name}`);
  }
}

async function onSelectNode(node) {
  // Show details in bottom panel
  selectedNode.value = node;
  selectedNodeDetails.value = null;

  // Fetch full details for non-topic nodes
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
    // Timed data sources come from getAllFlows, find matching one
    const result = await getTimedDataSources();
    const sources = result?.data?.getAllFlows?.timedDataSource || [];
    return sources.find((s) => s.name === flowName) || null;
  }
  // REST and On-Error data sources don't have actions to display
  return null;
}

function onFocusNode(node) {
  // If already focused on this node, recollapse to original view
  if (node.id === focusedNodeId.value) {
    expandedUpstream.value = new Set();
    expandedDownstream.value = new Set();
    selectedNode.value = null;
    return;
  }
  // Navigate to make this node the focus
  router.push(`/pipeline/${node.type}/${node.name}`);
}

function expandUpstream(nodeId) {
  expandedUpstream.value.add(nodeId);
  // Force reactivity
  expandedUpstream.value = new Set(expandedUpstream.value);
}

function expandDownstream(nodeId) {
  expandedDownstream.value.add(nodeId);
  // Force reactivity
  expandedDownstream.value = new Set(expandedDownstream.value);
}

function clearFocusedFlow() {
  router.push("/pipeline");
}

async function manualRefresh() {
  await refreshFlowMetrics();
  if (selectedNodeDetails.value) {
    const actionNames = extractActionNames(selectedNodeDetails.value);
    if (actionNames.length > 0) {
      await fetchActionMetrics(actionNames, metricsInterval.value);
    }
  }
}

function onErrorBadgeClick(node) {
  const flowType = node.type === "DATA_SINK" ? "dataSink" : node.type === "TRANSFORM" ? "transform" : "dataSource";
  const url = router.resolve(`/errors?flowName=${encodeURIComponent(node.name)}&flowType=${flowType}`);
  window.open(url.href, "_blank");
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

// Refresh all visible flow metrics
async function refreshFlowMetrics() {
  const flowKeys = visibleNodes.value
    .filter((n) => n.type !== "TOPIC")
    .map((n) => ({ flowType: n.type, flowName: n.name }));

  if (flowKeys.length > 0) {
    await fetchPerFlowMetrics(flowKeys, metricsInterval.value);
  }
}

// Start auto-refresh timer
function startMetricsRefresh() {
  stopMetricsRefresh();
  metricsRefreshTimer = setInterval(async () => {
    await Promise.all([refreshFlowMetrics(), fetchFlowErrors()]);
    // Also refresh action metrics if a node is selected
    if (selectedNodeDetails.value) {
      const actionNames = extractActionNames(selectedNodeDetails.value);
      if (actionNames.length > 0) {
        await fetchActionMetrics(actionNames, metricsInterval.value);
      }
    }
  }, METRICS_REFRESH_INTERVAL);
}

// Stop auto-refresh timer
function stopMetricsRefresh() {
  if (metricsRefreshTimer) {
    clearInterval(metricsRefreshTimer);
    metricsRefreshTimer = null;
  }
}

// Watch route params
watch(
  () => route.params,
  (params) => {
    if (params.flowType && params.flowName) {
      focusedFlowType.value = params.flowType;
      focusedFlowName.value = params.flowName;
      expandedUpstream.value = new Set();
      expandedDownstream.value = new Set();
    } else {
      focusedFlowType.value = null;
      focusedFlowName.value = null;
    }
  },
  { immediate: true }
);

// Watch for interval changes to refresh metrics
watch(metricsInterval, async () => {
  await refreshFlowMetrics();
  if (selectedNodeDetails.value) {
    const actionNames = extractActionNames(selectedNodeDetails.value);
    if (actionNames.length > 0) {
      await fetchActionMetrics(actionNames, metricsInterval.value);
    }
  }
});

// Watch for visible nodes changes to fetch their metrics
watch(
  visibleNodes,
  async (nodes) => {
    if (nodes.length > 0) {
      await refreshFlowMetrics();
    }
  },
  { immediate: false }
);

// Update selectedNode when flowGraph changes (e.g., after state change)
watch(flowGraph, (newGraph) => {
  if (selectedNode.value) {
    const updatedNode = newGraph.nodes[selectedNode.value.id];
    if (updatedNode) {
      selectedNode.value = updatedNode;
    }
  }
});

// Update scroll shadow indicators for a listbox container
function updateScrollShadows(container) {
  if (!container) return;
  const scrollEl = container.querySelector(".p-listbox-list-wrapper");
  if (!scrollEl) return;

  const hasScrollTop = scrollEl.scrollTop > 0;
  const hasScrollBottom = scrollEl.scrollHeight - scrollEl.scrollTop - scrollEl.clientHeight > 1;

  container.classList.toggle("scroll-top", hasScrollTop);
  container.classList.toggle("scroll-bottom", hasScrollBottom);
}

// Setup scroll listeners for all listbox containers
function setupScrollListeners() {
  const containers = [scrollContainer1.value, scrollContainer2.value, scrollContainer3.value, scrollContainer4.value];

  for (const container of containers) {
    if (!container) continue;
    const scrollEl = container.querySelector(".p-listbox-list-wrapper");
    if (scrollEl) {
      // Initial state
      updateScrollShadows(container);
      // Listen for scroll
      scrollEl.addEventListener("scroll", () => updateScrollShadows(container));
    }
  }
}

onMounted(async () => {
  await Promise.all([getAllTopics(), fetchFlowErrors()]);
  startMetricsRefresh();
});

// Setup scroll shadows when the flow selection becomes visible
watch(
  () => !loading.value && !focusedFlowName.value,
  (showPicker) => {
    if (showPicker) {
      // Wait for DOM to update, then setup listeners
      setTimeout(setupScrollListeners, 50);
    }
  },
  { immediate: true }
);

// Adjust viewport when details panel opens/closes
watch(selectedNode, (node) => {
  nextTick(() => {
    if (node && detailsPanelRef.value) {
      const panelHeight = detailsPanelRef.value.offsetHeight;
      pipelineGraphRef.value?.setDetailPanelOffset(panelHeight);
    } else {
      pipelineGraphRef.value?.setDetailPanelOffset(0);
    }
  });
});

onUnmounted(() => {
  stopMetricsRefresh();
});
</script>

<style scoped>
.pipeline-visualization-page {
  height: 100%;
  display: flex;
  flex-direction: column;
}

/* Change flow button in header */
.change-flow-button {
  background: none;
  border: none;
  cursor: pointer;
  padding: 0.25rem;
  color: var(--text-color-secondary);
  border-radius: 4px;
  transition: all 0.15s ease;
  display: flex;
  align-items: center;
}

.change-flow-button:hover {
  background: var(--surface-hover);
  color: var(--primary-color);
}

.change-flow-button .pi {
  font-size: 0.85rem;
}

.pipeline-container {
  flex: 1;
  min-height: 500px;
  position: relative;
  overflow: hidden;
}

/* Flow picker container */
.flow-picker-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 1rem;
  min-height: 0;
  overflow: hidden;
}

.flow-picker-header {
  text-align: center;
  flex-shrink: 0;
}

.flow-picker-header h4 {
  margin-bottom: 0.25rem;
}

.flow-picker-header p {
  margin-bottom: 1rem;
}

.flow-selection-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 1.5rem;
  max-width: 1400px;
  margin: 0 auto;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.flow-selection-column {
  text-align: left;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.flow-selection-column h5 {
  margin-bottom: 0.5rem;
  color: var(--text-color-secondary);
  flex-shrink: 0;
}

/* Scroll container with shadow indicators */
.listbox-scroll-container {
  position: relative;
}

.listbox-scroll-container::before,
.listbox-scroll-container::after {
  content: "";
  position: absolute;
  left: 0;
  right: 0;
  height: 0;
  pointer-events: none;
  z-index: 1;
  transition: box-shadow 0.2s ease;
}

.listbox-scroll-container::before {
  top: 0;
}

.listbox-scroll-container::after {
  bottom: 0;
}

.listbox-scroll-container.scroll-top::before {
  box-shadow: inset 0 12px 12px -6px rgba(0, 0, 0, 0.35);
  height: 12px;
}

.listbox-scroll-container.scroll-bottom::after {
  box-shadow: inset 0 -12px 12px -6px rgba(0, 0, 0, 0.35);
  height: 12px;
}

.flow-listbox {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.flow-listbox :deep(.p-listbox) {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.flow-listbox :deep(.p-listbox-list-wrapper) {
  flex: 1;
  overflow-y: auto;
}

/* Bottom details panel */
.details-panel {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  background: var(--surface-card);
  border-top: 1px solid var(--surface-border);
  box-shadow: 0 -4px 12px rgba(0, 0, 0, 0.1);
  max-height: 45%;
  min-height: 200px;
  display: flex;
  flex-direction: column;
  z-index: 10;
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

/* Flow details */
.flow-details {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.flow-description {
  margin: 0;
  color: var(--text-color);
  font-size: 0.95rem;
  line-height: 1.4;
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
