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
package org.deltafi.actionkit.action.filter;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.ResultType;
import org.deltafi.actionkit.action.egress.EgressResultType;
import org.deltafi.actionkit.action.format.FormatResultType;
import org.deltafi.actionkit.action.load.LoadResultType;
import org.deltafi.actionkit.action.transform.TransformResultType;
import org.deltafi.actionkit.action.validate.ValidateResultType;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.ActionEvent;
import org.deltafi.common.types.ActionEventType;
import org.deltafi.common.types.FilterEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Specialized result class for terminating an action in the FILTERED state
 */
@Getter
@EqualsAndHashCode(callSuper = true)
public class FilterResult extends Result<FilterResult> implements EgressResultType, FormatResultType,
        LoadResultType, TransformResultType, ValidateResultType, ResultType {

    private final String filteredCause;

    /**
     * @param context Execution context of the filtered action
     * @param filteredCause Message explaining the reason for the filtered action
     */
    public FilterResult(@NotNull ActionContext context, @NotNull String filteredCause) {
        super(context);

        this.filteredCause = filteredCause;
    }

    @Override
    protected final ActionEventType actionEventType() {
        return ActionEventType.FILTER;
    }

    @Override
    public final ActionEvent toEvent() {
        ActionEvent event = super.toEvent();
        event.setFilter(FilterEvent.newBuilder().message(filteredCause).build());
        return event;
    }
}