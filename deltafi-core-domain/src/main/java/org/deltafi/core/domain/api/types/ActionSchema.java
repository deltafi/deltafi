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
package org.deltafi.core.domain.api.types;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.OffsetDateTime;
import java.util.Map;

/*
 * This is the codegen generated class, except the Spring-Mongo @Document annotation is added.
 */

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "__typename"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = DeleteActionSchema.class, name = "DeleteActionSchema"),
        @JsonSubTypes.Type(value = EgressActionSchema.class, name = "EgressActionSchema"),
        @JsonSubTypes.Type(value = EnrichActionSchema.class, name = "EnrichActionSchema"),
        @JsonSubTypes.Type(value = FormatActionSchema.class, name = "FormatActionSchema"),
        @JsonSubTypes.Type(value = LoadActionSchema.class, name = "LoadActionSchema"),
        @JsonSubTypes.Type(value = TransformActionSchema.class, name = "TransformActionSchema"),
        @JsonSubTypes.Type(value = ValidateActionSchema.class, name = "ValidateActionSchema")
})
@Document("actionSchema")
public interface ActionSchema {
    String getId();

    String getParamClass();

    Map<String, Object> getSchema();

    OffsetDateTime getLastHeard();
}
