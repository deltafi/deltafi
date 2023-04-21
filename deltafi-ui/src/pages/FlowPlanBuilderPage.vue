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
  <div class="flow-plan-builder-page">
    <PageHeader heading="Flow Plan Builder" />
    <div v-if="!model.type" class="pb-2">
      <Dropdown v-model="model.type" :options="flowTypesDisplay" option-label="header" option-value="field" placeholder="Select Flow Type" show-clear @change="addFlow($event)" />
    </div>
    <div v-if="model.type" class="row mx-0">
      <div class="col pl-0 pr-1">
        <Panel header="Actions" class="table-panel">
          <div v-if="model.active" class="list-group-item px-2 pt-0">
            <div v-for="value of flowTypesMap.get(model.type).actions" :key="value">
              <h4 class="pt-2">{{ _.startCase(actionTemplateMap.get(value).activeContainer) }}:</h4>
              <draggable v-model="loadedActions[value]" item-key="name" :sort="false" :group="{ name: value, pull: 'clone', put: false }" :clone="cloneTransformActionsTemplate" class="list-group mb-0">
                <template #item="{ element }">
                  <div class="list-group-item h-100 d-flex w-100 justify-content-between">
                    <div class="btn-group">
                      <i :class="actionTemplateClass(element)" @click="addAction(value, element)"></i>
                      <div>{{ element.type }}</div>
                    </div>
                    <i v-if="!(_.findIndex(actionTemplateObject[actionTemplateMap.get(element.actionType).activeContainer], ['type', element.type]) == -1)" class="fa-solid fa-minus pr-2 pt-1" @click="removeAction(element, _.findIndex(actionTemplateObject[actionTemplateMap.get(element.actionType).activeContainer], element))"></i>
                  </div>
                </template>
              </draggable>
              <draggable v-model="actionTemplateMap.get(value).selectTemplate" item-key="id" :sort="false" :group="{ name: value, pull: 'clone', put: false }" :clone="cloneTransformActionsTemplate" class="list-group new-action-group-item">
                <template #item="{ element }">
                  <div class="list-group-item h-100 d-flex btn-group">
                    <i class="fa-solid fa-plus pr-2 pt-1" @click="addAction(value, element)"></i>
                    <div>New {{ _.capitalize(element.actionType) }} Action</div>
                  </div>
                </template>
              </draggable>
            </div>
          </div>
        </Panel>
      </div>
      <div class="col pl-1 pr-1">
        <Panel header="Flow Plan" class="table-panel">
          <div class="list-group-item px-2" style="overflow: scroll">
            <div class="d-flex w-100 justify-content-between">
              <h4>{{ _.capitalize(model.type) }} Flow</h4>
              <i class="fa-solid fa-xmark" @click="removeFlow()"></i>
            </div>
            <dl>
              <dt class="pb-2">Name</dt>
              <dd>
                <InputText v-model="model.name" class="inputWidth" />
              </dd>
              <dt class="pb-2">Description</dt>
              <dd>
                <InputText v-model="model.description" class="inputWidth" />
              </dd>
            </dl>
            <div class="list-group">
              <div v-for="action of flowTypesMap.get(model.type).actions" :key="action">
                <dt class="pb-2">{{ _.startCase(actionTemplateMap.get(action).activeContainer) }}:</dt>
                <div v-if="_.isEmpty(actionTemplateObject[actionTemplateMap.get(action).activeContainer])" class="list-group-item">No {{ _.startCase(actionTemplateMap.get(action).activeContainer) }}</div>
                <draggable v-model="actionTemplateObject[actionTemplateMap.get(action).activeContainer]" item-key="id" :sort="true" :group="action" ghost-class="ghost" class="dragArea list-group pb-2" @change="oneActionVerification">
                  <template #item="{ element, index }">
                    <div class="list-group-item">
                      <i class="fa-solid fa-xmark float-right" @click="removeAction(element, index)"></i>
                      <div v-for="displayActionInfo of getDisplayValues(element)" :key="displayActionInfo">
                        <template v-if="(_.isEqual(displayActionInfo, 'requiresDomains') && _.isEmpty(element[displayActionInfo])) || (_.isEqual(displayActionInfo, 'requiresEnrichments') && _.isEmpty(element[displayActionInfo])) || (_.isEqual(displayActionInfo, 'schema') && _.isEmpty(_.get(element, 'schema.properties', null)))"> </template>
                        <template v-else>
                          <dt class="pb-2">{{ displayMap.get(displayActionInfo).header }}</dt>
                          <dd>
                            <InputText v-if="_.isEqual(displayMap.get(displayActionInfo).type, 'string')" v-model="element[displayActionInfo]" class="inputWidth" :disabled="displayMap.get(displayActionInfo).disableEdit && element.disableEdit" />
                          </dd>
                          <dd v-if="_.isEqual(displayMap.get(displayActionInfo).type, 'object')">
                            <template v-if="Array.isArray(element[displayActionInfo])">
                              <template v-if="element[displayActionInfo].includes('any')">
                                <DialogTemplate component-name="plugin/ListEdit" :model-value="element[displayActionInfo]" :header="`Add New ${_.capitalize(action)}`" dialog-width="25vw">
                                  <div class="p-inputtext p-component">
                                    <div v-for="item in element[displayActionInfo]" :key="item" class="list-item">{{ item }}</div>
                                  </div>
                                </DialogTemplate>
                              </template>
                              <template v-else>
                                <div v-for="item in element[displayActionInfo]" :key="item" class="list-item">{{ item }}</div>
                              </template>
                            </template>
                            <template v-if="_.isEqual(displayActionInfo, 'schema')">
                              <json-forms :data="data" :renderers="renderers" :uischema="uischema" :schema="element['schema']" @change="onChange($event, element)" />
                            </template>
                          </dd>
                        </template>
                      </div>
                    </div>
                  </template>
                </draggable>
              </div>
            </div>
          </div>
        </Panel>
      </div>
      <div class="col pl-1 pr-0">
        <Panel header="Output" class="table-panel header-hight">
          <template #icons>
            <Button v-tooltip.left="'Copy to Clipboard'" class="p-panel-header-icon p-link p-me-2" @click="copy(rawOutput)">
              <span class="fa-solid fa-copy" />
            </Button>
            <!-- <Button v-tooltip.left="'Show Schema'" class="p-panel-header-icon p-link p-me-2" @click="showSchma()">
              <span class="fa-solid fa-file-invoice" />
            </Button> -->
          </template>
          <div class="list-group">
            <div class="list-group-item px-0 py-0">
              <pre class="textAreaWidth" style="text-align: start; white-space: pre-wrap; overflow: auto; border-bottom: none" v-html="prettyPrint(rawOutput)"></pre>
            </div>
          </div>
        </Panel>
      </div>
    </div>
  </div>
