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
<!-- ABOUTME: Visualizes the historical flow of a DeltaFile through the system as a graph. -->
<!-- ABOUTME: Shows flows and topics as nodes with edges representing data flow. -->

<template>
  <component :is="hideHeader ? 'div' : CollapsiblePanel" :header="hideHeader ? undefined : 'Flow Graph'" class="flow-graph-panel">
    <div ref="containerRef" class="graph-container">
      <svg ref="svgRef" :width="svgWidth" :height="svgHeight" :viewBox="viewBoxString">
        <!-- Arrow marker definition -->
        <defs>
          <marker
            id="deltafile-arrowhead"
            markerWidth="10"
            markerHeight="7"
            refX="0"
            refY="3.5"
            orient="auto"
          >
            <polygon points="0 0, 10 3.5, 0 7" fill="var(--surface-500)" />
          </marker>
        </defs>

        <!-- Edges -->
        <g class="edges">
          <g
            v-for="edge in layoutEdges"
            :key="`${edge.source}-${edge.target}`"
            class="edge-group"
            @mouseenter="showEdgeTooltip($event, edge)"
            @mouseleave="hideTooltip"
          >
            <!-- Invisible wider path for easier hit detection -->
            <path :d="edge.path" class="edge-hit-area" />
            <!-- Visible path -->
            <path
              :d="edge.path"
              class="edge-path"
              marker-end="url(#deltafile-arrowhead)"
            />
          </g>
        </g>

        <!-- Nodes -->
        <g class="nodes">
          <g
            v-for="node in layoutNodes"
            :key="node.id"
            :transform="`translate(${node.x - nodeWidth / 2}, ${node.y - nodeHeight / 2})`"
            :class="['node-group', { 'node-clickable': nodeIsClickable(node) }]"
            @click="onNodeClick(node)"
          >
            <!-- Main node rectangle (extends to include output panel if present) -->
            <rect
                :width="flowHasOutput(node) ? nodeWidth + outputBoxWidth : nodeWidth"
                :height="nodeHeight"
                rx="6"
                ry="6"
                :fill="getNodeColors(node).fill"
                :stroke="getNodeColors(node).stroke"
                stroke-width="1.5"
              />
              <!-- Output panel background (flat left edge, rounded right edge) -->
              <path
                v-if="flowHasOutput(node)"
                :d="`M ${nodeWidth - 1} 2 L ${nodeWidth + outputBoxWidth - 6} 2 Q ${nodeWidth + outputBoxWidth - 2} 2 ${nodeWidth + outputBoxWidth - 2} 6 L ${nodeWidth + outputBoxWidth - 2} ${nodeHeight - 6} Q ${nodeWidth + outputBoxWidth - 2} ${nodeHeight - 2} ${nodeWidth + outputBoxWidth - 6} ${nodeHeight - 2} L ${nodeWidth - 1} ${nodeHeight - 2} Z`"
                class="output-panel-bg"
                :fill="getOutputPanelColor(node.type, node.state)"
                @click.stop="openFlowOutput(node)"
              />
              <text
                :x="nodeWidth / 2"
                :y="nodeHeight / 2 - 6"
                text-anchor="middle"
                dominant-baseline="middle"
                class="node-label"
                :class="{ truncated: isLabelTruncated(node.name) }"
                @mouseenter="isLabelTruncated(node.name) && showNodeTooltip($event, node.name)"
                @mouseleave="isLabelTruncated(node.name) && hideTooltip()"
              >
                {{ truncateLabel(node.name) }}
              </text>
              <text
                :x="nodeWidth / 2"
                :y="nodeHeight / 2 + 8"
                text-anchor="middle"
                dominant-baseline="middle"
                class="node-state"
                :fill="getStateTextColor(node.state)"
              >
                {{ node.state }}
              </text>
              <!-- Type icon (lower left corner) -->
              <text
                class="node-type-icon fas"
                :x="12"
                :y="nodeHeight - 6"
                text-anchor="middle"
              >{{ getTypeIcon(node.type) }}</text>
              <!-- Elapsed time (lower right corner) -->
              <text
                class="node-elapsed"
                :x="nodeWidth - 8"
                :y="nodeHeight - 6"
                text-anchor="end"
              >{{ node.elapsed }}</text>
              <!-- Output icon (centered in output panel) -->
              <text
                v-if="flowHasOutput(node)"
                class="node-output-icon fas"
                :x="nodeWidth + outputBoxWidth / 2"
                :y="nodeHeight / 2 + 1"
                text-anchor="middle"
                dominant-baseline="middle"
                @click.stop="openFlowOutput(node)"
              >&#xf15b;</text>
          </g>
        </g>
      </svg>

      <!-- Zoom controls -->
      <div class="zoom-controls">
        <button v-if="userHasInteracted" class="zoom-btn" title="Reset view" @click="resetView">
          <i class="fas fa-crosshairs" />
        </button>
        <button class="zoom-btn" title="Zoom in" @click="zoomIn">
          <i class="pi pi-plus" />
        </button>
        <button class="zoom-btn" title="Zoom out" @click="zoomOut">
          <i class="pi pi-minus" />
        </button>
      </div>

      <!-- Legend (only show types present in graph) -->
      <div class="graph-legend">
        <div v-if="presentTypes.has('REST_DATA_SOURCE')" class="legend-item"><i class="fas fa-file-import" /> REST Data Source</div>
        <div v-if="presentTypes.has('TIMED_DATA_SOURCE')" class="legend-item"><i class="fas fa-clock" /> Timed Data Source</div>
        <div v-if="presentTypes.has('ON_ERROR_DATA_SOURCE')" class="legend-item"><i class="fas fa-triangle-exclamation" /> Error Data Source</div>
        <div v-if="presentTypes.has('TRANSFORM')" class="legend-item"><i class="fas fa-project-diagram" /> Transform</div>
        <div v-if="presentTypes.has('DATA_SINK')" class="legend-item"><i class="fas fa-file-export" /> Data Sink</div>
      </div>

      <!-- Custom tooltip -->
      <div
        v-if="tooltip.visible"
        class="graph-tooltip"
        :style="{ left: tooltip.x + 'px', top: tooltip.y + 'px' }"
      >
        {{ tooltip.text }}
      </div>
    </div>

    <!-- Flow output dialog -->
    <FlowOutputDialog ref="flowOutputDialogRef" :flow="selectedFlow" />
    <!-- Error viewer dialog -->
    <ErrorViewerDialog v-model:visible="errorViewer.visible" :action="errorViewer.action" />
  </component>
