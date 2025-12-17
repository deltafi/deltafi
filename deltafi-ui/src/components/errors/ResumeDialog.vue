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
          <p v-if="!stopped">Resume in progress.<span v-if="props.did.length > 1"> If you navigate away, the operation will stop after the current batch.</span></p>
          <p v-else>{{ props.did.length > 1 ? 'Completing current batch...' : 'Completing...' }}</p>
          <ProgressBar :value="batchCompleteValue" />
          <p class="mt-2 text-center">{{ totalProcessedCount.toLocaleString() }} / {{ expectedTotalCount.toLocaleString() }} DeltaFiles</p>
        </div>
      </div>
      <template #footer>
        <template v-if="!displayBatchingDialog">
          <Button label="Modify Metadata" icon="fas fa-database fa-fw" class="p-button-secondary p-button-outlined" @click="showMetadataDialog" />
          <Button label="Cancel" icon="pi pi-times" class="p-button-secondary p-button-outlined" @click="closeConfirmDialog" />
          <Button label="Resume" icon="fas fa-play" @click="resumeClean" />
        </template>
        <template v-else>
          <Button label="Stop" icon="pi pi-stop" class="p-button-danger" :disabled="stopped" @click="stopBatching" />
        </template>
      </template>
    </Dialog>
  </div>
  <DialogTemplate ref="modifyMetadataDialog" component-name="errors/ResumeUpdateMetadataDialog" header="Modify Metadata" dialog-width="30vw" model-position="center" :did="props.did" error-request-type="Resume" @submit-with-metadata="handleMetadataSubmit" @refresh-and-close="closeMetadataDialogOnly" />
</template>

<script setup>
import DialogTemplate from "@/components/DialogTemplate.vue";
import ProgressBar from "@/components/deprecatedPrimeVue/ProgressBar.vue";
import useErrorResume from "@/composables/useErrorResume";
import useNotifications from "@/composables/useNotifications";
import useUtilFunctions from "@/composables/useUtilFunctions";
import { ref } from "vue";

import _ from "lodash";

import Button from "primevue/button";
import Dialog from "primevue/dialog";

const emit = defineEmits(["refreshPage"]);
const { pluralize } = useUtilFunctions();
const maxSuccessDisplay = 10;
const displayBatchingDialog = ref(false);
const batchCompleteValue = ref(0);
const totalProcessedCount = ref(0);
const expectedTotalCount = ref(0);
const stopped = ref(false);
const notify = useNotifications();
const props = defineProps({
  did: {
    type: Array,
    required: true,
  },
  bundleRequestType: {
    type: String,
    required: false,
    default: null,
  },
});

const { resume } = useErrorResume();

const modifyMetadataDialog = ref(null);
const confirmDialogVisible = ref(false);
const pluralized = ref();
const batchSize = 500;

const showMetadataDialog = async () => {
  confirmDialogVisible.value = false;
  modifyMetadataDialog.value.showDialog();
};

const showConfirmDialog = async () => {
  pluralized.value = pluralize(props.did.length, "DeltaFile");
  confirmDialogVisible.value = true;
};

const closeConfirmDialog = () => {
  confirmDialogVisible.value = false;
  emit("refreshPage");
};

const closeMetadataDialogOnly = () => {
  // Just close the metadata dialog without refreshing
};

const stopBatching = () => {
  stopped.value = true;
};

const resumeClean = async () => {
  await requestResume([]);
};

const handleMetadataSubmit = async (resumeMetadata) => {
  // Show the progress dialog in this component
  confirmDialogVisible.value = true;
  await requestResume(resumeMetadata);
};

const requestResume = async (resumeMetadata) => {
  const batchedDids = getBatchDids(props.did);
  let totalProcessed = 0;
  const expectedTotal = props.did.length;

  try {
    displayBatchingDialog.value = true;
    batchCompleteValue.value = 0;
    totalProcessedCount.value = 0;
    expectedTotalCount.value = expectedTotal;
    stopped.value = false;

    for (const dids of batchedDids) {
      if (stopped.value) break;

      const response = await resume(dids, resumeMetadata);
      const results = response?.data?.resume || [];

      if (results.length === 0) continue;

      const successResults = results.filter((r) => r.success);
      const failures = results.filter((r) => !r.success);

      totalProcessed += successResults.length;
      totalProcessedCount.value = totalProcessed;
      batchCompleteValue.value = Math.round((totalProcessed / expectedTotal) * 100);

      if (failures.length > 0) {
        for (const failure of failures) {
          notify.error(`Resume request failed for ${failure.did}`, failure.error);
        }
      }
    }

    if (totalProcessed > 0) {
      const links = props.did.slice(0, maxSuccessDisplay).map((did) => `<a href="/deltafile/viewer/${did}" class="monospace">${did}</a>`);
      if (props.did.length > maxSuccessDisplay) links.push("...");
      const pluralized = pluralize(totalProcessed, "DeltaFile");
      notify.success(`Resume request sent successfully for ${pluralized}`, links.join(", "));
    }
  } finally {
    displayBatchingDialog.value = false;
    batchCompleteValue.value = 0;
    closeConfirmDialog();
  }
};

const getBatchDids = (allDids) => {
  return _.chunk(allDids, batchSize);
};

defineExpose({
  showConfirmDialog,
});
</script>

<style />
