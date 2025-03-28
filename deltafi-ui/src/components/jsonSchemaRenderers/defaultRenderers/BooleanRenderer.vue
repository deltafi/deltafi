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
  <control-wrapper v-bind="schemaData.controlWrapper" :styles="schemaData.styles" :is-focused="schemaData.isFocused" :applied-options="schemaData.appliedOptions" class="pb-2">
    <dl>
      <div class="btn-group align-items-center">
        <dt class="pr-2">{{ computedLabel }}</dt>
        <dd>
          <Checkbox :id="schemaData.control.id + '-input'" :model-value="booleanDecider(schemaData.control.data)" :class="schemaData.styles.control.input" :binary="true" class="pt-1" @update:model-value="schemaData.onChange($event)" @focus="schemaData.isFocused = true" @blur="schemaData.isFocused = false" />
        </dd>
      </div>
      <div class="mt-n1">
        <small :id="schemaData.control.id + '-input-help'">{{ schemaData.control.description }}</small>
      </div>
    </dl>
  </control-wrapper>
</template>

<script setup lang="ts">
import useSchemaComposition from "@/components/jsonSchemaRenderers/defaultRenderers/util/useSchemaComposition";
import { default as ControlWrapper } from "./ControlWrapper.vue";
import { ControlElement } from "@jsonforms/core";
import { computed, reactive } from "vue";
import { rendererProps, useJsonFormsControl } from "@jsonforms/vue";

import _ from "lodash";

import Checkbox from "primevue/checkbox";

const { useControl } = useSchemaComposition();

const props = defineProps({
  ...rendererProps<ControlElement>(),
});

const schemaData = reactive(useControl(useJsonFormsControl(props), (newValue: any) => newValue || false));

const booleanDecider = (boolVal: any) => {
  // Checks to see if the value passed in is an empty string, object, collection, map, or set and also checks if the value is not a boolean.
  if (_.isEmpty(boolVal) && !_.isBoolean(boolVal)) {
    return false;
  }

  if (_.isString(boolVal)) {
    return String(boolVal).toLowerCase() === "true";
  }

  return boolVal;
};

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
