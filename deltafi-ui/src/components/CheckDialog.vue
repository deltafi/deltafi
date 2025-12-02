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
  <span>
    <Dialog v-model:visible="showStatusDialog" icon :header="props.header" :style="{ width: '50vw' }" :maximizable="true" :modal="true" :dismissable-mask="true" class="status-dialog">
      <DataTable v-model:expanded-rows="rowsExpanded" :value="props.checks" data-key="description" responsive-layout="scroll" class="p-datatable-sm p-datatable-gridlines status-table">
        <Column :expander="true" header-style="width: 3rem" class="expander-col" />
        <Column field="code" header="Status" class="severity-col">
          <template #body="slotProps">
            <EventSeverityBadge :severity="messageSeverityByCode(slotProps.data.code)" style="width: 6rem"
                :tooltip="slotProps.data.nextRunTime == null ? '' : 'Paused until ' + slotProps.data.nextRunTime" />
          </template>
        </Column>
        <Column field="description" header="Description" />
        <Column field="timestamp" header="Last Run" :sortable="true" class="timestamp-column">
          <template #body="row">
            <Timestamp :timestamp="row.data.timestamp" style="display: inline-block; padding-top: 2px;" />
          </template>
        </Column>
        <Column header-style="width: 3rem" v-if="!props.readonly" class="pause-control-col">
          <template #body="row">
            <Button v-if="row.data.loading" class="pause-control-button" icon="pi pi-spin pi-spinner" link disabled />
            <span v-else>
              <Button v-if="row.data.nextRunTime" class="pause-control-button" v-tooltip.top="'Resume'"
                  icon="pi pi-play-circle" link aria-label="Resume"
                  @click="onResume(row.data.statusCheckId)" />
              <Button v-if="!row.data.nextRunTime" class="pause-control-button" v-tooltip.top="'Pause'"
                  icon="pi pi-pause-circle" link aria-label="Pause"
                  @click="openPauseDialog(row.data.statusCheckId)" />
            </span>
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
    <Dialog v-if="!props.readonly" v-model:visible="showPauseDialog" icon :header="'Pause ' + pauseStatusCheckId" :style="{ width: '30vw' }" :modal="true" :dismissable-mask="true" class="pause-dialog">
      <Dropdown :options="pauseDurations" placeholder="Choose duration..." optionLabel="label" @change="onPauseChange($event, pauseStatusCheckId)" />
    </Dialog>
  </span>
</template>

<script setup>
import useStatus from "@/composables/useStatus";
import MarkdownIt from "markdown-it";
import Button from "primevue/button";
import Column from "primevue/column";
import DataTable from "primevue/datatable";
import Dialog from "primevue/dialog";
import Dropdown from "primevue/dropdown";
import Timestamp from "@/components/Timestamp.vue";
import EventSeverityBadge from "./events/EventSeverityBadge.vue";
import { ref, nextTick } from "vue";

const markdownIt = new MarkdownIt();
const showStatusDialog = ref(false);
const showPauseDialog = ref(false);
const pauseStatusCheckId = ref(-1);
const { pauseStatusCheck, resumeStatusCheck } = useStatus();
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
    type: [Date, String, Number],
    required: false,
  },
  readonly: {
    type: Boolean,
    required: false,
    default: false,
  },
});

const show = () => {
  nextTick(() => {
    showStatusDialog.value = true;
    rowsExpanded.value = [];
  });
};

const openPauseDialog = async (statusCheckId) => {
  showPauseDialog.value = true;
  pauseStatusCheckId.value = statusCheckId;
};

const pauseDurations = [
  { label: " 5 minutes", value: "PT5M" },
  { label: "15 minutes", value: "PT15M" },
  { label: "30 minutes", value: "PT30M" },
  { label: " 1 hour", value: "PT1H" },
  { label: " 3 hours", value: "PT3H" },
  { label: " 6 hours", value: "PT6H" },
  { label: "12 hours", value: "PT12H" },
  { label: "24 hours", value: "P1D" },
  { label: " 2 days", value: "P2D" }
];

const onPauseChange = (event, statusCheckId) => {
  pauseStatusCheck(statusCheckId, event.value.value);
  showPauseDialog.value = false;
  props.checks.forEach((check) => {
    if (check.statusCheckId === statusCheckId) { check.loading = true; }
  });
};

const onResume = (statusCheckId) => {
  resumeStatusCheck(statusCheckId);
  props.checks.forEach((check) => {
    if (check.statusCheckId === statusCheckId) { check.loading = true; }
  });
};

const messageSeverityByCode = (code) => {
  if (code === -1) return "inactive";
  return ["success", "warn", "error"][code] || "info";
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

.pause-dialog {
  .p-dialog-content {
    padding: 1rem !important;
  }
}

.status-table {
  .timestamp-column {
    width: 18rem !important;
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

  .pause-control-button {
    height: 20px;
    padding: 0;
    color: var(--text-color-secondary);
  }
}
</style>
