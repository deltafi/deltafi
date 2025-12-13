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
<!-- ABOUTME: Dialog for viewing a flow's output content and metadata. -->
<!-- ABOUTME: Used by the DeltaFile flow graph to show flow output on node click. -->

<template>
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
      <TabPanel header="Content" :disabled="!hasContent">
        <div v-if="hasContent" class="content-panel">
          <ContentSelector
            :did="did"
            :flow-number="flowNumber"
            :action-index="outputActionIndex"
            :content="outputContent"
          />
        </div>
        <div v-else class="empty-panel">
          No output content available
        </div>
      </TabPanel>
      <TabPanel header="Metadata" :disabled="!hasMetadata">
        <div v-if="hasMetadata" class="metadata-panel">
          <DataTable
            responsive-layout="scroll"
            :value="metadataArray"
            striped-rows
            sort-field="key"
            :sort-order="1"
            class="p-datatable-sm"
            scroll-height="calc(90vh - 200px)"
            data-key="key"
          >
            <Column field="key" header="Key" :style="{ width: '30%' }" :sortable="true" />
            <Column field="value" header="Value" :style="{ width: '70%' }" :sortable="true" class="metadata-value">
              <template #body="{ data }">
                <pre>{{ data.value }}</pre>
              </template>
            </Column>
          </DataTable>
        </div>
        <div v-else class="empty-panel">
          No metadata available
        </div>
      </TabPanel>
    </TabView>
  </Dialog>
</template>

<script setup>
import { computed, ref } from "vue";
import Dialog from "primevue/dialog";
import TabView from "primevue/tabview";
import TabPanel from "primevue/tabpanel";
import Column from "primevue/column";
import DataTable from "primevue/datatable";
import ContentSelector from "@/components/ContentSelector.vue";

const props = defineProps({
  did: {
    type: String,
    required: true,
  },
  flow: {
    type: Object,
    default: null,
  },
});

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

const outputContent = computed(() => {
  if (lastAction.value?.content?.length > 0) {
    return lastAction.value.content;
  }
  if (props.flow?.input?.content?.length > 0) {
    return props.flow.input.content;
  }
  return [];
});

const outputActionIndex = computed(() => {
  if (lastAction.value?.content?.length > 0) {
    return lastActionIndex.value;
  }
  return undefined;
});

const hasContent = computed(() => outputContent.value.length > 0);

const metadataArray = computed(() => {
  const metadata = props.flow?.input?.metadata || {};
  return Object.entries(metadata).map(([key, value]) => ({ key, value }));
});

const hasMetadata = computed(() => metadataArray.value.length > 0);

const show = () => {
  dialogVisible.value = true;
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
  height: calc(90vh - 180px);
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
</style>
