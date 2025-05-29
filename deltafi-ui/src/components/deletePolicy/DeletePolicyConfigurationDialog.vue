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
  <div class="delete-policy-configuration-dialog">
    <div class="delete-policy-panel">
      <div class="pb-0">
        <div v-if="!_.isEmpty(errorsList)" class="pt-2">
          <Message severity="error" :sticky="true" class="mb-2 mt-0" @close="clearErrors()">
            <ul>
              <div v-for="(error, key) in errorsList" :key="key">
                <li class="text-wrap text-break">
                  {{ error }}
                </li>
              </div>
            </ul>
          </Message>
        </div>
        <dl>
          <dt>{{ deletePolicyConfigurationMap.get("name").header }}</dt>
          <dd>
            <InputText v-model="selectedDeleteName" :placeholder="deletePolicyConfigurationMap.get('name').placeholder" :disabled="deletePolicyConfigurationMap.get('name').disabled" class="inputWidth" />
          </dd>
          <dt>{{ deletePolicyConfigurationMap.get("dataSource").header }}</dt>
          <dd>
            <Dropdown v-model="selectedDeleteFlow" :options="formattedDataSourceNames" option-group-label="label" option-group-children="sources" :placeholder="deletePolicyConfigurationMap.get('dataSource').placeholder" :disabled="deletePolicyConfigurationMap.get('dataSource').disabled" show-clear class="inputWidth" />
          </dd>
          <dt v-if="deletePolicyTypes.length > 1">{{ deletePolicyConfigurationMap.get("__typename").header }}</dt>
          <dd v-if="deletePolicyTypes.length > 1">
            <Dropdown v-model="selectedDeleteType" :options="deletePolicyTypes" :placeholder="deletePolicyConfigurationMap.get('__typename').placeholder" :disabled="deletePolicyConfigurationMap.get('__typename').disabled" class="inputWidth" />
          </dd>
          <dt>{{ deletePolicyConfigurationMap.get("enabled").header }}</dt>
          <dd>
            <InputSwitch v-model="selectedEnabledBoolean" :disabled="deletePolicyConfigurationMap.get('enabled').disabled" />
          </dd>
          <template v-if="selectedDeleteType">
            <template v-if="_.isEqual(selectedDeleteType, 'TimedDeletePolicy')">
              <dt>Time Delete Policy Options</dt>
              <dd>
                <div class="deltafi-fieldset">
                  <div class="px-2 pt-3">
                    <template v-if="_.isEmpty(selectedAfterComplete)">
                      <dt>{{ deletePolicyConfigurationMap.get("afterCreate").header }}</dt>
                      <dd>
                        <InputText v-model="selectedAfterCreate" :placeholder="deletePolicyConfigurationMap.get('afterCreate').placeholder" :disabled="deletePolicyConfigurationMap.get('afterCreate').disabled" class="inputWidth capitalizeText" />
                      </dd>
                    </template>
                    <template v-if="_.isEmpty(selectedAfterCreate)">
                      <dt>{{ deletePolicyConfigurationMap.get("afterComplete").header }}</dt>
                      <dd>
                        <InputText v-model="selectedAfterComplete" :placeholder="deletePolicyConfigurationMap.get('afterComplete').placeholder" :disabled="deletePolicyConfigurationMap.get('afterComplete').disabled" class="inputWidth capitalizeText" />
                      </dd>
                    </template>
                    <dt>{{ deletePolicyConfigurationMap.get("minBytes").header }}</dt>
                    <dd>
                      <InputNumber v-model="selectedMinBytes" show-buttons :min="deletePolicyConfigurationMap.get('minBytes').min" :max="deletePolicyConfigurationMap.get('minBytes').max" decrement-button-class="p-button-secondary" increment-button-class="p-button-secondary" increment-button-icon="pi pi-plus" decrement-button-icon="pi pi-minus" :disabled="deletePolicyConfigurationMap.get('minBytes').disabled" />
                    </dd>
                    <dt>{{ deletePolicyConfigurationMap.get("deleteMetadata").header }}</dt>
                    <dd>
                      <InputSwitch v-model="selectedDeleteMetadata" :disabled="deletePolicyConfigurationMap.get('deleteMetadata').disabled" />
                    </dd>
                  </div>
                </div>
              </dd>
            </template>
          </template>
        </dl>
      </div>
    </div>
    <teleport v-if="isMounted && !viewDeletePolicy" to="#dialogTemplate">
      <div class="p-dialog-footer">
        <Button label="Submit" @click="submit()" />
      </div>
    </teleport>
  </div>
