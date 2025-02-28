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
      <dd class="align-items-center">
        <InputNumber :id="schemaData.control.id + '-input'" :model-value="integerDecider(schemaData.control.data)" :class="schemaData.styles.control.input + ' inputWidth'" :step="steps" input-id="stacked-buttons" show-buttons @input="schemaData.onChange($event.value)" @focus="schemaData.isFocused = true" @blur="schemaData.isFocused = false" />
        <div>
          <small v-if="!_.isEmpty(schemaData.control.description)" :id="schemaData.control.id + '-input-help'">{{ schemaData.control.description }}</small>
        </div>
      </dd>
    </dl>
  </control-wrapper>
</template>

<script setup lang="ts">
import { ControlElement } from "@jsonforms/core";
import { computed, reactive } from "vue";
import { rendererProps, useJsonFormsControl } from "@jsonforms/vue";
import InputNumber from "primevue/inputnumber";
import { default as ControlWrapper } from "./ControlWrapper.vue";
import useSchemaComposition from "@/components/jsonSchemaRenderers/defaultRenderers/util/useSchemaComposition";

import _ from "lodash";

const { useControl } = useSchemaComposition();

const props = defineProps({
  ...rendererProps<ControlElement>(),
});

const schemaData = reactive(useControl(useJsonFormsControl(props), (value) => value));

const integerDecider = (intVal: any) => {
  // If its empty and not an integer, return null
  if (_.isEmpty(intVal) && !_.isInteger(intVal)) {
    return null;
  }

  // Trys to convert the value to a number and checks to see if it is an integer.
  if (_.isInteger(Number(intVal))) {
    return Number(intVal);
  }

  // If its not an integer, return null
  if (Number.isNaN(intVal)) {
    return null;
  }

  return intVal;
};

const steps = computed(() => {
  const options: any = schemaData.appliedOptions;
  return options.step ?? 1;
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
  width: 90% !important;
}
</style>
