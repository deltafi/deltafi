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
    <div v-if="metadataDialogVisible" class="metadata-body">
      <Message v-if="hasDuplicateKeys" severity="error" :closable="false"> No duplicate keys permitted </Message>

      <!-- Batch mode: simple key/value inputs that apply to all -->
      <template v-if="isBatchMode">
        <Message v-if="batchMetadata.length === 0" severity="info" :closable="false">
          Enter key/value pairs below to add or override metadata for all resumed DeltaFiles.
        </Message>
        <div v-for="(field, index) in batchMetadata" :key="index" class="row p-fluid mb-4">
          <div class="col-5">
            <InputText v-model="field.key" type="text" placeholder="Key" @change="onBatchInputChange" />
          </div>
          <div class="col-5">
            <InputText v-model="field.value" type="text" placeholder="Value" />
          </div>
          <div class="col-2 text-right">
            <Button icon="pi pi-times" @click="removeBatchField(index)" />
          </div>
        </div>
        <Button label="Add Metadata Field" icon="pi pi-plus" class="p-button-secondary p-button-outlined" @click="addBatchField" />
      </template>

      <!-- Single DeltaFile mode: flow/action structure -->
      <template v-else>
        <Message v-if="modifiedMetadata.length === 0" severity="info" :closable="false"> No metadata to display </Message>
        <CollapsiblePanel v-for="actions in modifiedMetadata" :key="actions" :header="actions.flow + '.' + actions.action" class="mb-3">
          <div v-for="pairs in actions.keyVals" :key="pairs">
            <div v-if="pairs.changed !== 'deleted'" class="row p-fluid mb-4">
              <div class="col-5">
                <InputText v-if="pairs.changed === 'new'" v-model="pairs.key" type="text" placeholder="Key" @change="onCellEditComplete(pairs, actions.flow, actions.action)" />
                <InputText v-else v-model="pairs.key" type="text" placeholder="Key" disabled class="p-valid" />
              </div>
              <div class="col-5">
                <InputText v-model="pairs.values" type="text" placeholder="Value" @change="onCellEditComplete(pairs, actions.flow, actions.action)" />
              </div>
              <div class="col-2 text-right">
                <Button icon="pi pi-times" @click="removeMetadataField(pairs, actions.flow, actions.action)" />
              </div>
            </div>
          </div>
          <Button label="Add Metadata Field" icon="pi pi-plus" class="p-button-secondary p-button-outlined" @click="addMetadataField(actions.flow, actions.action)" />
        </CollapsiblePanel>
      </template>
    </div>
    <div v-else-if="displayFetchingMetadataDialog">
      <div>
        <p>Loading metadata...</p>
        <ProgressBar mode="indeterminate" />
      </div>
    </div>
    <teleport v-if="isMounted && metadataDialogVisible" to="#dialogTemplate">
      <div class="p-dialog-footer">
        <Button label="Cancel" icon="pi pi-times" class="p-button-secondary p-button-outlined" @click="closeMetadataDialog" />
        <Button label="Resume" icon="fas fa-play" :disabled="hasDuplicateKeys" @click="submitMetadata" />
      </div>
    </teleport>
  </div>
</template>

<script setup>
import CollapsiblePanel from "@/components/CollapsiblePanel.vue";
import ProgressBar from "@/components/deprecatedPrimeVue/ProgressBar.vue";
import useDeltaFiles from "@/composables/useDeltaFiles";
import { computed, onBeforeMount, ref } from "vue";
import { useMounted } from "@vueuse/core";

import _ from "lodash";

import Button from "primevue/button";
import InputText from "primevue/inputtext";
import Message from "primevue/message";

const emit = defineEmits(["refreshAndClose", "submitWithMetadata"]);
const isMounted = ref(useMounted());
const displayFetchingMetadataDialog = ref(false);
const props = defineProps({
  did: {
    type: Array,
    required: true,
  },
});

const { getDeltaFile, data: deltaFileData } = useDeltaFiles();

// Single DeltaFile mode data
const modifiedMetadata = ref([]);
const allMetadata = ref([]);

// Batch mode data - simple key/value pairs
const batchMetadata = ref([]);

const metadataDialogVisible = ref(false);

const isBatchMode = computed(() => props.did.length !== 1);

onBeforeMount(async () => {
  displayFetchingMetadataDialog.value = true;
  await formatMetadata();
  metadataDialogVisible.value = true;
});

