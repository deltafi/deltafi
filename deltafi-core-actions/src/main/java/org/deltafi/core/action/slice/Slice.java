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
package org.deltafi.core.action.slice;

// ABOUTME: Transform action that extracts a byte range from content.
// ABOUTME: Uses offset and optional size parameters, similar to substring operations.

import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.ActionOptions;
import org.deltafi.core.action.ContentSelectingTransformAction;
import org.springframework.stereotype.Component;

@Component
public class Slice extends ContentSelectingTransformAction<SliceParameters> {
    public Slice() {
        super(ActionOptions.builder()
                .description("Extracts a byte range from content using offset and optional size.")
                .inputSpec(ActionOptions.InputSpec.builder()
                        .contentSummary(SliceParameters.CONTENT_SELECTION_DESCRIPTION)
                        .build())
                .outputSpec(ActionOptions.OutputSpec.builder()
                        .contentSummary("Content sliced to the specified byte range. " +
                                SliceParameters.CONTENT_RETENTION_DESCRIPTION)
                        .build())
                .errors("When offset is beyond content size and allowEmptyResult is false")
                .build());
    }

    @Override
    protected ActionContent transform(ActionContext context, SliceParameters params, ActionContent content)
            throws Exception {
        long contentSize = content.getSize();
        long offset = params.getOffset();

        // Handle negative offset (from end of content)
        if (offset < 0) {
            offset = contentSize + offset;
            if (offset < 0) {
                if (params.isAllowEmptyResult()) {
                    offset = 0;
                } else {
                    throw new IllegalArgumentException("Negative offset " + params.getOffset() +
                            " is beyond content size " + contentSize);
                }
            }
        }

        if (offset >= contentSize) {
            if (params.isAllowEmptyResult()) {
                return content.subcontent(0, 0);
            }
            throw new IllegalArgumentException("Offset " + offset + " is beyond content size " + contentSize);
        }

        long size;
        if (params.getSize() != null) {
            if (params.getSize() < 0) {
                throw new IllegalArgumentException("Size cannot be negative: " + params.getSize());
            }
            size = Math.min(params.getSize(), contentSize - offset);
        } else {
            size = contentSize - offset;
        }

        return content.subcontent(offset, size);
    }
}
