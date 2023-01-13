<!--
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>

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
  <div>
    <span @click="show()">
      <slot />
    </span>
    <Dialog v-model:visible="dialogVisible" :maximizable="false" :modal="true" :dismissable-mask="true" :draggable="false" :style="{ width: '30vw' }" @show="onShow">
      <template #header>
        <div class="p-dialog-title">
          Editing {{ localVariable.name }}
          <div class="subtitle">{{ localVariable.flow }}</div>
        </div>
      </template>
      <Message v-for="error of errors" :key="error.message" severity="error">{{ error.message }}</Message>
      <MetadataMapEdit :model-value="viewList(localVariable.requiredMetadata)" @metadata-changed="newMetadataChanges"></MetadataMapEdit>
      <template #footer>
        <div class="d-flex float-right">
          <Button label="Cancel" icon="pi pi-times" class="p-button-text" @click="cancel"></Button>
          <Button label="Confirm Changes" icon="pi pi-check" @click="onConfirmClick"></Button>
        </div>
      </template>
    </Dialog>
  </div>
</template>

<script setup>
import MetadataMapEdit from "@/components/ingressRouting/MetadataMapEdit";
import { ref, defineProps, defineEmits } from "vue";

import Button from "primevue/button";
import Dialog from "primevue/dialog";
import Message from "primevue/message";

import _ from "lodash";

const props = defineProps({
  variable: {
    type: Object,
    required: true,
  },
});
const errors = ref([]);
const emit = defineEmits(["saveMetadata"]);
const localVariable = ref({});
const dialogVisible = ref(false);
const addNewMetadata = ref([]);

const newMetadataChanges = (metadataValues) => {
  addNewMetadata.value = metadataValues;
};

const viewList = (value) => {
  if (_.isEmpty(value)) {
    return null;
  }
  // Combine objects of Key Name and Value Name into a key value pair
  let combineKeyValue = value.reduce((r, { key, value }) => ((r[key] = value), r), {});

  // Turn Object into string
  return Object.entries(combineKeyValue)
    .map(([k, v]) => `${k}: ${v}`)
    .join(", ");
};

const show = () => {
  errors.value = [];
  dialogVisible.value = true;
};

const hide = () => {
  localVariable.value = {};
  dialogVisible.value = false;
};

const onConfirmClick = () => {
  emit("saveMetadata", addNewMetadata.value);
  hide();
};

const cancel = () => {
  hide();
};

const onShow = () => {
  localVariable.value = JSON.parse(JSON.stringify(props.variable));

  if (localVariable.value.value === null && localVariable.value.defaultValue !== null) localVariable.value.value = localVariable.value.defaultValue;
};
</script>

<style lang="scss">
.subtitle {
  font-size: 0.75rem;
  font-weight: 300;
  color: var(--gray-700);
}
</style>
