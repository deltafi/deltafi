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
  <div class="ingress-action-configuration-dialog">
    <div class="ingress-action-panel">
      <div class="pb-0">
        <div v-if="!_.isEmpty(errorsList)" class="pt-2">
          <Message severity="error" :sticky="true" class="mb-2 mt-0" @close="clearErrors()">
            <ul>
              <div v-for="(error, key) in errorsList" :key="key">
                <li class="text-wrap text-break">{{ error }}</li>
              </div>
            </ul>
          </Message>
        </div>

        <dl v-for="ingressActionfield in ingressActionConfigurationMap.keys()" :key="ingressActionfield">
          <dt>{{ ingressActionConfigurationMap.get(ingressActionfield).header }}</dt>
          <dd v-if="_.isEqual(ingressActionConfigurationMap.get(ingressActionfield).type, 'InputText')">
            <InputText v-model="model[ingressActionfield]" :placeholder="ingressActionConfigurationMap.get(ingressActionfield).placeholder" :disabled="ingressActionConfigurationMap.get(ingressActionfield).disabled" class="inputWidth" />
          </dd>
          <dd v-if="_.isEqual(ingressActionConfigurationMap.get(ingressActionfield).type, 'TextArea')">
            <TextArea v-model="model[ingressActionfield]" :placeholder="ingressActionConfigurationMap.get(ingressActionfield).placeholder" class="inputWidth" rows="5" />
          </dd>
          <dd v-if="_.isEqual(ingressActionConfigurationMap.get(ingressActionfield).type, 'Dropdown')">
            <Dropdown v-model="model[ingressActionfield]" :options="ingressFlowNames" :placeholder="ingressActionConfigurationMap.get(ingressActionfield).placeholder" :show-clear="ingressActionConfigurationMap.get(ingressActionfield).disabled" class="inputWidth" />
          </dd>
          <dd v-if="_.isEqual(ingressActionConfigurationMap.get(ingressActionfield).type, 'StateInputSwitch')">
            <StateInputSwitch :row-data-prop="model" ingress-action-type="timedIngress" :configure-ingress-action-dialog="true" />
          </dd>
          <dd v-if="_.isEqual(ingressActionConfigurationMap.get(ingressActionfield).type, 'Dropdown2')">
            <Dropdown v-model="model[ingressActionfield]" :options="flattenedActionsTypes" option-label="name" :placeholder="ingressActionConfigurationMap.get(ingressActionfield).placeholder" show-clear class="inputWidth" />
          </dd>
        </dl>
        <template v-if="!_.isEmpty(model['timedIngressActionOption'])">
          <template v-if="schemaProvided(model['timedIngressActionOption']['schema'])">
            <dt>Ingress Action Variables</dt>
            <dd>
              <div class="deltafi-fieldset">
                <div class="px-2 pt-3">
                  <json-forms :data="model['timedIngressActionOption']['parameters']" :renderers="renderers" :uischema="uischema" :schema="model['timedIngressActionOption']['schema']" @change="onChange($event, model)" />
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
import { computed, defineEmits, defineProps, inject, onMounted, onBeforeMount, onUnmounted, provide, reactive, ref } from "vue";

import usePrimeVueJsonSchemaUIRenderers from "@/composables/usePrimeVueJsonSchemaUIRenderers";
import { JsonForms } from "@jsonforms/vue";

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
const { saveTimedIngressFlowPlan } = useIngressActions();

const { rendererList, myStyles } = usePrimeVueJsonSchemaUIRenderers();
provide("style", myStyles);
const renderers = ref(Object.freeze(rendererList));
const uischema = ref(undefined);

const errorsList = ref([]);

const allActionsData = ref({});

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
};

const rowdata = ref(_.cloneDeepWith(props.rowDataProp || ingressActionTemplate));

const model = computed({
  get() {
    return new Proxy(rowdata.value, {
      set(obj, key, value) {
        model.value = { ...obj, [key]: value };
        return true;
      },
    });
  },
  set(newValue) {
    Object.assign(
      rowdata.value,
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

const ingressActionConfigurationMap = new Map([
  ["name", { header: "Name*", placeholder: "Ingress action name.", type: "InputText", disabled: editIngressAction }],
  ["description", { header: "Description*", placeholder: "Ingress action description.", type: "TextArea" }],
  ["targetFlow", { header: "Target Flow", placeholder: "Select a flow", type: "Dropdown" }],
  ["cronSchedule", { header: "Duration", placeholder: "e.g.	*/5 * * * * *", type: "InputText" }],
  ["flowStatus", { header: "Enabled", type: "StateInputSwitch" }],
  ["timedIngressActionOption", { header: "Ingress Action*", placeholder: "Select a flow", type: "Dropdown2" }],
]);

const isMounted = ref(useMounted());

onBeforeMount(async () => {
  await fetchIngressFlowNames();
  let responseFlowAction = await getPluginActionSchema();
  allActionsData.value = responseFlowAction.data.plugins;

  flattenedActionsTypes.value = await getTimedIngressActions();

  if (!_.isEmpty(model.value["timedIngressAction"]["name"])) {
    model.value["timedIngressActionOption"] = _.find(flattenedActionsTypes.value, { name: model.value["timedIngressAction"]["name"], type: model.value["timedIngressAction"]["type"] });
    if (!_.isEmpty(model.value["timedIngressAction"]["parameters"])) {
      model.value["timedIngressActionOption"]["parameters"] = model.value["timedIngressAction"]["parameters"];
    }
  }
});

onMounted(async () => {
  editing.value = true;
});

onUnmounted(() => {
  editing.value = false;
});

const onChange = (event, element) => {
  element["timedIngressActionOption"]["parameters"] = event.data;
};

const schemaProvided = (schema) => {
  return !_.isEmpty(_.get(schema, "properties", null));
};

const formatData = (formattingData) => {
  formattingData = _.pick(formattingData, Object.keys(ingressActionTemplate));
  formattingData = _.merge(ingressActionTemplate, formattingData);
  if (!_.isEmpty(model.value.timedIngressActionOption?.name)) {
    formattingData["timedIngressAction"]["name"] = formattingData["timedIngressActionOption"]["name"];
    formattingData["timedIngressAction"]["type"] = formattingData["timedIngressActionOption"]["type"];
  }
  if (!_.isEmpty(model.value.timedIngressActionOption?.parameters)) {
    formattingData["timedIngressAction"]["parameters"] = formattingData["timedIngressActionOption"]["parameters"];
  }
  formattingData = _.omit(formattingData, ["flowStatus", "timedIngressActionOption", "timedIngressAction.actionType"]);
  return formattingData;
};

const submit = async () => {
  let timedIngressActionObject = _.cloneDeepWith(model.value);
  timedIngressActionObject = formatData(timedIngressActionObject);

  errorsList.value = [];
  if (_.isEmpty(timedIngressActionObject["name"])) {
    errorsList.value.push("Name is a required field.");
  }

  if (_.isEmpty(timedIngressActionObject["description"])) {
    errorsList.value.push("Description is a required field.");
  }

  if (_.isEmpty(timedIngressActionObject["timedIngressAction"]["name"])) {
    errorsList.value.push("Ingress Action is a required field.");
  }

  if (!_.isEmpty(errorsList.value)) {
    return;
  }

  await saveTimedIngressFlowPlan(timedIngressActionObject);
  closeDialogCommand.command();
  emit("reloadIngressActions");
};

const clearErrors = () => {
  errorsList.value = [];
};
</script>

<style lang="scss">
@import "@/styles/components/ingressAction/ingress-action-configuration-dialog.scss";
</style>
