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

// ABOUTME: Shared style definitions for pipeline and system map graph components.
// ABOUTME: Provides node colors, type labels, and state colors for consistent rendering.

export interface NodeColors {
  fill: string;
  stroke: string;
}

export const nodeColorsByType: Record<string, NodeColors> = {
  REST_DATA_SOURCE: { fill: "var(--orange-100)", stroke: "var(--orange-500)" },
  TIMED_DATA_SOURCE: { fill: "var(--orange-100)", stroke: "var(--orange-500)" },
  ON_ERROR_DATA_SOURCE: { fill: "var(--orange-100)", stroke: "var(--orange-500)" },
  TRANSFORM: { fill: "var(--blue-100)", stroke: "var(--blue-500)" },
  DATA_SINK: { fill: "var(--green-100)", stroke: "var(--green-500)" },
  TOPIC: { fill: "var(--surface-200)", stroke: "var(--surface-500)" },
};

export const typeLabels: Record<string, string> = {
  REST_DATA_SOURCE: "REST Source",
  TIMED_DATA_SOURCE: "Timed Source",
  ON_ERROR_DATA_SOURCE: "Error Source",
  TRANSFORM: "Transform",
  DATA_SINK: "Data Sink",
  TOPIC: "Topic",
};

export const typeLabelsLong: Record<string, string> = {
  REST_DATA_SOURCE: "REST Data Source",
  TIMED_DATA_SOURCE: "Timed Data Source",
  ON_ERROR_DATA_SOURCE: "On-Error Data Source",
  TRANSFORM: "Transform",
  DATA_SINK: "Data Sink",
  TOPIC: "Topic",
};

export const stateColors: Record<string, string> = {
  RUNNING: "var(--green-500)",
  PAUSED: "var(--yellow-500)",
  STOPPED: "var(--red-500)",
};

// Colors for DeltaFile flow states (null means use type-based color)
export const flowStateColors: Record<string, NodeColors | null> = {
  COMPLETE: null,
  IN_FLIGHT: null,
  PENDING: null,
  RESUMED: null,
  ERROR: { fill: "var(--red-100)", stroke: "var(--red-500)" },
  FILTERED: { fill: "var(--yellow-100)", stroke: "var(--yellow-500)" },
  CANCELLED: { fill: "var(--surface-200)", stroke: "var(--surface-500)" },
};

// Get node colors based on flow state, falling back to type-based colors
export function getFlowNodeColors(type: string, state: string): NodeColors {
  const stateColor = flowStateColors[state];
  if (stateColor) {
    return stateColor;
  }
  return nodeColorsByType[type] || { fill: "var(--surface-200)", stroke: "var(--surface-500)" };
}

export function formatType(type: string): string {
  return typeLabels[type] || type;
}

export function formatTypeLong(type: string): string {
  return typeLabelsLong[type] || type;
}

export function getNodeTypeClass(type: string): string {
  if (!type) return "node-type-unknown";
  return `node-type-${type.toLowerCase().replace(/_/g, "-")}`;
}

export function isControllableFlow(type: string): boolean {
  return type !== null && type !== undefined && type !== "TOPIC";
}

export function isDataSourceType(type: string): boolean {
  return type === "REST_DATA_SOURCE" || type === "TIMED_DATA_SOURCE" || type === "ON_ERROR_DATA_SOURCE";
}

export function formatMetricNumber(num: number): string {
  if (num >= 1000000) {
    return (num / 1000000).toFixed(1) + "M";
  }
  if (num >= 1000) {
    return (num / 1000).toFixed(1) + "K";
  }
  return num.toString();
}

export function formatBytesParts(bytes: number): { value: string; unit: string } {
  if (bytes >= 1099511627776) {
    return { value: (bytes / 1099511627776).toFixed(1), unit: "T" };
  }
  if (bytes >= 1073741824) {
    return { value: (bytes / 1073741824).toFixed(1), unit: "G" };
  }
  if (bytes >= 1048576) {
    return { value: (bytes / 1048576).toFixed(1), unit: "M" };
  }
  if (bytes >= 1024) {
    return { value: (bytes / 1024).toFixed(1), unit: "K" };
  }
  return { value: bytes.toString(), unit: "B" };
}

export function formatErrorCount(count: number): string {
  if (count >= 1000) {
    return Math.floor(count / 1000) + "k";
  }
  return count.toString();
}

export function formatQueueCount(count: number): string {
  if (count >= 1000000) {
    return (count / 1000000).toFixed(1) + "M";
  }
  if (count >= 1000) {
    return (count / 1000).toFixed(1) + "k";
  }
  return count.toString();
}

export function getErrorCount(errorCounts: Record<string, number>, nodeName: string): number {
  return errorCounts[nodeName] || 0;
}

export function getQueueCount(queueCounts: Record<string, number>, nodeName: string): number {
  return queueCounts[nodeName] || 0;
}

export function getNodeMetrics(flowMetrics: Record<string, unknown>, nodeName: string): Record<string, number> | null {
  return (flowMetrics[nodeName] as Record<string, number>) || null;
}

export function hasMetrics(flowMetrics: Record<string, unknown>, nodeName: string): boolean {
  const m = getNodeMetrics(flowMetrics, nodeName);
  return m !== null && (m.filesIn > 0 || m.filesOut > 0 || m.bytesIn > 0 || m.bytesOut > 0);
}

