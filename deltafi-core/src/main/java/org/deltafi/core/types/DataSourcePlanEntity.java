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
package org.deltafi.core.types;

import jakarta.persistence.MappedSuperclass;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.deltafi.common.types.FlowType;
import org.deltafi.common.types.PluginCoordinates;

@MappedSuperclass
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
public abstract class DataSourcePlanEntity extends FlowPlanEntity {
    private String topic;

    public DataSourcePlanEntity(String name, FlowType flowType, String description, PluginCoordinates sourcePlugin, String topic) {
        super(name, flowType, description, sourcePlugin);
        this.topic = topic;
    }
}