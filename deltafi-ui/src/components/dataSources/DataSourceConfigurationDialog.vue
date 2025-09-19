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
  <div id="error-message" class="data-source-configuration-dialog">
    <div class="data-source-panel">
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
            <InputText v-model="model['name']" placeholder="Data Source Name." :disabled="editDataSource" class="inputWidth" />
          </dd>
          <dt>Description*</dt>
          <dd>
            <TextArea v-model="model['description']" placeholder="Data Source Description." class="inputWidth" rows="5" />
          </dd>
          <dt>Data Source Type*</dt>
          <dd>
            <Dropdown v-model="model['dataSourceType']" :options="dataSourceTypeOptions" placeholder="Select a Data Source Type" show-clear class="inputWidth" />
          </dd>
          <template v-if="_.isEqual(model['dataSourceType'], 'On-Error Data Source')">
            <dt>Error Message Regex</dt>
            <dd>
              <InputText v-model="model['errorMessageRegex']" placeholder="Error Message Regex" class="inputWidth" />
            </dd>
            <dt>Source Metadata Prefix</dt>
            <dd>
              <InputText v-model="model['sourceMetadataPrefix']" placeholder="Source Metadata Prefix" class="inputWidth" />
            </dd>
            <dt>Include Source Metadata Regex</dt>
            <dd>
              <Chips v-model="model['includeSourceMetadataRegex']" class="inputWidth" />
            </dd>
          </template>
          <template v-if="_.isEqual(model['dataSourceType'], 'Timed Data Source')">
            <dt>Cron Schedule*</dt>
            <dd>
              <CronLight v-model="model['cronSchedule']" format="quartz" @error="errors.push($event)" />
              <InputText v-model="model['cronSchedule']" placeholder="e.g.	*/5 * * * * *" style="margin-top: 0.5rem" class="inputWidth" />
            </dd>
            <dt>Action*</dt>
            <dd>
              <Dropdown v-model="model['timedIngressActionOption']" :options="flattenedActionsTypes" option-label="name" placeholder="Select an action" show-clear class="inputWidth" />
            </dd>
            <template v-if="!_.isEmpty(model['timedIngressActionOption'])">
              <template v-if="schemaProvided(model['timedIngressActionOption']['schema'])">
                <dt>Action Variables</dt>
                <dd>
                  <div class="deltafi-fieldset">
                    <div class="px-2 pt-3">
                      <JsonForms :data="model['timedIngressActionOption']['parameters']" :renderers="renderers" :uischema="parametersSchema" :schema="model['timedIngressActionOption']['schema']" @change="onParametersChange($event, model)" />
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
          </template>
          <dt>Topic*</dt>
          <dd>
            <AutoComplete v-model="model['topic']" placeholder="Publish to Topic" class="auto-complete-input-width" :suggestions="topicList" dropdown @complete="search" />
          </dd>
          <dt>Annotation Configuration</dt>
          <dd>
            <div class="deltafi-fieldset">
              <div class="mb-n3">
                <JsonForms :data="model['annotationConfig']" :renderers="annotationConfigRenderers" :uischema="annotationConfigUISchema" :schema="annotationConfigSchema" @change="onAnnotationConfigChange($event, model)" />
              </div>
            </div>
          </dd>
          <dt>Metadata</dt>
          <dd>
            <div class="deltafi-fieldset">
              <div class="mb-n3">
                <JsonForms :data="metadataObject" :renderers="metadataRenderers" :uischema="metadataUISchema" :schema="metadataSchema" @change="onMetadataChange" />
              </div>
            </div>
          </dd>
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
import Chips from "primevue/chips";
import useFlows from "@/composables/useFlows";
import useDataSource from "@/composables/useDataSource";
import useFlowActions from "@/composables/useFlowActions";
import useTopics from "@/composables/useTopics";
import { useMounted } from "@vueuse/core";
import { computed, inject, nextTick, onMounted, onBeforeMount, onUnmounted, provide, reactive, ref } from "vue";

import usePrimeVueJsonSchemaUIRenderers from "@/composables/usePrimeVueJsonSchemaUIRenderers";
import { JsonForms } from "@jsonforms/vue";

import AutoComplete from "primevue/autocomplete";
import Button from "primevue/button";
import Dropdown from "primevue/dropdown";
import InputText from "primevue/inputtext";
import Message from "primevue/message";
import TextArea from "primevue/textarea";
import _ from "lodash";

import "@vue-js-cron/light/dist/light.css";
import { CronLight } from "@vue-js-cron/light";

