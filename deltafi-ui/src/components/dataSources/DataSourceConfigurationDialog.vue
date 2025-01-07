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
                <li class="text-wrap text-break">{{ error }}</li>
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
          <template v-if="_.isEqual(model['dataSourceType'], 'Timed Data Source')">
            <dt>Cron Schedule*</dt>
            <dd>
              <CronLight v-model="model['cronSchedule']" format="quartz" @error="errors.push($event)"></CronLight>
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
                  <Message severity="info" :closable="false">No action variables available</Message>
                </div>
              </dd>
            </template>
          </template>
          <dt>Topic*</dt>
          <dd>
            <AutoComplete v-model="model['topic']" placeholder="Publish to Topic" class="auto-complete-input-width" :suggestions="topicList" dropdown @complete="search" />
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
import useFlows from "@/composables/useFlows";
import useDataSource from "@/composables/useDataSource";
import useFlowActions from "@/composables/useFlowActions";
import useTopics from "@/composables/useTopics";
import { useMounted } from "@vueuse/core";
import { computed, defineEmits, defineProps, inject, nextTick, onMounted, onBeforeMount, onUnmounted, provide, reactive, ref } from "vue";

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
  closeDialogCommand: {
    type: Object,
    default: null,
  },
});

const { getAllTopicNames } = useTopics();
const { editDataSource, closeDialogCommand } = reactive(props);
const emit = defineEmits(["reloadDataSources"]);
const { getPluginActionSchema } = useFlowActions();
const { saveTimedDataSourcePlan, saveRestDataSourcePlan } = useDataSource();
const { allDataSourceFlowNames, fetchAllDataSourceFlowNames } = useFlows();

const { rendererList, myStyles } = usePrimeVueJsonSchemaUIRenderers();
provide("style", myStyles);
const renderers = ref(Object.freeze(rendererList));
const parametersSchema = ref(undefined);
const allTopics = ref(["default"]);
const topicList = ref(undefined);

const errors = ref([]);

const allActionsData = ref({});

const dataSourceNames = ref([]);

const dataSourceTypeOptions = ref(["Rest Data Source", "Timed Data Source"]);

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
};

const rowData = ref(_.cloneDeepWith(props.rowDataProp || dataSourceTemplate));

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
  let topics = await getAllTopicNames();
  allTopics.value.length = 0;
  topics.forEach((topic) => allTopics.value.push(topic));

  let responseFlowAction = await getPluginActionSchema();
  allActionsData.value = responseFlowAction.data.plugins;

  await fetchAllDataSourceFlowNames();
  dataSourceNames.value = _.concat(dataSourceNames.value, allDataSourceFlowNames.value.restDataSource, allDataSourceFlowNames.value.timedDataSource);

  flattenedActionsTypes.value = await getTimedIngressActions();

  if (!_.isEmpty(model.value["description"])) {
    if (!_.isEmpty(model.value["cronSchedule"])) {
      model.value["dataSourceType"] = "Timed Data Source";
    } else {
      model.value["dataSourceType"] = "Rest Data Source";
    }
  }

  if (_.has(model.value["timedIngressAction"], "name")) {
    if (!_.isEmpty(model.value["timedIngressAction"]["name"])) {
      let tmpName = model.value["timedIngressAction"]["name"];
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
  let tmpIngressActionTemplate = _.cloneDeepWith(dataSourceTemplate);
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
    let isFlowNamedUsed = _.includes(dataSourceNames.value, dataSourceObject["name"]);

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
  } else {
    response = await saveRestDataSourcePlan(dataSourceObject);
  }

  if (!_.isEmpty(_.get(response, "errors", null))) {
    errors.value.push(response["errors"][0]["message"]);
    scrollToErrors();
    return;
  }
  closeDialogCommand.command();
  emit("reloadDataSources");
};

const clearErrors = () => {
  errors.value = [];
};
</script>

<style lang="scss">
@import "@/styles/components/dataSources/data-source-configuration-dialog.scss";
</style>