</template>

<script setup>
import CollapsiblePanel from "@/components/CollapsiblePanel.vue";
import FlowOutputDialog from "@/components/DeltaFileViewer/FlowOutputDialog.vue";
import ErrorViewerDialog from "@/components/errors/ErrorViewerDialog.vue";
import { nodeColorsByType, getFlowNodeColors } from "@/components/pipeline/useGraphStyles";
import useUtilFunctions from "@/composables/useUtilFunctions";
import { computed, onMounted, onUnmounted, reactive, ref, watch } from "vue";

const { duration } = useUtilFunctions();

const props = defineProps({
  deltaFileData: {
    type: Object,
    required: true,
  },
  hideHeader: {
    type: Boolean,
    default: false,
  },
});

// Layout constants
const nodeWidth = 160;
const nodeHeight = 40;
const outputBoxWidth = 32; // Width of the output panel on flow nodes
const horizontalGap = 100;
const verticalGap = 30;
const padding = 40;

// Refs
const containerRef = ref(null);
const svgRef = ref(null);
const svgWidth = ref(800);
const svgHeight = ref(300);

// Pan/zoom state
const panOffset = ref({ x: 0, y: 0 });
const zoomLevel = ref(1);
const userHasInteracted = ref(false);
const isDragging = ref(false);
const dragStart = ref({ x: 0, y: 0 });

// Tooltip state
const tooltip = ref({ visible: false, x: 0, y: 0, text: "" });

const showEdgeTooltip = (event, edge) => {
  if (!edge.label) return;
  const container = containerRef.value;
  if (!container) return;
  const rect = container.getBoundingClientRect();
  tooltip.value = {
    visible: true,
    x: event.clientX - rect.left,
    y: event.clientY - rect.top - 30,
    text: `Topic: ${edge.label}`,
  };
};

const hideTooltip = () => {
  tooltip.value.visible = false;
};

// Flow output dialog
const flowOutputDialogRef = ref(null);
const selectedFlow = ref(null);