const editing = inject("isEditing");
const { rendererList, publishRenderList, myStyles } = usePrimeVueJsonSchemaUIRenderers();
provide("style", myStyles);
const renderers = ref(Object.freeze(rendererList));
const annotationConfigRenderers = ref(Object.freeze(rendererList));
const metadataRenderers = ref(Object.freeze(publishRenderList));
const annotationConfigUISchema = ref(undefined);
const metadataUISchema = ref(undefined);
const parametersSchema = ref(undefined);
const allTopics = ref(["default"]);
const topicList = ref(undefined);

const props = defineProps({
  rowDataProp: {
    type: Object,
    required: false,
    default: null,
  },
  editDataSource: {
    type: Boolean,
    required: false,
    default: false,
  },
});

const { getAllTopicNames } = useTopics();
const { editDataSource } = reactive(props);
const emit = defineEmits(["refreshAndClose"]);
const { getPluginActionSchema } = useFlowActions();
const { saveTimedDataSourcePlan, saveRestDataSourcePlan, saveOnErrorDataSourcePlan } = useDataSource();
const { allDataSourceFlowNames, fetchAllDataSourceFlowNames } = useFlows();

const errors = ref([]);

const allActionsData = ref({});

const dataSourceNames = ref([]);

const dataSourceTypeMap = {
  REST_DATA_SOURCE: "Rest Data Source",
  TIMED_DATA_SOURCE: "Timed Data Source",
  ON_ERROR_DATA_SOURCE: "On-Error Data Source",
};
const dataSourceTypeOptions = ref(Object.values(dataSourceTypeMap));

const dataSourceTemplate = {
  name: null,
  type: null,
  description: null,
  targetFlow: null,
  dataSourceType: null,
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
  topic: null,
  annotationConfig: null,
  metadata: null,
  errorMessageRegex: null,
  sourceMetadataPrefix: null,
  includeSourceMetadataRegex: [],
};

