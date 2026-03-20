<!--
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>

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

<!-- ABOUTME: Dialog for executing bulk error actions across fleet members. -->
<!-- ABOUTME: Supports acknowledge, resume, replay, and replay+acknowledge with progress tracking. -->

<template>
  <Dialog v-model:visible="displayDialog" :header="dialogHeader" :maximizable="false" :modal="true" :breakpoints="{ '960px': '75vw', '940px': '90vw' }" :style="{ width: '40vw' }" @update:visible="close">
    <div class="p-fluid">
      <div class="mb-3">
        <span class="font-semibold">Target Members:</span>
        {{ targetMemberNames.length === 0 ? "All members" : targetMemberNames.join(", ") }}
      </div>

      <div v-if="action === 'replay'" class="mt-3">
        <div class="flex align-items-center">
          <Checkbox v-model="acknowledgeAfterReplay" input-id="ackCheckbox" :binary="true" />
          <label for="ackCheckbox" class="ml-2">Acknowledge after replay</label>
        </div>
      </div>

      <div v-if="requiresReason" class="mt-3">
        <div class="field">
          <label for="reason">Reason (required)</label>
          <InputText id="reason" ref="reasonInput" v-model="reason" type="text" :class="{ 'p-invalid': reasonInvalid }" />
        </div>
      </div>

      <div v-if="activeFilters.length > 0" class="mt-3">
        <span class="font-semibold">Active Filters:</span>
        <ul class="filter-list mt-2">
          <li v-for="filter in activeFilters" :key="filter.label">
            <strong>{{ filter.label }}:</strong> {{ filter.value }}
          </li>
        </ul>
      </div>
    </div>

    <template #footer>
      <Button label="Cancel" icon="pi pi-times" class="p-button-text" @click="close" />
      <Button :label="actionLabel" :icon="actionIcon" @click="executeAction" />
    </template>
  </Dialog>

  <Dialog :visible="displayProgressDialog" :breakpoints="{ '960px': '75vw', '940px': '90vw' }" :style="{ width: '35vw' }" :modal="true" :closable="false" :close-on-escape="false" :draggable="false" :header="progressHeader">
    <div>
      <p v-if="!stopped">{{ actionLabel }} in progress.<span v-if="expectedTotalCount > 1"> If you navigate away, the operation will stop after the current batch.</span></p>
      <p v-else>Completing current batch...</p>

      <!-- Summary progress bar -->
      <ProgressBar :value="overallProgress" />
      <p class="mt-2 mb-3 text-center">{{ totalProcessedCount.toLocaleString() }} / {{ expectedTotalCount.toLocaleString() }} DeltaFiles</p>

      <!-- Per-node progress bars -->
      <div class="node-progress-list">
        <div v-for="node in nodeProgress" :key="node.memberName" class="node-progress-row">
          <span class="node-name">{{ node.memberName }}</span>
          <ProgressBar :value="node.progress" class="node-progress-bar" :class="getProgressBarClass(node.status)" />
        </div>
      </div>
    </div>
    <template #footer>
      <Button v-if="isProcessing" label="Stop" icon="pi pi-stop" class="p-button-danger" @click="stopProcessing" />
      <Button v-else label="Close" icon="pi pi-check" @click="closeProgress" />
    </template>
  </Dialog>
</template>

<script setup>
import ProgressBar from "primevue/progressbar";
import useFederatedErrors from "@/composables/useFederatedErrors";
import { computed, nextTick, ref, watch } from "vue";

import Button from "primevue/button";
import Checkbox from "primevue/checkbox";
import Dialog from "primevue/dialog";
import InputText from "primevue/inputtext";
import { useToast } from "primevue/usetoast";

const props = defineProps({
  visible: {
    type: Boolean,
    required: true,
  },
  action: {
    type: String,
    required: true,
    validator: (value) => ["acknowledge", "resume", "replay"].includes(value),
  },
  memberNames: {
    type: Array,
    required: false,
    default: () => [],
  },
  filter: {
    type: Object,
    required: true,
  },
  searchResponse: {
    type: Object,
    required: false,
    default: null,
  },
  expectedTotal: {
    type: Number,
    required: false,
    default: null,
  },
  flows: {
    type: Array,
    required: false,
    default: null,
  },
  messages: {
    type: Array,
    required: false,
    default: null,
  },
});

