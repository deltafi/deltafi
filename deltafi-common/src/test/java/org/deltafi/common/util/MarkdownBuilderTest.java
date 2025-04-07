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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class MarkdownBuilderTest {
    @Test
    void builds() {
        MarkdownBuilder markdownBuilder = new MarkdownBuilder("Title\n");
        markdownBuilder.addTable(List.of(new MarkdownBuilder.ColumnDef("Column A", false, true),
                new MarkdownBuilder.ColumnDef("Column B", true, true), new MarkdownBuilder.ColumnDef("Column C", true, true)),
                List.of(List.of("1A", "1B", "1C"), List.of("2A", "This is a long value", "2C"), List.of("3A", "3B", "3C")));
        Assertions.assertEquals("""
                Title
                | Column A | Column B             | Column C |
                |----------|:--------------------:|:--------:|
                | 1A       | 1B                   | 1C       |
                | 2A       | This is a long value | 2C       |
                | 3A       | 3B                   | 3C       |
                """, markdownBuilder.build());
    }

    @Test
    void buildsSimple() {
        MarkdownBuilder markdownBuilder = new MarkdownBuilder("Title\n");
        markdownBuilder.addSimpleTable(List.of("Column A", "Column B", "Column C"),
                List.of(List.of("1A", "1B", "1C"), List.of("2A", "This is a long value", "2C"), List.of("3A", "3B", "3C")));
        Assertions.assertEquals("""
                Title
                | Column A | Column B             | Column C |
                |----------|----------------------|----------|
                | 1A       | 1B                   | 1C       |
                | 2A       | This is a long value | 2C       |
                | 3A       | 3B                   | 3C       |
                """, markdownBuilder.build());
    }
}
