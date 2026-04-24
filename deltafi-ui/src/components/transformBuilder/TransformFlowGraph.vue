<!--
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>

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

<!-- ABOUTME: SVG graph component for visualizing transform pipeline. -->
<!-- ABOUTME: Shows subscribe/actions/publish in horizontal lanes with connecting edges. -->

<template>
  <div class="transform-flow-graph" ref="containerRef">
    <div
      v-if="tooltip.visible"
      class="node-tooltip"
      :style="{ left: tooltip.x + 'px', top: tooltip.y + 'px' }"
    >{{ tooltip.text }}</div>
    <svg
      :viewBox="`${viewBox.x} ${viewBox.y} ${viewBox.width} ${viewBox.height}`"
      :width="svgWidth"
      :height="svgHeight"
      preserveAspectRatio="xMidYMid meet"
    >
      <defs>
        <marker
          id="arrowhead"
          markerWidth="10"
          markerHeight="7"
          refX="9"
          refY="3.5"
          orient="auto"
          markerUnits="strokeWidth"
        >
          <polygon points="0 0, 10 3.5, 0 7" fill="var(--surface-500)" />
        </marker>
      </defs>

      <!-- Lane backgrounds and separators -->
      <g class="lanes">
        <g v-for="(lane, index) in lanes" :key="lane.label">
          <!-- Lane separator (dashed line) -->
          <line
            v-if="index > 0"
            :x1="0"
            :y1="lane.y"
            :x2="viewBox.width"
            :y2="lane.y"
            class="lane-separator"
          />
          <!-- Lane label in upper-left -->
          <text
            :x="10"
            :y="lane.y + 22"
            class="lane-label"
          >{{ lane.label }}</text>
          <!-- Publish description with settings icon (always visible at bottom) -->
          <g v-if="lane.label === 'PUBLISH'" class="publish-description-group">
            <g
              class="settings-button"
              :transform="`translate(${18}, ${lane.y + lane.height - 18})`"
              role="button"
              tabindex="0"
              aria-label="Publish settings"
              @click="$emit('open-publish-settings')"
              @keydown.enter="$emit('open-publish-settings')"
            >
              <circle r="8" class="settings-button-bg" />
              <text dy="0.35em" text-anchor="middle" class="settings-button-icon fas">&#xf013;</text>
            </g>
            <text
              :x="32"
              :y="lane.y + lane.height - 14"
              class="publish-description"
            >{{ publishDescription || 'Publishes to all matching topics. If no topics match, raise an error.' }}</text>
          </g>
        </g>
      </g>

      <!-- Edges -->
      <g class="edges">
        <path
          v-for="edge in edges"
          :key="edge.id"
          :d="edge.path"
          class="edge"
          marker-end="url(#arrowhead)"
        />
      </g>

      <!-- Drop zones (visible when dragging) -->
      <g v-if="isDragging" class="drop-zones">
        <g
          v-for="(zone, index) in dropZones"
          :key="`drop-${index}`"
          :transform="`translate(${zone.x}, ${zone.y})`"
          class="drop-zone"
          :class="{ active: activeDropZone === index }"
        >
          <rect
            :x="-20"
            :y="-config.nodeHeight/2 - 5"
            width="40"
            :height="config.nodeHeight + 10"
            rx="4"
            class="drop-zone-rect"
          />
          <line
            x1="0"
            :y1="-config.nodeHeight/2"
            x2="0"
            :y2="config.nodeHeight/2"
            class="drop-zone-line"
          />
        </g>
      </g>

      <!-- Delete zone (thin stripe that expands when dragging) -->
      <g class="delete-zone" :class="{ dragging: isDragging, active: isOverTrash }">
        <rect
          x="0"
          :y="viewBox.height - deleteZoneHeight"
          :width="viewBox.width"
          :height="deleteZoneHeight"
          class="delete-zone-rect"
        />
        <g
          v-if="isDragging"
          :transform="`translate(${viewBox.width / 2}, ${viewBox.height - deleteZoneHeight / 2})`"
          class="delete-zone-label"
        >
          <g class="delete-zone-icon" transform="translate(-88, -10)">
            <rect x="3" y="3" width="14" height="2" rx="1" />
            <rect x="6" y="1" width="8" height="3" rx="1" />
            <rect x="5" y="5" width="10" height="12" rx="1.5" />
            <line x1="8" y1="8" x2="8" y2="14" />
            <line x1="10" y1="8" x2="10" y2="14" />
            <line x1="12" y1="8" x2="12" y2="14" />
          </g>
          <text
            x="-58"
            y="4"
            text-anchor="start"
            class="delete-zone-text"
          >Drop here to delete</text>
        </g>
      </g>

      <!-- Nodes -->
      <g class="nodes">
        <!-- eslint-disable-next-line vuejs-accessibility/no-static-element-interactions -- SVG node group with tooltip on hover/focus -->
        <g
          v-for="node in nodes"
          :key="node.id"
          :transform="`translate(${node.x}, ${node.y})`"
          :class="{ 'dragging-source': isDragging && draggedNode?.id === node.id }"
          role="group"
          :aria-label="node.name"
          @mouseenter="(e) => showTooltip(e, node)"
          @mouseleave="hideTooltip"
          @focusin="(e) => showTooltip(e, node)"
          @focusout="hideTooltip"
        >
          <TransformFlowNode
            :name="node.name"
            :node-type="node.nodeType"
            :node-width="config.nodeWidth"
            :node-height="config.nodeHeight"
            :selected="selectedNodeId === node.id"
            :has-error="hasNodeError(node)"
            :condition="node.condition"
            role="button"
            tabindex="0"
            :aria-label="`Edit ${node.name}`"
            @click="handleNodeClick(node)"
            @keydown.enter="handleNodeClick(node)"
            @mousedown="(e) => handleDragStart(e, node)"
          />
        </g>
      </g>

      <!-- Dragged node ghost (follows cursor) -->
      <g
        v-if="isDragging && draggedNode"
        class="dragged-ghost"
        :transform="`translate(${dragPosition.x}, ${dragPosition.y})`"
      >
        <TransformFlowNode
          :name="draggedNode.name"
          :node-type="draggedNode.nodeType"
          :node-width="config.nodeWidth"
          :node-height="config.nodeHeight"
          :selected="false"
          :has-error="false"
        />
      </g>

      <!-- Add buttons -->
      <g class="add-buttons">
        <g
          v-for="(btn, index) in addButtonPositions"
          :key="`add-${btn.buttonType}-${index}`"
          :transform="`translate(${btn.x}, ${btn.y})`"
          class="add-button"
          role="button"
          tabindex="0"
          :aria-label="`Add ${btn.buttonType}`"
          @click="handleAddClick(btn)"
          @keydown.enter="handleAddClick(btn)"
        >
          <circle r="10" class="add-button-circle" />
          <text dy="0.35em" text-anchor="middle" class="add-button-text">+</text>
          <!-- Error badge -->
          <g v-if="addButtonError(btn)" class="error-badge" transform="translate(9, -9)">
            <circle r="6" class="error-badge-circle" />
            <text dy="0.35em" text-anchor="middle" class="error-badge-text fas">&#xf129;</text>
          </g>
          <!-- Error hint label -->
          <text
            v-if="addButtonError(btn)"
            :y="24"
            text-anchor="middle"
            class="add-button-hint"
          >{{ addButtonError(btn) }}</text>
        </g>
      </g>
    </svg>
  </div>
