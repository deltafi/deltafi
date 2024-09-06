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
package org.deltafi.common.types;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
public class DeltaFileFlowInput {
    @Builder.Default
    Map<String, String> metadata = new HashMap<>();
    @Builder.Default
    List<Content> content = new ArrayList<>();
    @Builder.Default
    Set<String> topics = new LinkedHashSet<>();
    @Builder.Default
    List<Integer> ancestorIds = new ArrayList<>();

    public DeltaFileFlowInput(DeltaFileFlowInput other) {
        this.metadata = new HashMap<>(other.metadata);
        this.content = other.content == null ? null : other.content.stream().map(Content::new).toList();
        this.topics = other.topics;
        this.ancestorIds = new ArrayList<>(other.ancestorIds);
    }
}