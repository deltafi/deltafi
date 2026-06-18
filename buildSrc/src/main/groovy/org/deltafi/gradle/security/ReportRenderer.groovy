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

// ABOUTME: Pure markdown renderers for per-image, aggregated, and consolidated security reports.
// ABOUTME: Inputs are scan-result Maps; outputs are markdown strings — no I/O, no Gradle types.
package org.deltafi.gradle.security

class ReportRenderer {

    /** Current UTC time in ISO-8601 offset date-time format. Used as the `Generated:` stamp on every report. */
    static String isoNow() {
        java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC)
            .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }

    /** Per-image markdown for grype or trivy results. */
    static String renderImageReport(Map result, String generatedAt) {
        def sb = new StringBuilder()
        def tool = result.tool ?: 'tool'
        sb << "# ${result.projectName} — ${tool}\n\n"
        sb << "Image: `${result.imageName}`\n"
        sb << "Scanned: ${generatedAt}\n\n"
        sb << "Total: ${result.totalVulnerabilities ?: 0} vulnerabilities (${result.fixableCount ?: 0} fixable)\n\n"

        sb << "## By severity\n\n"
        sb << severityTable(result.severityCounts ?: [:], result.fixableSeverityCounts ?: [:])

        sb << "\n## Findings\n\n"
        sb << cveTable(result.findings ?: [])
        sb.toString()
    }

    /** Markdown for repo-wide scans (trivy fs / Checkov / trivy secrets). */
    static String renderRepoScanReport(Map result, String generatedAt) {
        def sb = new StringBuilder()
        def tool = result.tool ?: 'tool'
        sb << "# ${tool}\n\n"
        sb << "Scope: ${result.projectName}\n"
        sb << "Scanned: ${generatedAt}\n\n"
        sb << "Total: ${result.totalVulnerabilities ?: 0} findings\n\n"

        sb << "## By severity\n\n"
        sb << severityTable(result.severityCounts ?: [:], result.fixableSeverityCounts ?: [:])

        sb << "\n## Findings\n\n"
        if (result.findings && result.findings.any { it.containsKey('file') }) {
            sb << locationTable(result.findings)
        } else {
            sb << cveTable(result.findings ?: [])
        }
        sb.toString()
    }

    /**
     * Aggregated root grype.md / trivy.md: at-a-glance table + per-image inlined sections.
     * Results are grouped subprojects-first (alphabetical), then referenced images (alphabetical).
     * Per-image sections use H3 headers with no deeper sub-headers, so embedding in the consolidated
     * security.md (which shifts +1) keeps the total depth at H4 or shallower.
     */
    static String renderAggregatedImageReport(String toolDisplayName, List<Map> results, List<Map> gateRecords, String generatedAt) {
        def sb = new StringBuilder()
        sb << "# ${toolDisplayName} Image Scan\n\n"
        sb << "Generated: ${generatedAt}\n\n"

        sb << "## At a glance\n\n"
        sb << atGlanceImageTable(results, gateRecords)

        sb << "\n## Per-image findings\n\n"
        groupedImages(results).each { r ->
            sb << "### ${imageLabel(r)}\n\n"
            sb << "Total: ${r.totalVulnerabilities ?: 0} (${r.fixableCount ?: 0} fixable). "
            sb << severityInline(r.severityCounts ?: [:]) << "\n\n"
            sb << cveTable(r.findings ?: [])
            sb << "\n"
        }
        sb.toString()
    }

    /**
     * Top-level security.md: a separate H3 section + table per (tool, scope), then a collapsible
     * embedded report per tool. Embedded bodies are stripped of their leading `# Title` + `Generated:`
     * lines and demoted by one heading level so that nothing in the consolidated document goes deeper
     * than H4. `toolSummaries` is a list of [tool, scope, rows] (rows: [label, threshold, total,
     * blocking, tripped]). Image scans contribute one row per image; repo-wide scans contribute a
     * single summary row.
     */
    static String renderConsolidatedReport(List<Map> toolSummaries, Map<String,String> embeddedReports, String generatedAt) {
        def sb = new StringBuilder()
        sb << "# Security Report\n\n"
        sb << "Generated: ${generatedAt}\n\n"

        sb << "## At a glance\n\n"
        toolSummaries.each { Map ts ->
            def isImageScope = ts.scope == 'images'
            def labelHeader = isImageScope ? 'Image' : 'Scope'
            sb << "### ${ts.tool} — ${ts.scope}\n\n"
            sb << "| ${labelHeader} | Threshold | Total | Blocking | Status |\n"
            sb << "|---|---|---|---|---|\n"
            (ts.rows ?: []).each { Map r ->
                def status = r.tripped ? '✗' : '✓'
                sb << "| ${r.label} | ${r.threshold} | ${r.total} | ${r.blocking} | ${status} |\n"
            }
            sb << "\n"
        }

        embeddedReports.each { title, body ->
            sb << "<details>\n<summary>${title}</summary>\n\n"
            sb << demoteForEmbedding(body)
            sb << "\n</details>\n\n"
        }
        sb.toString()
    }

    /**
     * Prepare a standalone report's markdown for embedding in the consolidated document. Strips the
     * leading `# Title` and `Generated:` lines (the <details><summary> already provides context) and
     * shifts every other heading down by one level. Caps any post-shift heading at H6.
     */
    private static String demoteForEmbedding(String body) {
        def stripped = body
            .replaceAll(/(?ms)\A# [^\n]*\n+/, '')
            .replaceAll(/(?m)^Generated: [^\n]*\n+/, '')
        stripped.replaceAll(/(?m)^(#{1,6})/) { match, hashes ->
            def next = (hashes.length() + 1)
            if (next > 6) next = 6
            ('#' * next)
        }
    }

    // ---- helpers ----

    private static String severityTable(Map counts, Map fixableCounts) {
        if (!counts) return "_No findings._\n"
        def sb = new StringBuilder()
        sb << "| Severity | Count | Fixable |\n"
        sb << "|----------|-------|--------:|\n"
        orderedSeverities(counts.keySet()).each { sev ->
            sb << "| ${sev} | ${counts[sev]} | ${fixableCounts[sev] ?: 0} |\n"
        }
        sb.toString()
    }

    private static String cveTable(List findings) {
        if (!findings) return "_No findings._\n"
        def sb = new StringBuilder()
        sb << "| Severity | ID | Package | Version | Fix |\n"
        sb << "|----------|----|---------|---------|-----|\n"
        sortFindings(findings).each { f ->
            def fix = f.fixVersion ? "`${f.fixVersion}`" : '—'
            sb << "| ${f.severity} | ${f.id} | `${f.package}` | `${f.version}` | ${fix} |\n"
        }
        sb.toString()
    }

    private static String locationTable(List findings) {
        if (!findings) return "_No findings._\n"
        def sb = new StringBuilder()
        sb << "| Severity | ID | File:Line | Title/Resource |\n"
        sb << "|----------|----|-----------|----------------|\n"
        sortFindings(findings).each { f ->
            def loc = f.line ? "`${f.file}:${f.line}`" : "`${f.file ?: '—'}`"
            def extra = f.resource ?: f.title ?: ''
            sb << "| ${f.severity} | ${f.id} | ${loc} | ${extra} |\n"
        }
        sb.toString()
    }

    private static String atGlanceImageTable(List<Map> results, List<Map> gateRecords) {
        def sb = new StringBuilder()
        def severityCols = Severity.DISPLAY_ORDER
        sb << "| Image | Threshold | Total | ${severityCols.join(' | ')} | Fixable |\n"
        sb << "|-------|-----------|-------|${severityCols.collect { '---' }.join('|')}|---------|\n"
        groupedImages(results).each { r ->
            def gate = gateRecords.find { it.projectName == r.projectName && it.tool == r.tool }
            def threshold = gate?.threshold ?: '—'
            def counts = r.severityCounts ?: [:]
            def cells = severityCols.collect { sev -> counts[sev] ?: 0 }
            sb << "| ${imageLabel(r)} | ${threshold} | ${r.totalVulnerabilities ?: 0} | ${cells.join(' | ')} | ${r.fixableCount ?: 0} |\n"
        }
        sb.toString()
    }

    private static String imageLabel(Map result) {
        def base = result.imageName ?: result.projectName
        isReferenced(result) ? "${base} (referenced)" : base
    }

    private static String imageSortKey(Map result) {
        (result.imageName ?: result.projectName ?: '').toString()
    }

    /** Subprojects first (alphabetical by image name), then referenced images (alphabetical). */
    static List<Map> groupedImages(List<Map> results) {
        def (referenced, owned) = results.split { isReferenced(it) }
        def ownedSorted      = owned.toList().sort { a, b -> imageSortKey(a) <=> imageSortKey(b) }
        def referencedSorted = referenced.toList().sort { a, b -> imageSortKey(a) <=> imageSortKey(b) }
        ownedSorted + referencedSorted
    }

    private static boolean isReferenced(Map result) {
        result.containsKey('referenced') && result.referenced
    }

    /** Inline-prose severity summary used under per-image headers in the aggregated report. */
    private static String severityInline(Map counts) {
        if (!counts) return ''
        def parts = orderedSeverities(counts.keySet()).collect { sev -> "${sev}: ${counts[sev]}" }
        "By severity — ${parts.join(', ')}."
    }

    private static List orderedSeverities(Set keys) {
        def known = Severity.DISPLAY_ORDER.findAll { keys.contains(it) }
        def extras = (keys - known).toList().sort()
        known + extras
    }

    private static List sortFindings(List findings) {
        findings.toList().sort { a, b ->
            int r = Severity.rank(a.severity ?: 'Unknown') <=> Severity.rank(b.severity ?: 'Unknown')
            if (r != 0) return r
            return (a.id ?: '') <=> (b.id ?: '')
        }
    }
}
