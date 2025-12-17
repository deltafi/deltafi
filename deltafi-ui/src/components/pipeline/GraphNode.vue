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

<!-- ABOUTME: Shared SVG node component for pipeline and system map graphs. -->
<!-- ABOUTME: Renders node rectangle, labels, controls, error badges, and metrics. -->

<template>
  <g
    class="node-group"
    :class="{ selected: selected, focused: focused, [nodeTypeClass]: true }"
    @click="$emit('click', $event)"
  >
    <!-- Queue indicator (left side, near incoming arrow) -->
    <g
      v-if="node.type !== 'TOPIC' && queueCounts && queueCounts.total > 0"
      class="queue-indicator"
      :transform="`translate(-${nodeWidth / 2 + (showInputMetrics ? metricsBoxWidth : 0) + 38}, 0)`"
      @mouseenter="$emit('show-tooltip', $event, queueTooltip)"
      @mouseleave="$emit('hide-tooltip')"
    >
      <rect x="-18" y="-10" width="36" height="20" rx="10" class="queue-indicator-bg" />
      <text dy="0.35em" text-anchor="middle" class="queue-indicator-text">
        {{ formatQueueCount(queueCounts.total) }}
      </text>
    </g>

    <!-- Expand upstream button (Detail view only) -->
    <g
      v-if="canExpandUpstream"
      class="expand-button expand-upstream"
      @click.stop="$emit('expand-upstream')"
      @mouseenter="$emit('show-tooltip', $event, 'Show upstream neighbors')"
      @mouseleave="$emit('hide-tooltip')"
      :transform="`translate(-${nodeWidth / 2 + (showInputMetrics ? metricsBoxWidth : 0) + 20}, 0)`"
    >
      <circle r="12" />
      <text dy="0.35em" text-anchor="middle">+</text>
    </g>

    <!-- Node rectangle (extended to include metrics boxes for flow nodes) -->
    <rect
      :x="showInputMetrics ? -nodeWidth / 2 - metricsBoxWidth : -nodeWidth / 2"
      :y="-nodeHeight / 2"
      :width="totalNodeWidth"
      :height="nodeHeight"
      :rx="node.type === 'TOPIC' ? nodeHeight / 2 : 4"
      class="node-rect"
    />

    <!-- Tinted background for input metrics area -->
    <rect
      v-if="showInputMetrics"
      :x="-nodeWidth / 2 - metricsBoxWidth"
      :y="-nodeHeight / 2"
      :width="metricsBoxWidth"
      :height="nodeHeight"
      rx="4"
      class="metrics-area-bg"
      :fill="metricsBackgroundColor"
    />

    <!-- Tinted background for output metrics area -->
    <rect
      v-if="showOutputMetrics"
      :x="nodeWidth / 2"
      :y="-nodeHeight / 2"
      :width="metricsBoxWidth"
      :height="nodeHeight"
      rx="4"
      class="metrics-area-bg"
      :fill="metricsBackgroundColor"
    />


    <!-- Error badge - positioned at top-right, outside output metrics box if present -->
    <g
      v-if="node.type !== 'TOPIC' && errorCount > 0"
      class="error-badge"
      :transform="`translate(${nodeWidth / 2 + (showOutputMetrics ? metricsBoxWidth : 0)}, ${-nodeHeight / 2})`"
      @click.stop="$emit('error-badge-click')"
      @mouseenter="$emit('show-tooltip', $event, errorCount + (errorCount === 1 ? ' error' : ' errors') + ' - click to view')"
      @mouseleave="$emit('hide-tooltip')"
    >
      <circle r="10" class="error-badge-circle" />
      <text dy="0.35em" text-anchor="middle" class="error-badge-text">
        {{ formatErrorCount(errorCount) }}
      </text>
    </g>

    <!-- Node label -->
    <text
      class="node-label"
      :class="{ truncated: isTruncated }"
      :dy="labelDy"
      text-anchor="middle"
      @mouseenter="isTruncated && $emit('show-tooltip', $event, node.name)"
      @mouseleave="isTruncated && $emit('hide-tooltip')"
    >
      {{ displayLabel }}
    </text>

    <!-- Flow controls (for flows, not topics) -->
    <g v-if="isControllableFlow(node.type)" class="flow-controls" :transform="`translate(0, ${controlsY})`">
      <!-- Start button -->
      <g
        class="control-button start-button"
        :class="{ active: node.state === 'RUNNING' }"
        @click.stop="$emit('flow-state-change', 'RUNNING')"
        :transform="`translate(-${controlSpacing}, 0)`"
      >
        <rect :x="-controlWidth / 2" :y="-controlHeight / 2" :width="controlWidth" :height="controlHeight" rx="2" />
        <polygon :points="playIconPoints" class="control-icon" />
      </g>

      <!-- Pause button -->
      <g
        class="control-button pause-button"
        :class="{ active: node.state === 'PAUSED' }"
        @click.stop="$emit('flow-state-change', 'PAUSED')"
        transform="translate(0, 0)"
      >
        <rect :x="-controlWidth / 2" :y="-controlHeight / 2" :width="controlWidth" :height="controlHeight" rx="2" />
        <rect :x="-pauseBarSpacing" :y="-pauseBarHeight / 2" :width="pauseBarWidth" :height="pauseBarHeight" class="control-icon" />
        <rect :x="pauseBarSpacing - pauseBarWidth" :y="-pauseBarHeight / 2" :width="pauseBarWidth" :height="pauseBarHeight" class="control-icon" />
      </g>

      <!-- Stop button -->
      <g
        class="control-button stop-button"
        :class="{ active: node.state === 'STOPPED' }"
        @click.stop="$emit('flow-state-change', 'STOPPED')"
        :transform="`translate(${controlSpacing}, 0)`"
      >
        <rect :x="-controlWidth / 2" :y="-controlHeight / 2" :width="controlWidth" :height="controlHeight" rx="2" />
        <rect :x="-stopSize / 2" :y="-stopSize / 2" :width="stopSize" :height="stopSize" class="control-icon" />
      </g>
    </g>

    <!-- Status badge (lower left corner) - for INVALID or PLUGIN states -->
    <g
      v-if="statusBadge"
      class="status-badge"
      :class="statusBadge.severity"
      :transform="`translate(${-nodeWidth / 2 + 30}, ${controlsY})`"
      @mouseenter="$emit('show-tooltip', $event, statusBadge.tooltip)"
      @mouseleave="$emit('hide-tooltip')"
    >
      <rect x="-26" y="-7" width="52" height="14" rx="3" class="status-badge-bg" />
      <text class="status-badge-icon fas" x="-20" dy="0.35em" text-anchor="middle">{{ statusBadge.icon }}</text>
      <text class="status-badge-text" x="4" dy="0.35em" text-anchor="middle">{{ statusBadge.label }}</text>
    </g>

    <!-- Type icon (lower right corner) -->
    <text
      class="node-type-icon fas"
      :x="node.type === 'TOPIC' ? 0 : nodeWidth / 2 - 12"
      :y="nodeHeight / 2 - 6"
      text-anchor="middle"
    >{{ typeIcon }}</text>

    <!-- Metrics text inside extended node areas (only show when there's data) -->
    <!-- Output metrics (right side) - for data sources and transforms -->
    <g
      v-if="showOutputMetrics && hasOutputMetrics"
      class="metrics-area"
      :transform="`translate(${nodeWidth / 2 + metricsBoxWidth / 2}, 0)`"
    >
      <!-- File count: number right-aligned, icon in fixed column -->
      <text class="metrics-text" x="10" dy="-4" text-anchor="end">
        {{ formatMetricNumber(metrics.filesOut) }}
      </text>
      <text class="metrics-icon fa-solid" x="18" dy="-4" text-anchor="middle">&#xf15b;</text>
      <!-- Bytes: number right-aligned, unit in fixed column -->
      <text class="metrics-bytes" x="10" dy="10" text-anchor="end">
        {{ formatBytesParts(metrics.bytesOut).value }}
      </text>
      <text class="metrics-unit" x="18" dy="10" text-anchor="middle">
        {{ formatBytesParts(metrics.bytesOut).unit }}
      </text>
    </g>

    <!-- Input metrics (left side) - for data sinks and transforms -->
    <g
      v-if="showInputMetrics && hasInputMetrics"
      class="metrics-area"
      :transform="`translate(${-nodeWidth / 2 - metricsBoxWidth / 2}, 0)`"
    >
      <!-- File count: number right-aligned, icon in fixed column -->
      <text class="metrics-text" x="10" dy="-4" text-anchor="end">
        {{ formatMetricNumber(metrics.filesIn) }}
      </text>
      <text class="metrics-icon fa-solid" x="18" dy="-4" text-anchor="middle">&#xf15b;</text>
      <!-- Bytes: number right-aligned, unit in fixed column -->
      <text class="metrics-bytes" x="10" dy="10" text-anchor="end">
        {{ formatBytesParts(metrics.bytesIn).value }}
      </text>
      <text class="metrics-unit" x="18" dy="10" text-anchor="middle">
        {{ formatBytesParts(metrics.bytesIn).unit }}
      </text>
    </g>

    <!-- Expand downstream button (Detail view only) -->
    <g
      v-if="canExpandDownstream"
      class="expand-button expand-downstream"
      @click.stop="$emit('expand-downstream')"
      @mouseenter="$emit('show-tooltip', $event, 'Show downstream neighbors')"
      @mouseleave="$emit('hide-tooltip')"
      :transform="`translate(${nodeWidth / 2 + (showOutputMetrics ? metricsBoxWidth : 0) + 20}, 0)`"
    >
      <circle r="12" />
      <text dy="0.35em" text-anchor="middle">+</text>
    </g>
  </g>
</template>

<script setup>
// ABOUTME: Shared SVG node component for pipeline and system map graphs.
// ABOUTME: Handles node rendering with configurable dimensions and features.

import { computed } from "vue";
import {
  getNodeTypeClass,
  isControllableFlow,
  isDataSourceType,
  formatMetricNumber,
  formatBytesParts,
  formatErrorCount,
  formatQueueCount,
} from "./useGraphStyles";

const props = defineProps({
  node: {
    type: Object,
    required: true,
  },
  nodeWidth: {
    type: Number,
    required: true,
  },
  nodeHeight: {
    type: Number,
    required: true,
  },
  horizontalGap: {
    type: Number,
    required: true,
  },
  selected: {
    type: Boolean,
    default: false,
  },
  focused: {
    type: Boolean,
    default: false,
  },
  canExpandUpstream: {
    type: Boolean,
    default: false,
  },
  canExpandDownstream: {
    type: Boolean,
    default: false,
  },
  errorCount: {
    type: Number,
    default: 0,
  },
  queueCounts: {
    type: Object,
    default: null,
  },
  metrics: {
    type: Object,
    default: null,
  },
  // Size variant for different views
  compact: {
    type: Boolean,
    default: false,
  },
});

defineEmits([
  "click",
  "expand-upstream",
  "expand-downstream",
  "error-badge-click",
  "flow-state-change",
  "show-tooltip",
  "hide-tooltip",
]);

// Computed properties for node type class
const nodeTypeClass = computed(() => getNodeTypeClass(props.node.type));

// Max characters for label based on compact mode
const maxLabelLength = computed(() => (props.compact ? 28 : 34));

// Check if name is truncated
const isTruncated = computed(() => props.node.name.length > maxLabelLength.value);

// Display label with truncation
const displayLabel = computed(() => {
  if (isTruncated.value) {
    return props.node.name.slice(0, maxLabelLength.value - 1) + "…";
  }
  return props.node.name;
});

// Check if metrics exist
const hasMetrics = computed(() => {
  const m = props.metrics;
  return m !== null && (m.filesIn > 0 || m.filesOut > 0 || m.bytesIn > 0 || m.bytesOut > 0);
});

// Check if input/output metrics exist separately
const hasInputMetrics = computed(() => {
  const m = props.metrics;
  return m !== null && (m.filesIn > 0 || m.bytesIn > 0);
});

const hasOutputMetrics = computed(() => {
  const m = props.metrics;
  return m !== null && (m.filesOut > 0 || m.bytesOut > 0);
});

// Metrics box width (must match value used in graph edge calculations)
const metricsBoxWidth = 52;

// Show output metrics box (right side) for data sources and transforms - always present
const showOutputMetrics = computed(() => {
  return isDataSourceType(props.node.type) || props.node.type === "TRANSFORM";
});

// Show input metrics box (left side) for data sinks and transforms - always present
const showInputMetrics = computed(() => {
  return props.node.type === "DATA_SINK" || props.node.type === "TRANSFORM";
});

// Total node width including metrics boxes
const totalNodeWidth = computed(() => {
  let width = props.nodeWidth;
  if (showInputMetrics.value) width += metricsBoxWidth;
  if (showOutputMetrics.value) width += metricsBoxWidth;
  return width;
});

// Layout calculations based on compact mode
const badgeOffset = computed(() => (props.compact ? 14 : 16));
const labelDy = computed(() => (isControllableFlow(props.node.type) ? (props.compact ? -4 : -8) : 0));
const controlsY = computed(() => (props.compact ? 8 : 10));

// Control button dimensions
const controlWidth = computed(() => (props.compact ? 16 : 24));
const controlHeight = computed(() => (props.compact ? 14 : 20));
const controlSpacing = computed(() => (props.compact ? 20 : 30));

// Play icon points
const playIconPoints = computed(() => {
  if (props.compact) {
    return "-3,-4 5,0 -3,4";
  }
  return "-4,-6 8,0 -4,6";
});

// Pause bar dimensions
const pauseBarWidth = computed(() => (props.compact ? 3 : 4));
const pauseBarHeight = computed(() => (props.compact ? 8 : 10));
const pauseBarSpacing = computed(() => (props.compact ? 4 : 5));

// Stop icon size
const stopSize = computed(() => (props.compact ? 8 : 10));

// Expose compact for template
const compact = computed(() => props.compact);

// Background color for metrics areas based on node type (slightly darker than main node)
const metricsBackgroundColor = computed(() => {
  const type = props.node.type;
  if (isDataSourceType(type)) {
    return "var(--orange-200)";
  } else if (type === "TRANSFORM") {
    return "var(--blue-200)";
  } else if (type === "DATA_SINK") {
    return "var(--green-200)";
  }
  return "var(--surface-300)";
});

// Status badge for INVALID or PLUGIN states (matches FlowStatusBadge logic)
const statusBadge = computed(() => {
  if (props.node.type === "TOPIC") return null;

  // INVALID state: flow has validation errors
  if (props.node.valid === false) {
    const errorMessages = props.node.errors?.map((e) => e.message).join(", ") || "Configuration error";
    return {
      label: "INVALID",
      icon: "\uf06a", // fa-exclamation-circle
      severity: "invalid",
      tooltip: errorMessages,
    };
  }

  // PLUGIN states: only show when flow is RUNNING but plugin isn't ready
  if (props.node.state === "RUNNING" && props.node.pluginReady === false) {
    const isDisabled = props.node.pluginNotReadyReason?.includes("disabled");
    return {
      label: "PLUGIN",
      icon: isDisabled ? "\uf04c" : "\uf017", // fa-pause or fa-clock
      severity: "plugin",
      tooltip: props.node.pluginNotReadyReason || "Plugin not ready",
    };
  }

  return null;
});

// Queue tooltip with warm/cold breakdown
const queueTooltip = computed(() => {
  const q = props.queueCounts;
  if (!q || q.total === 0) return '';

  const parts = [];
  if (q.warm > 0) parts.push(`${q.warm.toLocaleString()} warm`);
  if (q.cold > 0) parts.push(`${q.cold.toLocaleString()} cold`);
  return `Queued: ${parts.join(', ')}`;
});

// Icon for node type (Font Awesome unicode)
const typeIcon = computed(() => {
  const type = props.node.type;
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
});
</script>

<style scoped>
/* Node group */
.node-group {
  cursor: pointer;
}

.node-rect {
  fill: var(--surface-card);
  stroke: var(--surface-border);
  stroke-width: 1;
  transition: all 0.2s ease;
}

.node-group:hover .node-rect {
  stroke: var(--primary-color);
}

.node-group.selected .node-rect,
.node-group.focused .node-rect {
  stroke: var(--primary-color);
  stroke-width: 2;
  filter: drop-shadow(0 0 8px var(--primary-color));
}

.node-label {
  fill: var(--text-color);
  font-size: 11px;
  font-weight: 500;
  pointer-events: none;
}

.node-label.truncated {
  pointer-events: auto;
  cursor: default;
}

.node-type-icon {
  fill: var(--text-color-secondary);
  font-size: 12px;
  pointer-events: none;
}

/* Node type colors */
.node-type-rest-data-source .node-rect,
.node-type-timed-data-source .node-rect,
.node-type-on-error-data-source .node-rect {
  fill: var(--orange-100);
  stroke: var(--orange-500);
}

.node-type-transform .node-rect {
  fill: var(--blue-100);
  stroke: var(--blue-500);
}

.node-type-data-sink .node-rect {
  fill: var(--green-100);
  stroke: var(--green-500);
}

.node-type-topic .node-rect {
  fill: var(--surface-200);
  stroke: var(--surface-500);
}

/* Error badge */
.error-badge {
  cursor: pointer;
}

.error-badge-circle {
  fill: var(--red-500);
  stroke: white;
  stroke-width: 1.5;
}

.error-badge-text {
  fill: white;
  font-size: 9px;
  font-weight: 600;
  pointer-events: none;
}

.error-badge:hover .error-badge-circle {
  fill: var(--red-600);
}

/* Flow control buttons */
.flow-controls {
  pointer-events: all;
}

.control-button {
  cursor: pointer;
}

.control-button rect {
  fill: var(--surface-200);
  stroke: var(--surface-400);
  stroke-width: 1;
  transition: all 0.15s ease;
}

.control-button:hover rect {
  fill: var(--surface-300);
}

.control-button .control-icon {
  fill: var(--text-color-secondary);
  transition: all 0.15s ease;
}

/* Start button */
.control-button.start-button.active rect {
  fill: var(--green-100);
  stroke: var(--green-500);
}

.control-button.start-button.active .control-icon {
  fill: var(--green-600);
}

.control-button.start-button:hover rect {
  fill: var(--green-200);
  stroke: var(--green-500);
}

/* Pause button */
.control-button.pause-button.active rect {
  fill: var(--yellow-100);
  stroke: var(--yellow-500);
}

.control-button.pause-button.active .control-icon {
  fill: var(--yellow-700);
}

.control-button.pause-button:hover rect {
  fill: var(--yellow-200);
  stroke: var(--yellow-500);
}

/* Stop button */
.control-button.stop-button.active rect {
  fill: var(--red-100);
  stroke: var(--red-400);
}

.control-button.stop-button.active .control-icon {
  fill: var(--red-500);
}

.control-button.stop-button:hover rect {
  fill: var(--red-200);
  stroke: var(--red-400);
}

/* Expand buttons */
.expand-button {
  cursor: pointer;
}

.expand-button circle {
  fill: var(--surface-card);
  stroke: var(--primary-color);
  stroke-width: 2;
  transition: all 0.2s ease;
}

.expand-button:hover circle {
  fill: var(--primary-color);
}

.expand-button text {
  fill: var(--primary-color);
  font-size: 16px;
  font-weight: bold;
  pointer-events: none;
}

.expand-button:hover text {
  fill: white;
}

/* Queue indicator */
.queue-indicator {
  cursor: default;
}

.queue-indicator-bg {
  fill: var(--surface-0);
  stroke: var(--surface-400);
  stroke-width: 1;
}

.queue-indicator-text {
  fill: var(--text-color);
  font-size: 10px;
  font-weight: 600;
  pointer-events: none;
}

/* Metrics area background */
.metrics-area-bg {
  opacity: 0.5;
}

/* Metrics text areas */
.metrics-area {
  pointer-events: none;
}

.metrics-text {
  fill: var(--text-color);
  font-size: 10px;
  font-weight: 400;
  font-family: ui-monospace, monospace;
  pointer-events: none;
}

.metrics-bytes {
  fill: var(--text-color);
  font-size: 10px;
  font-weight: 400;
  font-family: ui-monospace, monospace;
}

.metrics-icon {
  fill: var(--text-color-secondary);
  font-size: 8px;
  pointer-events: none;
}

.metrics-unit {
  fill: var(--text-color);
  font-size: 10px;
  font-weight: 400;
  font-family: ui-monospace, monospace;
  pointer-events: none;
}

/* Status badge (INVALID/PLUGIN) */
.status-badge {
  cursor: default;
}

.status-badge-bg {
  stroke-width: 1;
}

.status-badge-icon {
  font-size: 9px;
  pointer-events: none;
}

.status-badge-text {
  font-size: 8px;
  font-weight: 600;
  pointer-events: none;
}

.status-badge.invalid .status-badge-bg {
  fill: var(--red-100);
  stroke: var(--red-500);
}

.status-badge.invalid .status-badge-icon,
.status-badge.invalid .status-badge-text {
  fill: var(--red-600);
}

.status-badge.plugin .status-badge-bg {
  fill: var(--yellow-100);
  stroke: var(--yellow-500);
}

.status-badge.plugin .status-badge-icon,
.status-badge.plugin .status-badge-text {
  fill: var(--yellow-700);
}

</style>
