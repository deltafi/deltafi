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
  <div class="action-configuration-dialog">
    <div class="action-configuration-panel">
      <div v-if="hasErrors" class="pt-2">
        <Message severity="error" :sticky="true" class="mb-2 mt-0" @close="clearErrors()">
          <ul>
            <div v-for="(error, key) in errors" :key="key">
              <li class="text-wrap text-break">{{ error }}</li>
            </div>
          </ul>
        </Message>
      </div>
      <dl>
        <template v-for="displayActionInfo of getDisplayValues(rowdata)" :key="displayActionInfo">
          <template v-if="displayFieldTest(displayActionInfo)"> </template>
          <template v-else>
            <dt>{{ displayMap.get(displayActionInfo).header }}</dt>
            <dd v-if="_.isEqual(displayMap.get(displayActionInfo).type, 'string')">
              <InputText v-model="rowdata[displayActionInfo]" class="inputWidth" :disabled="displayMap.get(displayActionInfo).disableEdit && rowdata.disableEdit" />
            </dd>
            <dd v-else-if="_.isEqual(displayMap.get(displayActionInfo).type, 'object')">
              <template v-if="Array.isArray(rowdata[displayActionInfo])">
                <template v-if="rowdata[displayActionInfo].includes('any')">
                  <DialogTemplate component-name="plugin/ListEdit" :model-value="rowdata[displayActionInfo]" :header="`Add New ${displayMap.get(displayActionInfo).header}`" dialog-width="25vw">
                    <div class="p-inputtext p-component">
                      <div v-for="item in rowdata[displayActionInfo]" :key="item" class="list-item">{{ item }}</div>
                    </div>
                  </DialogTemplate>
                </template>
                <template v-else>
                  <div v-for="item in rowdata[displayActionInfo]" :key="item" class="list-item">{{ item }}</div>
                </template>
              </template>
              <template v-if="_.isEqual(displayActionInfo, 'schema')">
                <div class="deltafi-fieldset">
                  <div class="px-2 pt-3">
                    <json-forms :data="data" :renderers="renderers" :uischema="uischema" :schema="rowdata['schema']" @change="onChange($event, rowdata)" />
                  </div>
                </div>
              </template>
              <template v-if="_.isEqual(displayActionInfo, 'collect')">
                <div class="deltafi-fieldset">
                  <div class="px-2 pt-3">
                    <dt>maxAge</dt>
                    <dd>
                      <InputText v-model="collectData['maxAge']" class="inputWidth" :disabled="displayMap.get(displayActionInfo).disableEdit && rowdata.disableEdit" />
                    </dd>
                    <dt>minNum</dt>
                    <dd>
                      <InputNumber v-model="collectData['minNum']" class="inputWidth" :disabled="displayMap.get(displayActionInfo).disableEdit && rowdata.disableEdit" :min="0" show-buttons />
                    </dd>
                    <dt>maxNumber</dt>
                    <dd>
                      <InputNumber v-model="collectData['maxNum']" class="inputWidth" :disabled="displayMap.get(displayActionInfo).disableEdit && rowdata.disableEdit" :min="0" show-buttons />
                    </dd>
                    <dt>metadataKey</dt>
                    <dd>
                      <InputText v-model="collectData['metadataKey']" class="inputWidth" :disabled="displayMap.get(displayActionInfo).disableEdit && rowdata.disableEdit" />
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
import { computed, defineEmits, defineProps, provide, reactive, ref } from "vue";

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
const uischema = ref(undefined);
const isMounted = ref(useMounted());
const emit = defineEmits(["updateAction"]);
const props = defineProps({
  rowDataProp: {
    type: Object,
    required: true,
  },
  actionIndexProp: {
    type: Number,
    required: true,
  },
  closeDialogCommand: {
    type: Object,
    default: null,
  },
});

const defaultCollectTemplate = ref({
  maxAge: null,
  minNum: null,
  maxNum: null,
  metadataKey: null,
});

const errors = ref([]);

const { actionIndexProp: actionIndex, closeDialogCommand } = reactive(props);

