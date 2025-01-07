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
  <div class="flow-viewer">
    <dl>
      <dt class="pb-1">Description</dt>
      <dd>
        {{ flowData.description }}
      </dd>
    </dl>
    <TabView ref="tabview1" class="pt-2">
      <TabPanel header="Actions">
        <div v-if="!_.isEmpty(_.get(flowData, 'flowStatus.errors'))" class="pt-2">
          <Message severity="error" :closable="false" class="mb-2 mt-0">
            <ul>
              <div v-for="(error, errorKey) in flowData.flowStatus.errors" :key="errorKey">
                <li class="text-wrap text-break">{{ error.message }}</li>
              </div>
            </ul>
          </Message>
        </div>
        <div class="flow-viewer">
          <template v-for="(flowAction, key, index) of flowActions" :key="key">
            <template v-if="!_.isEmpty(flowAction)">
              <CollapsiblePanel :header="panelHeader(key)" class="flow-viewer-panel">
                <div>
                  <template v-if="_.isArray(flowAction)">
                    <template v-for="(flowActionListValue, flowActionListKey) in flowAction" :key="flowActionListKey">
                      <div class="row mx-0 pt-2">
                        <template v-for="(value, actionInfoKey) in flowActionListValue" :key="actionInfoKey">
                          <div v-if="!_.isEmpty(value)" class="col-6 pb-0">
                            <dl>
                              <dt>{{ _.startCase(actionInfoKey) }}</dt>
                              <dd v-if="['parameters', 'join'].includes(actionInfoKey)" class="d-flex">
                                <ul>
                                  <li v-for="(pVal, pKey) in value" :key="pKey">{{ pKey }}: {{ pVal }}</li>
                                </ul>
                              </dd>
                              <dd v-else class="d-flex">
                                <div>{{ _.isArray(value) ? Array.from(value).join(", ") : value }}</div>
                                <template v-if="_.isEqual(actionInfoKey, 'name')">
                                  <a v-tooltip.top="`View logs`" :class="grafanaLogLink" style="color: black" :href="actionLogLink(value)" target="_blank" rel="noopener noreferrer">
                                    <i class="ml-1 text-muted fa-regular fa-chart-bar" />
                                  </a>
                                </template>
                              </dd>
                            </dl>
                          </div>
                        </template>
                      </div>
                      <template v-if="_.findIndex(flowAction, flowActionListValue) + 1 < Object.keys(flowAction).length">
                        <Divider />
                      </template>
                    </template>
                  </template>
                  <template v-else>
                    <div class="row mx-0 pt-2">
                      <template v-for="(value, actionInfoKey) in flowAction" :key="actionInfoKey">
                        <div v-if="!_.isEmpty(value)" class="col-6 pb-0">
                          <dl>
                            <dt>{{ _.startCase(actionInfoKey) }}</dt>
                            <dd v-if="['parameters', 'join'].includes(actionInfoKey)" class="d-flex">
                              <ul>
                                <li v-for="(pVal, pKey) in value" :key="pKey">{{ pKey }}: {{ pVal }}</li>
                              </ul>
                            </dd>
                            <dd v-else class="d-flex">
                              <div>{{ _.isArray(value) ? Array.from(value).join(", ") : value }}</div>
                              <template v-if="_.isEqual(actionInfoKey, 'name')">
                                <a v-tooltip.top="`View logs`" :class="grafanaLogLink" style="color: black" :href="actionLogLink(value)" target="_blank" rel="noopener noreferrer">
                                  <i class="ml-1 text-muted fa-regular fa-chart-bar" />
                                </a>
                              </template>
                            </dd>
                          </dl>
                        </div>
                      </template>
                    </div>
                  </template>
                </div>
              </CollapsiblePanel>
              <template v-if="index + 1 < Object.keys(flowActions).length && !_.isEmpty(Object.values(flowActions)[index + 1])">
                <div class="text-center pb-2">
                  <i class="fas fa-arrow-down fa-4x" />
                </div>
              </template>
            </template>
            <span v-else>No actions to display.</span>
          </template>
        </div>
      </TabPanel>
      <template v-if="!_.isEmpty(flowData?.variables)">
        <TabPanel header="Flow Variables">
          <FlowVariableViewer :header="header" :variables="flowData?.variables"></FlowVariableViewer>
        </TabPanel>
      </template>
      <TabPanel header="Subscribe">
        <SubscribeCell v-if="!_.isEmpty(flowData?.subscribe)" :subscribe-data="flowData?.subscribe" />
        <a v-else>No subscription information to display.</a>
      </TabPanel>
      <template v-if="['dataSink'].includes(flowType)">
        <TabPanel header="Read Receipts">
          <FlowExpectedAnnotationsViewer :key="Math.random()" :header="header" :expected-annotations="expectedAnnotations" :flow-name="flowName" :flow-type="flowType" @reload-flow-viewer="fetchFlows(flowName, flowType)"></FlowExpectedAnnotationsViewer>
        </TabPanel>
      </template>
      <template v-else>
        <TabPanel header="Publish">
          <PublishCell v-if="!_.isEmpty(flowData?.publish)" :publish-data="flowData?.publish" />
          <a v-else>No publish information to display.</a>
        </TabPanel>
      </template>
    </TabView>
  </div>
