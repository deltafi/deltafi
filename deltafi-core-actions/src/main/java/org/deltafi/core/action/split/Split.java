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
package org.deltafi.core.action.split;

import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.actionkit.action.transform.*;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.ActionOptions;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Component
public class Split extends TransformAction<ActionParameters> {
    public Split() {
        super(ActionOptions.builder()
                .description("Splits content into multiple DeltaFiles.")
                .outputSpec(ActionOptions.OutputSpec.builder()
                        .contentSummary("Each DeltaFile will contain a single content from the original DeltaFile.")
                        .metadataSummary("""
                                Each DeltaFile will contain a copy of the metadata from the original DeltaFile.""")
                        .build())
                .notes("""
                        The entry for this action in the original DeltaFile will be placed in the terminal SPLIT
                        state.""", """
                        New DeltaFiles created for each content in the original file will advance in the current
                        flow.""", """
                        This action is typically used after a Decompress action to process each content individually
                        after it has been extracted from an ingested compressed file.""")
                .build());
    }

    @Override
    public TransformResultType transform(@NotNull ActionContext context, @NotNull ActionParameters params,
            @NotNull TransformInput input) {
        TransformResults results = new TransformResults(context);

        for (ActionContent content : input.content()) {
            ChildTransformResult childResult = new ChildTransformResult(context, content.getName());
            childResult.addContent(content);
            childResult.addMetadata(input.getMetadata());
            results.add(childResult);
        }

        return results;
    }
}
