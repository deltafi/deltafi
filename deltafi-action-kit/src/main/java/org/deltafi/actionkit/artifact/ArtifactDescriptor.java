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
package org.deltafi.actionkit.artifact;

public record ArtifactDescriptor(
        String pluginName,
        String artifactName,
        String alias,
        ArtifactScope scope,
        // {@code false} (the default) means always fetch fresh; {@code true} means use shared cache.
        boolean cacheable,
        String defaultResource,
        // {@code true} resolves eagerly; {@code false} defers resolution to first handle access.
        boolean preload
) {

    /**
     * True if a default resource should be published if the artifact is missing
     * @return true if the defaultResource is set
     */
    boolean seedOnMissing() {
        return defaultResource != null && !defaultResource.isBlank();
    }
}
