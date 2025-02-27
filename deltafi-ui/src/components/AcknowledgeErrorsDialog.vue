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
  <Dialog v-model:visible="displayAcknowledgeDialog" :header="acknowledgeButtonLabel" :maximizable="false" :modal="true" :breakpoints="{ '960px': '75vw', '940px': '90vw' }" :style="{ width: '30vw' }" @update:visible="close">
    <div class="p-fluid">
      <span class="p-float-label mt-3">
        <InputText id="reason" v-model="reason" type="text" :class="{ 'p-invalid': reasonInvalid }" autofocus />
        <label for="reason">Reason</label>
      </span>
    </div>
    <template #footer>
      <Button label="Cancel" icon="pi pi-times" class="p-button-text" @click="close" />
      <Button :label="acknowledgeButtonLabel" icon="pi pi-check" @click="acknowledge" />
    </template>
  </Dialog>
  <Dialog :visible="displayBatchingDialog" :breakpoints="{ '960px': '75vw', '940px': '90vw' }" :style="{ width: '30vw' }" :modal="true" :closable="false" :close-on-escape="false" :draggable="false" header="Acknowledging Errors">
    <div>
      <p>Error acknowledgment in progress. Please do not refresh the page!</p>
      <ProgressBar :value="batchCompleteValue" />
    </div>
  </Dialog>
</template>

<script setup>
import ProgressBar from "@/components/deprecatedPrimeVue/ProgressBar.vue";
import useAcknowledgeErrors from "@/composables/useAcknowledgeErrors";
import useUtilFunctions from "@/composables/useUtilFunctions";
import { computed, ref, watch } from "vue";

import _ from "lodash";

import Button from "primevue/button";
import Dialog from "primevue/dialog";
import InputText from "primevue/inputtext";

const displayAcknowledgeDialog = ref(false);
const batchCompleteValue = ref(0);
const displayBatchingDialog = ref(false);
const batchSize = 500;
const props = defineProps({
  dids: {
    type: Array,
    required: true,
  },
  visible: {
    type: Boolean,
    required: true,
  },
});

const emit = defineEmits(["acknowledged", "update:visible"]);
const { post: PostAcknowledgeErrors } = useAcknowledgeErrors();
const { pluralize } = useUtilFunctions();
const reason = ref("");
const reasonInvalid = ref(false);

const acknowledgeButtonLabel = computed(() => {
  if (props.dids.length === 1) return "Acknowledge Error";
  const pluralized = pluralize(props.dids.length, "Error");
  return `Acknowledge ${pluralized}`;
});

watch(
  () => props.visible,
  () => {
    displayAcknowledgeDialog.value = props.visible;
  }
);

const acknowledge = async () => {
  if (reason.value) {
    try {
      const batchedDids = getBatchDids(props.dids);
      if (props.dids.length > batchSize) {
        close();
        displayBatchingDialog.value = true;
      } else {
        close();
      }
      let completedBatches = 0;
      batchCompleteValue.value = 0;
      for (const dids of batchedDids) {
        await PostAcknowledgeErrors(dids, reason.value);
        completedBatches += dids.length;
        batchCompleteValue.value = Math.round((completedBatches / props.dids.length) * 100);
      }
      displayBatchingDialog.value = false;
      batchCompleteValue.value = 0;
      emit("acknowledged", props.dids, reason.value);
      emit("update:visible", false);
      close();
      reason.value = "";
    } catch {
      // Do Nothing
    }
  } else {
    reasonInvalid.value = true;
  }
};

const getBatchDids = (allDids) => {
  return _.chunk(allDids, batchSize);
};

const close = () => {
  displayAcknowledgeDialog.value = false;
  emit("update:visible", false);
};
</script>

<style />
