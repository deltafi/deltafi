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
  <div
    class="system-map-graph"
    ref="graphContainer"
    @mousedown="startPan"
    @mousemove="doPan"
    @mouseup="endPan"
    @mouseleave="endPan"
    @wheel="doZoom"
  >
    <!-- Zoom controls -->
    <div class="zoom-controls">
      <button
        v-if="isViewModified"
        class="zoom-button"
        @click="resetView"
        title="Reset view"
      >
        <i class="fas fa-crosshairs" />
      </button>
      <button class="zoom-button" @click="zoomIn" title="Zoom in">
        <i class="fas fa-plus" />
      </button>
      <button class="zoom-button" @click="zoomOut" title="Zoom out">
        <i class="fas fa-minus" />
      </button>
    </div>

    <!-- Custom tooltip -->
    <div
      v-if="tooltip.visible"
      class="graph-tooltip"
      :style="{ left: tooltip.x + 'px', top: tooltip.y + 'px', transform: 'translateX(-50%)' }"
    >
      {{ tooltip.text }}
    </div>

    <!-- Legend -->
    <div class="graph-legend">
      <div class="legend-item"><i class="fas fa-file-import" /> REST Data Source</div>
      <div class="legend-item"><i class="fas fa-clock" /> Timed Data Source</div>
      <div class="legend-item"><i class="fas fa-triangle-exclamation" /> Error Data Source</div>
      <div class="legend-item"><i class="fas fa-project-diagram" /> Transform</div>
      <div class="legend-item"><i class="fas fa-file-export" /> Data Sink</div>
      <div class="legend-item"><i class="fas fa-database" /> Topic</div>
    </div>

    <svg :width="svgWidth" :height="svgHeight" :viewBox="viewBoxString">
      <defs>
        <marker id="system-arrowhead" markerWidth="10" markerHeight="7" refX="0" refY="3.5" orient="auto">
          <polygon points="0 0, 10 3.5, 0 7" fill="var(--text-color-secondary)" />
        </marker>
        <marker id="system-arrowhead-active" markerWidth="10" markerHeight="7" refX="0" refY="3.5" orient="auto">
          <polygon points="0 0, 10 3.5, 0 7" fill="var(--green-500)" />
        </marker>
      </defs>

      <!-- Edges -->
      <g class="edges">
        <g
          v-for="edge in layoutEdges"
          :key="`${edge.source}-${edge.target}`"
          class="edge-group"
          :class="{ active: isEdgeActive(edge) }"
        >
          <!-- Invisible wider path for easier hit detection -->
          <path :d="edge.path" class="edge-hit-area" />
          <!-- Visible path -->
          <path
            :d="edge.path"
            class="edge-path"
            :marker-end="isEdgeActive(edge) ? 'url(#system-arrowhead-active)' : 'url(#system-arrowhead)'"
          />
        </g>
      </g>

      <!-- Nodes -->
      <g class="nodes">
        <GraphNode
          v-for="node in layoutNodes"
          :key="node.id"
          :node="node"
          :node-width="nodeWidth"
          :node-height="nodeHeight"
          :horizontal-gap="horizontalGap"
          :selected="node.id === selectedNodeId"
          :error-count="getErrorCount(node.name)"
          :metrics="getNodeMetrics(node.name)"
          :compact="true"
          :transform="`translate(${node.x}, ${node.y})`"
          @click="handleNodeClick(node)"
          @error-badge-click="$emit('error-badge-click', node)"
          @flow-state-change="(state) => $emit('flow-state-change', node, state)"
          @show-tooltip="showTooltip"
          @hide-tooltip="hideTooltip"
        />
      </g>
    </svg>
  </div>
</template>

<script setup>
// ABOUTME: SVG-based system map graph component showing full topology.
// ABOUTME: Auto-layouts nodes in columns by type with pan and zoom support.

