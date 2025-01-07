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
  <div class="metadataDialog">
    <Dialog v-model:visible="metadataDialogVisible" header="Metadata" :modal="true" :breakpoints="{ '960px': '75vw', '940px': '90vw' }" :style="{ width: '60vw' }">
      <Message v-if="hasDuplicateKeys" severity="error" :closable="false">No duplicate keys permitted</Message>
      <template #header>
        <strong>Modify Metadata</strong>
      </template>
      <CollapsiblePanel v-for="actions in modifiedMetadata" :key="actions" :header="actions.flow + '.' + actions.action" class="mb-3">
        <div v-for="pairs in actions.keyVals" :key="pairs">
          <div v-if="pairs.changed !== 'deleted'" class="row p-fluid mb-4">
            <div class="col-5">
              <InputText v-if="pairs.changed === 'new'" v-model="pairs.key" type="text" placeholder="Key" @change="onCellEditComplete(pairs, actions.flow, actions.action)" />
              <InputText v-else v-model="pairs.key" type="text" placeholder="Key" disabled class="p-valid" />
            </div>
            <div class="col-5">
              <InputText v-if="pairs.changed === 'multiple'" v-model="pairs.values" type="text" placeholder="Multiple values not shown" @change="onCellEditComplete(pairs, actions.flow, actions.action)" />
              <InputText v-else v-model="pairs.values" type="text" placeholder="Value" @change="onCellEditComplete(pairs, actions.flow, actions.action)" />
            </div>
            <div class="col-2 text-right">
              <Button icon="pi pi-times" @click="removeMetadataField(pairs, actions.flow, actions.action)" />
            </div>
          </div>
        </div>
        <Button label="Add Metadata Field" icon="pi pi-plus" class="p-button-secondary p-button-outlined" @click="addMetadataField(actions.flow, actions.action)" />
      </CollapsiblePanel>
      <template #footer>
        <Button label="Cancel" icon="pi pi-times" class="p-button-secondary p-button-outlined" @click="closeMetadataDialog" />
        <Button label="Resume" icon="fas fa-play" :disabled="hasDuplicateKeys" @click="resumeClick" />
      </template>
    </Dialog>
    <Dialog v-model:visible="confirmDialogVisible" class="confirm-dialog" header="Confirm" :modal="true">
      <template #header>
        <strong>Resume</strong>
      </template>
      Resume {{ pluralized }}?
      <template #footer>
        <Button v-if="allMetadata.length > 0" label="Modify Metadata" icon="fas fa-database fa-fw" class="p-button-secondary p-button-outlined" @click="showMetadataDialog" />
        <span v-else v-tooltip.top="'No metadata to display'">
          <Button label="Modify Metadata" icon="fas fa-database fa-fw" class="p-button-secondary p-button-outlined" disabled @click="showMetadataDialog" />
        </span>
        <Button label="Cancel" icon="pi pi-times" class="p-button-secondary p-button-outlined" @click="closeConfirmDialog" />
        <Button label="Resume" icon="fas fa-play" @click="resumeClean" />
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
import ProgressBar from "@/components/deprecatedPrimeVue/ProgressBar";
import useErrorResume from "@/composables/useErrorResume";
import useMetadata from "@/composables/useMetadata";
import useNotifications from "@/composables/useNotifications";
import useUtilFunctions from "@/composables/useUtilFunctions";
import { computed, defineEmits, defineExpose, defineProps, ref } from "vue";
import _ from "lodash";
import Button from "primevue/button";
import Dialog from "primevue/dialog";
import InputText from "primevue/inputtext";
import Message from "primevue/message";
import CollapsiblePanel from "@/components/CollapsiblePanel.vue";

const emit = defineEmits(["update"]);
const { pluralize } = useUtilFunctions();
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
const { fetchAll: meta, data: batchMetadata } = useMetadata();

const modifiedMetadata = ref([]);
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
  let batchedDids = getBatchDids(props.did);
  displayMetadataBatchingDialog.value = props.did.length > batchSize;
  batchCompleteValue.value = 0;
  let completedBatches = 0;
  allMetadata.value = [];
  for (const dids of batchedDids) {
    await meta(dids);
    if (batchMetadata.value.length > 0) {
      let tmp = [...batchMetadata.value, ...allMetadata.value];
      allMetadata.value = tmp;
    }
    completedBatches += dids.length;
    batchCompleteValue.value = Math.round((completedBatches / props.did.length) * 100);
  }
  displayMetadataBatchingDialog.value = false;
};

const showConfirmDialog = async () => {
  modifiedMetadata.value = [];
  pluralized.value = pluralize(props.did.length, "DeltaFile");
  await getAllMeta();
  confirmDialogVisible.value = true;
};

const closeMetadataDialog = () => {
  metadataDialogVisible.value = false;
};

const closeConfirmDialog = () => {
  confirmDialogVisible.value = false;
};

