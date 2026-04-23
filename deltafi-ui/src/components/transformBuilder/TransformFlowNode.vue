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

<!-- ABOUTME: SVG node component for transform flow graph visualization. -->
<!-- ABOUTME: Renders action and topic nodes with selection and validation states. -->

<template>
  <g
    class="transform-flow-node"
    :class="{ selected: selected, [nodeTypeClass]: true, 'has-error': hasError }"
    @click="$emit('click', $event)"
    @mousedown="$emit('mousedown', $event)"
  >
    <!-- Node rectangle -->
    <rect
      :x="-nodeWidth / 2"
      :y="-nodeHeight / 2"
      :width="nodeWidth"
      :height="nodeHeight"
      :rx="nodeType !== 'action' ? nodeHeight / 2 : 4"
      class="node-rect"
    />

    <!-- Error badge -->
    <g
      v-if="hasError"
      class="error-badge"
      :transform="`translate(${nodeWidth / 2 - 4}, ${-nodeHeight / 2 + 4})`"
    >
      <circle r="8" class="error-badge-circle" />
      <text dy="0.35em" text-anchor="middle" class="error-badge-text">!</text>
    </g>

    <!-- Node label -->
    <text
      class="node-label"
      :class="{ truncated: isTruncated }"
      dy="0.35em"
      text-anchor="middle"
    >
      {{ displayLabel }}
    </text>

    <!-- Condition indicator for topics -->
    <text
      v-if="nodeType !== 'action' && condition"
      class="condition-indicator"
      :y="nodeHeight / 2 + 12"
      text-anchor="middle"
    >
      {{ truncateCondition(condition) }}
    </text>

    <!-- Type icon -->
    <text
      v-if="nodeType === 'action'"
      class="node-type-icon fas"
      :x="nodeWidth / 2 - 10"
      :y="nodeHeight / 2 - 8"
      text-anchor="middle"
    >&#xf542;</text>
  </g>
</template>

<script setup lang="ts">
// ABOUTME: SVG node component for transform flow graph visualization.
// ABOUTME: Handles node rendering with selection and validation states.

import { computed } from "vue";

interface Props {
  name: string;
  nodeType: 'subscribe' | 'action' | 'publish';
  nodeWidth?: number;
  nodeHeight?: number;
  selected?: boolean;
  hasError?: boolean;
  condition?: string | null;
}

const props = withDefaults(defineProps<Props>(), {
  nodeWidth: 140,
  nodeHeight: 36,
  selected: false,
  hasError: false,
  condition: null,
});

defineEmits<{
  click: [event: MouseEvent];
  mousedown: [event: MouseEvent];
}>();

const maxLabelLength = computed(() => Math.floor(props.nodeWidth / 7.5));

const isTruncated = computed(() => props.name.length > maxLabelLength.value);

const displayLabel = computed(() => {
  if (isTruncated.value) {
    return props.name.slice(0, maxLabelLength.value - 1) + "…";
  }
  return props.name;
});

const nodeTypeClass = computed(() => {
  if (props.nodeType === 'action') {
    return 'node-type-action';
  }
  return 'node-type-topic';
});

const truncateCondition = (condition: string): string => {
  if (condition.length > 20) {
    return condition.slice(0, 17) + "...";
  }
  return condition;
};
</script>

<style scoped>
.transform-flow-node {
  cursor: grab;
}

.transform-flow-node:active {
  cursor: grabbing;
}

.node-rect {
  fill: var(--surface-card);
  stroke: var(--surface-border);
  stroke-width: 1;
  transition: all 0.2s ease;
}

.transform-flow-node:hover .node-rect {
  stroke: var(--primary-color);
}

.transform-flow-node.selected .node-rect {
  stroke: var(--primary-color);
  stroke-width: 2;
  filter: drop-shadow(0 0 6px var(--primary-color));
}

.transform-flow-node.has-error .node-rect {
  stroke: var(--red-500);
}

.node-label {
  fill: var(--text-color);
  font-size: 11px;
  font-weight: 500;
  pointer-events: none;
}

.node-type-icon {
  fill: var(--text-color-secondary);
  font-size: 10px;
  pointer-events: none;
}

/* Action nodes */
.node-type-action .node-rect {
  fill: var(--df-node-transform-fill);
  stroke: var(--df-node-transform-stroke);
}

/* Topic nodes */
.node-type-topic .node-rect {
  fill: var(--df-node-topic-fill);
  stroke: var(--df-node-topic-stroke);
}

/* Error badge */
.error-badge {
  cursor: default;
}

.error-badge-circle {
  fill: var(--red-500);
  stroke: white;
  stroke-width: 1;
}

.error-badge-text {
  fill: white;
  font-size: 10px;
  font-weight: 700;
  pointer-events: none;
}

/* Condition indicator */
.condition-indicator {
  fill: var(--text-color-secondary);
  font-size: 9px;
  font-style: italic;
  pointer-events: none;
}
</style>
