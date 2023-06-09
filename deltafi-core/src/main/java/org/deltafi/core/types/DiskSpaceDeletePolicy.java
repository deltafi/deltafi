/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
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

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "__typename",
        defaultImpl = DiskSpaceDeletePolicy.class
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = DiskSpaceDeletePolicy.class, name = "DiskSpaceDeletePolicy")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(builderMethodName = "newBuilder")
public class DiskSpaceDeletePolicy implements DeletePolicy {
    private String id;
    private String name;
    private boolean enabled;
    private String flow;
    private int maxPercent;

}
