package org.deltafi.core.domain.api.types;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * PluginCoordinates are used to uniquely identify a plugin.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PluginCoordinates {
  private String groupId;
  private String artifactId;
  private String version;

  public boolean equalsIgnoreVersion(PluginCoordinates pluginCoordinates) {
    return groupId.equals(pluginCoordinates.groupId) && artifactId.equals(pluginCoordinates.artifactId);
  }

  @Override
  public String toString() {
    return String.join(":", groupId, artifactId, version);
  }
}
