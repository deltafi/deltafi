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
package org.deltafi.common.util;

import org.apache.commons.text.StringEscapeUtils;

import java.util.List;

public class MarkdownBuilder {
    private static final char NEWLINE = '\n';

    private final StringBuilder markdown;

    public MarkdownBuilder() {
        markdown = new StringBuilder();
    }

    public MarkdownBuilder(String content) {
        markdown = new StringBuilder(content);
    }

    public String build() {
        return markdown.toString();
    }

    public MarkdownBuilder addList(String title, List<String> items) {
        if (items != null && !items.isEmpty()) {
            String blob = title + "\n* " + String.join("\n* ", items) + NEWLINE;
            if (!markdown.isEmpty()) {
                markdown.append(NEWLINE);
            }
            append(blob);
        }
        return this;
    }

    public MarkdownBuilder addSimpleTable(List<String> columnHeaders, List<List<String>> rows) {
        return addTable(columnHeaders.stream().map(header -> new ColumnDef(header, false, true)).toList(), rows);
    }

    public record ColumnDef(String header, boolean center, boolean escapeJson) {}

    public MarkdownBuilder addTable(List<ColumnDef> columnDefs, List<List<String>> rows) {
        if (rows == null || rows.isEmpty()) {
            return this;
        }

        int[] columnWidths = getColumnWidths(columnDefs, rows);

        StringBuilder table = new StringBuilder();

        table.append("| ");
        for (int column = 0; column < columnWidths.length; column++) {
            table.append(columnDefs.get(column).header());
            table.append(" ".repeat(columnWidths[column] - columnDefs.get(column).header().length()));
            table.append(" |");
            if (column < (columnWidths.length - 1)) {
                table.append(' ');
            }
        }
        table.append(NEWLINE);

        table.append("|");
        for (int column = 0; column < columnWidths.length; column++) {
            ColumnDef header = columnDefs.get(column);
            table.append(header.center() ? ':' : '-');
            table.append("-".repeat(columnWidths[column]));
            table.append(header.center() ? ':' : '-');
            table.append('|');
        }
        table.append(NEWLINE);

        rows.forEach(row -> addRow(table, columnDefs, columnWidths, row));

        append(table.toString());

        return this;
    }

    private int[] getColumnWidths(List<ColumnDef> columnDefs, List<List<String>> rows) {
        int[] columnWidths = columnDefs.stream().map(columnDef -> columnDef.header().length()).mapToInt(a -> a).toArray();

        for (List<String> row : rows) {
            for (int column = 0; column < row.size(); column++) {
                String cellValue = columnDefs.get(column).escapeJson() ?
                        StringEscapeUtils.escapeJson(row.get(column)) : row.get(column);
                if (cellValue.length() > columnWidths[column]) {
                    columnWidths[column] = cellValue.length();
                }
            }
        }

        return columnWidths;
    }

    private void addRow(StringBuilder table, List<ColumnDef> columnDefs, int[] columnWidths, List<String> row) {
        table.append("| ");
        for (int column = 0; column < columnWidths.length; column++) {
            String cellValue = columnDefs.get(column).escapeJson() ?
                    StringEscapeUtils.escapeJson(row.get(column)) : row.get(column);
            table.append(cellValue);
            table.append(" ".repeat(columnWidths[column] - cellValue.length()));
            table.append(" |");
            if (column < (columnWidths.length - 1)) {
                table.append(' ');
            }
        }
        table.append(NEWLINE);
    }

    public MarkdownBuilder addJsonBlock(String title, String jsonLog) {
        String blob = title + "\n```json\n" + StringEscapeUtils.escapeJson(jsonLog) + "```";
        if (!markdown.isEmpty()) {
            markdown.append(NEWLINE);
        }
        append(blob);
        return this;
    }

    public MarkdownBuilder append(char content) {
        markdown.append(content);
        return this;
    }

    public MarkdownBuilder append(String content) {
        markdown.append(content);
        return this;
    }
}
