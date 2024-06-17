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
  <div v-if="schemaData.control.visible" :class="schemaData.styles.arrayList.root" class="mt-1 align-items-center">
    <dl :class="schemaData.styles.arrayList.legend + ' mb-0'">
      <div class="btn-group pb-0 align-items-center">
        <dt :class="schemaData.styles.arrayList.label">
          {{ schemaData.control.path.split(".").pop() }}
        </dt>
        <Button icon="pi pi-plus" small :class="schemaData.styles.arrayList.addButton + ' align-items-center text-dark'" text rounded @click="addButtonClick" />
      </div>
      <dd class="pb-0">
        <small v-if="!_.isEmpty(schemaData.control.description)" :id="schemaData.control.id + '-input-help'">
          <pre>{{ schemaData.control.description }}</pre>
        </small>
        <span v-if="!noData" class="deltafi-fieldset pb-1 pt-3">
          <div v-for="(element, index) in renderArrayData" :key="`${schemaData.control.path}-${index}`" :class="checkSchemaIsObject(schemaData.styles.arrayList.itemWrapper, schemaData.control.path, schemaData.control.schema.type)">
            <ArrayListElement :move-up="schemaData.moveUp?.(schemaData.control.path, index)" :move-up-enabled="index > 0" :move-down="schemaData.moveDown?.(schemaData.control.path, index)" :move-down-enabled="index < schemaData.control.data.length - 1" :delete="schemaData.removeItems?.(schemaData.control.path, [index])" :label="schemaData.childLabelForIndex(index)" :styles="schemaData.styles">
              <dispatch-renderer :schema="schemaData.control.schema" :uischema="schemaData.childUiSchema" :path="composePaths(schemaData.control.path, `${index}`)" :enabled="schemaData.control.enabled" :renderers="schemaData.control.renderers" :cells="schemaData.control.cells" />
            </ArrayListElement>
          </div>
        </span>
        <div v-if="noData" :class="schemaData.styles.arrayList.noData + ' mb-n1 pt-1'">No data</div>
      </dd>
    </dl>
  </div>
</template>

<script setup lang="ts">
import ArrayListElement from "@/components/jsonSchemaRenderers/arrayRender/ArrayListElement.vue";
import { computed, reactive } from "vue";
import { composePaths, createDefaultValue, ControlElement } from "@jsonforms/core";
import { DispatchRenderer, rendererProps, useJsonFormsArrayControl } from "@jsonforms/vue";
import { useVanillaArrayControl } from "@jsonforms/vue-vanilla";
import _ from "lodash";

import Button from "primevue/button";

const props = defineProps({
  ...rendererProps<ControlElement>(),
});

const schemaData = reactive(useVanillaArrayControl(useJsonFormsArrayControl(props)));

const noData = computed(() => {
  return !schemaData.control.data || schemaData.control.data.length === 0;
});

const renderArrayData = computed(() => {
  return schemaData.control.data;
});

// Checks to see if this is the root level of an array and puts a border around all items in the array
const checkSchemaIsObject = (value: any, path: any, schemaType: any) => {
  if (_.isEqual(schemaType, "object")) {
    return value + " deltafi-fieldset mb-2";
  }
  return value;
};

const addButtonClick = () => {
  schemaData.addItem(schemaData.control.path, createDefaultValue(schemaData.control.schema))();
};
</script>
<style scoped>
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
</style>
