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
  <control-wrapper v-bind="schemaData.controlWrapper" :styles="schemaData.styles" :is-focused="schemaData.isFocused" :applied-options="schemaData.appliedOptions">
    <dl>
      <dt v-if="!_.isEmpty(schemaData.computedLabel)" :id="schemaData.control.id + '-input-label'">{{ computedLabel }}</dt>
      <dd>
        <div class="env-var-container">
          <Message severity="warn" :closable="false" class="env-var-warning" icon="fas fa-exclamation-triangle">
            Enter the <strong>NAME</strong> of an environment variable, <strong>NOT</strong> the actual secret value!
          </Message>
          <div class="env-var-input-wrapper">
            <span class="env-var-prefix">$</span>
            <InputText
              :id="schemaData.control.id + '-input'"
              v-model="envVarName"
              :class="['env-var-input', { 'p-invalid': !isValid && envVarName }]"
              :disabled="!schemaData.control.enabled"
              :autofocus="schemaData.appliedOptions.focus"
              placeholder="MY_SECRET_KEY"
              @input="handleInput"
              @focus="schemaData.isFocused = true"
              @blur="schemaData.isFocused = false"
            />
          </div>
          <small v-if="!isValid && envVarName" class="p-error">
            Environment variable names must be UPPER_SNAKE_CASE (e.g., MY_SECRET_KEY)
          </small>
          <div v-if="fieldDescription">
            <small :id="schemaData.control.id + '-input-help'" class="text-color-secondary">
              {{ fieldDescription }}
            </small>
          </div>
        </div>
      </dd>
    </dl>
  </control-wrapper>
</template>

<script setup lang="ts">
import useSchemaComposition from "@/components/jsonSchemaRenderers/defaultRenderers/util/useSchemaComposition";
import { ControlElement } from "@jsonforms/core";
import { computed, reactive, ref, watch } from "vue";
import { rendererProps, useJsonFormsControl } from "@jsonforms/vue";
import { default as ControlWrapper } from "./ControlWrapper.vue";

import _ from "lodash";

import InputText from "primevue/inputtext";
import Message from "primevue/message";

const ENV_VAR_PATTERN = /^[A-Z][A-Z0-9_]*$/;

const { useControl } = useSchemaComposition();

const props = defineProps({
  ...rendererProps<ControlElement>(),
});

const schemaData = reactive(useControl(useJsonFormsControl(props), (value) => value || undefined, 300));

// Extract description from schema - may be in allOf[1] for EnvVar fields
const fieldDescription = computed(() => {
  const schema = schemaData.control.schema as { description?: string; allOf?: Array<{ description?: string }> };
  // First check direct description
  if (schema?.description) return schema.description;
  // Then check allOf - field description is typically in the second element
  if (schema?.allOf?.[1]?.description) return schema.allOf[1].description;
  if (schema?.allOf?.[0]?.description) return schema.allOf[0].description;
  return undefined;
});

// EnvVar serializes as a simple string (the env var name), not as an object
const envVarName = ref<string>(schemaData.control.data || '');

// Watch for external changes to the data
watch(() => schemaData.control.data, (newValue) => {
  envVarName.value = newValue || '';
});

const isValid = computed(() => {
  if (!envVarName.value) return true;
  return ENV_VAR_PATTERN.test(envVarName.value);
});

const handleInput = () => {
  const value = envVarName.value;
  if (!value || value.trim() === '') {
    schemaData.onChange(null);
  } else {
    schemaData.onChange(value);
  }
};

const computedLabel = computed(() => {
  let label = (schemaData.control.config.defaultLabels) ? schemaData.control.label : schemaData.control.i18nKeyPrefix.split(".").pop();
  label = (schemaData.control.required) ? label + "*" : label;
  return label;
});
</script>

<style scoped>
.env-var-container {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.env-var-warning {
  margin: 0;
  font-size: 0.875rem;
  width: 90%;
}

.env-var-warning :deep(.p-message-wrapper) {
  padding: 0.5rem 0.75rem;
}

.env-var-input-wrapper {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  width: 90%;
}

.env-var-prefix {
  font-family: monospace;
  font-size: 1.25rem;
  font-weight: bold;
  color: var(--primary-color);
  flex-shrink: 0;
}

.env-var-input {
  font-family: monospace;
  flex: 1;
}

.env-var-input::placeholder {
  font-family: monospace;
  opacity: 0.5;
}
</style>
