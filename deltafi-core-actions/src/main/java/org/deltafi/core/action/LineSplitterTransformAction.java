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
package org.deltafi.core.action;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.transform.TransformAction;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.actionkit.action.transform.TransformResultType;
import org.deltafi.core.action.splitter.LineSplitter;
import org.deltafi.core.exception.SplitException;
import org.deltafi.common.types.ActionContext;
import org.deltafi.core.parameters.LineSplitterParameters;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LineSplitterTransformAction extends TransformAction<LineSplitterParameters> {

    public LineSplitterTransformAction() {
        super("Splits the first Content into multiple pieces of content with sub-references to the original content");
    }

    @Override
    public TransformResultType transform(@NotNull ActionContext context, @NotNull LineSplitterParameters params, @NotNull TransformInput transformInput) {
        TransformResult result = new TransformResult(context);
        ActionContent originalContent = transformInput.content(0);

        try {
            result.addContent(LineSplitter.splitContent(originalContent, params));
        } catch (SplitException splitException) {
            return new ErrorResult(context, splitException.getMessage(), splitException);
        }

        return result;
    }
}