</template>

<script setup lang="ts">
// ABOUTME: SVG graph component for visualizing transform pipeline.
// ABOUTME: Renders lanes with labels, nodes, edges, and controls.

import { computed, ref, toRef, onMounted, onUnmounted } from "vue";
import TransformFlowNode from "./TransformFlowNode.vue";
import useTransformLayout, {
  SubscribeTopic,
  PublishRule,
  TransformAction,
  LayoutNode,
  AddButtonPosition,
} from "./composables/useTransformLayout";

interface PublishSettings {
  matchingPolicy?: string;
  defaultRule?: {
    defaultBehavior?: string;
    topic?: string;
  };
}

interface Props {
  subscribeTopics: SubscribeTopic[];
  actions: TransformAction[];
  publishRules: PublishRule[];
  publishSettings?: PublishSettings | null;
  selectedNodeId?: string | null;
  actionErrors?: Map<string, string>;
  subscribeError?: string | null;
  publishError?: string | null;
}

const props = withDefaults(defineProps<Props>(), {
  selectedNodeId: null,
  publishSettings: null,
  actionErrors: () => new Map(),
  subscribeError: null,
  publishError: null,
});

const publishDescription = computed(() => {
  const settings = props.publishSettings;
  if (!settings) return null;

  const matchingText = settings.matchingPolicy === 'FIRST_MATCHING'
    ? 'first matching topic'
    : 'all matching topics';

  let defaultText = 'raise an error';
  if (settings.defaultRule?.defaultBehavior === 'FILTER') {
    defaultText = 'filter (discard)';
  } else if (settings.defaultRule?.defaultBehavior === 'PUBLISH') {
    const topic = settings.defaultRule?.topic || 'default topic';
    defaultText = `publish to "${topic}"`;
  }

  return `Publishes to ${matchingText}. If no topics match, ${defaultText}.`;
});

