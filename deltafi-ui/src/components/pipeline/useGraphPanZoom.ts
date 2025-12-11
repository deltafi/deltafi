/*
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
*/

// ABOUTME: Composable for graph pan, zoom, and resize functionality.
// ABOUTME: Shared between PipelineGraph and SystemMapGraph components.

import { ref, computed, onMounted, onUnmounted, watch, type Ref, type ComputedRef } from "vue";

export interface PanZoomOptions {
  minZoom?: number;
  maxZoom?: number;
  excludeSelectors?: string[];
}

export interface LayoutNode {
  id: string;
  x: number;
  y: number;
  [key: string]: unknown;
}

export interface ViewBox {
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface TooltipState {
  visible: boolean;
  text: string;
  x: number;
  y: number;
}

export function useGraphPanZoom(
  graphContainer: Ref<HTMLElement | null>,
  layoutNodes: ComputedRef<LayoutNode[]>,
  nodeWidth: number,
  nodeHeight: number,
  padding: number,
  containerWidth: Ref<number>,
  containerHeight: Ref<number>,
  options: PanZoomOptions = {}
) {
  const { minZoom = 0.25, maxZoom = 2, excludeSelectors = [".node-group"] } = options;

  // Pan and zoom state
  const panOffset = ref({ x: 0, y: 0 });
  const zoomLevel = ref(1);
  const isPanning = ref(false);
  const panStart = ref({ x: 0, y: 0 });
  const userHasInteracted = ref(false);

  // Tooltip state
  const tooltip = ref<TooltipState>({ visible: false, text: "", x: 0, y: 0 });

  // SVG dimensions
  const svgWidth = computed(() => containerWidth.value);
  const svgHeight = computed(() => Math.max(containerHeight.value, 500));

  // Calculate base viewBox to fit all nodes
  // Extra horizontal padding for metrics boxes (~52px) and expand buttons (~32px)
  const horizontalExtra = 90;

  const baseViewBox = computed((): ViewBox => {
    if (!layoutNodes.value.length) {
      return { x: 0, y: 0, width: containerWidth.value, height: containerHeight.value };
    }

    let minX = Infinity,
      maxX = -Infinity,
      minY = Infinity,
      maxY = -Infinity;

    for (const node of layoutNodes.value) {
      minX = Math.min(minX, node.x - nodeWidth / 2 - padding - horizontalExtra);
      maxX = Math.max(maxX, node.x + nodeWidth / 2 + padding + horizontalExtra);
      minY = Math.min(minY, node.y - nodeHeight / 2 - padding);
      maxY = Math.max(maxY, node.y + nodeHeight / 2 + padding);
    }

    return {
      x: minX,
      y: minY,
      width: maxX - minX,
      height: maxY - minY,
    };
  });

  // Detail panel offset - call setDetailPanelOffset when detail panel opens/closes
  const detailPanelOffset = ref(0);

  function setDetailPanelOffset(offset: number) {
    detailPanelOffset.value = offset;
    if (!userHasInteracted.value) {
      panOffset.value = { x: 0, y: -offset / 2 };
    }
  }

  // Apply pan and zoom to viewBox
  const viewBoxString = computed(() => {
    const base = baseViewBox.value;
    const scaledWidth = base.width / zoomLevel.value;
    const scaledHeight = base.height / zoomLevel.value;
    // Simple formula without centering - enables focal zoom toward cursor
    const x = base.x - panOffset.value.x / zoomLevel.value;
    const y = base.y - panOffset.value.y / zoomLevel.value;
    return `${x} ${y} ${scaledWidth} ${scaledHeight}`;
  });

  const isViewModified = computed(() => userHasInteracted.value);

  // ResizeObserver setup - call setupResizeObserver() after component mounts
  let resizeObserver: ResizeObserver | null = null;

  function setupResizeObserver() {
    onMounted(() => {
      if (graphContainer.value) {
        resizeObserver = new ResizeObserver((entries) => {
          for (const entry of entries) {
            containerWidth.value = entry.contentRect.width || 1000;
            containerHeight.value = entry.contentRect.height || 600;
          }
        });
        resizeObserver.observe(graphContainer.value);
      }
    });

    onUnmounted(() => {
      if (resizeObserver) {
        resizeObserver.disconnect();
      }
    });
  }

  // Reset view
  function resetView() {
    panOffset.value = { x: 0, y: -detailPanelOffset.value / 2 };
    zoomLevel.value = 1;
    userHasInteracted.value = false;
  }

  // Pan handlers
  function startPan(event: MouseEvent) {
    const target = event.target as HTMLElement;
    for (const selector of excludeSelectors) {
      if (target.closest(selector)) {
        return;
      }
    }
    isPanning.value = true;
    panStart.value = { x: event.clientX - panOffset.value.x, y: event.clientY - panOffset.value.y };
  }

  function doPan(event: MouseEvent) {
    if (!isPanning.value) return;
    panOffset.value = {
      x: event.clientX - panStart.value.x,
      y: event.clientY - panStart.value.y,
    };
    userHasInteracted.value = true;
  }

  function endPan() {
    isPanning.value = false;
  }

  // Zoom toward a specific point (for focal zoom)
  function zoomAtPoint(factor: number, clientX: number, clientY: number) {
    if (!graphContainer.value) return;

    const rect = graphContainer.value.getBoundingClientRect();
    const cursorX = clientX - rect.left;
    const cursorY = clientY - rect.top;

    const oldZoom = zoomLevel.value;
    const newZoom = Math.max(minZoom, Math.min(maxZoom, oldZoom * factor));
    const zoomRatio = newZoom / oldZoom;

    // Scale cursor from pixels to viewBox units (panOffset is in viewBox units)
    const base = baseViewBox.value;
    const scaledCursorX = cursorX * base.width / containerWidth.value;
    const scaledCursorY = cursorY * base.height / containerHeight.value;

    // Adjust pan to keep cursor point stationary
    panOffset.value = {
      x: scaledCursorX - (scaledCursorX - panOffset.value.x) * zoomRatio,
      y: scaledCursorY - (scaledCursorY - panOffset.value.y) * zoomRatio,
    };

    zoomLevel.value = newZoom;
    userHasInteracted.value = true;
  }

  // Zoom handler - only responds to pinch zoom (ctrlKey), lets regular scroll pass through
  function doZoom(event: WheelEvent) {
    if (!event.ctrlKey) return;

    event.preventDefault();
    const factor = event.deltaY < 0 ? 1.1 : 1 / 1.1;
    zoomAtPoint(factor, event.clientX, event.clientY);
  }

  function zoomIn() {
    const newZoom = Math.min(maxZoom, zoomLevel.value * 1.2);
    zoomLevel.value = newZoom;
    userHasInteracted.value = true;
  }

  function zoomOut() {
    const newZoom = Math.max(minZoom, zoomLevel.value / 1.2);
    zoomLevel.value = newZoom;
    userHasInteracted.value = true;
  }

  // Tooltip functions
  function showTooltip(event: MouseEvent, text: string) {
    const rect = graphContainer.value?.getBoundingClientRect();
    if (rect) {
      tooltip.value = {
        visible: true,
        text,
        x: event.clientX - rect.left,
        y: event.clientY - rect.top + 20,
      };
    }
  }

  function hideTooltip() {
    tooltip.value.visible = false;
  }

  // Watch for node changes to reset view
  function setupNodeCountWatcher(nodeCount: ComputedRef<number> | Ref<number>) {
    let lastCount = 0;
    watch(
      nodeCount,
      (newCount) => {
        // Only reset view if count actually changed (not just reactivity trigger)
        // and user hasn't manually adjusted the view
        if (newCount !== lastCount) {
          lastCount = newCount;
          if (!userHasInteracted.value) {
            panOffset.value = { x: 0, y: -detailPanelOffset.value / 2 };
            zoomLevel.value = 1;
          }
        }
      },
      { immediate: true }
    );
  }

  // Watch for container height changes
  watch(containerHeight, () => {
    if (!userHasInteracted.value) {
      panOffset.value = { x: 0, y: -detailPanelOffset.value / 2 };
    }
  });

  return {
    // Refs
    panOffset,
    zoomLevel,
    tooltip,

    // Computed
    svgWidth,
    svgHeight,
    baseViewBox,
    viewBoxString,
    isViewModified,

    // Methods
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
    setDetailPanelOffset,
  };
}
