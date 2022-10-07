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
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.SourceInfo;
import org.deltafi.common.types.Content;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Base class for a TRANSFORM action that will process multi-part content, but does not need to extend
 * ActionParameters for configuration
 *
 * @see SimpleTransformAction
 * @see MultipartTransformAction
 * @see TransformAction
 */
@SuppressWarnings("unused")
public abstract class SimpleMultipartTransformAction extends MultipartTransformAction<ActionParameters> {
    public SimpleMultipartTransformAction(String description) {
        super(ActionParameters.class, description);
    }

    @Override
    public final Result transform(@NotNull ActionContext context,
                                  @NotNull ActionParameters params,
                                  @NotNull SourceInfo sourceInfo,
                                  @NotNull List<Content> contentList,
                                  @NotNull Map<String, String> metadata) {
        return transform(context, sourceInfo, contentList, metadata);
    }

    public abstract Result transform(@NotNull ActionContext context,
                                     @NotNull SourceInfo sourceInfo,
                                     @NotNull List<Content> contentList,
                                     @NotNull Map<String, String> metadata);
}
