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
package org.deltafi.common.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.*;

import java.util.Collections;
import java.util.Map;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
public abstract class DataSourcePlan extends FlowPlan {
    private String topic;
    private Map<String, String> metadata;
    private AnnotationConfig annotationConfig;

    @JsonCreator
    @SuppressWarnings("unused")
    public DataSourcePlan(@JsonProperty(value = "name", required = true) String name,
                          @JsonProperty(value = "type") FlowType type,
                          @JsonProperty(value = "description", required = true) String description,
                          @JsonProperty(value = "metadata") Map<String, String> metadata,
                          @JsonProperty(value = "annotationConfig") AnnotationConfig annotationConfig,
                          @JsonProperty(value = "topic", required = true) String topic) {
        super(name, type, description);
        this.topic = topic;
        this.metadata = metadata == null ? Collections.emptyMap() : metadata;
        this.annotationConfig = annotationConfig == null ? AnnotationConfig.emptyConfig() : annotationConfig;
    }
}
