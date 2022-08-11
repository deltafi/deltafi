/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
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

import java.util.List;

/**
 * A Plugin is a collection of actions. It may depend on other Plugins.
 */
public class Plugin {
  /**
   * The identifying coordinates
   */
  private PluginCoordinates pluginCoordinates;

  /**
   * A user-friendly name
   */
  private String displayName;

  /**
   * A description of the functionality provided
   */
  private String description;

  /**
   * The action kit version
   */
  private String actionKitVersion;

  /**
   * The actions included
   */
  private List<ActionDescriptor> actions;

  /**
   * The plugin coordinates of required plugins
   */
  private List<PluginCoordinates> dependencies;

  /**
   * The properties required
   */
  private List<PropertySet> propertySets;

  /**
   * Variables associated with this plugin
   */
  private List<Variable> variables;

  public Plugin() {
  }

  public Plugin(PluginCoordinates pluginCoordinates, String displayName, String description,
      String actionKitVersion, List<ActionDescriptor> actions, List<PluginCoordinates> dependencies,
      List<PropertySet> propertySets, List<Variable> variables) {
    this.pluginCoordinates = pluginCoordinates;
    this.displayName = displayName;
    this.description = description;
    this.actionKitVersion = actionKitVersion;
    this.actions = actions;
    this.dependencies = dependencies;
    this.propertySets = propertySets;
    this.variables = variables;
  }

  /**
   * The identifying coordinates
   */
  public PluginCoordinates getPluginCoordinates() {
    return pluginCoordinates;
  }

  public void setPluginCoordinates(PluginCoordinates pluginCoordinates) {
    this.pluginCoordinates = pluginCoordinates;
  }

  /**
   * A user-friendly name
   */
  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  /**
   * A description of the functionality provided
   */
  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  /**
   * The action kit version
   */
  public String getActionKitVersion() {
    return actionKitVersion;
  }

  public void setActionKitVersion(String actionKitVersion) {
    this.actionKitVersion = actionKitVersion;
  }

  /**
   * The actions included
   */
  public List<ActionDescriptor> getActions() {
    return actions;
  }

  public void setActions(List<ActionDescriptor> actions) {
    this.actions = actions;
  }

  /**
   * The plugin coordinates of required plugins
   */
  public List<PluginCoordinates> getDependencies() {
    return dependencies;
  }

  public void setDependencies(List<PluginCoordinates> dependencies) {
    this.dependencies = dependencies;
  }

  /**
   * The properties required
   */
  public List<PropertySet> getPropertySets() {
    return propertySets;
  }

  public void setPropertySets(List<PropertySet> propertySets) {
    this.propertySets = propertySets;
  }

  /**
   * Variables associated with this plugin
   */
  public List<Variable> getVariables() {
    return variables;
  }

  public void setVariables(List<Variable> variables) {
    this.variables = variables;
  }

  @Override
  public String toString() {
    return "Plugin{" + "pluginCoordinates='" + pluginCoordinates + "'," +"displayName='" + displayName + "'," +"description='" + description + "'," +"actionKitVersion='" + actionKitVersion + "'," +"actions='" + actions + "'," +"dependencies='" + dependencies + "'," +"propertySets='" + propertySets + "'," +"variables='" + variables + "'" +"}";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Plugin that = (Plugin) o;
        return java.util.Objects.equals(pluginCoordinates, that.pluginCoordinates) &&
                            java.util.Objects.equals(displayName, that.displayName) &&
                            java.util.Objects.equals(description, that.description) &&
                            java.util.Objects.equals(actionKitVersion, that.actionKitVersion) &&
                            java.util.Objects.equals(actions, that.actions) &&
                            java.util.Objects.equals(dependencies, that.dependencies) &&
                            java.util.Objects.equals(propertySets, that.propertySets) &&
                            java.util.Objects.equals(variables, that.variables);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(pluginCoordinates, displayName, description, actionKitVersion, actions, dependencies, propertySets, variables);
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  public static class Builder {
    /**
     * The identifying coordinates
     */
    private PluginCoordinates pluginCoordinates;

    /**
     * A user-friendly name
     */
    private String displayName;

    /**
     * A description of the functionality provided
     */
    private String description;

    /**
     * The action kit version
     */
    private String actionKitVersion;

    /**
     * The actions included
     */
    private List<ActionDescriptor> actions;

    /**
     * The plugin coordinates of required plugins
     */
    private List<PluginCoordinates> dependencies;

    /**
     * The properties required
     */
    private List<PropertySet> propertySets;

    /**
     * Variables associated with this plugin
     */
    private List<Variable> variables;

    public Plugin build() {
                  Plugin result = new Plugin();
                      result.pluginCoordinates = this.pluginCoordinates;
          result.displayName = this.displayName;
          result.description = this.description;
          result.actionKitVersion = this.actionKitVersion;
          result.actions = this.actions;
          result.dependencies = this.dependencies;
          result.propertySets = this.propertySets;
          result.variables = this.variables;
                      return result;
    }

    /**
     * The identifying coordinates
     */
    public Builder pluginCoordinates(
        PluginCoordinates pluginCoordinates) {
      this.pluginCoordinates = pluginCoordinates;
      return this;
    }

    /**
     * A user-friendly name
     */
    public Builder displayName(String displayName) {
      this.displayName = displayName;
      return this;
    }

    /**
     * A description of the functionality provided
     */
    public Builder description(String description) {
      this.description = description;
      return this;
    }

    /**
     * The action kit version
     */
    public Builder actionKitVersion(
        String actionKitVersion) {
      this.actionKitVersion = actionKitVersion;
      return this;
    }

    /**
     * The actions included
     */
    public Builder actions(
        List<ActionDescriptor> actions) {
      this.actions = actions;
      return this;
    }

    /**
     * The plugin coordinates of required plugins
     */
    public Builder dependencies(
        List<PluginCoordinates> dependencies) {
      this.dependencies = dependencies;
      return this;
    }

    /**
     * The properties required
     */
    public Builder propertySets(
        List<PropertySet> propertySets) {
      this.propertySets = propertySets;
      return this;
    }

    /**
     * Variables associated with this plugin
     */
    public Builder variables(
        List<Variable> variables) {
      this.variables = variables;
      return this;
    }
  }
}
