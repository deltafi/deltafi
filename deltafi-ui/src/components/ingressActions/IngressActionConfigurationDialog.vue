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
  <div id="error-message" class="ingress-action-configuration-dialog">
    <div class="ingress-action-panel">
      <div class="pb-0">
        <div v-if="!_.isEmpty(errors)" class="pt-2">
          <Message severity="error" :sticky="true" class="mb-2 mt-0" @close="clearErrors()">
            <ul>
              <div v-for="(error, key) in errors" :key="key">
                <li class="text-wrap text-break">{{ error }}</li>
              </div>
            </ul>
          </Message>
        </div>
        <dl>
          <dt>Name*</dt>
          <dd>
            <InputText v-model="model['name']" placeholder="Ingress action name." :disabled="editIngressAction" class="inputWidth" />
          </dd>
          <dt>Description*</dt>
          <dd>
            <TextArea v-model="model['description']" placeholder="Ingress action description." class="inputWidth" rows="5" />
          </dd>
          <dt>Target Flow</dt>
          <dd>
            <Dropdown v-model="model['targetFlow']" :options="ingressFlowNames" placeholder="Select a flow" show-clear class="inputWidth" />
          </dd>
          <dt>Duration*</dt>
          <dd>
            <InputText v-model="model['cronSchedule']" placeholder="e.g.	*/5 * * * * *" class="inputWidth" />
          </dd>
          <dt>Enabled</dt>
          <dd>
            <StateInputSwitch :row-data-prop="model" ingress-action-type="timedIngress" :configure-ingress-action-dialog="true" />
          </dd>
          <dt>Ingress Action*</dt>
          <dd>
            <Dropdown v-model="model['timedIngressActionOption']" :options="flattenedActionsTypes" option-label="name" placeholder="Select a flow" show-clear class="inputWidth" />
          </dd>
          <template v-if="!_.isEmpty(model['timedIngressActionOption'])">
            <template v-if="schemaProvided(model['timedIngressActionOption']['schema'])">
              <dt>Ingress Action Variables</dt>
              <dd>
                <div class="deltafi-fieldset">
                  <div class="px-2 pt-3">
                    <json-forms :data="model['timedIngressActionOption']['parameters']" :renderers="renderers" :uischema="parametersSchema" :schema="model['timedIngressActionOption']['schema']" :ajv="handleDefaultsAjv" @change="onParametersChange($event, model)" />
                  </div>
                </div>
              </dd>
            </template>
            <dd v-else>
              <div class="px-2">
                <Message severity="info" :closable="false">No Ingress Action variables available</Message>
              </div>
            </dd>
          </template>
          <dt>Publish Rules</dt>
          <template v-if="!_.isEmpty(model['publishRulesOption'])">
            <dd>
              <div class="deltafi-fieldset">
                <div class="px-2 pt-3">
                  <json-forms :data="model['publishRulesOption']" :renderers="renderers" :uischema="publishRulesUISchema" :schema="publishRulesSchema" :ajv="handleDefaultsAjv" @change="onPublishRulesChange($event, rowData)" />
                </div>
              </div>
            </dd>
          </template>
          <template v-else>
            <dd>
              <div class="px-2">
                <Message severity="info" :closable="false">No publishRules</Message>
              </div>
            </dd>
          </template>
        </dl>
      </div>
    </div>
    <teleport v-if="isMounted" to="#dialogTemplate">
      <div class="p-dialog-footer">
        <Button label="Submit" @click="submit()" />
      </div>
    </teleport>
  </div>
</template>

<script setup>
import StateInputSwitch from "@/components/ingressActions/StateInputSwitch.vue";
import useFlowActions from "@/composables/useFlowActions";
import useFlows from "@/composables/useFlows";
import useIngressActions from "@/composables/useIngressActions";
import { useMounted } from "@vueuse/core";
import { computed, defineEmits, defineProps, inject, nextTick, onMounted, onBeforeMount, onUnmounted, provide, reactive, ref } from "vue";

import usePrimeVueJsonSchemaUIRenderers from "@/composables/usePrimeVueJsonSchemaUIRenderers";
import { JsonForms } from "@jsonforms/vue";
import { createAjv } from "@jsonforms/core";

const handleDefaultsAjv = createAjv({ useDefaults: true });

import Button from "primevue/button";
import Dropdown from "primevue/dropdown";
import InputText from "primevue/inputtext";
import Message from "primevue/message";
import TextArea from "primevue/textarea";
import _ from "lodash";

const editing = inject("isEditing");

const props = defineProps({
  rowDataProp: {
    type: Object,
    required: false,
    default: null,
  },
  editIngressAction: {
    type: Boolean,
    required: false,
    default: false,
  },
  closeDialogCommand: {
    type: Object,
    default: null,
  },
});

const { editIngressAction, closeDialogCommand } = reactive(props);
const emit = defineEmits(["reloadIngressActions"]);
const { getPluginActionSchema } = useFlowActions();
const { ingressFlows: ingressFlowNames, fetchIngressFlowNames } = useFlows();
const { getAllTimedIngress, saveTimedIngressFlowPlan } = useIngressActions();

