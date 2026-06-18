package org.deltafi.gradle.security

import spock.lang.Specification

class ReportRendererSpec extends Specification {

    def "per-image grype report shows project, image, severity table, and findings table"() {
        given:
        def result = [
            projectName: 'deltafi-core',
            imageName: 'deltafi/deltafi-core:1.0.0',
            tool: 'grype',
            totalVulnerabilities: 2,
            severityCounts: [Critical: 0, High: 1, Medium: 1],
            fixableSeverityCounts: [High: 1, Medium: 0],
            fixableCount: 1,
            findings: [
                [id: 'CVE-2026-1', severity: 'High',   'package': 'foo', version: '1.0', fixVersion: '1.1'],
                [id: 'CVE-2026-2', severity: 'Medium', 'package': 'bar', version: '2.0', fixVersion: null]
            ]
        ]

        when:
        def md = ReportRenderer.renderImageReport(result, '2026-06-18T10:00:00Z')

        then:
        md.contains('# deltafi-core — grype')
        md.contains('Image: `deltafi/deltafi-core:1.0.0`')
        md.contains('Total: 2 vulnerabilities (1 fixable)')
        md.contains('| High')
        md.contains('| CVE-2026-1 | `foo` | `1.0` | `1.1` |')
        md.contains('| CVE-2026-2 | `bar` | `2.0` | — |')
        md.indexOf('CVE-2026-1') < md.indexOf('CVE-2026-2')
    }

    def "per-image report with zero findings emits placeholder"() {
        given:
        def result = [
            projectName: 'clean-svc',
            imageName: 'deltafi/clean-svc:1.0.0',
            tool: 'trivy',
            totalVulnerabilities: 0,
            severityCounts: [:],
            fixableSeverityCounts: [:],
            fixableCount: 0,
            findings: []
        ]

        expect:
        ReportRenderer.renderImageReport(result, '2026-06-18T10:00:00Z')
            .contains('_No findings._')
    }

    def "aggregated image report groups subprojects first then referenced, alphabetical within each"() {
        given:
        def results = [
            [projectName: 'b', imageName: 'deltafi/b:1', tool: 'grype',
             totalVulnerabilities: 0, severityCounts: [:], fixableSeverityCounts: [:],
             fixableCount: 0, findings: []],
            [projectName: 'deltafi_grafana_1', imageName: 'deltafi/grafana:1', tool: 'grype',
             referenced: true,
             totalVulnerabilities: 2, severityCounts: [High: 2], fixableSeverityCounts: [:],
             fixableCount: 0, findings: [[id: 'CVE-G', severity: 'High', 'package': 'q', version: 'v', fixVersion: null]]],
            [projectName: 'a', imageName: 'deltafi/a:1', tool: 'grype',
             totalVulnerabilities: 1, severityCounts: [High: 1], fixableSeverityCounts: [:],
             fixableCount: 0, findings: [[id: 'CVE-A', severity: 'High', 'package': 'p', version: 'v', fixVersion: null]]]
        ]
        def gates = [
            [projectName: 'a', tool: 'grype', threshold: 'Critical', tripped: false, blockingCounts: [:]],
            [projectName: 'b', tool: 'grype', threshold: 'Critical', tripped: false, blockingCounts: [:]],
            [projectName: 'deltafi_grafana_1', tool: 'grype', threshold: 'Critical', tripped: false, blockingCounts: [:]]
        ]

        when:
        def md = ReportRenderer.renderAggregatedImageReport('Grype', results, gates, '2026-06-18T10:00:00Z')

        then:
        md.startsWith('# Grype Image Scan')
        md.contains('## At a glance')
        md.contains('## Per-image findings')
        // grouped: subprojects a, b alphabetical, then referenced grafana
        md.indexOf('| deltafi/a:1 |') < md.indexOf('| deltafi/b:1 |')
        md.indexOf('| deltafi/b:1 |') < md.indexOf('| deltafi/grafana:1 (referenced) |')
        md.indexOf('### deltafi/a:1') < md.indexOf('### deltafi/b:1')
        md.indexOf('### deltafi/b:1') < md.indexOf('### deltafi/grafana:1 (referenced)')
        // depth cap: per-image content has no level >= 4 in the aggregated report
        !md.split('\n').any { it.startsWith('#### ') }
    }

