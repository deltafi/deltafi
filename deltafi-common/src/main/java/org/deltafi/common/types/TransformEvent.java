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

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.*;

@Data
@NoArgsConstructor
@EqualsAndHashCode
@SuperBuilder
public class TransformEvent {
    // optional name, used to rename children when splitting
    private String name;

    private UUID did;

    @Builder.Default
    private List<Content> content = new ArrayList<>();

    @Builder.Default
    private Map<String, String> annotations = new HashMap<>();

    @Builder.Default
    private Map<String, String> metadata = new HashMap<>();

    @Builder.Default
    private List<String> deleteMetadataKeys = new ArrayList<>();

    @Builder.Default
    private List<LogMessage> messages = new ArrayList<>();

    public Set<String> segmentObjectNames() {
        Set<String> objectNames = new HashSet<>();
        for (Content c : content) {
            objectNames.addAll(c.objectNames());
        }
        return objectNames;
    }
}
