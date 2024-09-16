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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class RestDataSourcePlan extends DataSourcePlan {

    @JsonCreator
    @SuppressWarnings("unused")
    public RestDataSourcePlan(@JsonProperty(value = "name", required = true) String name,
                              @JsonProperty(value = "type") FlowType type,
                              @JsonProperty(value = "description", required = true) String description,
                              @JsonProperty(value = "topic", required = true) String topic) {
        super(name, FlowType.REST_DATA_SOURCE, description, topic);
    }

    @Override
    public List<ActionConfiguration> allActionConfigurations() {
        return List.of();
    }
}
