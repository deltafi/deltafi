/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.plugin.generator;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.util.HashSet;
import java.util.Set;

@Data
public class PluginGeneratorInput {
    private String groupId;
    private String artifactId;
    private String description;
    private PluginLanguage pluginLanguage;
    private Set<ActionGeneratorInput> actions = new HashSet<>();

    public void validate() {
        requireNonBlank(groupId, "The groupId must be set");
        requireNonBlank(artifactId, "The artifactId must be set");
        requireNonBlank(description, "The description must be set");

        if (pluginLanguage == null) {
            throw new IllegalArgumentException("The pluginLanguage must be set");
        }

        actions.forEach(ActionGeneratorInput::validate);
    }

    void requireNonBlank(String field, String message) {
        if (StringUtils.isBlank(field)) {
            throw new IllegalArgumentException(message);
        }
    }
}
