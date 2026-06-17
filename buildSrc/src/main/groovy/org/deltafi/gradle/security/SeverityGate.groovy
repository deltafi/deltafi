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

// ABOUTME: Evaluates a per-scan severity threshold against a scan-result JSON map.
// ABOUTME: Returns which severities tripped the gate so the aggregator can fail the build and report.
package org.deltafi.gradle.security

class SeverityGate {

    static class Result {
        String tool
        String projectName
        String imageName
        String threshold
        boolean tripped = false
        Map<String, Integer> blockingCounts = [:]
        Map<String, Integer> evaluatedCounts = [:]
    }

    /**
     * Evaluate a scan result against a per-tool threshold.
     *
     * threshold: a Severity label (CRITICAL/HIGH/MEDIUM/LOW/NEGLIGIBLE) or "NONE" (never fail).
     *            null is treated as NONE.
     * ignoreUnfixed: when true, only findings with an available fix participate in gate evaluation.
     *
     * The scan-result JSON map is the one written by the grype/trivy tasks and contains
     * severityCounts (all findings) and fixableSeverityCounts (findings with a fix).
     */
    static Result evaluate(Map scanResult, String threshold, boolean ignoreUnfixed) {
        def result = new Result(
            tool: scanResult.tool,
            projectName: scanResult.projectName,
            imageName: scanResult.imageName,
            threshold: threshold ?: 'NONE'
        )

        Map<String, Integer> counts = ignoreUnfixed
            ? (scanResult.fixableSeverityCounts ?: [:])
            : (scanResult.severityCounts ?: [:])
        result.evaluatedCounts = counts

        if (result.threshold == 'NONE') {
            return result
        }

        int thresholdRank = Severity.rank(Severity.normalize(threshold))
        if (thresholdRank == 999) {
            throw new IllegalArgumentException("Unknown severity threshold: ${threshold}")
        }

        counts.each { String severity, Integer count ->
            if (Severity.rank(severity) <= thresholdRank && count > 0) {
                result.blockingCounts[severity] = count
                result.tripped = true
            }
        }
        return result
    }
}
