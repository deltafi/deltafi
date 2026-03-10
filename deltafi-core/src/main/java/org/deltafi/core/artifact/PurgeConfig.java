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
package org.deltafi.core.artifact;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Retention policy for artifact versions.
 * A version is eligible for purge when BOTH: it exceeds keepLatest count AND it is older than maxAgeMinutes.
 * The version pointed to by the 'latest' alias is never purged.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurgeConfig {

    /** Number of most-recent versions to always retain. */
    @Builder.Default
    private int keepLatest = 5;

    /** Maximum age of retained versions in minutes. Default: 30 days. */
    @Builder.Default
    private long maxAgeMinutes = 43200L;
}
