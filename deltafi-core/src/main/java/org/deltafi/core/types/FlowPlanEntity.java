/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.deltafi.common.types.ActionConfiguration;
import org.deltafi.common.types.FlowPlan;
import org.deltafi.common.types.FlowType;
import org.deltafi.common.types.PluginCoordinates;
import org.hibernate.annotations.Type;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "flow_plans", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"name", "type"})
})
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "discriminator", discriminatorType = DiscriminatorType.STRING)
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
public abstract class FlowPlanEntity {
    @Id
    @GeneratedValue
    private UUID id;

    private String name;

    @Enumerated(EnumType.STRING)
    private FlowType type;

    @Column(length = 100_000)
    private String description;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private PluginCoordinates sourcePlugin;

    public FlowPlanEntity(String name, FlowType type, String description, PluginCoordinates sourcePlugin) {
        this.name = name;
        this.type = type;
        this.description = description;
        this.sourcePlugin = sourcePlugin;
    }

    public abstract List<ActionConfiguration> allActionConfigurations();
    public abstract FlowPlan toFlowPlan();
}