const flowHasOutput = (node) => {
  const flow = getFlowByNumber(node.number);
  if (!flow) return false;
  const lastAction = flow.actions?.[flow.actions.length - 1];
  const hasContent = lastAction?.content?.length > 0 || flow.input?.content?.length > 0;
  const hasMetadata = Object.keys(flow.input?.metadata || {}).length > 0;
  return hasContent || hasMetadata;
};

const getFlowByNumber = (flowNumber) => {
  return props.deltaFileData?.flows?.find((f) => f.number === flowNumber);
};

const openFlowOutput = (node) => {
  selectedFlow.value = getFlowByNumber(node.number);
  flowOutputDialogRef.value?.show();
};

// Error viewer dialog (same as table view)
const errorViewer = reactive({
  visible: false,
  action: {},
});

const nodeIsClickable = (node) => {
  const flow = getFlowByNumber(node.number);
  if (!flow) return false;
  if (flow.state === "ERROR") return true;
  const lastAction = flow.actions?.[flow.actions.length - 1];
  if (flow.state === "COMPLETE" && lastAction?.state === "FILTERED") return true;
  return false;
};

const onNodeClick = (node) => {
  if (!nodeIsClickable(node)) return;

  const flow = getFlowByNumber(node.number);
  const action = flow.actions?.length > 0 ? flow.actions[flow.actions.length - 1] : flow;
  errorViewer.visible = true;
  errorViewer.action = action;
};

// Build graph data from DeltaFile flows
const graphData = computed(() => {
  const flows = props.deltaFileData?.flows || [];
  const nodes = [];
  const edges = [];

  // Create a map for quick lookup by flow number
  const flowsByNumber = new Map();
  for (const flow of flows) {
    flowsByNumber.set(flow.number, flow);
  }

  // Create flow nodes
  for (const flow of flows) {
    const elapsed = new Date(flow.modified) - new Date(flow.created);
    nodes.push({
      id: `FLOW:${flow.number}`,
      name: flow.name,
      type: flow.type,
      state: flow.state,
      depth: flow.depth,
      number: flow.number,
      elapsed: duration(elapsed),
    });
  }

  // Build edges using ancestorIds (the authoritative parent-child relationship)
  for (const flow of flows) {
    const ancestorIds = flow.input?.ancestorIds || [];
    if (ancestorIds.length > 0) {
      const parentNumber = ancestorIds[0]; // Immediate parent
      const parent = flowsByNumber.get(parentNumber);

      if (parent) {
        // Find the connecting topic (intersection of parent's publish and child's subscribe)
        const parentPublish = new Set(parent.publishTopics || []);
        const childSubscribe = flow.input?.topics || flow.input?.t || [];
        const connectingTopic = childSubscribe.find((t) => parentPublish.has(t));

        edges.push({
          source: `FLOW:${parentNumber}`,
          target: `FLOW:${flow.number}`,
          label: connectingTopic || null,
        });
      }
    }
  }

  return { nodes, edges };
});

// Compute node positions using column-based layout with crossing minimization
const nodePositions = computed(() => {
  const positions = {};
  const { nodes, edges } = graphData.value;

  // Group nodes by column (depth)
  const columns = new Map();
  for (const node of nodes) {
    const col = node.depth || 0;
    if (!columns.has(col)) columns.set(col, []);
    columns.get(col).push(node);
  }

  // Sort columns
  const sortedCols = Array.from(columns.keys()).sort((a, b) => a - b);

  // Helper to get predecessors (nodes with edges pointing to this node)
  const getPredecessors = (nodeId) => {
    return edges.filter((e) => e.target === nodeId).map((e) => e.source);
  };

  // Process columns left to right, ordering to minimize crossings
  for (let colIdx = 0; colIdx < sortedCols.length; colIdx++) {
    const col = sortedCols[colIdx];
    const colNodes = columns.get(col);
    const x = padding + colIdx * (nodeWidth + horizontalGap) + nodeWidth / 2;

    if (colIdx === 0) {
      // First column: sort by flow number (sequence)
      colNodes.sort((a, b) => (a.number || 0) - (b.number || 0));
    } else {
      // Subsequent columns: sort by average Y position of predecessors
      colNodes.sort((a, b) => {
        const aPreds = getPredecessors(a.id);
        const bPreds = getPredecessors(b.id);

        const aAvgY =
          aPreds.length > 0
            ? aPreds.reduce((sum, id) => sum + (positions[id]?.y || 0), 0) / aPreds.length
            : 0;
        const bAvgY =
          bPreds.length > 0
            ? bPreds.reduce((sum, id) => sum + (positions[id]?.y || 0), 0) / bPreds.length
            : 0;

        return aAvgY - bAvgY;
      });
    }

    // Position nodes starting from top
    const startY = padding + nodeHeight / 2;
    for (let i = 0; i < colNodes.length; i++) {
      const y = startY + i * (nodeHeight + verticalGap);
      positions[colNodes[i].id] = { x, y };
    }
  }

  return positions;
});

