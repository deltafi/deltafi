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
  <div id="error-message" class="rate-limiting-form">
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
    <span>
      <FillInSentence ref="refFill" v-model="rowData" :fields="fields" :template="rateLimitExplainer" :minBlankWidth="130" @validity="isValidRateLimit" />
    </span>
    <teleport v-if="isMounted" to="#dialogTemplate">
      <div class="p-dialog-footer">
        <Button v-show="canDelete" label="Delete" @click="deleteRateLimit()" class="p-button-secondary" />
        <Button label="Submit" @click="submit()" :disabled="!canSubmit" />
      </div>
    </teleport>
  </div>
</template>

<script setup>
import FillInSentence from "./FillInSentence.vue";
import useRateLimiting from "@/composables/useRateLimiting";
import useNotifications from "@/composables/useNotifications";
import { computed, inject, onMounted, onUnmounted, ref, watch } from "vue";
import { useMounted } from "@vueuse/core";

import Button from "primevue/button";
import Message from "primevue/message";

import _ from "lodash";

const isMounted = ref(useMounted());
const editing = inject("isEditing");
const notify = useNotifications();

const props = defineProps({
  restDataSourceName: {
    type: String,
    required: false,
    default: function () {
      return "";
    },
  },
  rateLimit: {
    type: Object,
    required: false,
    default: null,
  },
});

const { removeRestDataSourceRateLimit, setRestDataSourceRateLimit } = useRateLimiting();
const refFill = ref();
const isValid = ref(false);

const rateLimitTemplate = {
  unit: null,
  maxAmount: null,
  durationSeconds: null,
};

const emit = defineEmits(["refreshAndClose"]);
const errors = ref([]);

const originalRateLimit = _.cloneDeepWith(_.isEmpty(props.rateLimit) ? rateLimitTemplate : props.rateLimit);

const rowData = ref(_.cloneDeepWith(_.isEmpty(props.rateLimit) ? rateLimitTemplate : props.rateLimit));

onMounted(() => {
  console.log("RateLimitingForm mounted");
  editing.value = true;
});

onUnmounted(() => {
  editing.value = false;
});

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

watch(
  () => model.value.unit,
  () => {
    if (!model.value.unit) {
      model.value.maxAmount = null;
      model.value.durationSeconds = null;
    }
  }
);

const rateLimitExplainer = computed(() => {
  return `${props.restDataSourceName} will be rate limited to {maxAmount} {unit} every {durationSeconds} seconds.`;
});

const fields = {
  unit: {
    type: "dropdown",
    order: 1,
    required: true,
    label: "Unit",
    options: [
      { label: "Files", value: "FILES" },
      { label: "Bytes", value: "BYTES" },
    ],
  },
  maxAmount: { type: "number", order: 2, required: true, label: "Max Amount" },
  durationSeconds: { type: "number", order: 3, required: true, label: "Duration in seconds" },
};

const isValidRateLimit = ({ isValid: v }) => {
  isValid.value = v;
};

const canSubmit = computed(() => {
  if (isValid.value && !_.every(model)) {
    return true;
  }
  return false;
});

const submit = async () => {
  const response = await setRestDataSourceRateLimit(props.restDataSourceName, model.value.unit, model.value.maxAmount, model.value.durationSeconds);

  if (response) {
    notify.success("Rate Limit Set Successfully", `Rate Limit for ${props.restDataSourceName} set to ${model.value.unit} ${model.value.maxAmount} every ${model.value.durationSeconds} seconds`);
    editing.value = false;
    emit("refreshAndClose");
  } else {
    errors.value.push("Failed to set a rate limit for ", props.restDataSourceName);
  }
};

const canDelete = computed(() => {
  if (_.every(originalRateLimit, (value) => !_.isNil(value))) {
    return true;
  }
  return false;
});

const deleteRateLimit = async () => {
  const response = await removeRestDataSourceRateLimit(props.restDataSourceName);

  if (response) {
    notify.success("Removed Rate Limit", `Removed Rate Limit from Data Source ${props.restDataSourceName}.`, 3000);
  } else {
    notify.error("Error Removing Rate Limit", `Error removing Rate Limit from Data Source ${props.restDataSourceName}.`, 3000);
  }
  editing.value = false;
  emit("refreshAndClose");
};
</script>

<style>
.rate-limiting-form {
  .inline-row {
    display: flex;
    gap: 0.5rem;
    align-items: center;
    flex-wrap: wrap;
  }

  .inputWidth {
    flex: 0 0 180px;
  }
}
</style>
