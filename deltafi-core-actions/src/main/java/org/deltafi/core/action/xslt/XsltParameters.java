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
package org.deltafi.core.action.xslt;

import com.fasterxml.jackson.annotation.*;
import lombok.*;
import org.deltafi.actionkit.action.parameters.annotation.Size;
import org.deltafi.core.action.ContentSelectionParameters;

import java.util.List;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class XsltParameters extends ContentSelectionParameters {
    @JsonProperty(required = true)
    @JsonPropertyDescription("XSLT transformation specification")
    @Size(maxLength = 5000)
    private String xslt;

    @JsonProperty(defaultValue = "[\"*/xml\"]")
    @JsonPropertyDescription("List of media types to consider, supporting wildcards (*)")
    @Override
    public List<String> getMediaTypes() {
        return super.getMediaTypes();
    }

    @JsonCreator
    public XsltParameters(List<String> mediaTypes) {
        super(mediaTypes);
    }
}
