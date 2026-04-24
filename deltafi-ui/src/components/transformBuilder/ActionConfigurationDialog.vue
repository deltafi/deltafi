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
  <div id="error-message" class="action-configuration-dialog">
    <div class="action-configuration-panel">
      <div v-if="hasErrors" class="pt-2" role="alert" aria-live="assertive">
        <Message severity="error" :sticky="true" class="mb-2 mt-0" @close="clearErrors()">
          <ul class="error-list">
            <li v-for="(error, index) in errors" :key="index">{{ error }}</li>
          </ul>
        </Message>
      </div>
      <dl>
        <template v-for="displayActionInfo of getDisplayValues(rowData)" :key="displayActionInfo">
          <template v-if="displayFieldTest(displayActionInfo)" />
          <template v-else>
            <dt v-if="displayActionInfo !== 'schema'">{{ displayMap.get(displayActionInfo).header }}</dt>
            <dd v-if="_.isEqual(displayMap.get(displayActionInfo).type, 'string')">
              <InputText v-model="rowData[displayActionInfo]" class="inputWidth" :disabled="displayMap.get(displayActionInfo).disableEdit && rowData.disableEdit" />
            </dd>
            <dd v-else-if="_.isEqual(displayMap.get(displayActionInfo).type, 'object')">
              <template v-if="Array.isArray(rowData[displayActionInfo])">
                <template v-if="rowData[displayActionInfo].includes('any')">
                  <DialogTemplate component-name="plugin/ListEdit" :model-value="rowData[displayActionInfo]" :header="`Add New ${displayMap.get(displayActionInfo).header}`" dialog-width="25vw">
                    <div class="p-inputtext p-component">
                      <div v-for="item in rowData[displayActionInfo]" :key="item" class="list-item">
                        {{ item }}
                      </div>
                    </div>
                  </DialogTemplate>
                </template>
                <template v-else>
                  <div v-for="item in rowData[displayActionInfo]" :key="item" class="list-item">
                    {{ item }}
                  </div>
                </template>
              </template>
              <template v-if="_.isEqual(displayActionInfo, 'schema')">
                <JsonForms :data="data" :renderers="renderers" :uischema="uiSchema" :schema="rowData['schema']" @change="onChange($event, rowData)" />
              </template>
              <template v-if="_.isEqual(displayActionInfo, 'join')">
                <dl class="join-fields">
                  <dt><label for="ac-join-max-age">maxAge</label></dt>
                  <dd>
                    <InputText id="ac-join-max-age" v-model="joinData['maxAge']" class="inputWidth" :disabled="displayMap.get(displayActionInfo).disableEdit && rowData.disableEdit" aria-describedby="ac-join-max-age-help" />
                    <small id="ac-join-max-age-help" style="display: inline-block">The maximum duration (ISO 8601) to wait after the first DeltaFile is received for a join group before this action is executed</small>
                  </dd>
                  <dt><label for="ac-join-min-num">minNum</label></dt>
                  <dd>
                    <InputNumber id="ac-join-min-num" v-model="joinData['minNum']" class="inputWidth" :disabled="displayMap.get(displayActionInfo).disableEdit && rowData.disableEdit" :min="0" show-buttons aria-describedby="ac-join-min-num-help" />
                    <small id="ac-join-min-num-help" style="display: inline-block">The minimum number of DeltaFiles to join within maxAge. If this number is not reached, all DeltaFiles received for the join group will have this action marked in error.</small>
                  </dd>
                  <dt><label for="ac-join-max-num">maxNum</label></dt>
                  <dd>
                    <InputNumber id="ac-join-max-num" v-model="joinData['maxNum']" class="inputWidth" :disabled="displayMap.get(displayActionInfo).disableEdit && rowData.disableEdit" :min="0" show-buttons aria-describedby="ac-join-max-num-help" />
                    <small id="ac-join-max-num-help" style="display: inline-block">The maximum number of DeltaFiles to join before this action is executed</small>
                  </dd>
                  <dt><label for="ac-join-metadata-key">metadataKey</label></dt>
                  <dd>
                    <InputText id="ac-join-metadata-key" v-model="joinData['metadataKey']" class="inputWidth" :disabled="displayMap.get(displayActionInfo).disableEdit && rowData.disableEdit" aria-describedby="ac-join-metadata-key-help" />
                    <small id="ac-join-metadata-key-help" style="display: inline-block">An optional metadata key used to get the value to group joins by. If not set, DeltaFiles are not grouped when joining.</small>
                  </dd>
                </dl>
              </template>
            </dd>
          </template>
        </template>
      </dl>
    </div>
    <teleport v-if="isMounted" to="#dialogTemplate">
      <div class="p-dialog-footer">
        <Button label="Cancel" text @click="cancel()" />
        <Button label="Submit" @click="submit()" />
      </div>
    </teleport>
  </div>
</template>

<script setup>
import DialogTemplate from "@/components/DialogTemplate.vue";
import { useMounted } from "@vueuse/core";
import { computed, nextTick, provide, reactive, ref } from "vue";

