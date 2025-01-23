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
package org.deltafi.core.action;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.parameters.ActionParameters;

import java.util.List;
import java.util.function.BiFunction;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ContentMatchingParameters extends ActionParameters {
    @JsonPropertyDescription("List of content indexes to include or exclude")
    private List<Integer> contentIndexes;

    @JsonProperty(defaultValue = "false")
    @JsonPropertyDescription("Exclude specified content indexes")
    private boolean excludeContentIndexes = false;

    @JsonPropertyDescription("List of file patterns to include or exclude, supporting wildcards (*)")
    private List<String> filePatterns;

    @JsonProperty(defaultValue = "false")
    @JsonPropertyDescription("Exclude specified file patterns")
    private boolean excludeFilePatterns = false;

    // Annotated on getter so annotations can be overridden
    private List<String> mediaTypes;

    @JsonProperty(defaultValue = "false")
    @JsonPropertyDescription("Exclude specified media types")
    private boolean excludeMediaTypes = false;

    public ContentMatchingParameters(List<String> mediaTypes) {
        this.mediaTypes = mediaTypes;
    }

    @JsonPropertyDescription("List of media types to include or exclude, supporting wildcards (*)")
    public List<String> getMediaTypes() {
        return mediaTypes;
    }

    private static final BiFunction<List<?>, Object, Boolean> CONTAINS_PATTERN_FUNCTION = (list, item) ->
            list.stream().anyMatch(pattern -> ((String) item).matches(((String) pattern).replace("*", ".*")));

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean contentSelected(int index, ActionContent content) {
        return contentSelected(contentIndexes, index, excludeContentIndexes, List::contains) &&
                contentSelected(filePatterns, content.getName(), excludeFilePatterns, CONTAINS_PATTERN_FUNCTION) &&
                contentSelected(mediaTypes, content.getMediaType(), excludeMediaTypes, CONTAINS_PATTERN_FUNCTION);
    }

    private boolean contentSelected(List<?> list, Object item, boolean excludeFlag,
            BiFunction<List<?>, Object, Boolean> containsFunction) {
        return (list == null) || list.isEmpty() || (excludeFlag != containsFunction.apply(list, item));
    }
}