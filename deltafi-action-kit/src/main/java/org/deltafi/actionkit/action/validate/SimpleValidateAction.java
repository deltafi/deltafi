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
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.SourceInfo;
import org.deltafi.core.domain.generated.types.FormattedData;
import org.jetbrains.annotations.NotNull;

/**
 * Base class for a VALIDATE action that does not need to extend ActionParameters for configuration
 *
 * @see SimpleMultipartValidateAction
 * @see MultipartValidateAction
 * @see ValidateAction
 */
public abstract class SimpleValidateAction extends ValidateAction<ActionParameters> {
    public SimpleValidateAction() {
        super(ActionParameters.class);
    }

    @Override
    public final Result validate(@NotNull ActionContext context, @NotNull ActionParameters params, @NotNull SourceInfo sourceInfo, @NotNull FormattedData formattedData) {
        return validate(context, sourceInfo, formattedData);
    }

    /**
     * Implements the validate execution function of a validate action
     * @param context The action configuration context object for this action execution
     * @param sourceInfo The source info for this action execution
     * @param formattedData The content to be validated by this action
     * @return A result object containing results for the action execution.  The result can be an ErrorResult, a FilterResult, or
     * a ValidateResult
     * @see ValidateResult
     * @see org.deltafi.actionkit.action.error.ErrorResult
     * @see org.deltafi.actionkit.action.filter.FilterResult
     */
    public abstract Result validate(@NotNull ActionContext context, @NotNull SourceInfo sourceInfo, @NotNull FormattedData formattedData);
}