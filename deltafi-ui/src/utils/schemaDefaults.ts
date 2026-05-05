/*
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
*/

type SchemaLike = { properties?: Record<string, any>; default?: unknown };

// AJV's useDefaults won't create a missing parent object to populate its
// children's defaults, so we pre-seed nested defaults from the schema.
export const extractSchemaDefaults = (schema: SchemaLike | null | undefined): Record<string, unknown> => {
  const defaults: Record<string, unknown> = {};
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
