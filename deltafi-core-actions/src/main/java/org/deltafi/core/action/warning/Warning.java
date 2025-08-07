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
package org.deltafi.core.action.warning;

import org.deltafi.actionkit.action.transform.TransformAction;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.actionkit.action.transform.TransformResultType;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.ActionOptions;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Component
public class Warning extends TransformAction<WarningParameters> {
    public Warning() {
        super(ActionOptions.builder()
                .description("Logs a warning by default or when optional criteria is met in metadata.")
                .inputSpec(ActionOptions.InputSpec.builder()
                        .metadataSummary("""
                                If metadataTrigger is set, the input metadata will be checked to determine if a
                                warning should occur and what the warning message will be.""")
                        .build())
                .outputSpec(ActionOptions.OutputSpec.builder()
                        .contentSummary(" Input content is passed through unchanged")
                        .build())
                .errors("N/A")
                .build());
    }

    @Override
    public TransformResultType transform(@NotNull ActionContext context, @NotNull WarningParameters params,
                                         @NotNull TransformInput input) {
        TransformResult result = new TransformResult(context, input.getContent());
        if (params.getMetadataTrigger() == null) {
            result.logWarning(params.getMessage());
        }
        if (input.getMetadata().get(params.getMetadataTrigger()) != null) {
            result.logWarning(input.metadata(params.getMetadataTrigger()));
        }
        return result;
    }
}
