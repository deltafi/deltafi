/*
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
package org.deltafi.core.plugin;

import jakarta.persistence.*;
import lombok.Data;
import org.deltafi.common.types.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "plugins")
@Data
public class PluginEntity {
    @EmbeddedId
    private PluginCoordinates pluginCoordinates;

    private String displayName;
    private String description;
    private String actionKitVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<ActionDescriptor> actions;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<PluginCoordinates> dependencies;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<PropertySet> propertySets;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Variable> variables;

    public Plugin toPlugin() {
        Plugin plugin = new Plugin();
        plugin.setPluginCoordinates(this.pluginCoordinates);
        plugin.setDisplayName(this.displayName);
        plugin.setDescription(this.description);
        plugin.setActionKitVersion(this.actionKitVersion);
        plugin.setActions(this.actions);
        plugin.setDependencies(this.dependencies);
        plugin.setPropertySets(this.propertySets);
        plugin.setVariables(this.variables);
        return plugin;
    }

    public static PluginEntity fromPlugin(Plugin plugin) {
        PluginEntity entity = new PluginEntity();
        entity.setPluginCoordinates(plugin.getPluginCoordinates());
        entity.setDisplayName(plugin.getDisplayName());
        entity.setDescription(plugin.getDescription());
        entity.setActionKitVersion(plugin.getActionKitVersion());
        entity.setActions(plugin.getActions());
        entity.setDependencies(plugin.getDependencies());
        entity.setPropertySets(plugin.getPropertySets());
        entity.setVariables(plugin.getVariables());
        return entity;
    }

    public List<String> actionNames() {
        return Objects.nonNull(getActions()) ?
                getActions().stream().map(ActionDescriptor::getName).toList() : List.of();
    }
}