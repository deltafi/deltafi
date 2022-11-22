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
package org.deltafi.actionkit.action;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.deltafi.common.types.Metric;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.ActionEventInput;
import org.deltafi.common.types.ActionEventType;
import org.jetbrains.annotations.NotNull;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

/**
 * Base class for all action results.  Specializations of the Result class are provided for each action type.
 */
@Getter
@EqualsAndHashCode
public abstract class Result implements ResultType {

    protected final ActionContext context;

    public Result(@NotNull ActionContext context) {
        this.context = context;
    }

    /**
     * @return the action event type for the specific type of action being executed
     */
    public abstract ActionEventType actionEventType();

    /**
     * @return action event summary object based on the action context
     */
    public ActionEventInput toEvent() {
        return ActionEventInput.newBuilder()
                .did(context.getDid())
                .action(context.getName())
                .type(actionEventType())
                .start(context.getStartTime())
                .stop(OffsetDateTime.now())
                .metrics(this.getCustomMetrics())
                .build();
    }

    /**
     * @return action execution context
     */
    public ActionContext getContext() {
        return context;
    }

    /**
     * This method should be overridden to provide any custom metrics when the result is harvested.
     * @return collection of Metric objects
     */
    public List<Metric> getCustomMetrics() {
        return Collections.emptyList();
    }

}
