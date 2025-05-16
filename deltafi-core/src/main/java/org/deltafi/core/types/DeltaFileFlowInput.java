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

import com.fasterxml.jackson.annotation.*;
import jakarta.persistence.Transient;
import lombok.*;
import org.deltafi.common.types.Content;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeltaFileFlowInput {
    @Transient
    @JsonIgnore
    @EqualsAndHashCode.Exclude
    private DeltaFileFlow flow;

    @Transient
    @JsonIgnore
    @EqualsAndHashCode.Exclude
    private Map<String,String> cachedMetadata;

    @Transient
    @JsonIgnore
    public Map<String,String> getMetadata() {
        if (cachedMetadata == null) {
            cachedMetadata = (flow == null || flow.getOwner() == null)
                    ? Map.of()
                    : flow.getOwner().metadataFor(ancestorIds);
        }
        return cachedMetadata;
    }

    @Transient
    @JsonIgnore
    public List<Content> getContent() {
        if (ancestorIds.isEmpty()) {
            if (flow.getActions().isEmpty()) {
                return new ArrayList<>();
            }
            return flow.firstAction().getContent();
        } else {
            return flow.getOwner().getFlow(ancestorIds.getFirst()).lastContent();
        }
    }

    @Builder.Default
    @JsonProperty("t")
    @JsonAlias("topics")
    Set<String> topics = new LinkedHashSet<>();
    @Builder.Default
    @JsonProperty("a")
    @JsonAlias("ancestorIds")
    List<Integer> ancestorIds = new ArrayList<>();
}
