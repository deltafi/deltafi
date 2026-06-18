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

// ABOUTME: Runs a scan tool via ToolRunner and applies the *Report failure contract.
// ABOUTME: Fails loudly on non-zero exit, empty stdout, or unparseable JSON — never silently.
package org.deltafi.gradle.security

import groovy.json.JsonSlurper
import org.deltafi.gradle.cli.ToolRunner
import org.gradle.api.GradleException

class ScanExecutor {

    /**
     * Invoke the tool with the supplied args, capture JSON output, and return the parsed result.
     * The result is typically a Map but may be a List for tools that emit array-rooted JSON
     * (e.g. Checkov with multiple frameworks). Throws GradleException with a [taskPath] prefix if:
     *   - the process exits non-zero (stderr included in the message),
     *   - stdout is empty,
     *   - stdout fails to parse as JSON (first 1KB included in the message).
     */
    static Object runAndParseJson(ToolRunner runner, List<String> args, String taskPath) {
        def result = runner.run(args)
        if (result.exitValue != 0) {
            throw new GradleException(
                "[${taskPath}] ${runner.cliName} exited with code ${result.exitValue}.\n" +
                "stderr:\n${truncate(result.stderr, 4096)}"
            )
        }
        if (result.stdout == null || result.stdout.isEmpty()) {
            throw new GradleException(
                "[${taskPath}] ${runner.cliName} returned no output (exit 0). " +
                "This usually indicates a silent failure mode (e.g. DB lock contention)."
            )
        }
        try {
            return new JsonSlurper().parseText(result.stdout)
        } catch (Exception e) {
            throw new GradleException(
                "[${taskPath}] failed to parse ${runner.cliName} JSON output: ${e.message}\n" +
                "first 1KB of stdout:\n${truncate(result.stdout, 1024)}"
            )
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return ''
        s.length() > max ? s.substring(0, max) + "…(truncated)" : s
    }
}
