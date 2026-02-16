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

// ABOUTME: Composable for building and managing DeltaFile lineage trees.
// ABOUTME: Recursively fetches ancestors and descendants for tree visualization.

import { ref, computed } from "vue";
import useGraphQL from "./useGraphQL";

export interface LineageNode {
  did: string;
  name: string;
  stage: string;
  dataSource: string;
  parentDids: string[];
  childDids: string[];
  depth: number; // negative = ancestor, positive = descendant, 0 = root
  ancestorsExpanded: boolean;
  descendantsExpanded: boolean;
  loading: boolean;
  overflowCount: number;
}

export interface LineageTree {
  nodes: Map<string, LineageNode>;
  rootDid: string;
}

const MAX_CHILDREN_PER_NODE = 20;

// Cap a node's children or parents. Creates an overflow placeholder when over the limit.
// Returns the real (non-placeholder) DIDs to fetch.
function capRelationships(
  nodes: Map<string, LineageNode>,
  node: LineageNode,
  direction: "children" | "parents"
): string[] {
  const dids = direction === "children" ? node.childDids : node.parentDids;
  if (dids.length <= MAX_CHILDREN_PER_NODE) return dids;

  const kept = dids.slice(0, MAX_CHILDREN_PER_NODE);
  const overflowCount = dids.length - MAX_CHILDREN_PER_NODE;
  const prefix = direction === "children" ? "overflow-desc" : "overflow-anc";
  const overflowDid = `${prefix}-${node.did}`;

  if (!nodes.has(overflowDid)) {
    nodes.set(overflowDid, {
      did: overflowDid,
      name: `+${overflowCount} more`,
      stage: "",
      dataSource: "",
      parentDids: [],
      childDids: direction === "parents" ? [node.did] : [],
      depth: direction === "children" ? node.depth + 1 : node.depth - 1,
      ancestorsExpanded: true,
      descendantsExpanded: true,
      loading: false,
      overflowCount,
    });
  }

  if (direction === "children") {
    node.childDids = [...kept, overflowDid];
  } else {
    node.parentDids = [...kept, overflowDid];
  }

  return kept;
}

