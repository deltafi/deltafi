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
  <div id="error-message" class="egress-action-configuration-dialog">
    <div class="egress-action-panel">
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
            <InputText v-model="model['name']" placeholder="Egress Action Name." :disabled="editEgressAction" class="inputWidth" />
          </dd>
          <dt>Description*</dt>
          <dd>
            <TextArea v-model="model['description']" placeholder="Egress Action Description." class="inputWidth" rows="5" />
          </dd>
          <dt class="d-flex inputWidth justify-content-between">
            <div>Subcribe*</div>
            <div>
              <Badge v-if="!_.isEmpty(validateSubscribe)" v-tooltip.left="{ value: `${validateSubscribe}`, class: 'tooltip-width', showDelay: 300 }" value=" " :class="'pi pi-exclamation-triangle pt-1'" severity="danger"></Badge>
            </div>
          </dt>
          <dd>
            <div class="deltafi-fieldset inputWidth pl-1 py-0">
              <div class="px-2">
                <JsonForms :data="model['subscribe']" :renderers="subscribeRenderers" :uischema="subscribeUISchema" :schema="subscribeSchema" :config="subscribeConfig" @change="onSubscribeChange" />
              </div>
            </div>
          </dd>
          <dt>Action*</dt>
          <dd>
            <Dropdown v-model="model['egressActionOption']" :options="flattenedActionsTypes" option-label="name" placeholder="Select an action" show-clear class="inputWidth" />
          </dd>
          <template v-if="!_.isEmpty(model['egressActionOption'])">
            <template v-if="schemaProvided(model['egressActionOption']['schema'])">
              <dt>Action Variables</dt>
              <dd>
                <div class="deltafi-fieldset inputWidth">
                  <div class="px-2 pt-3">
                    <JsonForms :data="model['egressActionOption']['parameters']" :renderers="renderers" :uischema="parametersSchema" :schema="model['egressActionOption']['schema']" @change="onParametersChange($event, model)" />
                  </div>
                </div>
              </dd>
            </template>
            <dd v-else>
              <div class="px-2">
                <Message severity="info" :closable="false">No action variables available</Message>
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
import useEgressActions from "@/composables/useEgressActions";
import useFlowActions from "@/composables/useFlowActions";
import useTopics from "@/composables/useTopics";
import { useMounted } from "@vueuse/core";
import { computed, defineEmits, defineProps, inject, nextTick, onMounted, onBeforeMount, onUnmounted, provide, reactive, ref } from "vue";

import usePrimeVueJsonSchemaUIRenderers from "@/composables/usePrimeVueJsonSchemaUIRenderers";
import { JsonForms } from "@jsonforms/vue";

import Badge from "primevue/badge";
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
  editEgressAction: {
    type: Boolean,
    required: false,
    default: false,
  },
  closeDialogCommand: {
    type: Object,
    default: null,
  },
});

const { getAllTopicNames } = useTopics();
const { editEgressAction, closeDialogCommand } = reactive(props);
const emit = defineEmits(["reloadEgressActions"]);
const { getPluginActionSchema } = useFlowActions();
const { getAllEgress, saveEgressFlowPlan } = useEgressActions();

const { myStyles, rendererList, subscribeRenderList } = usePrimeVueJsonSchemaUIRenderers();
provide("style", myStyles);
const renderers = ref(Object.freeze(rendererList));
const subscribeRenderers = ref(Object.freeze(subscribeRenderList));
const subscribeUISchema = ref(undefined);
const parametersSchema = ref(undefined);
const allTopics = ref(["default"]);

const errors = ref([]);

const allActionsData = ref({});

const egressActions = ref([]);

const subscribeConfig = ref({ defaultLabels: true });

const defaultTopicTemplate = [{ condition: null, topic: null }];

const egressActionTemplate = {
  name: null,
  description: null,
  type: "EGRESS",
  flowStatus: {
    state: "STOPPED",
  },
  egressActionOption: null,
  egressAction: {
    apiVersion: null,
    name: null,
    parameters: null,
    type: null,
  },
  variables: null,
  subscribe: defaultTopicTemplate,
};

const rowData = ref(_.cloneDeepWith(props.rowDataProp || egressActionTemplate));

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