const emit = defineEmits(["completed", "update:visible"]);

const { executeActionOnMember } = useFederatedErrors();
const toast = useToast();

const displayDialog = ref(false);
const displayProgressDialog = ref(false);
const reason = ref("");
const reasonInvalid = ref(false);
const stopped = ref(false);
const acknowledgeAfterReplay = ref(true);
const reasonInput = ref(null);

// Progress tracking
const isProcessing = ref(false);
const totalProcessedCount = ref(0);
const totalSuccessCount = ref(0);
const totalFailureCount = ref(0);
const expectedTotalCount = ref(0);
const nodeProgress = ref([]);

const requiresReason = computed(() => props.action === "acknowledge" || (props.action === "replay" && acknowledgeAfterReplay.value));

// The effective action to execute (replay becomes replayAndAcknowledge when checkbox is checked)
const effectiveAction = computed(() => {
  if (props.action === "replay" && acknowledgeAfterReplay.value) {
    return "replayAndAcknowledge";
  }
  return props.action;
});

// Get target member names - either from selection or all connected members from search response
const targetMemberNames = computed(() => {
  if (props.memberNames.length > 0) {
    return props.memberNames;
  }
  if (props.searchResponse?.results) {
    return props.searchResponse.results
      .filter((r) => r.status === "CONNECTED")
      .map((r) => r.memberName);
  }
  return [];
});

const overallProgress = computed(() => {
  if (expectedTotalCount.value === 0) return 0;
  return Math.round((totalProcessedCount.value / expectedTotalCount.value) * 100);
});

const getProgressBarClass = (status) => {
  switch (status) {
    case "completed":
      return "progress-success";
    case "failed":
      return "progress-danger";
    case "processing":
      return "progress-processing";
    default:
      return "progress-pending";
  }
};

const progressHeader = computed(() => {
  switch (effectiveAction.value) {
    case "acknowledge":
      return "Acknowledging Errors";
    case "resume":
      return "Resuming Errors";
    case "replay":
      return "Replaying Errors";
    case "replayAndAcknowledge":
      return "Replaying and Acknowledging Errors";
    default:
      return "Processing Errors";
  }
});

const dialogHeader = computed(() => {
  switch (props.action) {
    case "acknowledge":
      return "Acknowledge Errors";
    case "resume":
      return "Resume Errors";
    case "replay":
      return "Replay Errors";
    default:
      return "Error Action";
  }
});

const actionLabel = computed(() => {
  switch (effectiveAction.value) {
    case "acknowledge":
      return "Acknowledge";
    case "resume":
      return "Resume";
    case "replay":
      return "Replay";
    case "replayAndAcknowledge":
      return "Replay + Acknowledge";
    default:
      return "Execute";
  }
});

const actionIcon = computed(() => {
  switch (effectiveAction.value) {
    case "acknowledge":
      return "pi pi-check-square";
    case "resume":
      return "pi pi-play";
    case "replay":
      return "pi pi-refresh";
    case "replayAndAcknowledge":
      return "pi pi-replay";
    default:
      return "pi pi-bolt";
  }
});

const activeFilters = computed(() => {
  const filters = [];
  if (props.filter.dataSources?.length) {
    filters.push({ label: "Data Sources", value: props.filter.dataSources.join(", ") });
  }
  if (props.messages?.length) {
    // When multiple messages are selected, show them as a filter
    const messageList = props.messages.length > 3
      ? props.messages.slice(0, 3).join(", ") + ` (+${props.messages.length - 3} more)`
      : props.messages.join(", ");
    filters.push({ label: "Error Messages", value: messageList });
  } else if (props.filter.errorCause) {
    filters.push({ label: "Error Message", value: props.filter.errorCause });
  }
  if (props.filter.errorAcknowledged !== undefined) {
    filters.push({ label: "Acknowledged", value: props.filter.errorAcknowledged ? "Yes" : "No" });
  }
  if (props.filter.modifiedAfter) {
    filters.push({ label: "Modified After", value: props.filter.modifiedAfter });
  }
  if (props.filter.modifiedBefore) {
    filters.push({ label: "Modified Before", value: props.filter.modifiedBefore });
  }
  return filters;
});

