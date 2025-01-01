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
package org.deltafi.actionkit.action.service;

import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.actionkit.action.transform.TransformAction;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.actionkit.action.transform.TransformResultType;
import org.deltafi.common.types.ActionContext;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Component
public class TransformTestAction extends TransformAction<ActionParameters> {
    public TransformTestAction() {
        super("Save content but return error");
    }

    @Override
    public TransformResultType transform(@NotNull ActionContext context,
                                         @NotNull ActionParameters params,
                                         @NotNull TransformInput input) {
        // These will be orphaned content
        TransformResult temp = new TransformResult(context, input.getContent());
        temp.saveContent("abc", "name1", "text/plain");
        temp.saveContent("abcd", "name2", "text/plain");

        TransformResult transformResult = new TransformResult(context, input.getContent());
        transformResult.saveContent("abcde", "name3", "text/plain");

        return transformResult;
    }
}
