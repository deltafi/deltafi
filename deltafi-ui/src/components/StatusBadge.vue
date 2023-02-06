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
  <span>
    <Tag v-tooltip.bottom="'Click for more info'" class="status-tag" :icon="icon(computedStatus.code)" :severity="tagSeverity(computedStatus.code)" :value="computedStatus.state" @click="openStatusDialog()" />
    <Dialog v-model:visible="showStatusDialog" icon header="System Status" :style="{ width: '50vw' }" :maximizable="true" :modal="true" position :dismissable-mask="true">
      <span v-for="check in computedStatus.checks" :key="check.description">
        <Message :severity="messageSeverity(check.code)" :closable="false">{{ check.description }}</Message>
        <div v-if="check.message">
          <div class="message" v-html="markdown(check.message)" />
        </div>
      </span>
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
import Message from "primevue/message";
import Timestamp from "@/components/Timestamp.vue";
import { onMounted, ref, computed } from "vue";
import { useTimeAgo } from '@vueuse/core'

const markdownIt = new MarkdownIt();
const timeSinceLastStatusThreshold = 30;
const status = ref({})
const now = ref(new Date().getTime());
const showStatusDialog = ref(false);
const { serverSentEvents, connectionStatus } = useServerSentEvents();
const { fetchStatus, loading: apiLoading } = useStatus();

onMounted(async () => {
  status.value = await fetchStatus();
  setInterval(() => now.value = new Date().getTime(), 1000);
})

const timeSinceLastStatus = computed(() => {
  return Math.round((now.value - new Date(status.value.timestamp).getTime()) / 1000);
})

const statusBuilder = (code, state, checkCode, checkDescription, checkMessage, timestamp = new Date()) => {
  return {
    code: code,
    state: state,
    checks: [{
      code: checkCode,
      description: checkDescription,
      message: checkMessage
    }],
    timestamp: timestamp
  }
}

const computedStatus = computed(() => {
  if (connectionStatus.value === 'CONNECTING' || apiLoading.value) {
    return statusBuilder(3, "Connecting", 3, "API Connection", "Establishing connection to API...")
  } else if (connectionStatus.value === 'DISCONNECTED') {
    return statusBuilder(3, "Reconnecting", 3, "API Connection", "Connection to API has been lost. Reconnecting...")
  } else if (timeSinceLastStatus.value > timeSinceLastStatusThreshold) {
    const message = `The \`deltafi-monitor\` last reported system status ${timeSinceLastStatusInWords.value}.`
    return statusBuilder(1, "Unknown", 1, "Monitor", message, status.value.timestamp)
  } else {
    return status.value;
  }
})

const timeSinceLastStatusInWords = computed(() => {
  if (timeSinceLastStatus.value < 60) {
    return `${timeSinceLastStatus.value} seconds ago`;
  } else {
    return useTimeAgo(new Date(status.value.timestamp)).value;
  }
});

serverSentEvents.addEventListener('status', (event) => {
  status.value = JSON.parse(event.data);
});

const openStatusDialog = () => {
  showStatusDialog.value = true;
};

const tagSeverity = (code) => {
  let severities = ["success", "warning", "danger"];
  return severities[code] || "info";
};

const messageSeverity = (code) => {
  let severities = ["success", "warn", "error"];
  return severities[code] || "info";
};

const icon = (code) => {
  let icons = ["check", "exclamation-triangle", "times", "spin pi-spinner"];
  let icon = icons[code] || "question-circle";
  return `pi pi-${icon}`;
};

const markdown = (source) => {
  return markdownIt.render(source);
};
</script>

<style scoped lang="scss">
@import "@/styles/components/status-badge.scss";
</style>
