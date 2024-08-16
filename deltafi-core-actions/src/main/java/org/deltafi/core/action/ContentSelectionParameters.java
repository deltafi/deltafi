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
package org.deltafi.core.action;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.deltafi.actionkit.action.parameters.ActionParameters;

import java.util.Collections;
import java.util.List;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class ContentSelectionParameters extends ActionParameters {
    @JsonPropertyDescription("List of content indexes to consider")
    private List<Integer> contentIndexes;

    // Annotated on getter so it can be overridden
    private List<String> mediaTypes;

    @JsonPropertyDescription("List of file patterns to consider, supporting wildcards (*)")
    private List<String> filePatterns;

    @JsonProperty(defaultValue = "false")
    @JsonPropertyDescription("Retain the existing content")
    private boolean retainExistingContent = false;

    public ContentSelectionParameters() {
        this(Collections.emptyList());
    }

    public ContentSelectionParameters(List<String> mediaTypes) {
        this.mediaTypes = mediaTypes;
    }

    @JsonPropertyDescription("List of media types to consider, supporting wildcards (*)")
    public List<String> getMediaTypes() {
        return mediaTypes;
    }

    public boolean contentMatches(String name, String mediaType, int index) {
        return (contentIndexes == null || contentIndexes.isEmpty() || contentIndexes.contains(index)) &&
                (filePatterns == null || filePatterns.isEmpty() || filePatterns.stream().anyMatch(pattern -> matchesPattern(name, pattern))) &&
                (mediaTypes == null || mediaTypes.isEmpty() || mediaTypes.stream().anyMatch(allowedType -> matchesPattern(mediaType, allowedType)));
    }

    private boolean matchesPattern(String value, String pattern) {
        return value.matches(pattern.replace("*", ".*"));
    }
}