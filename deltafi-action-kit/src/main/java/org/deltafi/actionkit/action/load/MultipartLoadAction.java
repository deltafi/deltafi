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

import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.api.types.SourceInfo;
import org.deltafi.core.domain.api.types.Content;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Base class for a LOAD action that will handle multi-part content on input and needs to extend
 * ActionParameters for configuration
 *
 * @see SimpleMultipartLoadAction
 * @see SimpleLoadAction
 * @see LoadAction
 */
@SuppressWarnings("unused")
public abstract class MultipartLoadAction<P extends ActionParameters> extends LoadActionBase<P> {
    public MultipartLoadAction(Class<P> actionParametersClass) {
        super(actionParametersClass);
    }

    @Override
    protected final Result execute(@NotNull DeltaFile deltaFile,
                                   @NotNull ActionContext context,
                                   @NotNull P params) {
        return load(context,
                params,
                deltaFile.getSourceInfo(),
                deltaFile.getLastProtocolLayerContent(),
                deltaFile.getLastProtocolLayerMetadataAsMap());
    }

    /**
     * Implements the load execution function of a load action
     * @param context The action configuration context object for this action execution
     * @param params The parameter class that configures the behavior of this action execution
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
    public abstract Result load(@NotNull ActionContext context,
                                @NotNull P params,
                                @NotNull SourceInfo sourceInfo,
                                @NotNull List<Content> contentList,
                                @NotNull Map<String, String> metadata);
}