/*
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
*/

// ABOUTME: Layout algorithm for transform flow graph visualization.
// ABOUTME: Wraps nodes into rows within subscribe/actions/publish lanes, with connecting edges.

import { computed, Ref } from "vue";

export interface SubscribeTopic {
  topic: string | null;
  condition?: string | null;
}

export interface PublishRule {
  topic: string | null;
  condition?: string | null;
}

export interface TransformAction {
  id: string;
  name: string;
  type: string;
  displayName: string;
  [key: string]: unknown;
}

export interface LayoutNode {
  id: string;
  name: string;
  nodeType: 'subscribe' | 'action' | 'publish';
  x: number;
  y: number;
  condition?: string | null;
  data?: unknown;
  index?: number;
}

export interface LayoutEdge {
  id: string;
  source: string;
  target: string;
  path: string;
}

export interface AddButtonPosition {
  x: number;
  y: number;
  insertIndex: number;
  buttonType: 'subscribe' | 'action' | 'publish';
}

export interface LaneInfo {
  y: number;
  height: number;
  label: string;
}

export interface LayoutConfig {
  nodeWidth: number;
  nodeHeight: number;
  horizontalGap: number;
  laneHeight: number;
  lanePadding: number;
  leftMargin: number;
}

const DEFAULT_CONFIG: LayoutConfig = {
  nodeWidth: 180,
  nodeHeight: 50,
  horizontalGap: 70,
  laneHeight: 150,
  lanePadding: 25,
  leftMargin: 100,
};

// Vertical spacing between row centers within a lane
const ROW_SPACING = 70;

// Minimum distance from lane edge to node center (prevents label overlap)
const LABEL_SPACE = 35;

// Side margin for add buttons at the edges of each row
const ADD_BUTTON_MARGIN = 30;

