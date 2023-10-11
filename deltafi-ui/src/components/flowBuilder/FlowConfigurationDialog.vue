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
  <div>
    <div v-if="hasErrors" class="pt-2">
      <Message severity="error" :sticky="true" class="mb-2 mt-0" @close="clearErrors()">
        <ul>
          <div v-for="(error, key) in errors" :key="key">
            <li class="text-wrap text-break">{{ error }}</li>
          </div>
        </ul>
      </Message>
    </div>
    <dt>Type:</dt>
    <dd>
      <Dropdown v-model="model.type" :options="flowTypesDisplay" option-label="header" option-value="field" placeholder="Select Flow Type" :show-clear="!editFlowPlan" :disabled="editFlowPlan" />
    </dd>
    <dt>Name:</dt>
    <dd>
      <InputText v-model="model.name" class="inputWidth" />
    </dd>
    <dt>Description:</dt>
    <dd>
      <Textarea v-model="model.description" rows="4" cols="47" />
    </dd>
    <div class="delete-policy-configuration-dialog">
      <teleport v-if="isMounted" to="#dialogTemplate">
        <div class="p-dialog-footer">
          <Button label="Submit" @click="submit()" />
        </div>
      </teleport>
    </div>
  </div>
</template>
    
    <script setup>
import { useMounted } from "@vueuse/core";
import { computed, defineEmits, defineProps, reactive, ref } from "vue";

import Button from "primevue/button";
import Dropdown from "primevue/dropdown";
import InputText from "primevue/inputtext";
import Message from "primevue/message";
import Textarea from "primevue/textarea";
import _ from "lodash";

const isMounted = ref(useMounted());
const emit = defineEmits(["newFlow", "updateFlow"]);
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
  type: null,
  name: null,
  description: null,
};

const { editFlowPlan, closeDialogCommand } = reactive(props);
const errors = ref([]);
const flowData = ref(Object.assign({}, props.dataProp || flowTemplate));

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

  if (!_.isEmpty(errors.value)) {
    return;
  }

  closeDialogCommand.command();
  if (editFlowPlan) {
    emit("updateFlow", { type: model.value.type, name: model.value.name, description: model.value.description });
  } else {
    emit("newFlow", { type: model.value.type, name: model.value.name, description: model.value.description });
  }
};
</script>
    
    <style lang="scss">
</style>
    