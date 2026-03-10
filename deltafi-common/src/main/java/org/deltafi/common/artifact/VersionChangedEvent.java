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
package org.deltafi.common.artifact;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Instant;

/**
 * Published to the Valkey pub/sub channel
 * {@link ArtifactRegistryConstants#VERSION_CHANGED_CHANNEL} when an artifact's alias is updated.
 * Emitted on initial publish (with makeLatest=true) and on rollback.
 */
public record VersionChangedEvent(
        /** {@code "plugins/{pluginName}"} for plugin-owned artifacts, {@code "shared"} for shared. */
        String prefix,
        String artifactName,
        /** The alias that changed, e.g. {@code "active"}. */
        String alias,
        /** Previous version resolved by the alias; {@code null} on first publish. */
        String previousVersion,
        String newVersion,
        Instant timestamp
) {
    @JsonIgnore
    public boolean isShared() {
        return ArtifactRegistryConstants.SHARED_OWNER.equals(prefix);
    }
}
