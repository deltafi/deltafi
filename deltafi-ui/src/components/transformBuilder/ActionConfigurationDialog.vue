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
  <div id="error-message" class="action-configuration-dialog">
    <div class="action-configuration-panel">
      <div v-if="hasErrors" class="pt-2">
        <Message severity="error" :sticky="true" class="mb-2 mt-0" @close="clearErrors()">
          <ul>
            <div v-for="(error, key) in errors" :key="key">
              <li class="text-wrap text-break">
                {{ error }}
              </li>
            </div>
          </ul>
        </Message>
      </div>
      <dl>
        <template v-for="displayActionInfo of getDisplayValues(rowData)" :key="displayActionInfo">
          <template v-if="displayFieldTest(displayActionInfo)" />
          <template v-else>
            <dt>{{ displayMap.get(displayActionInfo).header }}</dt>
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
                <div class="deltafi-fieldset">
                  <div class="px-2 pt-3">
                    <JsonForms :data="data" :renderers="renderers" :uischema="uiSchema" :schema="rowData['schema']" @change="onChange($event, rowData)" />
                  </div>
                </div>
              </template>
              <template v-if="_.isEqual(displayActionInfo, 'join')">
                <div class="deltafi-fieldset">
                  <div class="px-2 pt-3">
                    <dt>maxAge</dt>
                    <dd>
                      <InputText v-model="joinData['maxAge']" class="inputWidth" :disabled="displayMap.get(displayActionInfo).disableEdit && rowData.disableEdit" />
                      <small :id="join - maxAge - input - help" style="display: inline-block">The maximum duration (ISO 8601) to wait after the first DeltaFile is received for a join group before this action is executed</small>
                    </dd>
                    <dt>minNum</dt>
                    <dd>
                      <InputNumber v-model="joinData['minNum']" class="inputWidth" :disabled="displayMap.get(displayActionInfo).disableEdit && rowData.disableEdit" :min="0" show-buttons />
                      <small :id="join - minNum - input - help" style="display: inline-block">The minimum number of DeltaFiles to join within maxAge. If this number is not reached, all DeltaFiles received for the join group will have this action marked in error.</small>
                    </dd>
                    <dt>maxNum</dt>
                    <dd>
                      <InputNumber v-model="joinData['maxNum']" class="inputWidth" :disabled="displayMap.get(displayActionInfo).disableEdit && rowData.disableEdit" :min="0" show-buttons />
                      <small :id="join - maxNum - input - help" style="display: inline-block">The maximum number of DeltaFiles to join before this action is executed</small>
                    </dd>
                    <dt>metadataKey</dt>
                    <dd>
                      <InputText v-model="joinData['metadataKey']" class="inputWidth" :disabled="displayMap.get(displayActionInfo).disableEdit && rowData.disableEdit" />
                      <small :id="join - metadataKey - input - help" style="display: inline-block">An optional metadata key used to get the value to group joins by. If not set, DeltaFiles are not grouped when joining.</small>
                    </dd>
                  </div>
                </div>
              </template>
            </dd>
          </template>
        </template>
      </dl>
    </div>
    <teleport v-if="isMounted" to="#dialogTemplate">
      <div class="p-dialog-footer">
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
const data = ref(_.isEmpty(_.get(rowData, "parameters", null)) ? {} : rowData["parameters"]);
const joinData = ref(_.isEmpty(_.get(rowData, "join", null)) ? defaultJoinTemplate.value : rowData["join"]);

const originalJoinData = JSON.parse(JSON.stringify(joinData.value));

const onChange = (event, element) => {
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

const displayKeysList = ["name", "type", "description", "schema"];

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

const submit = async () => {
  clearErrors();
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

  .action-configuration-panel {
    .deltafi-fieldset {
      display: block;
      margin-inline-start: 2px;
      margin-inline-end: 2px;
      padding-block-start: 0.35em;
      padding-inline-start: 0.75em;
      padding-inline-end: 0.75em;
      padding-block-end: 0.625em;
      min-inline-size: min-content;
      border-radius: 4px;
      border: 1px solid #ced4da;
      border-width: 1px;
      border-style: groove;
      border-color: rgb(225, 225, 225);
      border-image: initial;
    }

    dt {
      margin-bottom: 0rem;
    }

    dd {
      margin-bottom: 1.2rem;
    }

    dl {
      margin-bottom: 1rem;
    }

    .p-panel-content {
      padding-bottom: 0.25rem !important;
    }
  }

  .inputWidth {
    width: 97% !important;
  }
}
</style>
