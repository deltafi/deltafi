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
  <div class="resume-dialog">
    <Dialog v-model:visible="confirmDialogVisible" class="confirm-dialog" header="Confirm" :modal="true">
      <template #header>
        <strong>Resume</strong>
      </template>
      <div v-if="!displayBatchingDialog" class="metadata-body">Resume {{ pluralized }}?</div>
      <div v-else-if="displayBatchingDialog">
        <div>
          <p>Resume in progress. Please do not refresh the page!</p>
          <ProgressBar :value="batchCompleteValue" />
        </div>
      </div>
      <template #footer>
        <Button label="Modify Metadata" icon="fas fa-database fa-fw" class="p-button-secondary p-button-outlined" @click="showMetadataDialog" />
        <Button label="Cancel" icon="pi pi-times" class="p-button-secondary p-button-outlined" @click="closeConfirmDialog" />
        <Button label="Resume" icon="fas fa-play" @click="resumeSubmit" />
      </template>
    </Dialog>
  </div>
  <DialogTemplate ref="modifyBulkActionMetadataDialog" component-name="errors/ResumeBulkActionUpdateMetadataDialog" header="Modify Metadata" dialog-width="30vw" :flow-info="props.flowInfo" :bundleRequestType="props.bundleRequestType" :acknowledged="props.acknowledged" @refresh-page="refreshPage()" />
</template>

<script setup>
import DialogTemplate from "@/components/DialogTemplate.vue";
import ProgressBar from "@/components/deprecatedPrimeVue/ProgressBar.vue";
import useErrorResume from "@/composables/useErrorResume";
import useNotifications from "@/composables/useNotifications";
import useUtilFunctions from "@/composables/useUtilFunctions";
import { computed, ref } from "vue";

import _ from "lodash";

import Button from "primevue/button";
import Dialog from "primevue/dialog";

const emit = defineEmits(["refreshPage"]);
const { pluralize } = useUtilFunctions();
const displayBatchingDialog = ref(false);
const notify = useNotifications();
const batchCompleteValue = ref(0);
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
  acknowledged: {
    type: [Boolean, null],
    required: true,
  },
});

const { resumeByErrorCause, resumeByFlow, resumeMatching } = useErrorResume();

const modifyBulkActionMetadataDialog = ref(null);
const confirmDialogVisible = ref(false);
const pluralized = ref();

const showMetadataDialog = async () => {
  confirmDialogVisible.value = false;
  modifyBulkActionMetadataDialog.value.showDialog();
};

const showConfirmDialog = async () => {
  pluralized.value = pluralize(_.flatten([...new Set(_.map(props.flowInfo, "dids"))]).length, "DeltaFile");
  confirmDialogVisible.value = true;
};

const closeConfirmDialog = () => {
  confirmDialogVisible.value = false;
  refreshPage();
};

const resumeSubmit = async () => {
  if (_.isEqual(props.bundleRequestType, "resumeByErrorCause")) {
    await requestResumeByErrorCause();
  } else if (_.isEqual(props.bundleRequestType, "resumeByFlow")) {
    await requestResumeByFlow();
  }
  closeConfirmDialog();
};

const mergeGroupedMessages = computed(() => {
  const mergedMap = new Map();
  const result = [];

  for (const item of props.flowInfo) {
    const { messageGrouping, message, flowType, dids } = item;

    if (messageGrouping === "ALL") {
      const key = `${messageGrouping}|${message}|${flowType}`;
      if (!mergedMap.has(key)) {
        mergedMap.set(key, {
          messageGrouping,
          flowType,
          message,
          dids: [...dids], // clone to avoid mutating original
        });
      } else {
        // Merge dids
        mergedMap.get(key).dids.push(...dids);
      }
    } else {
      // Leave SINGLE items as is
      result.push(item);
    }
  }

  // Add merged ALL items to result
  result.push(...mergedMap.values());

  return result;
});

const requestResumeByErrorCause = async () => {
  let response;
  let completedBatches = 0;

  try {
    displayBatchingDialog.value = true;
    batchCompleteValue.value = 0;
    for (const flow of mergeGroupedMessages.value) {
      if (flow.messageGrouping === "ALL") {
        response = await resumeByErrorCause(flow.message, [], includeAcknowledged.value);
        response = response.data.resumeByErrorCause;
      } else {
        const filterParams = {
          dids: flow.dids,
          errorCause: flow.message,
          [`${_.camelCase(flow.flowType)}s`]: flow.flowName,
        };
        response = await resumeMatching(JSON.parse(JSON.stringify(filterParams)), []);
        response = response.data.resumeMatching;
      }
      if (response !== undefined && response !== null) {
        for (const resumeStatus of response) {
          if (resumeStatus.success) {
            notify.success(`Resume request sent successfully`, `Successfully Resumed ${flow.flowName || ""}`);
          } else {
            notify.error(`Resume request failed for ${flow.flowName || ""}`, resumeStatus.error);
          }
        }
      }
      completedBatches++;
      batchCompleteValue.value = Math.round((completedBatches / props.flowInfo.length) * 100);
    }
    displayBatchingDialog.value = false;
    batchCompleteValue.value = 0;
  } catch (error) {
    displayBatchingDialog.value = false;
  }
};

const requestResumeByFlow = async () => {
  let response;
  let completedBatches = 0;
  try {
    displayBatchingDialog.value = true;
    batchCompleteValue.value = 0;
    for (const flow of props.flowInfo) {
      response = await resumeByFlow(flow.flowType, flow.flowName, [], includeAcknowledged.value);
      if (response.data !== undefined && response.data !== null) {
        for (const resumeStatus of response.data.resumeByFlow) {
          if (resumeStatus.success) {
            notify.success(`Resume request sent successfully`, `Successfully Resumed ${flow.flowName}`);
          } else {
            notify.error(`Resume request failed for ${flow.flowName}`, resumeStatus.error);
          }
        }
      }
      completedBatches++;
      batchCompleteValue.value = Math.round((completedBatches / props.flowInfo.length) * 100);
    }
    displayBatchingDialog.value = false;
    batchCompleteValue.value = 0;
  } catch (error) {
    displayBatchingDialog.value = false;
  }
};

const includeAcknowledged = computed(() => {
  if (props.acknowledged === true || _.isNull(props.acknowledged)) {
    return true;
  }
  return false;
});

const refreshPage = () => {
  emit("refreshPage");
};

defineExpose({
  showConfirmDialog,
});
</script>

<style />
