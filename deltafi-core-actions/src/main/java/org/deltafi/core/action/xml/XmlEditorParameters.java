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
package org.deltafi.core.action.xml;

import com.fasterxml.jackson.annotation.*;
import lombok.*;
import org.deltafi.core.action.ContentMatchingParameters;

import java.util.List;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class XmlEditorParameters extends ContentMatchingParameters {
    @JsonProperty(required = true)
    @JsonPropertyDescription("List of XML editing commands to be applied in order")
    private List<String> xmlEditingCommands;

    @JsonProperty(defaultValue = "[\"*/xml\"]")
    @JsonPropertyDescription("List of media types to consider, supporting wildcards (*)")
    @Override
    public List<String> getMediaTypes() {
        return super.getMediaTypes();
    }

    @JsonCreator
    public XmlEditorParameters(List<String> mediaTypes) {
        super(mediaTypes);
    }
}
