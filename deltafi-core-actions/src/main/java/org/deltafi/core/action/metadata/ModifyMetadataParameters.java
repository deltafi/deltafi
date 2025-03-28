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
    @JsonPropertyDescription("Key value pairs of metadata to be added or modified")
    private Map<String, String> addOrModifyMetadata = new HashMap<>();

    @JsonPropertyDescription("Map of old metadata key names to new metadata key names to copy. The new metadata key names can be a comma-separated list to make multiple copies.")
    private Map<String, String> copyMetadata = new HashMap<>();

    @JsonPropertyDescription("List of metadata keys to delete")
    private List<String> deleteMetadataKeys = new ArrayList<>();
}