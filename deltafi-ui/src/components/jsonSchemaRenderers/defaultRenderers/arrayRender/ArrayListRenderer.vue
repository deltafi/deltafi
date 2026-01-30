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
  <div v-if="schemaData.control.visible" class="array-list-wrapper">
    <dt class="array-list-label">
      {{ computedLabel }}<span v-if="schemaData.control.required" class="required-indicator">*</span>
      <i v-if="!_.isEmpty(schemaData.control.description)" v-tooltip.right="schemaData.control.description" class="pi pi-info-circle info-icon" />
    </dt>
    <div class="array-list-container">

      <div v-if="noData" class="array-list-empty">
        <span>No items</span>
        <Button icon="pi pi-plus" class="p-button-text p-button-sm p-button-rounded" @click="addButtonClick" />
      </div>

      <div v-else class="array-list-items">
        <template v-if="isSimpleType">
          <div v-for="(element, index) in renderArrayData" :key="`${schemaData.control.path}-${index}`" class="array-list-row">
            <div class="array-list-input">
              <InputText :model-value="element" class="value-input" :disabled="!schemaData.control.enabled" @update:model-value="updateItem(Number(index), $event)" />
            </div>
            <div class="array-list-actions">
              <Button icon="pi pi-angle-up" class="p-button-text p-button-sm p-button-rounded" :disabled="index === 0" @click="schemaData.moveUp?.(schemaData.control.path, Number(index))()" />
              <Button icon="pi pi-angle-down" class="p-button-text p-button-sm p-button-rounded" :disabled="Number(index) >= schemaData.control.data.length - 1" @click="schemaData.moveDown?.(schemaData.control.path, Number(index))()" />
              <Button icon="pi pi-times" class="p-button-text p-button-sm p-button-rounded p-button-danger" @click="schemaData.removeItems?.(schemaData.control.path, [Number(index)])()" />
            </div>
          </div>
        </template>
        <template v-else>
          <div v-for="(element, index) in renderArrayData" :key="`${schemaData.control.path}-${index}`" :class="['array-list-item', { 'array-list-item-object': isObjectType }]">
            <ArrayListElement :move-up="schemaData.moveUp?.(schemaData.control.path, Number(index))" :move-up-enabled="Number(index) > 0" :move-down="schemaData.moveDown?.(schemaData.control.path, Number(index))" :move-down-enabled="Number(index) < schemaData.control.data.length - 1" :delete="schemaData.removeItems?.(schemaData.control.path, [Number(index)])" :label="schemaData.childLabelForIndex(Number(index))" :styles="schemaData.styles" :is-object-type="isObjectType">
              <dispatch-renderer :schema="schemaData.control.schema" :uischema="schemaData.childUiSchema" :path="composePaths(schemaData.control.path, `${index}`)" :enabled="schemaData.control.enabled" :renderers="schemaData.control.renderers" :cells="schemaData.control.cells" />
            </ArrayListElement>
          </div>
        </template>
        <div class="array-list-add">
          <Button icon="pi pi-plus" label="Add" class="p-button-text p-button-sm" @click="addButtonClick" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import ArrayListElement from "@/components/jsonSchemaRenderers/defaultRenderers/arrayRender/ArrayListElement.vue";
import { computed, reactive } from "vue";
import { composePaths, createDefaultValue, ControlElement } from "@jsonforms/core";
import { DispatchRenderer, rendererProps, useJsonFormsArrayControl, useJsonFormsControl } from "@jsonforms/vue";
import { useVanillaArrayControl } from "@jsonforms/vue-vanilla";
import _ from "lodash";

import Button from "primevue/button";
import InputText from "primevue/inputtext";

const props = defineProps({
  ...rendererProps<ControlElement>(),
});

const jsonFormsControl = useJsonFormsArrayControl(props);
const { handleChange } = useJsonFormsControl(props);
const schemaData = reactive(useVanillaArrayControl(jsonFormsControl));

const noData = computed(() => {
  return !schemaData.control.data || schemaData.control.data.length === 0;
});

const renderArrayData = computed(() => {
  return schemaData.control.data;
});

const isObjectType = computed(() => {
  return schemaData.control.schema.type === "object";
});

const isSimpleType = computed(() => {
  const type = schemaData.control.schema.type;
  return type === "string" || type === "number" || type === "integer";
});

const updateItem = (index: number, value: any) => {
  const newData = [...schemaData.control.data];
  newData[index] = value;
  handleChange(schemaData.control.path, newData);
};

const addButtonClick = () => {
  schemaData.addItem(schemaData.control.path, createDefaultValue(schemaData.control.schema, schemaData.control.rootSchema))();
};

const computedLabel = computed(() => {
  return schemaData.control.config.defaultLabels ? schemaData.control.label : schemaData.control.i18nKeyPrefix.split(".").pop();
});
</script>
<style scoped>
.array-list-wrapper {
  margin-bottom: 0.75rem;
}

.array-list-label {
  font-weight: 600;
  font-size: 0.9rem;
  color: var(--text-color);
  margin-bottom: 0.5rem;
  display: flex;
  align-items: center;
  gap: 0.25rem;
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

.array-list-container {
  padding: 0.5rem 0.75rem;
  border: 1px solid var(--surface-border);
  border-radius: 6px;
  background-color: var(--surface-50);
}

.array-list-empty {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  color: var(--text-color-secondary);
  font-style: italic;
}

.array-list-items {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.array-list-item {
  display: flex;
  align-items: flex-start;
}

.array-list-item-object {
  padding: 0.5rem;
  border: 1px solid var(--surface-border);
  border-radius: 4px;
  background-color: var(--surface-0);
}

.array-list-row {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.array-list-input {
  flex: 1;
}

.value-input {
  width: 100%;
}

.array-list-actions {
  display: flex;
  align-items: center;
  gap: 0;
  flex-shrink: 0;
}

.array-list-add {
  margin-top: 0.25rem;
}
</style>
