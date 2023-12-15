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
  <div class="delete-policy-configuration-dialog">
    <div class="delete-policy-panel">
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
          <dt>{{ deletePolicyConfigurationMap.get("name").header }}</dt>
          <dd>
            <InputText v-model="selectedDeleteName" :placeholder="deletePolicyConfigurationMap.get('name').placeholder" :disabled="deletePolicyConfigurationMap.get('name').disabled" class="inputWidth" />
          </dd>
          <dt>{{ deletePolicyConfigurationMap.get("flow").header }}</dt>
          <dd>
            <Dropdown v-model="selectedDeleteflow" :options="ingressFlowNames" :placeholder="deletePolicyConfigurationMap.get('flow').placeholder" :disabled="deletePolicyConfigurationMap.get('flow').disabled" show-clear class="inputWidth" />
          </dd>
          <dt>{{ deletePolicyConfigurationMap.get("__typename").header }}</dt>
          <dd>
            <Dropdown v-model="selectedDeleteType" :options="deletePolicyTypes" :placeholder="deletePolicyConfigurationMap.get('__typename').placeholder" :disabled="deletePolicyConfigurationMap.get('__typename').disabled" class="inputWidth" />
          </dd>
          <dt>{{ deletePolicyConfigurationMap.get("enabled").header }}</dt>
          <dd>
            <InputSwitch v-model="selectedEnabledBoolean" :disabled="deletePolicyConfigurationMap.get('enabled').disabled" />
          </dd>
          <div v-if="selectedDeleteType">
            <template v-if="_.isEqual(selectedDeleteType, 'TimedDeletePolicy')">
              <Divider align="left">
                <div class="inline-flex align-items-center">
                  <i class="far fa-clock text-muted ml-1"></i>
                  <b> Time Delete Policy Options</b>
                </div>
              </Divider>
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
            </template>
            <template v-else>
              <Divider align="left">
                <div class="inline-flex align-items-center">
                  <i class="fas fa-hdd text-muted ml-1"></i>
                  <b> Disk Space Delete Policy Options</b>
                </div>
              </Divider>
              <dt>{{ deletePolicyConfigurationMap.get("maxPercent").header }}</dt>
              <dd>
                <InputNumber v-model="selectedMaxPercent" show-buttons :min="deletePolicyConfigurationMap.get('maxPercent').min" :max="deletePolicyConfigurationMap.get('maxPercent').max" decrement-button-class="p-button-secondary" increment-button-class="p-button-secondary" increment-button-icon="pi pi-plus" decrement-button-icon="pi pi-minus" :disabled="deletePolicyConfigurationMap.get('maxPercent').disabled" />
              </dd>
            </template>
          </div>
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
import { defineEmits, defineProps, onMounted, reactive, ref } from "vue";

import Button from "primevue/button";
import Divider from "primevue/divider";
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
  closeDialogCommand: {
    type: Object,
    default: null,
  },
});

const { rowDataProp: rowdata, editDeletePolicy, viewDeletePolicy, closeDialogCommand } = reactive(props);
const emit = defineEmits(["reloadDeletePolicies"]);
const { validateDeletePolicyFile } = useDeletePolicyConfiguration();
const { loadDeletePolicies } = useDeletePolicyQueryBuilder();
const { ingressFlows: ingressFlowNames, fetchIngressFlowNames } = useFlows();
const notify = useNotifications();

const deletePolicyTypes = ref(["TimedDeletePolicy", "DiskSpaceDeletePolicy"]);

const newDeletePolicyUpload = ref({});

const errorsList = ref([]);

