/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.action.metadata;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.*;
import org.deltafi.actionkit.action.parameters.ActionParameters;

import java.util.*;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ModifyMetadataParameters extends ActionParameters {
    @JsonPropertyDescription("Key value pairs of metadata to be added or modified (applied first before other operations)")
    private Map<String, String> addOrModifyMetadata = new HashMap<>();

    @JsonPropertyDescription("Map of old metadata key names to new metadata key names to copy. The new metadata key names can be a comma-separated list to make multiple copies. This is applied after addOrModifyMetadata but before the operations list.")
    private Map<String, String> copyMetadata = new HashMap<>();

    @JsonPropertyDescription("List of metadata keys to delete. This is applied after addOrModifyMetadata and copyMetadata but before the operations list.")
    private List<String> deleteMetadataKeys = new ArrayList<>();

    @JsonPropertyDescription("""
            Ordered metadata operations applied as a pipeline over a working copy: the first sees the input
            metadata, each operation after sees the prior result. The convenience fields above run first. Types: ADD_OR_MODIFY,
            COPY, DELETE_KEYS, DELETE_MATCHING, REPLACE_KEY, REPLACE_VALUE.""")
    private List<MetadataOperation> operations = new ArrayList<>();
}