// Compute edge paths (accounting for output panels on flow nodes)
const layoutEdges = computed(() => {
  const edges = graphData.value.edges;
  const positions = nodePositions.value;
  const nodes = graphData.value.nodes;

  // Build a map of node id to node for quick lookup
  const nodeMap = new Map(nodes.map((n) => [n.id, n]));

  return edges
    .filter((edge) => positions[edge.source] && positions[edge.target])
    .map((edge) => {
      const source = positions[edge.source];
      const target = positions[edge.target];
      const sourceNode = nodeMap.get(edge.source);

      // Source exits from right side - add outputBoxWidth if source has output panel
      const sourceHasOutput = sourceNode && sourceNode.type !== "TOPIC" && flowHasOutput(sourceNode);
      const sourceX = source.x + nodeWidth / 2 + (sourceHasOutput ? outputBoxWidth : 0);

      // Target enters from left side
      // Path ends at back of arrowhead (refX=0), arrow is 10 units long with stroke 1.5 = 15px
      const targetX = target.x - nodeWidth / 2 - 15;

      // Control points ensure horizontal exit from source and horizontal entry to target
      const controlOffset = Math.min(40, (targetX - sourceX) * 0.4);
      const cx1 = sourceX + controlOffset;
      const cx2 = targetX - controlOffset;

      const path = `M ${sourceX} ${source.y} C ${cx1} ${source.y}, ${cx2} ${target.y}, ${targetX} ${target.y}`;

      return { ...edge, path };
    });
});

// Compute node positions with layout info
const layoutNodes = computed(() => {
  return graphData.value.nodes.map((node) => ({
    ...node,
    ...(nodePositions.value[node.id] || { x: 0, y: 0 }),
  }));
});

// Compute which node types are present in the graph
const presentTypes = computed(() => {
  return new Set(graphData.value.nodes.map((node) => node.type));
});

// Compute SVG dimensions based on node positions
const graphBounds = computed(() => {
  const positions = Object.values(nodePositions.value);
  if (positions.length === 0) {
    return { minX: 0, minY: 0, maxX: 400, maxY: 200 };
  }

  let minX = Infinity,
    minY = Infinity,
    maxX = -Infinity,
    maxY = -Infinity;

  for (const pos of positions) {
    minX = Math.min(minX, pos.x - nodeWidth / 2);
    minY = Math.min(minY, pos.y - nodeHeight / 2);
    maxX = Math.max(maxX, pos.x + nodeWidth / 2);
    maxY = Math.max(maxY, pos.y + nodeHeight / 2);
  }

  return {
    minX: minX - padding,
    minY: minY - padding,
    maxX: maxX + padding,
    maxY: maxY + padding,
  };
});

const viewBoxString = computed(() => {
  const bounds = graphBounds.value;
  const width = (bounds.maxX - bounds.minX) / zoomLevel.value;
  const height = (bounds.maxY - bounds.minY) / zoomLevel.value;
  const x = bounds.minX - panOffset.value.x / zoomLevel.value;
  const y = bounds.minY - panOffset.value.y / zoomLevel.value;
  return `${x} ${y} ${width} ${height}`;
});

// Helpers
function getNodeColors(node) {
  return getFlowNodeColors(node.type, node.state);
}

function getStateTextColor(state) {
  switch (state) {
    case "COMPLETE":
      return "var(--green-700)";
    case "ERROR":
      return "var(--red-700)";
    case "FILTERED":
      return "var(--yellow-700)";
    case "IN_FLIGHT":
    case "PENDING":
      return "var(--blue-700)";
    case "CANCELLED":
      return "var(--surface-600)";
    default:
      return "var(--text-color-secondary)";
  }
}