export function isEdgeActive(edge: { source: string; target: string }, flowMetrics: Record<string, unknown>): boolean {
  // Extract node names from IDs (format: "TYPE:name")
  const sourceName = edge.source.split(":").slice(1).join(":");
  const targetName = edge.target.split(":").slice(1).join(":");

  const sourceMetrics = flowMetrics[sourceName] as Record<string, number> | undefined;
  const targetMetrics = flowMetrics[targetName] as Record<string, number> | undefined;

  // Edge is active if source has files out or target has files in (files, not bytes, since 0-byte files exist)
  const sourceActive = (sourceMetrics?.filesOut ?? 0) > 0;
  const targetActive = (targetMetrics?.filesIn ?? 0) > 0;

  return sourceActive || targetActive;
}

export interface LayoutEdge {
  source: string;
  target: string;
  path?: string;
  [key: string]: unknown;
}

export interface NodePosition {
  x: number;
  y: number;
}

export interface NodeInfo {
  id: string;
  type: string;
  name: string;
}

export interface ComputeEdgePathsOptions {
  metricsBoxWidth?: number;
  flowMetrics?: Record<string, unknown>;
  nodes?: NodeInfo[];
  arrowLength?: number;
}

export function computeEdgePaths(
  edges: LayoutEdge[],
  nodePositions: Record<string, NodePosition>,
  nodeWidth: number,
  options: ComputeEdgePathsOptions = {}
): LayoutEdge[] {
  const { metricsBoxWidth = 0, flowMetrics = {}, nodes = [], arrowLength = 17 } = options;

  // Build lookup for node info by ID
  const nodeById: Record<string, NodeInfo> = {};
  for (const node of nodes) {
    nodeById[node.id] = node;
  }

  return edges
    .filter((edge) => nodePositions[edge.source] && nodePositions[edge.target])
    .map((edge) => {
      const source = nodePositions[edge.source];
      const target = nodePositions[edge.target];
      const sourceNode = nodeById[edge.source];
      const targetNode = nodeById[edge.target];

      // Check if source has output metrics box (right side) - always present on flow nodes
      const sourceHasOutputBox =
        metricsBoxWidth > 0 &&
        sourceNode &&
        (isDataSourceType(sourceNode.type) || sourceNode.type === "TRANSFORM");

      // Check if source has input metrics box (left side) - for transforms and data sinks
      const sourceHasInputBox =
        metricsBoxWidth > 0 &&
        sourceNode &&
        (sourceNode.type === "DATA_SINK" || sourceNode.type === "TRANSFORM");

      // Check if target has input metrics box (left side) - always present on flow nodes
      const targetHasInputBox =
        metricsBoxWidth > 0 &&
        targetNode &&
        (targetNode.type === "DATA_SINK" || targetNode.type === "TRANSFORM");

      // Check if target has output metrics box (right side) - for data sources and transforms
      const targetHasOutputBox =
        metricsBoxWidth > 0 &&
        targetNode &&
        (isDataSourceType(targetNode.type) || targetNode.type === "TRANSFORM");

      // Determine which side to connect from, adjusting for metrics boxes
      // Edges exit from outer edge of source's metrics box and end at outer edge of target's metrics box
      let sourceX: number;
      let targetX: number;
      let path: string;

      // Special handling for same-X nodes (vertical edges)
      const sameColumn = Math.abs(target.x - source.x) < 50;

      if (sameColumn) {
        // For nodes in same column, draw edge on the right side going down/up
        const sourceRight = source.x + nodeWidth / 2 + (sourceHasOutputBox ? metricsBoxWidth : 0);
        const targetRight = target.x + nodeWidth / 2 + (targetHasOutputBox ? metricsBoxWidth : 0);
        const edgeX = Math.max(sourceRight, targetRight) + 20;
        // Curved path that goes out to the right, then down/up, then back in
        const targetEdge = target.x + nodeWidth / 2 + (targetHasOutputBox ? metricsBoxWidth : 0);
        targetX = targetEdge + arrowLength;
        path = `M ${sourceRight} ${source.y} C ${edgeX} ${source.y}, ${edgeX} ${target.y}, ${targetX} ${target.y}`;
      } else {
        const goingRight = target.x > source.x;

        if (goingRight) {
          // Source exits from right side (from outer edge of metrics box if present)
          sourceX = source.x + nodeWidth / 2 + (sourceHasOutputBox ? metricsBoxWidth : 0);
          // Target enters from left side - line ends arrowLength before the outer edge
          const targetEdge = target.x - nodeWidth / 2 - (targetHasInputBox ? metricsBoxWidth : 0);
          targetX = targetEdge - arrowLength;
        } else {
          // Source exits from left side (from outer edge of input metrics box if present)
          sourceX = source.x - nodeWidth / 2 - (sourceHasInputBox ? metricsBoxWidth : 0);
          // Target enters from right side - line ends arrowLength before the outer edge
          const targetEdge = target.x + nodeWidth / 2 + (targetHasOutputBox ? metricsBoxWidth : 0);
          targetX = targetEdge + arrowLength;
        }

        // Create curved path
        const midX = (sourceX + targetX) / 2;
        path = `M ${sourceX} ${source.y} C ${midX} ${source.y}, ${midX} ${target.y}, ${targetX} ${target.y}`;
      }

      return {
        ...edge,
        path,
      };
    });
}

export default function useGraphStyles() {
  return {
    nodeColorsByType,
    typeLabels,
    typeLabelsLong,
    stateColors,
    flowStateColors,
    formatType,
    formatTypeLong,
    getNodeTypeClass,
    isControllableFlow,
    isDataSourceType,
    formatMetricNumber,
    formatBytesParts,
    formatErrorCount,
    getErrorCount,
    getNodeMetrics,
    hasMetrics,
    isEdgeActive,
    computeEdgePaths,
    getFlowNodeColors,
  };
}
