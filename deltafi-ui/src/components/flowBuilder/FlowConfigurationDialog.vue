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
  <div class="flow-configuration-dialog">
    <div class="flow-configuration-panel">
      <div class="pb-0">
        <div v-if="hasErrors" class="pt-2">
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
            <InputText v-model="model.name" class="inputWidth" :disabled="editFlowPlan" />
          </dd>
          <dt>Description*</dt>
          <dd>
            <Textarea v-model="model.description" rows="4" cols="47" class="inputWidth" />
          </dd>
          <dt>Type*</dt>
          <dd>
            <Dropdown v-model="model.type" :options="flowTypesDisplay" option-label="header" option-value="field" placeholder="Select Flow Type" :show-clear="!editFlowPlan" :disabled="editFlowPlan" class="inputWidth" />
          </dd>
          <dt>Clone From <small class="text-muted">- Optional</small></dt>
          <dd>
            <Dropdown v-model="model.selectedFlowPlan" :options="_.orderBy(allFlowPlans[`${_.toLower(model.type)}`], [(flow) => flow.name.toLowerCase()], ['asc'])" option-label="name" placeholder="Select Flow" :show-clear="!editFlowPlan" :disabled="editFlowPlan" class="inputWidth" />
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
import useFlowQueryBuilder from "@/composables/useFlowQueryBuilder";
import { useMounted } from "@vueuse/core";
import { computed, defineEmits, defineProps, onMounted, reactive, ref, watch } from "vue";

import Button from "primevue/button";
import Dropdown from "primevue/dropdown";
import InputText from "primevue/inputtext";
import Message from "primevue/message";
import Textarea from "primevue/textarea";
import _ from "lodash";

const { getAllFlows } = useFlowQueryBuilder();

const isMounted = ref(useMounted());
const emit = defineEmits(["createFlowPlan"]);
const props = defineProps({
  dataProp: {
    type: Object,
    required: false,
    default: Object,
  },
  editFlowPlan: {
    type: Boolean,
    required: false,
    default: false,
  },
  closeDialogCommand: {
    type: Object,
    default: null,
  },
});

const flowTemplate = {
  name: null,
  description: null,
  type: null,
  selectedFlowPlan: null,
};

const { editFlowPlan, closeDialogCommand } = reactive(props);
const errors = ref([]);
const flowData = ref(Object.assign({}, props.dataProp || flowTemplate));

const allFlowPlans = ref({});

const model = computed({
  get() {
    return new Proxy(flowData.value, {
      set(obj, key, value) {
        model.value = { ...obj, [key]: value };
        return true;
      },
    });
  },
  set(newValue) {
    Object.assign(
      flowData.value,
      _.mapValues(newValue, (v) => (v === "" ? null : v))
    );
  },
});

// If the type changes or is removed make sure the selectedFlowPlan is null
watch(
  () => model.value.type,
  () => {
    model.value.selectedFlowPlan = null;
  }
);

onMounted(async () => {
  let response = await getAllFlows();

  allFlowPlans.value = response.data.getAllFlows;
});

const flowTypesDisplay = [
  { header: "Transform", field: "TRANSFORM" },
  { header: "Normalize", field: "NORMALIZE" },
  { header: "Enrich", field: "ENRICH" },
  { header: "Egress", field: "EGRESS" },
];

const hasErrors = computed(() => {
  return errors.value.length > 0;
});

const clearErrors = () => {
  errors.value = [];
};

const submit = async () => {
  clearErrors();
  if (_.isEmpty(model.value.type)) {
    errors.value.push("Type field is required.");
  }

  if (_.isEmpty(model.value.name)) {
    errors.value.push("Name field is required.");
  }

  if (_.isEmpty(model.value.description)) {
    errors.value.push("Description field is required.");
  }

  if (!_.isEmpty(model.value.name) && !_.isEmpty(model.value.type)) {
    let activeSystemFlowNames = _.map(allFlowPlans.value[`${_.toLower(model.value.type)}`], "name");
    let isFlowNameUsed = _.includes(activeSystemFlowNames, model.value.name.trim());

    if (isFlowNameUsed && !editFlowPlan) {
      errors.value.push("Name already exists in the system. Choose a different Name.");
    }
  }

  if (!_.isEmpty(errors.value)) {
    return;
  }

  closeDialogCommand.command();
  emit("createFlowPlan", { type: model.value.type, name: model.value.name.trim(), description: model.value.description, selectedFlowPlan: model.value.selectedFlowPlan });
};
</script>

<style lang="scss">
@import "@/styles/components/flowBuilder/flow-configuration-dialog.scss";
</style>
