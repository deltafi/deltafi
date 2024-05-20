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
package org.deltafi.core.action.convert;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.*;
import org.deltafi.actionkit.action.parameters.ActionParameters;

import java.util.List;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
public class ConvertParameters extends ActionParameters {
    @JsonPropertyDescription("Format of the input content. Supported formats are JSON, XML, and CSV.")
    private DataFormat inputFormat;

    @JsonPropertyDescription("Format of the output content. Supported formats are JSON, XML, and CSV.")
    private DataFormat outputFormat;

    @JsonProperty(defaultValue = "false")
    @JsonPropertyDescription("Boolean indicating whether the existing content should be retained or replaced by the new content. Default is false.")
    private boolean retainExistingContent = false;

    @JsonPropertyDescription("List of allowed media types. Supports wildcards (*) and defaults based on the input format if empty.")
    private List<String> mediaTypes;

    @JsonPropertyDescription("List of file patterns to consider. Supports wildcards (*) and if empty, all filenames are considered.")
    private List<String> filePatterns = List.of();

    @JsonPropertyDescription("List of content indexes to consider. If empty, all content is considered.")
    private List<Integer> contentIndexes;

    @JsonProperty(defaultValue = "xml")
    @JsonPropertyDescription("Name of the root XML tag to use when converting to XML. Defaults to xml.")
    private String xmlRootTag = "xml";

    @JsonProperty(defaultValue = "listEntry")
    @JsonPropertyDescription("Name of the XML tag to use for list entries when converting to XML. Defaults to listEntry.")
    private String xmlListEntryTag = "listEntry";

    @JsonProperty(defaultValue = "true")
    @JsonPropertyDescription("Whether to write a header row when converting to CSV. Defaults to true.")
    private boolean csvWriteHeader = true;
}