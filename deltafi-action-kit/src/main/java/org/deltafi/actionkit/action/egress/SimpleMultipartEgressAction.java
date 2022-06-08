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
import org.deltafi.actionkit.action.egress.EgressAction;
import org.deltafi.actionkit.action.egress.MultipartEgressAction;
import org.deltafi.actionkit.action.egress.SimpleEgressAction;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.SourceInfo;
import org.deltafi.core.domain.generated.types.FormattedData;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Base class for an EGRESS action that will process multi-part content, but does not need to extend
 * ActionParameters for configuration
 *
 * @see SimpleEgressAction
 * @see MultipartEgressAction
 * @see EgressAction
 */
@SuppressWarnings("unused")
public abstract class SimpleMultipartEgressAction extends MultipartEgressAction<EgressActionParameters> {
    public SimpleMultipartEgressAction() {
        super(EgressActionParameters.class);
    }

    @Override
    public final Result egress(@NotNull ActionContext context, @NotNull EgressActionParameters params, @NotNull SourceInfo sourceInfo, @NotNull List<FormattedData> formattedDataList) {
        return egress(context, sourceInfo, formattedDataList);
    }

    /**
     * Implements the egress execution function of an egress action
     * @param context The action configuration context object for this action execution
     * @param sourceInfo The source info for this action execution
     * @param formattedDataList The content to be egressed by this action
     * @return A result object containing results for the action execution.  The result can be an ErrorResult, a FilterResult, or
     * a FormatResult
     * @see FormatResult
     * @see org.deltafi.actionkit.action.error.ErrorResult
     * @see org.deltafi.actionkit.action.filter.FilterResult
     */
    public abstract Result egress(@NotNull ActionContext context, @NotNull SourceInfo sourceInfo, @NotNull List<FormattedData> formattedDataList);
}