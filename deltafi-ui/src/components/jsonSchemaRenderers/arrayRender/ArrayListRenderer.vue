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
  <fieldset v-if="schemaData.control.visible" :class="schemaData.styles.arrayList.root" class="mt-1 align-items-center">
    <div :class="schemaData.styles.arrayList.legend">
      <div class="btn-group pb-0">
        <div class="field">
          <legend :class="schemaData.styles.arrayList.label">
            {{ schemaData.control.path + ":" }}
          </legend>
        </div>
        <Button icon="pi pi-plus" small :class="schemaData.styles.arrayList.addButton + ' align-items-center text-dark'" text rounded @click="addButtonClick" />
      </div>
      <div>
        <small :id="schemaData.control.id + '-input-help'">{{ schemaData.control.description }}</small>
      </div>
    </div>
    <div v-for="(element, index) in renderArrayData" :key="`${schemaData.control.path}-${index}`" :class="schemaData.styles.arrayList.itemWrapper">
      <ArrayListElement :move-up="schemaData.moveUp?.(schemaData.control.path, index)" :move-up-enabled="index > 0" :move-down="schemaData.moveDown?.(schemaData.control.path, index)" :move-down-enabled="index < schemaData.control.data.length - 1" :delete="schemaData.removeItems?.(schemaData.control.path, [index])" :label="schemaData.childLabelForIndex(index)" :styles="schemaData.styles">
        <dispatch-renderer :schema="schemaData.control.schema" :uischema="schemaData.childUiSchema" :path="composePaths(schemaData.control.path, `${index}`)" :enabled="schemaData.control.enabled" :renderers="schemaData.control.renderers" :cells="schemaData.control.cells" />
      </ArrayListElement>
    </div>
    <div v-if="noData" :class="schemaData.styles.arrayList.noData">No data</div>
  </fieldset>
</template>
  
<script setup lang="ts">
import ArrayListElement from "@/components/jsonSchemaRenderers/arrayRender/ArrayListElement.vue";
import { computed, reactive } from "vue";
import { composePaths, createDefaultValue, ControlElement } from "@jsonforms/core";
import { DispatchRenderer, rendererProps, useJsonFormsArrayControl } from "@jsonforms/vue";
import { useVanillaArrayControl } from "@jsonforms/vue-vanilla";

import Button from "primevue/button";

const props = defineProps({
  ...rendererProps<ControlElement>(),
});

const schemaData = reactive(useVanillaArrayControl(useJsonFormsArrayControl(props)));

const noData = computed(() => {
  return !schemaData.control.data || schemaData.control.data.length === 0;
});

const renderArrayData = computed(() => {
  return schemaData.control.data
});

const addButtonClick = () => {
  schemaData.addItem(schemaData.control.path, createDefaultValue(schemaData.control.schema))();
};
</script>
<style scoped>
.field * {
  display: block;
}
</style>