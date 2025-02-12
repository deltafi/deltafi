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
  <div class="flow-expected-annotations-viewer">
    <div class="btn-group">
      <div class="btn-group-vertical">
        <Chips id="expectedAnnotationsInputId" ref="expectedAnnotationsInputRef" v-model="readReceiptsList" :class="newAnnotationDataPresent ? 'p-invalid' : ''" />
        <small v-if="newAnnotationDataPresent" id="expectedAnnotationsInputId-input-help" class="mt-2">Press enter to add to the list</small>
      </div>
      <div>
        <ConfirmPopup />
        <ConfirmPopup :group="flowName">
          <template #message="slotProps">
            <div class="flex btn-group p-4">
              <i :class="slotProps.message.icon" style="font-size: 1.5rem" />
              <p class="pl-2" v-html="slotProps.message.message" />
            </div>
          </template>
        </ConfirmPopup>
        <Button label="Save" icon="pi pi-check" :disabled="submitDisabled" class="ml-2" @click="confirmationPopup($event)" />
      </div>
    </div>
  </div>
</template>

<script setup>
import useFlowQueryBuilder from "@/composables/useFlowQueryBuilder";
import useNotifications from "@/composables/useNotifications";
import { computed, reactive, ref } from "vue";
import _ from "lodash";

import Button from "primevue/button";
import Chips from "primevue/chips";
import ConfirmPopup from "primevue/confirmpopup";
import { useConfirm } from "primevue/useconfirm";

const props = defineProps({
  expectedAnnotations: {
    type: Object,
    required: false,
    default: null,
  },
  header: {
    type: String,
    required: true,
  },
  flowName: {
    type: String,
    required: true,
  },
  flowType: {
    type: String,
    required: true,
  },
});

const emit = defineEmits(["reloadFlowViewer"]);

const confirm = useConfirm();
const { setTransformFlowExpectedAnnotations, setDataSinkExpectedAnnotations } = useFlowQueryBuilder();
const notify = useNotifications();

const expectedAnnotationsInputRef = ref(null);
const { flowName, flowType, expectedAnnotations } = reactive(props);
const originalExpectedAnnotations = ref(_.cloneDeep(expectedAnnotations));
const readReceiptsList = ref(_.cloneDeep(expectedAnnotations));

// If originalExpectedAnnotations is null keep the value null else sort the list
const sortecOriginalExpectedAnnotations = computed(() => {
  return _.isNull(originalExpectedAnnotations.value) ? null : [..._.sortBy(originalExpectedAnnotations.value)];
});

// If readReceiptsList is null keep the value null else sort the list
const sortedReadReceiptsList = computed(() => {
  return _.isNull(readReceiptsList.value) ? null : [..._.sortBy(readReceiptsList.value)];
});

const submitDisabled = computed(() => {
  return _.isEqual(sortecOriginalExpectedAnnotations.value, sortedReadReceiptsList.value);
});

const confirmationPopup = (event) => {
  confirm.require({
    target: event.currentTarget,
    group: flowName,
    message: `Update the <b>${flowName}</b> data sink by ${compareAnnotationsArrays()}?`,
    acceptLabel: "Yes",
    rejectLabel: "Cancel",
    icon: "pi pi-exclamation-triangle",
    accept: () => {
      notify.info(`Updating Read Receipts`, `Updating Read Receipts for ${flowName}.`, 3000);
      submitNewReadReceipts();
    },
    reject: () => { },
  });
};

const newAnnotationDataPresent = computed(() => {
  return expectedAnnotationsInputRef.value?.$.data.inputValue;
});

const compareAnnotationsArrays = () => {
  let addedRecieptsList = null;
  let removedRecieptsList = null;
  // If the readReceiptsList is empty then all the originalExpectedAnnotations have been removed
  if (_.isEmpty(sortedReadReceiptsList.value)) {
    removedRecieptsList = sortecOriginalExpectedAnnotations.value;
  }

  // If the originalExpectedAnnotations is empty then all the readReceiptsList are newly added
  if (_.isEmpty(sortecOriginalExpectedAnnotations.value)) {
    addedRecieptsList = sortedReadReceiptsList.value;
  }

  let andString = "";
  // If both readReceiptsList and originalExpectedAnnotations have values sorting both arrays and getting the difference
  if (_.isEmpty(addedRecieptsList) && _.isEmpty(removedRecieptsList)) {
    addedRecieptsList = _.difference(sortedReadReceiptsList.value, sortecOriginalExpectedAnnotations.value);
    removedRecieptsList = _.difference(sortecOriginalExpectedAnnotations.value, sortedReadReceiptsList.value);
    andString = !_.isEmpty(addedRecieptsList) && !_.isEmpty(removedRecieptsList) ? " and " : "";
  }

  return `${!_.isEmpty(addedRecieptsList) ? `adding Read Receipts: ${addedRecieptsList.join(", ")}` : ""}` + andString + `${!_.isEmpty(removedRecieptsList) ? `removing Read Receipts: ${removedRecieptsList.join(", ")}` : ""}`;
};

const submitNewReadReceipts = async () => {
  let response = null;
  if (_.isEqual(flowType, "transform")) {
    response = await setTransformFlowExpectedAnnotations(flowName, sortedReadReceiptsList.value);
  } else if (_.isEqual(flowType, "dataSink")) {
    response = await setDataSinkExpectedAnnotations(flowName, sortedReadReceiptsList.value);
  }

  if (!_.isEmpty(_.get(response, "errors", null))) {
    notify.error(`Failed to update Read Receipts for data sink: ${flowName}`, `Unsuccessful in ${compareAnnotationsArrays()}.`, 4000);
  } else {
    notify.success(`Updated Read Receipts for data sink: ${flowName}`, `Successful in ${compareAnnotationsArrays()}.`, 4000);
  }

  originalExpectedAnnotations.value = sortedReadReceiptsList.value;
  emit("reloadFlowViewer");
};
</script>
