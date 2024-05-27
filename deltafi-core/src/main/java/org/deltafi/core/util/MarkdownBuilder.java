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
package org.deltafi.core.util;

import org.apache.commons.text.StringEscapeUtils;

import java.util.List;

public class MarkdownBuilder {
    private static final String COLUMN_SEP = "|";
    private static final String HEADER_SEP = "-";
    private static final String NEWLINE = "\n";

    private final StringBuilder markdown;

    public MarkdownBuilder(String content) {
        markdown = new StringBuilder(content);
    }

    public String build() {
        return markdown.toString();
    }

    public MarkdownBuilder addList(String title, List<String> items) {
        if(items != null && !items.isEmpty()) {
            String blob = title + "\n* " + String.join("\n* ", items) + NEWLINE;
            append(blob);
        }
        return this;
    }

    public MarkdownBuilder addTable(String title, List<String> headers, List<List<String>> rows) {
        if (rows != null && !rows.isEmpty()) {
            StringBuilder table = new StringBuilder(title);
            table.append(NEWLINE);
            table.append(COLUMN_SEP).append(String.join(COLUMN_SEP, headers)).append(COLUMN_SEP).append(NEWLINE);
            table.append(COLUMN_SEP);
            headers.forEach(header -> table.append(HEADER_SEP.repeat(header.length())).append(COLUMN_SEP));
            table.append(NEWLINE);
            rows.forEach(row -> addRow(table, row));

            append(table.toString());
        }
        return this;
    }

    public MarkdownBuilder addJsonBlock(String title, String jsonLog) {
        String blob = title + "\n```json\n" + StringEscapeUtils.escapeJson(jsonLog) +"```";
        append(blob);
        return this;
    }

    private void addRow(StringBuilder content, List<String> row) {
        content.append(COLUMN_SEP);
        row.forEach(value -> content.append(StringEscapeUtils.escapeJson(value)).append(COLUMN_SEP));
        content.append(NEWLINE);
    }

    private void append(String content) {
        if (!markdown.isEmpty()) {
            markdown.append(NEWLINE);
        }

        markdown.append(content);
    }
}
