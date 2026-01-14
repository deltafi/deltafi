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
    <dl class="additional-string-field">
      <dt v-if="!_.isEmpty(schemaData.computedLabel)" :id="schemaData.control.id + '-input-label'" class="additional-string-label">
        {{ computedLabel }}
        <i v-if="!_.isEmpty(schemaData.control.description)" v-tooltip.right="schemaData.control.description" class="pi pi-info-circle info-icon" />
      </dt>
      <dd class="additional-string-input">
        <template v-if="suggestions !== undefined">
          <Dropdown :id="schemaData.control.id + '-input'" v-model="schemaData.control.data" :class="schemaData.styles.control.input + ' inputWidth'" :disabled="!schemaData.control.enabled" :autofocus="schemaData.appliedOptions.focus" :placeholder="schemaData.appliedOptions.placeholder" :options="suggestions" show-clear @change="schemaData.onChange(schemaData.control.data)" @focus="schemaData.isFocused = true" @blur="schemaData.isFocused = false" />
        </template>
        <template v-else>
          <InputText :id="schemaData.control.id + '-input'" v-model="schemaData.control.data" :class="schemaData.styles.control.input + ' inputWidth'" :disabled="!schemaData.control.enabled" :autofocus="schemaData.appliedOptions.focus" :placeholder="schemaData.appliedOptions.placeholder" @input="schemaData.onChange(schemaData.control.data)" @focus="schemaData.isFocused = true" @blur="schemaData.isFocused = false" />
        </template>
      </dd>
    </dl>
  </control-wrapper>
</template>

<script setup lang="ts">
import useSchemaComposition from "@/components/jsonSchemaRenderers/defaultRenderers/util/useSchemaComposition";
import { ControlElement } from "@jsonforms/core";
import { computed, reactive } from "vue";
import { rendererProps, useJsonFormsControl } from "@jsonforms/vue";
import { default as ControlWrapper } from "./ControlWrapper.vue";

import _ from "lodash";

import InputText from "primevue/inputtext";
import Dropdown from "primevue/dropdown";

const { useControl } = useSchemaComposition();

const props = defineProps({
  ...rendererProps<ControlElement>(),
});

const schemaData = reactive(useControl(useJsonFormsControl(props), (value) => value || undefined, 300));

const suggestions = computed(() => {
  const suggestions = schemaData.control.schema.enum;
  if (suggestions === undefined || !_.isArray(suggestions) || !_.every(suggestions, _.isString)) {
    // check for incorrect data
    return undefined;
  }
  return suggestions;
});

const computedLabel = computed(() => {
  let label = (schemaData.control.config.defaultLabels) ? schemaData.control.label : schemaData.control.i18nKeyPrefix.split(".").pop();

  label = (schemaData.control.required) ? label + "*" : label;

  return label;
});
</script>

<style scoped>
.additional-string-field {
  margin-bottom: 0.75rem;
}

.additional-string-label {
  font-weight: 600;
  font-size: 0.9rem;
  color: var(--text-color);
  margin-bottom: 0.25rem;
  display: flex;
  align-items: center;
  gap: 0.25rem;
}

.additional-string-input {
  margin-left: 0;
}

.info-icon {
  font-size: 0.8rem;
  color: var(--text-color-secondary);
  cursor: help;
}

.inputWidth {
  width: 100% !important;
}
</style>
