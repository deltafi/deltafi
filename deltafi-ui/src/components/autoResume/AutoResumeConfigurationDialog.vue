<!--
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>

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
  <div class="auto-resume-body">
    <div class="auto-resume-panel">
      <div class="pb-0">
        <div v-if="!_.isEmpty(errorsList)" class="pt-2" role="alert" aria-live="assertive">
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
          <dt><label for="ar-name">{{ autoResumeConfigurationMap.get("name").header }}<span v-if="autoResumeConfigurationMap.get('name').required" class="required-indicator" aria-hidden="true">*</span></label></dt>
          <dd>
            <InputText id="ar-name" v-model="selectedRuleName" :placeholder="autoResumeConfigurationMap.get('name').placeholder" :disabled="autoResumeConfigurationMap.get('name').disabled" :aria-required="autoResumeConfigurationMap.get('name').required" class="inputWidth" />
          </dd>
          <dt><label for="ar-datasource">{{ autoResumeConfigurationMap.get("dataSource").header }}</label></dt>
          <dd>
            <Select id="ar-datasource" v-model="selectedRuleDataSource" :options="formattedDataSourceNames" option-group-label="label" option-group-children="sources" :placeholder="autoResumeConfigurationMap.get('dataSource').placeholder" :disabled="autoResumeConfigurationMap.get('dataSource').disabled" show-clear class="inputWidth" />
          </dd>
          <dt><label for="ar-action">{{ autoResumeConfigurationMap.get("action").header }}</label></dt>
          <dd>
            <InputText id="ar-action" v-model="selectedRuleAction" :placeholder="autoResumeConfigurationMap.get('action').placeholder" :disabled="autoResumeConfigurationMap.get('action').disabled" class="inputWidth" />
          </dd>
          <dt><label for="ar-error-substring">{{ autoResumeConfigurationMap.get("errorSubstring").header }}</label></dt>
          <dd>
            <InputText id="ar-error-substring" v-model="selectedRuleErrorSubstring" :placeholder="autoResumeConfigurationMap.get('errorSubstring').placeholder" :disabled="autoResumeConfigurationMap.get('errorSubstring').disabled" class="inputWidth" />
          </dd>
          <dt><label for="ar-enabled">{{ autoResumeConfigurationMap.get("enabled").header }}</label></dt>
          <dd>
            <ToggleSwitch id="ar-enabled" v-model="selectedRuleEnabled" :disabled="autoResumeConfigurationMap.get('enabled').disabled" />
          </dd>
          <dt><label for="maxAttemptsId">{{ autoResumeConfigurationMap.get("maxAttempts").header }}<span v-if="autoResumeConfigurationMap.get('maxAttempts').required" class="required-indicator" aria-hidden="true">*</span></label></dt>
          <dd class="mb-0">
            <InputNumber id="maxAttemptsId" v-model="selectedRuleMaxAttempts" aria-describedby="maxAttemptsId-help" :aria-required="autoResumeConfigurationMap.get('maxAttempts').required" show-buttons :min="autoResumeConfigurationMap.get('maxAttempts').min" :max="autoResumeConfigurationMap.get('maxAttempts').max" decrement-button-class="p-button-secondary" increment-button-class="p-button-secondary" increment-button-icon="pi pi-plus" decrement-button-icon="pi pi-minus" :disabled="autoResumeConfigurationMap.get('maxAttempts').disabled" />
          </dd>
          <dd>
            <small id="maxAttemptsId-help">Set the max number of attempts for this rule.</small>
          </dd>
          <dt><label for="priorityId">{{ autoResumeConfigurationMap.get("priority").header }}</label></dt>
          <dd class="mb-0">
            <InputNumber id="priorityId" v-model="selectedPriority" aria-describedby="priorityId-help" show-buttons decrement-button-class="p-button-secondary" increment-button-class="p-button-secondary" increment-button-icon="pi pi-plus" decrement-button-icon="pi pi-minus" :disabled="autoResumeConfigurationMap.get('priority').disabled" />
          </dd>
          <dd>
            <small id="priorityId-help">Set the priority for the rule.</small>
          </dd>
          <dt>BackOff</dt>
          <dd>
            <div class="deltafi-fieldset">
              <div class="px-2 pt-3">
                <dl>
                  <dt><label for="delayId">{{ autoResumeConfigurationMap.get("delay").header }}<span v-if="autoResumeConfigurationMap.get('delay').required" class="required-indicator" aria-hidden="true">*</span></label></dt>
                  <dd class="mb-0">
                    <InputNumber id="delayId" v-model="selectedRuleDelay" aria-describedby="delayId-help" :aria-required="autoResumeConfigurationMap.get('delay').required" show-buttons :min="autoResumeConfigurationMap.get('delay').min" :max="autoResumeConfigurationMap.get('delay').max" decrement-button-class="p-button-secondary" increment-button-class="p-button-secondary" increment-button-icon="pi pi-plus" decrement-button-icon="pi pi-minus" :disabled="autoResumeConfigurationMap.get('delay').disabled" />
                  </dd>
                  <dd>
                    <small id="delayId-help">Delay (in seconds) is a required property and must be set to a non-negative number.</small>
                  </dd>
                  <dt><label for="maxDelayId">{{ autoResumeConfigurationMap.get("maxDelay").header }}</label></dt>
                  <dd class="mb-0">
                    <InputNumber id="maxDelayId" v-model="selectedRuleMaxDelay" aria-describedby="maxDelayId-help" show-buttons :min="autoResumeConfigurationMap.get('maxDelay').min" :max="autoResumeConfigurationMap.get('maxDelay').max" decrement-button-class="p-button-secondary" increment-button-class="p-button-secondary" increment-button-icon="pi pi-plus" decrement-button-icon="pi pi-minus" :disabled="autoResumeConfigurationMap.get('maxDelay').disabled" />
                  </dd>
                  <dd>
                    <small id="maxDelayId-help">Sets the Max Delay (in second) when using Multiplier or Random.</small>
                  </dd>
                  <dt><label for="multiplierId">{{ autoResumeConfigurationMap.get("multiplier").header }}</label></dt>
                  <dd class="mb-0">
                    <InputNumber id="multiplierId" v-model="selectedRuleMultiplier" aria-describedby="multiplierId-help" show-buttons :min="autoResumeConfigurationMap.get('multiplier').min" :max="autoResumeConfigurationMap.get('multiplier').max" decrement-button-class="p-button-secondary" increment-button-class="p-button-secondary" increment-button-icon="pi pi-plus" decrement-button-icon="pi pi-minus" :disabled="autoResumeConfigurationMap.get('multiplier').disabled" />
                  </dd>
                  <dd>
                    <small id="multiplierId-help">Setting Multiplier calculates the delay by multiplying Delay, Multiplier, and the number of attempts for that action.</small>
                  </dd>
                  <dt><label for="randomId">{{ autoResumeConfigurationMap.get("random").header }}</label></dt>
                  <dd class="mb-0">
                    <ToggleSwitch id="randomId" v-model="selectedRuleRandom" aria-describedby="randomId-help" :disabled="autoResumeConfigurationMap.get('random').disabled" />
                  </dd>
                  <dd>
                    <small id="randomId-help">Setting Random calculates the delay by using a random value between the Delay and Max Delay. If Random is true, Max Delay must also be set.</small>
                  </dd>
                </dl>
              </div>
            </div>
          </dd>
        </dl>
      </div>
    </div>
    <teleport v-if="!viewAutoResumeRule && isMounted" to="#dialogTemplate">
      <div class="p-dialog-footer">
        <Button label="Save and Run Now" class="p-button-secondary p-button-outlined" @click="confirmApply($event)" />
        <Button label="Save" class="p-button-primary p-button" @click="submit()" />
      </div>
      <ConfirmPopup />
    </teleport>
  </div>
