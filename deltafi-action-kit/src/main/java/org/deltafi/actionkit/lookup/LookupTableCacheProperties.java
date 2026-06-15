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
package org.deltafi.actionkit.lookup;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the in-process lookup result cache.
 * Bound from the {@code deltafi.lookup-table.cache} prefix.
 */
@ConfigurationProperties("deltafi.lookup-table.cache")
@Data
public class LookupTableCacheProperties {

    /**
     * Maximum estimated total bytes to retain in the cache. {@code 0} means unlimited.
     * This is the primary control; when exceeded, the least-recently-accessed entries are evicted first.
     */
    private long maxBytes = 64L * 1024 * 1024; // 64 MB

    /**
     * Maximum number of cached results. {@code 0} means unlimited.
     * A secondary guard against unbounded map overhead from many tiny results.
     */
    private int maxEntries = 10_000;
}
