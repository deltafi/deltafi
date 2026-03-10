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

import lombok.Builder;

import java.util.Map;

/**
 * Client-side metadata for a resolved artifact version, returned alongside artifact bytes.
 *
 * <p>Obtained via {@link ArtifactHandle#getManifest()}.
 */
@Builder
public record ArtifactManifest(
        String version,
        String description,
        String contentType,
        String sha256,
        Map<String, String> labels) {

    /**
     * Returns {@code true} if this artifact was published with the label {@code default=true},
     * indicating it was seeded as a plugin default.
     */
    public boolean isDefault() {
        return labels != null && "true".equals(labels.get("default"));
    }
}
