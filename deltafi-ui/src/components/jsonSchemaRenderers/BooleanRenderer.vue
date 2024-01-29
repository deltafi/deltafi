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
  <control-wrapper v-bind="schemaData.controlWrapper" :styles="schemaData.styles" :is-focused="schemaData.isFocused" :applied-options="schemaData.appliedOptions" class="pb-2">
    <div>
      <div class="btn-group pb-0">
        <legend :for="schemaData.control.id + '-input'" class="pr-2">{{ schemaData.control.i18nKeyPrefix.split(".").pop() }}:</legend>
        <Checkbox :id="schemaData.control.id + '-input'" v-model="booleanDecider" :class="schemaData.styles.control.input" :binary="true" class="pt-1" @change="schemaData.onChange(schemaData.control.data)" @focus="schemaData.isFocused = true" @blur="schemaData.isFocused = false" />
      </div>
      <div>
        <small :id="schemaData.control.id + '-input-help'">{{ schemaData.control.description }}</small>
      </div>
    </div>
  </control-wrapper>
</template>
  
<script setup lang="ts">
import useSchemaComposition from "@/components/jsonSchemaRenderers/util/useSchemaComposition";
import { default as ControlWrapper } from "./ControlWrapper.vue";
import { ControlElement } from "@jsonforms/core";
import { computed, defineProps, reactive } from "vue";
import { rendererProps, useJsonFormsControl } from "@jsonforms/vue";

import _ from "lodash";

import Checkbox from "primevue/checkbox";

const { useControl } = useSchemaComposition();

const props = defineProps({
  ...rendererProps<ControlElement>(),
});

const schemaData = reactive(useControl(useJsonFormsControl(props), (newValue: any) => newValue || false));

const booleanDecider = computed(() => {
  // Checks to see if the value passed in is an empty string, object, collection, map, or set and also checks if the value is not a boolean.
  if (_.isEmpty(schemaData.control.data) && !_.isBoolean(schemaData.control.data)) {
    return false
  }

  if (_.isString(schemaData.control.data)) {
    return String(schemaData.control.data).toLowerCase() === 'true'
  }

  return schemaData.control.data;
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