</template>

<script setup>
import useAutoResumeConfiguration from "@/composables/useAutoResumeConfiguration";
import useAutoResumeQueryBuilder from "@/composables/useAutoResumeQueryBuilder";
import useFlows from "@/composables/useFlows";
import useNotifications from "@/composables/useNotifications";
import { useMounted } from "@vueuse/core";
import { onMounted, reactive, ref } from "vue";

import ConfirmPopup from "primevue/confirmpopup";
import { useConfirm } from "primevue/useconfirm";
import Button from "primevue/button";
import Select from "primevue/select";
import InputNumber from "primevue/inputnumber";
import InputText from "primevue/inputtext";
import Message from "primevue/message";
import ToggleSwitch from "primevue/toggleswitch";
import _ from "lodash";

const props = defineProps({
  rowDataProp: {
    type: Object,
    required: false,
    default: Object,
  },
  viewAutoResumeRule: {
    type: Boolean,
    required: false,
    default: false,
  },
});
const confirm = useConfirm();

const confirmApply = (event) => {
  confirm.require({
    target: event.currentTarget,
    message: "Are you sure you want to save and run this rule now?",
    acceptLabel: "Save and Run Now",
    rejectLabel: "Cancel",
    icon: "pi pi-exclamation-triangle",
    accept: () => {
      submitApply();
    },
    reject: () => { },
  });
};