const emit = defineEmits<{
  'node-click': [node: LayoutNode];
  'add-action': [insertIndex: number];
  'add-subscribe': [insertIndex: number];
  'add-publish': [insertIndex: number];
  'reorder-action': [fromIndex: number, toIndex: number];
  'reorder-subscribe': [fromIndex: number, toIndex: number];
  'reorder-publish': [fromIndex: number, toIndex: number];
  'delete-action': [index: number];
  'delete-subscribe': [index: number];
  'delete-publish': [index: number];
  'open-publish-settings': [];
  'update:selectedNodeId': [nodeId: string | null];
}>();

const containerRef = ref<HTMLElement | null>(null);
const svgWidth = ref(800);
const svgHeight = ref(450);

const { nodes, edges, viewBox, addButtonPositions, lanes, config } = useTransformLayout(
  toRef(props, 'subscribeTopics'),
  toRef(props, 'actions'),
  toRef(props, 'publishRules'),
  svgWidth
);

// Tooltip state
const tooltip = ref({ visible: false, text: '', x: 0, y: 0 });
const maxLabelLength = computed(() => Math.floor(config.nodeWidth / 7.5));

const showTooltip = (event: MouseEvent | FocusEvent, node: LayoutNode) => {
  if (node.name.length <= maxLabelLength.value) return;
  const container = containerRef.value;
  if (!container) return;
  const rect = container.getBoundingClientRect();
  if ('clientX' in event) {
    tooltip.value = {
      visible: true,
      text: node.name,
      x: event.clientX - rect.left,
      y: event.clientY - rect.top - 30,
    };
  } else {
    tooltip.value = {
      visible: true,
      text: node.name,
      x: node.x + config.nodeWidth / 2,
      y: node.y - 10,
    };
  }
};

const hideTooltip = () => {
  tooltip.value = { ...tooltip.value, visible: false };
};

// Drag and drop state
const deleteZoneHeight = 72;
const isDragging = ref(false);
const draggedNode = ref<LayoutNode | null>(null);
const dragPosition = ref({ x: 0, y: 0 });
const activeDropZone = ref<number | null>(null);
const isOverTrash = ref(false);

// Compute drop zones for the currently dragged node type.
// Positions are derived from actual node coordinates so they
// work correctly with wrapped multi-row layouts.
const dropZones = computed(() => {
  if (!draggedNode.value) return [];

  const zones: { x: number; y: number; insertIndex: number; nodeType: string }[] = [];
  const { nodeWidth, horizontalGap } = config;
  const dragType = draggedNode.value.nodeType;

  const relevantNodes = nodes.value.filter(n => n.nodeType === dragType);

  if (relevantNodes.length === 0) return zones;

  // Zone before first node
  zones.push({
    x: relevantNodes[0].x - nodeWidth / 2 - horizontalGap / 2,
    y: relevantNodes[0].y,
    insertIndex: 0,
    nodeType: dragType,
  });

  // Zones after each node
  for (let i = 0; i < relevantNodes.length; i++) {
    zones.push({
      x: relevantNodes[i].x + nodeWidth / 2 + horizontalGap / 2,
      y: relevantNodes[i].y,
      insertIndex: i + 1,
      nodeType: dragType,
    });
  }

  return zones;
});

const getSvgPoint = (event: MouseEvent) => {
  const svg = containerRef.value?.querySelector('svg');
  if (!svg) return { x: 0, y: 0 };

  const rect = svg.getBoundingClientRect();
  const scaleX = viewBox.value.width / rect.width;
  const scaleY = viewBox.value.height / rect.height;

  return {
    x: (event.clientX - rect.left) * scaleX + viewBox.value.x,
    y: (event.clientY - rect.top) * scaleY + viewBox.value.y,
  };
};

const handleDragStart = (event: MouseEvent, node: LayoutNode) => {
  event.preventDefault();
  hideTooltip();
  isDragging.value = true;
  draggedNode.value = node;

  const point = getSvgPoint(event);
  dragPosition.value = { x: point.x, y: point.y };

  // Add global listeners for drag
  document.addEventListener('mousemove', handleGlobalMouseMove);
  document.addEventListener('mouseup', handleGlobalMouseUp);
};

