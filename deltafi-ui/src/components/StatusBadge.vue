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
    <CheckDialog ref="checkDialog" :header="'System Status Details'" :checks="computedStatus.checks" :last-updated="computedStatus.timestamp" />
  </span>
</template>

<script setup>
import useServerSentEvents from "@/composables/useServerSentEvents";
import useStatus from "@/composables/useStatus";
import MarkdownIt from "markdown-it";
import Tag from "primevue/tag";
import { onMounted, ref, computed } from "vue";
import { useTimeAgo } from "@vueuse/core";
import CheckDialog from "@/components/CheckDialog.vue";

const markdownIt = new MarkdownIt();
const timeSinceLastStatusThreshold = 30;
const clockSkewThreshold = 60_000; // 60 seconds
const status = ref({ checks: [] });
const now = ref(new Date().getTime());
const { serverSentEvents, connectionStatus } = useServerSentEvents();
const { fetchStatus, loading: apiLoading } = useStatus();
const checkDialog = ref(null);

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

const clockSkewCheck = computed(() => {
  if (status.value.timestamp === undefined) return;

  const clientTime = new Date();
  const serverTime = new Date(status.value.timestamp);
  const skew = Math.abs(clientTime - serverTime)
  if (skew <= clockSkewThreshold) return;

  const messageLines = [
    "The time on this client device does not match the server time. This could affect session security and search capabilities. Please check your system clock and ensure it is set to update automatically.\n",
    `    Current server time: ${serverTime.toISOString()}`,
    `    Current client time: ${clientTime.toISOString()}`,
    `    Time difference: ${skew}ms`
  ]
  return {
    description: "Clock Skew",
    code: 1,
    message: messageLines.join("\n"),
    timestamp: clientTime
  }
})

const computedStatus = computed(() => {
  if (connectionStatus.value === "CONNECTING" || apiLoading.value) {
    return statusBuilder(3, "Connecting", 3, "API Connection", "Establishing connection to API...");
  } else if (connectionStatus.value === "DISCONNECTED") {
    return statusBuilder(3, "Reconnecting", 3, "API Connection", "Connection to API has been lost. Reconnecting...");
  } else if (timeSinceLastStatus.value > timeSinceLastStatusThreshold) {
    const message = `The \`deltafi-monitor\` last reported system status ${timeSinceLastStatusInWords.value}.`;
    return statusBuilder(1, "Unknown", 1, "Monitor", message, status.value.timestamp);
  } else if (clockSkewCheck.value) {
    return {
      ...status.value,
      code: status.value.code == 0 ? 1 : status.value.code,
      state: status.value.state == "Healthy" ? "Degraded" : status.value.state,
      checks: [clockSkewCheck.value, ...status.value.checks],
    }
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
  checkDialog.value.show();
};

const tagSeverity = (code) => {
  const severities = ["success", "warning", "danger"];
  return severities[code] || "info";
};

const icon = (code) => {
  const icons = ["check", "exclamation-triangle", "times", "spin pi-spinner"];
  const icon = icons[code] || "question-circle";
  return `pi pi-${icon}`;
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
