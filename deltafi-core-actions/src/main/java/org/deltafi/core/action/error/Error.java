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
package org.deltafi.core.action.error;

import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.transform.*;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.ActionOptions;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Component
public class Error extends TransformAction<ErrorParameters> {
    public Error() {
        super(ActionOptions.builder()
                .description("Errors by default or when optional criteria is met in metadata.")
                .inputSpec(ActionOptions.InputSpec.builder()
                        .metadataSummary("""
                                If metadataTrigger is set, the input metadata will be checked to determine if an error
                                should occur and what the error message will be.""")
                        .build())
                .outputSpec(ActionOptions.OutputSpec.builder()
                        .contentSummary("""
                                Input content is passed through unchanged if metadataTrigger is set and no matching
                                input metadata is present.""")
                        .build())
                .errors("On metadataTrigger unset or when input metadata matching metadataTrigger is present")
                .build());
    }

    @Override
    public TransformResultType transform(@NotNull ActionContext context, @NotNull ErrorParameters params,
            @NotNull TransformInput input) {
        if (params.getMetadataTrigger() == null) {
            return new ErrorResult(context, params.getMessage());
        }
        if (input.getMetadata().get(params.getMetadataTrigger()) != null) {
            return new ErrorResult(context, input.metadata(params.getMetadataTrigger()));
        }
        return new TransformResult(context, input.getContent());
    }
}
