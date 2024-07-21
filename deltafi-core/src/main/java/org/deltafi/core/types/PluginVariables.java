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
package org.deltafi.core.types;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.common.types.Variable;
import org.hibernate.annotations.Type;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "plugin_variables",
        uniqueConstraints = @UniqueConstraint(columnNames = {"group_id", "artifact_id", "version"}))
@Data
@EqualsAndHashCode
public class PluginVariables {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    private String groupId;
    private String artifactId;
    private String version;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private List<Variable> variables;

    @Transient
    public PluginCoordinates getSourcePlugin() {
        return new PluginCoordinates(groupId, artifactId, version);
    }

    public void setSourcePlugin(PluginCoordinates sourcePlugin) {
        this.groupId = sourcePlugin.getGroupId();
        this.artifactId = sourcePlugin.getArtifactId();
        this.version = sourcePlugin.getVersion();
    }
}
