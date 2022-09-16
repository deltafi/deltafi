<!--
   DeltaFi - Data transformation and enrichment platform

   Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>

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
  <div class="metadataDialog">
    <Dialog v-model:visible="metadataDialogVisible" header="Metadata" :modal="true" :breakpoints="{ '960px': '75vw', '940px': '90vw' }" :style="{ width: '30vw' }">
      <template #header>
        <strong>Modify Metadata</strong>
      </template>
      <div v-if="modifiedMetadata !== 'undefined'" class="metadata-body">
        <Message v-if="hasDuplicateKeys" severity="error" :closable="false">No duplicate keys permitted</Message>
        <Message v-if="modifiedMetadata.length == 0" severity="info" :closable="false">No metadata to display</Message>
        <div v-for="field in modifiedMetadata" :key="field" class="row p-fluid mb-4">
          <div class="col-5">
            <InputText v-if="field.changed === 'new'" v-model="field.key" type="text" placeholder="Key" @change="onInputChange(field)" />
            <InputText v-else-if="field.changed === 'error'" v-model="field.key" type="text" placeholder="Key" class="p-invalid" @change="onInputChange(field)" />
            <InputText v-else v-model="field.key" type="text" placeholder="Key" disabled class="p-valid" />
          </div>
          <div class="col-5">
            <InputText v-if="field.changed === 'multiple'" v-model="field.values" type="text" placeholder="Multiple values not shown" @change="onInputChange(field)" />
            <InputText v-else v-model="field.values" type="text" placeholder="Value" @change="onInputChange(field)" />
          </div>
          <div class="col-2 text-right">
            <Button icon="pi pi-times" @click="removeMetadataField(field)" />
          </div>
        </div>
      </div>
      <template #footer>
        <Button label="Add Metadata Field" icon="pi pi-plus" class="p-button-secondary p-button-outlined" @click="addMetadataField" />
        <Button label="Cancel" icon="pi pi-times" class="p-button-secondary p-button-outlined" @click="closeMetadataDialog" />
        <Button v-if="resumeReplay === 'Resume'" label="Resume" icon="fas fa-play" :disabled="hasDuplicateKeys" @click="resumeReplayClick" />
        <Button v-else label="Replay" icon="fas fa-play" @click="resumeReplayClick" />
      </template>
    </Dialog>
    <Dialog v-model:visible="confirmDialogVisible" class="confirm-dialog" header="Confirm" :modal="true">
      <template #header>
        <strong>{{ resumeReplay }}</strong>
      </template>
      {{ resumeReplay }} {{ pluralized }}?
      <template #footer>
        <Button label="Modify Metadata" icon="fas fa-database fa-fw" class="p-button-secondary p-button-outlined" @click="showMetadataDialog" />
        <Button label="Cancel" icon="pi pi-times" class="p-button-secondary p-button-outlined" @click="closeConfirmDialog" />
        <Button v-if="resumeReplay === 'Resume'" label="Resume" icon="fas fa-play" @click="resumeReplayClean" />
        <Button v-else label="Replay" icon="fas fa-play" @click="resumeReplayClean" />
      </template>
    </Dialog>
  </div>
  <Dialog v-model:visible="displayBatchingDialog" :breakpoints="{ '960px': '75vw', '940px': '90vw' }" :style="{ width: '30vw' }" :modal="true" :closable="false" :close-on-escape="false" :draggable="false" header="Resuming">
    <div>
      <p>Resume in progress. Please do not refresh the page!</p>
      <ProgressBar :value="batchCompleteValue" />
    </div>
  </Dialog>
  <Dialog v-model:visible="displayMetadataBatchingDialog" :breakpoints="{ '960px': '75vw', '940px': '90vw' }" :style="{ width: '30vw' }" :modal="true" :closable="false" :close-on-escape="false" :draggable="false" header="Loading Metadata">
    <div>
      <p>Metadata loading in progress. Please do not refresh the page!</p>
      <ProgressBar :value="batchCompleteValue" />
    </div>
  </Dialog>
</template>