const getAllMeta = async () => {
  allMetadata.value = [];

  if (isBatchMode.value) {
    displayFetchingMetadataDialog.value = false;
    return;
  }

  // Single DeltaFile mode - fetch it and extract errored flow's metadata
  const did = props.did[0];
  await getDeltaFile(did);

  if (deltaFileData && deltaFileData.flows) {
    // Find the first flow with an active error
    const erroredFlow = deltaFileData.flows.find((flow) => flow.state === "ERROR" && flow.actions && flow.actions.length > 0);

    if (erroredFlow) {
      const lastAction = erroredFlow.actions[erroredFlow.actions.length - 1];
      if (lastAction && lastAction.state === "ERROR") {
        // Get the flow's input metadata merged with action metadata
        const flowMetadata = erroredFlow.input?.metadata || {};
        const actionMetadata = lastAction.metadata || {};
        const mergedMetadata = { ...flowMetadata, ...actionMetadata };

        const keyVals = Object.entries(mergedMetadata).map(([key, value]) => ({
          key,
          values: [value],
        }));

        allMetadata.value = [
          {
            flow: erroredFlow.name,
            action: lastAction.name,
            keyVals,
          },
        ];
      }
    }
  }

  displayFetchingMetadataDialog.value = false;
};

const closeMetadataDialog = () => {
  metadataDialogVisible.value = false;
  emit("refreshAndClose");
};

// Batch mode functions
const addBatchField = () => {
  batchMetadata.value.push({ key: "", value: "" });
};

const removeBatchField = (index) => {
  batchMetadata.value.splice(index, 1);
};

const onBatchInputChange = () => {
  // Trigger reactivity for duplicate check
};

// Single DeltaFile mode functions
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
  const keyValObj = modifiedMetadata.value[index].keyVals[field];
  if (keyValObj.changed !== "new" && keyValObj.changed !== "error") {
    modifiedMetadata.value[index].keyVals[field].changed = "deleted";
  } else {
    modifiedMetadata.value[index].keyVals.splice(field, 1);
  }
};

const submitMetadata = () => {
  emit("submitWithMetadata", getResumeMetadata());
  emit("refreshAndClose");
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
  if (isBatchMode.value) {
    const keys = batchMetadata.value.map((field) => field.key).filter(Boolean);
    return _.some(Object.values(_.countBy(keys)), (count) => count > 1);
  } else {
    let isError = false;
    modifiedMetadata.value.forEach((metadata) => {
      const keys = metadata.keyVals.map((object) => object.key);
      if (!isError) {
        isError = _.some(Object.values(_.countBy(keys)), (count) => count > 1);
      }
    });
    return isError;
  }
});

const formatMetadata = async () => {
  await getAllMeta();
  if (allMetadata.value !== undefined && allMetadata.value.length > 0) {
    modifiedMetadata.value = JSON.parse(JSON.stringify(allMetadata.value)).map((metadata) => ({
      flow: metadata.flow,
      action: metadata.action,
      keyVals: metadata.keyVals.map((keyVal) => ({
        values: keyVal.values.length > 1 ? "" : keyVal.values.toString(),
        changed: keyVal.values.length > 1 ? "multiple" : "no",
        key: keyVal.key,
      })),
    }));
  }
};

const getResumeMetadata = () => {
  if (isBatchMode.value) {
    // Batch mode: send metadata with null flow/action to apply to all
    const metadata = batchMetadata.value
      .filter((field) => field.key && field.key.trim())
      .map((field) => ({ key: field.key, value: field.value }));

    if (metadata.length === 0) {
      return [];
    }

    return [
      {
        flow: null,
        action: null,
        metadata,
        deleteMetadataKeys: [],
      },
    ];
  } else {
    // Single DeltaFile mode: include flow/action targeting
    const results = [];
    modifiedMetadata.value.forEach((metadata) => {
      const removedMetadata = [];
      const cleanMetadata = [];
      metadata.keyVals.forEach((keyVal) => {
        if (keyVal.changed === "deleted") {
          removedMetadata.push(keyVal.key);
        } else if (keyVal.changed === "yes" || keyVal.changed === "new") {
          if (keyVal.key) {
            cleanMetadata.push({
              key: keyVal.key,
              value: keyVal.values,
            });
          }
        }
      });
      if (cleanMetadata.length > 0 || removedMetadata.length > 0) {
        results.push({
          flow: metadata.flow,
          action: metadata.action,
          metadata: cleanMetadata,
          deleteMetadataKeys: removedMetadata,
        });
      }
    });
    return results;
  }
};

defineExpose({});
</script>

<style />
