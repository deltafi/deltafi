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
package org.deltafi.core.plugin.deployer.customization;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.deltafi.common.types.PluginCoordinates;
import org.hibernate.annotations.Type;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "plugin_customization", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"plugin_coordinates"})
})
public class PluginCustomizationWithId {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    UUID id;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private PluginCoordinates pluginCoordinates;
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private  PluginCustomization pluginCustomization;
}