const handleGlobalMouseMove = (event: MouseEvent) => {
  if (!isDragging.value) return;

  const point = getSvgPoint(event);

  // Clamp position to viewBox bounds
  const clampedX = Math.max(0, Math.min(viewBox.value.width, point.x));
  const clampedY = Math.max(0, Math.min(viewBox.value.height - 5, point.y));
  dragPosition.value = { x: clampedX, y: clampedY };

  // Check if over delete zone - use raw Y to detect when dragging below the graph
  const deleteZoneY = viewBox.value.height - deleteZoneHeight;
  isOverTrash.value = point.y >= deleteZoneY;

  // Find nearest drop zone
  if (!isOverTrash.value) {
    let nearestIndex: number | null = null;
    let nearestDist = Infinity;

    for (let i = 0; i < dropZones.value.length; i++) {
      const zone = dropZones.value[i];
      // Skip the zones adjacent to the dragged node
      if (draggedNode.value?.index !== undefined) {
        if (zone.insertIndex === draggedNode.value.index ||
            zone.insertIndex === draggedNode.value.index + 1) {
          continue;
        }
      }

      const dist = Math.abs(clampedX - zone.x) + Math.abs(clampedY - zone.y) * 0.5;
      if (dist < nearestDist && dist < 100) {
        nearestDist = dist;
        nearestIndex = i;
      }
    }

    activeDropZone.value = nearestIndex;
  } else {
    activeDropZone.value = null;
  }
};

const handleGlobalMouseUp = () => {
  document.removeEventListener('mousemove', handleGlobalMouseMove);
  document.removeEventListener('mouseup', handleGlobalMouseUp);
  handleMouseUp();
};

const handleMouseUp = () => {
  if (!isDragging.value || draggedNode.value === null) {
    isDragging.value = false;
    return;
  }

  // Capture state immediately before anything can change it
  const shouldDelete = isOverTrash.value;
  const dropZoneIndex = activeDropZone.value;
  const fromIndex = draggedNode.value.index;
  const nodeType = draggedNode.value.nodeType;

  if (shouldDelete && fromIndex !== undefined) {
    if (nodeType === 'action') {
      emit('delete-action', fromIndex);
    } else if (nodeType === 'subscribe') {
      emit('delete-subscribe', fromIndex);
    } else if (nodeType === 'publish') {
      emit('delete-publish', fromIndex);
    }
  } else if (dropZoneIndex !== null && fromIndex !== undefined) {
    const toIndex = dropZones.value[dropZoneIndex].insertIndex;
    if (toIndex !== fromIndex && toIndex !== fromIndex + 1) {
      const finalIndex = toIndex > fromIndex ? toIndex - 1 : toIndex;
      if (nodeType === 'action') {
        emit('reorder-action', fromIndex, finalIndex);
      } else if (nodeType === 'subscribe') {
        emit('reorder-subscribe', fromIndex, finalIndex);
      } else if (nodeType === 'publish') {
        emit('reorder-publish', fromIndex, finalIndex);
      }
    }
  }

  // Reset drag state
  isDragging.value = false;
  draggedNode.value = null;
  activeDropZone.value = null;
  isOverTrash.value = false;
};

const hasNodeError = (node: LayoutNode): boolean => {
  if (node.nodeType === 'action' && node.data) {
    const action = node.data as TransformAction;
    return props.actionErrors.has(action.id);
  }
  return false;
};

const handleNodeClick = (node: LayoutNode) => {
  emit('node-click', node);
  emit('update:selectedNodeId', node.id);
};

const addButtonError = (btn: AddButtonPosition): string | null => {
  if (btn.buttonType === 'subscribe' && props.subscribeError) return props.subscribeError;
  if (btn.buttonType === 'publish' && props.publishError) return props.publishError;
  return null;
};

const handleAddClick = (btn: AddButtonPosition) => {
  if (btn.buttonType === 'action') {
    emit('add-action', btn.insertIndex);
  } else if (btn.buttonType === 'subscribe') {
    emit('add-subscribe', btn.insertIndex);
  } else if (btn.buttonType === 'publish') {
    emit('add-publish', btn.insertIndex);
  }
};

let resizeObserver: ResizeObserver | null = null;

onMounted(() => {
  if (containerRef.value) {
    resizeObserver = new ResizeObserver((entries) => {
      const entry = entries[0];
      if (entry) {
        svgWidth.value = entry.contentRect.width;
        svgHeight.value = viewBox.value.height;
      }
    });
    resizeObserver.observe(containerRef.value);
  }
});

