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
  <fieldset v-if="schemaData.control.visible">
    <dispatch-renderer :visible="schemaData.control.visible" :enabled="schemaData.control.enabled" :schema="schemaData.control.schema" :uischema="detailUiSchema" :path="schemaData.control.path" :renderers="schemaData.control.renderers" :cells="schemaData.control.cells" />
    <AdditionalPropertiesRenderer v-if="hasAdditionalProperties && showAdditionalProperties" :input="input"></AdditionalPropertiesRenderer>
  </fieldset>
</template>

<script setup lang="ts">
import useSchemaComposition from "@/components/jsonSchemaRenderers/util/useSchemaComposition";
import { ControlElement, findUISchema, Generate, GroupLayout } from "@jsonforms/core";
import { computed, reactive } from "vue";
import { DispatchRenderer, rendererProps, useJsonFormsControlWithDetail } from "@jsonforms/vue";
import AdditionalPropertiesRenderer from "./AdditionalPropertiesRenderer.vue";
import _ from "lodash";
const { useNested } = useSchemaComposition();

const props = defineProps({
  ...rendererProps<ControlElement>(),
});

const nested = useNested("object");

const schemaData = reactive(useJsonFormsControlWithDetail(props));

const input = reactive(useJsonFormsControlWithDetail(props));

const detailUiSchema = computed(() => {
  const uiSchemaGenerator = () => {
    const uiSchema = Generate.uiSchema(schemaData.control.schema, "Group");
    if (_.isEmpty(schemaData.control.path)) {
      uiSchema.type = "VerticalLayout";
    } else {
      (uiSchema as GroupLayout).label = schemaData.control.label;
    }
    return uiSchema;
  };
  let result = findUISchema(schemaData.control.uischemas, schemaData.control.schema, schemaData.control.uischema.scope, schemaData.control.path, uiSchemaGenerator, schemaData.control.uischema, schemaData.control.rootSchema);
  if (nested.level > 0) {
    result = _.cloneDeep(result);
    result.options = {
      ...result.options,
      bare: true,
      alignLeft: nested.level >= 4 || nested.parentElement === "array",
    };
  }
  return result;
});

const hasAdditionalProperties = computed(() => {
  return (
    !_.isEmpty(schemaData.control.schema.patternProperties) || _.isObject(schemaData.control.schema.additionalProperties)
    // do not support - additionalProperties === true - since then the type should be any and we won't know what kind of renderer we should use for new properties
  );
});
const showAdditionalProperties = computed(() => {
  const showAdditionalProperties = schemaData.control.uischema.options?.showAdditionalProperties;
  return showAdditionalProperties === undefined || showAdditionalProperties === true;
});
</script>
