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
    class="pipeline-graph"
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

    <!-- Hint text at bottom -->
    <div class="graph-hint">
      Double-click a node to focus
    </div>

    <svg :width="svgWidth" :height="svgHeight" :viewBox="viewBoxString">
      <defs>
        <marker id="arrowhead" markerWidth="10" markerHeight="7" refX="0" refY="3.5" orient="auto">
          <polygon points="0 0, 10 3.5, 0 7" fill="var(--surface-500)" />
        </marker>
        <marker id="arrowhead-active" markerWidth="10" markerHeight="7" refX="0" refY="3.5" orient="auto">
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
            :marker-end="isEdgeActive(edge) ? 'url(#arrowhead-active)' : 'url(#arrowhead)'"
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
          :focused="node.id === focusedNodeId"
          :can-expand-upstream="node.canExpandUpstream"
          :can-expand-downstream="node.canExpandDownstream"
          :error-count="getErrorCount(node.name)"
          :metrics="getNodeMetrics(node.name)"
          :transform="`translate(${node.x}, ${node.y})`"
          @click="handleNodeClick(node)"
          @expand-upstream="hideTooltip(); $emit('expand-upstream', node.id)"
          @expand-downstream="hideTooltip(); $emit('expand-downstream', node.id)"
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
// ABOUTME: SVG-based pipeline graph component with progressive expansion.
// ABOUTME: Renders nodes in horizontal lanes with expand buttons for exploration.

import { computed, ref } from "vue";
import {
  getErrorCount as getErrorCountFn,
  getNodeMetrics as getNodeMetricsFn,
  isEdgeActive as isEdgeActiveFn,
  isDataSourceType,
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
  focusedNodeId: {
    type: String,
    default: null,
  },
  flowMetrics: {
    type: Object,
    default: () => ({}),
  },
  errorCounts: {
    type: Object,
    default: () => ({}),
  },
});

const emit = defineEmits(["select-node", "focus-node", "expand-upstream", "expand-downstream", "flow-state-change", "error-badge-click"]);

// Layout constants
const nodeWidth = 220;
const nodeHeight = 60;
const horizontalGap = 160;
const verticalGap = 40;
const padding = 60;
const metricsBoxWidth = 52;

// Graph container ref
const graphContainer = ref(null);

// Container dimensions (need to be defined before layoutNodes)
const containerWidth = ref(1000);
const containerHeight = ref(600);

// Group nodes into 3 lanes: data sources, middle (transforms/topics), data sinks
const layoutNodes = computed(() => {
  if (!props.nodes.length) return [];

  // Separate nodes into lanes
  const dataSources = [];
  const dataSinks = [];
  const middleNodes = [];

  for (const node of props.nodes) {
    if (isDataSourceType(node.type)) {
      dataSources.push(node);
    } else if (node.type === "DATA_SINK") {
      dataSinks.push(node);
    } else {
      middleNodes.push(node);
    }
  }

  // Group middle nodes by depth for internal layout
  const byDepth = {};
  for (const node of middleNodes) {
    const depth = node.depth || 0;
    if (!byDepth[depth]) byDepth[depth] = [];
    byDepth[depth].push(node);
  }

  const depths = Object.keys(byDepth)
    .map(Number)
    .sort((a, b) => a - b);

  // Calculate column positions
  // Lane 1: Data sources (leftmost)
  // Lane 2: Middle nodes (transforms/topics) - multiple columns by depth
  // Lane 3: Data sinks (rightmost)
  const result = [];
  const centerY = containerHeight.value / 2;
  const columnSpacing = nodeWidth + horizontalGap;

  // Determine middle section width
  const middleColumnCount = depths.length || 1;

  // Calculate starting X positions for each lane
  const totalColumns = 1 + middleColumnCount + 1; // sources + middle + sinks
  const totalWidth = (totalColumns - 1) * columnSpacing;
  const startX = containerWidth.value / 2 - totalWidth / 2;

  // Lane 1: Data sources
  if (dataSources.length > 0) {
    const columnX = startX;
    const totalHeight = dataSources.length * nodeHeight + (dataSources.length - 1) * verticalGap;
    const startY = centerY - totalHeight / 2 + nodeHeight / 2;

    dataSources.forEach((node, index) => {
      result.push({
        ...node,
        x: columnX,
        y: startY + index * (nodeHeight + verticalGap),
      });
    });
  }

  // Lane 2: Middle nodes (transforms/topics) by depth
  const middleStartX = startX + columnSpacing;
  for (let i = 0; i < depths.length; i++) {
    const depth = depths[i];
    const nodesAtDepth = byDepth[depth];
    const columnX = middleStartX + i * columnSpacing;

    const totalHeight = nodesAtDepth.length * nodeHeight + (nodesAtDepth.length - 1) * verticalGap;
    const startY = centerY - totalHeight / 2 + nodeHeight / 2;

    nodesAtDepth.forEach((node, index) => {
      result.push({
        ...node,
        x: columnX,
        y: startY + index * (nodeHeight + verticalGap),
      });
    });
  }

  // Lane 3: Data sinks
  if (dataSinks.length > 0) {
    const columnX = middleStartX + Math.max(middleColumnCount - 1, 0) * columnSpacing + columnSpacing;
    const totalHeight = dataSinks.length * nodeHeight + (dataSinks.length - 1) * verticalGap;
    const startY = centerY - totalHeight / 2 + nodeHeight / 2;

    dataSinks.forEach((node, index) => {
      result.push({
        ...node,
        x: columnX,
        y: startY + index * (nodeHeight + verticalGap),
      });
    });
  }

  return result;
});

// Use pan/zoom composable (pass container dimension refs to be updated by ResizeObserver)
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
  setupNodeCountWatcher,
  setupResizeObserver,
  setDetailPanelOffset,
} = useGraphPanZoom(graphContainer, layoutNodes, nodeWidth, nodeHeight, padding, containerWidth, containerHeight, {
  minZoom: 0.25,
  maxZoom: 2,
  excludeSelectors: [".node-group", ".expand-button"],
});

// Setup resize observer and node count watcher
setupResizeObserver();
setupNodeCountWatcher(computed(() => props.nodes.length));

// Expose methods for parent component
defineExpose({
  setDetailPanelOffset,
});

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
    arrowLength: 18,
  })
);

// Double-click detection
const lastClickTime = {};
const DOUBLE_CLICK_THRESHOLD = 300;

function handleNodeClick(node) {
  const now = Date.now();
  const lastClick = lastClickTime[node.id] || 0;

  if (now - lastClick < DOUBLE_CLICK_THRESHOLD) {
    emit("focus-node", node);
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
.pipeline-graph {
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

.pipeline-graph:active {
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
  transform: translateX(-50%);
}

/* Hint text at bottom */
.graph-hint {
  position: absolute;
  bottom: 10px;
  left: 50%;
  transform: translateX(-50%);
  color: var(--text-color-secondary);
  font-size: 0.75rem;
  opacity: 0.6;
  pointer-events: none;
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
  stroke-width: 2;
  opacity: 0.7;
  pointer-events: none;
  transition: stroke-width 0.15s ease, opacity 0.15s ease;
}

.edge-group:hover .edge-path {
  stroke: var(--blue-500);
  stroke-width: 4;
  opacity: 1;
}

.edge-group.active .edge-path {
  stroke: var(--green-500);
  opacity: 0.9;
}

.edge-group.active:hover .edge-path {
  stroke-width: 4;
  opacity: 1;
}

/* Legend */
.graph-legend {
  position: absolute;
  bottom: 30px;
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
