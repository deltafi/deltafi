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
package org.deltafi.common.types;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * Framework-agnostic build metadata for a plugin. Populated by a framework adapter (Spring autoconfig
 * builds this from {@code org.springframework.boot.info.BuildProperties}; Micronaut, Quarkus, and
 * plain-Java callers populate it from their own sources). Used by both the registration payload and
 * the action runner.
 */
public record BuildInfo(
        String group,
        String artifact,
        String version,
        String name,
        String description,
        String actionKitVersion,
        String pluginDependencies) {

    private static final String BUILD_INFO_RESOURCE = "META-INF/build-info.properties";
    private static final String BUILD_INFO_PREFIX = "build.";

    public PluginCoordinates pluginCoordinates() {
        return PluginCoordinates.builder()
                .groupId(group)
                .artifactId(artifact)
                .version(version)
                .build();
    }

    public List<PluginCoordinates> dependenciesList() {
        return pluginDependencies == null ? List.of() :
                Arrays.stream(pluginDependencies.split(",\\s?")).map(PluginCoordinates::new).toList();
    }

    /**
     * Load build metadata from {@code META-INF/build-info.properties} on the classpath
     * (the file the Spring Boot Gradle plugin emits). Missing file yields an empty {@code BuildInfo}.
     */
    public static BuildInfo fromBuildInfoProperties(ClassLoader classLoader) {
        Properties raw = new Properties();
        try (InputStream in = classLoader.getResourceAsStream(BUILD_INFO_RESOURCE)) {
            if (in != null) {
                raw.load(in);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + BUILD_INFO_RESOURCE, e);
        }
        return new BuildInfo(
                value(raw, "group"),
                value(raw, "artifact"),
                value(raw, "version"),
                value(raw, "name"),
                value(raw, "description"),
                value(raw, "actionKitVersion"),
                value(raw, "pluginDependencies"));
    }

    private static String value(Properties raw, String key) {
        String prefixed = raw.getProperty(BUILD_INFO_PREFIX + key);
        return prefixed != null ? prefixed : raw.getProperty(key);
    }
}
