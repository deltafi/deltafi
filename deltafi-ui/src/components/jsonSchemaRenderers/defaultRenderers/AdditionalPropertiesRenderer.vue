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
  <div v-if="jsonSchema.control.visible" class="additional-properties-inline">
    <div v-if="noData" class="additional-properties-empty">
      <span>No entries</span>
      <Button v-tooltip.bottom="'Add entry'" icon="pi pi-plus" class="p-button-text p-button-sm p-button-rounded" :disabled="!jsonSchema.control.enabled" @click="addNewEntry" />
    </div>

    <div v-else class="additional-properties-list">
      <div v-for="(element, index) in additionalPropertyItems" :key="`entry-${index}`" class="additional-properties-row">
        <div class="additional-properties-key">
          <InputText :model-value="element.propertyName.startsWith('__new_key_') ? '' : element.propertyName" :class="['key-input', { 'p-invalid': getKeyError(element.propertyName, index) }]" :disabled="!jsonSchema.control.enabled" placeholder="key" @update:model-value="updatePropertyKey(index, $event ?? '')" />
          <small v-if="getKeyError(element.propertyName, index)" class="p-error">{{ getKeyError(element.propertyName, index) }}</small>
        </div>
        <span class="additional-properties-equals">=</span>
        <div class="additional-properties-value">
          <InputText v-if="isSimpleStringType" :model-value="element.value ?? ''" class="value-input" :disabled="!jsonSchema.control.enabled" placeholder="value" @update:model-value="updatePropertyValue(index, $event)" />
          <dispatch-renderer v-else-if="element.schema && element.uischema" :visible="jsonSchema.control.visible" :enabled="jsonSchema.control.enabled" :schema="element.schema" :uischema="element.uischema" :path="element.path" :renderers="jsonSchema.control.renderers" :cells="jsonSchema.control.cells" />
        </div>
        <Button v-if="jsonSchema.control.enabled" v-tooltip.bottom="'Remove'" icon="pi pi-times" class="p-button-text p-button-sm p-button-rounded p-button-danger remove-btn" @click="removeProperty(index)" />
      </div>
      <div class="additional-properties-add">
        <Button v-tooltip.bottom="'Add entry'" icon="pi pi-plus" label="Add" class="p-button-text p-button-sm" :disabled="!jsonSchema.control.enabled" @click="addNewEntry" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import useSchemaComposition from "@/components/jsonSchemaRenderers/defaultRenderers/util/useSchemaComposition";
import { computed, PropType, ref, reactive, watch } from "vue";
import { createControlElement, encode, Generate, getI18nKey, GroupLayout, JsonSchema, JsonSchema7, UISchemaElement, validate } from "@jsonforms/core";
import { DispatchRenderer, useJsonFormsControlWithDetail } from "@jsonforms/vue";
import Ajv, { ValidateFunction } from "ajv";

import _ from "lodash";

import Button from "primevue/button";
import InputText from "primevue/inputtext";

const tempKeyCounter = ref(0);

const generateTempKey = () => {
  tempKeyCounter.value++;
  return `__new_key_${tempKeyCounter.value}__`;
};

const getKeyError = (propertyName: string, index: number): string | null => {
  if (!propertyName || propertyName.startsWith("__new_key_")) {
    return "Key is required";
  }

  const duplicateIndex = additionalPropertyItems.value.findIndex(
    (item, i) => i !== index && item.propertyName === propertyName
  );
  if (duplicateIndex !== -1) {
    return "Duplicate key";
  }

  return null;
};

