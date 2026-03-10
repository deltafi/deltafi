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

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the in-process artifact cache.
 * Bound from the {@code deltafi.artifact-registry.cache} prefix.
 */
@ConfigurationProperties("deltafi.artifact-registry.cache")
@Data
class ArtifactRegistryCacheProperties {

    /**
     * Maximum number of artifacts to keep in the cache. {@code 0} means unlimited.
     * When exceeded, the least-recently-fetched non-pinned entries are evicted first.
     */
    private int maxEntries = 100;

    /**
     * Maximum total bytes to keep in the cache. {@code 0} means unlimited.
     * When exceeded, the least-recently-fetched non-pinned entries are evicted first.
     */
    private long maxBytes = 256L * 1024 * 1024; // 256 MB

    /**
     * How often (in seconds) the cache polls the registry to check whether any alias-based
     * entries have drifted from what is cached — a safety net for missed notifications.
     * {@code 0} disables polling entirely. Defaults to 300 (5 minutes).
     */
    private int pollIntervalSeconds = 300;
}