</template>

<script setup>
import DialogTemplate from "@/components/DialogTemplate.vue";
import PageHeader from "@/components/PageHeader.vue";
import useFlowActions from "@/composables/useFlowActions";
import usePrimeVueJsonSchemaUIRenderers from "@/composables/usePrimeVueJsonSchemaUIRenderers";
import { computed, onBeforeMount, provide, ref } from "vue";
import { useClipboard } from "@vueuse/core";

import Button from "primevue/button";
import Dropdown from "primevue/dropdown";
import InputText from "primevue/inputtext";
import Panel from "primevue/panel";
import draggable from "vuedraggable";

import _ from "lodash";

import { JsonForms } from "@jsonforms/vue";

const { rendererList, myStyles } = usePrimeVueJsonSchemaUIRenderers();

provide("style", myStyles);

const renderers = ref(Object.freeze(rendererList));

const uischema = ref(undefined);

const data = ref({});

const { getPluginActionSchema } = useFlowActions();
const { copy } = useClipboard();

const allActionsData = ref({});

const flowTemplate = {
  type: null,
  active: false,
  name: null,
  description: null,
};

const defaultActionKeys = {
  name: null,
  description: null,
  parameters: {},
  apiVersion: null,
};

const transformActionsTemplate = {
  actionType: "TRANSFORM",
  disableEdit: false,
  ...defaultActionKeys,
};

const joinActionTemplate = {
  actionType: "JOIN",
  disableEdit: false,
  ...defaultActionKeys,
};

const loadActionTemplate = {
  actionType: "LOAD",
  disableEdit: false,
  ...defaultActionKeys,
};

const domainActionsTemplate = {
  actionType: "DOMAIN",
  disableEdit: false,
  ...defaultActionKeys,
  requiresDomains: [],
};

const enrichActionsTemplate = {
  actionType: "ENRICH",
  disableEdit: false,
  ...defaultActionKeys,
  requiresDomains: [],
  requiresEnrichments: [],
  requiresMetadataKeyValues: [],
};

const formatActionTemplate = {
  actionType: "FORMAT",
  disableEdit: false,
  ...defaultActionKeys,
  requiresDomains: [],
  requiresEnrichments: [],
};

