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
  <div id="error-message" class="data-sink-configuration-dialog">
    <div class="data-sink-panel">
      <div class="pb-0">
        <div v-if="!_.isEmpty(errors)" class="pt-2">
          <Message severity="error" :sticky="true" class="mb-2 mt-0" @close="clearErrors()">
            <ul>
              <div v-for="(error, key) in errors" :key="key">
                <li class="text-wrap text-break">
                  {{ error }}
                </li>
              </div>
            </ul>
          </Message>
        </div>
        <dl>
          <dt>Name*</dt>
          <dd>
            <InputText v-model="model['name']" placeholder="Data Sink Name" :disabled="editDataSink" class="inputWidth" />
          </dd>
          <dt>Description*</dt>
          <dd>
            <TextArea v-model="model['description']" placeholder="Data Sink Description" class="inputWidth" rows="5" />
          </dd>
          <dt class="d-flex inputWidth justify-content-between">
            <div>Subscribe*</div>
            <div>
              <Badge v-if="!_.isEmpty(validateSubscribe)" v-tooltip.left="{ value: `${validateSubscribe}`, class: 'tooltip-width', showDelay: 300 }" value=" " :class="'pi pi-exclamation-triangle pt-1'" severity="danger" />
            </div>
          </dt>
          <dd>
            <div class="deltafi-fieldset inputWidth pl-1 py-0">
              <div class="px-2">
                <JsonForms :data="model['subscribe']" :renderers="subscribeRenderers" :uischema="subscribeUISchema" :schema="subscribeSchema" :config="subscribeConfig" @change="onSubscribeChange" />
              </div>
            </div>
          </dd>
          <dt>Egress Action*</dt>
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
                <Message severity="info" :closable="false"> No action variables available </Message>
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
import useDataSink from "@/composables/useDataSink";
import useFlowActions from "@/composables/useFlowActions";
import useTopics from "@/composables/useTopics";
import { useMounted } from "@vueuse/core";
import { computed, inject, nextTick, onMounted, onBeforeMount, onUnmounted, provide, reactive, ref } from "vue";

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
  editDataSink: {
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
const { editDataSink, closeDialogCommand } = reactive(props);
const emit = defineEmits(["reloadDataSinks"]);
const { getPluginActionSchema } = useFlowActions();
const { getAllDataSinks, saveDataSinkPlan } = useDataSink();

const { myStyles, rendererList, subscribeRenderList } = usePrimeVueJsonSchemaUIRenderers();
provide("style", myStyles);
const renderers = ref(Object.freeze(rendererList));
const subscribeRenderers = ref(Object.freeze(subscribeRenderList));
const subscribeUISchema = ref(undefined);
const parametersSchema = ref(undefined);
const allTopics = ref(["default"]);

const errors = ref([]);

const allActionsData = ref({});

const dataSinks = ref([]);

const subscribeConfig = ref({ defaultLabels: true });

const defaultTopicTemplate = [{ condition: null, topic: null }];

const dataSinkTemplate = {
  name: null,
  type: "DATA_SINK",
  description: null,
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

const rowData = ref(_.cloneDeepWith(_.isEmpty(props.rowDataProp) ? dataSinkTemplate : props.rowDataProp));

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
  const tmpFlattenedActionsTypes = [];
  for (const plugins of allActionsData.value) {
    for (const action of plugins["actions"]) {
      if (!_.isEqual(action.type, "EGRESS")) continue;

      // Reformatting each action.
      action["type"] = action["name"];
      const name = action.name.split(".").pop();
      action["name"] = name;
      action["parameters"] = {};

      tmpFlattenedActionsTypes.push(action);
    }
  }
  return tmpFlattenedActionsTypes;
};

onBeforeMount(async () => {
  const topics = await getAllTopicNames();
  allTopics.value.length = 0;
  topics.forEach((topic) => allTopics.value.push(topic));

  const responseFlowAction = await getPluginActionSchema();
  allActionsData.value = responseFlowAction.data.plugins;

  const response = await getAllDataSinks();
  dataSinks.value = response.data.getAllFlows.dataSinks;

  flattenedActionsTypes.value = await getEgressActions();

  if (!_.isEmpty(model.value["egressAction"]["name"])) {
    const tmpName = model.value["egressAction"]["name"];
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

const formatData = (formattingData) => {
  formattingData = _.pick(formattingData, Object.keys(dataSinkTemplate));
  const tmpEgressActionTemplate = JSON.parse(JSON.stringify(dataSinkTemplate));
  formattingData = _.merge(tmpEgressActionTemplate, formattingData);
  if (!_.isEmpty(model.value.egressActionOption?.name)) {
    formattingData["egressAction"]["name"] = formattingData["egressActionOption"]["name"];
    formattingData["egressAction"]["type"] = formattingData["egressActionOption"]["type"];
  }
  if (!_.isEmpty(model.value.egressActionOption?.parameters)) {
    formattingData["egressAction"]["parameters"] = formattingData["egressActionOption"]["parameters"];
  }

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

  const checkIfSubscribeHasTopic = (key) =>
    model.value["subscribe"].some(
      (obj) =>
        Object.keys(obj).includes(key) &&
        Object.keys(obj).some(function (key) {
          return !_.isEmpty(obj[key]);
        })
    );

  const isKeyPresent = checkIfSubscribeHasTopic("topic");
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
    const activeSystemEgressActionNames = _.map(dataSinks.value, "name");
    const isFlowNamedUsed = _.includes(activeSystemEgressActionNames, egressActionObject["name"]);

    if (isFlowNamedUsed && !editDataSink) {
      errors.value.push("Name already exists in the system. Choose a different Name.");
    }
  }

  if (!_.isEmpty(errors.value)) {
    scrollToErrors();
    return;
  }

  const response = await saveDataSinkPlan(egressActionObject);

  if (!_.isEmpty(_.get(response, "errors", null))) {
    errors.value.push(response["errors"][0]["message"]);
    scrollToErrors();
    return;
  }

  closeDialogCommand.command();
  emit("reloadDataSinks");
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
    required: ["topic"],
  },
};
</script>

<style>
.data-sink-configuration-dialog {
  width: 98%;

  .data-sink-panel {
    .deltafi-fieldset {
      display: block;
      margin-inline-start: 2px;
      margin-inline-end: 2px;
      padding-block-start: 0.35em;
      padding-inline-start: 0.75em;
      padding-inline-end: 0.75em;
      padding-block-end: 0.625em;
      min-inline-size: min-content;
      border-radius: 4px;
      border: 1px solid #ced4da;
      border-width: 1px;
      border-style: groove;
      border-color: rgb(225, 225, 225);
      border-image: initial;
    }

    dt {
      margin-bottom: 0rem;
    }

    dd {
      margin-bottom: 1.2rem;
    }

    dl {
      margin-bottom: 1rem;
    }

    .p-panel-content {
      padding-bottom: 0.25rem !important;
    }
  }

  .auto-complete-input-width {
    width: 90% !important;

    >.p-inputtext {
      width: 100% !important;
    }
  }

  .inputWidth {
    width: 90% !important;
  }

  .p-filled.capitalizeText {
    text-transform: uppercase;
  }
}
</style>
