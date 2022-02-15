<template>
  <div>
    <Tag v-tooltip.left="'Click for more info'" class="p-mr-3 mr-3 status-tag" :icon="icon(status.code)" :severity="tagSeverity(status.code)" :value="status.state" @click="openStatusDialog()" />
    <Dialog v-model:visible="showStatusDialog" icon header="System Status Checks" :style="{ width: '50vw' }" :maximizable="true" :modal="true" position="" :dismissable-mask="true">
      <span v-for="check in status.checks" :key="check.description">
        <Message :severity="messageSeverity(check.code)" :closable="false">
          {{ check.description }}
        </Message>
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

<script>
import useStatus from "@/composables/useStatus";
import MarkdownIt from "markdown-it";
import Dialog from "primevue/dialog";
import Tag from "primevue/tag";
import Message from "primevue/message";
import { ref, onMounted } from "vue";

const refreshInterval = 5000; // 5 seconds

export default {
  components: {
    Dialog,
    Tag,
    Message,
  },
  setup() {
    const status = ref({
      code: -1,
      state: "Unknown",
      checks: [],
      timestamp: null,
    });
    const showStatusDialog = ref(false);
    const { data: response, fetch: getStatus } = useStatus();

    const fetchStatus = async () => {
      await getStatus();
      status.value = response.value;
    };

    onMounted(() => {
      fetchStatus();
      setInterval(fetchStatus, refreshInterval);
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
      let icons = ["check", "exclamation-triangle", "times"];
      let icon = icons[code] || "question-circle";
      return `pi pi-${icon}`;
    };

    const markdownIt = new MarkdownIt();
    const markdown = (source) => {
      return markdownIt.render(source);
    };

    return {
      status,
      showStatusDialog,
      openStatusDialog,
      tagSeverity,
      messageSeverity,
      icon,
      markdown,
    };
  },
};
</script>

<style scoped lang="scss">
@import "@/styles/components/status-badge.scss";
</style>