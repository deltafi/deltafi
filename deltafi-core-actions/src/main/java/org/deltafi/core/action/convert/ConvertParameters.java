/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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
import org.deltafi.core.action.ContentSelectionParameters;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ConvertParameters extends ContentSelectionParameters {
    @JsonProperty(required = true)
    @JsonPropertyDescription("Format of the input content")
    private DataFormat inputFormat;

    @JsonProperty(required = true)
    @JsonPropertyDescription("Format of the output content")
    private DataFormat outputFormat;

    @JsonProperty(defaultValue = "true")
    @JsonPropertyDescription("Write a header row when converting to CSV")
    private boolean csvWriteHeader = true;

    @JsonProperty(defaultValue = "xml")
    @JsonPropertyDescription("Name of the root XML tag to use when converting to XML")
    private String xmlRootTag = "xml";

    @JsonProperty(defaultValue = "listEntry")
    @JsonPropertyDescription("Name of the XML tag to use for list entries when converting to XML")
    private String xmlListEntryTag = "listEntry";
}