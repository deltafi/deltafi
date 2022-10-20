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

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;

@Data
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
@JsonSubTypes({
        @JsonSubTypes.Type(value = IngressFlowConfiguration.class, name = "IngressFlowConfiguration"),
        @JsonSubTypes.Type(value = EnrichFlowConfiguration.class, name = "EnrichFlowConfiguration"),
        @JsonSubTypes.Type(value = EgressFlowConfiguration.class, name = "EgressFlowConfiguration"),
        @JsonSubTypes.Type(value = TransformActionConfiguration.class, name = "TransformActionConfiguration"),
        @JsonSubTypes.Type(value = LoadActionConfiguration.class, name = "LoadActionConfiguration"),
        @JsonSubTypes.Type(value = DomainActionConfiguration.class, name = "DomainActionConfiguration"),
        @JsonSubTypes.Type(value = EnrichActionConfiguration.class, name = "EnrichActionConfiguration"),
        @JsonSubTypes.Type(value = FormatActionConfiguration.class, name = "FormatActionConfiguration"),
        @JsonSubTypes.Type(value = ValidateActionConfiguration.class, name = "ValidateActionConfiguration"),
        @JsonSubTypes.Type(value = EgressActionConfiguration.class, name = "EgressActionConfiguration")
})
public class DeltaFiConfiguration {
    protected final String name;
    protected String apiVersion;
}
