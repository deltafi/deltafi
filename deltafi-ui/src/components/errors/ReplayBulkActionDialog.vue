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
<!-- ABOUTME: Dialog for bulk replay operations by flow or message. -->
<!-- ABOUTME: Supports batch processing with progress tracking and optional acknowledgement. -->

<template>
  <div class="replay-dialog">
    <Dialog v-model:visible="confirmDialogVisible" class="confirm-dialog" header="Confirm" :modal="true" :style="{ width: '35vw' }">
      <template #header>
        <strong>Replay</strong>
      </template>
      <div v-if="!displayBatchingDialog" class="metadata-body">
        <p class="mb-3">Replay {{ pluralized }}?</p>
        <div class="p-fluid">
          <div class="flex align-items-center mb-4">
            <Checkbox v-model="acknowledgeOnReplay" input-id="acknowledgeOnReplay" :binary="true" />
            <label for="acknowledgeOnReplay" class="ml-2 mb-0" style="line-height: 1;">Acknowledge error on replay</label>
          </div>
          <div class="field mt-2">
            <label for="reason">Acknowledge Reason</label>
            <InputText id="reason" v-model="reason" type="text" :disabled="!acknowledgeOnReplay" :class="{ 'p-invalid': reasonInvalid }" />
          </div>
          <small v-if="reasonInvalid" class="p-error">Reason is required when acknowledging</small>
        </div>
      </div>
      <div v-else-if="displayBatchingDialog">
        <div>
          <p v-if="!stopped">Replay in progress.<span v-if="expectedTotalCount > 1"> If you navigate away, the operation will stop after the current batch.</span></p>
          <p v-else>{{ expectedTotalCount > 1 ? 'Completing current batch...' : 'Completing...' }}</p>
          <ProgressBar :value="batchCompleteValue" />
          <p class="mt-2 text-center">{{ totalProcessedCount.toLocaleString() }} / {{ expectedTotalCount.toLocaleString() }} DeltaFiles</p>
        </div>
      </div>
      <template #footer>
        <template v-if="!displayBatchingDialog">
          <Button label="Modify Metadata" icon="fas fa-database fa-fw" class="p-button-secondary p-button-outlined" @click="showMetadataDialog" />
          <Button label="Cancel" icon="pi pi-times" class="p-button-secondary p-button-outlined" @click="closeConfirmDialog" />
          <Button label="Replay" icon="fas fa-redo" @click="replaySubmit" />
        </template>
        <template v-else>
          <Button label="Stop" icon="pi pi-stop" class="p-button-danger" :disabled="stopped" @click="stopBatching" />
        </template>
      </template>
    </Dialog>
  </div>
  <DialogTemplate ref="modifyBulkActionMetadataDialog" component-name="errors/ReplayBulkActionUpdateMetadataDialog" header="Modify Metadata" dialog-width="30vw" model-position="center" :flow-info="props.flowInfo" @submit-with-metadata="handleMetadataSubmit" @refresh-and-close="closeMetadataDialogOnly" />
</template>

<script setup>
import DialogTemplate from "@/components/DialogTemplate.vue";
import ProgressBar from "primevue/progressbar";
import useReplayErrors from "@/composables/useReplayErrors";
import useReplayAndAcknowledge from "@/composables/useReplayAndAcknowledge";
import useNotifications from "@/composables/useNotifications";
import useUtilFunctions from "@/composables/useUtilFunctions";
import { ref } from "vue";

import _ from "lodash";

import Button from "primevue/button";
import Checkbox from "primevue/checkbox";
import Dialog from "primevue/dialog";
import InputText from "primevue/inputtext";

const emit = defineEmits(["refreshPage"]);
const { pluralize } = useUtilFunctions();
const displayBatchingDialog = ref(false);
const batchCompleteValue = ref(0);
const totalProcessedCount = ref(0);
const expectedTotalCount = ref(0);
const stopped = ref(false);
const notify = useNotifications();
const maxSuccessDisplay = 10;
const props = defineProps({
  flowInfo: {
    type: Array,
    required: true,
  },
  bundleRequestType: {
    type: String,
    required: false,
    default: null,
  },
});

const { replayErrorsByFlow, replayErrorsByMessage } = useReplayErrors();
const { replayAndAcknowledgeErrorsByFlow, replayAndAcknowledgeErrorsByMessage } = useReplayAndAcknowledge();

const modifyBulkActionMetadataDialog = ref(null);
const confirmDialogVisible = ref(false);
const pluralized = ref();
const acknowledgeOnReplay = ref(true);
const reason = ref("");
const reasonInvalid = ref(false);

const validateReason = () => {
  if (acknowledgeOnReplay.value && (!reason.value || reason.value.trim() === "")) {
    reasonInvalid.value = true;
    return false;
  }
  reasonInvalid.value = false;
  return true;
};

const showMetadataDialog = async () => {
  if (!validateReason()) return;
  confirmDialogVisible.value = false;
  modifyBulkActionMetadataDialog.value.showDialog();
};

const showConfirmDialog = async () => {
  const totalCount = _.sumBy(props.flowInfo, "count") || 0;
  pluralized.value = pluralize(totalCount, "DeltaFile");
  reason.value = "";
  reasonInvalid.value = false;
  confirmDialogVisible.value = true;
};

const closeConfirmDialog = () => {
  confirmDialogVisible.value = false;
  refreshPage();
};

const stopBatching = () => {
  stopped.value = true;
};

const closeMetadataDialogOnly = () => {
  // Just close the metadata dialog without refreshing
};

