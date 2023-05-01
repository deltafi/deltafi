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
package org.deltafi.actionkit.action.egress;

import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.common.types.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Specialization class for EGRESS actions.
 * @param <P> Parameter class for configuring the egress action
 */
public abstract class EgressAction<P extends ActionParameters> extends Action<P> {
    public EgressAction(String description) {
        super(ActionType.EGRESS, description);
    }

    @Override
    protected final EgressResultType execute(@NotNull List<DeltaFileMessage> deltaFileMessages, @NotNull ActionContext context, @NotNull P params) {
        return egress(context, params, egressInput(deltaFileMessages.get(0), context));
    }

    private static EgressInput egressInput(DeltaFileMessage deltaFileMessage, ActionContext context) {
        return EgressInput.builder()
                .sourceFilename(deltaFileMessage.getSourceFilename())
                .content(deltaFileMessage.getContentList().get(0))
                .metadata(deltaFileMessage.getMetadata())
                .actionContext(context)
                .build();
    }

    /**
     * Implements the egress execution function of an egress action
     * @param context The action configuration context object for this action execution
     * @param params The parameter class that configures the behavior of this action execution
     * @param egressInput Action input from the DeltaFile
     * @return A result object containing results for the action execution.  The result can be an ErrorResult, a FilterResult, or
     * an EgressResult
     * @see EgressResult
     * @see org.deltafi.actionkit.action.error.ErrorResult
     * @see org.deltafi.actionkit.action.filter.FilterResult
     */
    public abstract EgressResultType egress(@NotNull ActionContext context, @NotNull P params, @NotNull EgressInput egressInput);
}
