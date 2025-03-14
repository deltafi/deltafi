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
  <div>
    <CollapsiblePanel header="Flows" class="actions-panel table-panel">
      <DataTable v-model:expanded-rows="expandedRows" responsive-layout="scroll" class="p-datatable-sm p-datatable-gridlines" striped-rows :value="flows" :row-class="rowClass" data-key="name" @row-click="rowClick">
        <Column class="expander-column" :expander="true" />
        <Column field="name" header="Name" class="flow-name-column" :sortable="true">
          <template #body="{ data }">
            <span class="branch">{{ branchIcons(data.depth) }}</span>
            <span v-tooltip.top="topicsTooltip(data)">{{ data.name }}</span>
          </template>
        </Column>
        <Column field="type" header="Type" :sortable="true" />
        <Column field="state" header="State" class="state-column" :sortable="true">
          <template #body="{ data }">
            {{ data.state }}
            <TestModeBadge v-if="data.testMode" :reason="data.testModeReason" />
          </template>
        </Column>
        <Column field="created" header="Created" class="timestamp-column" :sortable="true">
          <template #body="row">
            <Timestamp :timestamp="row.data.created" />
          </template>
        </Column>
        <Column field="modified" header="Modified" class="timestamp-column" :sortable="true">
          <template #body="row">
            <Timestamp :timestamp="row.data.modified" />
          </template>
        </Column>
        <Column field="elapsed" header="Elapsed" class="elapsed-column" />
        <Column header="Metadata" class="metadata-column">
          <template #body="{ data: flow }">
            <span v-if="flow.input.hasOwnProperty('metadata') && Object.keys(flow.input.metadata).length > 0">
              <DialogTemplate component-name="MetadataViewer" header="Metadata" :metadata="{ [flow.name]: metadataAsArray(flow.input.metadata) }" :dismissable-mask="true">
                <Button icon="fas fa-table" label="View" class="content-button p-button-link" />
              </DialogTemplate>
            </span>
          </template>
        </Column>
        <Column v-if="!contentDeleted && $hasPermission('DeltaFileContentView')" header="Input" class="content-column">
          <template #body="{ data: flow }">
            <span v-if="flow.input.hasOwnProperty('content') && flow.input.content.length > 0">
              <ContentDialog :content="flow.input.content" :action="flow.input.content.name">
                <Button icon="far fa-window-maximize" label="View" class="content-button p-button-link" />
              </ContentDialog>
            </span>
          </template>
        </Column>
        <Column field="last_action_content" header="Output" class="content-column">
          <template #body="{ data: flow }">
            <span v-if="flow.actions.length > 0">
              <ContentDialog v-if="lastAction(flow.actions).content.length > 0" :content="lastAction(flow.actions).content" :action="lastAction(flow.actions).name">
                <Button icon="far fa-window-maximize" label="View" class="content-button p-button-link" />
              </ContentDialog>
            </span>
            <ContentDialog v-else :content="flow.input.content" :action="flow.input.content.name">
              <Button icon="far fa-window-maximize" label="View" class="content-button p-button-link" />
            </ContentDialog>
          </template>
        </Column>
        <template #expansion="flow">
          <DeltaFileActionsTable v-if="flow.data.actions.length > 0" :delta-file-data="flow.data" :content-deleted="contentDeleted" />
          <span v-else>This flow has no actions.</span>
        </template>
      </DataTable>
    </CollapsiblePanel>
    <ErrorViewerDialog v-model:visible="errorViewer.visible" :action="errorViewer.action" />
  </div>
</template>

<script setup>
import CollapsiblePanel from "@/components/CollapsiblePanel.vue";
import ContentDialog from "@/components/ContentDialog.vue";
import DeltaFileActionsTable from "@/components/DeltaFileViewer/DeltaFileActionsTable.vue";
import DialogTemplate from "@/components/DialogTemplate.vue";
import ErrorViewerDialog from "@/components/errors/ErrorViewerDialog.vue";
import TestModeBadge from "@/components/TestModeBadge.vue";
import Timestamp from "@/components/Timestamp.vue";
import useUtilFunctions from "@/composables/useUtilFunctions";
import { computed, reactive, ref, inject } from "vue";

import _ from "lodash";

import Button from "primevue/button";
import Column from "primevue/column";
import DataTable from "primevue/datatable";

