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

<template>
  <control-wrapper v-bind="schemaData.controlWrapper" :styles="schemaData.styles" :is-focused="schemaData.isFocused" :applied-options="schemaData.appliedOptions">
    <dl class="boolean-field">
      <dd class="boolean-row">
        <TriStateToggle :model-value="triStateValue(schemaData.control.data)" @update:model-value="schemaData.onChange($event)" />
        <label :for="schemaData.control.id + '-input'" class="boolean-label">{{ computedLabel }}<span v-if="schemaData.control.required" class="required-indicator" aria-hidden="true">*</span></label>
        <i v-if="schemaData.control.description" v-tooltip.right="schemaData.control.description" class="pi pi-info-circle info-icon" />
      </dd>
    </dl>
  </control-wrapper>
</template>

<script setup lang="ts">
import useSchemaComposition from "@/components/jsonSchemaRenderers/defaultRenderers/util/useSchemaComposition";
import { default as ControlWrapper } from "./ControlWrapper.vue";
import TriStateToggle from "@/components/TriStateToggle.vue";
import { ControlElement } from "@jsonforms/core";
import { computed, reactive } from "vue";
import { rendererProps, useJsonFormsControl } from "@jsonforms/vue";

import _ from "lodash";

const { useControl } = useSchemaComposition();

const props = defineProps({
  ...rendererProps<ControlElement>(),
});

const schemaData = reactive(useControl(useJsonFormsControl(props), (newValue: any) => newValue));

const triStateValue = (val: any): boolean | null => {
  if (_.isBoolean(val)) return val;
  if (_.isString(val)) {
    const lower = val.toLowerCase();
    if (lower === "true") return true;
    if (lower === "false") return false;
  }
  return null;
};

const computedLabel = computed(() => {
  return schemaData.control.config.defaultLabels ? schemaData.control.label : schemaData.control.i18nKeyPrefix.split(".").pop();
});
</script>
<style scoped>
.boolean-field {
  margin-bottom: 0.75rem;
}

.boolean-row {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin: 0;
}

.boolean-label {
  font-weight: 500;
  color: var(--text-color);
  cursor: pointer;
  margin: 0;
}

.info-icon {
  font-size: 0.8rem;
  color: var(--text-color-secondary);
  cursor: help;
}

.required-indicator {
  color: var(--red-500);
  margin-left: 0.125rem;
}
</style>
