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
        <!-- When the value is a template expression, show it with an edit affordance instead of the toggle -->
        <!-- When the value is an expression, show it as a labeled chip instead of the indeterminate toggle -->
        <div v-if="isTemplate" class="expression-chip">
          <i v-tooltip.top="'Resolved to true/false before the action runs'" class="fas fa-code expression-icon" />
          <code class="expression-text" :title="schemaData.control.data">{{ schemaData.control.data }}</code>
          <SpelEditButton variant="inline" :model-value="schemaData.control.data" :label="fieldLabel" :disabled="!schemaData.control.enabled" @update:model-value="schemaData.onChange($event || null)" />
          <Button v-tooltip.top="'Clear expression'" icon="pi pi-times" text rounded severity="secondary" :disabled="!schemaData.control.enabled" @click="schemaData.onChange(null)" />
        </div>
        <template v-else>
          <TriStateToggle :model-value="triStateValue(schemaData.control.data)" @update:model-value="schemaData.onChange($event)" />
          <SpelEditButton variant="inline" tooltip="Use an expression" :model-value="schemaData.control.data" :label="fieldLabel" :disabled="!schemaData.control.enabled" @update:model-value="schemaData.onChange($event || null)" />
        </template>
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
import { SpelEditButton } from "@/components/spel";
import { ControlElement } from "@jsonforms/core";
import { computed, reactive } from "vue";
import { rendererProps, useJsonFormsControl } from "@jsonforms/vue";

import Button from "primevue/button";
import _ from "lodash";

const { useControl } = useSchemaComposition();

const props = defineProps({
  ...rendererProps<ControlElement>(),
});

const schemaData = reactive(useControl(useJsonFormsControl(props), (newValue: any) => newValue));

// A template expression resolves to true/false before the action runs, so a boolean field may hold one.
const isTemplate = computed(() => {
  const value = schemaData.control.data;
  return _.isString(value) && value.includes("{{");
});

const triStateValue = (val: any): boolean | null => {
  if (_.isBoolean(val)) return val;
  if (_.isString(val)) {
    const lower = val.toLowerCase();
    if (lower === "true") return true;
    if (lower === "false") return false;
  }
  return null;
};

const fieldLabel = computed(() => {
  return schemaData.control.i18nKeyPrefix?.split(".").pop() || "Value";
});

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

.expression-chip {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  max-width: 36rem;
  padding: 0.1rem 0.25rem 0.1rem 0.5rem;
  border: 1px solid var(--surface-border);
  border-radius: 6px;
  background-color: var(--surface-ground);
}

.expression-icon {
  font-size: 0.8rem;
  color: var(--primary-color);
  flex-shrink: 0;
}

.expression-text {
  flex: 1;
  font-family: var(--font-family-monospace, monospace);
  font-size: 0.85rem;
  color: var(--text-color);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