const syncToData = () => {
  const newData: Record<string, any> = {};
  let hasDuplicates = false;
  let hasEmptyKeys = false;

  additionalPropertyItems.value.forEach((item, idx) => {
    const error = getKeyError(item.propertyName, idx);
    if (error === "Duplicate key") {
      hasDuplicates = true;
    } else if (error === "Key is required") {
      hasEmptyKeys = true;
    }
    // Sync all items with non-empty keys (including duplicates - last one wins)
    if (item.propertyName && !item.propertyName.startsWith("__new_key_")) {
      newData[item.propertyName] = item.value ?? "";
    }
  });

  // Add markers for validation errors so submit can detect them
  if (hasDuplicates) {
    newData["__has_duplicate_keys__"] = true;
  }
  if (hasEmptyKeys) {
    newData["__has_empty_keys__"] = true;
  }

  jsonSchema.handleChange(jsonSchema.control.path, Object.keys(newData).length > 0 ? newData : undefined);
};

const addNewEntry = () => {
  const tempKey = generateTempKey();
  const additionalProperty = toAdditionalPropertyType(tempKey, "");
  additionalPropertyItems.value = [...additionalPropertyItems.value, additionalProperty];
  // Don't sync yet - temp keys won't be included in data until user enters a valid key
};

const updatePropertyKey = (index: number, newKey: string) => {
  const item = additionalPropertyItems.value[index];
  if (!item) return;

  const oldKey = item.propertyName;
  if (oldKey === newKey) return;

  // Update the item's key (allow duplicates - error will show)
  item.propertyName = newKey || oldKey;

  syncToData();
};

const { useAjv, useControlAppliedOptions, useTranslator } = useSchemaComposition();
type Input = ReturnType<typeof useJsonFormsControlWithDetail>;
interface AdditionalPropertyType {
  propertyName: string;
  value: any;
  path: string;
  schema: JsonSchema | undefined;
  uischema: UISchemaElement | undefined;
}
const reuseAjvForSchema = (ajv: Ajv, schema: JsonSchema): Ajv => {
  if (Object.prototype.hasOwnProperty.call(schema, "id") || Object.prototype.hasOwnProperty.call(schema, "$id")) {
    ajv.removeSchema(schema);
  }
  return ajv;
};

const props = defineProps({
  input: {
    type: Object as PropType<Input>,
    required: true,
  },
});
const reservedPropertyNames = computed(() => {
  return Object.keys(jsonSchema.control.schema.properties || {});
});

const jsonSchema = reactive(props.input);
let additionalKeys: any[];
if (jsonSchema.control.data) {
  additionalKeys = Object.keys(jsonSchema.control.data).filter((k) => !reservedPropertyNames.value.includes(k));
} else {
  additionalKeys = [];
}

const toAdditionalPropertyType = (propName: string, propValue: any): AdditionalPropertyType => {
  let propSchema: JsonSchema | undefined = undefined;
  let propUiSchema: UISchemaElement | undefined = undefined;
  if (jsonSchema.control.schema.patternProperties) {
    const matchedPattern = Object.keys(jsonSchema.control.schema.patternProperties).find((pattern) => new RegExp(pattern).test(propName));
    if (matchedPattern) {
      propSchema = jsonSchema.control.schema.patternProperties[matchedPattern];
    }
  }

  if (!propSchema && typeof jsonSchema.control.schema.additionalProperties === "object") {
    propSchema = jsonSchema.control.schema.additionalProperties;
  }

  if (!propSchema && propValue !== undefined) {
    // can't find the propertySchema so use the schema based on the value
    // this covers case where the data in invalid according to the schema
    propSchema = Generate.jsonSchema(
      { prop: propValue },
      {
        additionalProperties: false,
        required: () => false,
      }
    ).properties?.prop;
  }

  if (propSchema) {
    if (propSchema.type === "object" || propSchema.type === "array") {
      propUiSchema = Generate.uiSchema(propSchema, "Group");
      //(propUiSchema as GroupLayout).label = propSchema.title ?? _.startCase(propName);
      (propUiSchema as GroupLayout).label = propSchema.title ?? propName;
    } else {
      propUiSchema = createControlElement(jsonSchema.control.path + "/" + encode(propName));
    }
  }
  return {
    propertyName: propName,
    value: propValue,
    path: jsonSchema.control.path,
    schema: { type: "additionalPropertyString" },
    uischema: propUiSchema,
  };
};