onUnmounted(() => {
  if (resizeObserver) {
    resizeObserver.disconnect();
  }
  // Clean up any lingering drag listeners
  document.removeEventListener('mousemove', handleGlobalMouseMove);
  document.removeEventListener('mouseup', handleGlobalMouseUp);
});
</script>

<style scoped>
.transform-flow-graph {
  position: relative;
  width: 100%;
  min-height: 350px;
  background: var(--surface-ground);
  border: 1px solid var(--surface-border);
  border-radius: 6px;
  overflow: hidden;
}

.node-tooltip {
  position: absolute;
  background: var(--surface-900);
  color: white;
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 12px;
  pointer-events: none;
  white-space: nowrap;
  z-index: 10;
  transform: translateX(-50%);
}

.transform-flow-graph svg {
  display: block;
}

/* Lanes */
.lane-separator {
  stroke: var(--surface-300);
  stroke-width: 1;
}

.lane-label {
  fill: var(--text-color-secondary);
  font-size: 15px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.publish-description {
  fill: var(--text-color-secondary);
  font-size: 13px;
  font-style: italic;
}

/* Settings button */
.settings-button {
  cursor: pointer;
}

.settings-button-bg {
  fill: transparent;
  transition: fill 0.15s;
}

.settings-button:hover .settings-button-bg {
  fill: var(--surface-hover);
}

.settings-button-icon {
  fill: var(--text-color-secondary);
  font-size: 11px;
}

.settings-button:hover .settings-button-icon {
  fill: var(--primary-color);
}

/* Edges */
.edge {
  fill: none;
  stroke: var(--surface-500);
  stroke-width: 1.5;
}

/* Add buttons */
.add-button {
  cursor: pointer;
}

.add-button-circle {
  fill: var(--surface-card);
  stroke: var(--primary-color);
  stroke-width: 1.5;
  transition: all 0.15s;
}

.add-button:hover .add-button-circle {
  fill: var(--primary-color);
  stroke-dasharray: none;
}

.add-button-text {
  fill: var(--primary-color);
  font-size: 14px;
  font-weight: bold;
  pointer-events: none;
}

.add-button:hover .add-button-text {
  fill: white;
}

/* Add button error badge */
.add-button .error-badge-circle {
  fill: var(--red-500);
  stroke: white;
  stroke-width: 1;
}

.add-button .error-badge-text {
  fill: white;
  font-size: 7px;
  pointer-events: none;
}

.add-button-hint {
  fill: var(--text-color-secondary);
  font-size: 11px;
  font-style: italic;
  pointer-events: none;
}

/* Drag and drop styles */
.dragging-source {
  opacity: 0.3;
}

.dragged-ghost {
  pointer-events: none;
  opacity: 0.9;
  filter: drop-shadow(0 4px 8px rgba(0, 0, 0, 0.3));
}

/* Drop zones */
.drop-zone {
  pointer-events: none;
}

.drop-zone-rect {
  fill: transparent;
  stroke: transparent;
  transition: all 0.15s;
}

.drop-zone-line {
  stroke: var(--surface-400);
  stroke-width: 2;
  stroke-dasharray: 4 4;
  transition: all 0.15s;
}

.drop-zone.active .drop-zone-rect {
  fill: var(--df-status-selected-bg);
  stroke: var(--primary-color);
  stroke-width: 2;
}

.drop-zone.active .drop-zone-line {
  stroke: var(--primary-color);
  stroke-width: 3;
  stroke-dasharray: none;
}

/* Delete zone */
.delete-zone-rect {
  fill: var(--df-subtle-bg-alt);
  transition: all 0.2s;
}

.delete-zone-text {
  fill: var(--text-color-secondary);
  font-size: 13px;
  opacity: 0;
  transition: all 0.2s;
}

.delete-zone-icon {
  fill: none;
  stroke: var(--text-color-secondary);
  stroke-width: 1.6;
  stroke-linecap: round;
  stroke-linejoin: round;
  opacity: 0;
  transition: all 0.2s;
}

.delete-zone.dragging .delete-zone-rect {
  fill: var(--df-subtle-border);
}

.delete-zone.dragging .delete-zone-text {
  opacity: 1;
}

.delete-zone.dragging .delete-zone-icon {
  opacity: 1;
}

.delete-zone.active .delete-zone-rect {
  fill: var(--df-status-stopped-bg);
}

.delete-zone.active .delete-zone-text {
  fill: var(--red-500);
  font-weight: 600;
}

.delete-zone.active .delete-zone-icon {
  stroke: var(--red-500);
}
</style>
