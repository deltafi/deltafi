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
package org.deltafi.core.parameters;

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
public class XsltParameters extends ActionParameters {
    @JsonProperty(required = true)
    @JsonPropertyDescription("XSLT transformation specification provided as a string.")
    public String xslt;

    @JsonPropertyDescription("List of allowed media types. Supports wildcards (*) and defaults to 'application/xml' if empty.")
    public List<String> mediaTypes = List.of("application/xml");

    @JsonPropertyDescription("List of file patterns to consider. Supports wildcards (*) and if empty, all filenames are considered.")
    public List<String> filePatterns = List.of();

    @JsonPropertyDescription("List of content indexes to consider. If empty, all content is considered.")
    public List<Integer> contentIndexes;
}