const appliedOptions = useControlAppliedOptions(props.input);
const additionalPropertyItems = ref<AdditionalPropertyType[]>([]);
additionalKeys.forEach((propName) => {
  const additionalProperty = toAdditionalPropertyType(propName, jsonSchema.control.data[propName]);
  additionalPropertyItems.value.push(additionalProperty);
});
const newPropertyName = ref<string | null>("");
const ajv = useAjv();
let propertyNameSchema: JsonSchema7 | undefined = undefined;
let propertyNameValidator: ValidateFunction<unknown> | undefined = undefined;
// TODO: create issue against jsonforms to add propertyNames into the JsonSchema interface
// propertyNames exist in draft-6 but not defined in the JsonSchema
if (typeof (jsonSchema.control.schema as any).propertyNames === "object") {
  propertyNameSchema = (jsonSchema.control.schema as any).propertyNames;
}
if (typeof jsonSchema.control.schema.additionalProperties !== "object" && typeof jsonSchema.control.schema.patternProperties === "object") {
  const matchPatternPropertiesKeys: JsonSchema7 = {
    type: "string",
    pattern: Object.keys(jsonSchema.control.schema.patternProperties).join("|"),
  };
  propertyNameSchema = propertyNameSchema ? { allOf: [propertyNameSchema, matchPatternPropertiesKeys] } : matchPatternPropertiesKeys;
}

if (propertyNameSchema) {
  propertyNameValidator = reuseAjvForSchema(ajv, propertyNameSchema).compile(propertyNameSchema);
}

const t = useTranslator();

const addPropertyDisabled = computed(() => {
  return (
    // add is disabled because the overall control is disabled
    !jsonSchema.control.enabled ||
    // add is disabled because of contraints
    (appliedOptions.value.restrict && maxPropertiesReached) ||
    // add is disabled because there are errors for the new property name or it is not specified
    newPropertyErrors.value.length > 0 ||
    !newPropertyName.value
  );
});

const maxPropertiesReached = computed(() => {
  return (
    jsonSchema.control.schema.maxProperties !== undefined && // we have maxProperties constraint
    jsonSchema.control.data && // we have data to check
    // the current number of properties in the object is greater or equals to the maxProperties
    Object.keys(jsonSchema.control.data).length >= jsonSchema.control.schema.maxProperties
  );
});

const removePropertyDisabled = computed(() => {
  return (
    // add is disabled because the overall control is disabled
    !jsonSchema.control.enabled ||
    // add is disabled because of contraints
    (appliedOptions.value.restrict && minPropertiesReached)
  );
});

const minPropertiesReached = computed(() => {
  return (
    jsonSchema.control.schema.minProperties !== undefined && // we have minProperties constraint
    jsonSchema.control.data && // we have data to check
    // the current number of properties in the object is less or equals to the minProperties
    Object.keys(jsonSchema.control.data).length <= jsonSchema.control.schema.minProperties
  );
});

const newPropertyErrors = computed(() => {
  if (newPropertyName.value) {
    const messages = propertyNameValidator
      ? (validate(propertyNameValidator, newPropertyName.value)
        .map((error) => error.message)
        .filter((message) => message) as string[])
      : [];
    if (reservedPropertyNames.value.includes(newPropertyName.value) || additionalPropertyItems.value.find((ap) => ap.propertyName === newPropertyName.value) !== undefined) {
      // already defined
      messages.push(`Property '${newPropertyName.value}' is already defined`);
    }
    // JSONForms has special means for "[]." chars - those are part of the path composition so for not we can't support those without special handling
    if (newPropertyName.value.includes("[")) {
      messages.push("Property name contains invalid char: [");
    }
    if (newPropertyName.value.includes("]")) {
      messages.push("Property name contains invalid char: ]");
    }
    if (newPropertyName.value.includes(".")) {
      messages.push("Property name contains invalid char: .");
    }
    return messages;
  }
  return [];
});

const placeholder = computed(() => {
  return t.value(i18nKey("newProperty.placeholder"), "New Property");
});

