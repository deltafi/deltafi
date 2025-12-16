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

// ABOUTME: Transform action that filters content based on line-level criteria.
// ABOUTME: Supports head, tail, skip, include/exclude patterns, and empty line handling.

import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.ActionOptions;
import org.deltafi.core.action.ContentSelectingTransformAction;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class FilterLines extends ContentSelectingTransformAction<FilterLinesParameters> {

    private static final String DESCRIPTION = """
            Filters content based on line-level criteria including positional selection \
            (head, tail, skip) and pattern matching (include/exclude with regex).

            ## Order of Operations

            Filters are applied in this sequence:
            1. **skipLines** - Remove N lines from the beginning
            2. **headLines + tailLines** - Keep union of first N and last M lines (deduplicated)
            3. **includePatterns** - Keep only lines matching the patterns
            4. **excludePatterns** - Remove lines matching the patterns
            5. **keepEmpty** - Remove empty lines (if false)

            ## Head and Tail Union Behavior

            When both `headLines` and `tailLines` are specified, they produce a union of lines, \
            not a sequential filter. For example, with `headLines: 2` and `tailLines: 2` on a \
            5-line file, you get lines 1, 2, 4, and 5 (the first 2 and last 2). Overlapping \
            ranges are deduplicated.

            ## Pattern Match Modes

            - **OR mode** (default): A line matches if it matches ANY pattern in the list
            - **AND mode**: A line matches only if it matches ALL patterns in the list

            Patterns use Java regex `find()` semantics, meaning patterns match anywhere in the \
            line (not the entire line). Use `^` and `$` anchors for start/end of line matching.

            Invalid regex patterns in includePatterns or excludePatterns will cause an error \
            at flow configuration time, not during DeltaFile processing.

            ## Examples

            ### Skip CSV Header
            Remove the first line (header row) from a CSV file:
            ```yaml
            - name: RemoveHeader
              type: org.deltafi.core.action.filterlines.FilterLines
              parameters:
                skipLines: 1
            ```

            ### Keep Header and Last Lines
            Keep the first line (header) and last 10 lines (useful for CSV preview):
            ```yaml
            - name: HeaderAndTail
              type: org.deltafi.core.action.filterlines.FilterLines
              parameters:
                headLines: 1
                tailLines: 10
            ```

            ### Filter Log Lines by Severity
            Keep only ERROR and WARN log entries:
            ```yaml
            - name: FilterErrors
              type: org.deltafi.core.action.filterlines.FilterLines
              parameters:
                includePatterns:
                  - "ERROR"
                  - "WARN"
            ```

            ### Remove Comment Lines
            Remove lines starting with `#` or `//`:
            ```yaml
            - name: RemoveComments
              type: org.deltafi.core.action.filterlines.FilterLines
              parameters:
                excludePatterns:
                  - "^#"
                  - "^//"
            ```

            ### Filter Lines Containing Multiple Keywords
            Keep only lines containing BOTH "ERROR" and "database":
            ```yaml
            - name: DatabaseErrors
              type: org.deltafi.core.action.filterlines.FilterLines
              parameters:
                includePatterns:
                  - "ERROR"
                  - "database"
                includeMatchMode: AND
            ```

            ### Complex Log Processing
            Skip header, keep first 1000 lines, include only ERROR/WARN, exclude DEBUG noise, remove empty lines:
            ```yaml
            - name: ProcessLogs
              type: org.deltafi.core.action.filterlines.FilterLines
              parameters:
                skipLines: 1
                headLines: 1000
                includePatterns:
                  - "ERROR"
                  - "WARN"
                excludePatterns:
                  - "DEBUG"
                keepEmpty: false
            ```

            ### Process Only Log Files
            Apply filtering only to `.log` files, passing other content through unchanged:
            ```yaml
            - name: FilterLogFiles
              type: org.deltafi.core.action.filterlines.FilterLines
              parameters:
                filePatterns:
                  - "*.log"
                tailLines: 500
            ```

            ### Windows Line Endings
            Process content with Windows-style line endings:
            ```yaml
            - name: FilterWindowsFile
              type: org.deltafi.core.action.filterlines.FilterLines
              parameters:
                lineDelimiter: "\\r\\n"
                headLines: 100
            ```

            ### Pipe-Delimited Records
            Process pipe-separated records:
            ```yaml
            - name: FilterPipeRecords
              type: org.deltafi.core.action.filterlines.FilterLines
              parameters:
                lineDelimiter: "|"
                excludePatterns:
                  - "^HEADER"
            ```
            """;

    public FilterLines() {
        super(ActionOptions.builder()
                .description(DESCRIPTION)
                .inputSpec(ActionOptions.InputSpec.builder()
                        .contentSummary(FilterLinesParameters.CONTENT_SELECTION_DESCRIPTION)
                        .build())
                .outputSpec(ActionOptions.OutputSpec.builder()
                        .contentSummary("Content with lines filtered according to specified criteria. " +
                                FilterLinesParameters.CONTENT_RETENTION_DESCRIPTION)
                        .build())
                .errors("When lineDelimiter is null or empty", "When Java regex patterns are invalid")
                .build());
    }

    @Override
    protected ActionContent transform(ActionContext context, FilterLinesParameters params, ActionContent content)
            throws Exception {
        String text = content.loadString();
        String delimiter = params.getLineDelimiter();

        if (delimiter == null || delimiter.isEmpty()) {
            throw new IllegalArgumentException("lineDelimiter cannot be null or empty");
        }

        // Split into lines, preserving empty trailing elements
        List<String> lines = new ArrayList<>(Arrays.asList(text.split(Pattern.quote(delimiter), -1)));

        // Remove the last empty element if content ended with delimiter
        if (!lines.isEmpty() && lines.getLast().isEmpty() && text.endsWith(delimiter)) {
            lines.removeLast();
        }

        // Apply skipLines first
        if (params.getSkipLines() != null && params.getSkipLines() > 0) {
            int skip = Math.min(params.getSkipLines(), lines.size());
            lines = new ArrayList<>(lines.subList(skip, lines.size()));
        }

        // Apply headLines and tailLines as a union
        boolean hasHead = params.getHeadLines() != null && params.getHeadLines() >= 0;
        boolean hasTail = params.getTailLines() != null && params.getTailLines() >= 0;

        if (hasHead || hasTail) {
            Set<Integer> keepIndexes = new LinkedHashSet<>();

            if (hasHead) {
                int head = Math.min(params.getHeadLines(), lines.size());
                for (int i = 0; i < head; i++) {
                    keepIndexes.add(i);
                }
            }

            if (hasTail) {
                int tail = Math.min(params.getTailLines(), lines.size());
                for (int i = lines.size() - tail; i < lines.size(); i++) {
                    keepIndexes.add(i);
                }
            }

            List<String> originalLines = lines;
            lines = new ArrayList<>();
            for (int index : keepIndexes) {
                lines.add(originalLines.get(index));
            }
        }

        // Apply includePatterns (keep lines matching based on match mode)
        List<Pattern> includePatterns = params.getCompiledIncludePatterns();
        if (includePatterns != null) {
            MatchMode mode = params.getIncludeMatchMode() != null ? params.getIncludeMatchMode() : MatchMode.OR;
            lines = lines.stream()
                    .filter(line -> matches(line, includePatterns, mode))
                    .collect(Collectors.toCollection(ArrayList::new));
        }

        // Apply excludePatterns (remove lines matching based on match mode)
        List<Pattern> excludePatterns = params.getCompiledExcludePatterns();
        if (excludePatterns != null) {
            MatchMode mode = params.getExcludeMatchMode() != null ? params.getExcludeMatchMode() : MatchMode.OR;
            lines = lines.stream()
                    .filter(line -> !matches(line, excludePatterns, mode))
                    .collect(Collectors.toCollection(ArrayList::new));
        }

        // Apply keepEmpty
        if (!params.isKeepEmpty()) {
            lines = lines.stream()
                    .filter(line -> !line.isEmpty())
                    .collect(Collectors.toCollection(ArrayList::new));
        }

        // Join lines back together
        String outputDelimiter = params.isPreserveDelimiter() ? delimiter : System.lineSeparator();
        String result = String.join(outputDelimiter, lines);

        // Add trailing delimiter if original had one and we have content
        if (!result.isEmpty() && text.endsWith(delimiter)) {
            result = result + outputDelimiter;
        }

        return ActionContent.saveContent(context, result, content.getName(), content.getMediaType());
    }

    private boolean matches(String line, List<Pattern> patterns, MatchMode mode) {
        if (mode == MatchMode.AND) {
            return patterns.stream().allMatch(pattern -> pattern.matcher(line).find());
        } else {
            return patterns.stream().anyMatch(pattern -> pattern.matcher(line).find());
        }
    }
}