export default function useLineageTree() {
  const { response, queryGraphQL } = useGraphQL();
  const tree = ref<LineageTree>({ nodes: new Map(), rootDid: "" });
  const loading = ref(false);
  const error = ref<string | null>(null);

  // Fetch DeltaFiles by DIDs, including parentDids and childDids for recursion
  const fetchDeltaFilesByDids = async (dids: string[]): Promise<LineageNode[]> => {
    if (dids.length === 0) return [];

    const query = {
      deltaFiles: {
        __args: {
          offset: 0,
          limit: 10000,
          filter: {
            dids: dids,
          },
        },
        deltaFiles: {
          did: true,
          name: true,
          stage: true,
          dataSource: true,
          parentDids: true,
          childDids: true,
        },
      },
    };

    await queryGraphQL(query, "getLineageDeltaFiles");
    const deltaFiles = response.value?.data?.deltaFiles?.deltaFiles || [];

    return deltaFiles.map((df: any) => ({
      did: df.did,
      name: df.name,
      stage: df.stage,
      dataSource: df.dataSource,
      parentDids: df.parentDids || [],
      childDids: df.childDids || [],
      depth: 0, // Will be set by caller
      ancestorsExpanded: false,
      descendantsExpanded: false,
      loading: false,
      overflowCount: 0,
    }));
  };

  // Build the initial tree from a root DeltaFile
  const buildTree = async (
    rootDid: string,
    rootData: { name: string; stage: string; dataSource: string; parentDids: string[]; childDids: string[] },
    ancestorDepth: number = 3,
    descendantDepth: number = 3
  ) => {
    loading.value = true;
    error.value = null;

    try {
      const nodes = new Map<string, LineageNode>();

      // Add root node
      const rootNode: LineageNode = {
        did: rootDid,
        name: rootData.name,
        stage: rootData.stage,
        dataSource: rootData.dataSource,
        parentDids: rootData.parentDids || [],
        childDids: rootData.childDids || [],
        depth: 0,
        ancestorsExpanded: ancestorDepth > 0,
        descendantsExpanded: descendantDepth > 0,
        loading: false,
        overflowCount: 0,
      };
      nodes.set(rootDid, rootNode);

      // Cap root's relationships before fetching tree
      const rootParentDids = capRelationships(nodes, rootNode, "parents");
      const rootChildDids = capRelationships(nodes, rootNode, "children");

      await fetchAncestors(nodes, rootParentDids, -1, -ancestorDepth);
      await fetchDescendants(nodes, rootChildDids, 1, descendantDepth);

      tree.value = { nodes, rootDid };
    } catch (e) {
      error.value = e instanceof Error ? e.message : "Failed to build lineage tree";
    } finally {
      loading.value = false;
    }
  };

  // Recursively fetch ancestors up to maxDepth
  const fetchAncestors = async (
    nodes: Map<string, LineageNode>,
    parentDids: string[],
    currentDepth: number,
    maxDepth: number
  ) => {
    if (currentDepth < maxDepth || parentDids.length === 0) return;

    // Filter out DIDs we already have
    const newDids = parentDids.filter((did) => !nodes.has(did));
    if (newDids.length === 0) return;

    const fetched = await fetchDeltaFilesByDids(newDids);

    // Add nodes at current depth
    const nextParentDids: string[] = [];
    for (const node of fetched) {
      node.depth = currentDepth;
      node.ancestorsExpanded = currentDepth > maxDepth;
      nodes.set(node.did, node);
      const realParentDids = capRelationships(nodes, node, "parents");
      nextParentDids.push(...realParentDids);
    }

    // Recursively fetch next level of ancestors
    await fetchAncestors(nodes, nextParentDids, currentDepth - 1, maxDepth);
  };

  // Recursively fetch descendants up to maxDepth
  const fetchDescendants = async (
    nodes: Map<string, LineageNode>,
    childDids: string[],
    currentDepth: number,
    maxDepth: number
  ) => {
    if (currentDepth > maxDepth || childDids.length === 0) return;

    // Filter out DIDs we already have
    const newDids = childDids.filter((did) => !nodes.has(did));
    if (newDids.length === 0) return;

    const fetched = await fetchDeltaFilesByDids(newDids);

    // Add nodes at current depth
    const nextChildDids: string[] = [];
    for (const node of fetched) {
      node.depth = currentDepth;
      node.descendantsExpanded = currentDepth < maxDepth;
      nodes.set(node.did, node);
      const realChildDids = capRelationships(nodes, node, "children");
      nextChildDids.push(...realChildDids);
    }

    // Recursively fetch next level of descendants
    await fetchDescendants(nodes, nextChildDids, currentDepth + 1, maxDepth);
  };

  // Expand ancestors of a specific node
  const expandAncestors = async (did: string, levels: number = 1) => {
    const node = tree.value.nodes.get(did);
    if (!node || node.parentDids.length === 0) return;

    node.loading = true;

    try {
      const nodes = tree.value.nodes;
      const parentDidsToFetch = capRelationships(nodes, node, "parents");
      const targetDepth = node.depth - levels;
      await fetchAncestors(nodes, parentDidsToFetch, node.depth - 1, targetDepth);
      node.ancestorsExpanded = true;
      // Trigger reactivity
      tree.value = { ...tree.value, nodes: new Map(nodes) };
    } finally {
      node.loading = false;
    }
  };

  // Expand descendants of a specific node
  const expandDescendants = async (did: string, levels: number = 1) => {
    const node = tree.value.nodes.get(did);
    if (!node || node.childDids.length === 0) return;

    node.loading = true;

    try {
      const nodes = tree.value.nodes;
      const childDidsToFetch = capRelationships(nodes, node, "children");
      const targetDepth = node.depth + levels;
      await fetchDescendants(nodes, childDidsToFetch, node.depth + 1, targetDepth);
      node.descendantsExpanded = true;
      // Trigger reactivity
      tree.value = { ...tree.value, nodes: new Map(nodes) };
    } finally {
      node.loading = false;
    }
  };

  // Computed: nodes as array sorted by depth
  const nodesList = computed(() => {
    return Array.from(tree.value.nodes.values()).sort((a, b) => a.depth - b.depth);
  });

  // Computed: edges between nodes (parent -> child)
  const edges = computed(() => {
    const result: Array<{ source: string; target: string }> = [];
    const nodes = tree.value.nodes;

    for (const node of nodes.values()) {
      for (const childDid of node.childDids) {
        if (nodes.has(childDid)) {
          result.push({ source: node.did, target: childDid });
        }
      }
    }

    return result;
  });

  // Check if a node has unexpanded ancestors
  const hasUnexpandedAncestors = (did: string): boolean => {
    const node = tree.value.nodes.get(did);
    if (!node) return false;
    // Has parents and at least one parent is not in the tree
    return node.parentDids.some((parentDid) => !tree.value.nodes.has(parentDid));
  };

  // Check if a node has unexpanded descendants
  const hasUnexpandedDescendants = (did: string): boolean => {
    const node = tree.value.nodes.get(did);
    if (!node) return false;
    // Has children and at least one child is not in the tree
    return node.childDids.some((childDid) => !tree.value.nodes.has(childDid));
  };

  return {
    tree,
    loading,
    error,
    buildTree,
    expandAncestors,
    expandDescendants,
    nodesList,
    edges,
    hasUnexpandedAncestors,
    hasUnexpandedDescendants,
  };
}
