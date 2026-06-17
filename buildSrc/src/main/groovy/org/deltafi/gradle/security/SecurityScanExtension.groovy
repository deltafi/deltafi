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

// ABOUTME: DSL for the root-level securityScan task — per-tool severity gates, suppression dir, report output.
// ABOUTME: Configures per-tool failOn thresholds, suppressionsDir, sbomDir, reportDir, and skip patterns.
package org.deltafi.gradle.security

import org.gradle.api.Action
import org.gradle.api.Project

class SecurityScanExtension {

    /**
     * Per-tool severity gates. Each value is the lowest severity that fails the build.
     * Accepts any Severity.DISPLAY_ORDER label, plus "NONE" (never fail).
     */
    static class FailOnConfig {
        String trivyImage  = Severity.HIGH
        String grypeImage  = Severity.CRITICAL
        String trivyFs     = Severity.HIGH
        String checkov     = Severity.HIGH
        String trivySecret = Severity.LOW
    }

    final FailOnConfig failOn = new FailOnConfig()

    /**
     * When true, vulnerabilities without an available fix are excluded from gate evaluation
     * (they still appear in the report).
     */
    boolean ignoreUnfixed = true

    /** Where suppression files (.trivyignore, grype.yaml) live. */
    File suppressionsDir

    /** Where the aggregated summary + raw scan outputs are written. */
    File reportDir

    /**
     * Container images used when a scan tool isn't installed natively. Override an entry to pin
     * to a specific version or point at a mirrored image. Keys: 'grype', 'trivy', 'syft', 'checkov'.
     *
     * Example (in a downstream plugin's build.gradle):
     *
     *     securityScan {
     *         toolImages.grype = 'deltafi/grype:latest'
     *         toolImages.trivy = 'deltafi/trivy:latest'
     *     }
     */
    Map<String, String> toolImages = [
        grype:   'anchore/grype:latest',
        trivy:   'aquasec/trivy:latest',
        syft:    'anchore/syft:latest',
        checkov: 'bridgecrew/checkov:latest',
    ]

    /** When true, every scan also emits a SARIF file alongside its JSON output. Roughly doubles scan time. */
    boolean sarif = false

    /** Where per-image SBOMs (CycloneDX JSON) are written by the syft sbom task. */
    File sbomDir

    /** Root path for source dependency scanning (trivy fs). */
    File sourcePath

    /** Paths to scan for IaC/Dockerfile misconfig (Checkov). */
    List<File> configPaths = []

    /**
     * Substring patterns passed to Checkov as `--skip-path` (one flag per entry). Excludes files
     * that look like Dockerfiles by name but aren't deployable (template scaffolding, Go source
     * code that builds Dockerfiles), plus build artefacts and vendored module caches.
     *
     * Checkov's `--skip-path` flag accepts simple substrings (which are reliable) and regex
     * (which empirically breaks on parenthesised alternation), so we stick to substrings.
     */
    List<String> configSkipPaths = [
        'dockerfile.go',         // TUI plugin generator Go source — name matches Checkov's Dockerfile detector
        '.tmpl',                 // Go-template files that emit Dockerfiles
        'internal/generator',    // TUI plugin-scaffolding tree
        'build/',                // any Gradle build/ tree (staged Dockerfile prep, build/resources/test fixtures, javadoc, etc.)
        'bin/test',              // test fixtures (Gradle Eclipse output) — not deployable
        'src/test',              // test fixtures + resources (Java/Gradle convention) — not deployable
        'node_modules',          // npm vendored deps
        '.go/pkg/mod',           // Go module cache (third-party test fixtures)
        '.gradle',               // Gradle metadata
    ]

    /** Root path for secret scanning. */
    File secretsPath

    /**
     * Glob patterns excluded from source and secret scans. Use `**\/<dir>` to match a directory
     * at any depth — bare names only match a top-level directory, which is the wrong default for
     * a multi-module Gradle repo where every subproject has its own `build/`.
     */
    List<String> skipDirs = [
        '**/build',
        '**/.gradle',
        '**/node_modules',
        '**/m2',
        '**/.git',
        '**/dist',
        '**/out',
        '**/.vitepress',
        '**/.go/pkg/mod',     // Go module cache — third-party test fixtures show up as false-positive secrets
    ]

    /**
     * Container images that aren't built by this repo but are referenced at runtime — typically
     * third-party `deltafi/*` images pulled from the registry. scanReferencedImages walks this list
     * and runs trivy on each. The default is auto-populated by parsing the TUI orchestrator (when
     * present); downstream plugins can either set their own list or leave it empty.
     */
    List<String> referencedImages = []

    SecurityScanExtension(Project project) {
        suppressionsDir = project.file('.security')
        reportDir = project.layout.buildDirectory.dir('reports/security').get().asFile
        sbomDir = project.layout.buildDirectory.dir('sboms').get().asFile
        sourcePath = project.projectDir
        secretsPath = project.projectDir
        configPaths = [project.projectDir]

        // Auto-populate referenced images from the TUI orchestrator source when present. Lines
        // matching the `<Name>Container string `default:"<image>"`` pattern are pulled in. Images
        // named *Java*Container (IDE / devcontainer scaffolding) are excluded — they're not
        // deployed at runtime.
        def orchestratorGo = project.file('tui/internal/orchestration/orchestrator.go')
        if (orchestratorGo.isFile()) {
            def re = /^\s*(\w+)Container\s+string\s+`default:"([^"]+)"`\s*$/
            orchestratorGo.eachLine { line ->
                def m = line =~ re
                if (m && !m[0][1].contains('Java')) {
                    referencedImages << m[0][2]
                }
            }
        }
    }

    void failOn(Action<FailOnConfig> action) {
        action.execute(failOn)
    }
}