import { computed, ref } from "vue";
import {
  getErrorCount as getErrorCountFn,
  getNodeMetrics as getNodeMetricsFn,
  isEdgeActive as isEdgeActiveFn,
  computeEdgePaths,
} from "./useGraphStyles";
import { useGraphPanZoom } from "./useGraphPanZoom";
import GraphNode from "./GraphNode.vue";

const props = defineProps({
  nodes: {
    type: Array,
    required: true,
  },
  edges: {
    type: Array,
    required: true,
  },
  selectedNodeId: {
    type: String,
    default: null,
  },
  errorCounts: {
    type: Object,
    default: () => ({}),
  },
  flowMetrics: {
    type: Object,
    default: () => ({}),
  },
});

const emit = defineEmits(["select-node", "error-badge-click", "flow-state-change", "show-dataflow"]);

// Layout constants
const nodeWidth = 180;
const nodeHeight = 40;
const horizontalGap = 140;
const verticalGap = 20;
const padding = 60;
const metricsBoxWidth = 52;

// Column order for layout
const columnOrder = [
  "DATA_SOURCE",
  "TOPIC_SOURCE",
  "TRANSFORM",
  "TOPIC_TRANSFORM",
  "DATA_SINK",
];

// Data source types that go in the combined DATA_SOURCE column
const dataSourceTypes = ["REST_DATA_SOURCE", "TIMED_DATA_SOURCE", "ON_ERROR_DATA_SOURCE"];

// Graph container ref
const graphContainer = ref(null);

// Container dimensions
const containerWidth = ref(1000);
const containerHeight = ref(600);

// Categorize topics based on their publishers
function getTopicCategory(topicId, edges, nodes) {
  const publishers = edges
    .filter((e) => e.target === topicId)
    .map((e) => nodes.find((n) => n.id === e.source))
    .filter(Boolean);

  const hasTransformPublisher = publishers.some((p) => p.type === "TRANSFORM");
  return hasTransformPublisher ? "TOPIC_TRANSFORM" : "TOPIC_SOURCE";
}

// Group nodes by column type
const nodesByColumn = computed(() => {
  const columns = {};
  for (const type of columnOrder) {
    columns[type] = [];
  }

  for (const node of props.nodes) {
    if (node.type === "TOPIC") {
      const category = getTopicCategory(node.id, props.edges, props.nodes);
      columns[category].push(node);
    } else if (dataSourceTypes.includes(node.type)) {
      // All data source types go in the combined DATA_SOURCE column
      columns["DATA_SOURCE"].push(node);
    } else if (columns[node.type]) {
      columns[node.type].push(node);
    }
  }

  // Sort each column alphabetically by name
  for (const type of columnOrder) {
    columns[type].sort((a, b) => a.name.toLowerCase().localeCompare(b.name.toLowerCase()));
  }

  return columns;
});

// Calculate layout positions
const layoutNodes = computed(() => {
  if (!props.nodes.length) return [];

  const result = [];
  let currentX = padding;

  // Find the maximum column height to center vertically
  let maxColumnHeight = 0;
  for (const type of columnOrder) {
    const columnNodes = nodesByColumn.value[type];
    if (columnNodes.length > 0) {
      const height = columnNodes.length * nodeHeight + (columnNodes.length - 1) * verticalGap;
      maxColumnHeight = Math.max(maxColumnHeight, height);
    }
  }

  for (const type of columnOrder) {
    const columnNodes = nodesByColumn.value[type];
    if (columnNodes.length === 0) continue;

    // Calculate column height and starting Y position
    const columnHeight = columnNodes.length * nodeHeight + (columnNodes.length - 1) * verticalGap;
    const startY = (maxColumnHeight - columnHeight) / 2 + nodeHeight / 2 + padding;

    for (let i = 0; i < columnNodes.length; i++) {
      const node = columnNodes[i];
      result.push({
        ...node,
        x: currentX + nodeWidth / 2,
        y: startY + i * (nodeHeight + verticalGap),
      });
    }

    currentX += nodeWidth + horizontalGap;
  }

  return result;
});