const getEgressActions = async () => {
  let tmpFlattenedActionsTypes = [];
  for (const plugins of allActionsData.value) {
    for (const action of plugins["actions"]) {
      if (!_.isEqual(action.type, "EGRESS")) continue;

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

onBeforeMount(async () => {
  let topics = await getAllTopicNames();
  allTopics.value.length = 0;
  topics.forEach((topic) => allTopics.value.push(topic));

  let responseFlowAction = await getPluginActionSchema();
  allActionsData.value = responseFlowAction.data.plugins;

  const response = await getAllEgress();
  egressActions.value = response.data.getAllFlows.Egress;

  flattenedActionsTypes.value = await getEgressActions();

  if (!_.isEmpty(model.value["egressAction"]["name"])) {
    let tmpName = model.value["egressAction"]["name"];
    model.value["egressActionOption"] = _.find(flattenedActionsTypes.value, { type: model.value["egressAction"]["type"] });
    model.value["egressActionOption"]["name"] = tmpName;
    if (!_.isEmpty(model.value["egressAction"]["parameters"])) {
      model.value["egressActionOption"]["parameters"] = model.value["egressAction"]["parameters"];
    }
  }
});

const isMounted = ref(useMounted());

onMounted(async () => {
  editing.value = true;
});

onUnmounted(() => {
  editing.value = false;
});

const onParametersChange = (event, element) => {
  element["egressActionOption"]["parameters"] = event.data;
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
  formattingData = _.pick(formattingData, Object.keys(egressActionTemplate));
  let tmpEgressActionTemplate = _.cloneDeepWith(egressActionTemplate);
  formattingData = _.merge(tmpEgressActionTemplate, formattingData);
  if (!_.isEmpty(model.value.egressActionOption?.name)) {
    formattingData["egressAction"]["name"] = formattingData["egressActionOption"]["name"];
    formattingData["egressAction"]["type"] = formattingData["egressActionOption"]["type"];
  }
  if (!_.isEmpty(model.value.egressActionOption?.parameters)) {
    formattingData["egressAction"]["parameters"] = formattingData["egressActionOption"]["parameters"];
  }

  formattingData = clearEmptyObjects(formattingData);

  formattingData = _.omit(formattingData, ["flowStatus", "egressActionOption", "egressAction.actionType"]);

  return formattingData;
};

const scrollToErrors = async () => {
  const errorMessages = document.getElementById("error-message");
  await nextTick();
  errorMessages.scrollIntoView();
};

const validateSubscribe = computed(() => {
  // If the subscribe field is empty return "Not Subscribing to any Topic."
  if (_.isEmpty(model.value["subscribe"])) {
    return "Not Subscribing to any Topic.";
  }

  let checkIfSubscribeHasTopic = (key) =>
    model.value["subscribe"].some(
      (obj) =>
        Object.keys(obj).includes(key) &&
        Object.keys(obj).some(function (key) {
          return !_.isEmpty(obj[key]);
        })
    );

  var isKeyPresent = checkIfSubscribeHasTopic("topic");
  // If the subscribe field isn't empty but there isn't a topic return "Not Subscribing to any Topic Name."
  if (!isKeyPresent) {
    return "Not Subscribing to any Topic Name.";
  }

  return null;
});

const submit = async () => {
  let egressActionObject = _.cloneDeepWith(model.value);
  egressActionObject = formatData(egressActionObject);

  errors.value = [];
  if (_.isEmpty(egressActionObject["name"])) {
    errors.value.push("Name is a required field.");
  }

  if (_.isEmpty(egressActionObject["description"])) {
    errors.value.push("Description is a required field.");
  }

  if (_.isEmpty(egressActionObject["egressAction"]["name"])) {
    errors.value.push("Egress Action is a required field.");
  }

  if (!_.isEmpty(egressActionObject["name"])) {
    let activeSystemEgressActionNames = _.map(egressActions.value, "name");
    let isFlowNamedUsed = _.includes(activeSystemEgressActionNames, egressActionObject["name"]);

    if (isFlowNamedUsed && !editEgressAction) {
      errors.value.push("Name already exists in the system. Choose a different Name.");
    }
  }

  if (!_.isEmpty(errors.value)) {
    scrollToErrors();
    return;
  }

  let response = await saveEgressFlowPlan(egressActionObject);

  if (!_.isEmpty(_.get(response, "errors", null))) {
    errors.value.push(response["errors"][0]["message"]);
    scrollToErrors();
    return;
  }

  closeDialogCommand.command();
  emit("reloadEgressActions");
};

const clearErrors = () => {
  errors.value = [];
};

const onSubscribeChange = (event) => {
  model.value["subscribe"] = event.data;
};

const subscribeSchema = {
  type: "array",
  title: "Topics",
  items: {
    type: "object",
    properties: {
      topic: {
        type: "string",
        title: "Topic Name",
        enum: allTopics.value,
      },
      condition: {
        type: "string",
        title: "Condition (Optional)",
      },
    },
  },
};
</script>

<style lang="scss">
@import "@/styles/components/egressAction/egress-action-configuration-dialog.scss";
</style>
