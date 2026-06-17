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

// ABOUTME: Canonical severity labels for security scan findings.
// ABOUTME: Normalizes raw values from grype, trivy, and other tools into a consistent display form.
package org.deltafi.gradle.security

class Severity {

    static final String CRITICAL = 'Critical'
    static final String HIGH = 'High'
    static final String MEDIUM = 'Medium'
    static final String LOW = 'Low'
    static final String NEGLIGIBLE = 'Negligible'
    static final String UNKNOWN = 'Unknown'

    /** Severity labels in descending order of severity, used for display ordering. */
    static final List<String> DISPLAY_ORDER = [CRITICAL, HIGH, MEDIUM, LOW, NEGLIGIBLE, UNKNOWN]

    /**
     * Fold a raw severity string into the canonical display form ("CRITICAL" → "Critical",
     * "high" → "High", null/empty → "Unknown"). Robust to either capitalization convention.
     */
    static String normalize(String raw) {
        if (raw == null || raw.isEmpty()) return UNKNOWN
        return raw.substring(0, 1).toUpperCase() + raw.substring(1).toLowerCase()
    }

    /** Return a sort index where Critical=0 and unknown labels sort last. */
    static int rank(String severity) {
        int idx = DISPLAY_ORDER.indexOf(severity)
        return idx == -1 ? 999 : idx
    }
}
