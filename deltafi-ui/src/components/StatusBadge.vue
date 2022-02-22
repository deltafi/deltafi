<template>
  <div>
    <Tag v-tooltip.left="'Click for more info'" class="p-mr-3 mr-3 status-tag" :icon="icon(status.code)" :severity="tagSeverity(status.code)" :value="status.state" @click="openStatusDialog()" />
    <Dialog v-model:visible="showStatusDialog" icon header="System Status" :style="{ width: '50vw' }" :maximizable="true" :modal="true" position :dismissable-mask="true">
      <span v-for="check in status.checks" :key="check.description">
        <Message :severity="messageSeverity(check.code)" :closable="false">{{ check.description }}</Message>
        <div v-if="check.message">
          <div class="message" v-html="markdown(check.message)" />
        </div>
      </span>
      <template #footer>
        <small v-if="status.timestamp" class="text-muted">Last Updated: {{ status.timestamp }}</small>
      </template>
    </Dialog>
  </div>
</template>

<script setup>
import useServerSentEvents from "@/composables/useServerSentEvents";
import MarkdownIt from "markdown-it";
import Dialog from "primevue/dialog";
import Tag from "primevue/tag";
import Message from "primevue/message";
import { ref, watch } from "vue";

const status = ref({
  state: "Connecting",
  code: 3,
  checks: [{
    description: "API Connection",
    message: "Establishing connection to API..."
  }]
})
const showStatusDialog = ref(false);
const { serverSentEvents, connectionStatus } = useServerSentEvents();

watch(connectionStatus, (value) => {
  if (value === 'DISCONNECTED') {
    status.value = {
      code: 3,
      state: "Reconnecting",
      checks: [{
        description: "API Connection",
        code: 3,
        message: "Connection to API has been lost. Reconnecting..."
      }]
    };
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

const markdownIt = new MarkdownIt();
const markdown = (source) => {
  return markdownIt.render(source);
};
</script>

<style scoped lang="scss">
@import "@/styles/components/status-badge.scss";
</style>