const hasPermission = inject("hasPermission");
const expandedRows = ref([]);

const props = defineProps({
  deltaFileData: {
    type: Object,
    required: true,
  },
});

const lastAction = (actions) => {
  return actions[actions.length - 1];
};

const { duration } = useUtilFunctions();
const deltaFile = reactive(props.deltaFileData);
const errorViewer = reactive({
  visible: false,
  action: {},
});

const metadataAsArray = (metadataObject) => {
  return Object.entries(metadataObject).map(([key, value]) => ({ key, value }));
};

const topicsTooltip = (flow) => {
  const tooltip = [];
  if (flow.input.topics.length) {
    tooltip.push("From:");
    flow.input.topics.forEach((topic) => {
      tooltip.push(`• ${topic}`);
    });
  }

  if (flow.publishTopics.length) {
    tooltip.push("To:");
    flow.publishTopics.forEach((topic) => {
      tooltip.push(`• ${topic}`);
    });
  }
  return tooltip.join("\n");
};

const branchIcons = (depth) => {
  if (depth < 1) return;
  const result = [];
  _.times(depth - 1, () => {
    result.push("│ ");
  });
  result.push("├─");
  return result.join("");
};

function flattenTree(tree) {
  const result = [];
  function traverse(node) {
    result.push(node);
    for (const child of node.children) {
      traverse(child);
    }
  }
  for (const root of tree) {
    traverse(root);
  }
  return result;
}

function generateTree(flows) {
  const nodeMap = new Map();
  flows.forEach((flow) => {
    nodeMap.set(flow.number, {
      ...flow,
      children: [],
    });
  });
  const roots = [];
  flows.forEach((flow) => {
    const node = nodeMap.get(flow.number);
    const ancestorIds = flow.input.ancestorIds;
    if (ancestorIds.length === 0) {
      roots.push(node);
    } else {
      const immediateParentId = ancestorIds[0];
      const parent = nodeMap.get(immediateParentId);
      if (parent) {
        parent.children.push(node);
      }
    }
  });
  return roots;
}

const flows = computed(() => {
  const deltaFileFlows = deltaFile.flows.map((flow) => {
    const timeElapsed = new Date(flow.modified) - new Date(flow.created);
    flow.created = new Date(flow.created).toISOString();
    flow.modified = new Date(flow.modified).toISOString();
    return {
      ...flow,
      elapsed: duration(timeElapsed),
    };
  });
  return flattenTree(generateTree(deltaFileFlows));
});

const contentDeleted = computed(() => {
  return deltaFile.contentDeleted !== null ? deltaFile.contentDeleted : false;
});

const rowClass = (flow) => {
  if (flow.state === "ERROR") return "table-danger cursor-pointer";
  if (flow.state === "COMPLETE" && lastAction(flow.actions)?.state === "FILTERED") return "table-warning cursor-pointer";
};

const rowClick = (event) => {
  // Don't interfere with expander
  if (event.originalEvent.target.nodeName === "svg") return;

  const action = event.data.actions.length > 0 ? event.data.actions.slice(-1)[0] : event.data;
  if (!["ERROR", "RETRIED", "FILTERED"].includes(action.state)) return;

  errorViewer.visible = true;
  errorViewer.action = action;
};
</script>

<style>
.actions-panel {
  tr.action-error {
    cursor: pointer !important;
  }

  .content-column,
  .metadata-column {
    width: 1%;
    padding: 0 0.5rem !important;

    .content-button {
      cursor: pointer !important;
      padding: 0.1rem 0.4rem;
      margin: 0;
      color: #333333;
    }

    .content-button:hover {
      color: #666666 !important;

      .p-button-label {
        text-decoration: none !important;
      }
    }

    .content-button:focus {
      outline: none !important;
      box-shadow: none !important;
    }
  }

  .state-column {
    width: 8rem;
  }

  .elapsed-column {
    width: 6rem;
  }

  .timestamp-column {
    width: 16rem;
  }

  .flow-name-column {
    padding-top: 0 !important;
    padding-bottom: 0 !important;

    .branch {
      font-family: "Courier New", Courier, monospace;
      font-size: 1.5rem;
      color: #6c757d;
      margin-right: 0.25rem;
    }
  }
}
</style>
