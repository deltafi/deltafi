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

<!-- ABOUTME: Rate limiting form for data sinks. -->
<!-- ABOUTME: Allows setting or removing rate limits (files or bytes per duration) on data sinks. -->

<template>
  <div class="rate-limiting-form">
    <div v-if="errors.length > 0" class="pt-2">
      <Message severity="error" :sticky="true" class="mb-2 mt-0" @close="errors = []">
        <ul>
          <li v-for="(error, key) in errors" :key="key" class="text-wrap text-break">
            {{ error }}
          </li>
        </ul>
      </Message>
    </div>

    <div class="form-grid">
      <label for="maxAmount">Max Amount</label>
      <InputNumber id="maxAmount" v-model="formData.maxAmount" :min="1" :useGrouping="false" placeholder="e.g. 100" class="w-full" />

      <label for="unit">Unit</label>
      <Select id="unit" v-model="formData.unit" :options="unitOptions" optionLabel="label" optionValue="value" class="w-full" />

      <label for="duration">Duration (seconds)</label>
      <InputNumber id="duration" v-model="formData.durationSeconds" :min="1" :useGrouping="false" placeholder="e.g. 60" class="w-full" />
    </div>

    <div class="summary">
      <i class="pi pi-info-circle mr-2" />
      {{ summaryText }}
    </div>

    <teleport v-if="isMounted" to="#dialogTemplate">
      <div class="p-dialog-footer">
        <Button v-if="canDelete" label="Delete" severity="danger" @click="deleteRateLimit()" />
        <Button label="Submit" :disabled="!isComplete" @click="submit()" />
      </div>
    </teleport>
  </div>
</template>

<script setup>
import useRateLimiting from "@/composables/useRateLimiting";
import useNotifications from "@/composables/useNotifications";
import { computed, inject, onMounted, onUnmounted, reactive } from "vue";
import { useMounted } from "@vueuse/core";

import Button from "primevue/button";
import Select from "primevue/select";
import InputNumber from "primevue/inputnumber";
import Message from "primevue/message";

const isMounted = useMounted();
const editing = inject("isEditing");
const notify = useNotifications();
const { removeDataSinkRateLimit, setDataSinkRateLimit } = useRateLimiting();

const props = defineProps({
  dataSinkName: {
    type: String,
    required: false,
    default: "",
  },
  rateLimit: {
    type: Object,
    required: false,
    default: null,
  },
});

const emit = defineEmits(["refreshAndClose"]);
const errors = reactive([]);

const unitOptions = [
  { label: "Files", value: "FILES" },
  { label: "Bytes", value: "BYTES" },
];

const formData = reactive({
  maxAmount: props.rateLimit?.maxAmount ?? null,
  unit: props.rateLimit?.unit ?? "FILES",
  durationSeconds: props.rateLimit?.durationSeconds ?? null,
});

const isComplete = computed(() => {
  return formData.maxAmount != null && formData.unit != null && formData.durationSeconds != null;
});

const summaryText = computed(() => {
  const amount = formData.maxAmount ?? "___";
  const unitLabel = formData.unit === "FILES" ? "files" : "bytes";
  const duration = formData.durationSeconds ?? "___";
  return `${props.dataSinkName} will egress up to ${amount} ${unitLabel} every ${duration} seconds.`;
});

const canDelete = computed(() => {
  return props.rateLimit?.maxAmount != null && props.rateLimit?.durationSeconds != null;
});

onMounted(() => {
  editing.value = true;
});

onUnmounted(() => {
  editing.value = false;
});

const submit = async () => {
  const response = await setDataSinkRateLimit(
    props.dataSinkName,
    formData.unit,
    formData.maxAmount,
    formData.durationSeconds
  );

  if (response) {
    notify.success("Rate Limit Set", `Rate limit for ${props.dataSinkName} updated.`);
    editing.value = false;
    emit("refreshAndClose", {
      name: props.dataSinkName,
      rateLimit: {
        unit: formData.unit,
        maxAmount: formData.maxAmount,
        durationSeconds: formData.durationSeconds,
      },
    });
  } else {
    errors.push(`Failed to set rate limit for ${props.dataSinkName}`);
  }
};

const deleteRateLimit = async () => {
  const response = await removeDataSinkRateLimit(props.dataSinkName);

  if (response) {
    notify.success("Rate Limit Removed", `Removed rate limit from ${props.dataSinkName}.`);
    editing.value = false;
    emit("refreshAndClose", {
      name: props.dataSinkName,
      rateLimit: null,
    });
  } else {
    notify.error("Error", `Failed to remove rate limit from ${props.dataSinkName}.`);
  }
};
</script>

<style scoped>
.rate-limiting-form {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.form-grid {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 0.75rem 1rem;
  align-items: center;
}

.form-grid label {
  font-weight: 500;
  white-space: nowrap;
}

.summary {
  background: var(--surface-100);
  border-radius: 6px;
  padding: 0.75rem 1rem;
  color: var(--text-color-secondary);
  font-size: 0.9rem;
}
</style>