const additionalPropertiesTitle = computed(() => {
  const additionalProperties = jsonSchema.control.schema.additionalProperties;
  const label = typeof additionalProperties === "object" && Object.prototype.hasOwnProperty.call(additionalProperties, "title") ? additionalProperties.title ?? "Additional Properties" : "Additional Properties";
  return t.value(i18nKey("title"), label);
});

const additionalPropertiesDescription = computed(() => {
  const additionalProperties = jsonSchema.control.schema.additionalProperties;
  if (typeof additionalProperties === "object" && Object.prototype.hasOwnProperty.call(additionalProperties, "description")) {
    return additionalProperties.description;
  }
  return null;
});

const isSimpleStringType = computed(() => {
  const additionalProperties = jsonSchema.control.schema.additionalProperties;
  return typeof additionalProperties === "object" && additionalProperties.type === "string";
});

const updatePropertyValue = (index: number, value: any) => {
  const item = additionalPropertyItems.value[index];
  if (!item) return;

  item.value = value;
  syncToData();
};

const addToLabel = computed(() => {
  return t.value(i18nKey("btn.add"), `Add to ${additionalPropertiesTitle.value}`);
});

const deleteLabel = computed(() => {
  return t.value(i18nKey("btn.delete"), `Delete from ${additionalPropertiesTitle.value}`);
});

watch(
  () => jsonSchema.control.data,
  (newData) => {
    // revert back any undefined values back to the default value when the key is part of the addtional properties since we want to preserved the key
    // for example when we have a string additonal property then when we clear the text component the componet by default sets the value to undefined to remove the property from the object - for additional properties we do not want that behaviour
    if (typeof jsonSchema.control.data === "object") {
      const keys = Object.keys(newData);
      let hasChanges = false;
      additionalPropertyItems.value.forEach((ap) => {
        if (
          ap.schema &&
          (!keys.includes(ap.propertyName) || newData[ap.propertyName] === undefined || (newData[ap.propertyName] === null && ap.schema.type !== "null")) // createDefaultValue will return null only when the ap.schema.type is 'null'
        ) {
          const newValue = null;
          hasChanges = newData[ap.propertyName] !== newValue;
          newData[ap.propertyName] = newValue;
        }
      });
      if (hasChanges) {
        jsonSchema.handleChange(jsonSchema.control.path, newData);
      }
    }
  },
  { deep: true }
);

const i18nKey = (key: string) => {
  return getI18nKey(jsonSchema.control.schema, jsonSchema.control.uischema, jsonSchema.control.path, `additionalProperties.${key}`);
};

const removeProperty = (index: number) => {
  additionalPropertyItems.value.splice(index, 1);
  syncToData();
};

const noData = computed(() => {
  return !additionalPropertyItems.value || additionalPropertyItems.value.length === 0;
});
</script>
<style scoped>
.additional-properties-inline {
  margin: 0;
  padding: 0;
}

.additional-properties-empty {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  color: var(--text-color-secondary);
  font-style: italic;
}

.additional-properties-list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.additional-properties-row {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
}

.additional-properties-row .additional-properties-equals {
  margin-top: 0.5rem;
}

.additional-properties-row .remove-btn {
  margin-top: 0.25rem;
}

.additional-properties-key {
  flex: 0 0 35%;
}

.additional-properties-equals {
  color: var(--text-color-secondary);
  font-weight: 500;
}

.additional-properties-value {
  flex: 1;
}

.key-input,
.value-input {
  width: 100%;
}

.key-input:disabled {
  background-color: var(--surface-100);
  opacity: 1;
}

.remove-btn {
  flex-shrink: 0;
}

.additional-properties-add {
  margin-top: 0.25rem;
}

.p-error {
  display: block;
  font-size: 0.75rem;
  margin-top: 0.25rem;
}

.add-property-dialog .field {
  margin-bottom: 1rem;
}

.add-property-dialog .field label {
  display: block;
  margin-bottom: 0.5rem;
  font-weight: 500;
}
</style>
