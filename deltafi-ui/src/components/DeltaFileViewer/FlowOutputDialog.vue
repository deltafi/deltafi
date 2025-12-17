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
<!-- ABOUTME: Dialog for viewing a flow's metadata and content (input/output). -->
<!-- ABOUTME: Used by flow graph and table view to show flow details on icon click. -->

<template>
  <span @click="show">
    <slot />
  </span>
  <Dialog
    v-model:visible="dialogVisible"
    :header="flowName"
    :style="{ width: '75vw', height: '90vh' }"
    :maximizable="true"
    :modal="true"
    :dismissable-mask="true"
    :draggable="false"
  >
    <TabView>
      <TabPanel header="Metadata">
        <div class="metadata-panel">
          <Toolbar>
            <template #start>
              <Badge v-if="canShowUnchangedBadge && !metadataHasChanges" value="unchanged" severity="secondary" />
            </template>
            <template #end>
              <span v-if="canShowDiffToggle" v-tooltip.top="!metadataHasChanges ? 'No metadata changes' : (showDiff ? 'Hide diff' : 'Show diff')">
                <Button icon="fas fa-code-compare" :class="['p-button-text', showDiff ? 'p-button-primary' : 'p-button-secondary']" :disabled="!metadataHasChanges" @click="showDiff = !showDiff" />
              </span>
              <Button v-tooltip.top="'Copy to Clipboard'" icon="fas fa-copy" class="p-button-text p-button-secondary" @click="onCopyMetadata" />
              <Button v-tooltip.top="'Download Metadata'" icon="fas fa-download" class="p-button-text p-button-secondary" @click="onDownloadMetadata" />
            </template>
          </Toolbar>
          <div v-if="hasMetadata" class="metadata-table-container">
            <DataTable
              responsive-layout="scroll"
              :value="diffMetadataArray"
              striped-rows
              sort-field="key"
              :sort-order="1"
              class="p-datatable-sm"
              scroll-height="flex"
              data-key="key"
            >
              <Column field="key" header="Key" :style="{ width: '30%' }" :sortable="true">
                <template #body="{ data }">
                  <span :class="getDiffClass(data.diffStatus)">{{ data.key }}</span>
                </template>
              </Column>
              <Column field="value" header="Value" :style="{ width: '70%' }" :sortable="true" class="metadata-value">
                <template #body="{ data }">
                  <div :class="getDiffClass(data.diffStatus)">
                    <pre v-if="data.diffStatus === 'modified'" class="diff-modified">
<span class="diff-old">{{ data.oldValue }}</span>
<span class="diff-new">{{ data.value }}</span></pre>
                    <pre v-else>{{ data.value }}</pre>
                  </div>
                </template>
              </Column>
            </DataTable>
          </div>
          <div v-else class="empty-panel">
            No metadata available
          </div>
        </div>
      </TabPanel>
      <!-- Single Content tab when input equals output -->
      <TabPanel v-if="contentIsUnchanged" header="Content" :disabled="!hasInputContent">
        <div class="content-panel">
          <ContentSelector :did="did" :flow-number="flowNumber" :content="inputContent">
            <template v-if="canShowUnchangedBadge" #toolbar-start>
              <Badge value="unchanged" severity="secondary" class="mr-2" />
            </template>
          </ContentSelector>
        </div>
      </TabPanel>
      <!-- Separate Input/Output tabs when content differs -->
      <TabPanel v-if="!contentIsUnchanged" header="Input Content" :disabled="!hasInputContent">
        <div v-if="hasInputContent" class="content-panel">
          <ContentSelector :did="did" :flow-number="flowNumber" :content="inputContent" />
        </div>
        <div v-else class="empty-panel">
          No input content available
        </div>
      </TabPanel>
      <TabPanel v-if="!contentIsUnchanged" header="Output Content" :disabled="!hasOutputContent">
        <div v-if="hasOutputContent" class="content-panel">
          <ContentSelector :did="did" :flow-number="flowNumber" :action-index="outputActionIndex" :content="outputContent" />
        </div>
        <div v-else class="empty-panel">
          No output content available
        </div>
      </TabPanel>
    </TabView>
  </Dialog>