// Use pan/zoom composable
const {
  tooltip,
  svgWidth,
  svgHeight,
  viewBoxString,
  isViewModified,
  resetView,
  startPan,
  doPan,
  endPan,
  doZoom,
  zoomIn,
  zoomOut,
  showTooltip,
  hideTooltip,
  setupResizeObserver,
  setupNodeCountWatcher,
} = useGraphPanZoom(graphContainer, layoutNodes, nodeWidth, nodeHeight, padding, containerWidth, containerHeight, {
  minZoom: 0.1,
  maxZoom: 2,
  excludeSelectors: [".node-group"],
});

// Setup resize observer and node count watcher
setupResizeObserver();
setupNodeCountWatcher(computed(() => props.nodes.length));

// Create node position lookup
const nodePositions = computed(() => {
  const positions = {};
  for (const node of layoutNodes.value) {
    positions[node.id] = { x: node.x, y: node.y };
  }
  return positions;
});

// Calculate edge paths using shared function
const layoutEdges = computed(() =>
  computeEdgePaths(props.edges, nodePositions.value, nodeWidth, {
    metricsBoxWidth,
    flowMetrics: props.flowMetrics,
    nodes: props.nodes,
    arrowLength: 16,
  })
);

// Double-click detection
const lastClickTime = {};
const DOUBLE_CLICK_THRESHOLD = 300;

function handleNodeClick(node) {
  const now = Date.now();
  const lastClick = lastClickTime[node.id] || 0;

  if (now - lastClick < DOUBLE_CLICK_THRESHOLD) {
    emit("show-dataflow", node);
    lastClickTime[node.id] = 0;
  } else {
    emit("select-node", node);
    lastClickTime[node.id] = now;
  }
}

// Wrapper functions that use props
function getNodeMetrics(nodeName) {
  return getNodeMetricsFn(props.flowMetrics, nodeName);
}

function getErrorCount(nodeName) {
  return getErrorCountFn(props.errorCounts, nodeName);
}

function isEdgeActive(edge) {
  return isEdgeActiveFn(edge, props.flowMetrics);
}
</script>

<style scoped>
.system-map-graph {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  overflow: hidden;
  background: var(--surface-ground);
  border-radius: 4px;
  cursor: grab;
  user-select: none;
}

.system-map-graph:active {
  cursor: grabbing;
}

/* Zoom controls */
.zoom-controls {
  position: absolute;
  top: 10px;
  right: 10px;
  z-index: 10;
  display: flex;
  gap: 4px;
}

.zoom-button {
  width: 32px;
  height: 32px;
  border: 1px solid var(--surface-border);
  border-radius: 6px;
  background: var(--surface-card);
  color: var(--text-color);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s ease;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.zoom-button:hover {
  background: var(--surface-hover);
  border-color: var(--primary-color);
  color: var(--primary-color);
}

/* Custom tooltip */
.graph-tooltip {
  position: absolute;
  z-index: 20;
  background: var(--surface-900);
  color: var(--surface-0);
  padding: 0.4rem 0.6rem;
  border-radius: 4px;
  font-size: 0.8rem;
  white-space: nowrap;
  pointer-events: none;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
}

svg {
  display: block;
}

.edge-group {
  cursor: pointer;
}

.edge-hit-area {
  fill: none;
  stroke: transparent;
  stroke-width: 20;
  pointer-events: stroke;
}

.edge-path {
  fill: none;
  stroke: var(--surface-500);
  stroke-width: 1.5;
  opacity: 0.7;
  pointer-events: none;
  transition: stroke-width 0.15s ease, opacity 0.15s ease;
}

.edge-group:hover .edge-path {
  stroke: var(--blue-500);
  stroke-width: 3;
  opacity: 1;
}

.edge-group.active .edge-path {
  stroke: var(--green-500);
  opacity: 0.9;
}

.edge-group.active:hover .edge-path {
  stroke-width: 3;
  opacity: 1;
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

</style>
