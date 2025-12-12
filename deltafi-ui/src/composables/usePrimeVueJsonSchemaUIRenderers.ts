/*
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
*/

import ArrayListRenderer from '../components/jsonSchemaRenderers/defaultRenderers/arrayRender/ArrayListRenderer.vue';
import PublishArrayListRenderer from '../components/jsonSchemaRenderers/publishRenderers/arrayRender/ArrayListRenderer.vue';
import SubscribeArrayListRenderer from '../components/jsonSchemaRenderers/subscribeRenderers/arrayRender/ArrayListRenderer.vue';
import BooleanRenderer from '../components/jsonSchemaRenderers/defaultRenderers/BooleanRenderer.vue';
import EnvVarRenderer from '../components/jsonSchemaRenderers/defaultRenderers/EnvVarRenderer.vue';
import IntegerRenderer from '../components/jsonSchemaRenderers/defaultRenderers/IntegerRenderer.vue';
import ObjectRenderer from "../components/jsonSchemaRenderers/defaultRenderers/ObjectRenderer.vue";
import PublishObjectRenderer from '../components/jsonSchemaRenderers/publishRenderers/ObjectRenderer.vue';
import SubscribeObjectRenderer from '../components/jsonSchemaRenderers/subscribeRenderers/ObjectRenderer.vue';
import StringRenderer from '../components/jsonSchemaRenderers/defaultRenderers/StringRenderer.vue';
import PublishStringRenderer from '../components/jsonSchemaRenderers/publishRenderers/StringRenderer.vue';
import SubscribeStringRenderer from '../components/jsonSchemaRenderers/subscribeRenderers/StringRenderer.vue';
import AdditionalPropertiesStringRenderer from '../components/jsonSchemaRenderers/defaultRenderers/AdditionalPropertiesStringRenderer.vue';
import { vanillaRenderers } from "@jsonforms/vue-vanilla";
import { rankWith, isObjectControl, isBooleanControl, isStringControl, isIntegerControl, schemaTypeIs, JsonSchema, UISchemaElement, isControl } from "@jsonforms/core";

// Custom tester for EnvVar type
// Detected via x-deltafi-type property in the field's schema
const isEnvVarControl = (uischema: UISchemaElement, schema: JsonSchema): boolean => {
  if (!isControl(uischema)) return false;

  // The schema passed is the ROOT schema, not the field's schema
  // We need to extract the field's schema using the uischema scope
  const scope = (uischema as { scope?: string })?.scope || '';
  const pathParts = scope.replace('#/properties/', '').split('/properties/');

  // Navigate to the field's schema
  type SchemaWithProps = JsonSchema & {
    properties?: Record<string, JsonSchema & {
      'x-deltafi-type'?: string;
      allOf?: Array<{ 'x-deltafi-type'?: string }>;
    }>;
  };

  let fieldSchema: SchemaWithProps | undefined = schema as SchemaWithProps;
  for (const part of pathParts) {
    if (fieldSchema?.properties?.[part]) {
      fieldSchema = fieldSchema.properties[part] as SchemaWithProps;
    } else {
      return false;
    }
  }

  if (!fieldSchema) return false;

  const extFieldSchema = fieldSchema as { 'x-deltafi-type'?: string; allOf?: Array<{ 'x-deltafi-type'?: string }> };

  // Check for x-deltafi-type at top level of field schema
  if (extFieldSchema?.['x-deltafi-type'] === 'EnvVar') return true;
  // Check for x-deltafi-type inside allOf (when field has its own description)
  if (extFieldSchema?.allOf?.[0]?.['x-deltafi-type'] === 'EnvVar') return true;

  return false;
};

export default function usePrimeVueJsonSchemaUIRenderers() {
  const rendererList = [
    ...vanillaRenderers,
    { tester: rankWith(5, isEnvVarControl), renderer: EnvVarRenderer },
    { tester: rankWith(3, isObjectControl), renderer: ObjectRenderer },
    { tester: rankWith(3, isStringControl), renderer: StringRenderer },
    { tester: rankWith(3, isIntegerControl), renderer: IntegerRenderer },
    { tester: rankWith(3, isBooleanControl), renderer: BooleanRenderer },
    { tester: rankWith(3, schemaTypeIs("array")), renderer: ArrayListRenderer },
    { tester: rankWith(3, schemaTypeIs("additionalPropertyString")), renderer: AdditionalPropertiesStringRenderer },
  ];

  const publishRenderList = [
    ...rendererList,
    { tester: rankWith(4, isObjectControl), renderer: PublishObjectRenderer },
    { tester: rankWith(4, schemaTypeIs("array")), renderer: PublishArrayListRenderer },
    { tester: rankWith(4, isStringControl), renderer: PublishStringRenderer },
  ]

  const subscribeRenderList = [
    ...rendererList,
    { tester: rankWith(4, isObjectControl), renderer: SubscribeObjectRenderer },
    { tester: rankWith(4, schemaTypeIs("array")), renderer: SubscribeArrayListRenderer },
    { tester: rankWith(4, isStringControl), renderer: SubscribeStringRenderer },
  ]

  return { rendererList, publishRenderList, subscribeRenderList };
}