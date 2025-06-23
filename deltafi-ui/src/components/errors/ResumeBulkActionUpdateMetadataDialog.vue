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
    <div v-if="modifiedMetadata !== 'undefined' && metadataDialogVisible && !displayBatchingDialog" class="metadata-body">
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
    <div v-else-if="displayBatchingDialog">
      <div>
        <p>Resume in progress. Please do not refresh the page!</p>
        <ProgressBar :value="batchCompleteValue" />
      </div>
    </div>
    <teleport v-if="isMounted && metadataDialogVisible" to="#dialogTemplate">
      <div class="p-dialog-footer">
        <Button label="Cancel" icon="pi pi-times" class="p-button-secondary p-button-outlined" @click="closeMetadataDialog" />
        <Button label="Resume" icon="fas fa-play" :disabled="hasDuplicateKeys" @click="submitResume" />
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
const displayBatchingDialog = ref(false);
const displayFetchingMetadataDialog = ref(false);
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

const did = ref(_.flatten([...new Set(_.map(props.flowInfo, "dids"))]));

const { resumeByErrorCause, resumeByFlow } = useErrorResume();

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
  const batchedDids = getBatchDids(did.value);
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
    batchCompleteValue.value = Math.round((completedBatches / did.value.length) * 100);
  }
  displayFetchingMetadataDialog.value = false;
};

const showConfirmDialog = async () => {
  modifiedMetadata.value = [];
  pluralized.value = pluralize(did.value.length, "DeltaFile");
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

const submitResume = async () => {
  if (_.isEqual(props.bundleRequestType, "resumeByErrorCause")) {
    await requestResumeByErrorCause();
  } else if (_.isEqual(props.bundleRequestType, "resumeByFlow")) {
    await requestResumeByFlow();
  }
  closeMetadataDialog();
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

const requestResumeByErrorCause = async () => {
  let response;
  let completedBatches = 0;
  try {
    displayBatchingDialog.value = true;
    batchCompleteValue.value = 0;
    for (const flow of props.flowInfo) {
      response = await resumeByErrorCause(flow.message, _.find(getModifiedMetadata(), { flow: flow.flowName, flowType: flow.flowType }), includeAcknowledged.value);
      if (response.data !== undefined && response.data !== null) {
        for (const resumeStatus of response.data.resumeByErrorCause) {
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

const requestResumeByFlow = async () => {
  let response;
  let completedBatches = 0;
  try {
    displayBatchingDialog.value = true;
    batchCompleteValue.value = 0;
    for (const flow of props.flowInfo) {
      response = await resumeByFlow(flow.flowType, flow.flowName, _.find(getModifiedMetadata(), { flow: flow.flowName, flowType: flow.flowType }), includeAcknowledged.value);
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

const includeAcknowledged = computed(() => {
  if (props.acknowledged === true || _.isNull(props.acknowledged)) {
    return true;
  }
  return false;
});

const getBatchDids = (allDids) => {
  return _.chunk(allDids, batchSize);
};

defineExpose({
  showConfirmDialog,
});
</script>

<style />