const addMetadataField = (flowValue, actionValue) => {
  let index = modifiedMetadata.value.findIndex((action) => action.flow === flowValue && action.action === actionValue);
  modifiedMetadata.value[index].keyVals.push({
    values: "",
    changed: "new",
    key: "",
  });
};

const removeMetadataField = (keyVal, flow, action) => {
  let index = modifiedMetadata.value.findIndex((object) => object.flow === flow && object.action === action);
  let field = modifiedMetadata.value[index].keyVals.findIndex((object) => object.key === keyVal.key);
  if (field.changed !== "new" && field.changed !== "error") {
    modifiedMetadata.value[index].keyVals[field].changed = "deleted";
  } else {
    modifiedMetadata.value[index].keyVals.splice(field, 1);
  }
};

const resumeClick = () => {
  requestResume();
  closeMetadataDialog();
};

const onCellEditComplete = (keyVal, flow, action) => {
  let index = modifiedMetadata.value.findIndex((object) => object.flow === flow && object.action === action);
  let field = modifiedMetadata.value[index].keyVals.findIndex((object) => object.key === keyVal.key);
  if (keyVal.changed === "new" || keyVal.changed === "error") {
    modifiedMetadata.value[index].keyVals[field].changed = checkDuplicates(keyVal.key, index);
  } else {
    modifiedMetadata.value[index].keyVals[field].changed = "yes";
  }
};

const checkDuplicates = (key, actionIndex) => {
  const index = modifiedMetadata.value[actionIndex].keyVals.map((object) => object.key).indexOf(key);
  const lastIndex = modifiedMetadata.value[actionIndex].keyVals.map((object) => object.key).lastIndexOf(key);
  if (index !== lastIndex) {
    return "error";
  } else {
    return "new";
  }
};

const hasDuplicateKeys = computed(() => {
  let isError = false;
  modifiedMetadata.value.map((metadata) => {
    let keys = metadata.keyVals.map((object) => object.key);
    if (isError === true) {
      return true;
    } else {
      isError = _.some(Object.values(_.countBy(keys)), (count) => count > 1);
    }
  });
  return isError;
});

const formatMetadata = async () => {
  if (allMetadata.value !== undefined) {
    modifiedMetadata.value = JSON.parse(JSON.stringify(allMetadata.value)).map((metadata) => {
      return {
        flow: metadata.flow,
        action: metadata.action,
        keyVals: metadata.keyVals.map((keyVal) => {
          return {
            values: keyVal.values.length > 1 ? "multiple" : keyVal.values.toString(),
            changed: keyVal.values.length > 1 ? "multiple" : "no",
            key: keyVal.key,
          };
        }),
      };
    });
  }
};
const resumeClean = () => {
  closeConfirmDialog();
  requestResume();
};

const requestResume = async () => {
  let response;
  let batchedDids = getBatchDids(props.did);
  let success = false;
  let completedBatches = 0;
  try {
    displayBatchingDialog.value = true;
    batchCompleteValue.value = 0;
    for (const dids of batchedDids) {
      response = await resume(dids, getAllModifiedMetadata());
      if (response.value.data !== undefined && response.value.data !== null) {
        let successResume = [];
        for (const resumeStatus of response.value.data.resume) {
          if (resumeStatus.success) {
            successResume.push(resumeStatus);
          } else {
            notify.error(`Resume request failed for ${resumeStatus.did}`, resumeStatus.error);
          }
        }
        if (successResume.length > 0) {
          success = true;
        }
      }
      completedBatches += dids.length;
      batchCompleteValue.value = Math.round((completedBatches / props.did.length) * 100);
    }
    displayBatchingDialog.value = false;
    batchCompleteValue.value = 0;
    if (success) {
      const links = props.did.slice(0, maxSuccessDisplay).map((did) => `<a href="/deltafile/viewer/${did}" class="monospace">${did}</a>`);
      if (props.did.length > maxSuccessDisplay) links.push("...");
      let pluralized = pluralize(props.did.length, "DeltaFile");
      notify.success(`Resume request sent successfully for ${pluralized}`, links.join(", "));
      emit("update");
    }
  } catch (error) {
    displayBatchingDialog.value = false;
  }
};

const getAllModifiedMetadata = () => {
  let results = [];
  modifiedMetadata.value.forEach((metadata) => {
    let removedMetadata = [];
    let cleanMetadata = [];
    metadata.keyVals.forEach((keyVal) => {
      if (keyVal.changed === "deleted") {
        removedMetadata.push(keyVal.key);
      } else if (keyVal.changed === "yes" || keyVal.changed === "new") {
        cleanMetadata.push({
          key: keyVal.key,
          value: keyVal.values,
        });
      }
    });
    results.push({
      flow: metadata.flow,
      action: metadata.action,
      metadata: cleanMetadata,
      deleteMetadataKeys: removedMetadata,
    });
  });
  return results;
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
