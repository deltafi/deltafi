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
  <div class="replay-dialog">
    <Dialog v-model:visible="confirmDialogVisible" class="confirm-dialog" header="Confirm" :modal="true">
      <template #header>
        <strong>Replay</strong>
      </template>
      <div v-if="!displayBatchingDialog" class="metadata-body">Replay {{ pluralized }}?</div>
      <div v-else-if="displayBatchingDialog">
        <div>
          <p>Replay in progress. Please do not refresh the page!</p>
          <ProgressBar :value="batchCompleteValue" />
        </div>
      </div>
      <template #footer>
        <Button icon="fas fa-database fa-fw" label="Modify Metadata" class="p-button-secondary p-button-outlined" @click="showMetadataDialog" />
        <Button label="Cancel" icon="pi pi-times" class="p-button-secondary p-button-outlined" @click="closeConfirmDialog" />
        <Button label="Replay" icon="fas fa-play" @click="replayClean" />
      </template>
    </Dialog>
  </div>
  <DialogTemplate ref="modifyMetadataDialog" component-name="ReplayUpdateMetadataDialog" header="Modify Metadata" dialog-width="30vw" :did="props.did" @refresh-page="$emit('refreshPage')" />
</template>

<script setup>
import DialogTemplate from "@/components/DialogTemplate.vue";
import ProgressBar from "@/components/deprecatedPrimeVue/ProgressBar.vue";
import useNotifications from "@/composables/useNotifications";
import useReplay from "@/composables/useReplay";
import useUtilFunctions from "@/composables/useUtilFunctions";
import { ref } from "vue";

import _ from "lodash";

import Button from "primevue/button";
import Dialog from "primevue/dialog";

const emit = defineEmits(["refreshPage"]);
const { pluralize } = useUtilFunctions();
const maxSuccessDisplay = 10;
const displayBatchingDialog = ref(false);
const notify = useNotifications();
const batchCompleteValue = ref(0);
const props = defineProps({
  did: {
    type: Array,
    required: true,
  },
});

const { replay } = useReplay();
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
};

const replayClean = async () => {
  await requestReplay();
  closeConfirmDialog();
};

const requestReplay = async () => {
  let response;
  const batchedDids = getBatchDids(props.did);
  let success = false;
  let completedBatches = 0;
  try {
    displayBatchingDialog.value = true;
    batchCompleteValue.value = 0;
    const newDids = new Array();
    for (const dids of batchedDids) {
      response = await replay(dids, [], []);
      if (response.value.data !== undefined && response.value.data !== null) {
        const successReplayBatch = new Array();
        for (const replayStatus of response.value.data.replay) {
          if (replayStatus.success) {
            successReplayBatch.push(replayStatus);
            newDids.push(replayStatus.did);
          } else {
            notify.error(`Replay request failed for ${replayStatus.did}`, replayStatus.error);
          }
        }
        if (successReplayBatch.length > 0) {
          success = true;
        }
      }
      completedBatches += dids.length;
      batchCompleteValue.value = Math.round((completedBatches / props.did.length) * 100);
    }
    displayBatchingDialog.value = false;
    batchCompleteValue.value = 0;
    if (success) {
      const pluralized = pluralize(newDids.length, "DeltaFile");
      const links = newDids.slice(0, maxSuccessDisplay).map((did) => `<a href="/deltafile/viewer/${did}" class="monospace">${did}</a>`);
      notify.success(`Replay request sent successfully for ${pluralized}`, links.join(", "));
      emit("refreshPage");
    }
  } catch (error) {
    displayBatchingDialog.value = false;
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
