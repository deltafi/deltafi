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

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.deltafi.common.types.Content;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class DeltaFileFlowInput {
    @Builder.Default
    @JsonProperty("m")
    @JsonAlias("metadata")
    Map<String, String> metadata = new HashMap<>();
    @Builder.Default
    @JsonProperty("c")
    @JsonAlias("content")
    List<Content> content = new ArrayList<>();
    @Builder.Default
    @JsonProperty("t")
    @JsonAlias("topics")
    Set<String> topics = new LinkedHashSet<>();
    @Builder.Default
    @JsonProperty("a")
    @JsonAlias("ancestorIds")
    List<Integer> ancestorIds = new ArrayList<>();
}