const { rendererList, myStyles } = usePrimeVueJsonSchemaUIRenderers();
provide("style", myStyles);
const renderers = ref(Object.freeze(rendererList));
const parametersSchema = ref(undefined);
const publishRulesUISchema = ref(undefined);

const errors = ref([]);

const allActionsData = ref({});

const timedIngressActions = ref([]);

const ingressActionTemplate = {
  name: null,
  type: "TIMED_INGRESS",
  description: null,
  targetFlow: null,
  cronSchedule: null,
  flowStatus: {
    state: "STOPPED",
  },
  timedIngressActionOption: null,
  timedIngressAction: {
    apiVersion: null,
    name: null,
    parameters: null,
    type: null,
  },
  variables: null,
  publishRules: {
    defaultRule: {
      defaultBehavior: "ERROR",
      topic: null,
    },
    matchingPolicy: "ALL_MATCHING",
    rules: null,
  },
  publishRulesOption: null,
};

const rowData = ref(_.cloneDeepWith(props.rowDataProp || ingressActionTemplate));

const model = computed({
  get() {
    return new Proxy(rowData.value, {
      set(obj, key, value) {
        model.value = { ...obj, [key]: value };
        return true;
      },
    });
  },
  set(newValue) {
    Object.assign(
      rowData.value,
      _.mapValues(newValue, (v) => (v === "" ? null : v))
    );
  },
});

const flattenedActionsTypes = ref([]);

const getTimedIngressActions = async () => {
  let tmpFlattenedActionsTypes = [];
  for (const plugins of allActionsData.value) {
    for (const action of plugins["actions"]) {
      if (!_.isEqual(action.type, "TIMED_INGRESS")) continue;

      // Reformatting each action.
      action["type"] = action["name"];
      let name = action.name.split(".").pop();
      action["name"] = name;
      action["parameters"] = {};

      tmpFlattenedActionsTypes.push(action);
    }
  }
  return tmpFlattenedActionsTypes;
};

const isMounted = ref(useMounted());

onBeforeMount(async () => {
  await fetchIngressFlowNames();
  let responseFlowAction = await getPluginActionSchema();
  allActionsData.value = responseFlowAction.data.plugins;

  const response = await getAllTimedIngress();
  timedIngressActions.value = response.data.getAllFlows.timedIngress;

  flattenedActionsTypes.value = await getTimedIngressActions();

  if (!_.isEmpty(model.value["timedIngressAction"]["name"])) {
    let tmpName = model.value["timedIngressAction"]["name"];
    model.value["timedIngressActionOption"] = _.find(flattenedActionsTypes.value, { type: model.value["timedIngressAction"]["type"] });
    model.value["timedIngressActionOption"]["name"] = tmpName;
    if (!_.isEmpty(model.value["timedIngressAction"]["parameters"])) {
      model.value["timedIngressActionOption"]["parameters"] = model.value["timedIngressAction"]["parameters"];
    }
  }

  model.value["publishRules"] = _.get(model.value, "publishRules", null) || ingressActionTemplate["publishRules"];
  model.value["publishRulesOption"] = _.cloneDeepWith(model.value["publishRules"]);
});

onMounted(async () => {
  editing.value = true;
});

onUnmounted(() => {
  editing.value = false;
});

const onParametersChange = (event, element) => {
  element["timedIngressActionOption"]["parameters"] = event.data;
};

const onPublishRulesChange = (event, element) => {
  element["publishRulesOption"] = event.data;
};

const schemaProvided = (schema) => {
  return !_.isEmpty(_.get(schema, "properties", null));
};

const removeEmptyKeyValues = (queryObj) => {
  const newObj = {};
  Object.entries(queryObj).forEach(([k, v]) => {
    if (v instanceof Array) {
      newObj[k] = queryObj[k];
    } else if (v === Object(v)) {
      newObj[k] = removeEmptyKeyValues(v);
    } else if (v != null) {
      newObj[k] = queryObj[k];
    }
  });
  return newObj;
};

const clearEmptyObjects = (queryObj) => {
  for (const objKey in queryObj) {
    if (_.isArray(queryObj[objKey])) {
      if (Object.keys(queryObj[objKey]).length === 0) {
        delete queryObj[objKey];
      } else {
        if (!_.every(queryObj[objKey], _.isString)) {
          queryObj[objKey].forEach(function (item, index) {
            queryObj[objKey][index] = removeEmptyKeyValues(item);
          });
        }
      }
    }

    if (_.isObject(queryObj[objKey])) {
      clearEmptyObjects(queryObj[objKey]);
    }

    if (_.isEmpty(queryObj[objKey])) {
      delete queryObj[objKey];
    }
  }
  return queryObj;
};

