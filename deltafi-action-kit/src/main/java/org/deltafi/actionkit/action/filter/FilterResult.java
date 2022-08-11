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
package org.deltafi.actionkit.action.filter;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.deltafi.actionkit.action.Result;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.ActionEventInput;
import org.deltafi.common.types.ActionEventType;
import org.deltafi.common.types.FilterInput;
import org.jetbrains.annotations.NotNull;

/**
 * Specialized result class for terminating an action in the FILTERED state
 */
@Getter
@EqualsAndHashCode(callSuper = true)
public class FilterResult extends Result {
    private final String message;

    /**
     * @param context Execution context of the filtered action
     * @param message Message explaining the reason for the filtered action
     */
    public FilterResult(@NotNull ActionContext context, @NotNull String message) {
        super(context);

        this.message = message;
    }

    @Override
    public final ActionEventType actionEventType() {
        return ActionEventType.FILTER;
    }

    @Override
    public final ActionEventInput toEvent() {
        ActionEventInput event = super.toEvent();
        event.setFilter(FilterInput.newBuilder().message(message).build());
        return event;
    }
}