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
  <div id="error-message" class="map-edit">
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
    <div v-for="(pair, i) in pairs" :key="i" class="map-row mb-2 align-items-center">
      <div class="mr-2" :style="{ width: '30px', 'text-align': 'right' }">{{ pair.column }}{{ props.rowDataProp.keyColumns.includes(pair.column) ? "*" : "" }}:</div>
      <InputText v-model="pair.value" class="mr-2 inputWidth" />
    </div>
    <teleport v-if="isMounted" to="#dialogTemplate">
      <div class="p-dialog-footer">
        <Button label="Submit" @click="submit()" />
      </div>
    </teleport>
  </div>
</template>

<script setup>
import useLookupTable from "@/composables/useLookupTable";
import useNotifications from "@/composables/useNotifications";
import { nextTick, onMounted, ref, watch } from "vue";
import { useMounted } from "@vueuse/core";

import _ from "lodash";

import Button from "primevue/button";
import InputText from "primevue/inputtext";
import Message from "primevue/message";

const emit = defineEmits(["update:modelValue", "refreshAndClose"]);
const notify = useNotifications();
const { upsertLookupTableRows } = useLookupTable();
const props = defineProps({
  rowDataProp: {
    type: Object,
    required: true,
  },
  modelValue: {
    type: String,
    required: false,
    default: "",
  },
});
const pairs = ref([]);
const errors = ref([]);

const loadPairsFromModelValue = () => {
  if (props.modelValue === null || props.modelValue === "") {
    pairs.value = [];
  } else {
    pairs.value = props.modelValue
      .split(",")
      .map((i) => i.split(":"))
      .map((j) => {
        return { column: j[0].trim(), value: j[1].trim() };
      });
  }
};

onMounted(() => {
  loadPairsFromModelValue();
  const orderingPairs = [];
  for (const column of _.toArray(props.rowDataProp.columns)) {
    orderingPairs.push({ required: props.rowDataProp.keyColumns.includes(column) ? true : false, column: column, value: "" });
  }

  pairs.value = _.orderBy(orderingPairs, ["required"], ["desc"]);
});

const emitUpdate = () => {
  if (pairs.value.length == 0) {
    emit("update:modelValue", null);
  } else {
    emit("update:modelValue", pairs.value.map((p) => `${p.column}: ${p.value}`).join(", "));
  }
};

const isMounted = ref(useMounted());

watch(() => pairs.value, emitUpdate, { deep: true });

watch(() => props.modelValue, loadPairsFromModelValue, { deep: true });

const scrollToErrors = async () => {
  const errorMessages = document.getElementById("error-message");
  await nextTick();
  errorMessages.scrollIntoView();
};

const submit = async () => {
  let result = _.cloneDeep(pairs.value);

  errors.value = [];
  if (
    !_.every(result, (item) => {
      return !item.required || !_.isEmpty(item.value);
    })
  ) {
    errors.value.push("Every key column must have a value.");
  }

  if (!_.isEmpty(errors.value)) {
    scrollToErrors();
    return;
  }

  result = _.map(result, (obj) => _.omit(obj, "required"));

  const response = await upsertLookupTableRows(props.rowDataProp.name, [result]);
  if (!_.isEmpty(response.data.upsertLookupTableRows.errors)) {
    errors.value = response.data.upsertLookupTableRows.errors;
    scrollToErrors();
    return;
  }
  notify.success("Rows added successfully");
  emit("refreshAndClose");
};

const clearErrors = () => {
  errors.value = [];
};
</script>

<style>
.map-edit {
  .map-row {
    width: 100%;
    display: flex;
  }

  .map-row > * {
    flex: 1 1 auto;
  }

  .remove-button {
    flex: 0 0 auto;
  }

  .inputWidth {
    width: 60% !important;
  }
}
</style>
