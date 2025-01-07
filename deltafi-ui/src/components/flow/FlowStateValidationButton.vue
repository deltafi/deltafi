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
  <span v-if="$hasPermission('FlowValidate')">
    <Button v-tooltip.left="'Rerun validation on ' + rowData.name" icon="fa fa-sync-alt" label="Revalidate" class="p-button p-button-sm p-button-warning validate-button-padding" @click="validationRetry(rowData.name, rowData.flowType)" />
  </span>
  <span v-else>
    <Button label="ERROR" class="p-button-danger" style="width: 5.5rem" disabled />
  </span>
</template>

<script setup>
import useFlowQueryBuilder from "@/composables/useFlowQueryBuilder";
import { defineProps, nextTick, toRefs, defineEmits } from "vue";

import Button from "primevue/button";
import _ from "lodash";

const { validateTransformFlow, validateDataSink } = useFlowQueryBuilder();
const emit = defineEmits(['updateFlows'])

const props = defineProps({
  rowDataProp: {
    type: Object,
    required: true,
  },
});

const { rowDataProp: rowData } = toRefs(props);

const validationRetry = async (flowName, flowType) => {
  let validatedFlowStatus = {};
  if (_.isEqual(flowType, "transform")) {
    let response = await validateTransformFlow(flowName);
    validatedFlowStatus = response.data.validateTransformFlow;
  } else if (_.isEqual(flowType, "dataSink")) {
    let response = await validateDataSink(flowName);
    validatedFlowStatus = response.data.validateDataSink;
  }
  await nextTick();
  Object.assign(rowData.value, { ...rowData.value, ...validatedFlowStatus });
  emit('updateFlows');
};
</script>
