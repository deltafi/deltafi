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
  <div v-if="schemaData.control.visible" class="pb-2">
    <dt>{{ computedLabel }}</dt>
    <div class="deltafi-fieldset">
      <dispatch-renderer :visible="schemaData.control.visible" :enabled="schemaData.control.enabled" :schema="schemaData.control.schema" :uischema="detailUiSchema" :path="schemaData.control.path" :renderers="schemaData.control.renderers" :cells="schemaData.control.cells" />
      <small :id="schemaData.control.id + '-input-help'">{{ schemaData.control.description }}</small>
      <AdditionalPropertiesRenderer v-if="hasAdditionalProperties && showAdditionalProperties" :input="input"></AdditionalPropertiesRenderer>
    </div>
  </div>
</template>

<script setup lang="ts">
import useSchemaComposition from "@/components/jsonSchemaRenderers/defaultRenderers/util/useSchemaComposition";
import { ControlElement, findUISchema, Generate } from "@jsonforms/core";
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

const input = useJsonFormsControlWithDetail(props);

const detailUiSchema = computed(() => {
  const uiSchemaGenerator = () => {
    const uiSchema = Generate.uiSchema(schemaData.control.schema, "Group");
    if (_.isEmpty(schemaData.control.path)) {
      uiSchema.type = "VerticalLayout";
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

const computedLabel = computed(() => {
  if (schemaData.control.config.defaultLabels) {
    return schemaData.control.label;
  }

  return schemaData.control.i18nKeyPrefix.split(".").pop();
});
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