// Background color for output panel (slightly darker than main node)
// Uses state color for special states, type color otherwise
function getOutputPanelColor(type, state) {
  // Special states get their own colors
  switch (state) {
    case "ERROR":
      return "var(--red-200)";
    case "FILTERED":
      return "var(--yellow-200)";
    case "CANCELLED":
      return "var(--surface-300)";
  }
  // Type-based colors for normal states (COMPLETE, IN_FLIGHT, PENDING, RESUMED)
  switch (type) {
    case "REST_DATA_SOURCE":
    case "TIMED_DATA_SOURCE":
    case "ON_ERROR_DATA_SOURCE":
      return "var(--orange-200)";
    case "TRANSFORM":
      return "var(--blue-200)";
    case "DATA_SINK":
      return "var(--green-200)";
    default:
      return "var(--surface-300)";
  }
}

// Icon for node type (Font Awesome unicode)
function getTypeIcon(type) {
  switch (type) {
    case "REST_DATA_SOURCE":
      return "\uf56f"; // fa-file-import
    case "TIMED_DATA_SOURCE":
      return "\uf017"; // fa-clock
    case "ON_ERROR_DATA_SOURCE":
      return "\uf071"; // fa-triangle-exclamation
    case "TRANSFORM":
      return "\uf542"; // fa-project-diagram
    case "DATA_SINK":
      return "\uf56e"; // fa-file-export
    case "TOPIC":
      return "\uf1c0"; // fa-database
    default:
      return "\uf128"; // fa-question
  }
}

const maxLabelLength = 18;

function truncateLabel(label) {
  if (label.length <= maxLabelLength) return label;
  return label.substring(0, maxLabelLength - 1) + "…";
}

function isLabelTruncated(label) {
  return label.length > maxLabelLength;
}

function showNodeTooltip(event, text) {
  const container = containerRef.value;
  if (!container) return;
  const rect = container.getBoundingClientRect();
  tooltip.value = {
    visible: true,
    x: event.clientX - rect.left,
    y: event.clientY - rect.top - 30,
    text,
  };
}

// Pan/zoom handlers
function zoomIn() {
  zoomLevel.value = Math.min(zoomLevel.value * 1.2, 3);
  userHasInteracted.value = true;
}

function zoomOut() {
  zoomLevel.value = Math.max(zoomLevel.value / 1.2, 0.3);
  userHasInteracted.value = true;
}

function zoomAtPoint(factor, clientX, clientY) {
  if (!containerRef.value) return;

  const rect = containerRef.value.getBoundingClientRect();
  const cursorX = clientX - rect.left;
  const cursorY = clientY - rect.top;

  const oldZoom = zoomLevel.value;
  const newZoom = Math.max(0.3, Math.min(3, oldZoom * factor));

  // Adjust pan to keep cursor point stationary
  panOffset.value = {
    x: cursorX - (cursorX - panOffset.value.x) * (newZoom / oldZoom),
    y: cursorY - (cursorY - panOffset.value.y) * (newZoom / oldZoom),
  };

  zoomLevel.value = newZoom;
  userHasInteracted.value = true;
}

function resetView() {
  zoomLevel.value = 1;
  panOffset.value = { x: 0, y: 0 };
  userHasInteracted.value = false;
}

function onWheel(e) {
  // Only handle pinch zoom (ctrlKey), let regular scroll pass through
  if (!e.ctrlKey) return;

  e.preventDefault();
  const factor = e.deltaY < 0 ? 1.1 : 1 / 1.1;
  zoomAtPoint(factor, e.clientX, e.clientY);
}

function onMouseDown(e) {
  if (e.button === 0) {
    isDragging.value = true;
    dragStart.value = { x: e.clientX - panOffset.value.x, y: e.clientY - panOffset.value.y };
  }
}

function onMouseMove(e) {
  if (isDragging.value) {
    panOffset.value = {
      x: e.clientX - dragStart.value.x,
      y: e.clientY - dragStart.value.y,
    };
    userHasInteracted.value = true;
  }
}

function onMouseUp() {
  isDragging.value = false;
}

