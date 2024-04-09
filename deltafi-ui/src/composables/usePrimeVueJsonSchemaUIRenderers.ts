/*
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
*/

import ArrayListRenderer from '../components/jsonSchemaRenderers/arrayRender/ArrayListRenderer.vue';
import BooleanRenderer from '../components/jsonSchemaRenderers/BooleanRenderer.vue';
import IntegerRenderer from '../components/jsonSchemaRenderers/IntegerRenderer.vue';
import ObjectRenderer from "../components/jsonSchemaRenderers/ObjectRenderer.vue";
import StringRenderer from '../components/jsonSchemaRenderers/StringRenderer.vue';
import AdditionalPropertiesStringRenderer from '../components/jsonSchemaRenderers/AdditionalPropertiesStringRenderer.vue';
import { vanillaRenderers } from "@jsonforms/vue-vanilla";
import { rankWith, isObjectControl, isBooleanControl, isStringControl, isIntegerControl, schemaTypeIs } from "@jsonforms/core";

export default function usePrimeVueJsonSchemaUIRenderers() {
  const rendererList = [
    ...vanillaRenderers, 
    { tester: rankWith(3, isObjectControl), renderer: ObjectRenderer },
    { tester: rankWith(3, isStringControl), renderer: StringRenderer },
    { tester: rankWith(3, isIntegerControl), renderer: IntegerRenderer },
    { tester: rankWith(3, isBooleanControl), renderer: BooleanRenderer },
    { tester: rankWith(3, schemaTypeIs("array")), renderer: ArrayListRenderer},
    { tester: rankWith(3, schemaTypeIs("additionalPropertyString")), renderer: AdditionalPropertiesStringRenderer},
  ];

  return { rendererList };
}