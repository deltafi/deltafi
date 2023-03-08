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
  <TabView ref="tabview1" class="flow-viewer">
    <TabPanel header="Flow Actions">
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
                            <dd v-if="_.isEqual(actionInfoKey, 'parameters')" class="d-flex">
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
                    <div class="row mx-0 pt-2">
                      <div class="col-12">
                        <dl>
                          <dt>Metrics</dt>
                          <dd>
                            <ActionMetricsTable :actions="actionMetricsUngrouped" :loading="!loaded" class="px-0 pt-1" :filter-type="null" :filter-by="flowActionListValue.name" :hidden-column="true" />
                          </dd>
                        </dl>
                      </div>
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
                          <dd v-if="_.isEqual(actionInfoKey, 'parameters')" class="d-flex">
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
                  <div class="row mx-0 pt-2">
                    <div class="col-12">
                      <dl>
                        <dt>Metrics</dt>
                        <dd>
                          <ActionMetricsTable :actions="actionMetricsUngrouped" :loading="!loaded" class="px-0 pt-1" :filter-type="null" :filter-by="flowAction.name" :hidden-column="true" />
                        </dd>
                      </dl>
                    </div>
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
        </template>
      </div>
    </TabPanel>
    <template v-if="!_.isEmpty(variables)">
      <TabPanel header="Flow Variables">
        <FlowVariableViewer :header="header" :variables="variables"></FlowVariableViewer>
      </TabPanel>
    </template>
  </TabView>
</template>

<script setup>
import ActionMetricsTable from "@/components/ActionMetricsTable.vue";
import CollapsiblePanel from "@/components/CollapsiblePanel.vue";
import FlowVariableViewer from "@/components/flow/FlowVariableViewer.vue";
import useActionMetrics from "@/composables/useActionMetrics";
import useFlowQueryBuilder from "@/composables/useFlowQueryBuilder";
import { computed, defineExpose, defineProps, inject, onBeforeMount, onUnmounted, reactive, ref } from "vue";
import useUtilFunctions from "@/composables/useUtilFunctions";

import Divider from "primevue/divider";
import Message from "primevue/message";
import TabPanel from "primevue/tabpanel";
import TabView from "primevue/tabview";

import _ from "lodash";

const hasPermission = inject("hasPermission");
const isIdle = inject("isIdle");
const { buildURL } = useUtilFunctions();

const { fetch: getActionMetrics, loaded, loading, actionMetricsUngrouped } = useActionMetrics();

const { getEgressFlowByName, getEnrichFlowByName, getIngressFlowByName } = useFlowQueryBuilder();

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
  variables: {
    type: Object,
    required: false,
    default: null,
  },
});

const { header, flowName, flowType, variables } = reactive(props);

const actionsList = ["transformActions", "loadAction", "deleteActions", "domainActions", "enrichActions", "formatAction", "validateActions", "egressAction"];

const refreshInterval = 5000; // 5 seconds
const flowData = ref("");
const dialogVisible = ref(false);
const ingressFlowNameSelected = ref(null);

let autoRefresh = null;

onUnmounted(() => {
  clearInterval(autoRefresh);
});

onBeforeMount(async () => {
  fetchFlows(flowName, flowType);
  await fetchActionMetrics();
  autoRefresh = setInterval(fetchActionMetrics, refreshInterval);
});
const showDialog = () => {
  dialogVisible.value = true;
};

defineExpose({
  showDialog,
});

const fetchActionMetrics = async () => {
  if (!isIdle.value && !loading.value) {
    let actionMetricsParams = { last: "5m" };
    if (ingressFlowNameSelected.value) {
      actionMetricsParams["flowName"] = ingressFlowNameSelected.value;
    }
    await getActionMetrics(actionMetricsParams);
  }
};

const fetchFlows = async (paramFlowName, paramFlowType) => {
  let response = "";
  if (_.isEqual(paramFlowType, "ingress")) {
    response = await getIngressFlowByName(paramFlowName);
    flowData.value = response.data.getIngressFlow;
  } else if (_.isEqual(paramFlowType, "enrich")) {
    response = await getEnrichFlowByName(paramFlowName);
    flowData.value = response.data.getEnrichFlow;
  } else if (_.isEqual(paramFlowType, "egress")) {
    response = await getEgressFlowByName(paramFlowName);
    flowData.value = response.data.getEgressFlow;
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
