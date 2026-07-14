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

package org.deltafi.gradle.docker

import org.gradle.api.Project
import org.gradle.api.file.CopySpec

import java.util.regex.Pattern

class DockerExtension {
    Project project

    private static final String DEFAULT_DOCKERFILE_PATH = 'Dockerfile'
    private static final Pattern LABEL_KEY = ~/^[a-z0-9]([a-z0-9.-]*[a-z0-9])?$/
    private String name = null
    private File dockerfile = null
    private Map<String, String> namedTags = new HashMap<>()
    private final Map<String, String> labels = new LinkedHashMap<>()

    private File resolvedDockerfile = null

    private final CopySpec copySpec

    DockerExtension(Project project) {
        this.project = project
        this.copySpec = project.copySpec()
    }

    void setName(String name) {
        this.name = name
    }

    String getName() {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException('name is a required docker configuration item.')
        }
        return name
    }

    void setDockerfile(File dockerfile) {
        this.dockerfile = dockerfile
    }

    void files(Object... files) {
        copySpec.from(files)
    }

    Map<String, String> getNamedTags() {
        return Collections.unmodifiableMap(namedTags)
    }

    void tag(String taskName, String tag) {
        namedTags.put(taskName, tag)
    }

    Map<String, String> getLabels() {
        return Collections.unmodifiableMap(labels)
    }

    void label(String key, String value) {
        if (!(key ==~ LABEL_KEY)) {
            throw new IllegalArgumentException("Invalid docker label key '${key}'. Keys must match ${LABEL_KEY}.")
        }
        labels.put(key, value)
    }

    void labels(Map<String, String> labels) {
        labels.each { key, value -> label(key, value) }
    }

    File getResolvedDockerfile() {
        return resolvedDockerfile
    }

    CopySpec getCopySpec() {
        return copySpec
    }

    void resolvePathsAndValidate() {
        resolvedDockerfile = dockerfile != null ? dockerfile : project.file(DEFAULT_DOCKERFILE_PATH)
    }
}