const rowData = ref(_.cloneDeepWith(_.isEmpty(props.rowDataProp) ? dataSourceTemplate : props.rowDataProp));
const metadataObject = ref({ metadata: JSON.parse(JSON.stringify(_.get(props.rowDataProp, "metadata", {}))) });

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
  const tmpFlattenedActionsTypes = [];
  for (const plugins of allActionsData.value) {
    for (const action of plugins["actions"]) {
      if (!_.isEqual(action.type, "TIMED_INGRESS")) continue;

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

const isMounted = ref(useMounted());

onBeforeMount(async () => {
  const topics = await getAllTopicNames();
  allTopics.value.length = 0;
  topics.forEach((topic) => allTopics.value.push(topic));

  const responseFlowAction = await getPluginActionSchema();
  allActionsData.value = responseFlowAction.data.plugins;

  await fetchAllDataSourceFlowNames();
  dataSourceNames.value = _.concat(dataSourceNames.value, allDataSourceFlowNames.value.restDataSource, allDataSourceFlowNames.value.timedDataSource);

  flattenedActionsTypes.value = await getTimedIngressActions();

  model.value['dataSourceType'] = dataSourceTypeMap[model.value['type']];

  if (_.has(model.value["timedIngressAction"], "name")) {
    if (!_.isEmpty(model.value["timedIngressAction"]["name"])) {
      const tmpName = model.value["timedIngressAction"]["name"];
      model.value["timedIngressActionOption"] = _.find(flattenedActionsTypes.value, { type: model.value["timedIngressAction"]["type"] });
      model.value["timedIngressActionOption"]["name"] = tmpName;
      if (!_.isEmpty(model.value["timedIngressAction"]["parameters"])) {
        model.value["timedIngressActionOption"]["parameters"] = model.value["timedIngressAction"]["parameters"];
      }
    }
  }
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

const onAnnotationConfigChange = (event, element) => {
  element["annotationConfig"] = event.data;
};

const onMetadataChange = (event) => {
  metadataObject.value.metadata = event.data.metadata || {};
};

const schemaProvided = (schema) => {
  return !_.isEmpty(_.get(schema, "properties", null));
};

const search = async (event) => {
  setTimeout(() => {
    if (!event.query.trim().length) {
      topicList.value = allTopics.value;
    } else {
      topicList.value = allTopics.value.filter((topic) => {
        return topic.toLowerCase().includes(event.query.toLowerCase());
      });
    }
  }, 300);
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

    if (_.isEmpty(queryObj[objKey]) && !_.isBoolean(queryObj[objKey])) {
      delete queryObj[objKey];
    }
  }
  return queryObj;
};

const omitFields = (omittingData) => {
  let fieldsToOmit = ["flowStatus", "timedIngressActionOption", "timedIngressAction.actionType", "dataSourceType"];
  if (omittingData["dataSourceType"] === "Rest Data Source") {
    fieldsToOmit = fieldsToOmit.concat(["cronSchedule", "timedIngressAction"]);
  }
  return _.omit(omittingData, fieldsToOmit);
};

const formatData = (formattingData) => {
  formattingData = _.pick(formattingData, Object.keys(dataSourceTemplate));
  const tmpIngressActionTemplate = _.cloneDeepWith(dataSourceTemplate);
  formattingData = _.merge(tmpIngressActionTemplate, formattingData);
  if (!_.isEmpty(model.value.timedIngressActionOption?.name)) {
    formattingData["timedIngressAction"]["name"] = formattingData["timedIngressActionOption"]["name"];
    formattingData["timedIngressAction"]["type"] = formattingData["timedIngressActionOption"]["type"];
  }
  if (!_.isEmpty(model.value.timedIngressActionOption?.parameters)) {
    formattingData["timedIngressAction"]["parameters"] = formattingData["timedIngressActionOption"]["parameters"];
  }

  formattingData = clearEmptyObjects(formattingData);

  return formattingData;
};

const scrollToErrors = async () => {
  const errorMessages = document.getElementById("error-message");
  await nextTick();
  errorMessages.scrollIntoView();
};

const submit = async () => {
  let dataSourceObject = _.cloneDeepWith(model.value);
  if (!_.isEmpty(metadataObject.value.metadata)) {
    dataSourceObject["metadata"] = JSON.parse(JSON.stringify(metadataObject.value.metadata));
  }
  dataSourceObject = formatData(dataSourceObject);
  errors.value = [];
  if (_.isEmpty(dataSourceObject["name"])) {
    errors.value.push("Name is a required field.");
  }

  if (_.isEmpty(dataSourceObject["description"])) {
    errors.value.push("Description is a required field.");
  }

  if (_.isEmpty(dataSourceObject["dataSourceType"])) {
    errors.value.push("Data Source Type is a required field.");
  }

  if (_.isEqual(dataSourceObject["dataSourceType"], "Timed Data Source")) {
    if (_.isEmpty(dataSourceObject["cronSchedule"])) {
      errors.value.push("Cron Schedule is a required field.");
    }
    if (_.has(model.value["timedIngressAction"], "name")) {
      if (_.isEmpty(dataSourceObject["timedIngressAction"]["name"])) {
        errors.value.push("Action is a required field.");
      }
    }
  }

  if (!_.isEmpty(dataSourceObject["name"])) {
    const isFlowNamedUsed = _.includes(dataSourceNames.value, dataSourceObject["name"]);

    if (isFlowNamedUsed && !editDataSource) {
      errors.value.push("Name already exists in the system. Choose a different Name.");
    }
  }

  if (_.isEmpty(dataSourceObject["topic"])) {
    errors.value.push("Topic is a required field.");
  }

  if (!_.isEmpty(errors.value)) {
    scrollToErrors();
    return;
  }

  dataSourceObject = omitFields(dataSourceObject);

  let response = null;

  if (_.isEqual(model.value["dataSourceType"], "Timed Data Source")) {
    response = await saveTimedDataSourcePlan(dataSourceObject);
  } else if (_.isEqual(model.value["dataSourceType"], "On-Error Data Source")) {
    response = await saveOnErrorDataSourcePlan(dataSourceObject);
  } else {
    response = await saveRestDataSourcePlan(dataSourceObject);
  }

  if (!_.isEmpty(_.get(response, "errors", null))) {
    errors.value.push(response["errors"][0]["message"]);
    scrollToErrors();
    return;
  }

  editing.value = false;
  emit("refreshAndClose");
};

const clearErrors = () => {
  errors.value = [];
};

const annotationConfigSchema = {
  type: "object",
  properties: {
    annotations: {
      type: "object",
      label: "Annotations",
      additionalProperties: {
        type: "string",
      },
    },
    metadataPatterns: {
      type: "array",
      label: "Metadata Patterns",
      items: {
        type: "string",
      },
    },
    discardPrefix: {
      label: "Discard Prefix",
      type: "string",
    },
  },
};

const metadataSchema = {
  type: "object",
  properties: {
    metadata: {
      type: "object",
      additionalProperties: {
        type: "string",
      },
    },
  },
};
</script>

<style>
.data-source-configuration-dialog {
  width: 98%;

  .data-source-panel {
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

    > .p-inputtext {
      width: 100% !important;
    }
  }

  .inputWidth {
    width: 90% !important;

    .p-chips-multiple-container {
      width: 100% !important;
    }
  }

  .p-filled.capitalizeText {
    text-transform: uppercase;
  }
}
</style>