    def "consolidated report demotes embedded body and strips title/Generated lines"() {
        given:
        def summaries = [
            [tool: 'Grype', scope: 'images', rows: [[label: 'x', threshold: 'Critical', total: 1, blocking: 0, tripped: false]]]
        ]
        def embedded = [
            'Grype (images)': '# Grype Image Scan\n\nGenerated: 2026-06-18T10:00:00Z\n\n## At a glance\n\n| col |\n| --- |\n\n## Per-image findings\n\n### project\n\nbody\n'
        ]

        when:
        def md = ReportRenderer.renderConsolidatedReport(summaries, embedded, '2026-06-18T10:00:00Z')

        then:
        // title line stripped (only the top-level one)
        md.findAll(/(?m)^# /).size() == 1
        md.contains('# Security Report')
        // Generated: only once (top level), embedded one stripped
        md.findAll(/(?m)^Generated:/).size() == 1
        // demoted by one: ## becomes ###, ### becomes ####, never deeper than ####
        md.contains('### At a glance')           // demoted from ##
        md.contains('### Per-image findings')    // demoted from ##
        md.contains('#### project')              // demoted from ###
        !md.split('\n').any { it.startsWith('##### ') }
    }

    def "consolidated report shows one at-a-glance table per tool"() {
        given:
        def summaries = [
            [tool: 'Grype', scope: 'images', rows: [
                [label: 'deltafi/core:1', threshold: 'Critical', total: 20, blocking: 0, tripped: false],
                [label: 'deltafi/x:2 (referenced)', threshold: 'Critical', total: 12, blocking: 0, tripped: false]
            ]],
            [tool: 'Trivy', scope: 'images', rows: [
                [label: 'deltafi/core:1', threshold: 'High', total: 32, blocking: 2, tripped: true]
            ]],
            [tool: 'Trivy', scope: 'source', rows: [
                [label: 'source dependencies', threshold: 'High', total: 8, blocking: 0, tripped: false]
            ]],
            [tool: 'Checkov', scope: 'config', rows: [
                [label: 'Dockerfiles + manifests', threshold: 'High', total: 14, blocking: 3, tripped: true]
            ]],
            [tool: 'Trivy', scope: 'secrets', rows: [
                [label: 'source secrets', threshold: 'Low', total: 0, blocking: 0, tripped: false]
            ]]
        ]
        def embedded = [
            'Grype (images)':   '## (mock grype report)',
            'Trivy (images)':   '## (mock trivy report)',
            'Trivy (source)':   '## (mock source report)',
            'Checkov (config)': '## (mock config report)',
            'Trivy (secrets)':  '## (mock secrets report)'
        ]

        when:
        def md = ReportRenderer.renderConsolidatedReport(summaries, embedded, '2026-06-18T10:00:00Z')

        then:
        md.startsWith('# Security Report')
        md.contains('### Grype — images')
        md.contains('### Trivy — images')
        md.contains('### Trivy — source')
        md.contains('### Checkov — config')
        md.contains('### Trivy — secrets')
        md.contains('| deltafi/core:1 | Critical | 20 | 0 | ✓ |')
        md.contains('| deltafi/x:2 (referenced) | Critical | 12 | 0 | ✓ |')
        md.contains('| deltafi/core:1 | High | 32 | 2 | ✗ |')
        md.contains('| source dependencies | High | 8 | 0 | ✓ |')
        md.contains('<details>')
        md.contains('<summary>Trivy (images)</summary>')
    }
}
