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
package org.deltafi.actionkit.action.egress;

import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.format.FormatResult;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.DeltaFile;
import org.deltafi.common.types.SourceInfo;
import org.deltafi.common.types.FormattedData;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Base class for an EGRESS action that will handle multi-part content on input and needs to extend
 * ActionParameters for configuration
 *
 * @see SimpleMultipartEgressAction
 * @see SimpleEgressAction
 * @see EgressAction
 */
@SuppressWarnings("unused")
public abstract class MultipartEgressAction<P extends EgressActionParameters> extends EgressActionBase<P> {
    public MultipartEgressAction(Class<P> actionParametersClass) {
        super(actionParametersClass);
    }

    @Override
    protected final Result execute(@NotNull DeltaFile deltaFile, @NotNull ActionContext context, @NotNull P params) {
        return egress(context, params, deltaFile.getSourceInfo(), deltaFile.getFormattedData());
    }

    /**
     * Implements the egress execution function of an egress action
     * @param context The action configuration context object for this action execution
     * @param params The parameter class that configures the behavior of this action execution
     * @param sourceInfo The source info for this action execution
     * @param formattedDataList The content to be egressed by this action
     * @return A result object containing results for the action execution.  The result can be an ErrorResult, a FilterResult, or
     * a FormatResult
     * @see FormatResult
     * @see org.deltafi.actionkit.action.error.ErrorResult
     * @see org.deltafi.actionkit.action.filter.FilterResult
     */
    public abstract Result egress(@NotNull ActionContext context, @NotNull P params, @NotNull SourceInfo sourceInfo, @NotNull List<FormattedData> formattedDataList);
}
