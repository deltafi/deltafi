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
<!-- ABOUTME: Visualizes the parent/child lineage tree of a DeltaFile as an SVG graph. -->
<!-- ABOUTME: Shows ancestors above and descendants below with expand capability. -->

<template>
  <div ref="containerRef" class="lineage-graph-container">
    <div v-if="treeLoading" class="loading-overlay">
      <i class="pi pi-spin pi-spinner" style="font-size: 2rem" />
      <span>Loading lineage tree...</span>
    </div>

    <svg v-else ref="svgRef" :width="svgWidth" :height="svgHeight" :viewBox="viewBoxString">
      <!-- Arrow marker definition -->
      <defs>
        <marker
          id="lineage-arrowhead"
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
        <path
          v-for="edge in layoutEdges"
          :key="`${edge.source}-${edge.target}`"
          :d="edge.path"
          class="edge-path"
          marker-end="url(#lineage-arrowhead)"
        />
      </g>

      <!-- Nodes -->
      <g class="nodes">
        <g
          v-for="node in layoutNodes"
          :key="node.did"
          :transform="`translate(${node.x - nodeWidth / 2}, ${node.y - nodeHeight / 2})`"
          class="node-group"
        >
          <!-- Main node rectangle -->
          <rect
            :width="nodeWidth"
            :height="nodeHeight"
            rx="6"
            ry="6"
            :fill="getNodeFill(node)"
            :stroke="getNodeStroke(node)"
            :stroke-width="node.did === rootDid ? 3 : 1.5"
            class="node-rect"
            @click="onNodeClick(node)"
          />

          <!-- Filename (line 1) -->
          <text
            :x="nodeWidth / 2"
            :y="14"
            text-anchor="middle"
            class="node-filename"
            @mouseenter="isLabelTruncated(node.name) && showTooltip($event, node.name)"
            @mouseleave="hideTooltip"
          >
            {{ truncateLabel(node.name) }}
          </text>

          <!-- Stage badge + DataSource (line 2) -->
          <g :transform="`translate(${nodeWidth / 2}, 28)`">
            <!-- Stage badge -->
            <rect
              :x="-getStageWidth(node.stage) / 2 - 2"
              :y="-9"
              :width="getStageWidth(node.stage) + 4"
              :height="14"
              rx="3"
              :fill="getStageBadgeColor(node.stage)"
            />
            <text
              :x="0"
              :y="2"
              text-anchor="middle"
              class="node-stage"
            >
              {{ node.stage }}
            </text>
          </g>

          <!-- Data Source (below stage) -->
          <text
            :x="nodeWidth / 2"
            :y="nodeHeight - 4"
            text-anchor="middle"
            class="node-datasource"
            @mouseenter="isLabelTruncated(node.dataSource) && showTooltip($event, node.dataSource)"
            @mouseleave="hideTooltip"
          >
            {{ truncateLabel(node.dataSource, 20) }}
          </text>

          <!-- Expand ancestors button (left side) -->
          <g
            v-if="hasUnexpandedAncestors(node.did)"
            class="expand-button"
            :transform="`translate(-12, ${nodeHeight / 2})`"
            @click.stop="onExpandAncestors(node)"
          >
            <circle r="10" class="expand-button-bg" />
            <text y="4" text-anchor="middle" class="expand-button-icon">
              {{ node.loading ? '⏳' : '+' }}
            </text>
          </g>

          <!-- Expand descendants button (right side) -->
          <g
            v-if="hasUnexpandedDescendants(node.did)"
            class="expand-button"
            :transform="`translate(${nodeWidth + 12}, ${nodeHeight / 2})`"
            @click.stop="onExpandDescendants(node)"
          >
            <circle r="10" class="expand-button-bg" />
            <text y="4" text-anchor="middle" class="expand-button-icon">
              {{ node.loading ? '⏳' : '+' }}
            </text>
          </g>
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

    <!-- Legend -->
    <div class="graph-legend">
      <div class="legend-item"><span class="legend-badge complete">COMPLETE</span></div>
      <div class="legend-item"><span class="legend-badge in-flight">IN_FLIGHT</span></div>
      <div class="legend-item"><span class="legend-badge error">ERROR</span></div>
      <div class="legend-item"><span class="legend-badge cancelled">CANCELLED</span></div>
      <div class="legend-item legend-hint">Click node to view DeltaFile</div>
    </div>

    <!-- Tooltip -->
    <div
      v-if="tooltip.visible"
      class="graph-tooltip"
      :style="{ left: tooltip.x + 'px', top: tooltip.y + 'px' }"
    >
      {{ tooltip.text }}
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import { useRouter } from "vue-router";
import useLineageTree from "@/composables/useLineageTree";

