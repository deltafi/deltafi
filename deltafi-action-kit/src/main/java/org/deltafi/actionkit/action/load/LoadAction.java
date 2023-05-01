/**
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
package org.deltafi.actionkit.action.load;

import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.common.types.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Specialization class for LOAD actions.
 * @param <P> Parameter class for configuring the Load action
 */
public abstract class LoadAction<P extends ActionParameters> extends Action<P> {
    public LoadAction(String description) {
        super(ActionType.LOAD, description);
    }

    @Override
    protected final LoadResultType execute(@NotNull List<DeltaFileMessage> deltaFileMessages,
                                           @NotNull ActionContext context,
                                           @NotNull P params) {
        return load(context, params, loadInput(deltaFileMessages.get(0), context));
    }

    private static LoadInput loadInput(DeltaFileMessage deltaFileMessage, ActionContext context) {
        return LoadInput.builder()
                .contentList(deltaFileMessage.getContentList())
                .metadata(deltaFileMessage.getMetadata())
                .actionContext(context)
                .build();
    }

    /**
     * Implements the load execution function of a load action
     * @param context The action configuration context object for this action execution
     * @param params The parameter class that configures the behavior of this action execution
     * @param loadInput Action input from the DeltaFile
     * @return A result object containing results for the action execution.
     *         The result can be an ErrorResult, SplitResult, FilterResult, or LoadResult
     * @see LoadResult
     * @see org.deltafi.actionkit.action.error.ErrorResult
     * @see org.deltafi.actionkit.action.filter.FilterResult
     * @see SplitResult
     */
    public abstract LoadResultType load(@NotNull ActionContext context,
                                        @NotNull P params,
                                        @NotNull LoadInput loadInput);
}