import usePrimeVueJsonSchemaUIRenderers from "@/composables/usePrimeVueJsonSchemaUIRenderers";
import { JsonForms } from "@jsonforms/vue";

import Button from "primevue/button";
import InputNumber from "primevue/inputnumber";
import InputText from "primevue/inputtext";
import Message from "primevue/message";
import _ from "lodash";

const { rendererList, myStyles } = usePrimeVueJsonSchemaUIRenderers();
provide("style", myStyles);
const renderers = ref(Object.freeze(rendererList));
const uiSchema = ref(undefined);
const isMounted = ref(useMounted());
const emit = defineEmits(["updateAction", "refreshAndClose"]);
const props = defineProps({
  rowDataProp: {
    type: Object,
    required: true,
  },
  actionIndexProp: {
    type: Number,
    required: true,
  },
});

const defaultJoinTemplate = ref({
  maxAge: null,
  minNum: null,
  maxNum: null,
  metadataKey: null,
});

const errors = ref([]);

const { actionIndexProp: actionIndex } = reactive(props);

const rowData = reactive(JSON.parse(JSON.stringify(props.rowDataProp)));

const extractSchemaDefaults = (schema) => {
  const defaults = {};
  if (schema && schema.properties) {
    for (const [key, propSchema] of Object.entries(schema.properties)) {
      if (propSchema.default !== undefined) {
        defaults[key] = propSchema.default;
      } else if (propSchema.properties) {
        const nested = extractSchemaDefaults(propSchema);
        if (Object.keys(nested).length > 0) {
          defaults[key] = nested;
        }
      }
    }
  }
  return defaults;
};

// Extract defaults from schema and merge with existing parameters
const getInitialData = () => {
  const existingParams = _.get(rowData, "parameters", {}) || {};
  const schema = _.get(rowData, "schema", null);

  if (!schema || !schema.properties) {
    return existingParams;
  }

  const defaults = extractSchemaDefaults(schema);

  // Merge: existing values take precedence over defaults
  return { ...defaults, ...existingParams };
};

const data = ref(getInitialData());
const joinData = ref(_.isEmpty(_.get(rowData, "join", null)) ? defaultJoinTemplate.value : rowData["join"]);

const originalJoinData = JSON.parse(JSON.stringify(joinData.value));

const onChange = (event, element) => {
  data.value = event.data;
  element["parameters"] = event.data;
};

const displayMap = new Map([
  ["name", { header: "Name", type: "string", disableEdit: false }],
  ["type", { header: "Type", type: "string", disableEdit: true }],
  ["displayName", { header: "Display Name", type: "string", disableEdit: true }],
  ["description", { header: "Description", type: "string", disableEdit: true }],
  ["join", { header: "Join", type: "object", disableEdit: false }],
  ["schema", { header: "Parameters", type: "object", disableEdit: false }],
]);

const displayKeysList = ["name", "description", "schema"];

const getDisplayValues = (obj) => {
  const displayValues = _.intersection(Object.keys(obj), displayKeysList);
  if (obj.supportsJoin) {
    displayValues.push("join");
  }

  return displayValues;
};

const displayFieldTest = (displayActionInfo) => {
  const fieldsCheck = ["requiresDomains", "requiresEnrichments", "requiresMetadataKeyValues"];
  return (fieldsCheck.includes(displayActionInfo) && _.isEmpty(rowData[displayActionInfo])) || (_.isEqual(displayActionInfo, "schema") && _.isEmpty(_.get(rowData, "schema.properties", null)));
};

const hasErrors = computed(() => {
  return errors.value.length > 0;
});

const clearErrors = () => {
  errors.value = [];
};

const cancel = () => {
  emit("refreshAndClose");
};

const validateMapParameters = (obj, parentName = "") => {
  const emptyKeyFields = [];
  const duplicateKeyFields = [];

  if (obj && typeof obj === "object") {
    for (const [key, value] of Object.entries(obj)) {
      if (key === "__has_empty_keys__") {
        const fieldName = parentName || "Parameters";
        if (!emptyKeyFields.includes(fieldName)) {
          emptyKeyFields.push(fieldName);
        }
      } else if (key === "__has_duplicate_keys__") {
        const fieldName = parentName || "Parameters";
        if (!duplicateKeyFields.includes(fieldName)) {
          duplicateKeyFields.push(fieldName);
        }
      } else if (key.startsWith("__new_key_")) {
        const fieldName = parentName || "Parameters";
        if (!emptyKeyFields.includes(fieldName)) {
          emptyKeyFields.push(fieldName);
        }
      } else if (value && typeof value === "object" && !Array.isArray(value)) {
        const nested = validateMapParameters(value, key);
        emptyKeyFields.push(...nested.emptyKeyFields);
        duplicateKeyFields.push(...nested.duplicateKeyFields);
      }
    }
  }
  return { emptyKeyFields, duplicateKeyFields };
};