const props = defineProps({
  rootDid: {
    type: String,
    required: true,
  },
  deltaFileData: {
    type: Object,
    required: true,
  },
  initialAncestorDepth: {
    type: Number,
    default: 3,
  },
  initialDescendantDepth: {
    type: Number,
    default: 3,
  },
});

const router = useRouter();

const {
  tree,
  loading: treeLoading,
  buildTree,
  expandAncestors,
  expandDescendants,
  nodesList,
  edges,
  hasUnexpandedAncestors,
  hasUnexpandedDescendants,
} = useLineageTree();

// Layout constants (left-to-right orientation)
const nodeWidth = 160;
const nodeHeight = 52;
const horizontalGap = 80; // Gap between columns (depths)
const verticalGap = 30;   // Gap between nodes in same column
const padding = 60;

// Refs
const containerRef = ref(null);
const svgRef = ref(null);
const svgWidth = ref(800);
const svgHeight = ref(600);

// Pan/zoom state
const panOffset = ref({ x: 0, y: 0 });
const zoomLevel = ref(1);
const userHasInteracted = ref(false);
const isDragging = ref(false);
const dragStart = ref({ x: 0, y: 0 });

// Tooltip
const tooltip = ref({ visible: false, x: 0, y: 0, text: "" });

// Initialize tree on mount
onMounted(async () => {
  await buildTree(
    props.rootDid,
    {
      name: props.deltaFileData.name,
      stage: props.deltaFileData.stage,
      dataSource: props.deltaFileData.dataSource,
      parentDids: props.deltaFileData.parentDids || [],
      childDids: props.deltaFileData.childDids || [],
    },
    props.initialAncestorDepth,
    props.initialDescendantDepth
  );

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

let resizeObserver = null;

function updateSize() {
  if (containerRef.value) {
    svgWidth.value = containerRef.value.clientWidth;
    svgHeight.value = containerRef.value.clientHeight;
  }
}

// Compute node positions using column-based layout (left-to-right, columns by depth)
// Uses barycenter method to minimize edge crossings
const nodePositions = computed(() => {
  const positions = {};
  const nodes = nodesList.value;
  const edgeList = edges.value;

  if (nodes.length === 0) return positions;

  // Group nodes by depth (column)
  const columns = new Map();
  for (const node of nodes) {
    const col = node.depth;
    if (!columns.has(col)) columns.set(col, []);
    columns.get(col).push(node);
  }

  // Get sorted column indices (ancestors negative/left, root 0, descendants positive/right)
  const sortedCols = Array.from(columns.keys()).sort((a, b) => a - b);

  // Build adjacency maps for barycenter calculation
  const getConnectedInPrevColumn = (nodeDid, prevColNodes) => {
    const prevDids = new Set(prevColNodes.map(n => n.did));
    const connected = [];
    // Check edges where this node is target (incoming from previous column)
    for (const edge of edgeList) {
      if (edge.target === nodeDid && prevDids.has(edge.source)) {
        connected.push(edge.source);
      }
      // Also check reverse direction for ancestor relationships
      if (edge.source === nodeDid && prevDids.has(edge.target)) {
        connected.push(edge.target);
      }
    }
    return connected;
  };

  // Process columns left to right, ordering to minimize crossings
  for (let colIdx = 0; colIdx < sortedCols.length; colIdx++) {
    const col = sortedCols[colIdx];
    const colNodes = columns.get(col);
    const x = padding + colIdx * (nodeWidth + horizontalGap) + nodeWidth / 2;

    if (colIdx === 0) {
      // First column: sort alphabetically by name
      colNodes.sort((a, b) => a.name.localeCompare(b.name));
    } else {
      // Subsequent columns: sort by barycenter (average Y of connected nodes in prev column)
      const prevCol = sortedCols[colIdx - 1];
      const prevColNodes = columns.get(prevCol);

      colNodes.sort((a, b) => {
        const aConnected = getConnectedInPrevColumn(a.did, prevColNodes);
        const bConnected = getConnectedInPrevColumn(b.did, prevColNodes);

        const aAvgY = aConnected.length > 0
          ? aConnected.reduce((sum, did) => sum + (positions[did]?.y || 0), 0) / aConnected.length
          : 0;
        const bAvgY = bConnected.length > 0
          ? bConnected.reduce((sum, did) => sum + (positions[did]?.y || 0), 0) / bConnected.length
          : 0;

        return aAvgY - bAvgY;
      });
    }

    // Position nodes vertically within column
    const startY = padding + nodeHeight / 2;
    for (let i = 0; i < colNodes.length; i++) {
      const y = startY + i * (nodeHeight + verticalGap);
      positions[colNodes[i].did] = { x, y };
    }
  }

  return positions;
});

// Compute edge paths (horizontal: parent right to child left)
const layoutEdges = computed(() => {
  const positions = nodePositions.value;

  return edges.value
    .filter((edge) => positions[edge.source] && positions[edge.target])
    .map((edge) => {
      const source = positions[edge.source];
      const target = positions[edge.target];

      // Source exits from right side, target enters from left side
      const sourceX = source.x + nodeWidth / 2;
      const targetX = target.x - nodeWidth / 2 - 15; // Arrow length adjustment

      // Control points for smooth horizontal bezier curve
      const controlOffset = Math.min(40, (targetX - sourceX) * 0.4);
      const cx1 = sourceX + controlOffset;
      const cx2 = targetX - controlOffset;

      const path = `M ${sourceX} ${source.y} C ${cx1} ${source.y}, ${cx2} ${target.y}, ${targetX} ${target.y}`;

      return { ...edge, path };
    });
});

// Layout nodes with positions
const layoutNodes = computed(() => {
  return nodesList.value.map((node) => ({
    ...node,
    ...(nodePositions.value[node.did] || { x: 0, y: 0 }),
  }));
});

// Graph bounds for viewbox
const graphBounds = computed(() => {
  const positions = Object.values(nodePositions.value);
  if (positions.length === 0) {
    return { minX: 0, minY: 0, maxX: 400, maxY: 300 };
  }

  let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;

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

// Node styling based on stage
function getNodeFill(node) {
  if (node.did === props.rootDid) {
    return "var(--primary-100)";
  }
  switch (node.stage) {
    case "ERROR":
      return "var(--red-100)";
    case "CANCELLED":
      return "var(--surface-200)";
    default:
      return "var(--surface-100)";
  }
}

function getNodeStroke(node) {
  if (node.did === props.rootDid) {
    return "var(--primary-500)";
  }
  switch (node.stage) {
    case "ERROR":
      return "var(--red-500)";
    case "CANCELLED":
      return "var(--surface-500)";
    case "COMPLETE":
      return "var(--green-500)";
    case "IN_FLIGHT":
    case "PENDING":
      return "var(--blue-500)";
    default:
      return "var(--surface-400)";
  }
}

function getStageBadgeColor(stage) {
  switch (stage) {
    case "COMPLETE":
      return "var(--green-200)";
    case "ERROR":
      return "var(--red-200)";
    case "IN_FLIGHT":
    case "PENDING":
      return "var(--blue-200)";
    case "CANCELLED":
      return "var(--surface-300)";
    default:
      return "var(--surface-200)";
  }
}

function getStageWidth(stage) {
  // Approximate width based on character count
  return stage.length * 6 + 8;
}

const maxLabelLength = 22;

function truncateLabel(label, maxLen = maxLabelLength) {
  if (!label) return "";
  if (label.length <= maxLen) return label;
  return label.substring(0, maxLen - 1) + "…";
}

function isLabelTruncated(label, maxLen = maxLabelLength) {
  return label && label.length > maxLen;
}

// Tooltip handlers
function showTooltip(event, text) {
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

function hideTooltip() {
  tooltip.value.visible = false;
}

// Node click - navigate to DeltaFile viewer
function onNodeClick(node) {
  const route = router.resolve({ name: "DeltaFile Viewer", params: { did: node.did } });
  window.open(route.href, "_blank");
}

// Expand handlers
async function onExpandAncestors(node) {
  await expandAncestors(node.did, 3);
}

async function onExpandDescendants(node) {
  await expandDescendants(node.did, 3);
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

function resetView() {
  zoomLevel.value = 1;
  panOffset.value = { x: 0, y: 0 };
  userHasInteracted.value = false;
}

function onWheel(e) {
  if (!e.ctrlKey) return;

  e.preventDefault();
  const factor = e.deltaY < 0 ? 1.1 : 1 / 1.1;
  zoomAtPoint(factor, e.clientX, e.clientY);
}

function zoomAtPoint(factor, clientX, clientY) {
  if (!containerRef.value) return;

  const rect = containerRef.value.getBoundingClientRect();
  const cursorX = clientX - rect.left;
  const cursorY = clientY - rect.top;

  const oldZoom = zoomLevel.value;
  const newZoom = Math.max(0.3, Math.min(3, oldZoom * factor));

  panOffset.value = {
    x: cursorX - (cursorX - panOffset.value.x) * (newZoom / oldZoom),
    y: cursorY - (cursorY - panOffset.value.y) * (newZoom / oldZoom),
  };

  zoomLevel.value = newZoom;
  userHasInteracted.value = true;
}

function onMouseDown(e) {
  if (e.button === 0 && !e.target.closest(".expand-button") && !e.target.closest(".node-rect")) {
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

// Watch for tree changes to update size
watch(
  () => tree.value,
  () => {
    updateSize();
  },
  { deep: true }
);
</script>

<style scoped>
.lineage-graph-container {
  position: relative;
  width: 100%;
  height: 100%;
  background: var(--surface-ground);
  overflow: hidden;
  cursor: grab;
}

.lineage-graph-container:active {
  cursor: grabbing;
}

.loading-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 1rem;
  background: var(--surface-ground);
  color: var(--text-color-secondary);
}

.node-group {
  cursor: pointer;
}

.node-rect {
  transition: filter 0.15s ease;
}

.node-rect:hover {
  filter: brightness(0.95);
}

.node-filename {
  font-size: 11px;
  font-weight: 600;
  fill: var(--text-color);
  pointer-events: auto;
  cursor: pointer;
}

.node-stage {
  font-size: 8px;
  font-weight: 700;
  fill: var(--text-color);
  text-transform: uppercase;
  pointer-events: none;
}

.node-datasource {
  font-size: 9px;
  fill: var(--text-color-secondary);
  pointer-events: auto;
  cursor: pointer;
}

/* Edges */
.edge-path {
  fill: none;
  stroke: var(--surface-400);
  stroke-width: 1.5;
  pointer-events: none;
}

/* Expand buttons */
.expand-button {
  cursor: pointer;
}

.expand-button-bg {
  fill: var(--surface-200);
  stroke: var(--surface-400);
  stroke-width: 1;
  transition: fill 0.15s ease;
}

.expand-button:hover .expand-button-bg {
  fill: var(--primary-100);
  stroke: var(--primary-500);
}

.expand-button-icon {
  font-size: 14px;
  font-weight: bold;
  fill: var(--text-color);
  pointer-events: none;
}

/* Zoom controls */
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
  gap: 8px 12px;
  max-width: 400px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
}

.legend-badge {
  display: inline-block;
  padding: 2px 6px;
  border-radius: 3px;
  font-size: 8px;
  font-weight: 700;
}

.legend-badge.complete {
  background: var(--green-200);
  color: var(--green-900);
}

.legend-badge.in-flight {
  background: var(--blue-200);
  color: var(--blue-900);
}

.legend-badge.error {
  background: var(--red-200);
  color: var(--red-900);
}

.legend-badge.cancelled {
  background: var(--surface-300);
  color: var(--surface-700);
}

.legend-hint {
  color: var(--text-color-secondary);
  font-style: italic;
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
