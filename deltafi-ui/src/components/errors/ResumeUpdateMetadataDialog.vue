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
  <div class="update-metadata-dialog">
    <div v-if="modifiedMetadata !== 'undefined' && metadataDialogVisible" class="metadata-body">
      <Message v-if="hasDuplicateKeys" severity="error" :closable="false"> No duplicate keys permitted </Message>
      <Message v-if="modifiedMetadata.length == 0" severity="info" :closable="false"> No metadata to display </Message>
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
    </div>
    <div v-else-if="displayFetchingMetadataDialog">
      <div>
        <p>Metadata loading in progress. Please do not refresh the page!</p>
        <ProgressBar :value="batchCompleteValue" />
      </div>
    </div>
    <div v-else-if="displayBatchingMetadataDialog">
      <div>
        <p>{{ errorRequestType }} in progress. Please do not refresh the page!</p>
        <ProgressBar :value="batchCompleteValue" />
      </div>
    </div>
    <teleport v-if="isMounted && metadataDialogVisible" to="#dialogTemplate">
      <div class="p-dialog-footer">
        <Button label="Cancel" icon="pi pi-times" class="p-button-secondary p-button-outlined" @click="closeMetadataDialog" />
        <Button :label="`${errorRequestType}`" icon="fas fa-play" :disabled="hasDuplicateKeys" @click="submitClick" />
      </div>
    </teleport>
  </div>
</template>

<script setup>
import CollapsiblePanel from "@/components/CollapsiblePanel.vue";
import ProgressBar from "@/components/deprecatedPrimeVue/ProgressBar.vue";
import useErrorResume from "@/composables/useErrorResume";
import useMetadata from "@/composables/useMetadata";
import useNotifications from "@/composables/useNotifications";
import useUtilFunctions from "@/composables/useUtilFunctions";
import { computed, onBeforeMount, reactive, ref } from "vue";
import { useMounted } from "@vueuse/core";

import _ from "lodash";

import Button from "primevue/button";
import InputText from "primevue/inputtext";
import Message from "primevue/message";

const emit = defineEmits(["refreshAndClose"]);
const isMounted = ref(useMounted());
const { pluralize } = useUtilFunctions();
const maxSuccessDisplay = 10;
const displayFetchingMetadataDialog = ref(false);
const displayBatchingMetadataDialog = ref(false);
const notify = useNotifications();
const batchCompleteValue = ref(0);
const props = defineProps({
  did: {
    type: Array,
    required: true,
  },
  errorRequestType: {
    type: String,
    required: true,
  },
});

const { resume } = useErrorResume();
const { errorRequestType } = reactive(props);
const { fetchAll: meta, data: batchMetadata } = useMetadata();

const modifiedMetadata = ref([]);
const metadataDialogVisible = ref(false);
const confirmDialogVisible = ref(false);
const pluralized = ref();
const batchSize = 500;
const allMetadata = ref([]);

onBeforeMount(async () => {
  displayFetchingMetadataDialog.value = true;
  await formatMetadata();
  metadataDialogVisible.value = true;
});

const getAllMeta = async () => {
  const batchedDids = getBatchDids(props.did);
  batchCompleteValue.value = 0;
  let completedBatches = 0;
  allMetadata.value = [];
  for (const dids of batchedDids) {
    await meta(dids);
    if (batchMetadata.value.length > 0) {
      const tmp = [...batchMetadata.value, ...allMetadata.value];
      allMetadata.value = tmp;
    }
    completedBatches += dids.length;
    batchCompleteValue.value = Math.round((completedBatches / props.did.length) * 100);
  }
  displayFetchingMetadataDialog.value = false;
};

const showConfirmDialog = async () => {
  modifiedMetadata.value = [];
  pluralized.value = pluralize(props.did.length, "DeltaFile");
  confirmDialogVisible.value = true;
};

const closeMetadataDialog = () => {
  metadataDialogVisible.value = false;
  emit("refreshAndClose");
};

const addMetadataField = (flowValue, actionValue) => {
  const index = modifiedMetadata.value.findIndex((action) => action.flow === flowValue && action.action === actionValue);
  modifiedMetadata.value[index].keyVals.push({
    values: "",
    changed: "new",
    key: "",
  });
};

const removeMetadataField = (keyVal, flow, action) => {
  const index = modifiedMetadata.value.findIndex((object) => object.flow === flow && object.action === action);
  const field = modifiedMetadata.value[index].keyVals.findIndex((object) => object.key === keyVal.key);
  if (field.changed !== "new" && field.changed !== "error") {
    modifiedMetadata.value[index].keyVals[field].changed = "deleted";
  } else {
    modifiedMetadata.value[index].keyVals.splice(field, 1);
  }
};

const submitClick = async () => {
  submit();
};

const onCellEditComplete = (keyVal, flow, action) => {
  const index = modifiedMetadata.value.findIndex((object) => object.flow === flow && object.action === action);
  const field = modifiedMetadata.value[index].keyVals.findIndex((object) => object.key === keyVal.key);
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
    const keys = metadata.keyVals.map((object) => object.key);
    if (isError === true) {
      return true;
    } else {
      isError = _.some(Object.values(_.countBy(keys)), (count) => count > 1);
    }
  });
  return isError;
});

const formatMetadata = async () => {
  await getAllMeta();
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

const submit = async () => {
  let response;
  const batchedDids = getBatchDids(props.did);
  let success = false;
  let completedBatches = 0;
  try {
    displayBatchingMetadataDialog.value = true;
    batchCompleteValue.value = 0;
    for (const dids of batchedDids) {
      response = await resume(dids, getModifiedMetadata());
      if (response.value.data !== undefined && response.value.data !== null) {
        const successfulBatch = [];
        for (const responseStatus of response.value.data.resume) {
          if (responseStatus.success) {
            successfulBatch.push(responseStatus);
          } else {
            notify.error(`${errorRequestType} request failed for ${responseStatus.did}`, responseStatus.error);
          }
        }
        if (successfulBatch.length > 0) {
          success = true;
        }
      }
      completedBatches += dids.length;
      batchCompleteValue.value = Math.round((completedBatches / props.did.length) * 100);
    }
    displayBatchingMetadataDialog.value = false;
    batchCompleteValue.value = 0;

    if (success) {
      const links = props.did.slice(0, maxSuccessDisplay).map((did) => `<a href="/deltafile/viewer/${did}" class="monospace">${did}</a>`);
      if (props.did.length > maxSuccessDisplay) links.push("...");
      const pluralized = pluralize(props.did.length, "DeltaFile");
      notify.success(`${errorRequestType} request sent successfully for ${pluralized}`, links.join(", "));
      emit("refreshAndClose");
    }
  } catch (error) {
    displayBatchingMetadataDialog.value = false;
  }
};

const getModifiedMetadata = () => {
  const results = [];
  modifiedMetadata.value.forEach((metadata) => {
    const removedMetadata = [];
    const cleanMetadata = [];
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
  return _.chunk(allDids, batchSize);
};

defineExpose({
  showConfirmDialog,
});
</script>

<style />