watch(
  () => props.visible,
  (newVal) => {
    displayDialog.value = newVal;
    if (newVal) {
      reason.value = "";
      reasonInvalid.value = false;
      acknowledgeAfterReplay.value = true;
      // Focus reason input after dialog renders
      nextTick(() => {
        if (requiresReason.value && reasonInput.value) {
          reasonInput.value.$el.focus();
        }
      });
    }
  }
);

// Focus reason input when acknowledge checkbox is checked
watch(acknowledgeAfterReplay, (newVal) => {
  if (newVal && props.action === "replay") {
    nextTick(() => {
      if (reasonInput.value) {
        reasonInput.value.$el.focus();
      }
    });
  }
});

const getNodeExpectedCount = (memberName) => {
  if (!props.searchResponse?.results) return 0;
  const member = props.searchResponse.results.find((r) => r.memberName === memberName);
  if (!member) return 0;
  return member.count || 0;
};

const initializeNodeProgress = () => {
  nodeProgress.value = targetMemberNames.value.map((memberName) => ({
    memberName,
    status: "pending",
    progress: 0,
    expectedCount: getNodeExpectedCount(memberName),
    processedCount: 0,
  }));
};

const calculateExpectedTotal = () => {
  // Use explicit expectedTotal prop if provided (for By Flow / By Message tabs)
  if (props.expectedTotal !== null) {
    return props.expectedTotal;
  }
  return nodeProgress.value.reduce((sum, node) => sum + node.expectedCount, 0);
};

const BATCH_SIZE = 200;
const MAX_CONCURRENT_MEMBERS = 5;

const executeAction = async () => {
  if (requiresReason.value && !reason.value.trim()) {
    reasonInvalid.value = true;
    return;
  }

  close();
  displayProgressDialog.value = true;
  isProcessing.value = true;
  stopped.value = false;
  totalProcessedCount.value = 0;
  totalSuccessCount.value = 0;
  totalFailureCount.value = 0;

  initializeNodeProgress();
  expectedTotalCount.value = calculateExpectedTotal();

  // Process a single member with batching until complete
  const processMember = async (node) => {
    node.status = "processing";
    node.processedCount = 0;

    // When multiple messages are selected, process each message separately
    const messagesToProcess = props.messages?.length > 0 ? props.messages : [null];

    for (const message of messagesToProcess) {
      if (stopped.value) break;

      // Build filter with specific errorCause if processing by message
      const filterForMessage = message
        ? { ...props.filter, errorCause: message }
        : props.filter;

      let batchProcessed = BATCH_SIZE;
      while (!stopped.value && batchProcessed === BATCH_SIZE) {
        const result = await executeActionOnMember(
          effectiveAction.value,
          node.memberName,
          filterForMessage,
          reason.value || undefined,
          BATCH_SIZE,
          props.flows || undefined
        );

        if (result && result.status === "CONNECTED") {
          const successCount = result.successCount || 0;
          const failureCount = result.failureCount || 0;
          batchProcessed = successCount + failureCount;
          node.processedCount += batchProcessed;
          totalProcessedCount.value += batchProcessed;
          totalSuccessCount.value += successCount;
          totalFailureCount.value += failureCount;

          // Update node progress based on expected count
          if (node.expectedCount > 0) {
            node.progress = Math.min(100, Math.round((node.processedCount / node.expectedCount) * 100));
          } else {
            node.progress = batchProcessed > 0 ? 50 : 100;
          }
        } else {
          // Failed or unreachable
          node.status = "failed";
          return;
        }
      }
    }

    // Mark as completed if we finished normally (not stopped, not failed)
    if (node.status !== "failed") {
      node.progress = 100;
      node.status = "completed";
    }
  };

  // Process members in parallel, up to MAX_CONCURRENT_MEMBERS at a time
  const pendingNodes = [...nodeProgress.value];
  const activePromises = new Map();

  while ((pendingNodes.length > 0 || activePromises.size > 0) && !stopped.value) {
    // Start new members up to the concurrency limit
    while (pendingNodes.length > 0 && activePromises.size < MAX_CONCURRENT_MEMBERS && !stopped.value) {
      const node = pendingNodes.shift();
      const promise = processMember(node).then(() => {
        activePromises.delete(node.memberName);
      });
      activePromises.set(node.memberName, promise);
    }

    // Wait for at least one to complete before continuing
    if (activePromises.size > 0) {
      await Promise.race(activePromises.values());
    }
  }

  // Wait for any remaining active operations to complete
  if (activePromises.size > 0) {
    await Promise.all(activePromises.values());
  }

  isProcessing.value = false;
};

