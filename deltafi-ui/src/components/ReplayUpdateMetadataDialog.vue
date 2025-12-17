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
      <Message v-if="isBatchMode && modifiedMetadata.length === 0" severity="info" :closable="false">
        No metadata preview available for batch operations. Enter key/value pairs below to add or override metadata.
      </Message>
      <Message v-else-if="!isBatchMode && modifiedMetadata.length === 0" severity="info" :closable="false"> No metadata to display </Message>
      <div v-for="field in modifiedMetadata" :key="field" class="row p-fluid mb-4">
        <div class="col-5">
          <InputText v-if="field.changed === 'new'" v-model="field.key" type="text" placeholder="Key" @change="onInputChange(field)" />
          <InputText v-else-if="field.changed === 'error'" v-model="field.key" type="text" placeholder="Key" class="p-invalid" @change="onInputChange(field)" />
          <InputText v-else v-model="field.key" type="text" placeholder="Key" disabled class="p-valid" />
        </div>
        <div class="col-5">
          <InputText v-model="field.values" type="text" placeholder="Value" @change="onInputChange(field)" />
        </div>
        <div class="col-2 text-right">
          <Button icon="pi pi-times" @click="removeMetadataField(field)" />
        </div>
      </div>
    </div>
    <div v-else-if="displayFetchingMetadataDialog">
      <div>
        <p>Loading metadata...</p>
        <ProgressBar mode="indeterminate" />
      </div>
    </div>
    <teleport v-if="isMounted && metadataDialogVisible" to="#dialogTemplate">
      <div class="p-dialog-footer">
        <Button label="Add Metadata Field" icon="pi pi-plus" class="p-button-secondary p-button-outlined" @click="addMetadataField" />
        <Button label="Cancel" icon="pi pi-times" class="p-button-secondary p-button-outlined" @click="closeMetadataDialog" />
        <Button label="Replay" icon="fas fa-play" :disabled="hasDuplicateKeys" @click="submitMetadata" />
      </div>
    </teleport>
  </div>
</template>

<script setup>
import ProgressBar from "@/components/deprecatedPrimeVue/ProgressBar.vue";
import useDeltaFiles from "@/composables/useDeltaFiles";
import { computed, nextTick, onBeforeMount, ref } from "vue";
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

const modifiedMetadata = ref([]);
const removedMetadata = ref([]);
const metadataDialogVisible = ref(false);

const isBatchMode = computed(() => props.did.length !== 1);

onBeforeMount(async () => {
  displayFetchingMetadataDialog.value = true;
  await formatMetadata();
  metadataDialogVisible.value = true;
});

const getAllMeta = async () => {
  if (isBatchMode.value) {
    displayFetchingMetadataDialog.value = false;
    return [];
  }

  // Single DeltaFile mode - fetch it and extract source metadata from flow 0
  const did = props.did[0];
  await getDeltaFile(did);

  if (deltaFileData && deltaFileData.flows && deltaFileData.flows.length > 0) {
    // Flow 0 is the source flow - get its accumulated metadata
    const sourceFlow = deltaFileData.flows[0];
    const sourceMetadata = sourceFlow.input?.metadata || {};

    displayFetchingMetadataDialog.value = false;
    return Object.entries(sourceMetadata).map(([key, value]) => ({
      key,
      values: value,
    }));
  }

  displayFetchingMetadataDialog.value = false;
  return [];
};

const closeMetadataDialog = () => {
  metadataDialogVisible.value = false;
  emit("refreshAndClose");
};

const addMetadataField = async () => {
  modifiedMetadata.value.push({ key: "", values: "", changed: "new" });
  await nextTick();
  const replayDialogElement = document.getElementById("dialogTemplateContent");
  if (replayDialogElement) {
    replayDialogElement.scrollTo(0, replayDialogElement.scrollHeight);
  }
};

const removeMetadataField = (field) => {
  if (field.changed !== "new" && field.changed !== "error") {
    removedMetadata.value.push(field.key);
  }
  const index = modifiedMetadata.value.indexOf(field);
  modifiedMetadata.value.splice(index, 1);
};

const submitMetadata = () => {
  emit("submitWithMetadata", {
    removeMetadataKeys: removedMetadata.value,
    replaceMetadata: getModifiedMetadata(),
  });
  emit("refreshAndClose");
};

const onInputChange = (field) => {
  const index = modifiedMetadata.value.indexOf(field);
  if (modifiedMetadata.value[index].changed === "new" || modifiedMetadata.value[index].changed === "error") {
    modifiedMetadata.value[index].changed = checkDuplicates(field.key);
  } else {
    modifiedMetadata.value[index].changed = "yes";
  }
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
  const allMetadata = await getAllMeta();
  if (allMetadata && allMetadata.length > 0) {
    modifiedMetadata.value = allMetadata.map((metadata) => ({
      values: metadata.values,
      changed: "no",
      key: metadata.key,
    }));
  }
};

const getModifiedMetadata = () => {
  if (modifiedMetadata.value !== null) {
    const filteredMetadata = modifiedMetadata.value.filter((metadata) => metadata.changed === "new" || metadata.changed === "yes");
    return filteredMetadata.map((metadata) => ({
      key: metadata.key,
      value: metadata.values,
    }));
  } else {
    return [];
  }
};

defineExpose({});
</script>

<style />