const deletePolicyConfigurationMap = new Map([
  ["name", { header: "Name*", placeholder: "e.g. oneHourAfterComplete, over98PerCent", type: "string", disabled: viewDeletePolicy }],
  ["__typename", { header: "Type*", placeholder: "e.g. TimedDeletePolicy, DiskSpaceDeletePolicy", type: "select", disabled: viewDeletePolicy || editDeletePolicy }],
  ["flow", { header: "Flow", placeholder: "e.g. smoke, passthrough", type: "string", disabled: viewDeletePolicy }],
  ["enabled", { header: "Enabled", type: "boolean", disabled: viewDeletePolicy }],
  ["afterCreate", { header: "After Create", placeholder: "Duration in ISO 1806 notation. e.g. PT1H, P23DT23H, P4Y", type: "string", disabled: viewDeletePolicy }],
  ["afterComplete", { header: "After Complete", placeholder: "Duration in ISO 1806 notation. e.g. PT1H, P23DT23H, P4Y", type: "string", disabled: viewDeletePolicy }],
  ["minBytes", { header: "Min Bytes", placeholder: "e.g. oneHourAfterComplete, over98PerCent", type: "number", min: null, max: null, disabled: viewDeletePolicy }],
  ["deleteMetadata", { header: "Delete Metadata", placeholder: "e.g. oneHourAfterComplete, over98PerCent", type: "boolean", disabled: viewDeletePolicy }],
  ["maxPercent", { header: "Max Percent*", placeholder: "A number between 0 and 100", type: "number", min: 0, max: 100, disabled: viewDeletePolicy }],
]);

const selectedDeleteId = ref(_.get(rowdata, "id", null));
const selectedDeleteName = ref(_.get(rowdata, "name", null));
const selectedDeleteflow = ref(_.isEmpty(_.get(rowdata, "flow")) || _.isEqual(_.get(rowdata, "flow"), "All") ? null : rowdata["flow"]);
const selectedDeleteType = ref(_.get(rowdata, "__typename", null));
const selectedEnabledBoolean = ref(_.get(rowdata, "enabled", false));
const selectedAfterCreate = ref(_.get(rowdata, "afterCreate", null));
const selectedAfterComplete = ref(_.get(rowdata, "afterComplete", null));
const selectedMinBytes = ref(_.get(rowdata, "minBytes", null));
const selectedDeleteMetadata = ref(_.get(rowdata, "deleteMetadata", false));
const selectedMaxPercent = ref(_.get(rowdata, "maxPercent", null));
const isMounted = ref(useMounted());

onMounted(async () => {
  await fetchIngressFlowNames();
});

const createNewPolicy = () => {
  let newDeletePolicy = {};
  newDeletePolicyUpload.value = {};
  newDeletePolicyUpload.value["timedPolicies"] = [];
  newDeletePolicyUpload.value["diskSpacePolicies"] = [];

  newDeletePolicy["id"] = selectedDeleteId.value;
  newDeletePolicy["name"] = selectedDeleteName.value;
  if (_.isEqual(selectedDeleteflow.value, "All")) {
    newDeletePolicy["flow"] = null;
  } else {
    newDeletePolicy["flow"] = selectedDeleteflow.value;
  }

  newDeletePolicy["enabled"] = selectedEnabledBoolean.value;

  if (_.isEqual(selectedDeleteType.value, "TimedDeletePolicy")) {
    newDeletePolicy["afterCreate"] = selectedAfterCreate.value ? selectedAfterCreate.value.toUpperCase() : null;
    newDeletePolicy["afterComplete"] = selectedAfterComplete.value ? selectedAfterComplete.value.toUpperCase() : null;
    newDeletePolicy["minBytes"] = selectedMinBytes.value;
    newDeletePolicy["deleteMetadata"] = selectedDeleteMetadata.value;
    newDeletePolicyUpload.value["timedPolicies"].push(newDeletePolicy);
  }

  if (_.isEqual(selectedDeleteType.value, "DiskSpaceDeletePolicy")) {
    newDeletePolicy["maxPercent"] = selectedMaxPercent.value;
    newDeletePolicyUpload.value["diskSpacePolicies"].push(newDeletePolicy);
  }
};

const submit = async () => {
  createNewPolicy();

  let uploadNotValid = validateDeletePolicyFile(JSON.stringify(newDeletePolicyUpload.value));
  errorsList.value = [];
  if (uploadNotValid) {
    for (let errorMessages of uploadNotValid) {
      errorsList.value.push(errorMessages.message);
    }
    notify.error(`Delete Policy Validation Errors`, `Unable to upload Delete Policy `, 4000);
  } else {
    let response = await loadDeletePolicies(newDeletePolicyUpload.value);
    if (!_.isEmpty(_.get(response, "errors", null))) {
      notify.error(`Upload failed`, `Unable to update Delete Policy.`, 4000);
    }
    closeDialogCommand.command();
    emit("reloadDeletePolicies");
  }
};

const clearErrors = () => {
  errorsList.value = [];
};
</script>

<style lang="scss">
@import "@/styles/components/deletePolicy/delete-policy-configuration-dialog.scss";
</style>
