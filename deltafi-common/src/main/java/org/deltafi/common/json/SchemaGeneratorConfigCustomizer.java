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
// ABOUTME: Optional interface for customizing the JSON schema generator configuration.
// ABOUTME: Implement as a Spring bean to add custom schema rules for action parameters.
package org.deltafi.common.json;

import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;

/**
 * Optional interface for customizing the JSON schema generator
 */
public interface SchemaGeneratorConfigCustomizer {
    /**
     * Implement this method to customize the SchemaGeneratorConfig that builds the SchemaGenerator.
     * @param schemaGeneratorConfig config that will be modified
     */
    void customize(SchemaGeneratorConfigBuilder schemaGeneratorConfig);
}
