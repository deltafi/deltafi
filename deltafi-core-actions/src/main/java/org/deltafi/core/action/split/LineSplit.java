/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.action.split;

// ABOUTME: Transform action that extracts each line of content into its own subcontent.
// ABOUTME: Output can split into multiple child results, or stay as a single result.

import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.transform.*;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.ActionOptions;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Component
public class LineSplit extends TransformAction<LineSplitParameters> {
    public LineSplit() {
        super(ActionOptions.builder()
                .description("Split lines from a single input content into multiple output contents, optionally creating new DeltaFiles.")
                .inputSpec(ActionOptions.InputSpec.builder()
                        .contentSummary("""
                                Only a single content input is allowed. Content must not be empty.
                                Content will be split by line using offset and size references from
                                the original saved content. LF and CRLF line delimiters are both supported.
                                """)
                        .build())
                .outputSpec(ActionOptions.OutputSpec.builder()
                        .contentSummary("""
                                Each line of content from the input will become its own content.
                                When 'skipBlankLines' is TRUE, lines of content which are empty or only
                                contains spaces will not be included in the output.
                                If 'splitIntoChildResults' is TRUE, output content will be split into child
                                DeltaFiles. When splitting into child DeltaFiles, the number of output
                                contents per child can be controlled with the 'maxContentPerChild'
                                parameter.
                                All output is stripped of LF and CRLF delimiters.
                                """)
                        .build())
                .errors("More than one input content is received",
                        "Input content is empty",
                        "An unexpected problem reading the input content")
                .notes("Output only uses offset and size references from the input content. Action does not require saving any new content to storage.")
                .build());

    }

    @Override
    public TransformResultType transform(@NotNull ActionContext context, @NotNull LineSplitParameters params, @NotNull TransformInput input) {

        if (input.getContent().size() != 1) {
            return new ErrorResult(context, "Expected one content but received " + input.getContent().size());
        }

        ActionContent firstContent = input.getContent().getFirst();
        List<ActionContent> lines;
        try (InputStream is = firstContent.loadInputStream()) {
            lines = parseInputStream(firstContent, is, params.isSkipBlankLines());
        } catch (IOException e) {
            return new ErrorResult(context, "Error occurred while reading content", e);
        }

        if (lines.isEmpty()) {
            return new ErrorResult(context, "No content lines were found");
        }

        if (params.isSplitIntoChildResults()) {
            return makeChildResults(context, params, lines);
        }

        return new TransformResult(context, lines);
    }

    private List<ActionContent> parseInputStream(ActionContent firstContent, InputStream is, boolean skipBlank) throws IOException {
        List<ActionContent> lines = new ArrayList<>();
        long currentOffset = 0;
        long lineStartOffset = 0;
        boolean blank = true;
        int b;
        while ((b = is.read()) != -1) {
            currentOffset++;

            if (b == '\n') {
                if (!skipBlank || !blank) {
                    ActionContent line = firstContent.subcontent(lineStartOffset, (currentOffset - lineStartOffset - 1));
                    lines.add(line);
                }
                lineStartOffset = currentOffset;
                blank = true;
            } else if (b == '\r') {
                // Check Windows CRLF; peek
                is.mark(1);
                int next = is.read();
                if (next == '\n') {
                    if (!skipBlank || !blank) {
                        ActionContent line = firstContent.subcontent(lineStartOffset, (currentOffset - lineStartOffset - 1));
                        lines.add(line);
                    }
                    blank = true;
                    currentOffset++;
                } else if (next != -1) {
                    // Not a LF; roll back
                    is.reset();
                }
                lineStartOffset = currentOffset;
            } else if (b != ' ') {
                blank = false;
            }
        }
        if (currentOffset > lineStartOffset) {
            // last line
            if (!skipBlank || !blank) {
                ActionContent line = firstContent.subcontent(lineStartOffset, (currentOffset - lineStartOffset));
                lines.add(line);
            }
        }

        return lines;
    }

    private TransformResults makeChildResults(ActionContext context, LineSplitParameters params, List<ActionContent> lines) {
        TransformResults results = new TransformResults(context);
        int maxContentPerChild = Math.max(1, params.getMaxContentPerChild());
        int start = 0;
        while (start < lines.size()) {
            int end = Math.min(lines.size(), start + maxContentPerChild);
            results.add(new ChildTransformResult(context, lines.subList(start, end)));
            start = end;
        }
        return results;
    }
}
