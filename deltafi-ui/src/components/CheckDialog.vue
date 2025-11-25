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
  <Dialog v-model:visible="showStatusDialog" icon :header="props.header" :style="{ width: '50vw' }" :maximizable="true" :modal="true" :dismissable-mask="true" class="status-dialog">
    <DataTable v-model:expanded-rows="rowsExpanded" :value="props.checks" data-key="description" responsive-layout="scroll" class="p-datatable-sm p-datatable-gridlines status-table">
      <Column :expander="true" header-style="width: 3rem" class="expander-col" />
      <Column field="code" header="Status" class="severity-col">
        <template #body="slotProps">
          <EventSeverityBadge :severity="messageSeverity(slotProps.data.code)" style="width: 6rem" />
        </template>
      </Column>
      <Column field="description" header="Description" />
      <Column header="Last Run" field="timestamp" :sortable="true" style="width: 15rem" class="timestamp-column">
        <template #body="row">
          <Timestamp :timestamp="row.data.timestamp" style="width: 12rem" />
        </template>
      </Column>
      <template #expansion="message">
        <div v-if="message.data.message !== ''">
          <div class="message" v-html="markdown(message.data.message)" />
        </div>
        <div v-else>
          <div class="message"><i>No details provided</i></div>
        </div>
      </template>
    </DataTable>
    <template #footer>
      <small v-if="props.lastUpdated" class="text-muted">
        Last Updated:
        <Timestamp :timestamp="props.lastUpdated" />
      </small>
    </template>
  </Dialog>
</template>

<script setup>
import MarkdownIt from "markdown-it";
import Dialog from "primevue/dialog";
import Timestamp from "@/components/Timestamp.vue";
import DataTable from "primevue/datatable";
import Column from "primevue/column";
import EventSeverityBadge from "./events/EventSeverityBadge.vue";
import { ref, nextTick } from "vue";

const markdownIt = new MarkdownIt();
const showStatusDialog = ref(false);
const rowsExpanded = ref([]);

const props = defineProps({
  header: {
    type: String,
    required: true,
  },
  checks: {
    type: Array,
    required: true,
  },
  lastUpdated: {
    type: String,
    required: true,
  },
  show: {
    type: Boolean,
    required: true,
  },
});

const show = () => {
  nextTick(() => {
    showStatusDialog.value = true;
    rowsExpanded.value = [];
  });
};

const messageSeverity = (code) => {
  const severities = ["success", "warn", "error"];
  return severities[code] || "info";
};

const markdown = (source) => {
  return markdownIt.render(source);
};

defineExpose({ show });
</script>

<style>
.status-tag {
  cursor: pointer;
}

.status-dialog {
  .p-dialog-content {
    padding: 0.5rem !important;
  }
}

.status-table {
  .timestamp-column {
    font-size: 90%;
    width: 14rem !important;
  }

  td.expander-col {
    padding: 0 0.5rem !important;
  }

  td.severity-col {
    padding: 0 0.5rem !important;
    width: 1rem;
  }

  .message {
    margin: 0.5rem;
  }

  .message pre {
    background-color: #333333;
    color: #dddddd;
    padding: 1em;
    border-radius: 4px;
  }
}
</style>
