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
package org.deltafi.actionkit.action.transform;

import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.converters.ContentConverter;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.common.types.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Specialization class for TRANSFORM actions.
 * @param <P> Parameter class for configuring the transform action
 */
public abstract class TransformAction<P extends ActionParameters> extends Action<P> {
    public TransformAction(String description) {
        super(ActionType.TRANSFORM, description);
    }

    @Override
    protected final TransformResultType execute(@NotNull List<DeltaFileMessage> deltaFileMessages,
                                                @NotNull ActionContext context,
                                                @NotNull P params) {
        return transform(context, params, transformInput(deltaFileMessages.get(0), context));
    }

    private static TransformInput transformInput(DeltaFileMessage deltaFileMessage, ActionContext context) {
        return TransformInput.builder()
                .contentList(ContentConverter.convert(deltaFileMessage.getContentList(), context.getContentStorageService()))
                .metadata(deltaFileMessage.getMetadata())
                .build();
    }

    /**
     * Implements the transform execution function of a transform action
     * @param context The action configuration context object for this action execution
     * @param params The parameter class that configures the behavior of this action execution
     * @param transformInput Action input from the DeltaFile
     * @return A result object containing results for the action execution.  The result can be an ErrorResult, a FilterResult, or
     * a TransformResult
     * @see TransformResult
     * @see org.deltafi.actionkit.action.error.ErrorResult
     * @see org.deltafi.actionkit.action.filter.FilterResult
     */
    public abstract TransformResultType transform(@NotNull ActionContext context,
                                                  @NotNull P params,
                                                  @NotNull TransformInput transformInput);
}