<script setup>
import { ref, defineProps, defineExpose, defineEmits, computed } from "vue";
import Dialog from "primevue/dialog";
import InputText from "primevue/inputtext";
import Button from "primevue/button";
import useMetadata from "@/composables/useMetadata";
import useErrorResume from "@/composables/useErrorResume";
import useReplay from "@/composables/useReplay";
import useNotifications from "@/composables/useNotifications";
import useUtilFunctions from "@/composables/useUtilFunctions";
import Message from "primevue/message";
import ProgressBar from "primevue/progressbar";
import _ from "lodash";

const emit = defineEmits(["update"]);
const { pluralize } = useUtilFunctions();
const invalidKey = ref(false);
const maxSuccessDisplay = 10;
const displayBatchingDialog = ref(false);
const displayMetadataBatchingDialog = ref(false);
const notify = useNotifications();
const batchCompleteValue = ref(0);
const props = defineProps({
  did: {
    type: Array,
    required: true,
  },
});

const { resume } = useErrorResume();
const { replay } = useReplay();
const { fetch: meta, data: batchMetadata } = useMetadata();

const resumeReplay = ref();
const modifiedMetadata = ref([]);
const removedMetadata = ref([]);
const metadataDialogVisible = ref(false);
const confirmDialogVisible = ref(false);
const pluralized = ref();
const batchSize = 500;
const allMetadata = ref([]);

const showMetadataDialog = async () => {
  confirmDialogVisible.value = false;
  await formatMetadata();
  metadataDialogVisible.value = true;
};

const getAllMeta = async () => {
  let batchedDids = props.did.length > batchSize ? getBatchDids(props.did) : props.did;
  // displayMetadataBatchingDialog.value = props.did.length > batchSize ? true : false;
  displayMetadataBatchingDialog.value = true;
  batchCompleteValue.value = 0;
  let completedBatches = 0;
  allMetadata.value = [];
  for (const dids of batchedDids) {
    await meta(dids);
    if (batchMetadata.value.length > 0) {
      let tmp = [...batchMetadata.value, ...allMetadata.value];
      allMetadata.value = tmp;
    }
    ++completedBatches;
    batchCompleteValue.value = Math.round((completedBatches / batchedDids.length) * 100);
  }

  allMetadata.value = await getUniqueMetadataKeys(allMetadata.value);
  displayMetadataBatchingDialog.value = false;
};

const getUniqueMetadataKeys = async (originalMetadata) => {
  let data = JSON.parse(JSON.stringify(originalMetadata));
  let current = [];
  for (let i = 0; i < data.length; i++) {
    current = data[i];
    for (let j = data.length - 1; j > i; j--) {
      if (current.key === data[j].key) {
        if (Array.isArray(current.value)) {
          current.values = current.value.concat([data[j].value]);
        } else {
          current.values = "Multiple";
        }
        data.splice(j, 1);
      }
    }
  }
  return data;
};

const showConfirmDialog = async (replayResume) => {
  resumeReplay.value = replayResume;
  modifiedMetadata.value = [];
  removedMetadata.value = [];
  pluralized.value = pluralize(props.did.length, "DeltaFile");
  confirmDialogVisible.value = true;
};

const closeMetadataDialog = () => {
  metadataDialogVisible.value = false;
};

const closeConfirmDialog = () => {
  confirmDialogVisible.value = false;
};

const addMetadataField = () => {
  modifiedMetadata.value.push({ key: "", values: "", changed: "new" });
};

const removeMetadataField = (field) => {
  if (field.changed !== "new") {
    removedMetadata.value.push(field.key);
  }
  let index = modifiedMetadata.value.indexOf(field);
  modifiedMetadata.value.splice(index, 1);
};

const resumeReplayClick = () => {
  requestResumeReplay();
  closeMetadataDialog();
};

const onInputChange = (field) => {
  let index = modifiedMetadata.value.indexOf(field);
  if (modifiedMetadata.value[index].changed === "new" || modifiedMetadata.value[index].changed === "error") {
    modifiedMetadata.value[index].changed = checkDuplicates(field.key);
  } else {
    modifiedMetadata.value[index].changed = "yes";
  }
  invalidKey.value = modifiedMetadata.value.map((object) => object.changed).indexOf("error") === -1 ? false : true;
};