</template>

<script setup>
import { computed, ref } from "vue";
import { useClipboard } from "@vueuse/core";
import Badge from "primevue/badge";
import Button from "primevue/button";
import Column from "primevue/column";
import DataTable from "primevue/datatable";
import Dialog from "primevue/dialog";
import TabPanel from "primevue/tabpanel";
import TabView from "primevue/tabview";
import Toolbar from "primevue/toolbar";
import ContentSelector from "@/components/ContentSelector.vue";
import useNotifications from "@/composables/useNotifications";

const props = defineProps({
  did: {
    type: String,
    required: true,
  },
  flow: {
    type: Object,
    default: null,
  },
  parentFlow: {
    type: Object,
    default: null,
  },
});

const { copy: copyToClipboard } = useClipboard();
const notify = useNotifications();

const dialogVisible = ref(false);

const flowName = computed(() => props.flow?.name || "Flow Output");
const flowNumber = computed(() => props.flow?.number || 0);

const lastActionIndex = computed(() => {
  if (!props.flow?.actions?.length) return null;
  return props.flow.actions.length - 1;
});

const lastAction = computed(() => {
  if (lastActionIndex.value === null) return null;
  return props.flow.actions[lastActionIndex.value];
});

const inputContent = computed(() => {
  return props.flow?.input?.content || [];
});

const hasInputContent = computed(() => inputContent.value.length > 0);

const outputContent = computed(() => {
  if (lastAction.value?.content?.length > 0) {
    return lastAction.value.content;
  }
  return [];
});

const hasOutputContent = computed(() => outputContent.value.length > 0);

const outputActionIndex = computed(() => {
  if (lastAction.value?.content?.length > 0) {
    return lastActionIndex.value;
  }
  return undefined;
});

const contentIsUnchanged = computed(() => {
  if (!hasInputContent.value || !hasOutputContent.value) return false;
  if (inputContent.value.length !== outputContent.value.length) return false;

  for (let i = 0; i < inputContent.value.length; i++) {
    const input = inputContent.value[i];
    const output = outputContent.value[i];
    if (input.name !== output.name || input.mediaType !== output.mediaType || input.size !== output.size) {
      return false;
    }
    if (JSON.stringify(input.segments) !== JSON.stringify(output.segments)) {
      return false;
    }
  }
  return true;
});

const outputMetadata = computed(() => {
  const result = { ...(props.flow?.input?.metadata || {}) };

  for (const action of props.flow?.actions || []) {
    if (action.metadata) {
      Object.assign(result, action.metadata);
    }
    for (const key of action.deleteMetadataKeys || []) {
      delete result[key];
    }
  }

  return result;
});

const metadataArray = computed(() => {
  return Object.entries(outputMetadata.value).map(([key, value]) => ({ key, value }));
});

const hasMetadata = computed(() => metadataArray.value.length > 0);

const showDiff = ref(false);

const parentOutputMetadata = computed(() => {
  if (!props.parentFlow) return {};

  // Start with parent's input metadata
  const result = { ...(props.parentFlow.input?.metadata || {}) };

  // Apply parent's action changes
  for (const action of props.parentFlow.actions || []) {
    if (action.metadata) {
      Object.assign(result, action.metadata);
    }
    for (const key of action.deleteMetadataKeys || []) {
      delete result[key];
    }
  }

  return result;
});

const hasParentFlow = computed(() => props.parentFlow !== null);

const isTransform = computed(() => props.flow?.type === "TRANSFORM");

const isDataSource = computed(() => props.flow?.type?.endsWith("DATA_SOURCE"));

const canShowUnchangedBadge = computed(() => hasParentFlow.value && isTransform.value);

const canShowDiffToggle = computed(() => isDataSource.value || (hasParentFlow.value && isTransform.value));