const rowData = reactive(JSON.parse(JSON.stringify(props.rowDataProp)));
const { viewAutoResumeRule } = reactive(props);
const emit = defineEmits(["refreshAndClose"]);
const { validateAutoResumeFile, validateAutoResumeRule } = useAutoResumeConfiguration();
const { loadResumePolicies, updateResumePolicy, applyResumePolicies } = useAutoResumeQueryBuilder();
const { allDataSourceFlowNames, fetchAllDataSourceFlowNames } = useFlows();
const notify = useNotifications();
const isMounted = ref(useMounted());
const formattedDataSourceNames = ref([]);
const autoResumeRuleUpload = ref(null);
const errorsList = ref([]);

const autoResumeConfigurationMap = new Map([
  ["name", { header: "Name", required: true, placeholder: "e.g. Auto resume smoke error", type: "string", disabled: viewAutoResumeRule }],
  ["dataSource", { header: "Data Source", placeholder: "e.g. smoke, passthrough", type: "string", disabled: viewAutoResumeRule }],
  ["action", { header: "Action", placeholder: "e.g. smoke.SmokeEgressAction", type: "string", disabled: viewAutoResumeRule }],
  ["maxAttempts", { header: "Max Attempts", required: true, placeholder: "A number 2 or greater", type: "number", min: 2, max: null, disabled: viewAutoResumeRule }],
  ["priority", { header: "Priority", placeholder: "A number 0 or greater", type: "number", disabled: viewAutoResumeRule }],
  ["errorSubstring", { header: "Error Substring", placeholder: "Exact substring match; e.g. post failure", type: "string", disabled: viewAutoResumeRule }],
  ["enabled", { header: "Enabled", type: "boolean", disabled: viewAutoResumeRule }],
  ["delay", { header: "Delay", required: true, placeholder: "A number 0 or greater", type: "number", min: 0, max: null, disabled: viewAutoResumeRule }],
  ["maxDelay", { header: "Max Delay", placeholder: "A number greater than 0", type: "number", min: 0, max: null, disabled: viewAutoResumeRule }],
  ["multiplier", { header: "Multiplier", placeholder: "A number greater than 0", type: "number", min: 0, max: null, disabled: viewAutoResumeRule }],
  ["random", { header: "Random", type: "number", min: 0, max: null, disabled: viewAutoResumeRule }],
]);

const ruleId = ref(_.get(rowData, "id", null));
const selectedRuleName = ref(_.get(rowData, "name", null));
const selectedRuleDataSource = ref(_.get(rowData, "dataSource", null));
const selectedRuleAction = ref(_.get(rowData, "action", null));
const selectedRuleErrorSubstring = ref(_.get(rowData, "errorSubstring", null));
const selectedRuleEnabled = ref(_.get(rowData, "enabled", true));
const selectedRuleMaxAttempts = ref(_.get(rowData, "maxAttempts", 2));
const selectedPriority = ref(_.get(rowData, "priority", 0));
const selectedRuleDelay = ref(_.get(rowData, "backOff.delay", 0));
const selectedRuleMaxDelay = ref(_.get(rowData, "backOff.maxDelay", null));
const selectedRuleMultiplier = ref(_.get(rowData, "backOff.multiplier", null));
const selectedRuleRandom = ref(_.get(rowData, "backOff.random", false));

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

const clearErrors = () => {
  errorsList.value = [];
};