const rowdata = reactive(JSON.parse(JSON.stringify(props.rowDataProp)));
const data = ref(_.isEmpty(_.get(rowdata, "parameters", null)) ? {} : rowdata["parameters"]);
const collectData = ref(_.isEmpty(_.get(rowdata, "collect", null)) ? defaultCollectTemplate.value : rowdata["collect"]);

const originalCollectData = JSON.parse(JSON.stringify(collectData.value));

const onChange = (event, element) => {
  element["parameters"] = event.data;
};

const displayMap = new Map([
  ["name", { header: "Name", type: "string", disableEdit: false }],
  ["type", { header: "Type", type: "string", disableEdit: true }],
  ["displayName", { header: "Display Name", type: "string", disableEdit: true }],
  ["description", { header: "Description", type: "string", disableEdit: true }],
  ["collect", { header: "Collect", type: "object", disableEdit: false }],
  ["schema", { header: "Parameters", type: "object", disableEdit: false }],
  ["requiresDomains", { header: "Requires Domains", type: "object", disableEdit: false }],
  ["requiresEnrichments", { header: "Requires Enrichments", type: "object", disableEdit: false }],
  ["requiresMetadataKeyValues", { header: "Requires Metadata Key Values", type: "object", disableEdit: false }],
]);

const displayKeysList = ["name", "type", "description", "collect", "schema", "requiresDomains", "requiresEnrichments", "requiresMetadataKeyValues"];

const getDisplayValues = (obj) => {
  return _.intersection(Object.keys(obj), displayKeysList);
};

const displayFieldTest = (displayActionInfo) => {
  let fieldsCheck = ["requiresDomains", "requiresEnrichments", "requiresMetadataKeyValues"];
  return (fieldsCheck.includes(displayActionInfo) && _.isEmpty(rowdata[displayActionInfo])) || (_.isEqual(displayActionInfo, "schema") && _.isEmpty(_.get(rowdata, "schema.properties", null)));
};

const hasErrors = computed(() => {
  return errors.value.length > 0;
});

const clearErrors = () => {
  errors.value = [];
};

const submit = async () => {
  clearErrors();
  // Remove any value that have not changed from the original originalCollectData value it was set at
  let changedCollectValues = _.omitBy(collectData.value, function (v, k) {
    return JSON.stringify(originalCollectData[k]) === JSON.stringify(v);
  });

  let newCollect = {};
  if (!_.isEmpty(changedCollectValues)) {
    if (!_.isEmpty(collectData.value["maxAge"])) {
      const regexPattern = new RegExp("^P([0-9]+(?:[,.][0-9]+)?Y)?([0-9]+(?:[,.][0-9]+)?M)?([0-9]+(?:[,.][0-9]+)?D)?(?:T([0-9]+(?:[,.][0-9]+)?H)?([0-9]+(?:[,.][0-9]+)?M)?([0-9]+(?:[,.][0-9]+)?S)?)?$");
      let match = regexPattern.test(collectData.value["maxAge"]);

      if (!match) {
        errors.value.push("maxAge is not a valid ISO8601 Duration");
      } else {
        newCollect["maxAge"] = collectData.value["maxAge"];
      }
    }

    if (_.isNumber(collectData.value["minNum"])) {
      newCollect["minNum"] = collectData.value["minNum"];
    }

    if (_.isNumber(collectData.value["maxNum"])) {
      newCollect["maxNum"] = collectData.value["maxNum"];
    }

    if (_.isNumber(collectData.value["minNum"]) && _.isNumber(collectData.value["maxNum"])) {
      if (!_.lt(collectData.value["minNum"], collectData.value["maxNum"])) {
        errors.value.push("minNum cannot be greater than maxNum");
      }
    }

    if (!_.isEmpty(collectData.value["metadataKey"])) {
      newCollect["metadataKey"] = collectData.value["metadataKey"];
    }
  }

  if (!_.isEmpty(errors.value)) {
    return;
  }

  if (!_.isEmpty(newCollect)) {
    rowdata["collect"] = newCollect;
  }

  closeDialogCommand.command();
  emit("updateAction", { actionIndex: actionIndex, updatedAction: rowdata });
};
</script>

<style lang="scss">
@import "@/styles/components/flowBuilder/action-configuration-dialog.scss";
</style>
