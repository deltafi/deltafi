/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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
// ABOUTME: Data record for plugin information used in leader configuration comparison.
// ABOUTME: Contains plugin coordinates and display information for cross-member comparison.
package org.deltafi.core.types.leader;

/**
 * Information about an installed plugin.
 * Used for comparing plugin installations across members.
 */
public record PluginInfo(
        String groupId,
        String artifactId,
        String version,
        String displayName,
        String imageName,
        String imageTag
) {
    /**
     * Returns the plugin coordinates in groupId:artifactId format.
     */
    public String coordinates() {
        return groupId + ":" + artifactId;
    }
}
