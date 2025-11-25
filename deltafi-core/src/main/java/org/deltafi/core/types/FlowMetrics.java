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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * Aggregated flow metrics for leader-member monitoring.
 * Contains totals and breakdowns for ingress, egress, storage, and deleted bytes.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FlowMetrics(
        Map<String, Long> ingressByDataSource,
        Map<String, Long> egressByDataSink,
        Map<String, Long> storageByService,
        Map<String, Long> deletedByPolicy
) {
    public static FlowMetrics empty() {
        return new FlowMetrics(Map.of(), Map.of(), Map.of(), Map.of());
    }
}
