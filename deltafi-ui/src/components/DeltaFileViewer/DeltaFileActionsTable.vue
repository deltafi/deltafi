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
    <DataTable responsive-layout="scroll" class="p-datatable-sm p-datatable-gridlines" striped-rows :value="actions" :row-class="rowClass" data-key="name" @row-click="rowClick">
      <Column field="name" header="Action" :sortable="true" />
      <Column field="type" header="Type" :sortable="true" />
      <Column field="state" header="State" class="state-column" :sortable="true">
        <template #body="{ data: action }">
          {{ action.state }}
          <AutoResumeBadge v-if="action.state === 'ERROR' && action.nextAutoResume !== null" :timestamp="action.nextAutoResume" :reason="action.nextAutoResumeReason" class="ml-2" />
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
      <Column field="elapsed" header="Elapsed" class="elapsed-column" :sortable="true">
        <template #body="action">
          {{ action.data.elapsed }}
        </template>
      </Column>
      <Column v-if="!contentDeleted && $hasPermission('DeltaFileContentView')" header="Content" class="content-column">
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
          <span v-if="(action.hasOwnProperty('metadata') && Object.keys(action.metadata).length > 0) || action.deleteMetadataKeys.length > 0">
            <DialogTemplate component-name="MetadataViewer" header="Metadata" :metadata="{ [action.name]: metadataAsArray(action.metadata) }" :deleted-metadata="deletedMetadata(action.name, action.deleteMetadataKeys)" :dismissable-mask="true">
              <Button icon="fas fa-table" label="View" class="content-button p-button-link" />
            </DialogTemplate>
          </span>
        </template>
      </Column>
    </DataTable>
    <ErrorViewerDialog v-model:visible="errorViewer.visible" :action="errorViewer.action" />
  </div>
</template>

<script setup>
import AutoResumeBadge from "@/components/errors/AutoResumeBadge.vue";
import ContentDialog from "@/components/ContentDialog.vue";
import DialogTemplate from "@/components/DialogTemplate.vue";
import ErrorViewerDialog from "@/components/errors/ErrorViewerDialog.vue";
import Timestamp from "@/components/Timestamp.vue";
import useUtilFunctions from "@/composables/useUtilFunctions";
import { computed, reactive, inject } from "vue";

import _ from "lodash";

import Button from "primevue/button";
import Column from "primevue/column";
import DataTable from "primevue/datatable";

const hasPermission = inject("hasPermission");

const props = defineProps({
  deltaFileData: {
    type: Object,
    required: true,
  },
  contentDeleted: {
    type: Boolean,
    required: false,
    default: false,
  },
});

const { duration } = useUtilFunctions();
const deltaFile = reactive(props.deltaFileData);
const errorViewer = reactive({
  visible: false,
  action: {},
});

const contentDeleted = computed(() => {
  // return deltaFile.contentDeleted !== null;
  return props.contentDeleted;
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

const deletedMetadata = (deletedMetadataActionName, deletedMetadataActionDeletedKeys) => {
  if (_.isEmpty(deletedMetadataActionDeletedKeys)) return null;
  const deletedMetadataList = [];
  const deletedMetadataObject = {};
  deletedMetadataObject["name"] = deletedMetadataActionName;
  deletedMetadataObject["deleteMetadataKeys"] = deletedMetadataActionDeletedKeys;
  deletedMetadataList.push(deletedMetadataObject);
  return deletedMetadataList;
};

const rowClass = (action) => {
  if (action.state === "ERROR") return "table-danger action-error";
  if (action.state === "RETRIED") return "table-warning action-error";
  if (action.state === "FILTERED") return "table-warning action-error";
};

const rowClick = (event) => {
  const action = event.data;
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
