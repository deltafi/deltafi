/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.deltafi.actionkit.action.parameters;

// ABOUTME: Custom JSON Schema definition provider for EnvVar type.
// ABOUTME: Adds x-deltafi-type marker for UI detection.

import com.fasterxml.classmate.ResolvedType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.victools.jsonschema.generator.CustomDefinition;
import com.github.victools.jsonschema.generator.CustomDefinitionProviderV2;
import com.github.victools.jsonschema.generator.SchemaGenerationContext;
import com.github.victools.jsonschema.generator.SchemaKeyword;

/**
 * Provides custom JSON Schema definition for the EnvVar type.
 * <p>
 * Generates a schema with:
 * - type: string
 * - x-deltafi-type: EnvVar (for UI detection)
 * - pattern: UPPER_SNAKE_CASE validation
 * <p>
 * EnvVar serializes as a simple string (the env var name), not as an object.
 */
public class EnvVarDefinitionProvider implements CustomDefinitionProviderV2 {

    @Override
    public CustomDefinition provideCustomSchemaDefinition(ResolvedType javaType, SchemaGenerationContext context) {
        if (javaType.getErasedType().equals(EnvVar.class)) {
            ObjectNode customSchema = context.getGeneratorConfig().createObjectNode();

            // Mark type as EnvVar for UI detection
            customSchema.put("x-deltafi-type", "EnvVar");

            // Set as string type (EnvVar serializes as simple string via @JsonValue)
            customSchema.put(SchemaKeyword.TAG_TYPE.forVersion(context.getGeneratorConfig().getSchemaVersion()), "string");
            customSchema.put(SchemaKeyword.TAG_PATTERN.forVersion(context.getGeneratorConfig().getSchemaVersion()),
                    "^[A-Z][A-Z0-9_]*$");
            customSchema.put(SchemaKeyword.TAG_DESCRIPTION.forVersion(context.getGeneratorConfig().getSchemaVersion()),
                    "The secret value is resolved at runtime from this environment variable.");

            return new CustomDefinition(customSchema, true);
        }

        return null;
    }
}
