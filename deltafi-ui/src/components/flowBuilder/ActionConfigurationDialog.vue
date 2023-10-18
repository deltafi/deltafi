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
    <div v-for="displayActionInfo of getDisplayValues(rowdata)" :key="displayActionInfo">
      <template v-if="(_.isEqual(displayActionInfo, 'requiresDomains') && _.isEmpty(rowdata[displayActionInfo])) || (_.isEqual(displayActionInfo, 'requiresEnrichments') && _.isEmpty(rowdata[displayActionInfo])) || (_.isEqual(displayActionInfo, 'schema') && _.isEmpty(_.get(rowdata, 'schema.properties', null)))"> </template>
      <template v-else>
        <h5 class="font-weight-bold pb-2">{{ displayMap.get(displayActionInfo).header }}:</h5>
        <dd>
          <InputText v-if="_.isEqual(displayMap.get(displayActionInfo).type, 'string')" v-model="rowdata[displayActionInfo]" class="inputWidth" :disabled="displayMap.get(displayActionInfo).disableEdit && rowdata.disableEdit" />
        </dd>
        <dd v-if="_.isEqual(displayMap.get(displayActionInfo).type, 'object')">
          <template v-if="Array.isArray(rowdata[displayActionInfo])">
            <template v-if="rowdata[displayActionInfo].includes('any')">
              <DialogTemplate component-name="plugin/ListEdit" :model-value="rowdata[displayActionInfo]" :header="`Add New ${_.capitalize(action)}`" dialog-width="25vw">
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
            <div class="px-2">
              <json-forms :data="data" :renderers="renderers" :uischema="uischema" :schema="rowdata['schema']" @change="onChange($event, rowdata)" />
            </div>
          </template>
        </dd>
      </template>
    </div>
    <div class="action-configuration-dialog">
      <teleport v-if="isMounted" to="#dialogTemplate">
        <div class="p-dialog-footer">
          <Button label="Submit" @click="submit()" />
        </div>
      </teleport>
    </div>
  </div>
</template>
  
  <script setup>
import DialogTemplate from "@/components/DialogTemplate.vue";
import { useMounted } from "@vueuse/core";
import { provide, defineEmits, defineProps, reactive, ref } from "vue";

import usePrimeVueJsonSchemaUIRenderers from "@/composables/usePrimeVueJsonSchemaUIRenderers";
import { JsonForms } from "@jsonforms/vue";

import Button from "primevue/button";
import InputText from "primevue/inputtext";
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

const { actionIndexProp: actionIndex, closeDialogCommand } = reactive(props);

const rowdata = reactive(JSON.parse(JSON.stringify(props.rowDataProp)));

const data = ref(_.isEmpty(_.get(rowdata, "parameters", null)) ? {} : rowdata["parameters"]);

const onChange = (event, element) => {
  element["parameters"] = event.data;
};

const displayMap = new Map([
  ["name", { header: "Name", type: "string", disableEdit: false }],
  ["type", { header: "Type", type: "string", disableEdit: true }],
  ["displayName", { header: "Display Name", type: "string", disableEdit: true }],
  ["description", { header: "Description", type: "string", disableEdit: true }],
  ["schema", { header: "Parameters", type: "object", disableEdit: false }],
  ["requiresDomains", { header: "Requires Domains", type: "object", disableEdit: false }],
  ["requiresEnrichments", { header: "Requires Enrichments", type: "object", disableEdit: false }],
  ["requiresMetadataKeyValues", { header: "Requires Metadata Key Values", type: "object", disableEdit: false }],
]);

const displayKeysList = ["name", "type", "description", "schema", "requiresDomains", "requiresEnrichments", "requiresMetadataKeyValues"];

const getDisplayValues = (obj) => {
  return _.intersection(Object.keys(obj), displayKeysList);
};

const submit = async () => {
  closeDialogCommand.command();
  emit("updateAction", { actionIndex: actionIndex, updatedAction: rowdata });
};
</script>
  
<style lang="scss">
.action-configuration-dialog {
  width: 98%;
}
</style>
  