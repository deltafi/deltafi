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
package org.deltafi.actionkit.action.load;

import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.SourceInfo;
import org.deltafi.common.types.Content;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Base class for a LOAD action that will process multi-part content, but does not need to extend
 * ActionParameters for configuration
 *
 * @see SimpleLoadAction
 * @see MultipartLoadAction
 * @see LoadAction
 */
@SuppressWarnings("unused")
public abstract class SimpleMultipartLoadAction extends MultipartLoadAction<ActionParameters> {
    public SimpleMultipartLoadAction(String description) {
        super(ActionParameters.class, description);
    }

    @Override
    public final LoadResultType load(@NotNull ActionContext context,
                                     @NotNull ActionParameters params,
                                     @NotNull SourceInfo sourceInfo,
                                     @NotNull List<Content> contentList,
                                     @NotNull Map<String, String> metadata) {
        return load(context, sourceInfo, contentList, metadata);
    }

    /**
     * Implements the load execution function of a load action
     * @param context The action configuration context object for this action execution
     * @param sourceInfo The source info for this action execution
     * @param contentList The content to be loaded by this action
     * @param metadata The metadata to be applied to this action
     * @return A result object containing results for the action execution.
     *         The result can be an ErrorResult, SplitResult, FilterResult, or LoadResult
     * @see LoadResult
     * @see org.deltafi.actionkit.action.error.ErrorResult
     * @see org.deltafi.actionkit.action.filter.FilterResult
     * @see SplitResult
     */
    public abstract LoadResultType load(@NotNull ActionContext context,
                                        @NotNull SourceInfo sourceInfo,
                                        @NotNull List<Content> contentList,
                                        @NotNull Map<String, String> metadata);
}
