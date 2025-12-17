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
      <p v-if="!stopped">Error acknowledgment in progress. If you navigate away, the operation will stop after the current batch.</p>
      <p v-else>Completing current batch...</p>
      <ProgressBar :value="batchCompleteValue" />
      <p class="mt-2 text-center">{{ totalProcessedCount.toLocaleString() }} / {{ expectedTotalCount.toLocaleString() }} DeltaFiles</p>
    </div>
    <template #footer>
      <Button label="Stop" icon="pi pi-stop" class="p-button-danger" :disabled="stopped" @click="stopBatching" />
    </template>
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
const totalProcessedCount = ref(0);
const expectedTotalCount = ref(0);
const stopped = ref(false);
const batchSize = 200;
const props = defineProps({
  dids: {
    type: Array,
    required: false,
    default: () => [],
  },
  flowInfo: {
    type: Array,
    required: false,
    default: null,
  },
  messageInfo: {
    type: Array,
    required: false,
    default: null,
  },
  visible: {
    type: Boolean,
    required: true,
  },
});

const emit = defineEmits(["acknowledged", "update:visible"]);
const { post: PostAcknowledgeErrors, postByFlow, postByMessage } = useAcknowledgeErrors();
const { pluralize } = useUtilFunctions();
const reason = ref("");
const reasonInvalid = ref(false);

const isFlowMode = computed(() => props.flowInfo && props.flowInfo.length > 0);
const isMessageMode = computed(() => props.messageInfo && props.messageInfo.length > 0);

const stopBatching = () => {
  stopped.value = true;
};

const acknowledgeButtonLabel = computed(() => {
  if (isFlowMode.value) {
    const count = props.flowInfo.length;
    if (count === 1) return `Acknowledge Errors in ${props.flowInfo[0].flowName}`;
    return `Acknowledge Errors in ${count} Flows`;
  }
  if (isMessageMode.value) {
    const count = props.messageInfo.length;
    if (count === 1) return "Acknowledge Errors with Message";
    return `Acknowledge Errors with ${count} Messages`;
  }
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
      close();

      if (isFlowMode.value) {
        const expectedTotal = _.sumBy(props.flowInfo, "count") || 0;
        let totalProcessed = 0;
        displayBatchingDialog.value = true;
        batchCompleteValue.value = 0;
        totalProcessedCount.value = 0;
        expectedTotalCount.value = expectedTotal;
        stopped.value = false;

        for (const flow of props.flowInfo) {
          let flowProcessed = 0;
          const flowExpected = flow.count || 0;

          while (!stopped.value && flowProcessed < flowExpected && totalProcessed < expectedTotal) {
            const response = await postByFlow(flow.flowType, flow.flowName, reason.value, batchSize);
            const results = response?.acknowledgeByFlow || [];
            if (results.length === 0) break;
            flowProcessed += results.length;
            totalProcessed += results.length;
            totalProcessedCount.value = totalProcessed;
            batchCompleteValue.value = Math.round((totalProcessed / expectedTotal) * 100);
          }
          if (stopped.value) break;
        }

        displayBatchingDialog.value = false;
        batchCompleteValue.value = 0;
        emit("acknowledged", props.flowInfo, reason.value);
      } else if (isMessageMode.value) {
        const expectedTotal = _.sumBy(props.messageInfo, "count") || 0;
        let totalProcessed = 0;
        displayBatchingDialog.value = true;
        batchCompleteValue.value = 0;
        totalProcessedCount.value = 0;
        expectedTotalCount.value = expectedTotal;
        stopped.value = false;

        for (const msg of props.messageInfo) {
          while (!stopped.value && totalProcessed < expectedTotal) {
            const response = await postByMessage(msg.errorCause, reason.value, batchSize);
            const results = response?.acknowledgeByMessage || [];
            if (results.length === 0) break;
            totalProcessed += results.length;
            totalProcessedCount.value = totalProcessed;
            batchCompleteValue.value = Math.round((totalProcessed / expectedTotal) * 100);
          }
          if (stopped.value) break;
        }

        displayBatchingDialog.value = false;
        batchCompleteValue.value = 0;
        emit("acknowledged", props.messageInfo, reason.value);
      } else {
        const batchedDids = getBatchDids(props.dids);
        const expectedTotal = props.dids.length;
        if (expectedTotal > batchSize) {
          displayBatchingDialog.value = true;
        }
        let totalProcessed = 0;
        batchCompleteValue.value = 0;
        totalProcessedCount.value = 0;
        expectedTotalCount.value = expectedTotal;
        stopped.value = false;

        for (const dids of batchedDids) {
          if (stopped.value) break;
          await PostAcknowledgeErrors(dids, reason.value);
          totalProcessed += dids.length;
          totalProcessedCount.value = totalProcessed;
          batchCompleteValue.value = Math.round((totalProcessed / expectedTotal) * 100);
        }
        displayBatchingDialog.value = false;
        batchCompleteValue.value = 0;
        emit("acknowledged", props.dids, reason.value);
      }

      emit("update:visible", false);
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
