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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.deltafi.common.types.VariableDataType;

@Entity
@Table(name = "properties")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Property {
    @Id
    private String key;
    @JsonIgnore
    private String customValue;
    private String description;
    private String defaultValue;
    private boolean refreshable;
    @Enumerated(EnumType.STRING)
    private VariableDataType dataType;

    public boolean hasValue() {
        return null != customValue;
    }

    @JsonProperty("value")
    public String getValue() {
        return customValue != null ? customValue : defaultValue;
    }

    @SuppressWarnings("unused")
    public PropertySource getPropertySource() {
        return customValue != null ? PropertySource.CUSTOM : PropertySource.DEFAULT;
    }
}
