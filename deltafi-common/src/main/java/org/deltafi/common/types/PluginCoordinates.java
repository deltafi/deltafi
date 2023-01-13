/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PluginCoordinates are used to uniquely identify a plugin.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PluginCoordinates {
  private static final Pattern PATTERN = Pattern.compile("(.+):(.+):(.+)");

  private String groupId;
  private String artifactId;
  private String version;

  public PluginCoordinates(String pluginCoordinatesString) {
    Matcher matcher = PATTERN.matcher(pluginCoordinatesString);
    if (!matcher.matches()) {
      throw new IllegalArgumentException("Invalid plugin coordinates: " + pluginCoordinatesString);
    }

    groupId = matcher.group(1);
    artifactId = matcher.group(2);
    version = matcher.group(3);
  }

  public String groupAndArtifact() {
    return groupId + ":" + artifactId;
  }

  public boolean equalsIgnoreVersion(PluginCoordinates pluginCoordinates) {
    return groupId.equals(pluginCoordinates.groupId) && artifactId.equals(pluginCoordinates.artifactId);
  }

  @Override
  public String toString() {
    return String.join(":", groupId, artifactId, version);
  }
}