const validateActionsTemplate = {
  actionType: "VALIDATE",
  disableEdit: false,
  ...defaultActionKeys,
};

const egressActionTemplate = {
  actionType: "EGRESS",
  disableEdit: false,
  ...defaultActionKeys,
};

const flowPlan = ref(JSON.parse(JSON.stringify(flowTemplate)));

const flowTypesDisplay = [
  { header: "Transform", field: "TRANSFORM" },
  { header: "Ingress", field: "INGRESS" },
  { header: "Enrich", field: "ENRICH" },
  { header: "Egress", field: "EGRESS" },
];

const flowTypesMap = new Map([
  ["TRANSFORM", { actions: ["TRANSFORM", "EGRESS"] }],
  ["INGRESS", { actions: ["TRANSFORM", "JOIN", "LOAD"] }],
  ["ENRICH", { actions: ["DOMAIN", "ENRICH"] }],
  ["EGRESS", { actions: ["FORMAT", "VALIDATE", "EGRESS"] }],
]);

const actionTemplateMap = new Map([
  ["TRANSFORM", { actionTemplate: transformActionsTemplate, selectTemplate: [transformActionsTemplate], activeContainer: "transformActions", limit: false }],
  ["JOIN", { actionTemplate: joinActionTemplate, selectTemplate: [joinActionTemplate], activeContainer: "joinAction", limit: true }],
  ["LOAD", { actionTemplate: loadActionTemplate, selectTemplate: [loadActionTemplate], activeContainer: "loadAction", limit: true }],
  ["DOMAIN", { actionTemplate: domainActionsTemplate, selectTemplate: [domainActionsTemplate], activeContainer: "domainActions", limit: false }],
  ["ENRICH", { actionTemplate: enrichActionsTemplate, selectTemplate: [enrichActionsTemplate], activeContainer: "enrichActions", limit: false }],
  ["FORMAT", { actionTemplate: formatActionTemplate, selectTemplate: [formatActionTemplate], activeContainer: "formatAction", limit: true }],
  ["VALIDATE", { actionTemplate: validateActionsTemplate, selectTemplate: [validateActionsTemplate], activeContainer: "validateActions", limit: false }],
  ["EGRESS", { actionTemplate: egressActionTemplate, selectTemplate: [egressActionTemplate], activeContainer: "egressAction", limit: true }],
]);

const actionTemplateObject = ref({
  transformActions: [],
  joinAction: [],
  loadAction: [],
  domainActions: [],
  enrichActions: [],
  formatAction: [],
  validateActions: [],
  egressAction: [],
});

const loadedActions = ref({
  TRANSFORM: [],
  JOIN: [],
  LOAD: [],
  DOMAIN: [],
  ENRICH: [],
  FORMAT: [],
  VALIDATE: [],
  EGRESS: [],
});

const originalActionTemplateObject = JSON.parse(JSON.stringify(actionTemplateObject.value));

const displayKeysList = ["name", "type", "description", "schema", "requiresDomains", "requiresEnrichments", "requiresMetadataKeyValues"];

const displayMap = new Map([
  ["name", { header: "Name", type: "string", disableEdit: false }],
  ["type", { header: "Type", type: "string", disableEdit: true }],
  ["description", { header: "Description", type: "string", disableEdit: true }],
  ["schema", { header: "Parameters", type: "object", disableEdit: false }],
  ["requiresDomains", { header: "Requires Domains", type: "object", disableEdit: false }],
  ["requiresEnrichments", { header: "Requires Enrichments", type: "object", disableEdit: false }],
  ["requiresMetadataKeyValues", { header: "Requires Metadata Key Values", type: "object", disableEdit: false }],
]);

const schemaVisable = ref(false);

onBeforeMount(async () => {
  await fetchData();
});

const fetchData = async () => {
  let responseFlowAction = await getPluginActionSchema();
  allActionsData.value = responseFlowAction.data.plugins;
  getloadedActions();
};

const model = computed({
  get() {
    return new Proxy(flowPlan.value, {
      set(obj, key, value) {
        model.value = { ...obj, [key]: value };
        return true;
      },
    });
  },
  set(newValue) {
    Object.assign(
      flowPlan.value,
      _.mapValues(newValue, (v) => (v === "" ? null : v))
    );
  },
});

const addFlow = (flowType) => {
  removeFlow();
  model.value.type = flowType.value;
  model.value.active = flowType.value ? true : false;
};

const removeFlow = () => {
  model.value = JSON.parse(JSON.stringify(flowTemplate));
  actionTemplateObject.value = JSON.parse(JSON.stringify(originalActionTemplateObject));
};

