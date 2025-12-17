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
  <div>
    <Dialog v-model:visible="dialogVisible" header="Annotate DeltaFile" :maximizable="false" :modal="true" :draggable="false" :style="{ width: '30vw' }">
      <MapEdit v-model="metadata" />
      <template #footer>
        <span style="float: left" class="field-checkbox">
          <Checkbox v-model="allowOverwrites" input-id="allOverwrite" :binary="true" />
          <label for="allowOverwrite" class="ml-2">Overwrite?</label>
        </span>
        <Button label="Cancel" icon="pi pi-times" class="p-button-text" @click="cancel" />
        <Button :label="saveButtonLabel" icon="pi pi-check" :loading="saving" @click="onSaveClick" />
      </template>
    </Dialog>
    <Dialog :visible="displayBatchingDialog" :style="{ width: '30vw' }" :modal="true" :closable="false" :close-on-escape="false" :draggable="false" header="Annotating DeltaFiles">
      <div>
        <p v-if="!stopped">Annotation in progress.<span v-if="expectedTotalCount > 1"> If you navigate away, the operation will stop after the current batch.</span></p>
        <p v-else>{{ expectedTotalCount > 1 ? 'Completing current batch...' : 'Completing...' }}</p>
        <ProgressBar :value="batchCompleteValue" />
        <p class="mt-2 text-center">{{ totalProcessedCount.toLocaleString() }} / {{ expectedTotalCount.toLocaleString() }} DeltaFiles</p>
      </div>
      <template #footer>
        <Button label="Stop" icon="pi pi-stop" class="p-button-danger" :disabled="stopped" @click="stopBatching" />
      </template>
    </Dialog>
  </div>
</template>

<script setup>
import { ref, computed } from "vue";
import MapEdit from "@/components/plugin/MapEdit.vue";
import ProgressBar from "@/components/deprecatedPrimeVue/ProgressBar.vue";
import Dialog from "primevue/dialog";
import Button from "primevue/button";
import Checkbox from "primevue/checkbox";
import useAnnotate from "@/composables/useAnnotate";
import _ from "lodash";

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
});

const emit = defineEmits(["refreshPage"]);
const { annotate, annotateByFlow, annotateByMessage } = useAnnotate();
const metadata = ref(":");
const dialogVisible = ref(false);
const saving = ref(false);
const saveButtonLabel = computed(() => (saving.value ? "Saving" : "Save"));
const allowOverwrites = ref(false);
const displayBatchingDialog = ref(false);
const batchCompleteValue = ref(0);
const totalProcessedCount = ref(0);
const expectedTotalCount = ref(0);
const stopped = ref(false);
const batchSize = 200;

const isFlowMode = computed(() => props.flowInfo && props.flowInfo.length > 0);
const isMessageMode = computed(() => props.messageInfo && props.messageInfo.length > 0);

const parseMetadataToAnnotations = (metadataStr) => {
  const annotations = [];
  const pairs = metadataStr.split(',').map(s => s.trim()).filter(s => s);
  for (const pair of pairs) {
    const [key, value] = pair.split(':').map(s => s.trim());
    if (key && value !== undefined) {
      annotations.push({ key, value });
    }
  }
  return annotations;
};

const stopBatching = () => {
  stopped.value = true;
};

const onSaveClick = async () => {
  saving.value = true;
  stopped.value = false;

  try {
    if (isFlowMode.value) {
      const annotations = parseMetadataToAnnotations(metadata.value);
      const expectedTotal = _.sumBy(props.flowInfo, "count") || 0;
      let totalProcessed = 0;
      displayBatchingDialog.value = true;
      batchCompleteValue.value = 0;
      totalProcessedCount.value = 0;
      expectedTotalCount.value = expectedTotal;

      for (const flow of props.flowInfo) {
        let flowProcessed = 0;
        const flowExpected = flow.count || 0;

        while (!stopped.value && flowProcessed < flowExpected && totalProcessed < expectedTotal) {
          const count = await annotateByFlow(flow.flowType, flow.flowName, annotations, allowOverwrites.value, batchSize);
          if (count === 0) break;
          flowProcessed += count;
          totalProcessed += count;
          totalProcessedCount.value = totalProcessed;
          batchCompleteValue.value = Math.round((totalProcessed / expectedTotal) * 100);
        }
        if (stopped.value) break;
      }

      displayBatchingDialog.value = false;
      batchCompleteValue.value = 0;
    } else if (isMessageMode.value) {
      const annotations = parseMetadataToAnnotations(metadata.value);
      const expectedTotal = _.sumBy(props.messageInfo, "count") || 0;
      let totalProcessed = 0;
      displayBatchingDialog.value = true;
      batchCompleteValue.value = 0;
      totalProcessedCount.value = 0;
      expectedTotalCount.value = expectedTotal;

      for (const msg of props.messageInfo) {
        while (!stopped.value && totalProcessed < expectedTotal) {
          const count = await annotateByMessage(msg.errorCause, annotations, allowOverwrites.value, batchSize);
          if (count === 0) break;
          totalProcessed += count;
          totalProcessedCount.value = totalProcessed;
          batchCompleteValue.value = Math.round((totalProcessed / expectedTotal) * 100);
        }
        if (stopped.value) break;
      }

      displayBatchingDialog.value = false;
      batchCompleteValue.value = 0;
    } else {
      await annotate(props.dids, metadata.value, allowOverwrites.value);
    }
  } finally {
    dialogVisible.value = false;
    saving.value = false;
    emit("refreshPage");
  }
};

const cancel = () => {
  dialogVisible.value = false;
  metadata.value = ":";
};

const showDialog = () => {
  metadata.value = ":";
  dialogVisible.value = true;
};

defineExpose({
  showDialog,
});
</script>

<style>
.field-checkbox {
  display: flex;

  label {
    display: flex;
    align-items: center;
    margin-top: 0.15rem;
    margin-left: 0.4rem;
  }
}
</style>
