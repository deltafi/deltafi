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
    <Dialog v-model:visible="confirmDialogVisible" class="confirm-dialog" header="Confirm" :modal="true" :style="{ width: '30vw' }">
      <template #header>
        <strong>Resume</strong>
      </template>
      <div v-if="!displayBatchingDialog" class="metadata-body">Resume {{ pluralized }}?</div>
      <div v-else-if="displayBatchingDialog">
        <div>
          <p v-if="!stopped">Resume in progress.<span v-if="expectedTotalCount > 1"> If you navigate away, the operation will stop after the current batch.</span></p>
          <p v-else>{{ expectedTotalCount > 1 ? 'Completing current batch...' : 'Completing...' }}</p>
          <ProgressBar :value="batchCompleteValue" />
          <p class="mt-2 text-center">{{ totalProcessedCount.toLocaleString() }} / {{ expectedTotalCount.toLocaleString() }} DeltaFiles</p>
        </div>
      </div>
      <template #footer>
        <template v-if="!displayBatchingDialog">
          <Button label="Modify Metadata" icon="fas fa-database fa-fw" class="p-button-secondary p-button-outlined" @click="showMetadataDialog" />
          <Button label="Cancel" icon="pi pi-times" class="p-button-secondary p-button-outlined" @click="closeConfirmDialog" />
          <Button label="Resume" icon="fas fa-play" @click="resumeSubmit" />
        </template>
        <template v-else>
          <Button label="Stop" icon="pi pi-stop" class="p-button-danger" :disabled="stopped" @click="stopBatching" />
        </template>
      </template>
    </Dialog>
  </div>
  <DialogTemplate ref="modifyBulkActionMetadataDialog" component-name="errors/ResumeBulkActionUpdateMetadataDialog" header="Modify Metadata" dialog-width="30vw" model-position="center" :flow-info="props.flowInfo" :bundleRequestType="props.bundleRequestType" :acknowledged="props.acknowledged" @submit-with-metadata="handleMetadataSubmit" @refresh-and-close="closeMetadataDialogOnly" />
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
const batchCompleteValue = ref(0);
const totalProcessedCount = ref(0);
const expectedTotalCount = ref(0);
const stopped = ref(false);
const notify = useNotifications();
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

const { resumeByErrorCause, resumeByFlow } = useErrorResume();

const modifyBulkActionMetadataDialog = ref(null);
const confirmDialogVisible = ref(false);
const pluralized = ref();

const showMetadataDialog = async () => {
  confirmDialogVisible.value = false;
  modifyBulkActionMetadataDialog.value.showDialog();
};

const showConfirmDialog = async () => {
  const totalCount = _.sumBy(props.flowInfo, "count") || 0;
  pluralized.value = pluralize(totalCount, "DeltaFile");
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

const handleMetadataSubmit = async (resumeMetadata) => {
  // Show the progress dialog in this component
  confirmDialogVisible.value = true;
  await resumeWithMetadata(resumeMetadata);
};

const resumeSubmit = async () => {
  await resumeWithMetadata([]);
};

const resumeWithMetadata = async (resumeMetadata) => {
  if (_.isEqual(props.bundleRequestType, "resumeByErrorCause")) {
    await requestResumeByErrorCause(resumeMetadata);
  } else if (_.isEqual(props.bundleRequestType, "resumeByFlow")) {
    await requestResumeByFlow(resumeMetadata);
  }
  closeConfirmDialog();
};

const requestResumeByErrorCause = async (resumeMetadata) => {
  const batchSize = 200;
  let totalProcessed = 0;
  const expectedTotal = _.sumBy(props.flowInfo, "count") || 0;

  try {
    displayBatchingDialog.value = true;
    batchCompleteValue.value = 0;
    totalProcessedCount.value = 0;
    expectedTotalCount.value = expectedTotal;
    stopped.value = false;

    for (const item of props.flowInfo) {
      while (!stopped.value && totalProcessed < expectedTotal) {
        const response = await resumeByErrorCause(item.errorCause, resumeMetadata, includeAcknowledged.value, batchSize);
        const results = response?.data?.resumeByErrorCause || [];

        if (results.length === 0) break;

        totalProcessed += results.length;
        totalProcessedCount.value = totalProcessed;
        batchCompleteValue.value = Math.round((totalProcessed / expectedTotal) * 100);

        const failures = results.filter((r) => !r.success);
        if (failures.length > 0) {
          notify.error(`Resume failed for ${failures.length} DeltaFiles`);
        }
      }
      if (stopped.value) break;
    }
    notify.success(`Resumed ${totalProcessed} DeltaFiles`);
  } finally {
    displayBatchingDialog.value = false;
    batchCompleteValue.value = 0;
  }
};

const requestResumeByFlow = async (resumeMetadata) => {
  const batchSize = 200;
  let totalProcessed = 0;
  const expectedTotal = _.sumBy(props.flowInfo, "count") || 0;

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
        const response = await resumeByFlow(flow.flowType, flow.flowName, resumeMetadata, includeAcknowledged.value, batchSize);
        const results = response?.data?.resumeByFlow || [];

        if (results.length === 0) break;

        flowProcessed += results.length;
        totalProcessed += results.length;
        totalProcessedCount.value = totalProcessed;
        batchCompleteValue.value = Math.round((totalProcessed / expectedTotal) * 100);

        const failures = results.filter((r) => !r.success);
        if (failures.length > 0) {
          notify.error(`Resume failed for ${failures.length} DeltaFiles in ${flow.flowName}`);
        }
      }
      if (stopped.value) break;
    }
    notify.success(`Resumed ${totalProcessed} DeltaFiles`);
  } finally {
    displayBatchingDialog.value = false;
    batchCompleteValue.value = 0;
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
