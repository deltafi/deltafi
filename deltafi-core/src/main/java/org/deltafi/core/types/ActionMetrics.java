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
package org.deltafi.core.types;

// ABOUTME: Metrics for a single action within a flow.
// ABOUTME: Contains execution count and total execution time.

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Metrics for a single action: execution count and timing.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ActionMetrics(
        String actionName,
        long executionCount,
        long executionTimeMs
) {
    public static ActionMetrics empty(String actionName) {
        return new ActionMetrics(actionName, 0, 0);
    }
}
