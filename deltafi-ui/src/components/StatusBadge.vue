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
    <Tag v-tooltip.bottom="'Click for more info'" class="status-tag" :icon="icon(computedStatus.code)" :severity="tagSeverity(computedStatus.code)" :value="computedStatus.state" @click="openStatusDialog()" />
    <Dialog v-model:visible="showStatusDialog" icon header="System Status Checks" :style="{ width: '50vw' }" :maximizable="true" :modal="true" :dismissable-mask="true" class="status-dialog">
      <DataTable v-model:expanded-rows="rowsExpanded" :value="computedStatus.checks" data-key="description" responsive-layout="scroll" class="p-datatable-sm p-datatable-gridlines status-table">
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
        <small v-if="computedStatus.timestamp" class="text-muted">
          Last Updated:
          <Timestamp :timestamp="computedStatus.timestamp" />
        </small>
      </template>
    </Dialog>
  </span>
</template>

<script setup>
import useServerSentEvents from "@/composables/useServerSentEvents";
import useStatus from "@/composables/useStatus";
import MarkdownIt from "markdown-it";
import Dialog from "primevue/dialog";
import Tag from "primevue/tag";
import Timestamp from "@/components/Timestamp.vue";
import { onMounted, ref, computed, nextTick } from "vue";
import { useTimeAgo } from "@vueuse/core";
import DataTable from "primevue/datatable";
import Column from "primevue/column";
import EventSeverityBadge from "./events/EventSeverityBadge.vue";

const markdownIt = new MarkdownIt();
const timeSinceLastStatusThreshold = 30;
const status = ref({});
const now = ref(new Date().getTime());
const showStatusDialog = ref(false);
const { serverSentEvents, connectionStatus } = useServerSentEvents();
const { fetchStatus, loading: apiLoading } = useStatus();
const rowsExpanded = ref([]);

onMounted(async () => {
  status.value = await fetchStatus();
  setInterval(() => (now.value = new Date().getTime()), 1000);
});

const timeSinceLastStatus = computed(() => {
  return Math.round((now.value - new Date(status.value.timestamp).getTime()) / 1000);
});

const statusBuilder = (code, state, checkCode, checkDescription, checkMessage, timestamp = new Date()) => {
  return {
    code: code,
    state: state,
    checks: [
      {
        code: checkCode,
        description: checkDescription,
        message: checkMessage,
      },
    ],
    timestamp: timestamp,
  };
};

const computedStatus = computed(() => {
  if (connectionStatus.value === "CONNECTING" || apiLoading.value) {
    return statusBuilder(3, "Connecting", 3, "API Connection", "Establishing connection to API...");
  } else if (connectionStatus.value === "DISCONNECTED") {
    return statusBuilder(3, "Reconnecting", 3, "API Connection", "Connection to API has been lost. Reconnecting...");
  } else if (timeSinceLastStatus.value > timeSinceLastStatusThreshold) {
    const message = `The \`deltafi-monitor\` last reported system status ${timeSinceLastStatusInWords.value}.`;
    return statusBuilder(1, "Unknown", 1, "Monitor", message, status.value.timestamp);
  } else {
    return status.value;
  }
});

const timeSinceLastStatusInWords = computed(() => {
  if (timeSinceLastStatus.value < 60) {
    return `${timeSinceLastStatus.value} seconds ago`;
  } else {
    return useTimeAgo(new Date(status.value.timestamp)).value;
  }
});

serverSentEvents.addEventListener("status", (event) => {
  try {
    status.value = JSON.parse(event.data);
  } catch (error) {
    console.error(`Failed to parse SSE status data: ${event.data}`);
  }
});

const openStatusDialog = async () => {
  rowsExpanded.value = [];
  showStatusDialog.value = true;
};

const tagSeverity = (code) => {
  const severities = ["success", "warning", "danger"];
  return severities[code] || "info";
};

const messageSeverity = (code) => {
  const severities = ["success", "warn", "error"];
  return severities[code] || "info";
};

const icon = (code) => {
  const icons = ["check", "exclamation-triangle", "times", "spin pi-spinner"];
  const icon = icons[code] || "question-circle";
  return `pi pi-${icon}`;
};

const markdown = (source) => {
  return markdownIt.render(source);
};
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
