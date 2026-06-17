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

// ABOUTME: Runs a CLI tool with automatic fallback to a Docker image when the CLI is not installed.
// ABOUTME: Used by security scanning tasks to invoke trivy/grype/syft without requiring local installs.
package org.deltafi.gradle.cli

import org.gradle.api.GradleException
import org.gradle.process.ExecOperations

class ToolRunner {

    static class Result {
        int exitValue
        String stdout
        String stderr
    }

    final String cliName
    final String dockerImage
    final boolean mountDockerSocket
    /** Host → container path mappings. Used by filesystem-scanning tools (Checkov) when falling
     *  back to docker — the host path becomes a `-v host:container` mount and any path the caller
     *  asks about via translatePath() is rewritten to its container-side equivalent. */
    private final Map<String, String> volumeMounts = [:]
    /** Environment variables set on every invocation (native via process env, container via -e flag). */
    private final Map<String, String> envVars = [:]

    private final ExecOperations execOperations
    private Boolean useContainerCache = null
    private boolean fallbackAnnounced = false

    ToolRunner(ExecOperations execOperations, String cliName, String dockerImage, boolean mountDockerSocket = true) {
        this.execOperations = execOperations
        this.cliName = cliName
        this.dockerImage = dockerImage
        this.mountDockerSocket = mountDockerSocket
    }

    /**
     * Register a host path that should be available to the tool when running in container mode.
     * Paths inside this host directory can be translated to their container-side equivalents via
     * translatePath(). In native mode this is a no-op (paths are used as-is).
     */
    void mount(String hostPath, String containerPath) {
        volumeMounts[hostPath] = containerPath
    }

    /** Set an environment variable for the tool's process (works in both native and container modes). */
    void env(String key, String value) {
        envVars[key] = value
    }

    /**
     * Translate a host path to the path the tool should see. In native mode, returns the host
     * path unchanged. In container mode, rewrites the prefix to match the registered mount with
     * the longest matching host-side prefix.
     */
    String translatePath(String hostPath) {
        if (!isUsingContainer()) return hostPath
        def best = volumeMounts.entrySet().findAll { hostPath == it.key || hostPath.startsWith(it.key + '/') }
                                          .max { it.key.length() }
        if (best == null) return hostPath
        return hostPath == best.key ? best.value : best.value + hostPath.substring(best.key.length())
    }

    boolean isUsingContainer() {
        if (useContainerCache == null) ensureAvailable()
        return useContainerCache
    }

    void ensureAvailable() {
        if (useContainerCache != null) return
        useContainerCache = !commandAvailable(cliName)
        if (useContainerCache && !commandAvailable('docker')) {
            throw new GradleException("${cliName} is not installed and docker is not available. Please install ${cliName} or docker.")
        }
    }

    /**
     * Print a one-line notice the first time we observe that the CLI is missing and we are
     * falling back to docker. Subsequent calls are no-ops so multi-step scan tasks don't repeat.
     */
    void announceFallbackOnce() {
        if (!isUsingContainer() || fallbackAnnounced) return
        println "${cliName} not found locally, using Docker container: ${dockerImage}"
        fallbackAnnounced = true
    }

    String getExecutable() {
        isUsingContainer() ? 'docker' : cliName
    }

    /**
     * Build the argument list for the chosen execution mode. The caller supplies every
     * argument that should appear after the CLI binary (e.g. ['image', '--format', 'json', name]
     * for trivy); when falling back to docker we prepend the run + image bits transparently.
     */
    List<String> buildArgs(List<String> cliArgs) {
        if (!isUsingContainer()) return cliArgs.collect()
        def dockerArgs = ['run', '--rm']
        if (mountDockerSocket) {
            dockerArgs += ['-v', "${dockerSocketPath()}:/var/run/docker.sock"]
        }
        volumeMounts.each { String hostPath, String containerPath ->
            dockerArgs += ['-v', "${hostPath}:${containerPath}"]
        }
        envVars.each { String k, String v ->
            dockerArgs += ['-e', "${k}=${v}"]
        }
        dockerArgs << dockerImage
        dockerArgs.addAll(cliArgs)
        return dockerArgs
    }

    /**
     * Run the tool capturing stdout and stderr. Always sets ignoreExitValue=true so callers can
     * inspect the exit code (scanners typically return non-zero on findings, which is not a build error).
     */
    Result run(List<String> cliArgs) {
        ensureAvailable()
        // Capture before the exec closure — inside it, bare method references resolve against the
        // ExecSpec delegate, not this ToolRunner instance.
        String exe = getExecutable()
        List<String> resolvedArgs = buildArgs(cliArgs)
        boolean nativeMode = !isUsingContainer()
        Map<String, String> processEnv = new LinkedHashMap<>(envVars)
        def stdout = new ByteArrayOutputStream()
        def stderr = new ByteArrayOutputStream()
        def execResult = execOperations.exec {
            it.ignoreExitValue = true
            it.executable = exe
            it.args = resolvedArgs
            if (nativeMode && !processEnv.isEmpty()) {
                processEnv.each { k, v -> it.environment(k, v) }
            }
            it.standardOutput = stdout
            it.errorOutput = stderr
        }
        return new Result(exitValue: execResult.exitValue, stdout: stdout.toString(), stderr: stderr.toString())
    }

    /**
     * Run the tool streaming output directly to the console (used for the human-friendly table
     * pass that runs alongside the JSON capture).
     */
    Result runStreaming(List<String> cliArgs) {
        ensureAvailable()
        String exe = getExecutable()
        List<String> resolvedArgs = buildArgs(cliArgs)
        boolean nativeMode = !isUsingContainer()
        Map<String, String> processEnv = new LinkedHashMap<>(envVars)
        def execResult = execOperations.exec {
            it.ignoreExitValue = true
            it.executable = exe
            it.args = resolvedArgs
            if (nativeMode && !processEnv.isEmpty()) {
                processEnv.each { k, v -> it.environment(k, v) }
            }
        }
        return new Result(exitValue: execResult.exitValue, stdout: '', stderr: '')
    }

    private boolean commandAvailable(String name) {
        def execResult = execOperations.exec {
            it.ignoreExitValue = true
            it.executable = 'bash'
            it.args = ['-c', "command -v ${name}"]
            it.standardOutput = new ByteArrayOutputStream()
            it.errorOutput = new ByteArrayOutputStream()
        }
        return execResult.exitValue == 0
    }

    private static String dockerSocketPath() {
        def osName = System.getProperty('os.name').toLowerCase()
        return osName.contains('windows') ? '//var/run/docker.sock' : '/var/run/docker.sock'
    }
}