</template>

<script setup>
import useDeletePolicyConfiguration from "@/composables/useDeletePolicyConfiguration";
import useDeletePolicyQueryBuilder from "@/composables/useDeletePolicyQueryBuilder";
import useFlows from "@/composables/useFlows";
import useNotifications from "@/composables/useNotifications";
import { useMounted } from "@vueuse/core";
import { onMounted, reactive, ref } from "vue";

import Button from "primevue/button";
import Dropdown from "primevue/dropdown";
import InputNumber from "primevue/inputnumber";
import InputSwitch from "primevue/inputswitch";
import InputText from "primevue/inputtext";
import Message from "primevue/message";
import _ from "lodash";

const props = defineProps({
  rowDataProp: {
    type: Object,
    required: false,
    default: Object,
  },
  editDeletePolicy: {
    type: Boolean,
    required: false,
    default: false,
  },
  viewDeletePolicy: {
    type: Boolean,
    required: false,
    default: false,
  },
});

const { rowDataProp: rowData, editDeletePolicy, viewDeletePolicy } = reactive(props);
const emit = defineEmits(["refreshAndClose"]);
const { validateDeletePolicyFile } = useDeletePolicyConfiguration();
const { loadDeletePolicies } = useDeletePolicyQueryBuilder();
const { allDataSourceFlowNames, fetchAllDataSourceFlowNames } = useFlows();
const notify = useNotifications();

const deletePolicyTypes = ref(["TimedDeletePolicy"]);
const newDeletePolicyUpload = ref({});
const formattedDataSourceNames = ref([]);
const errorsList = ref([]);

const deletePolicyConfigurationMap = new Map([
  ["name", { header: "Name*", placeholder: "e.g. oneHourAfterComplete", type: "string", disabled: viewDeletePolicy }],
  ["__typename", { header: "Type*", placeholder: "e.g. TimedDeletePolicy", type: "select", disabled: viewDeletePolicy || editDeletePolicy }],
  ["dataSource", { header: "Data Source", placeholder: "e.g. smoke, passthrough", type: "string", disabled: viewDeletePolicy }],
  ["enabled", { header: "Enabled", type: "boolean", disabled: viewDeletePolicy }],
  ["afterCreate", { header: "After Create", placeholder: "Duration in ISO 1806 notation. e.g. PT1H, P23DT23H, P4Y", type: "string", disabled: viewDeletePolicy }],
  ["afterComplete", { header: "After Complete", placeholder: "Duration in ISO 1806 notation. e.g. PT1H, P23DT23H, P4Y", type: "string", disabled: viewDeletePolicy }],
  ["minBytes", { header: "Min Bytes", placeholder: "e.g. oneHourAfterComplete, over98PerCent", type: "number", min: null, max: null, disabled: viewDeletePolicy }],
  ["deleteMetadata", { header: "Delete Metadata", placeholder: "e.g. oneHourAfterComplete, over98PerCent", type: "boolean", disabled: viewDeletePolicy }],
  ["maxPercent", { header: "Max Percent*", placeholder: "A number between 0 and 100", type: "number", min: 0, max: 100, disabled: viewDeletePolicy }],
]);

