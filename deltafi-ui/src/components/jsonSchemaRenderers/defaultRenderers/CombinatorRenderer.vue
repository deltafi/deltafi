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

<!--
  Renders a discriminated union (anyOf/oneOf where each branch pins a discriminator property to a const).
  A dropdown selects which branch is active; the selected branch's remaining fields are rendered via the
  normal dispatch mechanism. The discriminator property itself is managed by the dropdown and not rendered
  as a field (its schema is a bare const, which has no applicable renderer on its own).
-->
<template>
  <div v-if="control.visible" class="combinator-wrapper">
    <dt class="combinator-label">
      {{ computedLabel }}<span v-if="control.required" class="required-indicator" aria-hidden="true">*</span>
      <i v-if="!_.isEmpty(control.description)" v-tooltip.right="control.description" class="pi pi-info-circle info-icon" />
    </dt>
    <div class="combinator-container">
      <Select
        :model-value="selectedIndex"
        :options="typeOptions"
        option-label="label"
        option-value="value"
        :disabled="!control.enabled"
        placeholder="Select a type"
        class="combinator-select"
        @update:model-value="onTypeChange"
      />
      <dispatch-renderer
        v-if="selectedSchema"
        :visible="control.visible"
        :enabled="control.enabled"
        :schema="selectedSchema"
        :uischema="detailUiSchema"
        :path="control.path"
        :renderers="control.renderers"
        :cells="control.cells"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ControlElement, Generate, JsonSchema, createDefaultValue } from "@jsonforms/core";
import { computed, reactive } from "vue";
import { DispatchRenderer, rendererProps, useJsonFormsControlWithDetail } from "@jsonforms/vue";
import _ from "lodash";

import Select from "primevue/select";

const props = defineProps({
  ...rendererProps<ControlElement>(),
});

const detail = reactive(useJsonFormsControlWithDetail(props));
const control = computed(() => detail.control);

// the branch schemas of the combinator (anyOf preferred, oneOf supported)
const branches = computed<JsonSchema[]>(() => {
  const schema = control.value.schema as JsonSchema & { anyOf?: JsonSchema[]; oneOf?: JsonSchema[] };
  return (schema.anyOf ?? schema.oneOf ?? []) as JsonSchema[];
});

// the property each branch pins to a const value identifies the active branch
const discriminatorProp = computed<string | undefined>(() => {
  for (const branch of branches.value) {
    const props = (branch as JsonSchema).properties ?? {};
    for (const [name, propSchema] of Object.entries(props)) {
      if ((propSchema as { const?: unknown })?.const !== undefined) {
        return name;
      }
    }
  }
  return undefined;
});

const discriminatorValue = (branch: JsonSchema): string | undefined => {
  const prop = discriminatorProp.value;
  if (!prop) return undefined;
  const propSchema = (branch.properties ?? {})[prop] as { const?: unknown } | undefined;
  return propSchema?.const as string | undefined;
};

const typeOptions = computed(() =>
  branches.value.map((branch, index) => ({
    label: branch.title ?? discriminatorValue(branch) ?? `Option ${index + 1}`,
    value: index,
  }))
);

// which branch matches the current data, by its discriminator value
const selectedIndex = computed<number | null>(() => {
  const prop = discriminatorProp.value;
  const data = control.value.data as Record<string, unknown> | undefined;
  if (!prop || !data) return null;
  const current = data[prop];
  const index = branches.value.findIndex((branch) => discriminatorValue(branch) === current);
  return index >= 0 ? index : null;
});

const selectedSchema = computed<JsonSchema | null>(() => {
  if (selectedIndex.value === null) return null;
  // render the branch without the discriminator property; it is owned by the dropdown and its
  // bare-const schema has no applicable field renderer
  const branch = _.cloneDeep(branches.value[selectedIndex.value]) as JsonSchema & { required?: string[] };
  const prop = discriminatorProp.value;
  if (prop && branch.properties) {
    delete branch.properties[prop];
    if (Array.isArray(branch.required)) {
      branch.required = branch.required.filter((r) => r !== prop);
    }
  }
  return branch;
});

const detailUiSchema = computed(() => {
  if (!selectedSchema.value) return Generate.uiSchema({});
  return Generate.uiSchema(selectedSchema.value, "VerticalLayout");
});

const onTypeChange = (index: number) => {
  const branch = branches.value[index];
  const prop = discriminatorProp.value;
  // seed defaults for the chosen branch, then stamp the discriminator value
  const defaults = (createDefaultValue(branch, control.value.rootSchema) ?? {}) as Record<string, unknown>;
  if (prop) {
    defaults[prop] = discriminatorValue(branch);
  }
  detail.handleChange(control.value.path, defaults);
};

const computedLabel = computed(() => {
  return control.value.config?.defaultLabels ? control.value.label : control.value.i18nKeyPrefix?.split(".").pop();
});
</script>

<style scoped>
.combinator-wrapper {
  margin-bottom: 0.75rem;
}

.combinator-label {
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

.combinator-container {
  display: block;
  padding: 0.5rem 0.75rem;
  border-radius: 6px;
  border: 1px solid var(--surface-border);
  background-color: var(--surface-ground);
}

.combinator-select {
  width: 100%;
  margin-bottom: 0.5rem;
}
</style>