const handleMetadataSubmit = async (metadata) => {
  // Show the progress dialog in this component
  confirmDialogVisible.value = true;
  await replayWithMetadata(metadata.removeMetadataKeys, metadata.replaceMetadata);
};

const replaySubmit = async () => {
  if (!validateReason()) return;
  await replayWithMetadata([], []);
};

const replayWithMetadata = async (removeMetadataKeys, replaceMetadata) => {
  if (_.isEqual(props.bundleRequestType, "replayByErrorCause")) {
    await requestReplayByErrorCause(removeMetadataKeys, replaceMetadata);
  } else if (_.isEqual(props.bundleRequestType, "replayByFlow")) {
    await requestReplayByFlow(removeMetadataKeys, replaceMetadata);
  }
  closeConfirmDialog();
};

const requestReplayByErrorCause = async (removeMetadataKeys, replaceMetadata) => {
  const batchSize = 200;
  let totalProcessed = 0;
  const expectedTotal = _.sumBy(props.flowInfo, "count") || 0;
  const newDids = [];
  const shouldAcknowledge = acknowledgeOnReplay.value;

  try {
    displayBatchingDialog.value = true;
    batchCompleteValue.value = 0;
    totalProcessedCount.value = 0;
    expectedTotalCount.value = expectedTotal;
    stopped.value = false;

    for (const item of props.flowInfo) {
      while (!stopped.value && totalProcessed < expectedTotal) {
        let response;
        let results;
        if (shouldAcknowledge) {
          response = await replayAndAcknowledgeErrorsByMessage(item.errorCause, reason.value, removeMetadataKeys, replaceMetadata, batchSize);
          results = response?.value?.data?.replayAndAcknowledgeErrorsByMessage || [];
        } else {
          response = await replayErrorsByMessage(item.errorCause, removeMetadataKeys, replaceMetadata, batchSize);
          results = response?.value?.data?.replayErrorsByMessage || [];
        }

        if (results.length === 0) break;

        totalProcessed += results.length;
        totalProcessedCount.value = totalProcessed;
        batchCompleteValue.value = Math.round((totalProcessed / expectedTotal) * 100);

        for (const result of results) {
          if (result.success) {
            newDids.push(shouldAcknowledge ? result.replayDid : result.did);
          } else {
            notify.error(`Replay failed for ${result.did}`, result.error);
          }
        }
      }
      if (stopped.value) break;
    }

    if (newDids.length > 0) {
      const pluralizedMsg = pluralize(newDids.length, "DeltaFile");
      const links = newDids.slice(0, maxSuccessDisplay).map((did) => `<a href="/deltafile/viewer/${did}" class="monospace">${did}</a>`);
      if (newDids.length > maxSuccessDisplay) links.push("...");
      const successMessage = shouldAcknowledge ? `Replay and acknowledge completed for ${pluralizedMsg}` : `Replay request sent successfully for ${pluralizedMsg}`;
      notify.success(successMessage, links.join(", "));
    }
  } finally {
    displayBatchingDialog.value = false;
    batchCompleteValue.value = 0;
  }
};

const requestReplayByFlow = async (removeMetadataKeys, replaceMetadata) => {
  const batchSize = 200;
  let totalProcessed = 0;
  const expectedTotal = _.sumBy(props.flowInfo, "count") || 0;
  const newDids = [];
  const shouldAcknowledge = acknowledgeOnReplay.value;

  try {
    displayBatchingDialog.value = true;
    batchCompleteValue.value = 0;
    totalProcessedCount.value = 0;
    expectedTotalCount.value = expectedTotal;
    stopped.value = false;

    for (const flow of props.flowInfo) {
      let flowProcessed = 0;
      const flowExpected = flow.count || 0;

      while (!stopped.value && flowProcessed < flowExpected && totalProcessed < expectedTotal) {
        let response;
        let results;
        if (shouldAcknowledge) {
          response = await replayAndAcknowledgeErrorsByFlow(flow.flowType, flow.flowName, reason.value, removeMetadataKeys, replaceMetadata, batchSize);
          results = response?.value?.data?.replayAndAcknowledgeErrorsByFlow || [];
        } else {
          response = await replayErrorsByFlow(flow.flowType, flow.flowName, removeMetadataKeys, replaceMetadata, batchSize);
          results = response?.value?.data?.replayErrorsByFlow || [];
        }

        if (results.length === 0) break;

        flowProcessed += results.length;
        totalProcessed += results.length;
        totalProcessedCount.value = totalProcessed;
        batchCompleteValue.value = Math.round((totalProcessed / expectedTotal) * 100);

        for (const result of results) {
          if (result.success) {
            newDids.push(shouldAcknowledge ? result.replayDid : result.did);
          } else {
            notify.error(`Replay failed for ${result.did} in ${flow.flowName}`, result.error);
          }
        }
      }
      if (stopped.value) break;
    }

    if (newDids.length > 0) {
      const pluralizedMsg = pluralize(newDids.length, "DeltaFile");
      const links = newDids.slice(0, maxSuccessDisplay).map((did) => `<a href="/deltafile/viewer/${did}" class="monospace">${did}</a>`);
      if (newDids.length > maxSuccessDisplay) links.push("...");
      const successMessage = shouldAcknowledge ? `Replay and acknowledge completed for ${pluralizedMsg}` : `Replay request sent successfully for ${pluralizedMsg}`;
      notify.success(successMessage, links.join(", "));
    }
  } finally {
    displayBatchingDialog.value = false;
    batchCompleteValue.value = 0;
  }
};

const refreshPage = () => {
  emit("refreshPage");
};

defineExpose({
  showConfirmDialog,
});
</script>

<style />
