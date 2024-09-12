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
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Component
public class Split extends TransformAction<ActionParameters> {
    public Split() {
        super("Splits content into multiple DeltaFiles.");
    }

    @Override
    public TransformResultType transform(@NotNull ActionContext context, @NotNull ActionParameters params,
            @NotNull TransformInput input) {
        TransformResults results = new TransformResults(context);

        for (ActionContent content : input.content()) {
            TransformResult transformResult = new TransformResult(context);
            transformResult.addContent(content);
            transformResult.addMetadata(input.getMetadata());
            results.add(transformResult, content.getName());
        }

        return results;
    }
}