const formatData = (formattingData) => {
  formattingData = _.pick(formattingData, Object.keys(ingressActionTemplate));
  let tmpIngressActionTemplate = _.cloneDeepWith(ingressActionTemplate);
  formattingData = _.merge(tmpIngressActionTemplate, formattingData);
  if (!_.isEmpty(model.value.timedIngressActionOption?.name)) {
    formattingData["timedIngressAction"]["name"] = formattingData["timedIngressActionOption"]["name"];
    formattingData["timedIngressAction"]["type"] = formattingData["timedIngressActionOption"]["type"];
  }
  if (!_.isEmpty(model.value.timedIngressActionOption?.parameters)) {
    formattingData["timedIngressAction"]["parameters"] = formattingData["timedIngressActionOption"]["parameters"];
  }

  let changedFlowValues = _.omitBy(model.value["publishRulesOption"], function (v, k) {
    return JSON.stringify(model.value["publishRules"][k]) === JSON.stringify(v);
  });

  if (!_.isEmpty(changedFlowValues)) {
    formattingData["publishRules"] = model.value["publishRulesOption"];
  }

  if (_.isEqual(ingressActionTemplate["publishRules"], model.value["publishRulesOption"])) {
    formattingData["publishRules"] = null;
  }

  formattingData = clearEmptyObjects(formattingData);

  formattingData = _.omit(formattingData, ["flowStatus", "timedIngressActionOption", "timedIngressAction.actionType", "publishRulesOption", "originalPublishRules"]);

  return formattingData;
};

const scrollToErrors = async () => {
  const errorMessages = document.getElementById("error-message");
  await nextTick();
  errorMessages.scrollIntoView();
};

const submit = async () => {
  let timedIngressActionObject = _.cloneDeepWith(model.value);
  timedIngressActionObject = formatData(timedIngressActionObject);

  errors.value = [];
  if (_.isEmpty(timedIngressActionObject["name"])) {
    errors.value.push("Name is a required field.");
  }

  if (_.isEmpty(timedIngressActionObject["description"])) {
    errors.value.push("Description is a required field.");
  }

  if (_.isEmpty(timedIngressActionObject["timedIngressAction"]["name"])) {
    errors.value.push("Ingress Action is a required field.");
  }

  if (_.isEmpty(timedIngressActionObject["cronSchedule"])) {
    errors.value.push("Duration is a required field.");
  }

  if (_.isEmpty(timedIngressActionObject["publishRules"]) && _.isEmpty(timedIngressActionObject["targetFlow"])) {
    errors.value.push("Both publishRules and targetFlow are missing, one must be set.");
  }

  if (!_.isEmpty(timedIngressActionObject["publishRules"]) && !_.isEmpty(timedIngressActionObject["targetFlow"])) {
    errors.value.push("Both publishRules and targetFlow are set, only one should be set.");
  }

  // If publishRules are set and rules are set make sure every rule object has at least topics set.
  if (!_.isEmpty(timedIngressActionObject["publishRules"]) && !_.isEmpty(timedIngressActionObject["publishRules"]["rules"])) {
    let allTopicsSet = timedIngressActionObject["publishRules"]["rules"].every((rule) => !_.isEmpty(rule["topics"]));

    if (!allTopicsSet) {
      errors.value.push("All rules must have at least the topics set.");
    }
  }

  if (!_.isEmpty(timedIngressActionObject["name"])) {
    let activeSystemIngressActionNames = _.map(timedIngressActions.value, "name");
    let isFlowNamedUsed = _.includes(activeSystemIngressActionNames, timedIngressActionObject["name"]);

    if (isFlowNamedUsed && !editIngressAction) {
      errors.value.push("Name already exists in the system. Choose a different Name.");
    }
  }

  if (!_.isEmpty(errors.value)) {
    scrollToErrors();
    return;
  }

  let response = await saveTimedIngressFlowPlan(timedIngressActionObject);

  if (!_.isEmpty(_.get(response, "errors", null))) {
    errors.value.push(response["errors"][0]["message"]);
    scrollToErrors();
    return;
  }

  closeDialogCommand.command();
  emit("reloadIngressActions");
};

const clearErrors = () => {
  errors.value = [];
};

const publishRulesSchema = {
  type: "object",
  properties: {
    defaultRule: {
      type: "object",
      default: {},
      properties: {
        defaultBehavior: {
          type: "string",
          enum: ["ERROR", "FILTER", "PUBLISH"],
          default: "ERROR",
        },
        topic: {
          type: "string",
        },
      },
    },
    matchingPolicy: {
      type: "string",
      enum: ["ALL_MATCHING", "FIRST_MATCHING"],
      default: "ALL_MATCHING",
    },
    rules: {
      type: "array",
      items: {
        type: "object",
        properties: {
          condition: {
            type: "string",
          },
          topics: {
            type: "array",
            items: {
              type: "string",
            },
          },
        },
      },
    },
  },
};
</script>

<style lang="scss">
@import "@/styles/components/ingressAction/ingress-action-configuration-dialog.scss";
</style>
