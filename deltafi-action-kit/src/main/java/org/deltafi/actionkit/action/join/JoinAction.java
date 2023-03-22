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
package org.deltafi.actionkit.action.join;

import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.ActionInput;
import org.deltafi.common.types.ActionType;
import org.deltafi.common.types.DeltaFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Specialization class for JOIN actions.
 * @param <P> Parameter class for configuring the Join action
 */
public abstract class JoinAction<P extends ActionParameters> extends Action<P> {
    public JoinAction(String description) {
        super(ActionType.JOIN, description);
    }

    @Override
    public final JoinResultType executeAction(ActionInput actionInput) {
        return join(actionInput.getDeltaFile(), actionInput.getJoinedDeltaFiles(), actionInput.getActionContext(),
                convertToParams(actionInput.getActionParams()));
    }

    /**
     * Joins content and metadata into the provided DeltaFile from the provided joined DeltaFiles.
     * @param deltaFile the DeltaFile to join into
     * @param joinedDeltaFiles the DeltaFiles to join from
     * @param context the context for this action execution
     * @param params the parameters that configure the behavior of this action execution
     * @return a {@link JoinResult} object containing results for the action execution, an
     * {@link org.deltafi.actionkit.action.error.ErrorResult} if an error occurs, or a
     * {@link org.deltafi.actionkit.action.filter.FilterResult} if the joined DeltaFile should be filtered
     */
    protected abstract JoinResultType join(DeltaFile deltaFile, List<DeltaFile> joinedDeltaFiles, ActionContext context,
            P params);

    @Override
    protected JoinResultType execute(@NotNull DeltaFile deltaFile, @NotNull ActionContext context,
            @NotNull ActionParameters params) {
        throw new UnsupportedOperationException("Join actions require a list of joined DeltaFiles");
    }
}