// Resize observer
let resizeObserver = null;

function updateSize() {
  if (containerRef.value) {
    svgWidth.value = containerRef.value.clientWidth;
    svgHeight.value = Math.max(300, graphBounds.value.maxY - graphBounds.value.minY + 40);
  }
}

onMounted(() => {
  updateSize();

  if (containerRef.value) {
    containerRef.value.addEventListener("wheel", onWheel, { passive: false });
    containerRef.value.addEventListener("mousedown", onMouseDown);
    window.addEventListener("mousemove", onMouseMove);
    window.addEventListener("mouseup", onMouseUp);
  }

  resizeObserver = new ResizeObserver(updateSize);
  if (containerRef.value) {
    resizeObserver.observe(containerRef.value);
  }
});

onUnmounted(() => {
  if (containerRef.value) {
    containerRef.value.removeEventListener("wheel", onWheel);
    containerRef.value.removeEventListener("mousedown", onMouseDown);
  }
  window.removeEventListener("mousemove", onMouseMove);
  window.removeEventListener("mouseup", onMouseUp);

  if (resizeObserver) {
    resizeObserver.disconnect();
  }
});

watch(
  () => props.deltaFileData,
  () => {
    updateSize();
  },
  { deep: true }
);
</script>

<style scoped>
.flow-graph-panel :deep(.p-panel-content) {
  padding: 0;
}

.graph-container {
  position: relative;
  min-height: 200px;
  background: var(--surface-ground);
  overflow: hidden;
  cursor: grab;
}

.graph-container:active {
  cursor: grabbing;
}

.node-group {
  cursor: default;
}

.node-clickable {
  cursor: pointer;
}

.node-label {
  font-size: 11px;
  font-weight: 500;
  fill: var(--text-color);
  pointer-events: none;
}

.node-label.truncated {
  pointer-events: auto;
  cursor: default;
}

.node-state {
  font-size: 9px;
  font-weight: 600;
  text-transform: uppercase;
  pointer-events: none;
}

.zoom-controls {
  position: absolute;
  bottom: 10px;
  right: 10px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.zoom-btn {
  width: 28px;
  height: 28px;
  border: 1px solid var(--surface-border);
  border-radius: 4px;
  background: var(--surface-card);
  color: var(--text-color);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s ease;
}

.zoom-btn:hover {
  background: var(--surface-hover);
}

.zoom-btn .pi {
  font-size: 12px;
}

.node-type-icon {
  fill: var(--text-color-secondary);
  font-size: 12px;
  pointer-events: none;
}

.node-elapsed {
  fill: var(--text-color-secondary);
  font-size: 9px;
  pointer-events: none;
}

.output-panel-bg {
  cursor: pointer;
  transition: opacity 0.15s ease;
}

.output-panel-bg:hover {
  opacity: 0.8;
}

.node-output-icon {
  fill: var(--text-color);
  font-size: 14px;
  cursor: pointer;
  pointer-events: none;
}

/* Legend */
.graph-legend {
  position: absolute;
  bottom: 10px;
  left: 10px;
  z-index: 10;
  background: var(--surface-card);
  border: 1px solid var(--surface-border);
  border-radius: 6px;
  padding: 8px 12px;
  font-size: 0.75rem;
  color: var(--text-color-secondary);
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
  max-width: 400px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
}

.legend-item i {
  width: 14px;
  text-align: center;
  color: var(--text-color);
}

/* Edge styles */
.edge-group {
  cursor: pointer;
}

.edge-hit-area {
  fill: none;
  stroke: transparent;
  stroke-width: 15;
  pointer-events: stroke;
}

.edge-path {
  fill: none;
  stroke: var(--surface-400);
  stroke-width: 1.5;
  pointer-events: none;
  transition: stroke 0.15s ease, stroke-width 0.15s ease;
}

.edge-group:hover .edge-path {
  stroke: var(--blue-500);
  stroke-width: 2.5;
}

/* Tooltip */
.graph-tooltip {
  position: absolute;
  z-index: 20;
  background: var(--surface-800);
  color: var(--surface-0);
  padding: 6px 10px;
  border-radius: 4px;
  font-size: 12px;
  white-space: nowrap;
  pointer-events: none;
  transform: translateX(-50%);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
}
</style>
