/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.actionkit.action.transform;

import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.SourceInfo;
import org.deltafi.core.domain.api.types.Content;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Base class for a TRANSFORM action that does not need to extend ActionParameters for configuration
 *
 * @see SimpleMultipartTransformAction
 * @see MultipartTransformAction
 * @see TransformAction
 */
@SuppressWarnings("unused")
public abstract class SimpleTransformAction extends TransformAction<ActionParameters> {
    public SimpleTransformAction() {
        super(ActionParameters.class);
    }

    @Override
    public final Result transform(@NotNull ActionContext context,
                                  @NotNull ActionParameters params,
                                  @NotNull SourceInfo sourceInfo,
                                  @NotNull Content content,
                                  @NotNull Map<String, String> metadata) {
        return transform(context, sourceInfo, content, metadata);
    }

    /**
     * Implements the transform execution function of a transform action
     * @param context The action configuration context object for this transform execution
     * @param sourceInfo The source info for this action
     * @param content The content for this transformation action
     * @param metadata A key-value map of metadata items for this transform execution
     * @return A result object containing results for the transform execution.  The result can be an ErrorResult, a FilterResult, or
     * a TransformResult
     * @see TransformResult
     * @see org.deltafi.actionkit.action.error.ErrorResult
     * @see org.deltafi.actionkit.action.filter.FilterResult
     */
    public abstract Result transform(@NotNull ActionContext context,
                                     @NotNull SourceInfo sourceInfo,
                                     @NotNull Content content,
                                     @NotNull Map<String, String> metadata);
}
