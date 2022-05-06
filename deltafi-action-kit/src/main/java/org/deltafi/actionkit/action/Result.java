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
import org.deltafi.common.metric.Metric;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.generated.types.ActionEventInput;
import org.deltafi.core.domain.generated.types.ActionEventType;
import org.jetbrains.annotations.NotNull;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Base class for all action results.  Specializations of the Result class are provided for each action type.
 */
@Getter
@EqualsAndHashCode
public abstract class Result {
    private static final String FILES_IN = "files_in";
    private static final String FILES_COMPLETED = "files_completed";
    private static final String FILES_ERRORED = "files_errored";
    private static final String FILES_FILTERED = "files_filtered";

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
    public Collection<Metric> getCustomMetrics() {
        return Collections.emptyList();
    }

    /**
     * @return a collection of default metrics (based on the result event type) and custom metrics
     */
    public Collection<Metric> getMetrics() {
        List<String> metricCounters = new ArrayList<>();

        switch (actionEventType()) {
            case ERROR:
                metricCounters.addAll(List.of(FILES_IN, FILES_ERRORED));
                break;
            case FILTER:
                metricCounters.addAll(List.of(FILES_IN, FILES_FILTERED));
                break;
            default:
                metricCounters.addAll(List.of(FILES_IN, FILES_COMPLETED));
        }

        ArrayList<Metric> metrics = new ArrayList<>();
        metricCounters.forEach(counter -> metrics.add(Metric.builder().name(counter).value(1).build()));
        metrics.addAll(getCustomMetrics());
        return metrics;
    }
}
