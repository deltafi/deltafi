/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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
import lombok.RequiredArgsConstructor;
import org.deltafi.common.types.*;
import org.jetbrains.annotations.NotNull;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Base class for all action results.  Specializations of the Result class are provided for each action type.
 */
@RequiredArgsConstructor
@EqualsAndHashCode
@Getter
public abstract class Result<T extends Result<T>> implements ResultType {
    protected final ActionContext context;
    protected final ActionEventType actionEventType;

    protected final ArrayList<Metric> metrics = new ArrayList<>();
    protected final List<LogMessage> messages = new ArrayList<>();

    /**
     * @return action event summary object based on the action context
     */
    public ActionEvent toEvent() {
        return ActionEvent.builder()
                .type(actionEventType)
                .actionName(context.getActionName())
                .flowName(context.getFlowName())
                .flowId(context.getFlowId())
                .did(context.getDid())
                .start(context.getStartTime())
                .stop(OffsetDateTime.now())
                .metrics(getCustomMetrics())
                .messages(messages)
                .build();
    }

    /**
     * Return a list of custom metrics
     *
     * @return collection of Metric objects
     */
    public List<Metric> getCustomMetrics() {
        return metrics;
    }

    /**
     * Add a custom metric to the result
     *
     * @param metric Metric object to add to the list of custom metrics in the result
     * @return this instance of Result base class
     */
    public T add(@NotNull Metric metric) {
        metrics.add(metric);
        //noinspection unchecked
        return (T) this;
    }

    /**
     * Add an informational message to the result
     *
     * @param message the informational message to be added
     * @return this instance of Result base class
     */
    public T logInfo(@NotNull String message) {
        messages.add(LogMessage.createInfo(context.getActionName(), message));
        return (T) this;
    }

    /**
     * Add a warning message to the result
     *
     * @param message the warning message to be added
     * @return this instance of Result base class
     */
    public T logWarning(@NotNull String message) {
        messages.add(LogMessage.createWarning(context.getActionName(), message));
        return (T) this;
    }

    /**
     * Add an error message to the result
     *
     * @param message the error message to be added
     * @return this instance of Result base class
     */
    protected T logError(@NotNull String message) {
        messages.add(LogMessage.createError(context.getActionName(), message));
        return (T) this;
    }
}