</template>

<script setup>
import CollapsiblePanel from "@/components/CollapsiblePanel.vue";
import FlowExpectedAnnotationsViewer from "@/components/flow/FlowExpectedAnnotationsViewer.vue";
import FlowVariableViewer from "@/components/flow/FlowVariableViewer.vue";
import useFlowQueryBuilder from "@/composables/useFlowQueryBuilder";
import { computed, defineProps, inject, onBeforeMount, reactive, ref } from "vue";
import useUtilFunctions from "@/composables/useUtilFunctions";
import PublishCell from "@/components/PublishCell.vue";
import SubscribeCell from "@/components/SubscribeCell.vue";

import Divider from "primevue/divider";
import Message from "primevue/message";
import TabPanel from "primevue/tabpanel";
import TabView from "primevue/tabview";

import _ from "lodash";

const hasPermission = inject("hasPermission");
const { buildURL } = useUtilFunctions();

const { getDataSinkByName, getTransformFlowByName } = useFlowQueryBuilder();

const props = defineProps({
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

const { header, flowName, flowType } = reactive(props);
const actionsList = ["transformActions", "egressAction"];

const flowData = ref("");

onBeforeMount(async () => {
  await fetchFlows(flowName, flowType);
});

const fetchFlows = async (paramFlowName, paramFlowType) => {
  let response = "";
  if (_.isEqual(paramFlowType, "transform")) {
    response = await getTransformFlowByName(paramFlowName);
    flowData.value = response.data.getTransformFlow;
  } else if (_.isEqual(paramFlowType, "dataSink")) {
    response = await getDataSinkByName(paramFlowName);
    flowData.value = response.data.getDataSink;
  }
};

const flowActions = computed(() => {
  return _.pick(flowData.value, actionsList);
});

const grafanaLogLink = computed(() => {
  return [
    "cursor-pointer pl-1",
    {
      "disable-grafana-link": !hasPermission("MetricsView"),
    },
  ];
});

const expectedAnnotations = computed(() => {
  return flowData.value.expectedAnnotations ? flowData.value.expectedAnnotations : null;
});

const panelHeader = (actionType) => {
  const words = actionType.replace(/([A-Z])/g, " $1");
  return words.charAt(0).toUpperCase() + words.slice(1);
};

const actionLogLink = (actionNameForLink) => {
  return buildURL("metrics", `/d/action-log-viewer/action-log-viewer?var-datasource=Loki&var-searchable_pattern=&var-action_name=${actionNameForLink}`);
};
</script>

<style lang="scss">
@import "@/styles/components/flow/flow-viewer.scss";
</style>