const checkDuplicates = (key) => {
  const index = modifiedMetadata.value.map((object) => object.key).indexOf(key);
  const lastIndex = modifiedMetadata.value.map((object) => object.key).lastIndexOf(key);
  if (index !== lastIndex) {
    return "error";
  } else {
    return "new";
  }
};

const hasDuplicateKeys = computed(() => {
  let keys = modifiedMetadata.value.map((object) => object.key);
  return _.some(Object.values(_.countBy(keys)), (count) => count > 1);
});

const formatMetadata = async () => {
  await getAllMeta();
  if (allMetadata.value !== undefined) {
    modifiedMetadata.value = JSON.parse(JSON.stringify(allMetadata.value)).map((metadata) => {
      return {
        values: metadata.values.length > 1 ? "" : metadata.values,
        changed: metadata.values.length > 1 ? "multiple" : "no",
        key: metadata.key,
      };
    });
  }
};

const resumeReplayClean = () => {
  closeConfirmDialog();
  requestResumeReplay();
};

const requestResumeReplay = async () => {
  let response;
  try {
    if (resumeReplay.value === "Resume") {
      let batchedDids = props.did.length > batchSize ? getBatchDids(props.did) : props.did;
      let successBatch;
      displayBatchingDialog.value = props.did.length > batchSize ? true : false;
      let completedBatches = 0;
      batchCompleteValue.value = 0;
      for (const dids of batchedDids) {
        response = await resume(dids, removedMetadata.value, getModifiedMetadata());
        if (response.value.data !== undefined && response.value.data !== null) {
          let successResume = new Array();
          for (const resumeStatus of response.value.data.resume) {
            if (resumeStatus.success) {
              successResume.push(resumeStatus);
            } else {
              notify.error(`Resume request failed for ${resumeStatus.did}`, resumeStatus.error);
            }
          }
          if (successResume.length > 0) {
            let successfulDids = successResume.map((resumeStatus) => {
              return resumeStatus.did;
            });
            if (successfulDids.length > maxSuccessDisplay) {
              successfulDids = successfulDids.slice(0, maxSuccessDisplay);
              successfulDids.push("...");
            }
            let pluralized = pluralize(dids.length, "DeltaFile");
            notify.success(`Resume request sent successfully for ${pluralized}`, successfulDids.join(", "));
            successBatch = true;
          }
        }
        ++completedBatches;
        batchCompleteValue.value = Math.round((completedBatches / batchedDids.length) * 100);
      }
      displayBatchingDialog.value = false;
      batchCompleteValue.value = 0;
      if (successBatch) {
        emit("update");
      }
    } else {
      response = await replay(props.did, removedMetadata.value, getModifiedMetadata());
      if (response.value.data !== undefined && response.value.data !== null) {
        let successReplay = new Array();
        for (const replayStatus of response.value.data.replay) {
          if (replayStatus.success) {
            successReplay.push(replayStatus);
          } else {
            notify.error(`Replay request failed for ${replayStatus.did}`, replayStatus.error);
          }
        }
        if (successReplay.length > 0) {
          let successfulDids = successReplay.map((replayStatus) => {
            return replayStatus.did;
          });
          if (successfulDids.length > maxSuccessDisplay) {
            successfulDids = successfulDids.slice(0, maxSuccessDisplay);
            successfulDids.push("...");
          }
          let pluralized = pluralize(props.did.length, "DeltaFile");
          notify.success(`Replay request sent successfully for ${pluralized}`, successfulDids.join(", "));
          emit("update");
        }
      }
    }
  } catch (error) {
    notify.error("Resume request failed", error);
  }
};

const getModifiedMetadata = () => {
  if (modifiedMetadata.value !== null) {
    let filteredMetadata = modifiedMetadata.value.filter((metadata) => metadata.changed === "new" || metadata.changed === "yes");
    return filteredMetadata.map((metadata) => {
      return {
        key: metadata.key,
        value: metadata.values,
      };
    });
  } else {
    return [];
  }
};

const getBatchDids = (allDids) => {
  const res = [];
  for (let i = 0; i < allDids.length; i += batchSize) {
    const chunk = allDids.slice(i, i + batchSize);
    res.push(chunk);
  }
  return res;
};

defineExpose({
  showConfirmDialog,
});
</script>

<style lang="scss">
@import "@/styles/components/metadata-dialog.scss";
</style>
