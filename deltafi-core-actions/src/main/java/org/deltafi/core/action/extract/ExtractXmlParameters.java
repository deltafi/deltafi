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
package org.deltafi.core.action.extract;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ExtractXmlParameters extends ExtractParameters {
    @JsonProperty(defaultValue = "{}")
    @JsonPropertyDescription("A map of XPath expressions to keys. Values will be extracted using XPath and added to the corresponding metadata or annotation keys.")
    public Map<String, String> xpathToKeysMap = new HashMap<>();

    @JsonProperty(defaultValue = "[\"*/xml\"]")
    @JsonPropertyDescription("List of allowed media types. Supports wildcards (*) and defaults to */xml.")
    @Override
    public List<String> getMediaTypes() {
        return super.getMediaTypes();
    }

    @JsonCreator
    public ExtractXmlParameters(List<String> mediaTypes) {
        super(mediaTypes);
    }
}
