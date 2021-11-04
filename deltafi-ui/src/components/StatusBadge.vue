<template>
  <div>
    <Tag v-tooltip.left="'Click for more info'" class="p-mr-3 mr-3 status-tag" :icon="icon(status.code)" :severity="tagSeverity(status.code)" :value="status.state" @click="openStatusDialog()" />
    <Dialog v-model:visible="showStatusDialog" icon header="System Status Checks" :style="{width: '50vw'}" :maximizable="false" :modal="true" position="center">
      <span v-for="check in status.checks" :key="check.description">
        <Message :severity="messageSeverity(check.code)" :closable="false">
          {{ check.description }}
        </Message>
        <div v-if="check.message">
          <div class="message" v-html="markdown(check.message)" />
        </div>
      </span>
      <template v-if="timestamp" #footer>
        <small class="text-muted">Last Updated: {{ timestamp }}</small>
      </template>
    </Dialog>
  </div>
</template>

<script>
import ApiService from "../service/ApiService";
import MarkdownIt from "markdown-it";

const refreshInterval = 5000; // 5 seconds

export default {
  data() {
    return {
      status: {
        code: -1,
        state: "Unknown",
        checks: []
      },
      timestamp: null,
      autoRefresh: null,
      showStatusDialog: false,
    };
  },
  created() {
    this.apiService = new ApiService();
    this.markdownIt = new MarkdownIt();
  },
  mounted() {
    this.fetchStatus();
    this.autoRefresh = setInterval(
      function () {
        this.fetchStatus();
      }.bind(this),
      refreshInterval
    );
  },
  methods: {
    async fetchStatus() {
      let response = await this.apiService.getStatus();
      this.status.code = response.status.code;
      this.status.state = response.status.state;
      this.status.checks = response.status.checks;
      this.timestamp = response.timestamp;
    },
    openStatusDialog() {
      this.showStatusDialog = true;
    },
    tagSeverity(code) {
      let severities = ["success", "warning", "danger"];
      return severities[code] || "info";
    },
    messageSeverity(code) {
      let severities = ["success", "warn", "error"];
      return severities[code] || "info";
    },
    icon(code) {
      let icons = ["check", "exclamation-triangle", "times"];
      let icon = icons[code] || "question-circle";
      return `pi pi-${icon}`;
    },
    markdown(source) {
      return this.markdownIt.render(source);
    }
  },
  apiService: null,
  markdownIt: null,
};
</script>

<style>
.status-tag {
  cursor: pointer;
}
.message {
  margin-left: 1rem;
  margin-right: 1rem;
}
.message pre {
  background-color: #333333;
  color: #dddddd;
  padding: 1em;
  border-radius: 4px;
}
</style>
