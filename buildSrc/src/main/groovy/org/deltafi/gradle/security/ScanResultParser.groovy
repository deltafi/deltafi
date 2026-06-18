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

// ABOUTME: Converts raw grype/trivy JSON output into our normalized scan-result schema.
// ABOUTME: One source of truth for the schema, shared by per-subproject and root reports.
package org.deltafi.gradle.security

class ScanResultParser {

    /** Normalize a raw grype JSON document (matches[]) into our scan-result schema. */
    static Map grypeImage(Map raw, String projectName, String imageName, boolean referenced = false) {
        def matches = raw.matches ?: []
        def severityCounts = [:]
        def fixableSeverityCounts = [:]
        def findings = []
        int fixableCount = 0
        matches.each { Map m ->
            def severity = Severity.normalize(m.vulnerability?.severity)
            severityCounts[severity] = (severityCounts[severity] ?: 0) + 1
            def fixVersions = m.vulnerability?.fix?.versions
            def fixed = fixVersions && !fixVersions.isEmpty()
            if (fixed) {
                fixableCount++
                fixableSeverityCounts[severity] = (fixableSeverityCounts[severity] ?: 0) + 1
            }
            findings.add([
                id        : m.vulnerability?.id ?: 'Unknown',
                severity  : severity,
                'package' : m.artifact?.name ?: 'Unknown',
                version   : m.artifact?.version ?: 'Unknown',
                fixVersion: fixed ? fixVersions[0] : null
            ])
        }
        def result = [
            projectName          : projectName,
            imageName            : imageName,
            tool                 : 'grype',
            totalVulnerabilities : matches.size(),
            severityCounts       : severityCounts,
            fixableSeverityCounts: fixableSeverityCounts,
            fixableCount         : fixableCount,
            findings             : findings
        ]
        if (referenced) result.referenced = true
        result
    }

    /** Normalize a raw trivy image JSON document (Results[].Vulnerabilities[]) into our schema. */
    static Map trivyImage(Map raw, String projectName, String imageName, boolean referenced = false) {
        def vulns = []
        raw.Results?.each { r -> if (r.Vulnerabilities) vulns.addAll(r.Vulnerabilities) }
        def severityCounts = [:]
        def fixableSeverityCounts = [:]
        def findings = []
        int fixableCount = 0
        vulns.each { Map v ->
            def severity = Severity.normalize(v.Severity)
            severityCounts[severity] = (severityCounts[severity] ?: 0) + 1
            def fixed = v.FixedVersion && v.FixedVersion != ''
            if (fixed) {
                fixableCount++
                fixableSeverityCounts[severity] = (fixableSeverityCounts[severity] ?: 0) + 1
            }
            findings.add([
                id        : v.VulnerabilityID ?: 'Unknown',
                severity  : severity,
                'package' : v.PkgName ?: 'Unknown',
                version   : v.InstalledVersion ?: 'Unknown',
                fixVersion: fixed ? v.FixedVersion : null
            ])
        }
        def result = [
            projectName          : projectName,
            imageName            : imageName,
            tool                 : 'trivy',
            totalVulnerabilities : vulns.size(),
            severityCounts       : severityCounts,
            fixableSeverityCounts: fixableSeverityCounts,
            fixableCount         : fixableCount,
            findings             : findings
        ]
        if (referenced) result.referenced = true
        result
    }

    /** Normalize a raw trivy fs JSON document into our repo-wide schema (source dependencies). */
    static Map trivyRepo(String scanType, Map raw) {
        def severityCounts = [:]
        def fixableSeverityCounts = [:]
        def findings = []
        int total = 0
        raw.Results?.each { r ->
            r.Vulnerabilities?.each { Map v ->
                def s = Severity.normalize(v.Severity)
                severityCounts[s] = (severityCounts[s] ?: 0) + 1
                def fixed = v.FixedVersion && v.FixedVersion != ''
                if (fixed) fixableSeverityCounts[s] = (fixableSeverityCounts[s] ?: 0) + 1
                findings.add([
                    id        : v.VulnerabilityID ?: 'Unknown',
                    severity  : s,
                    'package' : v.PkgName ?: 'Unknown',
                    version   : v.InstalledVersion ?: 'Unknown',
                    fixVersion: fixed ? v.FixedVersion : null
                ])
                total++
            }
        }
        return [
            projectName          : scanType,
            imageName            : '',
            tool                 : "trivy-${scanType}".toString(),
            totalVulnerabilities : total,
            severityCounts       : severityCounts,
            fixableSeverityCounts: fixableSeverityCounts,
            fixableCount         : fixableSeverityCounts.values().sum() ?: 0,
            findings             : findings
        ]
    }
}