export function useTransformLayout(
  subscribeTopics: Ref<SubscribeTopic[]>,
  actions: Ref<TransformAction[]>,
  publishRules: Ref<PublishRule[]>,
  containerWidth: Ref<number>,
  config: Partial<LayoutConfig> = {}
) {
  const cfg = { ...DEFAULT_CONFIG, ...config };

  // How many nodes fit in one row, accounting for add button margins on each side.
  // Each node occupies nodeWidth, with horizontalGap between nodes.
  // The usable width subtracts space for add buttons at both edges.
  const nodesPerRow = computed(() => {
    const width = containerWidth.value || 800;
    const usable = width - 2 * ADD_BUTTON_MARGIN;
    return Math.max(1, Math.floor((usable + cfg.horizontalGap) / (cfg.nodeWidth + cfg.horizontalGap)));
  });

  const rowCount = (itemCount: number) => Math.max(1, Math.ceil(itemCount / nodesPerRow.value));

  // Lane height scales with row count. For a single row, rows are vertically
  // centered in cfg.laneHeight (the minimum). For multiple rows, the lane
  // expands so that the outermost node centers are at least LABEL_SPACE from
  // the lane edges.
  const laneHeight = (numRows: number) => {
    const minForRows = 2 * LABEL_SPACE + cfg.nodeHeight + (numRows - 1) * ROW_SPACING;
    return Math.max(cfg.laneHeight, minForRows);
  };

  // Vertical offset from the lane top to the first row center.
  // Rows are always vertically centered within the lane.
  const firstRowOffset = (height: number, numRows: number) => {
    return (height - (numRows - 1) * ROW_SPACING) / 2;
  };

  const lanes = computed<LaneInfo[]>(() => {
    const validSubs = subscribeTopics.value.filter(s => s.topic).length;
    const validPubs = (publishRules.value || []).filter(p => p.topic).length;

    const subHeight = laneHeight(rowCount(validSubs));
    const actHeight = laneHeight(rowCount(actions.value.length));
    const pubHeight = laneHeight(rowCount(validPubs));

    return [
      { y: 0, height: subHeight, label: 'SUBSCRIBE' },
      { y: subHeight, height: actHeight, label: 'ACTIONS' },
      { y: subHeight + actHeight, height: pubHeight, label: 'PUBLISH' },
    ];
  });

  // Position a list of items within a lane, wrapping into rows.
  // Each row is horizontally centered. Items flow left-to-right, top-to-bottom.
  const positionInLane = (
    items: { id: string; name: string; condition?: string | null; data?: unknown; index?: number }[],
    laneTop: number,
    laneH: number,
    nodeType: 'subscribe' | 'action' | 'publish',
  ): LayoutNode[] => {
    const perRow = nodesPerRow.value;
    const width = containerWidth.value || 800;
    const numRows = Math.max(1, Math.ceil(items.length / perRow));
    const rowOffsetY = firstRowOffset(laneH, numRows);

    return items.map((item, i) => {
      const row = Math.floor(i / perRow);
      const col = i % perRow;
      const itemsInRow = Math.min(perRow, items.length - row * perRow);
      const totalRowWidth = itemsInRow * cfg.nodeWidth + (itemsInRow - 1) * cfg.horizontalGap;
      const startX = (width - totalRowWidth) / 2 + cfg.nodeWidth / 2;

      return {
        ...item,
        nodeType,
        x: startX + col * (cfg.nodeWidth + cfg.horizontalGap),
        y: laneTop + rowOffsetY + row * ROW_SPACING,
      };
    });
  };

  const nodes = computed<LayoutNode[]>(() => {
    const lanesInfo = lanes.value;

    const validSubscribes = subscribeTopics.value.filter(s => s.topic);
    const subItems = validSubscribes.map(sub => ({
      id: `subscribe:${subscribeTopics.value.findIndex(s => s.topic === sub.topic && s.condition === sub.condition)}`,
      name: sub.topic!,
      condition: sub.condition,
      data: sub,
      index: subscribeTopics.value.findIndex(s => s.topic === sub.topic && s.condition === sub.condition),
    }));

    const actionItems = actions.value.map((action, index) => ({
      id: `action:${action.id}`,
      name: action.name || action.displayName,
      data: action,
      index,
    }));

    const publishRulesArray = publishRules.value || [];
    const validPublishes = publishRulesArray.filter(p => p.topic);
    const pubItems = validPublishes.map(pub => ({
      id: `publish:${publishRulesArray.findIndex(p => p.topic === pub.topic && p.condition === pub.condition)}`,
      name: pub.topic!,
      condition: pub.condition,
      data: pub,
      index: publishRulesArray.findIndex(p => p.topic === pub.topic && p.condition === pub.condition),
    }));

    return [
      ...positionInLane(subItems, lanesInfo[0].y, lanesInfo[0].height, 'subscribe'),
      ...positionInLane(actionItems, lanesInfo[1].y, lanesInfo[1].height, 'action'),
      ...positionInLane(pubItems, lanesInfo[2].y, lanesInfo[2].height, 'publish'),
    ];
  });

  const edges = computed<LayoutEdge[]>(() => {
    const result: LayoutEdge[] = [];
    const actionNodes = nodes.value.filter(n => n.nodeType === 'action');
    const perRow = nodesPerRow.value;

    for (let i = 0; i < actionNodes.length - 1; i++) {
      const source = actionNodes[i];
      const target = actionNodes[i + 1];
      const srcRow = Math.floor(i / perRow);
      const tgtRow = Math.floor((i + 1) / perRow);

      let path: string;
      if (srcRow === tgtRow) {
        // Same row — straight horizontal line
        path = `M ${source.x + cfg.nodeWidth / 2} ${source.y} L ${target.x - cfg.nodeWidth / 2} ${target.y}`;
      } else {
        // Row wrap — route: right, down, across left, down, right
        const srcRight = source.x + cfg.nodeWidth / 2;
        const tgtLeft = target.x - cfg.nodeWidth / 2;
        const turnMargin = 20;
        const midY = (source.y + target.y) / 2;

        path = `M ${srcRight} ${source.y} ` +
               `L ${srcRight + turnMargin} ${source.y} ` +
               `L ${srcRight + turnMargin} ${midY} ` +
               `L ${tgtLeft - turnMargin} ${midY} ` +
               `L ${tgtLeft - turnMargin} ${target.y} ` +
               `L ${tgtLeft} ${target.y}`;
      }

      result.push({
        id: `edge:action:${i}:action:${i + 1}`,
        source: source.id,
        target: target.id,
        path,
      });
    }

    return result;
  });

  const viewBox = computed(() => {
    const lanesInfo = lanes.value;
    const lastLane = lanesInfo[lanesInfo.length - 1];
    const deleteZoneHeight = 72;

    return {
      x: 0,
      y: 0,
      width: containerWidth.value || 800,
      height: lastLane.y + lastLane.height + deleteZoneHeight,
    };
  });

  // Add buttons sit in the gaps between nodes (using actual node positions)
  // and at the edges for empty lanes.
  const addButtonPositions = computed<AddButtonPosition[]>(() => {
    const result: AddButtonPosition[] = [];
    const width = containerWidth.value || 800;
    const centerX = width / 2;

    const addButtonsForLane = (
      laneNodes: LayoutNode[],
      laneInfo: LaneInfo,
      buttonType: 'subscribe' | 'action' | 'publish',
      showBetween: boolean,
    ) => {
      const laneCenterY = laneInfo.y + laneInfo.height / 2;

      if (laneNodes.length === 0) {
        result.push({ x: centerX, y: laneCenterY, insertIndex: 0, buttonType });
        return;
      }

      if (showBetween) {
        // Button before first node
        result.push({
          x: laneNodes[0].x - cfg.nodeWidth / 2 - cfg.horizontalGap / 2,
          y: laneNodes[0].y,
          insertIndex: 0,
          buttonType,
        });
      }

      // Button after each node
      for (let i = 0; i < laneNodes.length; i++) {
        const isLast = i === laneNodes.length - 1;
        // For subscribe/publish, only show button after the last node
        if (!showBetween && !isLast) continue;
        result.push({
          x: laneNodes[i].x + cfg.nodeWidth / 2 + cfg.horizontalGap / 2,
          y: laneNodes[i].y,
          insertIndex: i + 1,
          buttonType,
        });
      }
    };

    const subscribeNodes = nodes.value.filter(n => n.nodeType === 'subscribe');
    const actionNodes = nodes.value.filter(n => n.nodeType === 'action');
    const publishNodes = nodes.value.filter(n => n.nodeType === 'publish');
    const lanesInfo = lanes.value;

    addButtonsForLane(subscribeNodes, lanesInfo[0], 'subscribe', false);
    addButtonsForLane(actionNodes, lanesInfo[1], 'action', true);
    addButtonsForLane(publishNodes, lanesInfo[2], 'publish', false);

    return result;
  });

  return {
    nodes,
    edges,
    viewBox,
    addButtonPositions,
    lanes,
    config: cfg,
  };
}

export default useTransformLayout;
