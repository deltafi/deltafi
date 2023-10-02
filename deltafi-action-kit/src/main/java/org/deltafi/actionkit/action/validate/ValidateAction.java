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
package org.deltafi.actionkit.action.validate;

import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.ActionType;
import org.deltafi.common.types.DeltaFileMessage;
import org.jetbrains.annotations.NotNull;

/**
 * Specialization class for VALIDATE actions.
 * @param <P> Parameter class for configuring the validate action
 */
public abstract class ValidateAction<P extends ActionParameters> extends Action<ValidateInput, P, ValidateResultType> {
    public ValidateAction(@NotNull String description) {
        super(ActionType.VALIDATE, description);
    }

    @Override
    protected ValidateInput buildInput(@NotNull ActionContext context, @NotNull DeltaFileMessage deltaFileMessage) {
        return ValidateInput.builder()
                .content(new ActionContent(deltaFileMessage.getContentList().get(0), context.getContentStorageService()))
                .metadata(deltaFileMessage.getMetadata())
                .build();
    }

    @Override
    protected final ValidateResultType execute(@NotNull ActionContext context, @NotNull ValidateInput input, @NotNull P params) {
        return validate(context, params, input);
    }

    /**
     * Implements the validate execution function of a validate action
     * @param context The action configuration context object for this action execution
     * @param params The parameter class that configures the behavior of this action execution
     * @param validateInput Action input from the DeltaFile
     * @return A result object containing results for the action execution.  The result can be an ErrorResult, a
     * FilterResult, or a ValidateResult
     * @see ValidateResult
     * @see org.deltafi.actionkit.action.error.ErrorResult
     * @see org.deltafi.actionkit.action.filter.FilterResult
     */
    public abstract ValidateResultType validate(@NotNull ActionContext context, @NotNull P params, @NotNull ValidateInput validateInput);
}
