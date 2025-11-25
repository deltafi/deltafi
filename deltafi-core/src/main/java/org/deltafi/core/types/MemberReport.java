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
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Combined status report for leader-member monitoring.
 * Returns health status, error count, queue counts, system metrics, and version in a single response.
 * Lenient to version differences - ignores unknown fields and handles missing fields.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MemberReport(
        ObjectNode status,
        Long errorCount,
        Long inFlightCount,
        Long warmQueuedCount,
        Long coldQueuedCount,
        Long pausedCount,
        Double cpuUsage,
        Double memoryUsage,
        Double diskUsage,
        String version
) {}
