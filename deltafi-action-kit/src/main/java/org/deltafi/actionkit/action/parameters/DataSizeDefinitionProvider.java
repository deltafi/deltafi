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

import com.fasterxml.classmate.ResolvedType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.victools.jsonschema.generator.CustomDefinition;
import com.github.victools.jsonschema.generator.CustomDefinitionProviderV2;
import com.github.victools.jsonschema.generator.SchemaGenerationContext;
import com.github.victools.jsonschema.generator.SchemaKeyword;
import org.springframework.util.unit.DataSize;

public class DataSizeDefinitionProvider implements CustomDefinitionProviderV2 {

    @Override
    public CustomDefinition provideCustomSchemaDefinition(ResolvedType javaType, SchemaGenerationContext context) {
        if (javaType.getErasedType().equals(DataSize.class)) {
            ObjectNode customSchema = context.getGeneratorConfig().createObjectNode();
            customSchema.put(SchemaKeyword.TAG_TYPE.forVersion(context.getGeneratorConfig().getSchemaVersion()), "string");
            customSchema.put(SchemaKeyword.TAG_PATTERN.forVersion(context.getGeneratorConfig().getSchemaVersion()), "^[0-9]+(B|KB|MB|GB|TB)$");
            return new CustomDefinition(customSchema, true);
        }

        return null;
    }
}