const addAction = (actionType, action) => {
  if (actionTemplateMap.get(actionType).limit) {
    if (actionTemplateObject.value[actionTemplateMap.get(actionType).activeContainer].length > 0) {
      return;
    } else {
      actionTemplateObject.value[actionTemplateMap.get(actionType).activeContainer].push(JSON.parse(JSON.stringify(action)));
    }
  } else {
    actionTemplateObject.value[actionTemplateMap.get(actionType).activeContainer].push(JSON.parse(JSON.stringify(action)));
  }
};

const removeAction = (removeAction, index) => {
  actionTemplateObject.value[actionTemplateMap.get(removeAction.actionType).activeContainer].splice(index, 1);
};

const cloneTransformActionsTemplate = (event) => {
  return JSON.parse(JSON.stringify(event));
};

const oneActionVerification = (event) => {
  let addedEvent = _.get(event, "added");

  if (!_.isEmpty(addedEvent)) {
    let action = addedEvent.element.actionType;
    if (actionTemplateMap.get(action).limit) {
      if (actionTemplateObject.value[actionTemplateMap.get(action).activeContainer].length > 1) {
        actionTemplateObject.value[actionTemplateMap.get(action).activeContainer].pop();
      }
    }
  }
};

const getloadedActions = () => {
  for (const plugins of allActionsData.value) {
    for (const actions of plugins["actions"]) {
      actions["disableEdit"] = true;
      actions["actionType"] = actions["type"];
      actions["type"] = actions["name"];
      actions["name"] = "";
      actions["parameters"] = null;

      loadedActions.value[actions.actionType].push(actions);
    }
  }
};

const getDisplayValues = (obj) => {
  return _.intersection(Object.keys(obj), displayKeysList);
};

const rawOutput = computed(() => {
  let displayOutput = JSON.parse(JSON.stringify(model.value));

  for (const action of flowTypesMap.get(model.value.type).actions) {
    if (!_.isEmpty(actionTemplateObject.value[actionTemplateMap.get(action).activeContainer])) {
      displayOutput[actionTemplateMap.get(action).activeContainer] = JSON.parse(JSON.stringify(actionTemplateObject.value[actionTemplateMap.get(action).activeContainer]));
      if (schemaVisable.value) {
        displayOutput[actionTemplateMap.get(action).activeContainer] = displayOutput[actionTemplateMap.get(action).activeContainer].map(({ description, actionType, disableEdit, ...keepAttrs }) => keepAttrs); // eslint-disable-line @typescript-eslint/no-unused-vars
      } else {
        displayOutput[actionTemplateMap.get(action).activeContainer] = displayOutput[actionTemplateMap.get(action).activeContainer].map(({ schema, description, actionType, disableEdit, ...keepAttrs }) => keepAttrs); // eslint-disable-line @typescript-eslint/no-unused-vars
      }
    }
  }

  // Remove UI metakeys from output
  displayOutput = _.omit(displayOutput, ["active", "flowType"]);

  return displayOutput;
});

const prettyPrint = (json) => {
  if (json) {
    const stringified = JSON.stringify(json, null, 2);

    const stringifiedReplaced = stringified.replace(/&/g, "&").replace(/</g, "<").replace(/>/g, ">");
    const regex = /("(\\u[a-zA-Z0-9]{4}|\\[^u]|[^\\"])*"(\s*:)?|\b(true|false|null)\b|-?\d+(?:\.\d*)?(?:[eE][+-]?\d+)?)/g;

    let test = stringifiedReplaced.replace(regex, (match) => {
      let className = "number";
      if (/^"/.test(match)) {
        if (/:$/.test(match)) {
          className = "key";
        } else {
          className = "string";
        }
      } else if (/true|false/.test(match)) {
        className = "boolean";
      } else if (/null/.test(match)) {
        className = "null";
      }

      return `<span class="${className}">${match}</span>`;
    });

    return test;
  }

  return "";
};

// const showSchma = () => {
//   schemaVisable.value = !schemaVisable.value;
// };

const onChange = (event, element) => {
  element["parameters"] = event.data;
};

const actionTemplateClass = (element) => {
  return ["fa-solid", "pr-2", "pt-1", "fa-plus", { "added-action-color": !(_.findIndex(actionTemplateObject.value[actionTemplateMap.get(element.actionType).activeContainer], element) == -1) }];
};
</script>

<style lang="scss">
@import "@/styles/pages/flow-plan-builder-page.scss";
</style>