const selectedDeleteId = ref(_.get(rowData, "id", null));
const selectedDeleteName = ref(_.get(rowData, "name", null));
const selectedDeleteFlow = ref(_.isEmpty(_.get(rowData, "flow")) || _.isEqual(_.get(rowData, "flow"), "All") ? null : rowData["flow"]);
const selectedDeleteType = ref(_.get(rowData, "__typename", "TimedDeletePolicy"));
const selectedEnabledBoolean = ref(_.get(rowData, "enabled", false));
const selectedAfterCreate = ref(_.get(rowData, "afterCreate", null));
const selectedAfterComplete = ref(_.get(rowData, "afterComplete", null));
const selectedMinBytes = ref(_.get(rowData, "minBytes", null));
const selectedDeleteMetadata = ref(_.get(rowData, "deleteMetadata", false));
const isMounted = ref(useMounted());

onMounted(async () => {
  await fetchAllDataSourceFlowNames();
  formatDataSourceNames();
});

const formatDataSourceNames = () => {
  if (!_.isEmpty(allDataSourceFlowNames.value.restDataSource)) {
    formattedDataSourceNames.value.push({ label: "Rest Data Sources", sources: allDataSourceFlowNames.value.restDataSource });
  }
  if (!_.isEmpty(allDataSourceFlowNames.value.timedDataSource)) {
    formattedDataSourceNames.value.push({ label: "Timed Data Sources", sources: allDataSourceFlowNames.value.timedDataSource });
  }
};

const createNewPolicy = () => {
  const newDeletePolicy = {};
  newDeletePolicyUpload.value = {};
  newDeletePolicyUpload.value["timedPolicies"] = [];

  newDeletePolicy["id"] = selectedDeleteId.value;
  newDeletePolicy["name"] = selectedDeleteName.value;
  if (_.isEqual(selectedDeleteFlow.value, "All")) {
    newDeletePolicy["flow"] = null;
  } else {
    newDeletePolicy["flow"] = selectedDeleteFlow.value;
  }

  newDeletePolicy["enabled"] = selectedEnabledBoolean.value;

  if (_.isEqual(selectedDeleteType.value, "TimedDeletePolicy")) {
    newDeletePolicy["afterCreate"] = selectedAfterCreate.value ? selectedAfterCreate.value.toUpperCase() : null;
    newDeletePolicy["afterComplete"] = selectedAfterComplete.value ? selectedAfterComplete.value.toUpperCase() : null;
    newDeletePolicy["minBytes"] = selectedMinBytes.value;
    newDeletePolicy["deleteMetadata"] = selectedDeleteMetadata.value;
    newDeletePolicyUpload.value["timedPolicies"].push(newDeletePolicy);
  }
};

const submit = async () => {
  createNewPolicy();

  const uploadNotValid = validateDeletePolicyFile(JSON.stringify(newDeletePolicyUpload.value));
  errorsList.value = [];
  if (uploadNotValid) {
    for (const errorMessages of uploadNotValid) {
      errorsList.value.push(errorMessages.message);
    }
    notify.error(`Delete Policy Validation Errors`, `Unable to upload Delete Policy `, 4000);
  } else {
    const response = await loadDeletePolicies(newDeletePolicyUpload.value);

    if (!_.isEmpty(_.get(response, "errors", null))) {
      notify.error(`Upload failed`, `Unable to update Delete Policy.`, 4000);
      return;
    }

    if (response.data.loadDeletePolicies.success) {
      notify.success("Saved Delete Policy", `Successfully saved delete policy ${selectedDeleteName.value}.`, 3000);
      emit("refreshAndClose");
      return;
    }

    const responseErrors = response.data.loadDeletePolicies.errors;
    if (!_.isEmpty(responseErrors)) {
      for (const errorMessage of responseErrors) {
        errorsList.value.push(_.trim(errorMessage));
      }
      notify.error(`Policy format error`, `Unable to update Delete Policy.`, 4000);
    }
  }
};

const clearErrors = () => {
  errorsList.value = [];
};
</script>

<style>
.delete-policy-configuration-dialog {
  width: 98%;

  .delete-policy-panel {
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

  .inputWidth {
    width: 90% !important;
  }

  .p-filled.capitalizeText {
    text-transform: uppercase;
  }
}
</style>
