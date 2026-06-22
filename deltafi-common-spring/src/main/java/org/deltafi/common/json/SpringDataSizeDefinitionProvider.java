/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
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
// ABOUTME: Backwards-compatibility JSON schema provider for Spring DataSize parameters.
// ABOUTME: Deprecated bridge kept until 3.0 so existing plugin parameter fields still get a schema.
package org.deltafi.common.json;

import com.fasterxml.classmate.ResolvedType;
import tools.jackson.databind.node.ObjectNode;
import com.github.victools.jsonschema.generator.*;
import org.deltafi.common.constant.DeprecatedSince;
import org.springframework.util.unit.DataSize;

/**
 * Provides a JSON schema definition for {@link org.springframework.util.unit.DataSize} fields as a
 * pattern-validated string, mirroring {@link DataSizeDefinitionProvider} for the pure-Java
 * {@code org.deltafi.common.util.DataSize}.
 *
 * @deprecated Kept only so existing plugins whose action parameter fields are typed
 * {@code org.springframework.util.unit.DataSize} continue to get a schema. Migrate those fields to
 * {@code org.deltafi.common.util.DataSize}. This bridge will be removed in 3.0.
 */
@Deprecated(since = DeprecatedSince.VERSION_2_55_1, forRemoval = true)
public class SpringDataSizeDefinitionProvider implements CustomDefinitionProviderV2 {

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
