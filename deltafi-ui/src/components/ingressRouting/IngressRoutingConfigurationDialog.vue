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
  <div class="ingress-routing-body">
    <div class="information-panel">
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
        <dl>
          <dt>{{ ingressRouteConfigurationMap.get("name").header }}</dt>
          <dd>
            <InputText v-model="selectedRuleName" :placeholder="ingressRouteConfigurationMap.get('name').placeholder" :disabled="ingressRouteConfigurationMap.get('name').disabled" class="inputWidth" />
          </dd>
          <dt>{{ ingressRouteConfigurationMap.get("flow").header }}</dt>
          <dd>
            <Dropdown v-model="selectedRuleFlow" :options="ingressFlowNames.map((a) => a.name)" :placeholder="ingressRouteConfigurationMap.get('flow').placeholder" :disabled="ingressRouteConfigurationMap.get('flow').disabled" show-clear class="inputWidth" />
          </dd>
          <dt>{{ ingressRouteConfigurationMap.get("priority").header }}</dt>
          <dd class="mb-0">
            <InputNumber id="priorityId" v-model="selectedRulePriority" show-buttons :min="ingressRouteConfigurationMap.get('priority').min" :max="ingressRouteConfigurationMap.get('priority').max" decrement-button-class="p-button-secondary" increment-button-class="p-button-secondary" increment-button-icon="pi pi-plus" decrement-button-icon="pi pi-minus" :disabled="ingressRouteConfigurationMap.get('priority').disabled" />
          </dd>
          <dd>
            <small id="priorityId-help">Priority defaults to 500 unless changed.</small>
          </dd>
          <template v-if="_.isEmpty(selectedRequiredMetadata)">
            <dt>{{ ingressRouteConfigurationMap.get("filenameRegex").header }}</dt>
            <dd>
              <InputText v-model="selectedFilenameRegex" :placeholder="ingressRouteConfigurationMap.get('filenameRegex').placeholder" :disabled="ingressRouteConfigurationMap.get('filenameRegex').disabled" class="inputWidth" />
            </dd>
          </template>
          <template v-if="_.isEmpty(selectedFilenameRegex)">
            <dt>{{ ingressRouteConfigurationMap.get("requiredMetadata").header }}</dt>
            <dd class="d-flex">
              <div class="inputWidth">
                <Chips v-model="selectedRequiredMetadata" separator="," add-on-blur :allow-duplicate="false" :disabled="ingressRouteConfigurationMap.get('requiredMetadata').disabled" class="chipInputWidth" @add="addMetadata" @remove="removeMetadata" />
                <div>
                  <small id="priorityId-help">Enter text in key:value format(ex: testKey:testValue).</small>
                </div>
              </div>
              <template v-if="!viewIngressRouteRule">
                <IngressRoutingMetadataEditDialog :variable="rowdata" class="pl-2" @save-metadata="addMetadataFromEditDialog">
                  <Button icon="pi pi-plus" class="p-button-rounded p-button-secondary" />
                </IngressRoutingMetadataEditDialog>
              </template>
            </dd>
          </template>
        </dl>
      </div>
    </div>
    <template v-if="!viewIngressRouteRule">
      <div class="ml-n3 mr-n4">
        <Divider />
      </div>
      <div class="float-right">
        <Button label="Submit" @click="submit()" />
      </div>
    </template>
  </div>
</template>

<script setup>
import IngressRoutingMetadataEditDialog from "@/components/ingressRouting/IngressRoutingMetadataEditDialog";
import useIngressRoutingConfiguration from "@/composables/useIngressRoutingConfiguration";
import useIngressRoutingQueryBuilder from "@/composables/useIngressRoutingQueryBuilder";
import useFlows from "@/composables/useFlows";
import useNotifications from "@/composables/useNotifications";
import { defineEmits, defineProps, onMounted, reactive, ref } from "vue";

import Button from "primevue/button";
import Chips from "primevue/chips";
import Divider from "primevue/divider";
import Dropdown from "primevue/dropdown";
import InputNumber from "primevue/inputnumber";
import InputText from "primevue/inputtext";
import Message from "primevue/message";
import _ from "lodash";

const props = defineProps({
  rowDataProp: {
    type: Object,
    required: false,
    default: Object,
  },
  viewIngressRouteRule: {
    type: Boolean,
    required: false,
    default: false,
  },
  closeDialogCommand: {
    type: Object,
    default: null,
  },
});

const rowdata = reactive(JSON.parse(JSON.stringify(props.rowDataProp)));
const { viewIngressRouteRule, closeDialogCommand } = reactive(props);
const emit = defineEmits(["reloadIngressRoutes"]);
const { validateIngressRouteFile } = useIngressRoutingConfiguration();
const { loadFlowAssignmentRules } = useIngressRoutingQueryBuilder();
const { ingressFlows: ingressFlowNames, fetchIngressFlows } = useFlows();
const notify = useNotifications();

const newIngressRouteRuleUpload = ref({});

