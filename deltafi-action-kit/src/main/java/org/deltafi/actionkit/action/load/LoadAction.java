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
package org.deltafi.actionkit.action.load;

import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.converters.ContentConverter;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.ActionType;
import org.deltafi.common.types.DeltaFileMessage;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Specialization class for LOAD actions.
 * @param <P> Parameter class for configuring the Load action
 */
public abstract class LoadAction<P extends ActionParameters> extends Action<LoadInput, P, LoadResultType> {
    public LoadAction(@NotNull String description) {
        super(ActionType.LOAD, description);
    }

    @Override
    protected LoadInput buildInput(@NotNull ActionContext context, @NotNull DeltaFileMessage deltaFileMessage) {
        return LoadInput.builder()
                .content(ContentConverter.convert(deltaFileMessage.getContentList(), context.getContentStorageService()))
                .metadata(deltaFileMessage.getMetadata())
                .build();
    }

    @Override
    protected LoadInput collect(@NotNull List<LoadInput> loadInputs) {
        List<ActionContent> allContent = new ArrayList<>();
        Map<String, String> allMetadata = new HashMap<>();
        for (LoadInput loadInput : loadInputs) {
            allContent.addAll(loadInput.getContent());
            allMetadata.putAll(loadInput.getMetadata());
        }
        return LoadInput.builder()
                .content(allContent)
                .metadata(allMetadata)
                .build();
    }

    @Override
    protected final LoadResultType execute(@NotNull ActionContext context, @NotNull LoadInput input, @NotNull P params) {
        return load(context, params, input);
    }

    /**
     * Implements the load execution function of a load action
     * @param context The action configuration context object for this action execution
     * @param params The parameter class that configures the behavior of this action execution
     * @param loadInput Action input from the DeltaFile
     * @return A result object containing results for the action execution.
     *         The result can be an ErrorResult, ReinjectResult, FilterResult, or LoadResult
     * @see LoadResult
     * @see org.deltafi.actionkit.action.error.ErrorResult
     * @see org.deltafi.actionkit.action.filter.FilterResult
     * @see org.deltafi.actionkit.action.ReinjectResult
     */
    public abstract LoadResultType load(@NotNull ActionContext context, @NotNull P params, @NotNull LoadInput loadInput);
}
