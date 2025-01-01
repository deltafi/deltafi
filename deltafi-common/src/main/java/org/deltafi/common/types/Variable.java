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

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Variable {

  public static final String MASK_STRING = "*********";

  private String name;
  private String description;
  private VariableDataType dataType;
  private boolean required;
  private String defaultValue;
  private String value;
  private boolean masked;

  public boolean hasValue() {
    return null != value;
  }

  public Variable maskIfSensitive() {
    return masked ? copyWithMaskedValue() : this;
  }

  @JsonIgnore
  public boolean isNotMasked() {
    return !masked;
  }

  private Variable copyWithMaskedValue() {
    return Variable.builder()
            .name(name)
            .description(description)
            .value(MASK_STRING)
            .defaultValue(MASK_STRING)
            .dataType(dataType)
            .required(required)
            .masked(masked)
            .build();
  }

}
