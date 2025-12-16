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
package org.deltafi.core.action.filterlines;

// ABOUTME: Parameters for the FilterLines transform action.
// ABOUTME: Configures line selection criteria including head, tail, skip, include/exclude patterns, and empty line handling.

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.deltafi.core.action.ContentSelectionParameters;

import java.util.List;
import java.util.regex.Pattern;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class FilterLinesParameters extends ContentSelectionParameters {

    @JsonPropertyDescription("Keep the first N lines. Combined with tailLines as a union. Negative values are ignored.")
    private Integer headLines;

    @JsonPropertyDescription("Keep the last N lines. Combined with headLines as a union. Negative values are ignored.")
    private Integer tailLines;

    @JsonPropertyDescription("Skip the first N lines (e.g., for header removal). Negative values are ignored.")
    private Integer skipLines;

    @JsonPropertyDescription("List of Java regular expression patterns - keep lines matching based on includeMatchMode. Invalid patterns will cause an error at configuration time.")
    private List<String> includePatterns;

    private transient List<Pattern> compiledIncludePatterns;

    @JsonProperty(defaultValue = "OR")
    @JsonPropertyDescription("Match mode for includePatterns: OR (match any pattern) or AND (match all patterns)")
    private MatchMode includeMatchMode = MatchMode.OR;

    @JsonPropertyDescription("List of Java regular expression patterns - remove lines matching based on excludeMatchMode (applied after includePatterns). Invalid patterns will cause an error at configuration time.")
    private List<String> excludePatterns;

    private transient List<Pattern> compiledExcludePatterns;

    @JsonProperty(defaultValue = "OR")
    @JsonPropertyDescription("Match mode for excludePatterns: OR (remove if any pattern matches) or AND (remove only if all patterns match)")
    private MatchMode excludeMatchMode = MatchMode.OR;

    public void setIncludePatterns(List<String> includePatterns) {
        this.includePatterns = includePatterns;
        this.compiledIncludePatterns = compilePatterns(includePatterns);
    }

    public void setExcludePatterns(List<String> excludePatterns) {
        this.excludePatterns = excludePatterns;
        this.compiledExcludePatterns = compilePatterns(excludePatterns);
    }

    private static List<Pattern> compilePatterns(List<String> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            return null;
        }
        return patterns.stream().map(Pattern::compile).toList();
    }

    @JsonProperty(defaultValue = "true")
    @JsonPropertyDescription("Keep empty lines. If false, empty lines are removed")
    private boolean keepEmpty = true;

    @JsonProperty(defaultValue = "\n")
    @JsonPropertyDescription("Line delimiter for splitting content. Defaults to newline (\\n)")
    private String lineDelimiter = "\n";

    @JsonProperty(defaultValue = "true")
    @JsonPropertyDescription("Preserve the line delimiter in output. If false, uses system line separator")
    private boolean preserveDelimiter = true;
}
