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
    <div class="py-2 align-items-center">
      <dt v-if="!_.isEmpty(schemaData.computedLabel)" :id="schemaData.control.id + '-input-label'">{{ computedLabel }}</dt>
      <dd>
        <template v-if="suggestions !== undefined">
          <Dropdown :id="schemaData.control.id + '-input'" v-model="schemaData.control.data" :class="schemaData.styles.control.input + ' inputWidth'" :disabled="!schemaData.control.enabled" :autofocus="schemaData.appliedOptions.focus" :placeholder="schemaData.appliedOptions.placeholder" :options="suggestions" show-clear @change="schemaData.onChange(schemaData.control.data)" @focus="schemaData.isFocused = true" @blur="schemaData.isFocused = false" />
        </template>
        <template v-else>
          <InputText :id="schemaData.control.id + '-input'" v-model="schemaData.control.data" :class="schemaData.styles.control.input + ' inputWidth'" :disabled="!schemaData.control.enabled" :autofocus="schemaData.appliedOptions.focus" :placeholder="schemaData.appliedOptions.placeholder" @input="schemaData.onChange(schemaData.control.data)" @focus="schemaData.isFocused = true" @blur="schemaData.isFocused = false" />
        </template>
      </dd>
      <div>
        <small :id="schemaData.control.id + '-input-help'">{{ schemaData.control.description }}</small>
      </div>
    </div>
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

<style>
.field * {
  display: block;
}

.inputWidth {
  width: 100% !important;
}
</style>