const stopProcessing = () => {
  stopped.value = true;
};

const closeProgress = () => {
  displayProgressDialog.value = false;

  // Show toast with results summary
  const completedNodes = nodeProgress.value.filter((n) => n.status === "completed").length;
  const failedNodes = nodeProgress.value.filter((n) => n.status === "failed").length;
  const totalNodes = nodeProgress.value.length;

  if (totalSuccessCount.value > 0 || totalFailureCount.value > 0) {
    const actionPastTense = {
      acknowledge: "acknowledged",
      resume: "resumed",
      replay: "replayed",
      replayAndAcknowledge: "replayed and acknowledged",
    }[effectiveAction.value] || "processed";

    if (totalFailureCount.value === 0 && failedNodes === 0) {
      // All success
      toast.add({
        severity: "success",
        summary: `${actionLabel.value} Complete`,
        detail: `Successfully ${actionPastTense} ${totalSuccessCount.value.toLocaleString()} DeltaFile(s) across ${completedNodes} member(s).`,
        life: 5000,
      });
    } else if (totalSuccessCount.value === 0 && completedNodes === 0) {
      // All failed
      toast.add({
        severity: "error",
        summary: `${actionLabel.value} Failed`,
        detail: `Failed to ${effectiveAction.value} DeltaFiles. ${failedNodes} member(s) unreachable.`,
        life: 8000,
      });
    } else {
      // Partial success
      let detail = `${actionPastTense.charAt(0).toUpperCase() + actionPastTense.slice(1)} ${totalSuccessCount.value.toLocaleString()} DeltaFile(s)`;
      if (totalFailureCount.value > 0) {
        detail += `, ${totalFailureCount.value.toLocaleString()} failed`;
      }
      if (failedNodes > 0) {
        detail += `. ${failedNodes}/${totalNodes} member(s) had errors.`;
      } else {
        detail += ` across ${completedNodes} member(s).`;
      }
      toast.add({
        severity: "warn",
        summary: `${actionLabel.value} Completed with Issues`,
        detail,
        life: 8000,
      });
    }
  } else if (stopped.value) {
    toast.add({
      severity: "info",
      summary: `${actionLabel.value} Stopped`,
      detail: `Operation was stopped by user after processing ${totalProcessedCount.value.toLocaleString()} DeltaFile(s).`,
      life: 5000,
    });
  }

  emit("completed");
  emit("update:visible", false);
};

const close = () => {
  displayDialog.value = false;
  emit("update:visible", false);
};
</script>

<style scoped>
.filter-list {
  margin: 0;
  padding-left: 1.5rem;
}

.filter-list li {
  margin-bottom: 0.25rem;
}

.node-progress-list {
  max-height: 300px;
  overflow-y: auto;
}

.node-progress-row {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.25rem 0;
}

.node-name {
  flex: 0 0 auto;
  min-width: 120px;
  font-size: 0.875rem;
}

.node-progress-bar {
  flex: 1;
  height: 0.5rem;
}

:deep(.node-progress-bar .p-progressbar-value) {
  height: 0.5rem;
}

:deep(.node-progress-bar .p-progressbar-label) {
  display: none;
}

:deep(.progress-success .p-progressbar-value) {
  background: var(--green-500);
}

:deep(.progress-danger .p-progressbar-value) {
  background: var(--red-500);
}

:deep(.progress-processing .p-progressbar-value) {
  background: var(--primary-color);
}

:deep(.progress-pending .p-progressbar-value) {
  background: var(--surface-400);
}
</style>
