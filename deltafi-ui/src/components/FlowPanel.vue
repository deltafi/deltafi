<!--
   DeltaFi - Data transformation and enrichment platform

   Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>

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
  <Panel v-show="flowData.visible" :header="flowData.name" class="pb-3 panel-header-layout">
    <template #icons>
      <div class="row justify-content-end">
        <div class="px-1">
          <template v-if="!_.isEmpty(flowData.variables)">
            <FlowVariableViewer :header="flowData.name" :variables="flowData.variables">
              <Button v-tooltip.top.hover="'View Variables for ' + flowData.name" icon="fas fa-table" class="p-button p-button-sm p-button-text p-button-secondary variables-button" />
            </FlowVariableViewer>
          </template>
        </div>
        <div class="pl-1 pr-3">
          <template v-if="!_.isEmpty(flowData.flowStatus.errors)">
            <Button v-tooltip.left="'Rerun validation on ' + flowData.name" icon="fa fa-sync-alt" label="Validate" class="p-button p-button-sm p-button-warning validate-button-padding" @click="validationRetry(flowData.name, flowData.flowType)" />
          </template>
          <template v-else>
            <InputSwitch v-model="flowData.flowStatus.state" v-tooltip.top="flowData.flowStatus.state" false-value="STOPPED" true-value="RUNNING" class="p-button-sm" @click="toggleFlowState(flowData.name, flowData.flowStatus.state, flowData.flowType)" />
          </template>
        </div>
      </div>
    </template>
    {{ flowData.description }}
    <div v-if="!_.isEmpty(flowData.flowStatus.errors)" class="pt-2">
      <Message severity="error" :closable="false" class="mb-0 mt-0">
        <ul>
          <div v-for="(error, errorKey) in flowData.flowStatus.errors" :key="errorKey">
            <li class="text-wrap text-break">{{ error.message }}</li>
          </div>
        </ul>
      </Message>
    </div>
    <Divider />
    <span>
      Source Plugin:
      <router-link class="monospace" :to="{ path: 'plugins/' + flowData.mvnCoordinates }">{{ flowData.mvnCoordinates }}</router-link>
    </span>
  </Panel>
</template>

<script setup>
import FlowVariableViewer from "@/components/FlowVariableViewer.vue";
import useFlowQueryBuilder from "@/composables/useFlowQueryBuilder";
import { defineProps, nextTick, reactive } from "vue";

import Button from "primevue/button";
import Divider from "primevue/divider";
import InputSwitch from "primevue/inputswitch";
import Message from "primevue/message";
import Panel from "primevue/panel";
import _ from "lodash";

const { startIngressFlowByName, stopIngressFlowByName, startEnrichFlowByName, stopEnrichFlowByName, startEgressFlowByName, stopEgressFlowByName, validateIngressFlow, validateEnrichFlow, validateEgressFlow } = useFlowQueryBuilder();

const props = defineProps({
  flowDataProp: {
    type: Object,
    required: true,
  },
});

const flowData = reactive(props.flowDataProp);

const validationRetry = async (flowName, flowType) => {
  let validatedFlowStatus = {};
  if (_.isEqual(flowType, "ingress")) {
    let response = await validateIngressFlow(flowName);
    validatedFlowStatus = response.data.validateIngressFlow;
  } else if (_.isEqual(flowType, "enrich")) {
    let response = await validateEnrichFlow(flowName);
    validatedFlowStatus = response.data.validateEnrichFlow;
  } else if (_.isEqual(flowType, "egress")) {
    let response = await validateEgressFlow(flowName);
    validatedFlowStatus = response.data.validateEgressFlow;
  }
  await nextTick();
  Object.assign(flowData, { ...flowData, ...validatedFlowStatus });
};

const toggleFlowState = async (flowName, newflowState, flowType) => {
  if (_.isEqual(flowType, "ingress")) {
    if (_.isEqual(newflowState, "STOPPED")) {
      await startIngressFlowByName(flowName);
    } else {
      await stopIngressFlowByName(flowName);
    }
  } else if (_.isEqual(flowType, "enrich")) {
    if (_.isEqual(newflowState, "STOPPED")) {
      await startEnrichFlowByName(flowName);
    } else {
      await stopEnrichFlowByName(flowName);
    }
  } else if (_.isEqual(flowType, "egress")) {
    if (_.isEqual(newflowState, "STOPPED")) {
      await startEgressFlowByName(flowName);
    } else {
      await stopEgressFlowByName(flowName);
    }
  }
};
</script>

<style lang="scss">
@import "@/styles/components/flow-panel.scss";
</style>