const metadataHasChanges = computed(() => {
  // Data sources always have "changes" (everything is new)
  if (isDataSource.value) return true;
  if (!hasParentFlow.value) return true;

  const current = outputMetadata.value;
  const parent = parentOutputMetadata.value;

  const currentKeys = Object.keys(current);
  const parentKeys = Object.keys(parent);

  if (currentKeys.length !== parentKeys.length) return true;

  for (const key of currentKeys) {
    if (!(key in parent) || current[key] !== parent[key]) return true;
  }

  return false;
});

const diffMetadataArray = computed(() => {
  if (!showDiff.value) {
    return metadataArray.value;
  }

  // Data sources: everything is added
  if (isDataSource.value) {
    return metadataArray.value.map((item) => ({ ...item, diffStatus: "added" }));
  }

  if (!hasParentFlow.value) {
    return metadataArray.value;
  }

  const current = outputMetadata.value;
  const parent = parentOutputMetadata.value;
  const result = [];

  // Find all keys (union of current and parent)
  const allKeys = new Set([...Object.keys(current), ...Object.keys(parent)]);

  for (const key of allKeys) {
    const inCurrent = key in current;
    const inParent = key in parent;

    if (inCurrent && inParent) {
      if (current[key] === parent[key]) {
        // Unchanged
        result.push({ key, value: current[key], diffStatus: "unchanged" });
      } else {
        // Modified
        result.push({ key, value: current[key], oldValue: parent[key], diffStatus: "modified" });
      }
    } else if (inCurrent) {
      // Added
      result.push({ key, value: current[key], diffStatus: "added" });
    } else {
      // Removed
      result.push({ key, value: parent[key], diffStatus: "removed" });
    }
  }

  return result;
});

const getDiffClass = (status) => {
  if (!showDiff.value) return "";
  switch (status) {
    case "added":
      return "diff-added";
    case "removed":
      return "diff-removed";
    case "modified":
      return "diff-modified-row";
    default:
      return "";
  }
};

const onCopyMetadata = () => {
  copyToClipboard(JSON.stringify({ metadata: outputMetadata.value }, null, 2));
  notify.info("Copied to clipboard", "Metadata copied to clipboard.", 3000);
};

const onDownloadMetadata = () => {
  const link = document.createElement("a");
  const downloadFileName = "metadata_export_" + new Date(Date.now()).toLocaleDateString();
  link.download = downloadFileName.toLowerCase();
  const blob = new Blob([JSON.stringify({ metadata: outputMetadata.value }, null, 2)], {
    type: "application/json",
  });
  link.href = URL.createObjectURL(blob);
  link.click();
  URL.revokeObjectURL(link.href);
  link.remove();
};

const show = () => {
  dialogVisible.value = true;
  showDiff.value = false;
};

defineExpose({
  show,
});
</script>

<style scoped>
.content-panel {
  height: calc(90vh - 180px);
}

.metadata-panel {
  display: flex;
  flex-direction: column;
  height: calc(90vh - 180px);
}

.metadata-panel :deep(.p-toolbar) {
  border-radius: 4px 4px 0 0;
  padding: 0.5rem 0.75rem;
  min-height: auto;
  flex-shrink: 0;
}

.metadata-table-container {
  flex: 1;
  overflow: auto;
  min-height: 0;
}

.empty-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 200px;
  color: var(--text-color-secondary);
}

.metadata-value pre {
  margin: 0;
  padding: 0;
  white-space: pre-wrap;
  word-break: break-word;
}

.diff-added {
  background-color: var(--green-100);
  color: var(--green-900);
}

.diff-removed {
  background-color: var(--red-100);
  color: var(--red-900);
  text-decoration: line-through;
}

.diff-modified-row {
  background-color: var(--yellow-100);
}

.diff-modified {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.diff-modified .diff-old {
  color: var(--red-700);
  text-decoration: line-through;
}

.diff-modified .diff-new {
  color: var(--green-700);
}
</style>
