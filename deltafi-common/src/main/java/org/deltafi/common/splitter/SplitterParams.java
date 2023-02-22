/**
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
package org.deltafi.common.splitter;

import lombok.Builder;
import lombok.Data;
import org.springframework.util.unit.DataSize;

@Data
@Builder
public class SplitterParams {

    // Optional field - any lines that start with these characters will be ignored when finding the header row
    private String commentChars;

    // True to include the headers in all chunks of data (not including any comments)
    @Builder.Default
    private boolean includeHeaders = false;

    // Max number of rows that should be included in each chunk
    @Builder.Default
    private int maxRows = Integer.MAX_VALUE;

    // Max size of each chunk
    @Builder.Default
    private long maxSize = DataSize.ofMegabytes(500).toBytes();

    public SplitterParams(String commentChars, boolean includeHeaders, int maxRows, long maxSize) {
        this.commentChars = commentChars;
        this.includeHeaders = includeHeaders;
        setMaxRows(maxRows);
        setMaxSize(maxSize);
    }

    public void setMaxRows(int maxRows) {
        if (maxRows < 1) {
            throw new IllegalArgumentException("The max rows must be one or more");
        }

        this.maxRows = maxRows;
    }

    public void setMaxSize(long maxSize) {
        if (maxSize < 1) {
            throw new IllegalArgumentException("The max size must be one or more");
        }

        this.maxSize = maxSize;
    }

}
