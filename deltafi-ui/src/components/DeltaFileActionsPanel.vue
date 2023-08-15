<!--
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>

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
    <CollapsiblePanel header="Actions" class="actions-panel table-panel">
      <DataTable responsive-layout="scroll" class="p-datatable-sm p-datatable-gridlines" striped-rows :value="actions" :row-class="rowClass" @row-click="rowClick">
        <Column field="name" header="Action" :sortable="true" />
        <Column field="flow" header="Flow" :sortable="true" />
        <Column field="state" header="State" class="state-column" :sortable="true" />
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
        <Column field="elapsed" header="Elapsed" class="elapsed-column" :sortable="true">
          <template #body="action">{{ action.data.elapsed }}</template>
        </Column>
        <Column v-if="!contentDeleted && hasPermission('DeltaFileContentView')" header="Content" class="content-column">
          <template #body="{ data: action }">
            <span v-if="action.hasOwnProperty('content') && action.content.length > 0">
              <ContentDialog :content="action.content" :action="action.name">
                <Button icon="far fa-window-maximize" label="View" class="content-button p-button-link" />
              </ContentDialog>
            </span>
          </template>
        </Column>
        <Column header="Metadata" class="metadata-column">
          <template #body="{ data: action }">
            <span v-if="action.hasOwnProperty('metadata') && Object.keys(action.metadata).length > 0">
              <DialogTemplate component-name="MetadataViewer" header="Metadata" :metadata="{ [action.name]: metadataAsArray(action.metadata) }" :deleted-metadata="deletedMetadata(action.name, action.deleteMetadataKeys)">
                <Button icon="fas fa-table" label="View" class="content-button p-button-link" />
              </DialogTemplate>
            </span>
          </template>
        </Column>
      </DataTable>
    </CollapsiblePanel>
    <ErrorViewerDialog v-model:visible="errorViewer.visible" :action="errorViewer.action" />
  </div>
</template>

<script setup>
import CollapsiblePanel from "@/components/CollapsiblePanel.vue";
import ContentDialog from "@/components/ContentDialog.vue";
import DialogTemplate from "@/components/DialogTemplate.vue";
import ErrorViewerDialog from "@/components/errors/ErrorViewerDialog.vue";
import Timestamp from "@/components/Timestamp.vue";
import useUtilFunctions from "@/composables/useUtilFunctions";
import { computed, reactive, defineProps, inject } from "vue";

import Button from "primevue/button";
import Column from "primevue/column";
import DataTable from "primevue/datatable";

const hasPermission = inject("hasPermission");

const props = defineProps({
  deltaFileData: {
    type: Object,
    required: true,
  },
});

const { duration } = useUtilFunctions();
const deltaFile = reactive(props.deltaFileData);
const errorViewer = reactive({
  visible: false,
  action: {},
});

const contentDeleted = computed(() => {
  return deltaFile.contentDeleted !== null;
});

const actions = computed(() => {
  return deltaFile.actions.map((action) => {
    const timeElapsed = new Date(action.modified) - new Date(action.created);
    action.created = new Date(action.created).toISOString();
    action.modified = new Date(action.modified).toISOString();
    return {
      ...action,
      elapsed: duration(timeElapsed),
    };
  });
});

const metadataAsArray = (metadataObject) => {
  return Object.entries(metadataObject).map(([key, value]) => ({ key, value }));
};

const deletedMetadata = (deletedMetadataActionName) => {
  let deletedMetadataList = _.filter(deltaFile.actions, function (o) {
    return o.deleteMetadataKeys.length > 0 && _.isEqual(o.name, deletedMetadataActionName);
  });

  if (_.isEmpty(deletedMetadataList)) return null;
  return deletedMetadataList;
};

const rowClass = (action) => {
  if (action.state === "ERROR") return "table-danger action-error";
  if (action.state === "RETRIED") return "table-warning action-error";
  if (action.state === "FILTERED") return "table-warning action-error";
};

const rowClick = (event) => {
  let action = event.data;
  if (!["ERROR", "RETRIED", "FILTERED"].includes(action.state)) return;

  errorViewer.visible = true;
  errorViewer.action = action;
};
</script>

<style lang="scss">
@import "@/styles/components/deltafile-actions-panel.scss";
</style>
