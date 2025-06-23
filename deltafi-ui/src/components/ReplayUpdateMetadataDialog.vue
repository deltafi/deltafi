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
  <div class="update-meatadata-dialog">
    <div v-if="modifiedMetadata !== 'undefined' && metadataDialogVisible" class="metadata-body">
      <Message v-if="hasDuplicateKeys" severity="error" :closable="false"> No duplicate keys permitted </Message>
      <Message v-if="modifiedMetadata.length == 0" severity="info" :closable="false"> No metadata to display </Message>
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
    <div v-else-if="displayFetchingMetadataDialog">
      <div>
        <p>Metadata loading in progress. Please do not refresh the page!</p>
        <ProgressBar :value="batchCompleteValue" />
      </div>
    </div>
    <div v-else-if="displayBatchingMetadataDialog">
      <div>
        <p>Replay in progress. Please do not refresh the page!</p>
        <ProgressBar :value="batchCompleteValue" />
      </div>
    </div>
    <teleport v-if="isMounted && metadataDialogVisible" to="#dialogTemplate">
      <div class="p-dialog-footer">
        <Button label="Add Metadata Field" icon="pi pi-plus" class="p-button-secondary p-button-outlined" @click="addMetadataField" />
        <Button label="Cancel" icon="pi pi-times" class="p-button-secondary p-button-outlined" @click="closeMetadataDialog" />
        <Button :label="`Replay`" icon="fas fa-play" @click="submitClick" />
      </div>
    </teleport>
  </div>
</template>

<script setup>
import ProgressBar from "@/components/deprecatedPrimeVue/ProgressBar.vue";
import useMetadata from "@/composables/useMetadata";
import useNotifications from "@/composables/useNotifications";
import useReplay from "@/composables/useReplay";
import useUtilFunctions from "@/composables/useUtilFunctions";
import { computed, nextTick, onBeforeMount, reactive, ref } from "vue";
import { useMounted } from "@vueuse/core";

import _ from "lodash";

import Button from "primevue/button";
import InputText from "primevue/inputtext";
import Message from "primevue/message";

const emit = defineEmits(["refreshAndClose"]);
const isMounted = ref(useMounted());
const { pluralize } = useUtilFunctions();
const invalidKey = ref(false);
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
});

const { replay } = useReplay();
const { fetch: meta, data: batchMetadata } = useMetadata();

const modifiedMetadata = ref([]);
const removedMetadata = ref([]);
const metadataDialogVisible = ref(false);
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

  allMetadata.value = await getUniqueMetadataKeys(allMetadata.value);
  displayFetchingMetadataDialog.value = false;
};

const getUniqueMetadataKeys = async (originalMetadata) => {
  const data = JSON.parse(JSON.stringify(originalMetadata));
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

const showConfirmDialog = async () => {
  modifiedMetadata.value = [];
  removedMetadata.value = [];
  pluralized.value = pluralize(props.did.length, "DeltaFile");
};

const closeMetadataDialog = () => {
  metadataDialogVisible.value = false;
  emit("refreshAndClose");
};

const addMetadataField = async () => {
  modifiedMetadata.value.push({ key: "", values: "", changed: "new" });
  await nextTick();
  const replayDialogElement = document.getElementById("dialogTemplateContent");
  replayDialogElement.scrollTo(0, replayDialogElement.scrollHeight);
};

const removeMetadataField = (field) => {
  if (field.changed !== "new") {
    removedMetadata.value.push(field.key);
  }
  const index = modifiedMetadata.value.indexOf(field);
  modifiedMetadata.value.splice(index, 1);
};

const submitClick = () => {
  submit();
  closeMetadataDialog();
};

const onInputChange = (field) => {
  const index = modifiedMetadata.value.indexOf(field);
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
  const keys = modifiedMetadata.value.map((object) => object.key);
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

const submit = async () => {
  let response;
  const batchedDids = getBatchDids(props.did);
  let success = false;
  let completedBatches = 0;
  try {
    displayBatchingMetadataDialog.value = true;
    batchCompleteValue.value = 0;
    const newDids = new Array();
    for (const dids of batchedDids) {
      response = await replay(dids, removedMetadata.value, getModifiedMetadata());
      if (response.value.data !== undefined && response.value.data !== null) {
        const successfulBatch = new Array();
        for (const responseStatus of response.value.data.replay) {
          if (responseStatus.success) {
            successfulBatch.push(responseStatus);
            newDids.push(responseStatus.did);
          } else {
            notify.error(`Replay request failed for ${responseStatus.did}`, responseStatus.error);
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
      const pluralized = pluralize(newDids.length, "DeltaFile");
      const links = newDids.slice(0, maxSuccessDisplay).map((did) => `<a href="/deltafile/viewer/${did}" class="monospace">${did}</a>`);
      notify.success(`Replay request sent successfully for ${pluralized}`, links.join(", "));
      emit("refreshAndClose");
    }
  } catch (error) {
    displayBatchingMetadataDialog.value = false;
  }
};

const getModifiedMetadata = () => {
  if (modifiedMetadata.value !== null) {
    const filteredMetadata = modifiedMetadata.value.filter((metadata) => metadata.changed === "new" || metadata.changed === "yes");
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
  return _.chunk(allDids, batchSize);
};

defineExpose({
  showConfirmDialog,
});
</script>

<style />
