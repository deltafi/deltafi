<!--
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>

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
<!-- ABOUTME: Metadata dialog for bulk replay operations. -->
<!-- ABOUTME: Allows adding or overriding source metadata for replay by flow or message. -->

<template>
  <div class="update-metadata-dialog">
    <div v-if="metadataDialogVisible" class="metadata-body">
      <Message v-if="hasDuplicateKeys" severity="error" :closable="false"> No duplicate keys permitted </Message>
      <Message v-if="modifiedMetadata.length === 0" severity="info" :closable="false">
        Enter key/value pairs below to add or override source metadata for all replayed DeltaFiles.
      </Message>
      <div v-for="(field, index) in modifiedMetadata" :key="index" class="row p-fluid mb-4">
        <div class="col-5">
          <InputText v-model="field.key" type="text" placeholder="Key" @change="onInputChange" />
        </div>
        <div class="col-5">
          <InputText v-model="field.value" type="text" placeholder="Value" />
        </div>
        <div class="col-2 text-right">
          <Button icon="pi pi-times" aria-label="Remove metadata field" @click="removeMetadataField(index)" />
        </div>
      </div>
    </div>
    <teleport v-if="isMounted && metadataDialogVisible" to="#dialogTemplate">
      <div class="p-dialog-footer">
        <Button label="Add Metadata Field" icon="pi pi-plus" class="p-button-secondary p-button-outlined" @click="addMetadataField" />
        <Button label="Cancel" icon="pi pi-times" class="p-button-secondary p-button-outlined" @click="closeMetadataDialog" />
        <Button label="Replay" icon="fas fa-redo" :disabled="hasDuplicateKeys" @click="submitMetadata" />
      </div>
    </teleport>
  </div>
</template>

<script setup>
import { computed, onBeforeMount, ref } from "vue";
import { useMounted } from "@vueuse/core";

import _ from "lodash";

import Button from "primevue/button";
import InputText from "primevue/inputtext";
import Message from "primevue/message";

const emit = defineEmits(["refreshAndClose", "submitWithMetadata"]);
const isMounted = ref(useMounted());
const props = defineProps({
  flowInfo: {
    type: Array,
    required: true,
  },
});

const modifiedMetadata = ref([]);
const metadataDialogVisible = ref(false);

onBeforeMount(async () => {
  metadataDialogVisible.value = true;
});

const closeMetadataDialog = () => {
  metadataDialogVisible.value = false;
  emit("refreshAndClose");
};

const addMetadataField = () => {
  modifiedMetadata.value.push({ key: "", value: "" });
};

const removeMetadataField = (index) => {
  modifiedMetadata.value.splice(index, 1);
};

const submitMetadata = () => {
  emit("submitWithMetadata", {
    removeMetadataKeys: [],
    replaceMetadata: getModifiedMetadata(),
  });
  emit("refreshAndClose");
};

const onInputChange = () => {
  // Trigger reactivity for duplicate check
};

const hasDuplicateKeys = computed(() => {
  const keys = modifiedMetadata.value.map((field) => field.key).filter(Boolean);
  return _.some(Object.values(_.countBy(keys)), (count) => count > 1);
});

const getModifiedMetadata = () => {
  return modifiedMetadata.value
    .filter((field) => field.key && field.key.trim())
    .map((field) => ({
      key: field.key,
      value: field.value,
    }));
};

defineExpose({});
</script>

<style />
