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
package org.deltafi.common.types;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;

import java.util.List;

@Data
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = DataSinkPlan.class, name = "DATA_SINK"),
        @JsonSubTypes.Type(value = TransformFlowPlan.class, name = "TRANSFORM"),
        @JsonSubTypes.Type(value = TimedDataSourcePlan.class, name = "TIMED_DATA_SOURCE"),
        @JsonSubTypes.Type(value = RestDataSourcePlan.class, name = "REST_DATA_SOURCE")
})
public abstract class FlowPlan {
    private final String name;
    private final FlowType type;
    private final String description;

    private PluginCoordinates sourcePlugin;

    public abstract List<ActionConfiguration> allActionConfigurations();
}