const errorsList = ref([]);

const ingressRouteConfigurationMap = new Map([
  ["name", { header: "Name", placeholder: "e.g. fileNameRegex, fileNameRequiredMetadata ", type: "string", disabled: viewIngressRouteRule }],
  ["flow", { header: "Flow", placeholder: "e.g. smoke, passthrough", type: "string", disabled: viewIngressRouteRule }],
  ["priority", { header: "Priority", placeholder: "A number greater than 0", type: "number", min: 0, max: null, disabled: viewIngressRouteRule }],
  ["filenameRegex", { header: "Filename Regex", placeholder: "e.g. ^abc.*, [Dd]eltafi", type: "string", disabled: viewIngressRouteRule }],
  ["requiredMetadata", { header: "Required Metadata", placeholder: "Format strings key:value", type: "string", disabled: viewIngressRouteRule }],
]);

const selectedRuleId = ref(_.get(rowdata, "id", null));
const selectedRuleName = ref(_.get(rowdata, "name", null));
const selectedRuleFlow = ref(_.get(rowdata, "flow", null));
const selectedRulePriority = ref(_.get(rowdata, "priority", 500));
const selectedFilenameRegex = ref(_.get(rowdata, "filenameRegex", null));

onMounted(async () => {
  fetchIngressFlows();
});

const viewList = (value) => {
  if (_.isEmpty(value)) {
    return null;
  }
  // Combine objects of Key Name and Value Name into a key value pair
  let combineKeyValue = value.reduce((r, { key, value }) => ((r[key] = value), r), {});

  // Turn Object into string
  let stringifyCombineKeyValue = Object.entries(combineKeyValue)
    .map(([k, v]) => `${k}: ${v}`)
    .join(", ");

  return stringifyCombineKeyValue.split(",").map((i) => i.trim());
};

const selectedRequiredMetadata = ref(viewList(_.get(rowdata, "requiredMetadata", null)));

const addMetadata = (event) => {
  rowdata.requiredMetadata = validateRequiredMetadata(event.value);
};

const removeMetadata = () => {
  rowdata.requiredMetadata = validateRequiredMetadata(selectedRequiredMetadata.value);
};

function isJsonString(str) {
  try {
    JSON.parse(str);
  } catch (e) {
    return false;
  }
  return true;
}

const validateRequiredMetadata = (metadata) => {
  let arrayOfNewObjects = [];
  let validationErrors = [];
  for (var k in metadata) {
    if (!metadata[k].includes(":")) {
      validationErrors.push(`requiredMetadata: ${metadata[k]} is not a valid key:value format(ex: testKey:testValue).`);
      continue;
    }

    var newKeyValueObj = {};
    let tup = metadata[k].split(":");

    newKeyValueObj["key"] = tup[0].trim();
    newKeyValueObj["value"] = tup[1].trim();

    if (isJsonString(newKeyValueObj)) {
      validationErrors.push(`requiredMetadata: ${metadata[k]} is not valid JSON.`);
      continue;
    }

    arrayOfNewObjects.push(newKeyValueObj);
  }

  if (!_.isEmpty(validationErrors)) {
    errorsList.value = validationErrors;
    selectedRequiredMetadata.value.pop();
  }

  return arrayOfNewObjects;
};

const clearErrors = () => {
  errorsList.value = [];
};

const addMetadataFromEditDialog = (dialogMetadata) => {
  selectedRequiredMetadata.value = dialogMetadata.split(",").map((i) => i.trim());
};

const createNewRule = () => {
  newIngressRouteRuleUpload.value = [];

  var newIngressRouteRule = {};
  newIngressRouteRule["id"] = selectedRuleId.value;
  newIngressRouteRule["name"] = selectedRuleName.value;
  newIngressRouteRule["flow"] = selectedRuleFlow.value;
  newIngressRouteRule["priority"] = selectedRulePriority.value;
  newIngressRouteRule["filenameRegex"] = selectedFilenameRegex.value;

  // If there is requiredMetadata reformat it into JSON and add it to the new rule
  newIngressRouteRule["requiredMetadata"] = validateRequiredMetadata(selectedRequiredMetadata.value);

  newIngressRouteRuleUpload.value.push(newIngressRouteRule);
};

const submit = async () => {
  createNewRule();

  let uploadNotValid = validateIngressRouteFile(JSON.stringify(newIngressRouteRuleUpload.value));
  errorsList.value = [];
  if (uploadNotValid) {
    for (let errorMessages of uploadNotValid) {
      errorsList.value.push(errorMessages.message);
    }
    notify.error(`Ingress Routing Rule Validation Errors`, `Unable to upload Ingress Routing Rule`, 4000);
  } else {
    await loadFlowAssignmentRules(newIngressRouteRuleUpload.value);
    closeDialogCommand.command();
    emit("reloadIngressRoutes");
  }
};
</script>

<style lang="scss">
@import "@/styles/components/ingress-routing-configuration-dialog.scss";
</style>