const createNewRule = () => {
  autoResumeRuleUpload.value = null;

  const autoResumeRule = {};

  if (ruleId.value) {
    autoResumeRule["id"] = ruleId.value;
  }

  autoResumeRule["enabled"] = selectedRuleEnabled.value;

  if (selectedRuleName.value) {
    autoResumeRule["name"] = selectedRuleName.value;
  }

  if (selectedRuleDataSource.value) {
    autoResumeRule["dataSource"] = selectedRuleDataSource.value;
  }

  if (selectedRuleAction.value) {
    autoResumeRule["action"] = selectedRuleAction.value;
  }

  if (selectedRuleErrorSubstring.value) {
    autoResumeRule["errorSubstring"] = selectedRuleErrorSubstring.value;
  }

  autoResumeRule["maxAttempts"] = selectedRuleMaxAttempts.value;

  if (selectedPriority.value) {
    autoResumeRule["priority"] = selectedPriority.value;
  }

  const backOffObject = {};
  if (selectedRuleDelay.value != null) {
    backOffObject["delay"] = selectedRuleDelay.value;
  }

  if (selectedRuleMaxDelay.value) {
    backOffObject["maxDelay"] = selectedRuleMaxDelay.value;
  }

  if (selectedRuleMultiplier.value) {
    backOffObject["multiplier"] = selectedRuleMultiplier.value;
  }

  if (selectedRuleRandom.value) {
    backOffObject["random"] = selectedRuleRandom.value;
  }

  autoResumeRule["backOff"] = backOffObject;

  if (ruleId.value) {
    autoResumeRuleUpload.value = autoResumeRule;
  } else {
    autoResumeRuleUpload.value = [];
    autoResumeRuleUpload.value.push(autoResumeRule);
  }
};
const submitApply = async () => {
  await submit(true);
  if (selectedRuleName.value != null) {
    applyResumeSingle(selectedRuleName.value);
  }
};

const applyResumeSingle = async (ruleName) => {
  let response = null;
  response = await applyResumePolicies([ruleName]);
  if (response.data.applyResumePolicies.success) {
    notify.success("Auto Resume Rule Ran Successfully", response.data.applyResumePolicies.info);
  } else {
    notify.error("Auto Resume Rule Failed to Run", response.data.applyResumePolicies.errors);
  }
};
const submit = async () => {
  createNewRule();

  let uploadNotValid = null;
  if (ruleId.value) {
    uploadNotValid = validateAutoResumeRule(JSON.stringify(autoResumeRuleUpload.value));
  } else {
    uploadNotValid = validateAutoResumeFile(JSON.stringify(autoResumeRuleUpload.value));
  }
  errorsList.value = [];
  if (uploadNotValid) {
    for (const errorMessages of uploadNotValid) {
      errorsList.value.push(errorMessages.message);
    }
    notify.error(`Auto Resume Rule Validation Errors`, `Unable to upload Auto Resume Rule`, 4000);
  } else {
    let response = null;
    let uploadErrorsList = null;
    if (ruleId.value) {
      response = await updateResumePolicy(autoResumeRuleUpload.value);
      uploadErrorsList = response.data.updateResumePolicy;
    } else {
      response = await loadResumePolicies(autoResumeRuleUpload.value);
      uploadErrorsList = response.data.loadResumePolicies;
    }

    if (!_.isEmpty(_.get(uploadErrorsList[0], "errors", null))) {
      for (const errorMessages of uploadErrorsList[0].errors) {
        errorsList.value.push(errorMessages);
      }
      notify.error(`Auto Resume Upload failed`, "Unable to update Auto Resume Rules", 4000);
    } else {
      emit("refreshAndClose");
    }
  }
};
</script>

<style>
.auto-resume-body {
  width: 98%;

  .auto-resume-panel {
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
      border: 1px solid var(--df-input-border, #ced4da);
      border-width: 1px;
      border-style: groove;
      border-color: var(--df-border-color, rgb(225, 225, 225));
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
    width: 100% !important;
  }

  .p-inputchips .p-inputchips-input {
    padding: 0.25rem 0.75rem;
    width: 100% !important;
  }

  .chipInputWidth {
    width: 100% !important;
  }
}
</style>
