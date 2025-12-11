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

// ABOUTME: Metrics for a single flow.
// ABOUTME: Contains files and bytes in/out for the flow.

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Metrics for a single flow: files and bytes in/out.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PerFlowMetrics(
        String flowType,
        String flowName,
        long filesIn,
        long filesOut,
        long bytesIn,
        long bytesOut
) {
    public static PerFlowMetrics empty(String flowType, String flowName) {
        return new PerFlowMetrics(flowType, flowName, 0, 0, 0, 0);
    }
}
