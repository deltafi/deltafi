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
  <div v-if="jsonSchema.control.visible" class="px-0 pb-2">
    <div class="d-flex w-100 justify-content-between">
      <div class="px-0 py-0">
        <dt>Additional properties renderer</dt>
        <dd class="pt-3">{{ additionalPropertiesTitle }}</dd>
      </div>
      <div class="btn-group">
        <div class="flex-column additionPropsInputBox">
          <InputText v-model="newPropertyName" :disabled="!jsonSchema.control.enabled" :placeholder="placeholder" :class="!_.isEmpty(newPropertyErrors) && 'p-invalid'" />
          <div v-if="!_.isEmpty(newPropertyErrors)">
            <small v-for="(error, key) in newPropertyErrors" :key="key" class="p-error">
              {{ error }}
            </small>
          </div>
        </div>
        <Button v-tooltip.bottom="addToLabel" icon="pi pi-plus" small :disabled="addPropertyDisabled" text rounded @click="addProperty" />
      </div>
    </div>
    <div v-if="noData">No data</div>
    <div v-for="(element, index) in additionalPropertyItems" :key="`${index}`">
      <div>
        <div class="inputField">
          <dispatch-renderer v-if="element.schema && element.uischema" :visible="jsonSchema.control.visible" :enabled="jsonSchema.control.enabled" :schema="element.schema" :uischema="element.uischema" :path="element.path" :renderers="jsonSchema.control.renderers" :cells="jsonSchema.control.cells" />
        </div>
        <div v-if="jsonSchema.control.enabled" class="deleteButton">
          <Button v-tooltip.bottom="deleteLabel" icon="pi pi-trash text-dark" small :disabled="removePropertyDisabled" text rounded @click="removeProperty(element.propertyName)" />
        </div>
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

const { useAjv, useControlAppliedOptions, useTranslator } = useSchemaComposition();
type Input = ReturnType<typeof useJsonFormsControlWithDetail>;
interface AdditionalPropertyType {
  propertyName: string;
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

const addProperty = () => {
  if (newPropertyName.value) {
    const additionalProperty = toAdditionalPropertyType(newPropertyName.value, undefined);
    if (additionalProperty) {
      additionalPropertyItems.value = [...additionalPropertyItems.value, additionalProperty];
    }
    if (typeof jsonSchema.control.data === "object" && additionalProperty.schema) {
      jsonSchema.control.data[newPropertyName.value] = null;
      // we need always to preserve the key even when the value is "empty"
      jsonSchema.handleChange(jsonSchema.control.path, jsonSchema.control.data);
    }
  }
  newPropertyName.value = "";
};

const removeProperty = (propName: string) => {
  additionalPropertyItems.value = additionalPropertyItems.value.filter((d) => d.propertyName !== propName);

  if (typeof jsonSchema.control.data === "object") {
    delete jsonSchema.control.data[propName];
    jsonSchema.handleChange(jsonSchema.control.path, jsonSchema.control.data);
  }
};

const noData = computed(() => {
  return !additionalPropertyItems.value || additionalPropertyItems.value.length === 0;
});
</script>
<style>
.inputWidth {
  width: 10% !important;
}

.inputField {
  display: inline-block;
  width: 90%;
}

.deleteButton {
  display: inline-block;
  width: 10%;
}

.additionPropsInputBox {
  width: 185px;
}
</style>
