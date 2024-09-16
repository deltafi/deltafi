/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.actionkit.action.ingress;

import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.ContentInput;
import org.deltafi.actionkit.action.transform.TransformResults;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.ActionType;
import org.deltafi.common.types.DeltaFileMessage;
import org.jetbrains.annotations.NotNull;

/**
 * Specialization class for TIMED INGRESS actions.
 * @param <P> Parameter class for configuring the Timed Ingress action
 */
public abstract class TimedIngressAction<P extends ActionParameters> extends Action<ContentInput, P, IngressResultType> {
    public TimedIngressAction(@NotNull String description) {
        super(ActionType.TIMED_INGRESS, description);
    }

    @Override
    protected ContentInput buildInput(@NotNull ActionContext actionContext, @NotNull DeltaFileMessage deltaFileMessage) {
        return null;
    }

    @Override
    protected final IngressResultType execute(@NotNull ActionContext context, @NotNull ContentInput contentInput,
            @NotNull P params) {
        return ingress(context, params);
    }

    /**
     * Implements the ingress execution function of a timed ingress action
     * @param context The action configuration context object for this action execution
     * @param params The parameter class that configures the behavior of this action execution
     * @see IngressResultItem
     * @see org.deltafi.actionkit.action.error.ErrorResult
     * @see org.deltafi.actionkit.action.filter.FilterResult
     * @see TransformResults
     */
    public abstract IngressResultType ingress(@NotNull ActionContext context, @NotNull P params);
}