const getMapValidationErrors = () => {
  const { emptyKeyFields, duplicateKeyFields } = validateMapParameters(data.value);
  const errors = [];

  emptyKeyFields.forEach(field => {
    errors.push(`${field} has a map entry with an empty key. Please provide a key or remove the entry.`);
  });

  duplicateKeyFields.forEach(field => {
    errors.push(`${field} has duplicate keys. Please use unique keys.`);
  });

  return errors;
};

const isValueEmpty = (value) => {
  if (value === null || value === undefined) return true;
  if (typeof value === 'string' && value.trim() === '') return true;
  if (Array.isArray(value) && value.length === 0) return true;
  return false;
};

const getRequiredFieldErrors = () => {
  const schema = _.get(rowData, "schema", null);
  if (!schema || !schema.required || !Array.isArray(schema.required)) {
    return [];
  }

  const missingFields = [];
  for (const fieldName of schema.required) {
    const value = _.get(data.value, fieldName);
    if (isValueEmpty(value)) {
      missingFields.push(fieldName);
    }
  }

  return missingFields.map(field => `${field} is required`);
};

const submit = async () => {
  clearErrors();

  const requiredFieldErrors = getRequiredFieldErrors();
  if (requiredFieldErrors.length > 0) {
    errors.value.push(...requiredFieldErrors);
  }

  const mapErrors = getMapValidationErrors();
  if (mapErrors.length > 0) {
    errors.value.push(...mapErrors);
  }

  // Remove any value that have not changed from the original originalJoinData value it was set at
  const changedJoinValues = _.omitBy(joinData.value, function (v, k) {
    return JSON.stringify(originalJoinData[k]) === JSON.stringify(v);
  });

  const newJoin = {};
  if (!_.isEmpty(changedJoinValues)) {
    if (!_.isEmpty(joinData.value["maxAge"])) {
      const regexPattern = new RegExp("^P([0-9]+(?:[,.][0-9]+)?Y)?([0-9]+(?:[,.][0-9]+)?M)?([0-9]+(?:[,.][0-9]+)?D)?(?:T([0-9]+(?:[,.][0-9]+)?H)?([0-9]+(?:[,.][0-9]+)?M)?([0-9]+(?:[,.][0-9]+)?S)?)?$");
      const match = regexPattern.test(joinData.value["maxAge"]);

      if (!match) {
        errors.value.push("maxAge is not a valid ISO8601 Duration");
      } else {
        newJoin["maxAge"] = joinData.value["maxAge"];
      }
    }

    if (_.isNumber(joinData.value["minNum"])) {
      newJoin["minNum"] = joinData.value["minNum"];
    }

    if (_.isNumber(joinData.value["maxNum"])) {
      newJoin["maxNum"] = joinData.value["maxNum"];
    }

    if (_.isNumber(joinData.value["minNum"])) {
      if (_.lte(joinData.value["minNum"], 0)) {
        errors.value.push("minNum cannot be 0 or less");
      }
    }

    if (_.isNumber(joinData.value["minNum"]) && _.isNumber(joinData.value["maxNum"])) {
      if (!_.lte(joinData.value["minNum"], joinData.value["maxNum"])) {
        errors.value.push("minNum cannot be greater than maxNum");
      }
    }

    if (!_.isEmpty(joinData.value["metadataKey"])) {
      newJoin["metadataKey"] = joinData.value["metadataKey"];
    }
  }

  if (!_.isEmpty(errors.value)) {
    const errorMessages = document.getElementById("error-message");
    await nextTick();
    errorMessages.scrollIntoView();
    return;
  }

  if (!_.isEmpty(newJoin)) {
    rowData["join"] = newJoin;
  }

  emit("updateAction", { actionIndex: actionIndex, updatedAction: rowData });
  emit("refreshAndClose");
};
</script>

<style>
.action-configuration-dialog {
  width: 98%;

  .error-list {
    margin: 0;
    padding-left: 1.25rem;
  }

  .action-configuration-panel {
    dt {
      font-weight: 600;
      font-size: 0.9rem;
      color: var(--text-color);
      margin-bottom: 0.25rem;
    }

    dd {
      margin-bottom: 1.25rem;
      margin-left: 0;
    }

    dl {
      margin-bottom: 0;
    }

    .p-panel-content {
      padding-bottom: 0.25rem !important;
    }

    /* Better spacing for nested parameters */
    :deep(.array-list-container),
    :deep(.additional-properties-container) {
      margin-top: 0.5rem;
    }

    /* Ensure consistent input widths */
    :deep(.p-inputtext),
    :deep(.p-select),
    :deep(.p-inputnumber) {
      width: 100%;
    }

    /* Better label styling for form fields */
    :deep(dt) {
      font-weight: 600;
      font-size: 0.9rem;
      color: var(--text-color);
      margin-bottom: 0.25rem;
    }

    /* Description text styling */
    :deep(small) {
      display: block;
      color: var(--text-color-secondary);
      margin-top: 0.25rem;
      line-height: 1.4;
    }
  }

  .inputWidth {
    width: 100% !important;
  }
}
</style>
