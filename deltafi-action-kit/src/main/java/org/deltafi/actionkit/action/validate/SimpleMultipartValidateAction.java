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
package org.deltafi.actionkit.action.validate;

import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.SourceInfo;
import org.deltafi.common.types.FormattedData;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Base class for a VALIDATE action that will process multi-part content, but does not need to extend
 * ActionParameters for configuration
 *
 * @see SimpleValidateAction
 * @see MultipartValidateAction
 * @see ValidateAction
 */
@SuppressWarnings("unused")
public abstract class SimpleMultipartValidateAction extends MultipartValidateAction<ActionParameters> {
    public SimpleMultipartValidateAction() {
        super(ActionParameters.class);
    }

    @Override
    public final Result validate(@NotNull ActionContext context, @NotNull ActionParameters params, @NotNull SourceInfo sourceInfo, @NotNull List<FormattedData> formattedDataList) {
        return validate(context, sourceInfo, formattedDataList);
    }

    /**
     * Implements the validate execution function of a Validate action
     * @param context The action configuration context object for this action execution
     * @param sourceInfo The source info for this action execution
     * @param formattedDataList The list of data objects to be validated by this action
     * @return A result object containing results for the action execution.  The result can be an ErrorResult, a FilterResult, or
     * a ValidateResult
     * @see ValidateResult
     * @see org.deltafi.actionkit.action.error.ErrorResult
     * @see org.deltafi.actionkit.action.filter.FilterResult
     */
    public abstract Result validate(@NotNull ActionContext context, @NotNull SourceInfo sourceInfo, @NotNull List<FormattedData> formattedDataList);
}
