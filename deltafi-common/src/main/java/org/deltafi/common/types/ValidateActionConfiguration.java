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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.springframework.data.annotation.PersistenceCreator;

@JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
public class ValidateActionConfiguration extends ActionConfiguration {
    public ValidateActionConfiguration(String name, String type) {
        super(name, ActionType.VALIDATE, type);
    }

    @PersistenceCreator
    @JsonCreator
    @SuppressWarnings("unused")
    public ValidateActionConfiguration(@JsonProperty(value = "name", required = true) String name,
            @JsonProperty(value = "actionType") ActionType actionType,
            @JsonProperty(value = "type", required = true) String type) {
        this(name, type);
    }
}
